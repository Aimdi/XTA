package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl

/**
 * How much the reader's Threads account may be read, and how long it rests
 * after Meta warns. Tuned to stay well under what a person reading by hand
 * does in the Threads app (a list opened, a few pages scrolled, a minute
 * read): Meta's anti-scripting looks at bursts and at volume over the day,
 * and a reader who opens followers, Activity and a search in one sitting
 * spends a dozen reads.
 *
 * - [perFiveMinutes] 15: about one read every 20 seconds kept up, far slower
 *   than the app's own scrolling, and a burst of tapping lists stops early.
 * - [perHour] 60: an hour of browsing lists by hand; a list that would need
 *   more is one no person scrolls through.
 * - [perDay] 300: a heavy day of reading, so a day's total never looks like
 *   a crawler's, whatever the screens ask.
 * - [ladderMillis]: a first warning rests 30 minutes (what Meta's own "Please
 *   wait a few minutes" means in practice), a second within
 *   [signalWindowMillis] of the last rest's end six hours, a third and any
 *   after it a whole day: a session Meta warns again soon after a rest is
 *   one it is watching. A full window with no warning starts the ladder again.
 * - [autoPages] 4: a list reads its first page and four more as the reader
 *   scrolls (five pages, a few hundred people), then each further page waits
 *   for Load more, so scrolling alone cannot spend the budget.
 * - [pageTtlMillis] 30 minutes: a page of a list read again within it is the
 *   same page, so going back and forth costs nothing.
 */
data class ThreadsGuardLimits(
    val perFiveMinutes: Int = 15,
    val perHour: Int = 60,
    val perDay: Int = 300,
    val ladderMillis: List<Long> = listOf(30 * MINUTE, 6 * HOUR, 24 * HOUR),
    val signalWindowMillis: Long = 24 * HOUR,
    val autoPages: Int = AUTO_PAGES,
    val pageTtlMillis: Long = 30 * MINUTE,
    /** The longest wait Meta's Retry-After is taken at: one longer is no wait a reader could sit out, and is capped. */
    val longestRetryAfterMillis: Long = 7 * 24 * HOUR,
) {
    /** The rolling windows and how many reads each allows. */
    internal val windows: List<Pair<Long, Int>> get() = listOf(5 * MINUTE to perFiveMinutes, HOUR to perHour, DAY to perDay)

    companion object {
        const val MINUTE = 60 * 1000L
        const val HOUR = 60 * MINUTE
        const val DAY = 24 * HOUR

        /** How many pages after the first a session list reads on its own as the reader scrolls. */
        const val AUTO_PAGES = 4
    }
}

/**
 * The headers a session is sent for as long as it lives: the browser and
 * app it says it is, and the app id. A published repair that changes them
 * reaches a new session, not one Meta already knows by them; nothing secret.
 */
data class ThreadsSessionHeaders(val webUserAgent: String, val appUserAgent: String, val appId: String, val asbdId: String) {
    companion object {
        fun of(repairs: ThreadsRepairs) = ThreadsSessionHeaders(repairs.webUserAgent, repairs.appUserAgent, repairs.appId, repairs.asbdId)
    }
}

/**
 * What the guard keeps across restarts, as epoch milliseconds: when each
 * session read of the last day left, when Meta warned in the last day, the
 * rung of the rest ladder the last warning reached and when its rest ended,
 * whether Meta said the session is gone, and the headers the session is
 * sent. No token, cookie or session value is ever in it.
 */
