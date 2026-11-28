package com.dualdex.emulator

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.Collections
import java.util.IdentityHashMap

/** Physical state is retained; move macros exclusively own the effective input mask. */
object ControllerInputRouter {
    private const val COMBINED_LOCK_TIMEOUT_MS = 17L

    private val inputMutex = Mutex()
    private val automationMutex = Mutex()
    private val lock = Any()
    // Serialize writes separately: physical updates must not wait on a core-lock acquisition
    // during exclusive macros, and leaf [lock] is never held across a core-lock wait.
    private val dispatchLock = Any()
    @Volatile private var physicalMask = 0
    private var automatedMask = 0
    private var automatedGeneration: Long? = null
    private var automatedCoordinator: LibretroCoreCoordinator? = null
    // Tracks cores whose native button mask may still hold an automated bit (including after
    // router automatedMask has been cleared to 0 while the release write is still acquiring
    // core.lock). Guarded exclusively by leaf [lock].
    private val dirtyAutomatedCores: MutableSet<LibretroCoreCoordinator> =
        Collections.newSetFromMap(IdentityHashMap())
    private var dirtyAutomatedAny = false
    private var latchedMask = 0
    private var exclusive = false

    internal var coordinatorProvider: () -> LibretroCoreCoordinator = {
        LibretroCoreCoordinator.defaultInstance
    }

    @Volatile internal var beforeAutomatedReleaseWriteHook: (() -> Unit)? = null
    @Volatile internal var beforeExclusiveTeardownWriteHook: (() -> Unit)? = null

    class PhysicalButtonHeld : RuntimeException()

    private fun physicalEffectiveMask() = if (exclusive) 0 else (physicalMask and latchedMask.inv())

    private fun effectiveMask() = physicalEffectiveMask() or automatedMask

    internal fun markAutomatedNativeDirty(coordinator: LibretroCoreCoordinator) {
        synchronized(lock) {
            dirtyAutomatedCores.add(coordinator)
        }
    }

    /**
     * Clears any active automated bits belonging to [coordinator] (or unbound) and, whenever
     * an automated mask was live OR a prior automated write/in-progress release has not yet
     * synced zero automated bits to native, immediately reconciles the native button mask to the
     * current effective physical mask inside leaf [lock]. Executing the native write under leaf
     * [lock] preserves `core.lock -> lock`, totally orders generation revocation and Hardcore
     * activation with physical input updates, and guarantees the first replacement frame never
     * observes stale automated bits even when a timed release already cleared [automatedMask].
     */
    internal fun revokeAutomatedMaskAndSync(coordinator: LibretroCoreCoordinator): Int = synchronized(lock) {
        val matches = automatedCoordinator == null || automatedCoordinator === coordinator
        val hadAutomated = matches && automatedMask != 0
        val needsNativeSync = hadAutomated || dirtyAutomatedAny || coordinator in dirtyAutomatedCores
        if (matches) {
            automatedMask = 0
            automatedGeneration = null
            automatedCoordinator = null
        }
        val physical = physicalEffectiveMask()
        if (needsNativeSync) {
            try {
                coordinator.setInputButtons(physical)
            } catch (_: UnsatisfiedLinkError) {
                // Host JVM checks do not load the native library.
            }
            dirtyAutomatedCores.remove(coordinator)
            if (dirtyAutomatedCores.isEmpty() && automatedMask == 0) {
                dirtyAutomatedAny = false
            }
        }
        physical
    }

    /**
     * Atomically reads the live effective physical mask under leaf [lock], optionally combines it
     * with [automatedMask], and writes the native button mask. Callers may hold [dispatchLock]
     * and/or [coordinator].lock; [lock] is always a leaf lock and never waits on any other lock.
     */
    internal fun writeLiveMaskFor(
        coordinator: LibretroCoreCoordinator,
        automatedMask: Int = 0,
        includeAutomated: Boolean = false
    ) {
        synchronized(lock) {
            val physical = physicalEffectiveMask()
            val effective = if (includeAutomated) physical or automatedMask else physical
            if (includeAutomated && automatedMask != 0) {
                dirtyAutomatedCores.add(coordinator)
            }
            try {
                coordinator.setInputButtons(effective)
            } catch (_: UnsatisfiedLinkError) {
                // Host JVM checks do not load the native library.
            }
            if (!includeAutomated || automatedMask == 0) {
                dirtyAutomatedCores.remove(coordinator)
                if (dirtyAutomatedCores.isEmpty() && this.automatedMask == 0) {
                    dirtyAutomatedAny = false
                }
            }
        }
    }

    private fun clearAutomatedMaskIfMatchesLocked(
        coordinator: LibretroCoreCoordinator,
        expectedGeneration: Long?
    ) {
        if ((automatedCoordinator == null || automatedCoordinator === coordinator) &&
            automatedGeneration == expectedGeneration
        ) {
            automatedMask = 0
            automatedGeneration = null
            automatedCoordinator = null
        }
    }

