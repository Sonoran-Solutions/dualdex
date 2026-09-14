package com.dualdex.emulator

import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Thread-safe controller input arbitration layer.
 *
 * Responsibilities:
 * - Owns the effective emulator input mask written to LibretroHost.nativeSetInputButtons()
 * - Tracks physical held-button state from physical gamepads/touch controls
 * - Tracks temporary automated button state from BattleInputAdapter
 * - Merges them atomically: effectiveMask = physicalMask OR automatedMask
 * - Serializes macros using a Mutex to prevent overlapping automated actions
 * - Never resets physical input when an automated press ends
 */
object ControllerInputRouter {

    private val inputMutex = Mutex()

    @Volatile
    private var physicalMask: Int = 0

    @Volatile
    private var automatedMask: Int = 0

    private val lock = Any()

    /**
     * Called by InputManager when physical or touch buttons change.
     */
    fun setPhysicalMask(mask: Int) {
        synchronized(lock) {
            physicalMask = mask
            updateEffectiveMask()
        }
    }

    /**
     * Get current physical button mask.
     */
    fun getPhysicalMask(): Int = physicalMask

    /**
     * Get current effective button mask.
     */
    fun getEffectiveMask(): Int = physicalMask or automatedMask

    /**
     * Sends an automated button press sequence while preserving any physically held buttons.
     * Serialized via Mutex so multiple automated macros cannot interleave.
     */
    private fun dispatchEffectiveMask(mask: Int) {
        try {
            LibretroHost.nativeSetInputButtons(mask)
        } catch (_: Throwable) {
            // Expected in host JVM unit tests where libretro native library is not loaded
        }
    }

    suspend fun sendAutomatedButton(
        buttonMask: Int,
        durationMs: Long,
        delayMs: Long,
        setButtons: (Int) -> Unit = { dispatchEffectiveMask(it) }
    ): Boolean {
        return inputMutex.withLock {
            synchronized(lock) {
                automatedMask = automatedMask or buttonMask
                val effective = physicalMask or automatedMask
                setButtons(effective)
            }

            delay(durationMs)

            synchronized(lock) {
                automatedMask = automatedMask and buttonMask.inv()
                val effective = physicalMask or automatedMask
                setButtons(effective)
            }

            if (delayMs > 0) {
                delay(delayMs)
            }
            true
        }
    }

    private fun updateEffectiveMask() {
        val effective = physicalMask or automatedMask
        dispatchEffectiveMask(effective)
    }

    /**
     * Executes an action holding the input router mutex lock.
     */
    suspend fun <T> withRouterLock(action: suspend () -> T): T {
        return inputMutex.withLock {
            action()
        }
    }

    /**
     * Reset router state (e.g. on game unload or pause).
     */
    fun reset() {
        synchronized(lock) {
            physicalMask = 0
            automatedMask = 0
            dispatchEffectiveMask(0)
        }
    }
}
