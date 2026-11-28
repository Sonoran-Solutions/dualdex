package com.dualdex.emulator

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

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
    private var latchedMask = 0
    private var exclusive = false

    internal var coordinatorProvider: () -> LibretroCoreCoordinator = {
        LibretroCoreCoordinator.defaultInstance
    }

    class PhysicalButtonHeld : RuntimeException()

    private fun physicalEffectiveMask() = if (exclusive) 0 else (physicalMask and latchedMask.inv())

    private fun effectiveMask() = physicalEffectiveMask() or automatedMask

    /**
     * Clears any active automated bits belonging to [coordinator] (or unbound) and returns the
     * current effective physical mask so the caller can preserve physical buttons while clearing
     * revoked automated presses under the core lock.
     */
    internal fun clearAutomatedMaskFor(coordinator: LibretroCoreCoordinator): Int = synchronized(lock) {
        if (automatedCoordinator == null || automatedCoordinator === coordinator) {
            automatedMask = 0
            automatedGeneration = null
            automatedCoordinator = null
        }
        physicalEffectiveMask()
    }

    private fun clearAutomatedMaskIfMatches(
        coordinator: LibretroCoreCoordinator,
        expectedGeneration: Long?
    ) {
        synchronized(lock) {
            if ((automatedCoordinator == null || automatedCoordinator === coordinator) &&
                automatedGeneration == expectedGeneration
            ) {
                automatedMask = 0
                automatedGeneration = null
                automatedCoordinator = null
            }
        }
    }

    fun setPhysicalMask(mask: Int) {
        synchronized(lock) {
            physicalMask = mask
            latchedMask = latchedMask and mask
            if (exclusive) return
        }
        synchronized(dispatchLock) {
            val physical: Int
            val automated: Int
            val generation: Long?
            val targetCore: LibretroCoreCoordinator
            synchronized(lock) {
                if (exclusive) return
                physical = physicalEffectiveMask()
                automated = automatedMask
                generation = automatedGeneration
                targetCore = automatedCoordinator ?: coordinatorProvider()
            }
            dispatchCombinedMask(targetCore, physical, automated, generation)
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
        physical: Int,
        automated: Int,
        generation: Long?
    ) {
        if (automated == 0) {
            try {
                coordinator.setInputButtons(physical)
            } catch (_: UnsatisfiedLinkError) {
                // Host JVM checks do not load the native library.
            }
            return
        }
        val allowed = try {
            coordinator.executeExclusive(COMBINED_LOCK_TIMEOUT_MS) {
                coordinator.setCombinedInputLocked(physical, automated, generation)
            }
        } catch (_: LibretroCoreCoordinator.LockTimeoutException) {
            // Never write unvalidated automated bits when core lock is contended; preserve physical input.
            try {
                coordinator.setInputButtons(physical)
            } catch (_: UnsatisfiedLinkError) {}
            false
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
            val effective = synchronized(lock) {
                automatedMask = mask
                if (mask == 0) {
                    automatedGeneration = null
                    automatedCoordinator = null
                }
                effectiveMask()
            }
            setButtons(effective)
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
            synchronized(lock) {
                automatedMask = mask
                automatedGeneration = if (mask != 0) expectedGeneration else null
                automatedCoordinator = if (mask != 0) coordinator else null
                physical = physicalEffectiveMask()
            }
            setCombinedButtons(physical, mask)
        }
    }

    suspend fun <T> withExclusiveAutomation(
        setButtons: (Int) -> Unit = ::dispatchEffectiveMask,
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
                val effective = synchronized(lock) {
                    automatedMask = 0
                    automatedGeneration = null
                    automatedCoordinator = null
                    latchedMask = latchedMask or physicalMask
                    exclusive = false
                    effectiveMask()
                }
                // Restore physical routing, but held buttons need release/repress for JOY_NEW.
                setButtons(effective)
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
                    setAutomatedState(buttonMask, generation, core) { physical, automated ->
                        dispatchCombinedMask(core, physical, automated, generation)
                    }
                } else {
                    setAutomatedMask(buttonMask, setButtons)
                }
                delay(durationMs)
            } finally {
                if (useDefaultDispatch) {
                    setAutomatedState(0, null, null) { physical, _ ->
                        dispatchCombinedMask(core, physical, 0, null)
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
                setAutomatedState(0, null, null, setCombinedButtons)
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
        setButtons: (Int) -> Unit = ::dispatchEffectiveMask
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
        synchronized(dispatchLock) {
            synchronized(lock) {
                physicalMask = 0
                automatedMask = 0
                automatedGeneration = null
                automatedCoordinator = null
                latchedMask = 0
                exclusive = false
            }
            dispatchEffectiveMask(0)
        }
    }
}
