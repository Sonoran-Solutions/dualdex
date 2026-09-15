package com.dualdex.emulator

import android.os.SystemClock
import android.util.Log
import com.dualdex.battle.ActiveEnemyResolution
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
 *
 * Scope of the lock: it serializes access to *core state*, i.e. anything that runs inside or
 * reads the emulated machine's memory. That is [stepFrame] plus every core mutation and every
 * protected memory reader below.
 *
 * It deliberately does NOT serialize the high-frequency framebuffer and audio-ring snapshots
 * ([getVideoFrame], [getAudioSamples]). Those buffers are owned natively and protected by
 * g_video_mutex / g_audio_mutex, and they hold no core state. Serializing them through the core
 * lock made the render thread lose frames whenever the core was busy; see [getVideoFrame].
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
        val diag = FastForwardDiagnostics.enabled
        val waitStart = if (diag) SystemClock.elapsedRealtimeNanos() else 0L
        val acquired = if (timeoutMs > 0) {
            lock.tryLock(timeoutMs, TimeUnit.MILLISECONDS)
        } else {
            lock.tryLock()
        }
        if (!acquired) {
            if (diag) FastForwardDiagnostics.onCompanionTimeout()
            val msg = "Timed out after ${timeoutMs}ms waiting for Libretro core exclusive lock"
            Log.e(TAG, msg)
            throw IllegalStateException(msg)
        }
        val acquiredNs = if (diag) SystemClock.elapsedRealtimeNanos() else 0L
        try {
            return block()
        } finally {
            if (diag) {
                FastForwardDiagnostics.onCompanionRead(
                    waitNs = acquiredNs - waitStart,
                    holdNs = SystemClock.elapsedRealtimeNanos() - acquiredNs
                )
            }
            lock.unlock()
        }
    }

    /**
     * Advances the emulation core by one frame.
     * Called by EmulatorSurfaceView at display refresh rate (~60fps).
     * Yields cleanly if an exclusive operation (save/load/switch) holds the core lock.
     */
    fun stepFrame(): Boolean {
        val diag = FastForwardDiagnostics.enabled
        val waitStart = if (diag) SystemClock.elapsedRealtimeNanos() else 0L
        try {
            lock.lockInterruptibly()
        } catch (_: InterruptedException) {
            return false
        }
        val acquiredNs = if (diag) SystemClock.elapsedRealtimeNanos() else 0L
        try {
            if (bridge != null) {
                return bridge?.stepFrame() ?: true
            }
            LibretroHost.nativeStepFrame()
            return true
        } finally {
            if (diag) {
                FastForwardDiagnostics.noteStepLock(
                    waitNs = acquiredNs - waitStart,
                    holdNs = SystemClock.elapsedRealtimeNanos() - acquiredNs
                )
            }
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

    /**
     * Copies the latest native framebuffer snapshot into [directBuffer].
     *
     * Deliberately NOT serialized by the coordinator lock. The framebuffer snapshot is owned
     * and protected natively by g_video_mutex: the video refresh callback takes it while
     * writing the buffer, and libretro_host_copy_video_frame() takes it while reading it, so
     * the copy can never observe a torn frame.
     *
     * Routing this through the frame lock was measurably harmful. stepFrame() holds the
     * coordinator for the whole duration of retro_run (2.5-4.3 ms per frame at 2x on an AYN
     * Thor), which is 30-50% of wall-clock time at fast-forward. Because this call is untimed
     * tryLock(), the GL render thread (120 Hz on this device) silently failed 22-33% of its
     * attempts and skipped the texture upload, so the top screen dropped updates exactly while
     * the game was moving and the core was working hardest.
     */
    fun getVideoFrame(directBuffer: ByteBuffer, outMetadata: IntArray): Boolean {
        val diag = FastForwardDiagnostics.enabled
        val start = if (diag) SystemClock.elapsedRealtimeNanos() else 0L
        val ok = LibretroHost.nativeGetVideoFrame(directBuffer, outMetadata)
        if (diag) {
            FastForwardDiagnostics.onVideoRead(
                waitNs = 0L,
                holdNs = SystemClock.elapsedRealtimeNanos() - start,
                hasFrame = ok
            )
        }
        return ok
    }

    /**
     * Drains resampled PCM from the native audio ring.
     *
     * Deliberately NOT serialized by the coordinator lock, for the same reason as
     * [getVideoFrame]: the ring buffer is owned and protected natively by g_audio_mutex, which
     * both the core's audio batch callback and this reader take. The reader only ever touches
     * the ring, never core memory, so overlapping it with retro_run is safe.
     */
    fun getAudioSamples(outBuffer: ShortArray): Int {
        val diag = FastForwardDiagnostics.enabled
        val start = if (diag) SystemClock.elapsedRealtimeNanos() else 0L
        val n = LibretroHost.nativeGetAudioSamples(outBuffer)
        if (diag) {
            FastForwardDiagnostics.onAudioRead(
                waitNs = 0L,
                holdNs = SystemClock.elapsedRealtimeNanos() - start,
                samples = n
            )
        }
        return n
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
    open fun readPartyFromCore(gameId: Int): Array<ParsedPokemon>? = try {
        executeExclusive(50L) { LibretroHost.nativeReadPartyFromCore(gameId) }
    } catch (_: UnsatisfiedLinkError) {
        null
    } catch (_: Exception) {
        null
    }

    open fun readEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>? = try {
        executeExclusive(50L) { LibretroHost.nativeReadEnemyPartyFromCore(gameId) }
    } catch (_: UnsatisfiedLinkError) {
        null
    } catch (_: Exception) {
        null
    }

    open fun getActiveBattlerSlot(gameId: Int): Int = try {
        executeExclusive(50L) { LibretroHost.nativeGetActiveBattlerSlot(gameId) }
    } catch (_: UnsatisfiedLinkError) {
        -1
    } catch (_: Exception) {
        -1
    }

    open fun getActiveEnemyBattlerSlot(gameId: Int): Int = try {
        executeExclusive(50L) { LibretroHost.nativeGetActiveEnemyBattlerSlot(gameId) }
    } catch (_: UnsatisfiedLinkError) {
        -1
    } catch (_: Exception) {
        -1
    }

    /**
     * Authoritative active-opponent resolution.
     *
     * One native call returns the state, battler index, party slot, opponent battler count and
     * faint flag together, so the state and the slot can never disagree. A failure, an
     * unsupported layout or a missing native library degrades to [ActiveEnemyResolution]'s
     * default, which is UNKNOWN with no slot -- never "slot 0".
     */
    open fun resolveActiveEnemy(gameId: Int): ActiveEnemyResolution = try {
        ActiveEnemyResolution.fromNativeArray(
            executeExclusive(50L) { LibretroHost.nativeResolveActiveEnemy(gameId) }
        )
    } catch (_: UnsatisfiedLinkError) {
        ActiveEnemyResolution()
    } catch (_: Exception) {
        ActiveEnemyResolution()
    }

    open fun readBattleStatStages(gameId: Int, battlerIndex: Int): IntArray? = try {
        executeExclusive(50L) { LibretroHost.nativeReadBattleStatStages(gameId, battlerIndex) }
    } catch (_: UnsatisfiedLinkError) {
        null
    } catch (_: Exception) {
        null
    }

    open fun readBattleUiState(gameId: Int): Int = try {
        executeExclusive(50L) { LibretroHost.nativeReadBattleUiState(gameId) }
    } catch (_: UnsatisfiedLinkError) {
        0
    } catch (_: Exception) {
        0
    }

    open fun readBattlePresence(gameId: Int): Int = try {
        executeExclusive(50L) { LibretroHost.nativeReadBattlePresence(gameId) }
    } catch (_: UnsatisfiedLinkError) {
        2
    } catch (_: Exception) {
        2
    }

    open fun readPlayerLocation(gameId: Int): PlayerLocation? = try {
        executeExclusive(50L) { LibretroHost.nativeReadPlayerLocation(gameId) }
    } catch (_: UnsatisfiedLinkError) {
        null
    } catch (_: Exception) {
        null
    }
}
