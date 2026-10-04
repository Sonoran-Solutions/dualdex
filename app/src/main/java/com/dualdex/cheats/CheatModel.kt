package com.dualdex.cheats

import java.util.UUID

data class CheatItem(
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val code: String,
    val enabled: Boolean = false,
    val isPreset: Boolean = false,
    val disabledReason: String? = null
)
