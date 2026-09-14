package com.dualdex.emulator

import android.app.Application
import android.content.pm.ApplicationInfo
import android.os.SystemClock
import android.util.Log
import java.util.concurrent.atomic.AtomicBoolean

/**
 * TEMPORARY DIAGNOSTIC INSTRUMENTATION for the AYN Thor fast-forward stall investigation.
 *
 * Everything here is debug-gated and aggregated: it never logs per frame, only a single
 * summary line approximately once per second, so that the act of measuring cannot itself
 * distort the timing being measured.
 *
 * Enable at runtime without rebuilding:
 *
 *     adb shell setprop debug.dualdex.ffdiag 1
 *     adb shell setprop debug.dualdex.ffdiag 0
 *
 * The property is sampled once per reporting window, so it can be toggled live.
 * This file is intended to be deleted (or left permanently disabled) once the
 * fast-forward regression is closed.
 */
object FastForwardDiagnostics {

    const val TAG = "DualDexFFDiag"
    private const val PROP = "debug.dualdex.ffdiag"
    private const val REPORT_INTERVAL_NS = 1_000_000_000L
    private const val NANOS_PER_MS = 1_000_000.0

    /**
     * Static so the JIT can fold the guards away entirely when diagnostics are off.
     * Only ever written by the emulation loop thread from a system property, which
     * cannot be set in a release build.
     */
    @JvmField
    @Volatile
    var enabled: Boolean = false

    private val started = AtomicBoolean(false)

    // ---- emulation loop: step + lock ----
    private var steps = 0L
    private var stepTotalNs = 0L
    private var stepMaxNs = 0L
    private var lockWaitTotalNs = 0L
    private var lockWaitMaxNs = 0L
    private var deadlineMisses = 0L
    private var deadlineResets = 0L
    private var latenessTotalNs = 0L
    private var latenessMaxNs = 0L
    private var lastResetFramesLost = 0L
    private var framesLostToReset = 0L

    // ---- audio ----
    private var audioReads = 0L
    private var audioMisses = 0L      // returned 0 samples
    private var audioSamples = 0L
    private var audioWaitTotalNs = 0L
    private var audioWaitMaxNs = 0L
    private var audioHoldTotalNs = 0L
    private var audioHoldMaxNs = 0L

    // ---- video ----
    private var videoReads = 0L
    private var videoMisses = 0L      // no frame available yet
    private var videoRefused = 0L     // lock held by the core -> GL frame dropped
    private var videoWaitTotalNs = 0L
    private var videoWaitMaxNs = 0L
    private var videoHoldTotalNs = 0L
    private var videoHoldMaxNs = 0L

    // ---- companion ----
    private var companionReads = 0L
    private var companionTimeouts = 0L  // executeExclusive timed out -> IllegalStateException caught
    private var companionWaitTotalNs = 0L
    private var companionWaitMaxNs = 0L
    private var companionHoldTotalNs = 0L
    private var companionHoldMaxNs = 0L

    private var windowStartNs = 0L
    private var speedSeen = 1

    // ---- frame pacing (the metric that actually corresponds to "bursty") ----
    private var frameIntervals = 0L
    private var intervalTotalNs = 0L
    private var intervalMaxNs = 0L
    private var wakeSamples = 0L
    private var wakeLateCount = 0L
    private var wakeLateTotalNs = 0L
    private var wakeMaxNs = 0L
    private var maxOvershootFrames = 0L
    private var lastTargetIntervalNs = 0L
    private var lastLoopStartNs = 0L

    // ------------------------------------------------------------------
    // Emulation loop
    // ------------------------------------------------------------------

    fun onEmulationLoopStart() {
        if (!enabled) return
        windowStartNs = SystemClock.elapsedRealtimeNanos()
        speedSeen = 1
    }

    /**
     * Reports a completed lock acquisition for one emulated frame: [waitNs] is how long the
     * caller sat queued before the lock was granted, [holdNs] is how long the core then ran
     * inside retro_run while holding it. Kept separate so contention can be told apart from
     * genuine core work.
     */
    fun noteStepLock(waitNs: Long, holdNs: Long) {
        if (!enabled) return
        steps++
        lockWaitTotalNs += waitNs
        if (waitNs > lockWaitMaxNs) lockWaitMaxNs = waitNs
        stepTotalNs += holdNs
        if (holdNs > stepMaxNs) stepMaxNs = holdNs
    }

