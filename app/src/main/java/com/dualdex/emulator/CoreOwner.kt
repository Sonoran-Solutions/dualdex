package com.dualdex.emulator

/**
 * Which activity owns the process-wide libretro core. The newest activity to start claims it;
 * an older one being torn down afterwards (finish/relaunch race, a stale instance) must not flush
 * SRAM from, or clean up, a core that now belongs to someone else.
 */
class CoreOwner {
    private var owner: Any? = null

    @Synchronized fun claim(token: Any) { owner = token }

    @Synchronized fun isOwner(token: Any): Boolean = owner === token

    /** Releases ownership and returns true only if [token] still held it. */
    @Synchronized fun release(token: Any): Boolean = (owner === token).also { if (it) owner = null }

    companion object {
        val process = CoreOwner()
    }
}
