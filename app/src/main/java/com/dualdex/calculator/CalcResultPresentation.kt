package com.dualdex.calculator

/**
 * The exact wording the Calc screen shows for a calculation's trust state.
 *
 * Kept out of the Android view so the production labels are unit-testable and cannot drift between
 * the trust policy and the screen: the screen renders [CalcResultPresentation.headline], and the
 * policy decides whether [CalcResultPresentation.isVerified] is true.
 *
 * [CalcResultPresentation.headline] is never empty, so a result can not be presented on the screen
 * without stating what it is worth.
 */
data class CalcResultPresentation(
    val headline: String,
    val isVerified: Boolean,
    val support: CalcSupport
) {
    companion object {

        /** Prefix used for a calculation whose inputs and rules are both fully covered. */
        const val VERIFIED_PREFIX = "✅ Verified"

        /** Prefix used for a calculation that must be read as an approximation. */
        const val ESTIMATED_PREFIX = "⚠️ Approximate"

        /** Prefix used when no number may be shown at all. */
        const val UNSUPPORTED_PREFIX = "⛔ Unsupported"

        /**
         * The headline for a verdict, including the build's identity when verified and the full
         * reason list when it is not.
         */
        fun forVerdict(verdict: CalcCapabilityVerdict): CalcResultPresentation =
            when (verdict.support) {
                CalcSupport.VERIFIED -> CalcResultPresentation(
                    headline = "$VERIFIED_PREFIX (${verdict.capability.label})",
                    isVerified = true,
                    support = verdict.support
                )
                CalcSupport.ESTIMATED -> CalcResultPresentation(
                    headline = "$ESTIMATED_PREFIX — ${verdict.supportDetail}",
                    isVerified = false,
                    support = verdict.support
                )
                CalcSupport.UNSUPPORTED -> CalcResultPresentation(
                    headline = "$UNSUPPORTED_PREFIX: no result — ${verdict.supportDetail}",
                    isVerified = false,
                    support = verdict.support
                )
            }
    }
}
