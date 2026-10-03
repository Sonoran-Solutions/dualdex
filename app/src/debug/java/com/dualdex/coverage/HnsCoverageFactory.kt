package com.dualdex.coverage

import android.content.Context
import android.content.Intent
import android.widget.LinearLayout
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.FileProvider
import com.dualdex.BuildInfo
import com.dualdex.companion.ui.DualDexComponents
import com.dualdex.battle.BattleHnsCalculationContext
import com.dualdex.calculator.CalcRequestOutcome
import com.dualdex.pokemon.*
import com.dualdex.pokemon.hns.HnsBattlerRuntimeStatus
import com.dualdex.romhack.RomHackProfile
import android.util.AtomicFile
import java.io.File
import java.util.concurrent.Executors

/** All disk, worker and sharing code lives exclusively in src/debug. */
object HnsCoverageFactory {
    private val debugLogger = DebugHnsCalcCoverageLogger()
    fun create(): HnsCalcCoverageLogger = debugLogger
    fun initialize(context: Context) {
        if (BuildInfo.isDebuggable) debugLogger.initialize(context.applicationContext)
    }
    fun addSettingsActions(container: LinearLayout) {
        container.addView(DualDexComponents.secondaryButton(container.context, "Export H&S Coverage Log") {
            debugLogger.export(container.context)
        })
        container.addView(DualDexComponents.secondaryButton(container.context, "Clear H&S Coverage Log") {
            AlertDialog.Builder(container.context).setTitle("Clear H&S Coverage Log?")
                .setMessage("Delete all collected calculator playtest observations?")
                .setNegativeButton("Cancel", null).setPositiveButton("Clear") { _, _ -> debugLogger.clear(container.context) }.show()
        })
    }
}

internal class AtomicCoverageStore(file: File) : CoverageStore {
    private val atomic = AtomicFile(file)
    override fun read(): String? {
        val stream = try { atomic.openRead() } catch (_: java.io.FileNotFoundException) { return null }
        return stream.use {
            // openRead first restores a recoverable backup, if present.
            require(atomic.baseFile.length() <= 32 * 1024 * 1024)
            it.bufferedReader(Charsets.UTF_8).readText()
        }
    }
    override fun write(json: String) {
        atomic.baseFile.parentFile?.mkdirs()
        val stream = atomic.startWrite()
        try {
            stream.write(json.toByteArray(Charsets.UTF_8))
            atomic.finishWrite(stream)
        } catch (e: Exception) { atomic.failWrite(stream); throw e }
    }
}

