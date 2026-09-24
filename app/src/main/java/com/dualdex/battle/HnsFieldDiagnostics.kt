package com.dualdex.battle

import android.util.Log
import com.dualdex.calculator.CalcCapabilityPolicy
import com.dualdex.pokemon.hns.BattlerRuntimeObservation
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.pokemon.hns.HnsFieldState
import com.dualdex.romhack.RomHackProfile
import com.dualdex.romhack.RuntimeRomTrust

/**
 * Makes the live H&S `gFieldStatuses` word observable on device: the Battle tab status row and a
 * debug log line whenever the observed word changes. Presentation only; the calculator authority is
 * the boundary-owned word in `CalcHnsLiveBattleState`.
 */
object HnsFieldDiagnostics {
    private const val TAG = "DualDexHnsField"

    @Volatile
    private var lastLogged: String? = null

    /**
     * The battle-global field word both observations read and agree on, decoded without masking, or
     * null when the build is not the exact H&S ROM or either observation did not read the word.
     */
    fun observedFieldState(
        profile: RomHackProfile,
        trust: RuntimeRomTrust?,
        player: BattlerRuntimeObservation?,
        enemy: BattlerRuntimeObservation?
    ): HnsFieldState? {
        if (!CalcCapabilityPolicy.isExactRuntimeVerified(profile, trust)) return null
        val p = player?.state ?: return null
        val e = enemy?.state ?: return null
        if (p.status != HnsBattlerRuntimeStatus.OBSERVED || e.status != HnsBattlerRuntimeStatus.OBSERVED) return null
        if (!p.fieldStatusesReadable || !e.fieldStatusesReadable || p.fieldStatuses != e.fieldStatuses) return null
        return HnsFieldState.decode(p.fieldStatuses)
    }

    /** Status-panel text, e.g. `Electric Terrain (0x00000100)`, `clear (0x00000000)` or `Unread`. */
    fun statusRow(state: HnsFieldState?): String = state?.describe() ?: "Unread"

    /** Logs the observed word once per change, so a device run records exactly what DualDex read. */
    fun record(state: HnsFieldState?) {
        val line = "H&S gFieldStatuses ${statusRow(state)}"
        if (line == lastLogged) return
        lastLogged = line
        Log.i(TAG, line)
    }
}