    /**
     * Records the wall-clock interval between the start of consecutive frames. This is the
     * metric that actually corresponds to "bursty": a healthy fast-forward has intervals
     * tightly clustered around the target, while a stalling loop shows a long tail.
     * Uses nanoTime() so it shares the scheduler's own clock domain.
     */
    fun onLoopIteration(): Long {
        val now = System.nanoTime()
        if (!enabled) return now
        if (lastLoopStartNs != 0L) {
            val intervalNs = now - lastLoopStartNs
            frameIntervals++
            intervalTotalNs += intervalNs
            if (intervalNs > intervalMaxNs) intervalMaxNs = intervalMaxNs.coerceAtLeast(intervalNs)
            intervalHistogram[bucketOf(intervalNs)]++
        }
        lastLoopStartNs = now
        return now
    }

    /**
     * Frame-interval histogram, in milliseconds. A flat fast-forward concentrates almost
     * every interval just above the target; a bursty one shows a bimodal spread with a few
     * very long intervals mixed with very short catch-up intervals.
     */
    private val intervalEdgesMs = longArrayOf(
        500_000, 1_000_000, 2_000_000, 3_000_000, 4_000_000, 6_000_000,
        8_000_000, 12_000_000, 16_000_000, 24_000_000, 32_000_000, 50_000_000,
        100_000_000, Long.MAX_VALUE
    )
    private val intervalHistogram = LongArray(intervalEdgesMs.size)

    private fun bucketOf(intervalNs: Long): Int {
        for (i in intervalEdgesMs.indices) {
            if (intervalNs < intervalEdgesMs[i]) return i
        }
        return intervalEdgesMs.size - 1
    }

    private fun histogramText(): String {
        val sb = StringBuilder()
        for (i in intervalEdgesMs.indices) {
            if (intervalHistogram[i] == 0L) continue
            val lower = if (i == 0) 0L else intervalEdgesMs[i - 1]
            sb.append(if (sb.isEmpty()) "" else ",")
                .append(lower / 1_000_000).append('-')
                .append(if (intervalEdgesMs[i] == Long.MAX_VALUE) "inf" else (intervalEdgesMs[i] / 1_000_000).toString())
                .append("ms:").append(intervalHistogram[i])
        }
        return sb.toString()
    }

    /** Records the lateness with which the loop woke relative to its own deadline. */
    fun noteWakeLateness(latenessNs: Long) {
        if (!enabled) return
        wakeSamples++
        if (latenessNs > wakeMaxNs) wakeMaxNs = latenessNs
        if (latenessNs > 0) {
            wakeLateCount++
            wakeLateTotalNs += latenessNs
            // Overshoot expressed in frame intervals is what decides a deadline reset.
            val interval = lastTargetIntervalNs
            if (interval > 0) {
                val overshootFrames = latenessNs / interval
                if (overshootFrames > maxOvershootFrames) maxOvershootFrames = overshootFrames
            }
        }
    }

    fun setTargetInterval(intervalNs: Long) {
        lastTargetIntervalNs = intervalNs
    }

    fun onLeftOverDeadline(latenessNs: Long) {
        if (!enabled) return
        deadlineMisses++
        latenessTotalNs += latenessNs
        if (latenessNs > latenessMaxNs) latenessMaxNs = latenessNs
    }

    fun onDeadlineReset(framesBehind: Long) {
        if (!enabled) return
        deadlineResets++
        framesLostToReset += framesBehind
        lastResetFramesLost = framesBehind
    }

    // ------------------------------------------------------------------
    // Audio / video snapshot paths
    // ------------------------------------------------------------------

    fun onAudioRead(waitNs: Long, holdNs: Long, samples: Int) {
        if (!enabled) return
        audioReads++
        if (samples == 0) audioMisses++
        audioSamples += samples
        audioWaitTotalNs += waitNs
        if (waitNs > audioWaitMaxNs) audioWaitMaxNs = waitNs
        audioHoldTotalNs += holdNs
        if (holdNs > audioHoldMaxNs) audioHoldMaxNs = holdNs
    }