internal class DebugHnsCalcCoverageLogger(
    private var repository: CoverageRepository? = null,
    private var worker: java.util.concurrent.ExecutorService? = null
) : HnsCalcCoverageLogger {
    // One worker serializes lifecycle, observations, persistence, clear and export snapshots.
    private var epoch = 0L
    private var active = false
    private var activeEpoch: Long? = null
    private var flushQueued = false // Accessed only by the serialized worker.

    @Synchronized fun initialize(context: Context) {
        if (worker != null) return
        worker = Executors.newSingleThreadExecutor()
        worker?.execute {
            repository = CoverageRepository(AtomicCoverageStore(File(context.filesDir, "hns-coverage/log.json")))
        }
    }
    @Synchronized override fun battle(active: Boolean) {
        if (active == this.active) return
        this.active = active
        activeEpoch = if (active) ++epoch else null
        worker?.execute { repository?.battle(active) }
    }
    @Synchronized override fun sessionToken(): Long? = activeEpoch

    @Synchronized override fun record(move: MoveInfo, defender: ParsedPokemon, profile: RomHackProfile,
                        context: BattleHnsCalculationContext, outcome: CalcRequestOutcome) {
        if (!active || context.coverageSession == null || context.coverageSession != activeEpoch) return
        if (GameDataPackRegistry.getForProfile(profile).id != "hns_2_0_5") return
        val pack = GameDataPackRegistry.getForProfile(profile)
        fun participant(mon: ParsedPokemon?, slot: Int?, state: com.dualdex.pokemon.hns.BattlerRuntimeObservation?): CoverageParticipant {
            val observed = state?.state?.takeIf { it.status == HnsBattlerRuntimeStatus.OBSERVED && it.partySlot == slot }
            val id = observed?.speciesId ?: mon?.species
            return CoverageParticipant(id?.takeIf { pack.isSpeciesAuthoritative(it) },
                id?.let { pack.getSpecies(it)?.name }.orEmpty(), slot, observed?.battlerIndex)
        }
        val matchup = CoverageMatchup(participant(context.playerParty.getOrNull(context.activePlayerSlot),
            context.activePlayerSlot, context.playerBattlerState), participant(defender, context.activeEnemySlot, context.enemyBattlerState),
            move.id.takeIf { pack.isMoveAuthoritative(it) }, move.name)
        val p = context.playerBattlerState?.state
        val e = context.enemyBattlerState?.state
        val format = if (p?.status == HnsBattlerRuntimeStatus.OBSERVED && e?.status == HnsBattlerRuntimeStatus.OBSERVED &&
            p.battlersCountReadable && e.battlersCountReadable &&
            p.battlersCount == e.battlersCount) when (p.battlersCount) {
                2 -> CoverageFormat.SINGLES; 4 -> CoverageFormat.DOUBLES; else -> CoverageFormat.UNKNOWN
            } else CoverageFormat.UNKNOWN
        val random = when (context.challengeSettings?.txRandomAbilities?.observedFlag) {
            true -> CoverageToggle.ON; false -> CoverageToggle.OFF; null -> CoverageToggle.UNKNOWN
        }
        // No authoritative trainer/wild operand is exposed by the current app reader.
        val metadata = CoverageContext(format, CoverageBattleKind.UNKNOWN, random)
        val observation = CoverageObservation.from(outcome)
        worker?.execute {
            runCatching {
                repository?.record(matchup, metadata, observation, persistNow = false)
                // One durable snapshot after a queued observation batch, instead of a whole-file
                // write for every move card. Neither serialization nor disk IO runs on Calc/UI.
                if (!flushQueued) {
                    flushQueued = true
                    worker?.execute { flushQueued = false; repository?.flush() }
                }
            }
        }
    }
    @Synchronized fun clear(context: Context) {
        worker?.execute {
            val result = runCatching { repository?.clear(); require(repository?.storageFailures == 0L) }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                Toast.makeText(context, if (result.isSuccess) "H&S coverage log cleared"
                    else "Unable to persist cleared coverage log", Toast.LENGTH_SHORT).show()
            }
        }
    }
    @Synchronized fun export(context: Context) {
        worker?.execute {
            val result = runCatching {
                repository?.flush()
                val json = repository?.export(BuildInfo.versionName) ?: error("Coverage store unavailable")
                val directory = File(context.cacheDir, "hns-coverage-export").apply { mkdirs() }
                val file = File(directory, "hns-coverage.json").apply { writeText(json, Charsets.UTF_8) }
                FileProvider.getUriForFile(context, "${BuildInfo.applicationId}.hnscoverage", file)
            }
            android.os.Handler(android.os.Looper.getMainLooper()).post {
                result.onSuccess { uri ->
                    runCatching {
                        val share = Intent(Intent.ACTION_SEND).setType("application/json")
                            .putExtra(Intent.EXTRA_STREAM, uri)
                            .apply { clipData = android.content.ClipData.newRawUri("H&S coverage", uri) }
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(share, "Export H&S Coverage Log"))
                    }.onFailure { Toast.makeText(context, "Unable to share coverage log", Toast.LENGTH_SHORT).show() }
                }.onFailure { Toast.makeText(context, "Unable to export coverage log", Toast.LENGTH_SHORT).show() }
            }
        }
    }
}
