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
         * The scope a verified result is limited to, shown with every verified headline.
         *
         * The request shape cannot express the generation III badge boost, so a badged player's real
         * damage is about 10% higher than a verified number shows. Documenting that only in a design
         * note did not enforce it, and the code cannot establish the running game's badge state, so
         * the limit travels with the claim itself.
         */
        const val BADGE_BOOST_NOTE: String = "unbadged; badge boost is not applied"

        /**
         * Appended when a participant's values were asserted rather than observed - a hypothetical
         * setup, or a benchmark the screen filled in.
         *
         * `MANUAL` means the user is asserting the values; it is not proof that every omitted field
         * was explicitly supplied. A verified hypothetical must not read as a verified observation
         * of the battle in front of the player, so the two say different things.
         */
        const val ASSUMED_INPUTS_NOTE: String = "assumed inputs, not read from the game"

        /**
         * The headline for a verdict, including the build's identity and scope when verified and the
         * full reason list when it is not.
         *
         * [request] is optional so the verdict alone can still be rendered; supply it to disclose
         * that the values were asserted rather than read.
         */
        fun forVerdict(
            verdict: CalcCapabilityVerdict,
            request: DamageCalculationRequest? = null
        ): CalcResultPresentation {
            val assumed = request?.hasAssumedInputs() == true
            return when (verdict.support) {
                CalcSupport.VERIFIED -> CalcResultPresentation(
                    headline = buildString {
                        append("$VERIFIED_PREFIX (${verdict.capability.label}; $BADGE_BOOST_NOTE")
                        if (assumed) append("; $ASSUMED_INPUTS_NOTE")
                        append(")")
                    },
                    isVerified = !assumed,
                    support = verdict.support
                )
                CalcSupport.ESTIMATED -> CalcResultPresentation(
                    headline = if (verdict.isCaveatedEstimate) {
                        "$ESTIMATED_PREFIX — estimate ignores:\n" +
                            verdict.ignoredMechanics.joinToString("\n") { it.presentationLine }
                    } else {
                        "$ESTIMATED_PREFIX — ${verdict.supportDetail}"
                    },
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
}

/**
 * True when any participant's values were asserted rather than read from the running game.
 *
 * This is the "hypothetical" signal. It is deliberately separate from whether the calculation is
 * *supported*: a manual matchup may be fully supported and still not be an observation of the battle
 * in front of the player.
 */
fun DamageCalculationRequest.hasAssumedInputs(): Boolean =
    attacker.origin == CalcInputOrigin.MANUAL || defender.origin == CalcInputOrigin.MANUAL
