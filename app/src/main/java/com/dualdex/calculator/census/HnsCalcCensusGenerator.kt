package com.dualdex.calculator.census

import com.dualdex.pokemon.GameDataPackRegistry
import java.io.File

/**
 * Runs the census and reads or writes its committed artifacts.
 *
 * Host-only. The app never calls this; `--check` is wired into `./ci.sh source-check` through
 * [com.dualdex.calculator.census.HnsCalcCensusTest].
 */
object HnsCalcCensusGenerator {

    const val INVENTORY_RELATIVE_PATH: String = "tools/hns-calc-census/trainer_inventory.json"
    const val FIXTURE_RELATIVE_PATH: String =
        "tools/hns-calc-census/fixtures/reference_teams.json"
    /**
     * The machine-readable artifact is gzip-compressed.
     *
     * It carries one row per evaluated request (~20 000) plus the ranked tables, which is 17 MB of
     * JSON text and ~2 MB gzipped. The compression is byte-deterministic: fixed level, a fixed
     * zero mtime, no filename, and the JDK's default OS byte. `--check` compares the raw bytes, so
     * a stale artifact still fails loudly.
     */
    const val JSON_RELATIVE_PATH: String = "tools/hns-calc-census/" +
        HnsCalcCensusReport.JSON_FILE_NAME + ".gz"
    const val DOC_RELATIVE_PATH: String = "docs/" + HnsCalcCensusReport.DOC_FILE_NAME

    /** The optional, uncommitted full detail dump (one row per evaluated policy decision). */
    const val DETAIL_RELATIVE_PATH: String = "tools/hns-calc-census/census-detail.json"

    /** Everything one derivation produced, rendered. */
    data class Artifacts(
        val json: String,
        val markdown: String,
        val detail: String,
        val run: HnsCalcCensusEngine.CensusRun
    )

    /**
     * One derivation per JVM. The derivation drives the production policy for every request and
     * every ability trial, so callers that need both the artifacts and the run must not pay for
     * it twice.
     */
    @Volatile
    private var cached: Pair<File, Artifacts>? = null

    fun deriveCached(root: File): Artifacts {
        cached?.let { if (it.first == root) return it.second }
        val artifacts = derive(root)
        cached = root to artifacts
        return artifacts
    }

    /** Locate the repository root from the JVM working directory. */
    fun repositoryRoot(start: File = File(System.getProperty("user.dir") ?: ".")): File {
        var candidate: File? = start.absoluteFile
        while (candidate != null) {
            if (File(candidate, "settings.gradle.kts").isFile &&
                File(candidate, "ci.sh").isFile
            ) {
                return candidate
            }
            candidate = candidate.parentFile
        }
        throw IllegalStateException("unable to locate the DualDex repository root from $start")
    }

    fun derive(root: File): Artifacts {
        val inventory = File(root, INVENTORY_RELATIVE_PATH)
        check(inventory.isFile) {
            "the committed trainer inventory is missing at ${inventory.path}; run " +
                "tools/hns-calc-census/generate_hns_trainer_census.py against the pinned checkout"
        }
        val fixture = File(root, FIXTURE_RELATIVE_PATH)
        check(fixture.isFile) { "the reference team fixture is missing at ${fixture.path}" }

        val profile = HnsCalcCensusBaseline.profile
        val pack = checkNotNull(GameDataPackRegistry.getForProfile(profile)) {
            "the census profile does not resolve to a data pack"
        }
        val trainers = HnsCalcCensusEngine.loadTrainerBattles(inventory)
        val leads = HnsCalcCensusEngine.loadReferenceLeads(fixture)
        val run = HnsCalcCensusEngine.run(pack, profile, trainers, leads)
        check(run.baselinePositiveControl.displayTier == HnsCensusResultTier.FULLY_MODELLED.wireName) {
            "the census baseline failed its positive control: " +
                "${run.baselinePositiveControl.limitations}"
        }
        return Artifacts(
            json = HnsCalcCensusReport.toJson(run),
            markdown = HnsCalcCensusReport.toMarkdown(run, HnsCalcCensusReport.JSON_FILE_NAME),
            detail = HnsCalcCensusReport.toDetailJson(run),
            run = run
        )
    }

    /**
     * Write the committed artifacts, plus the uncommitted detail dump when [full] is set.
     *
     * The detail dump is deliberately not committed: it is 20+ MB of purely derivative rows, and
     * `--check` never compares it. It exists so a reviewer can trace one ranked number back to one
     * exact policy decision without re-deriving anything.
     */
    fun write(root: File, artifacts: Artifacts, full: Boolean = false) {
        File(root, JSON_RELATIVE_PATH).apply { parentFile.mkdirs() }
            .writeBytes(gzipDeterministic(artifacts.json))
        File(root, DOC_RELATIVE_PATH).apply { parentFile.mkdirs() }.writeText(artifacts.markdown)
        if (full) {
            File(root, DETAIL_RELATIVE_PATH).apply { parentFile.mkdirs() }
                .writeText(artifacts.detail)
        }
    }