data class ThreadsGuardKept(
    val sent: List<Long> = emptyList(),
    val signals: List<Long> = emptyList(),
    val signedOut: Boolean = false,
    val headers: ThreadsSessionHeaders? = null,
    val rung: Int = 0,
    val restEnd: Long? = null,
) {
    /** As JSON, for the app's settings. */
    fun encode(): String = buildJsonObject {
        put("sent", JsonArray(sent.map(::JsonPrimitive)))
        put("signals", JsonArray(signals.map(::JsonPrimitive)))
        put("signed_out", signedOut)
        put("rung", rung)
        restEnd?.let { put("rest_end", it) }
        headers?.let { pinned ->
            put("headers", buildJsonObject {
                put("web", pinned.webUserAgent)
                put("app", pinned.appUserAgent)
                put("app_id", pinned.appId)
                put("asbd_id", pinned.asbdId)
            })
        }
    }.toString()

    companion object {
        /** [text] as [encode] wrote it; anything else reads as nothing kept. */
        fun decode(text: String): ThreadsGuardKept {
            val root = J.parse(text)
            val pinned = root["headers"]
            val headers = listOf("web", "app", "app_id", "asbd_id").map { pinned[it].string }
                .takeIf { values -> values.all { !it.isNullOrEmpty() } }
                ?.let { (web, app, id, asbd) -> ThreadsSessionHeaders(web!!, app!!, id!!, asbd!!) }
            return ThreadsGuardKept(
                sent = root["sent"].list.mapNotNull { it.long },
                signals = root["signals"].list.mapNotNull { it.long },
                signedOut = root["signed_out"].boolean == true,
                headers = headers,
                rung = root["rung"].long?.toInt()?.coerceAtLeast(0) ?: 0,
                restEnd = root["rest_end"].long,
            )
        }
    }
}

private val J.long: Long? get() = (raw as? JsonPrimitive)?.takeIf { !it.isString }?.content?.toLongOrNull()

/** Where the reader's account stands, as Settings and the screens that use it say it. */
enum class ThreadsAccountState {
    /** It may be read now, when a screen needs it. */
    READY,

    /** It rests: Meta warned, or a budget is spent; nothing is sent until [ThreadsAccountStatus.until]. */
    RESTING,

    /** Meta said the session is gone: nothing is sent until the reader signs in again. */
    SIGNED_OUT,
}

/** The account's state, when a rest ends, and the reads of the last 24 hours against the day's budget. */
data class ThreadsAccountStatus(
    val state: ThreadsAccountState = ThreadsAccountState.READY,
    val until: Instant? = null,
    val usedToday: Int = 0,
    val dailyBudget: Int = ThreadsGuardLimits().perDay,
)

/** What a session answer, or a redirect it was sent, says about the account. */
internal enum class ThreadsSignal {
    /** Slow down: a 429, "Please wait a few minutes", or Meta's spam or feedback flag. */
    THROTTLE,

    /** Meta wants the person to prove who they are, or has suspended the account: park at once. */
    CHALLENGE,

    /** The session is gone: the reader signs in again. */
    SIGNED_OUT,
}

/** Reads Meta's warnings out of a session answer, as cheaply as a substring for the answers that carry none. */
internal object ThreadsAccountSignals {
    private val challengeMessages = setOf("checkpoint_required", "challenge_required", "consent_required")
    private val throttleMessages = setOf("feedback_required", "rate_limit_error")
    private val markers = listOf("checkpoint", "challenge", "consent_required", "feedback_required", "spam", "require_login", "rate_limit_error")
    private val challengePaths = listOf(listOf("challenge"), listOf("checkpoint"), listOf("accounts", "suspended"), listOf("accounts", "disabled"), listOf("accounts", "consent"))

    /** What a redirect to [location] means: a challenge or suspension page, Meta's sign-in page, or nothing. */
    fun ofRedirect(location: HttpUrl): ThreadsSignal? {
        val segments = location.pathSegments.filter { it.isNotEmpty() }.map { it.lowercase() }
        return when {
            challengePaths.any { path -> segments.take(path.size) == path } -> ThreadsSignal.CHALLENGE
            ThreadsLinks.isSignInPage(location) -> ThreadsSignal.SIGNED_OUT
            else -> null
        }
    }

    /**
     * What an answer of [code] with [body] warns of: Meta's own fields at the
     * top of its JSON (`message`, `spam`, `checkpoint_url`, `challenge`,
     * `require_login`), so a post that merely mentions a word is no warning.
     * A 401 is always a session gone.
     */
    fun ofAnswer(code: Int, body: String): ThreadsSignal? {
        if (code == 401) return ThreadsSignal.SIGNED_OUT
        if (markers.none { it in body }) return null
        val root = J(ThreadsJson.decode(body) as? JsonObject)
        val message = root["message"].string?.lowercase().orEmpty()
        return when {
            message in challengeMessages || root["checkpoint_url"].string != null || root["challenge"].exists ||
                root["error_type"].string?.contains("checkpoint") == true -> ThreadsSignal.CHALLENGE
            message in throttleMessages || root["spam"].boolean == true -> ThreadsSignal.THROTTLE
            code == 403 && root["require_login"].boolean == true -> ThreadsSignal.SIGNED_OUT
            !root.exists && code != 200 && challengeMessages.any { it in body } -> ThreadsSignal.CHALLENGE
            else -> null
        }
    }