    private fun clearAutomatedMaskIfMatches(
        coordinator: LibretroCoreCoordinator,
        expectedGeneration: Long?
    ) {
        synchronized(lock) {
            clearAutomatedMaskIfMatchesLocked(coordinator, expectedGeneration)
        }
    }

    fun setPhysicalMask(mask: Int) {
        val needsAutomatedDispatch: Boolean
        synchronized(lock) {
            physicalMask = mask
            latchedMask = latchedMask and mask
            if (exclusive) return
            if (automatedMask == 0) {
                val targetCore = automatedCoordinator ?: coordinatorProvider()
                try {
                    targetCore.setInputButtons(physicalEffectiveMask())
                } catch (_: UnsatisfiedLinkError) {
                    // Host JVM checks do not load the native library.
                }
                dirtyAutomatedCores.remove(targetCore)
                return
            }
            needsAutomatedDispatch = true
        }
        if (needsAutomatedDispatch) {
            synchronized(dispatchLock) {
                val automated: Int
                val generation: Long?
                val targetCore: LibretroCoreCoordinator
                synchronized(lock) {
                    if (exclusive) return
                    automated = automatedMask
                    generation = automatedGeneration
                    targetCore = automatedCoordinator ?: coordinatorProvider()
                    if (automated == 0) {
                        try {
                            targetCore.setInputButtons(physicalEffectiveMask())
                        } catch (_: UnsatisfiedLinkError) {
                            // Host JVM checks do not load the native library.
                        }
                        dirtyAutomatedCores.remove(targetCore)
                        return
                    }
                }
                dispatchCombinedMask(targetCore, automated, generation)
            }
        }
    }

    fun getPhysicalMask(): Int = physicalMask
    fun getEffectiveMask(): Int = synchronized(lock) { effectiveMask() }

    private val defaultDispatch: (Int) -> Unit = ::dispatchEffectiveMask

    private fun dispatchEffectiveMask(mask: Int) {
        try {
            coordinatorProvider().setInputButtons(mask)
        } catch (_: UnsatisfiedLinkError) {
            // Host JVM checks do not load the native library.
        }
    }

    private fun dispatchCombinedMask(
        coordinator: LibretroCoreCoordinator,
        automated: Int,
        generation: Long?
    ) {
        if (automated == 0) {
            writeLiveMaskFor(coordinator)
            return
        }
        val allowed = try {
            coordinator.executeExclusive(COMBINED_LOCK_TIMEOUT_MS) {
                coordinator.setRoutedCombinedInputLocked(automated, generation)
            }
        } catch (_: LibretroCoreCoordinator.LockTimeoutException) {
            // Never write unvalidated automated bits when core lock is contended; preserve live physical input.
            synchronized(lock) {
                clearAutomatedMaskIfMatchesLocked(coordinator, generation)
                try {
                    coordinator.setInputButtons(physicalEffectiveMask())
                } catch (_: UnsatisfiedLinkError) {}
                dirtyAutomatedCores.remove(coordinator)
            }
            return
        } catch (_: UnsatisfiedLinkError) {
            // Host JVM checks without a native library cannot write native buttons.
            true
        }
        if (!allowed) {
            clearAutomatedMaskIfMatches(coordinator, generation)
        }
    }

    private fun setAutomatedMask(mask: Int, setButtons: (Int) -> Unit) {
        synchronized(dispatchLock) {
            val clearedAutomated: Boolean
            val effective = synchronized(lock) {
                clearedAutomated = mask == 0 && automatedMask != 0
                automatedMask = mask
                if (mask != 0) {
                    dirtyAutomatedAny = true
                } else {
                    automatedGeneration = null
                    automatedCoordinator = null
                }
                effectiveMask()
            }
            if (clearedAutomated) {
                beforeAutomatedReleaseWriteHook?.invoke()
            }
            setButtons(effective)
            if (mask == 0) {
                synchronized(lock) {
                    if (automatedMask == 0 && dirtyAutomatedCores.isEmpty()) {
                        dirtyAutomatedAny = false
                    }
                }
            }
        }
    }

    private fun setAutomatedState(
        mask: Int,
        expectedGeneration: Long?,
        coordinator: LibretroCoreCoordinator?,
        setCombinedButtons: (physicalMask: Int, automatedMask: Int) -> Unit
    ) {
        synchronized(dispatchLock) {
            val physical: Int
            val clearedAutomated: Boolean
            synchronized(lock) {
                clearedAutomated = mask == 0 && automatedMask != 0
                automatedMask = mask
                automatedGeneration = if (mask != 0) expectedGeneration else null
                automatedCoordinator = if (mask != 0) coordinator else null
                if (mask != 0) {
                    if (coordinator != null) {
                        dirtyAutomatedCores.add(coordinator)
                    } else {
                        dirtyAutomatedAny = true
                    }
                }
                physical = physicalEffectiveMask()
            }
            if (clearedAutomated) {
                beforeAutomatedReleaseWriteHook?.invoke()
            }
            setCombinedButtons(physical, mask)
            if (mask == 0) {
                synchronized(lock) {
                    if (automatedMask == 0) {
                        if (coordinator != null) {
                            dirtyAutomatedCores.remove(coordinator)
                        }
                        if (dirtyAutomatedCores.isEmpty()) {
                            dirtyAutomatedAny = false
                        }
                    }
                }
            }
        }
    }