    /**
     * Compare the derived artifacts with the committed ones.
     *
     * Returns a human-readable explanation of the first difference, or null when they match. The
     * explanation names the file and the first differing line so a stale artifact is a one-line
     * fix rather than a hunt.
     */
    fun check(root: File, artifacts: Artifacts): String? {
        val problems = mutableListOf<String>()
        problems += compareBytes(File(root, JSON_RELATIVE_PATH), gzipDeterministic(artifacts.json))
        problems += compare(File(root, DOC_RELATIVE_PATH), artifacts.markdown)
        return if (problems.isEmpty()) null else problems.joinToString("\n")
    }

    /**
     * gzip with every non-deterministic input pinned: a fixed level, a zero modification time, no
     * original filename, and the JDK's default OS byte (which is asserted by the census test to
     * equal the value used when the artifact was committed).
     */
    fun gzipDeterministic(text: String): ByteArray {
        val raw = java.io.ByteArrayOutputStream()
        java.util.zip.GZIPOutputStream(raw, 64 * 1024).use { gzip ->
            gzip.write(text.toByteArray(Charsets.UTF_8))
        }
        val bytes = raw.toByteArray()
        check(bytes[9] == COMMITTED_GZIP_OS_BYTE) {
            "this platform's gzip writes OS byte ${bytes[9]}, but the census artifact is " +
                "committed with ${COMMITTED_GZIP_OS_BYTE}; refusing to write an artifact that " +
                "would not match on the platform that committed it"
        }
        // The gzip header records MTIME (bytes 4..7), XFL (byte 8) and the producing OS (byte 9).
        // None of the three is content, so all three are pinned; the payload is the JDK's default
        // DEFLATE output, which is deterministic for a fixed input and level.
        check(bytes[9] == COMMITTED_GZIP_OS_BYTE) {
            "this platform's gzip writes OS byte ${bytes[9]}, but the census artifact is committed " +
                "with ${COMMITTED_GZIP_OS_BYTE}; refusing to write an artifact that would not match"
        }
        for (index in 4..7) bytes[index] = 0
        bytes[8] = 0
        bytes[9] = COMMITTED_GZIP_OS_BYTE
        return bytes
    }

    /**
     * The gzip OS byte this repository commits.
     *
     * gzip's header records the producing OS (the JDK writes `255`, "unknown"), which would
     * otherwise make the artifact machine-dependent. The census writes this constant, and
     * [gzipDeterministic] refuses to write anything when the platform disagrees, so a differing
     * platform fails loudly instead of silently producing a different artifact.
     */
    const val COMMITTED_GZIP_OS_BYTE: Byte = 255.toByte()

    private fun compareBytes(file: File, expected: ByteArray): List<String> {
        if (!file.isFile) {
            return listOf(
                "${file.path} is missing; regenerate with " +
                    "./gradlew testDebugUnitTest -Pdualdex.census.generate=true"
            )
        }
        val committed = file.readBytes()
        if (committed.contentEquals(expected)) return emptyList()
        val committedText = runCatching { readGzip(committed) }.getOrNull()
        val expectedText = readGzip(expected)
        if (committedText != null && committedText != expectedText) {
            val left = committedText.split("\n")
            val right = expectedText.split("\n")
            for (index in 0 until maxOf(left.size, right.size)) {
                val a = left.getOrNull(index) ?: "<missing>"
                val b = right.getOrNull(index) ?: "<missing>"
                if (a != b) {
                    return listOf(
                        "${file.path} is stale: first difference at line ${index + 1}\n" +
                            "  committed: ${a.take(200)}\n" +
                            "  derived:   ${b.take(200)}\n" +
                            "  regenerate with ./gradlew testDebugUnitTest " +
                            "-Pdualdex.census.generate=true"
                    )
                }
            }
            return listOf("${file.path} differs only in gzip framing, not in content")
        }
        return listOf(
            "${file.path} is stale and could not be decoded for a line diff; regenerate with " +
                "./gradlew testDebugUnitTest -Pdualdex.census.generate=true"
        )
    }

    private fun readGzip(bytes: ByteArray): String =
        java.util.zip.GZIPInputStream(bytes.inputStream()).use {
            it.readBytes().toString(Charsets.UTF_8)
        }

    private fun compare(file: File, expected: String): List<String> {
        if (!file.isFile) {
            return listOf(
                "${file.path} is missing; regenerate with " +
                    "./gradlew testDebugUnitTest -Pdualdex.census.generate=true"
            )
        }
        val committed = file.readText()
        if (committed == expected) return emptyList()
        val committedLines = committed.split("\n")
        val expectedLines = expected.split("\n")
        for (index in 0 until maxOf(committedLines.size, expectedLines.size)) {
            val left = committedLines.getOrNull(index) ?: "<missing>"
            val right = expectedLines.getOrNull(index) ?: "<missing>"
            if (left != right) {
                return listOf(
                    "${file.path} is stale: first difference at line ${index + 1}\n" +
                        "  committed: ${left.take(200)}\n" +
                        "  derived:   ${right.take(200)}\n" +
                        "  regenerate with ./gradlew testDebugUnitTest " +
                        "-Pdualdex.census.generate=true"
                )
            }
        }
        return listOf("${file.path} differs only in trailing whitespace")
    }
}
