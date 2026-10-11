package com.dualdex.emulator

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/** Physical state is retained; move macros exclusively own the effective input mask. */
object ControllerInputRouter {
    private val inputMutex = Mutex()
    private val automationMutex = Mutex()
    private val lock = Any()
    // Serialize writes separately: physical updates must not wait on a core-lock acquisition.
    private val dispatchLock = Any()
    @Volatile private var physicalMask = 0
    private var automatedMask = 0
    private var latchedMask = 0
    private var exclusive = false

    class PhysicalButtonHeld : RuntimeException()

    private fun effectiveMask() = if (exclusive) automatedMask
        else (physicalMask and latchedMask.inv()) or automatedMask

    fun setPhysicalMask(mask: Int) {
        synchronized(lock) {
            physicalMask = mask
            latchedMask = latchedMask and mask
            if (exclusive) return
        }
        synchronized(dispatchLock) {
            val effective = synchronized(lock) {
                if (exclusive) return
                effectiveMask()
            }
            dispatchEffectiveMask(effective)
        }
    }

    fun getPhysicalMask(): Int = physicalMask
    fun getEffectiveMask(): Int = synchronized(lock) { effectiveMask() }

    private fun dispatchEffectiveMask(mask: Int) {
        try {
            LibretroCoreCoordinator.defaultInstance.setInputButtons(mask)
        } catch (_: UnsatisfiedLinkError) {
            // Host JVM checks do not load the native library.
        }
    }

    private fun setAutomatedMask(mask: Int, setButtons: (Int) -> Unit) {
        synchronized(dispatchLock) {
            val effective = synchronized(lock) {
                automatedMask = mask
                effectiveMask()
            }
            setButtons(effective)
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
        setButtons: (Int) -> Unit = ::dispatchEffectiveMask
    ): Boolean = automationMutex.withLock {
        inputMutex.withLock {
            try {
                setAutomatedMask(buttonMask, setButtons)
                delay(durationMs)
            } finally {
                setAutomatedMask(0, setButtons)
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
                latchedMask = 0
            }
            dispatchEffectiveMask(0)
        }
    }
}
