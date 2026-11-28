package com.dualdex.nuzlocke

import java.io.File
import java.lang.ref.WeakReference
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Single-threaded serialized persistence queue for [NuzlockeRunState] snapshots.
 *
 * Guarantees:
 * - One shared instance per canonical store directory so recreated ViewModels serialize cleanly.
 * - Writes execute off the UI thread in FIFO order on one dedicated daemon worker thread.
 * - Per-ROM monotonic revisions prevent older snapshots from ever overwriting newer ones.
 * - Switching ROMs never cancels or drops pending writes for previous ROMs.
 * - Failed snapshots and failure errors are retained per canonical SHA-256 until a newer
 *   revision for that ROM succeeds.
 */
internal class NuzlockeWriteQueue {

    private data class WriteRequest(
        val store: NuzlockeStore,
        val romSha256: String,
        val revision: Long,
        val state: NuzlockeRunState,
        val onComplete: (String, Long, Result<Unit>) -> Unit
    )

    private data class StateSnapshot(
        val revision: Long,
        val state: NuzlockeRunState
    )

    private data class ErrorSnapshot(
        val revision: Long,
        val message: String
    )

    private val queueLock = ReentrantLock()
    private val idleCondition = queueLock.newCondition()
    private val pendingQueue = ArrayDeque<WriteRequest>()
    private val highestEnqueuedRevisionByRom = HashMap<String, Long>()
    private val lastAttemptedRevisionByRom = HashMap<String, Long>()
    private val lastCompletedRevisionByRom = HashMap<String, Long>()
    private val latestStateByRom = HashMap<String, StateSnapshot>()
    private val lastErrorByRom = HashMap<String, ErrorSnapshot>()
    private var writing = false

