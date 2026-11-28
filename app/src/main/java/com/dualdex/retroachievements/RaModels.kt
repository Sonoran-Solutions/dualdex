package com.dualdex.retroachievements

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RaAccountStatus { SIGNED_OUT, SIGNING_IN, SIGNED_IN }
enum class RaGameStatus { NONE, LOADING, ACTIVE, NO_SET, UNAVAILABLE }

data class RaAchievement(
    val id: Long,
    val title: String,
    val description: String,
    val points: Int,
    val unlocked: Boolean,
    val isMissable: Boolean,
    val measuredProgress: Float?,
    val category: Int
)

data class RaLeaderboardAttempt(val title: String, val value: String)

data class RaSessionState(
    val account: RaAccountStatus = RaAccountStatus.SIGNED_OUT,
    val username: String? = null,
    val game: RaGameStatus = RaGameStatus.NONE,
    val gameTitle: String? = null,
    val hardcoreRequested: Boolean = false,
    val hardcorePolicy: RaHardcorePolicy = RaHardcorePolicy(),
    val richPresence: String = "",
    val achievements: List<RaAchievement> = emptyList(),
    val attempts: List<RaLeaderboardAttempt> = emptyList(),
    val offline: Boolean = false,
    val message: String? = null,
    val canRetrySignIn: Boolean = false,
    val showBanners: Boolean = true,
    val playUnlockSound: Boolean = false
)

data class RaUnlockBanner(val id: Long, val title: String, val points: Int, internal val sequence: Long = 0)

internal class RaUnlockQueue {
    private val pending = ArrayDeque<RaUnlockBanner>()
    private val current = MutableStateFlow<RaUnlockBanner?>(null)
    val banner = current.asStateFlow()
    private var sequence = 0L

    @Synchronized
    fun add(unlock: RaUnlockBanner) {
        val entry = unlock.copy(sequence = ++sequence)
        if (current.value == null) current.value = entry else pending.addLast(entry)
    }

    @Synchronized
    fun dismiss(entry: RaUnlockBanner) {
        if (current.value == entry) current.value = pending.removeFirstOrNull()
    }

    @Synchronized
    fun clear() {
        pending.clear()
        current.value = null
    }
}

internal data class RaRom(val md5: String, val generation: Long)
