package com.dualdex.emulator

interface LibretroCoreBridge {
    fun loadRom(romPath: String): Boolean = true
    fun unloadRom(): Boolean = true
    fun getSaveRamSize(): Long = 131072L
    fun getSaveStateSize(): Long = 262144L
    fun saveState(statePath: String): Boolean = true
    fun loadState(statePath: String): Boolean = true
    fun loadSaveRam(savePath: String): Boolean = true
    fun flushSaveRam(savePath: String): Boolean = true
    fun resetCore() {}
    fun stepFrame(): Boolean = true
    fun cheatReset() {}
    fun cheatSet(index: Int, enabled: Boolean, code: String) {}
}
