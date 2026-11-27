package com.dualdex.retroachievements

import android.content.Context
import com.dualdex.emulator.LibretroCoreCoordinator
import com.dualdex.emulator.LibretroHost
import com.dualdex.settings.SettingsManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeUnit

/** One client for the shared core. UI state and HTTP never own or read emulated memory. */
class RetroAchievementsService private constructor(
    private val core: LibretroCoreCoordinator,
    private val tokens: RaTokenStore,
    private val settings: SettingsManager
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val commands = Channel<Command>(Channel.UNLIMITED)
    private val authentication = Any()
    @Volatile private var epoch = 0L
    private val unlocks = RaUnlockQueue()
    val unlockBanner = unlocks.banner
    private val current = MutableStateFlow(RaSessionState(
        hardcoreRequested = settings.retroAchievementsHardcore,
        showBanners = settings.retroAchievementsBanners,
        playUnlockSound = settings.retroAchievementsSound
    ))
    val state = current.asStateFlow()

    private var nativeSession = false
    private var usingSavedToken = false
    private var username: String? = null
    private var observedRom: RaRom? = null
    private var gameStatus = RaGameStatus.NONE
    private var activeEpoch = 0L
    private var retryLoginAt = Long.MAX_VALUE
    private var retryGameAt = Long.MAX_VALUE
    private var snapshotAt = 0L
    private var retryLoginDelay = RETRY_DELAY
    private var retryGameDelay = RETRY_DELAY
    private var offline = false
    private val inFlight = mutableMapOf<Long, Job>()
    private var unavailable = false

    init {
        val saved = tokens.read()
        core.armRaHardcore(saved != null && current.value.hardcoreRequested)
        if (saved != null) retrySignIn()
        scope.launch { core.raRom.collect { commands.send(Command.Wake) } }
        scope.launch { drive() }
    }

    fun signIn(username: String, password: ByteArray) {
        val name = username.trim()
        if (name.isEmpty() || name.length > 128 || password.isEmpty() || password.size > 1024 ||
            '\u0000' in name || password.any { it == 0.toByte() }) {
            password.fill(0)
            return
        }
        synchronized(authentication) {
            if (current.value.account != RaAccountStatus.SIGNED_OUT) {
                password.fill(0)
                return
            }
            enqueueLogin(name, password, savedToken = false)
        }
    }

    fun retrySignIn() {
        synchronized(authentication) {
            if (current.value.account != RaAccountStatus.SIGNED_OUT) return
            val saved = tokens.read() ?: return
            enqueueLogin(saved.username, saved.token.encodeToByteArray(), savedToken = true)
        }
    }

    private fun enqueueLogin(name: String, secret: ByteArray, savedToken: Boolean) {
        ++epoch
        current.value = current.value.copy(account = RaAccountStatus.SIGNING_IN, message = null, canRetrySignIn = false)
        commands.trySend(Command.Login(epoch, name, secret, savedToken))
    }

    fun signOut() {
        synchronized(authentication) {
            ++epoch
            tokens.clear()
            core.armRaHardcore(false)
            unlocks.clear()
            current.value = current.value.signedOut()
            commands.trySend(Command.Logout(epoch))
        }
    }

    fun setHardcore(enabled: Boolean) {
        settings.retroAchievementsHardcore = enabled
        update { it.copy(hardcoreRequested = enabled) }
        commands.trySend(Command.Mode(enabled))
    }

    fun setBanners(enabled: Boolean) {
        settings.retroAchievementsBanners = enabled
        synchronized(authentication) {
            current.value = current.value.copy(showBanners = enabled)
            if (!enabled) unlocks.clear()
        }
    }

    fun setUnlockSound(enabled: Boolean) {
        settings.retroAchievementsSound = enabled
        update { it.copy(playUnlockSound = enabled) }
    }

    fun dismissBanner(banner: RaUnlockBanner) = unlocks.dismiss(banner)

    private sealed interface Command {
        data object Wake : Command
        class Login(val epoch: Long, val name: String, val secret: ByteArray, val savedToken: Boolean) : Command
        class Logout(val epoch: Long) : Command
        class Mode(val enabled: Boolean) : Command
        class Response(val epoch: Long, val id: Long, val response: RaHttpResponse) : Command
    }

    private suspend fun drive() {
        while (true) {
            val wait = nextWait()
            val command = if (wait == null) commands.receive() else withTimeoutOrNull(wait) { commands.receive() }
            try {
                when (command) {
                    is Command.Login -> if (command.epoch == epoch) {
                        startLogin(command)
                    } else command.secret.fill(0)
                    is Command.Logout -> if (command.epoch == epoch) stopNative()
                    is Command.Mode -> changeMode(command.enabled)
                    is Command.Response -> complete(command)
                    Command.Wake -> Unit
                    null -> resumeScheduledWork()
                }
                if (nativeSession && activeEpoch == epoch) poll()
            } catch (_: LinkageError) {
                unavailable = true
                nativeSession = false
                retryLoginAt = Long.MAX_VALUE
                retryGameAt = Long.MAX_VALUE
                core.armRaHardcore(false)
                update { it.signedOut("RetroAchievements is unavailable in this build.") }
            }
        }
    }

    private fun nextWait(): Long? {
        if (nativeSession && (username == null || gameStatus == RaGameStatus.LOADING ||
                gameStatus == RaGameStatus.ACTIVE || observedRom != null && gameStatus == RaGameStatus.NONE ||
                offline || inFlight.isNotEmpty())) return POLL_INTERVAL
        val scheduled = minOf(retryLoginAt, retryGameAt)
        return if (scheduled == Long.MAX_VALUE) null else (scheduled - now()).coerceAtLeast(1)
    }

    private suspend fun startLogin(login: Command.Login) {
        try {
            // After an offline retry the original native client still has no authenticated account.
            if (nativeSession) stopNative(preservePolicy = login.savedToken)
            activeEpoch = login.epoch
            usingSavedToken = login.savedToken
            retryLoginAt = Long.MAX_VALUE
            if (!login.savedToken) retryLoginDelay = RETRY_DELAY
            if (unavailable || !withCore {
                    LibretroHost.nativeRaLogin(login.name.encodeToByteArray(), login.secret, login.savedToken)
                }
            ) {
                loginFailed(login.savedToken, rejected = false)
                return
            }
            nativeSession = true
        } finally {
            login.secret.fill(0)
        }
    }

    private suspend fun stopNative(preservePolicy: Boolean = false) {
        inFlight.values.forEach { it.cancel() }
        inFlight.clear()
        if (nativeSession || core.raHardcorePolicy.isHardcoreActive && !preservePolicy) withCore {
            if (!preservePolicy) core.setRaHardcoreLocked(false)
            if (nativeSession) LibretroHost.nativeRaLogout()
        }
        nativeSession = false
        username = null
        observedRom = null
        gameStatus = RaGameStatus.NONE
        offline = false
        retryLoginAt = Long.MAX_VALUE
        retryGameAt = Long.MAX_VALUE
        retryGameDelay = RETRY_DELAY
        snapshotAt = 0L
    }

    private suspend fun changeMode(enabled: Boolean) {
        val requested = current.value.hardcoreRequested
        if (enabled != requested) return
        core.armRaHardcore(enabled && (username != null || tokens.read() != null))
        if (unavailable) return
        withCore {
            if (!enabled || username != null && observedRom == core.raRom.value && gameStatus == RaGameStatus.ACTIVE) {
                core.setRaHardcoreLocked(enabled)
                snapshotAt = 0L
            }
        }
        update { it.copy(hardcorePolicy = core.raHardcorePolicy) }
    }

    private suspend fun complete(command: Command.Response) {
        try {
            if (command.epoch != epoch || command.epoch != activeEpoch || !nativeSession) return
            inFlight.remove(command.id)
            withCore {
                if (command.epoch == epoch) {
                    LibretroHost.nativeRaComplete(command.id, command.response.status, command.response.body)
                }
            }
            snapshotAt = 0L
        } finally {
            command.response.body.fill(0)
        }
    }

    private fun resumeScheduledWork() {
        if (retryLoginAt <= now()) {
            retryLoginAt = Long.MAX_VALUE
            retrySignIn()
        }
    }

    private suspend fun loginFailed(saved: Boolean, rejected: Boolean) {
        val failedEpoch = activeEpoch
        val retry = saved && !rejected && !unavailable
        stopNative(preservePolicy = retry)
        synchronized(authentication) {
            if (failedEpoch != epoch) return
            if (!retry) core.armRaHardcore(false)
            if (saved && rejected) tokens.clear()
            if (retry) {
                retryLoginAt = now() + retryLoginDelay
                retryLoginDelay = (retryLoginDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
            }
            current.value = current.value.signedOut(
                if (rejected) "Sign-in was rejected. Check your username and password."
                else "Could not reach RetroAchievements. Try signing in again.",
                retry
            )
        }
    }

    private suspend fun poll() {
        val rom = core.raRom.value
        if (rom != observedRom) {
            observedRom = rom
            gameStatus = RaGameStatus.NONE
            snapshotAt = 0L
            retryGameDelay = RETRY_DELAY
            retryGameAt = Long.MAX_VALUE
            synchronized(authentication) {
                unlocks.clear()
                if (epoch == activeEpoch) current.value = current.value.forRom(rom != null, core.raHardcorePolicy)
            }
        }

        val sample = withCore {
            if (rom != core.raRom.value) return@withCore null
            if (username != null && rom != null &&
                (gameStatus == RaGameStatus.NONE || retryGameAt <= now())) {
                retryGameAt = Long.MAX_VALUE
                LibretroHost.nativeRaHardcore(core.raHardcorePolicy.isHardcoreActive)
                LibretroHost.nativeRaLoadGame(rom.md5)
            }
            if (!core.emulationRunning || gameStatus != RaGameStatus.ACTIVE) LibretroHost.nativeRaIdle()
            val status = LibretroHost.nativeRaStatus()
            if (status[1] == NATIVE_NO_SET) core.setRaHardcoreLocked(false)
            val credentials = if (status[0] == NATIVE_READY && username == null) LibretroHost.nativeRaUser() else null
            val requests = buildList {
                repeat(MAX_REQUESTS - inFlight.size) {
                    val fields = LibretroHost.nativeRaNextRequest() ?: return@buildList
                    add(RaHttpRequest(
                        fields[0]!!.decodeToString().toLong(), fields[1]!!.decodeToString(),
                        fields[2], fields[3]?.decodeToString()
                    ))
                }
            }
            val banners = buildList {
                while (true) {
                    val unlock = LibretroHost.nativeRaNextUnlock() ?: break
                    add(RaUnlockBanner(unlock[0]!!.toLong(), unlock[2].orEmpty(), unlock[1]!!.toInt()))
                }
            }
            val snapshot = status[1] == NATIVE_ACTIVE && (snapshotAt <= now() || banners.isNotEmpty())
            if (snapshot) snapshotAt = now() + SNAPSHOT_INTERVAL
            Sample(
                status, credentials, requests, banners,
                if (snapshot) LibretroHost.nativeRaGame() else null,
                if (snapshot) LibretroHost.nativeRaAchievements() else null,
                core.raHardcorePolicy
            )
        } ?: return

        for (request in sample.requests) launchRequest(request)
        if (sample.status[0] == NATIVE_REJECTED || sample.status[0] == NATIVE_LOGIN_FAILED) {
            loginFailed(usingSavedToken, rejected = sample.status[0] == NATIVE_REJECTED)
            return
        }
        sample.credentials?.let { (name, token) ->
            try {
                synchronized(authentication) {
                    if (epoch != activeEpoch) return
                    val account = RaCredentials(name.decodeToString(), token.decodeToString())
                    tokens.save(account)
                    username = account.username
                    retryLoginDelay = RETRY_DELAY
                    core.armRaHardcore(current.value.hardcoreRequested)
                    current.value = current.value.signedIn(account.username).forRom(rom != null, sample.policy)
                }
            } finally {
                token.fill(0)
            }
        }
        val next = when (sample.status[1]) {
            NATIVE_LOADING -> RaGameStatus.LOADING
            NATIVE_ACTIVE -> RaGameStatus.ACTIVE
            NATIVE_NO_SET -> RaGameStatus.NO_SET
            NATIVE_GAME_FAILED -> RaGameStatus.UNAVAILABLE
            else -> RaGameStatus.NONE
        }
        if (next == RaGameStatus.UNAVAILABLE && retryGameAt == Long.MAX_VALUE) {
            retryGameAt = now() + retryGameDelay
            retryGameDelay = (retryGameDelay * 2).coerceAtMost(MAX_RETRY_DELAY)
        } else if (next == RaGameStatus.ACTIVE) retryGameDelay = RETRY_DELAY
        gameStatus = next
        offline = sample.status[3] != 0
        val achievements = sample.achievements?.toAchievements()
        val attempts = sample.game?.drop(2)?.chunked(2)?.map {
            RaLeaderboardAttempt(it[0].orEmpty(), it[1].orEmpty())
        }
        synchronized(authentication) {
            if (epoch != activeEpoch || rom != core.raRom.value || username == null) return
            val previous = current.value
            var policy = sample.policy
            val forcedDrop = next == RaGameStatus.ACTIVE && policy.isHardcoreActive && sample.status[2] == 0
            if (forcedDrop) policy = RaHardcorePolicy()
            current.value = previous.copy(
                game = if (next == RaGameStatus.NONE && rom != null) RaGameStatus.LOADING else next,
                gameTitle = sample.game?.get(0) ?: previous.gameTitle,
                richPresence = sample.game?.get(1) ?: previous.richPresence,
                achievements = achievements ?: previous.achievements,
                attempts = attempts ?: previous.attempts,
                hardcorePolicy = policy,
                offline = offline,
                message = when {
                    forcedDrop -> "RetroAchievements switched this game to Softcore."
                    sample.status[4] != 0 -> "RetroAchievements could not submit an unlock or leaderboard score."
                    else -> previous.message
                }
            )
            if (previous.showBanners || previous.playUnlockSound) sample.banners.forEach(unlocks::add)
        }
        if (next == RaGameStatus.ACTIVE && sample.policy.isHardcoreActive && sample.status[2] == 0) {
            withCore {
                if (rom == core.raRom.value) core.setRaHardcoreLocked(false)
            }
        }
    }

    private fun launchRequest(request: RaHttpRequest) {
        val requestEpoch = activeEpoch
        if (epoch != requestEpoch) {
            request.postData?.fill(0)
            return
        }
        inFlight[request.id] = scope.launch(Dispatchers.IO) {
            val response = try {
                if (epoch != requestEpoch) return@launch
                RaHttp.execute(request)
            } finally {
                request.postData?.fill(0)
            }
            if (epoch == requestEpoch) commands.trySend(Command.Response(requestEpoch, request.id, response))
            else response.body.fill(0)
        }
    }

    private fun update(transform: (RaSessionState) -> RaSessionState) {
        synchronized(authentication) { current.value = transform(current.value) }
    }

    /** Hold this only for native operations. HTTP, retry delay and UI work run outside it. */
    private suspend fun <T> withCore(action: () -> T): T {
        while (!core.lock.tryLock(0, TimeUnit.MILLISECONDS)) delay(5)
        return try { action() } finally { core.lock.unlock() }
    }

    private class Sample(
        val status: IntArray,
        val credentials: Array<ByteArray>?,
        val requests: List<RaHttpRequest>,
        val banners: List<RaUnlockBanner>,
        val game: Array<String?>?,
        val achievements: Array<String?>?,
        val policy: RaHardcorePolicy
    )

    companion object {
        private const val NATIVE_READY = 2
        private const val NATIVE_REJECTED = 3
        private const val NATIVE_LOGIN_FAILED = 4
        private const val NATIVE_LOADING = 1
        private const val NATIVE_ACTIVE = 2
        private const val NATIVE_NO_SET = 3
        private const val NATIVE_GAME_FAILED = 4
        private const val POLL_INTERVAL = 200L
        private const val SNAPSHOT_INTERVAL = 1000L
        private const val RETRY_DELAY = 5000L
        private const val MAX_RETRY_DELAY = 120_000L
        private const val MAX_REQUESTS = 4

        @Volatile private var instance: RetroAchievementsService? = null

        @Synchronized
        fun get(context: Context): RetroAchievementsService = instance ?: context.applicationContext.let { app ->
            RetroAchievementsService(LibretroCoreCoordinator.defaultInstance, RaTokenStore(app), SettingsManager(app))
                .also { instance = it }
        }

        private fun now() = TimeUnit.NANOSECONDS.toMillis(System.nanoTime())
    }
}

internal fun RaSessionState.signedOut(message: String? = null, canRetry: Boolean = false) =
    RaSessionState(
        hardcoreRequested = hardcoreRequested, message = message, canRetrySignIn = canRetry,
        showBanners = showBanners, playUnlockSound = playUnlockSound
    )

internal fun RaSessionState.signedIn(username: String) =
    copy(account = RaAccountStatus.SIGNED_IN, username = username, message = null, canRetrySignIn = false)

internal fun RaSessionState.forRom(loaded: Boolean, policy: RaHardcorePolicy) = copy(
    game = if (loaded && account == RaAccountStatus.SIGNED_IN) RaGameStatus.LOADING else RaGameStatus.NONE,
    gameTitle = null, richPresence = "", achievements = emptyList(), attempts = emptyList(),
    hardcorePolicy = policy, message = null
)

private fun Array<String?>.toAchievements(): List<RaAchievement> = asList().chunked(8).mapNotNull { fields ->
    if (fields.size != 8) return@mapNotNull null
    RaAchievement(
        id = fields[0]?.toLongOrNull() ?: return@mapNotNull null,
        title = fields[1].orEmpty(), description = fields[2].orEmpty(), points = fields[3]?.toIntOrNull() ?: 0,
        unlocked = fields[4] == "1", isMissable = fields[5] == "1",
        measuredProgress = fields[6]?.toFloatOrNull()?.takeIf { it.isFinite() }?.coerceIn(0f, 100f),
        category = fields[7]?.toIntOrNull() ?: 0
    )
}