    /** Meta's Retry-After, in seconds or as an HTTP date, as milliseconds from [now]; null when it sent none that reads. */
    fun retryAfter(value: String?, now: Long): Long? {
        val text = value?.trim()?.ifEmpty { null } ?: return null
        text.toLongOrNull()?.let { return (it * 1000).takeIf { millis -> millis > 0 } }
        return runCatching { ZonedDateTime.parse(text, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() - now }
            .getOrNull()?.takeIf { it > 0 }
    }
}

/**
 * The one place a session read asks whether it may leave: never while the
 * session rests or is signed out, never past a rolling budget
 * ([ThreadsGuardLimits]), and after a warning from Meta not again until a
 * rest that grows with each warning of the day has passed. Everything it
 * decides by is kept in [memory] ([ThreadsKept.guard] and
 * [ThreadsKept.cooldownUntil]), so a restart forgets none of it. [lock] is
 * the one the client keeps [memory] under.
 */
class ThreadsAccountGuard(
    private val memory: ThreadsMemory,
    private val clock: () -> Long,
    val limits: ThreadsGuardLimits = ThreadsGuardLimits(),
    private val lock: Any = Any(),
) {
    private val current = MutableStateFlow(ThreadsAccountStatus(dailyBudget = limits.perDay))

    /** The account's state as it last changed; [refresh] brings a rest that ended up to date. */
    val status: StateFlow<ThreadsAccountStatus> = current.asStateFlow()

    init {
        refresh()
    }

    /** The account's state now. */
    fun refresh(): ThreadsAccountStatus = synchronized(lock) { statusNow().also { current.value = it } }

    /**
     * Why a session read may not leave now, with nothing sent: the session
     * signed out ([ThreadsFailure.UNAUTHORIZED]), resting after a warning
     * ([ThreadsFailure.SESSION_SUSPENDED] until its end), or a budget spent
     * ([ThreadsFailure.ACCOUNT_RESTING] until a read frees up). Null when it
     * may. An ended rest is cleared.
     */
    fun refusal(): ThreadsException? = synchronized(lock) {
        val now = clock()
        val kept = memory.kept
        val refused = when {
            kept.guard.signedOut -> ThreadsException(ThreadsFailure.UNAUTHORIZED, "the session signed out")
            kept.cooldownUntil != null && now < kept.cooldownUntil ->
                Instant.ofEpochMilli(kept.cooldownUntil).let { ThreadsException(ThreadsFailure.SESSION_SUSPENDED, "cooling down until $it", retryAt = it) }
            else -> budgetSpentUntil(kept.guard.sent, now)?.let {
                ThreadsException(ThreadsFailure.ACCOUNT_RESTING, "budget spent until ${Instant.ofEpochMilli(it)}", retryAt = Instant.ofEpochMilli(it))
            }
        }
        if (kept.cooldownUntil != null && now >= kept.cooldownUntil) memory.kept = kept.copy(cooldownUntil = null)
        current.value = statusNow()
        refused
    }

    /** When the last session read left; null when none did in the last day. */
    fun lastSent(): Long? = synchronized(lock) { memory.kept.guard.sent.lastOrNull() }

    /** How many session reads in a row left with no rest between them, this one not counted: a person reading pauses every few. */
    fun streak(): Int = synchronized(lock) {
        val sent = memory.kept.guard.sent
        if (sent.isEmpty() || clock() - sent.last() > STREAK_BREAK_MILLIS) return 0
        val breakAt = (sent.size - 1 downTo 1).firstOrNull { sent[it] - sent[it - 1] > STREAK_BREAK_MILLIS } ?: 0
        sent.size - breakAt
    }

    /** A session read left now: it counts against every budget. */
    fun departed() = change { kept ->
        val now = clock()
        kept.copy(guard = kept.guard.copy(sent = (kept.guard.sent.filter { now - it < ThreadsGuardLimits.DAY } + now).takeLast(KEPT_MAX)))
    }

    /**
     * Meta warned (a throttle, a challenge, `login_required`): the session
     * rests from now, 30 minutes for a first warning, six hours for one
     * within a day of the last rest's end, a day for any after that, or as
     * long as Meta's [retryAfterMillis] when that is longer. Returns when the
     * rest ends. Nothing is retried on its own: every read refuses until then.
     */
    fun warned(retryAfterMillis: Long? = null): Instant {
        var until = 0L
        change { kept ->
            val now = clock()
            val last = listOfNotNull(kept.guard.signals.lastOrNull(), kept.guard.restEnd).maxOrNull()
            val rung = if (last != null && now - last < limits.signalWindowMillis) minOf(kept.guard.rung + 1, limits.ladderMillis.lastIndex) else 0
            val asked = retryAfterMillis?.coerceAtMost(limits.longestRetryAfterMillis) ?: 0L
            until = maxOf(now + limits.ladderMillis[rung], now + asked, kept.cooldownUntil ?: 0L)
            val recent = kept.guard.signals.filter { now - it < limits.signalWindowMillis }
            kept.copy(cooldownUntil = until, guard = kept.guard.copy(signals = (recent + now).takeLast(KEPT_MAX), rung = rung, restEnd = until))
        }
        return Instant.ofEpochMilli(until)
    }

    /** Meta said the session is gone: nothing is sent with it until the reader signs in again ([sessionChanged]). */
    fun signedOut() = change { it.copy(guard = it.guard.copy(signedOut = true)) }

    /**
     * The reader pasted, signed in with or cleared a session: a new one is
     * not signed out, and it is sent the headers in force now. What was
     * spent and Meta's warnings stay: they are the account's, not the
     * session's.
     */
    fun sessionChanged() = change { it.copy(guard = it.guard.copy(signedOut = false, headers = null)) }

    /** The headers this session is sent: those pinned when it was first used, else [now]'s, pinned from here on. */
    fun headers(now: () -> ThreadsSessionHeaders): ThreadsSessionHeaders {
        memory.kept.guard.headers?.let { return it }
        var pinned: ThreadsSessionHeaders? = null
        change { kept -> kept.guard.headers?.let { pinned = it; kept } ?: now().let { fresh -> pinned = fresh; kept.copy(guard = kept.guard.copy(headers = fresh)) } }
        return checkNotNull(pinned)
    }

    private fun change(edit: (ThreadsKept) -> ThreadsKept) = synchronized(lock) {
        memory.kept = edit(memory.kept)
        current.value = statusNow()
    }

    private fun statusNow(): ThreadsAccountStatus {
        val now = clock()
        val kept = memory.kept
        val used = kept.guard.sent.count { now - it < ThreadsGuardLimits.DAY }
        val resting = listOfNotNull(kept.cooldownUntil?.takeIf { it > now }, budgetSpentUntil(kept.guard.sent, now)).maxOrNull()
        return when {
            kept.guard.signedOut -> ThreadsAccountStatus(ThreadsAccountState.SIGNED_OUT, null, used, limits.perDay)
            resting != null -> ThreadsAccountStatus(ThreadsAccountState.RESTING, Instant.ofEpochMilli(resting), used, limits.perDay)
            else -> ThreadsAccountStatus(ThreadsAccountState.READY, null, used, limits.perDay)
        }
    }

    /** When the next read may leave, if a rolling budget is spent at [now]: once the read that filled it ages out of its window. */
    private fun budgetSpentUntil(sent: List<Long>, now: Long): Long? = limits.windows.mapNotNull { (window, allowed) ->
        val inside = sent.filter { now - it < window }
        if (inside.size < allowed) null else inside[inside.size - allowed] + window
    }.maxOrNull()

    companion object {
        /** A gap this long between two session reads is a rest: the next streak starts anew. */
        const val STREAK_BREAK_MILLIS = 2 * 60 * 1000L

        /** How many departures and warnings are kept at most: a day's budget and a little more. */
        private const val KEPT_MAX = 400
    }
}
