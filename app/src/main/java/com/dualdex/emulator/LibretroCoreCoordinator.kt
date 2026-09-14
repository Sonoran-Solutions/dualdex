package com.dualdex.emulator

import android.util.Log
import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation
import java.nio.ByteBuffer
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

/**
 * Single-owner execution and synchronization coordinator for the native Libretro core.
 *
 * Ensures mutual exclusion between the high-frequency emulation loop (retro_run / nativeStepFrame)
 * and mutating operations (save, load, flush, import, unload, switch ROM, reset, cheat changes)
 * invoked from background coroutines or lifecycle callbacks.
 *
 * Employs a fair ReentrantLock to prevent thread starvation and guarantees that composite
 * transactions (e.g. ROM switch, SRAM backup and commit) execute atomically with respect to the core.
 */
open class LibretroCoreCoordinator(
    var bridge: LibretroCoreBridge? = null,
    val lock: ReentrantLock = ReentrantLock(true)
) {
    companion object {
        private const val TAG = "LibretroCoordinator"

        @JvmStatic
        val defaultInstance = LibretroCoreCoordinator()
    }

    val isExclusiveLocked: Boolean
        get() = lock.isLocked

    /**
     * Executes an exclusive operation with respect to the Libretro core.
     * While held, frame execution (nativeStepFrame) and other operations yield.
     * Supports reentrant execution on the same thread without self-deadlock.
     */
    fun <T> executeExclusive(timeoutMs: Long = 5000L, block: () -> T): T {
        val acquired = if (timeoutMs > 0) {
            lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)
        } else {
            lock.tryLock()
        }
        if (!acquired) {
            val msg = "Timed out after ${timeoutMs}ms waiting for Libretro core exclusive lock"
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }
        try {
            return block()
        } finally {
            lock.unlock()
        }
    }

    /**
     * Advances the emulation core by one frame.
     * Called by EmulatorSurfaceView at display refresh rate (~60fps).
     * Yields cleanly if an exclusive operation (save/load/switch) holds the core lock.
     */
    fun stepFrame(): Boolean {
        try {
            lock.lockInterruptibly()
        } catch (_: InterruptedException) {
            return false
        }
        try {
            if (bridge != null) {
                return bridge?.stepFrame() ?: true
            }
            LibretroHost.nativeStepFrame()
            return true
        } finally {
            lock.unlock()
        }
    }

    fun loadCore(coreLibPath: String): Boolean = executeExclusive {
        try {
            LibretroHost.nativeLoadCore(coreLibPath)
        } catch (_: UnsatisfiedLinkError) {
            true
        }
    }

    fun loadRom(romPath: String): Boolean = executeExclusive {
        bridge?.loadRom(romPath) ?: try {
            LibretroHost.nativeLoadRom(romPath)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun unloadRom(): Boolean = executeExclusive {
        bridge?.unloadRom() ?: try {
            LibretroHost.nativeUnloadRom()
        } catch (_: UnsatisfiedLinkError) {
            true
        }
    }

    fun getSaveRamSize(): Long = executeExclusive {
        bridge?.getSaveRamSize() ?: try {
            LibretroHost.nativeGetSaveRamSize()
        } catch (_: UnsatisfiedLinkError) {
            0L
        }
    }

    fun getSaveStateSize(): Long = executeExclusive {
        bridge?.getSaveStateSize() ?: try {
            LibretroHost.nativeGetSaveStateSize()
        } catch (_: UnsatisfiedLinkError) {
            0L
        }
    }

    fun saveState(statePath: String): Boolean = executeExclusive {
        bridge?.saveState(statePath) ?: try {
            LibretroHost.nativeSaveState(statePath)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun loadState(statePath: String): Boolean = executeExclusive {
        bridge?.loadState(statePath) ?: try {
            LibretroHost.nativeLoadState(statePath)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun loadSaveRam(savePath: String): Boolean = executeExclusive {
        bridge?.loadSaveRam(savePath) ?: try {
            LibretroHost.nativeLoadSaveRam(savePath)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun flushSaveRam(savePath: String): Boolean = executeExclusive {
        bridge?.flushSaveRam(savePath) ?: try {
            LibretroHost.nativeFlushSaveRam(savePath)
        } catch (_: UnsatisfiedLinkError) {
            false
        }
    }

    fun resetCore(): Unit = executeExclusive {
        if (bridge != null) {
            bridge?.resetCore()
        } else {
            try {
                LibretroHost.nativeResetCore()
            } catch (_: UnsatisfiedLinkError) {}
        }
    }

    fun clearAudio(): Unit = executeExclusive {
        if (bridge == null) {
            try {
                LibretroHost.nativeClearAudio()
            } catch (_: UnsatisfiedLinkError) {}
        }
    }

    fun cleanup(): Unit = executeExclusive {
        if (bridge == null) {
            try {
                LibretroHost.nativeCleanup()
            } catch (_: UnsatisfiedLinkError) {}
        }
    }

    fun cheatReset(): Unit = executeExclusive {
        if (bridge != null) {
            bridge?.cheatReset()
        } else {
            try {
                LibretroHost.nativeCheatReset()
            } catch (_: UnsatisfiedLinkError) {}
        }
    }

    fun cheatSet(index: Int, enabled: Boolean, code: String): Unit = executeExclusive {
        if (bridge != null) {
            bridge?.cheatSet(index, enabled, code)
        } else {
            try {
                LibretroHost.nativeCheatSet(index, enabled, code)
            } catch (_: UnsatisfiedLinkError) {}
        }
    }

    fun getVideoFrame(directBuffer: ByteBuffer, outMetadata: IntArray): Boolean {
        if (!lock.tryLock()) return false
        try {
            return LibretroHost.nativeGetVideoFrame(directBuffer, outMetadata)
        } finally {
            lock.unlock()
        }
    }

    fun getAudioSamples(outBuffer: ShortArray): Int {
        if (!lock.tryLock()) return 0
        try {
            return LibretroHost.nativeGetAudioSamples(outBuffer)
        } finally {
            lock.unlock()
        }
    }

    fun setInputButtons(buttonMask: Int) {
        LibretroHost.nativeSetInputButtons(buttonMask)
    }

    fun setTargetAudioSampleRate(rate: Int) {
        LibretroHost.nativeSetTargetAudioSampleRate(rate)
    }

    fun getOutputAudioSampleRate(): Int = LibretroHost.nativeGetOutputAudioSampleRate()

    fun getTargetFps(): Double = LibretroHost.nativeGetTargetFps()

    fun getAudioSampleRate(): Double = LibretroHost.nativeGetAudioSampleRate()

    // Protected memory reader access
    fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? = try {
        executeExclusive(50L) { LibretroHost.nativeReadPartyFromCore(gameId) }
    } catch (_: Exception) {
        null
    }

    fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? = try {
        executeExclusive(50L) { LibretroHost.nativeReadEnemyPartyFromCore(gameId) }
    } catch (_: Exception) {
        null
    }

    fun getActiveBattlerSlot(gameId: Int): Int = try {
        executeExclusive(50L) { LibretroHost.nativeGetActiveBattlerSlot(gameId) }
    } catch (_: Exception) {
        -1
    }

    fun getActiveEnemyBattlerSlot(gameId: Int): Int = try {
        executeExclusive(50L) { LibretroHost.nativeGetActiveEnemyBattlerSlot(gameId) }
    } catch (_: Exception) {
        -1
    }

    fun readBattleStatStages(gameId: Int, battlerIndex: Int): IntArray? = try {
        executeExclusive(50L) { LibretroHost.nativeReadBattleStatStages(gameId, battlerIndex) }
    } catch (_: Exception) {
        null
    }

    fun readBattleUiState(gameId: Int): Int = try {
        executeExclusive(50L) { LibretroHost.nativeReadBattleUiState(gameId) }
    } catch (_: Exception) {
        0
    }

    fun readBattlePresence(gameId: Int): Int = try {
        executeExclusive(50L) { LibretroHost.nativeReadBattlePresence(gameId) }
    } catch (_: Exception) {
        2
    }

    fun readPlayerLocation(gameId: Int): PlayerLocation? = try {
        executeExclusive(50L) { LibretroHost.nativeReadPlayerLocation(gameId) }
    } catch (_: Exception) {
        null
    }
}
