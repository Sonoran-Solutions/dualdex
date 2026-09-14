package com.dualdex.companion

/** What the poller should do with a live observation after a read attempt. */
enum class LiveObservationDecision {
    /** The read was valid; publish (or keep) the observation. */
    PUBLISH,

    /** The read failed transiently; keep the previous observation and try again. */
    RETAIN,

    /** Too many consecutive failed reads; the observation is no longer trustworthy. */
    CLEAR
}

/**
 * Deterministic invalid-read stabilizer for a single live observation (party, location, ...).
 *
 * A single failed read is normal during transitions, screen fades, and save-state loads, so it must
 * not flicker the UI. Sustained failure means the observation can no longer be justified and must be
 * cleared rather than left on screen as stale "last known good" data.
 *
 * Deliberately time-free: decisions depend only on the number of consecutive invalid reads, so the
 * behaviour is reproducible in unit tests without sleeps.
 */
class LiveObservationStabilizer(
    private val invalidReadThreshold: Int = DEFAULT_INVALID_READ_THRESHOLD
) {
    init {
        require(invalidReadThreshold >= 1) { "invalidReadThreshold must be >= 1" }
    }

    var consecutiveInvalidReads: Int = 0
        private set

    fun onValidRead(): LiveObservationDecision {
        consecutiveInvalidReads = 0
        return LiveObservationDecision.PUBLISH
    }

    fun onInvalidRead(): LiveObservationDecision {
        consecutiveInvalidReads++
        return if (consecutiveInvalidReads >= invalidReadThreshold) {
            LiveObservationDecision.CLEAR
        } else {
            LiveObservationDecision.RETAIN
        }
    }

    /** Called on ROM identity changes and whenever the compatibility gate closes. */
    fun reset() {
        consecutiveInvalidReads = 0
    }

    companion object {
        /** 5 consecutive failed reads at the 100 ms poll cadence is ~500 ms of sustained failure. */
        const val DEFAULT_INVALID_READ_THRESHOLD = 5
    }
}
