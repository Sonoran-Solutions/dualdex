package com.dualdex.nuzlocke

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock
import kotlin.concurrent.withLock

/**
 * Single-threaded serialized persistence queue for [NuzlockeRunState] snapshots.
 *
 * Guarantees:
 * - Writes execute off the UI thread in FIFO order on one dedicated daemon worker thread.
 * - Per-ROM monotonic revisions prevent older snapshots from ever overwriting newer ones.
 * - Switching ROMs never cancels or drops pending writes for previous ROMs.
 */
internal class NuzlockeWriteQueue {

    private data class WriteRequest(
        val store: NuzlockeStore,
        val romSha256: String,
        val revision: Long,
        val state: NuzlockeRunState,
        val onComplete: (String, Long, Result<Unit>) -> Unit
    )

    private val queueLock = ReentrantLock()
    private val idleCondition = queueLock.newCondition()
    private val pendingQueue = ArrayDeque<WriteRequest>()
    private val highestEnqueuedRevisionByRom = HashMap<String, Long>()
    private val lastAttemptedRevisionByRom = HashMap<String, Long>()
    private val pendingByRom = HashMap<String, WriteRequest>()
    private var writing = false

    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "DualDex-NuzlockeIO").apply { isDaemon = true }
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
            pendingByRom[canonical] = request
            pendingQueue.addLast(request)
        }
        executor.execute(::drainNext)
        return true
    }

    /**
     * Returns the latest un-flushed in-memory state for [romSha256] if a write is still queued
     * or in-flight, so a quick ROM switch back does not read stale state from disk.
     */
    fun latestPendingState(romSha256: String): NuzlockeRunState? {
        val canonical = NuzlockeStore.canonicalSha256(romSha256) ?: return null
        return queueLock.withLock { pendingByRom[canonical]?.state }
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
                if (pendingByRom[next.romSha256]?.revision == next.revision) {
                    pendingByRom.remove(next.romSha256)
                }
                if (pendingQueue.isEmpty() && !writing) {
                    idleCondition.signalAll()
                }
                return
            }
            lastAttemptedRevisionByRom[next.romSha256] = next.revision
            writing = true
            next
        }

        val result = try {
            req.store.saveResult(req.romSha256, req.state)
        } catch (t: Throwable) {
            Result.failure(t)
        }

        queueLock.withLock {
            if (pendingByRom[req.romSha256]?.revision == req.revision) {
                pendingByRom.remove(req.romSha256)
            }
            writing = false
            if (pendingQueue.isEmpty()) {
                idleCondition.signalAll()
            }
        }

        req.onComplete(req.romSha256, req.revision, result)
    }
}
