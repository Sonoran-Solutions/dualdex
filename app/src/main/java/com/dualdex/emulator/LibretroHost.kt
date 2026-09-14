package com.dualdex.emulator

import com.dualdex.pokemon.ParsedPokemon
import com.dualdex.pokemon.PlayerLocation
import java.nio.ByteBuffer

object LibretroHost {
    init {
        try {
            System.loadLibrary("dualdex_native")
        } catch (_: UnsatisfiedLinkError) {
            // Expected in host JVM unit tests where dualdex_native is built for Android targets
        }
    }

    external fun nativeLoadCore(coreLibPath: String): Boolean
    external fun nativeLoadRom(romPath: String): Boolean
    external fun nativeUnloadRom(): Boolean
    external fun nativeGetSaveRamSize(): Long
    external fun nativeGetSaveStateSize(): Long
    external fun nativeStepFrame()
    external fun nativeSetInputButtons(buttonMask: Int)
    external fun nativeGetVideoFrame(directBuffer: ByteBuffer, outMetadata: IntArray): Boolean
    external fun nativeGetAudioSamples(outBuffer: ShortArray): Int
    external fun nativeClearAudio()
    external fun nativeSetTargetAudioSampleRate(rate: Int)
    external fun nativeGetOutputAudioSampleRate(): Int
    external fun nativeReadPartyFromCore(gameId: Int): Array<ParsedPokemon>?
    external fun nativeReadEnemyPartyFromCore(gameId: Int): Array<ParsedPokemon>?
    external fun nativeGetActiveBattlerSlot(gameId: Int): Int
    external fun nativeGetActiveEnemyBattlerSlot(gameId: Int): Int
    external fun nativeReadBattleStatStages(gameId: Int, battlerIndex: Int): IntArray?
    external fun nativeReadBattleUiState(gameId: Int): Int
    external fun nativeReadPlayerLocation(gameId: Int): PlayerLocation?
    external fun nativeSaveState(statePath: String): Boolean
    external fun nativeLoadState(statePath: String): Boolean
    external fun nativeLoadSaveRam(savePath: String): Boolean
    external fun nativeFlushSaveRam(savePath: String): Boolean
    external fun nativeResetCore()
    external fun nativeGetTargetFps(): Double
    external fun nativeGetAudioSampleRate(): Double
    external fun nativeCheatReset()
    external fun nativeCheatSet(index: Int, enabled: Boolean, code: String)
    external fun nativeCleanup()
}