    suspend fun <T> withExclusiveAutomation(
        setButtons: (Int) -> Unit = defaultDispatch,
        block: suspend () -> T
    ): T = automationMutex.withLock {
        synchronized(lock) {
            if (physicalMask != 0) throw PhysicalButtonHeld()
            exclusive = true
        }
        try {
            setAutomatedMask(0, setButtons)
            block()
        } finally {
            synchronized(dispatchLock) {
                synchronized(lock) {
                    automatedMask = 0
                    automatedGeneration = null
                    automatedCoordinator = null
                    latchedMask = latchedMask or physicalMask
                    exclusive = false
                }
                beforeExclusiveTeardownWriteHook?.invoke()
                // Restore physical routing, but held buttons need release/repress for JOY_NEW.
                // Read and apply the live physical mask under leaf [lock] so a fresh physical press
                // arriving after clearing [exclusive] is never erased by a stale zero snapshot.
                if (setButtons === defaultDispatch) {
                    writeLiveMaskFor(coordinatorProvider())
                } else {
                    setButtons(0)
                }
            }
        }
    }

    suspend fun sendAutomatedButton(
        buttonMask: Int,
        durationMs: Long,
        delayMs: Long,
        setButtons: (Int) -> Unit = defaultDispatch
    ): Boolean = automationMutex.withLock {
        inputMutex.withLock {
            val core = coordinatorProvider()
            val generation = core.stateGeneration
            val useDefaultDispatch = setButtons === defaultDispatch
            try {
                if (useDefaultDispatch) {
                    setAutomatedState(buttonMask, generation, core) { _, automated ->
                        dispatchCombinedMask(core, automated, generation)
                    }
                } else {
                    setAutomatedMask(buttonMask, setButtons)
                }
                delay(durationMs)
            } finally {
                if (useDefaultDispatch) {
                    setAutomatedState(0, null, core) { _, _ ->
                        dispatchCombinedMask(core, 0, null)
                    }
                } else {
                    setAutomatedMask(0, setButtons)
                }
            }
            if (delayMs > 0) delay(delayMs)
            true
        }
    }

    /**
     * Sends a timed automated button press bound to [coordinator] and [expectedGeneration].
     * Concurrent physical input updates validate the automated portion under [coordinator]'s lock
     * so revoked automated bits can never be reasserted during Hardcore or after a state change.
     */
    suspend fun sendAuthorizedAutomatedButton(
        buttonMask: Int,
        durationMs: Long,
        delayMs: Long,
        expectedGeneration: Long,
        coordinator: LibretroCoreCoordinator,
        setCombinedButtons: (physicalMask: Int, automatedMask: Int) -> Unit
    ): Boolean = automationMutex.withLock {
        inputMutex.withLock {
            try {
                setAutomatedState(buttonMask, expectedGeneration, coordinator, setCombinedButtons)
                delay(durationMs)
            } finally {
                setAutomatedState(0, null, coordinator, setCombinedButtons)
            }
            if (delayMs > 0) delay(delayMs)
            true
        }
    }

    /** Each edge spans a completed core frame. Cancellation always releases automation. */
    suspend fun sendAutomatedFrameButton(
        buttonMask: Int,
        settleFrames: Int,
        waitFrames: suspend (Int) -> Boolean,
        setButtons: (Int) -> Unit = defaultDispatch
    ): Boolean = inputMutex.withLock {
        if (synchronized(lock) { !exclusive && physicalMask and buttonMask != 0 }) return@withLock false
        try {
            setAutomatedMask(buttonMask, setButtons)
            if (!waitFrames(1)) return@withLock false
        } finally {
            setAutomatedMask(0, setButtons)
        }
        waitFrames(1 + settleFrames)
    }

    suspend fun <T> withRouterLock(action: suspend () -> T): T = inputMutex.withLock { action() }

    fun reset() {
        beforeAutomatedReleaseWriteHook = null
        beforeExclusiveTeardownWriteHook = null
        synchronized(dispatchLock) {
            synchronized(lock) {
                physicalMask = 0
                automatedMask = 0
                automatedGeneration = null
                automatedCoordinator = null
                dirtyAutomatedCores.clear()
                dirtyAutomatedAny = false
                latchedMask = 0
                exclusive = false
                dispatchEffectiveMask(0)
            }
        }
    }
}
