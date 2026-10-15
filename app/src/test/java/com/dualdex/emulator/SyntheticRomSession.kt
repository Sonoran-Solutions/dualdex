package com.dualdex.emulator

internal fun syntheticRomBridge() = object : LibretroCoreBridge {
    override fun loadRom(romPath: String): Boolean = true
}

internal fun bindSyntheticRom(core: LibretroCoreCoordinator, identity: RomIdentity) {
    check(core.loadVerifiedRom("synthetic:${identity.sha256}", identity))
}