    fun onVideoRead(waitNs: Long, holdNs: Long, hasFrame: Boolean) {
        if (!enabled) return
        videoReads++
        if (!hasFrame) videoMisses++
        videoWaitTotalNs += waitNs
        if (waitNs > videoWaitMaxNs) videoWaitMaxNs = waitNs
        videoHoldTotalNs += holdNs
        if (holdNs > videoHoldMaxNs) videoHoldMaxNs = holdNs
    }

    fun onCompanionRead(waitNs: Long, holdNs: Long) {
        if (!enabled) return
        companionReads++
        companionWaitTotalNs += waitNs
        if (waitNs > companionWaitMaxNs) companionWaitMaxNs = waitNs
        companionHoldTotalNs += holdNs
        if (holdNs > companionHoldMaxNs) companionHoldMaxNs = holdNs
    }

    fun onVideoSkip() {
        if (!enabled) return
        videoRefused++
    }

    fun onCompanionTimeout() {
        if (!enabled) return
        companionTimeouts++
    }

    // ------------------------------------------------------------------
    // Reporting
    // ------------------------------------------------------------------

    /** Call once per emulation-loop iteration; emits at most one line per second. */
    fun tick() {
        if (!enabled) return
        val now = SystemClock.elapsedRealtimeNanos()
        val elapsed = now - windowStartNs
        if (elapsed < REPORT_INTERVAL_NS) return

        val seconds = elapsed / 1_000_000_000.0
        val fps = if (seconds > 0) steps / seconds else 0.0

        fun avg(total: Long, count: Long): Double =
            if (count > 0) total.toDouble() / count / NANOS_PER_MS else 0.0

        fun ms(ns: Long): Double = ns / NANOS_PER_MS

        val sb = StringBuilder(768)
        sb.append("speed=").append(speedSeen).append('x')
        sb.append(" win=").append(String.format("%.3f", seconds)).append("s")
        sb.append(" steps=").append(steps)
        sb.append(" effFPS=").append(String.format("%.1f", fps))
        sb.append(" | step avg=").append(String.format("%.2f", avg(stepTotalNs, steps)))
        sb.append(" max=").append(String.format("%.2f", ms(stepMaxNs)))
        sb.append(" lockWait avg=").append(String.format("%.2f", avg(lockWaitTotalNs, steps)))
        sb.append(" max=").append(String.format("%.2f", ms(lockWaitMaxNs)))
        sb.append(" | deadline miss=").append(deadlineMisses)
        sb.append(" reset=").append(deadlineResets)
        sb.append(" lostFrames=").append(framesLostToReset)
        sb.append(" late avg=").append(String.format("%.2f", avg(latenessTotalNs, deadlineMisses)))
        sb.append(" max=").append(String.format("%.2f", ms(latenessMaxNs)))
        // Frame pacing: the decisive "bursty" evidence.
        sb.append(" | pacing n=").append(frameIntervals)
        sb.append(" avg=").append(String.format("%.2f", avg(intervalTotalNs, frameIntervals)))
        sb.append(" max=").append(String.format("%.2f", ms(intervalMaxNs)))
        sb.append(" hist=[").append(histogramText()).append(']')
        sb.append(" wakeLate=").append(wakeLateCount).append('/').append(wakeSamples)
        sb.append(" wakeLateAvg=").append(String.format("%.2f", avg(wakeLateTotalNs, wakeLateCount)))
        sb.append(" wakeMax=").append(String.format("%.2f", ms(wakeMaxNs)))
        sb.append(" maxOvershootFrames=").append(maxOvershootFrames)
        sb.append(" | audio n=").append(audioReads)
        sb.append(" miss=").append(audioMisses)
        sb.append(" smp=").append(audioSamples)
        sb.append(" waitMax=").append(String.format("%.2f", ms(audioWaitMaxNs)))
        sb.append(" hold avg=").append(String.format("%.3f", avg(audioHoldTotalNs, audioReads)))
        sb.append(" max=").append(String.format("%.2f", ms(audioHoldMaxNs)))
        sb.append(" | video n=").append(videoReads)
        sb.append(" refused=").append(videoRefused)
        sb.append(" miss=").append(videoMisses)
        sb.append(" waitMax=").append(String.format("%.2f", ms(videoWaitMaxNs)))
        sb.append(" hold avg=").append(String.format("%.3f", avg(videoHoldTotalNs, videoReads)))
        sb.append(" max=").append(String.format("%.2f", ms(videoHoldMaxNs)))
        sb.append(" | companion n=").append(companionReads)
        sb.append(" timeout=").append(companionTimeouts)
        sb.append(" wait avg=").append(String.format("%.2f", avg(companionWaitTotalNs, companionReads)))
        sb.append(" max=").append(String.format("%.2f", ms(companionWaitMaxNs)))
        sb.append(" hold avg=").append(String.format("%.3f", avg(companionHoldTotalNs, companionReads)))
        sb.append(" max=").append(String.format("%.2f", ms(companionHoldMaxNs)))
        if (lastResetFramesLost > 0) sb.append(" lastResetLost=").append(lastResetFramesLost)

        Log.i(TAG, sb.toString())

        resetWindow(now)
    }