    private val listenersLock = Any()
    private val listeners = ArrayList<WeakReference<(String, Long, Result<Unit>) -> Unit>>()

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DualDex-NuzlockeIO").apply { isDaemon = true }
    }

    /**
     * Registers a listener notified outside [queueLock] whenever any write on this queue completes,
     * before [awaitIdle] is unblocked. Held weakly so ViewModel instances are never leaked.
     */
    fun addWeakListener(listener: (String, Long, Result<Unit>) -> Unit) {
        synchronized(listenersLock) {
            listeners.removeAll { it.get() == null || it.get() === listener }
            listeners.add(WeakReference(listener))
        }
    }

    /**
     * Enqueues [state] for [romSha256] at [revision]. Returns `false` if [romSha256] is not a
     * canonical SHA-256 or if a greater-or-equal revision was already enqueued for that ROM.
     */
    fun enqueue(
        store: NuzlockeStore,
        romSha256: String,
        revision: Long,
        state: NuzlockeRunState,
        onComplete: (String, Long, Result<Unit>) -> Unit = { _, _, _ -> }
    ): Boolean {
        val canonical = NuzlockeStore.canonicalSha256(romSha256) ?: return false
        val request = WriteRequest(store, canonical, revision, state, onComplete)
        queueLock.withLock {
            val highest = highestEnqueuedRevisionByRom[canonical] ?: 0L
            if (revision <= highest) return false
            highestEnqueuedRevisionByRom[canonical] = revision
            latestStateByRom[canonical] = StateSnapshot(revision, state)
            pendingQueue.addLast(request)
        }
        executor.execute(::drainNext)
        return true
    }

    /**
     * Atomically allocates the next monotonic revision for [romSha256] and enqueues [state].
     */
    fun enqueueLatest(
        store: NuzlockeStore,
        romSha256: String,
        state: NuzlockeRunState,
        onComplete: (String, Long, Result<Unit>) -> Unit = { _, _, _ -> }
    ): Long? {
        val canonical = NuzlockeStore.canonicalSha256(romSha256) ?: return null
        val revision: Long
        queueLock.withLock {
            revision = (highestEnqueuedRevisionByRom[canonical] ?: 0L) + 1L
            val request = WriteRequest(store, canonical, revision, state, onComplete)
            highestEnqueuedRevisionByRom[canonical] = revision
            latestStateByRom[canonical] = StateSnapshot(revision, state)
            pendingQueue.addLast(request)
        }
        executor.execute(::drainNext)
        return revision
    }

    /**
     * Returns the latest un-flushed or failed in-memory state for [romSha256], so a ROM switch
     * back or Activity recreation never silently reverts unsaved progress to stale disk data.
     */
    fun latestStateForRom(romSha256: String): NuzlockeRunState? {
        val canonical = NuzlockeStore.canonicalSha256(romSha256) ?: return null
        return queueLock.withLock { latestStateByRom[canonical]?.state }
    }

    /**
     * Returns the latest persistence error message for [romSha256] until a newer save succeeds.
     */
    fun lastErrorForRom(romSha256: String): String? {
        val canonical = NuzlockeStore.canonicalSha256(romSha256) ?: return null
        return queueLock.withLock { lastErrorByRom[canonical]?.message }
    }

    data class RomSnapshot(
        val state: NuzlockeRunState?,
        val lastError: String?,
        val lastCompletedRevision: Long
    )

    /**
     * Atomically captures the latest retained state, persistence error, and completed revision
     * for [romSha256] under a single [queueLock] acquisition.
     */
    fun snapshotForRom(romSha256: String): RomSnapshot {
        val canonical = NuzlockeStore.canonicalSha256(romSha256)
            ?: return RomSnapshot(null, null, 0L)
        return queueLock.withLock {
            RomSnapshot(
                state = latestStateByRom[canonical]?.state,
                lastError = lastErrorByRom[canonical]?.message,
                lastCompletedRevision = lastCompletedRevisionByRom[canonical] ?: 0L
            )
        }
    }

    fun lastCompletedRevisionForRom(romSha256: String): Long {
        val canonical = NuzlockeStore.canonicalSha256(romSha256) ?: return 0L
        return queueLock.withLock { lastCompletedRevisionByRom[canonical] ?: 0L }
    }

    fun awaitIdle(timeoutMs: Long = 5000L): Boolean {
        val deadlineNs = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs)
        queueLock.withLock {
            while (pendingQueue.isNotEmpty() || writing) {
                val remainingNs = deadlineNs - System.nanoTime()
                if (remainingNs <= 0L) return false
                idleCondition.awaitNanos(remainingNs)
            }
            return true
        }
    }

    private fun drainNext() {
        val req = queueLock.withLock {
            val next = pendingQueue.removeFirstOrNull() ?: run {
                if (!writing) idleCondition.signalAll()
                return
            }
            val lastAttempted = lastAttemptedRevisionByRom[next.romSha256] ?: 0L
            if (next.revision <= lastAttempted) {
                if (pendingQueue.isEmpty() && !writing) {
                    idleCondition.signalAll()
                }
                return
            }
            lastAttemptedRevisionByRom[next.romSha256] = next.revision
            writing = true
            next
        }

        try {
            val result = try {
                req.store.saveResult(req.romSha256, req.state)
            } catch (t: Throwable) {
                Result.failure(t)
            }

            queueLock.withLock {
                val prevCompleted = lastCompletedRevisionByRom[req.romSha256] ?: 0L
                if (req.revision > prevCompleted) {
                    lastCompletedRevisionByRom[req.romSha256] = req.revision
                }
                if (result.isSuccess) {
                    if (latestStateByRom[req.romSha256]?.revision == req.revision) {
                        latestStateByRom.remove(req.romSha256)
                    }
                    val errorRev = lastErrorByRom[req.romSha256]?.revision ?: 0L
                    if (req.revision >= errorRev) {
                        lastErrorByRom.remove(req.romSha256)
                    }
                } else {
                    val errorRev = lastErrorByRom[req.romSha256]?.revision ?: 0L
                    if (req.revision >= errorRev) {
                        lastErrorByRom[req.romSha256] = ErrorSnapshot(
                            revision = req.revision,
                            message = formatError(result.exceptionOrNull())
                        )
                    }
                }
            }

            // Publish completion callbacks outside queueLock before marking the queue idle so
            // awaitIdle() never returns before save errors are published.
            req.onComplete(req.romSha256, req.revision, result)
            notifyListeners(req.romSha256, req.revision, result)
        } finally {
            queueLock.withLock {
                writing = false
                if (pendingQueue.isEmpty()) {
                    idleCondition.signalAll()
                }
            }
        }
    }

    private fun notifyListeners(romSha256: String, revision: Long, result: Result<Unit>) {
        val active = synchronized(listenersLock) {
            val live = ArrayList<(String, Long, Result<Unit>) -> Unit>(listeners.size)
            val iter = listeners.iterator()
            while (iter.hasNext()) {
                val listener = iter.next().get()
                if (listener == null) {
                    iter.remove()
                } else {
                    live.add(listener)
                }
            }
            live
        }
        for (listener in active) {
            listener(romSha256, revision, result)
        }
    }

    companion object {
        private val sharedQueues = ConcurrentHashMap<File, NuzlockeWriteQueue>()

        fun sharedFor(store: NuzlockeStore): NuzlockeWriteQueue {
            val key = canonicalDir(store.directory)
            return sharedQueues.computeIfAbsent(key) { NuzlockeWriteQueue() }
        }

        internal fun formatError(error: Throwable?): String =
            error?.message?.takeIf { it.isNotBlank() } ?: "Failed to save Nuzlocke progress."

        private fun canonicalDir(dir: File): File =
            runCatching { dir.canonicalFile }.getOrElse { dir.absoluteFile }
    }
}
