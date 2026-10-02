package com.dualdex.pokemon.hns

/** Reviewed unresolved consequence, after exact/request-local proofs, never a substitute for state. */
enum class HnsGroupETier { EXACT, CAVEATED_ESTIMATE, HARD_REFUSAL }

data class HnsGroupEDisposition(
    val tier: HnsGroupETier,
    val family: String,
    val reason: String,
    val source: String
)