    private fun resetWindow(now: Long) {
        windowStartNs = now
        steps = 0; stepTotalNs = 0; stepMaxNs = 0
        lockWaitTotalNs = 0; lockWaitMaxNs = 0
        deadlineMisses = 0; deadlineResets = 0
        latenessTotalNs = 0; latenessMaxNs = 0
        framesLostToReset = 0; lastResetFramesLost = 0
        frameIntervals = 0; intervalTotalNs = 0; intervalMaxNs = 0
        java.util.Arrays.fill(intervalHistogram, 0L)
        wakeSamples = 0; wakeLateCount = 0; wakeLateTotalNs = 0
        wakeMaxNs = 0; maxOvershootFrames = 0
        audioReads = 0; audioMisses = 0; audioSamples = 0
        audioWaitTotalNs = 0; audioWaitMaxNs = 0
        audioHoldTotalNs = 0; audioHoldMaxNs = 0
        videoReads = 0; videoMisses = 0; videoRefused = 0
        videoWaitTotalNs = 0; videoWaitMaxNs = 0
        videoHoldTotalNs = 0; videoHoldMaxNs = 0
        companionReads = 0; companionTimeouts = 0
        companionWaitTotalNs = 0; companionWaitMaxNs = 0
        companionHoldTotalNs = 0; companionHoldMaxNs = 0
    }

    fun onSpeedChanged(speed: Int) {
        if (!enabled) return
        speedSeen = speed
    }

    /**
     * Called once by the emulation loop at startup so the debug property can be toggled
     * between runs without a rebuild.
     *
     * Activating requires an explicit `debug.dualdex.ffdiag` system property, which can only
     * be set from a debuggable context (adb / a debug build). A release build therefore
     * never pays for this instrumentation even if the guard calls are left in place.
     */
    fun refreshFromSystemProperty() {
        if (started.getAndSet(true)) return
        if (!isDebuggableApp()) return
        enabled = readSystemProperty(PROP) == "1"
    }

    /**
     * True when this process is a debuggable build. Read from the loaded application info so
     * no Context is needed at this call site.
     */
    private fun isDebuggableApp(): Boolean = try {
        val clazz = Class.forName("android.app.ActivityThread")
        val info = (clazz.getMethod("currentApplication").invoke(null) as? Application)?.applicationInfo
            ?: clazz.getMethod("currentApplicationInfo").invoke(null) as? ApplicationInfo
        info == null || (info.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
    } catch (_: Throwable) {
        true
    }

    /**
     * Reflectively reads an Android system property so a reproduction run can be switched
     * between instrumented and quiet without reinstalling the APK. Returns null on any
     * failure (hidden-API restrictions on newer platform versions), which simply leaves
     * diagnostics off.
     */
    private fun readSystemProperty(key: String): String? = try {
        val clazz = Class.forName("android.os.SystemProperties")
        val get = clazz.getMethod("get", String::class.java)
        (get.invoke(null, key) as? String)?.trim()
    } catch (_: Throwable) {
        null
    }
}
