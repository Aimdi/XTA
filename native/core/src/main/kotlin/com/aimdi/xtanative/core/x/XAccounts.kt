package com.aimdi.xtanative.core.x

import java.util.concurrent.ConcurrentHashMap

/** An X session XTA stored: its id (the `ct0`), its name and the headers it signs in with. */
data class XAccount(val id: String, val screenName: String?, val headers: Map<String, String>) {
    val cookie: String? get() = headers.entries.firstOrNull { it.key.equals("cookie", ignoreCase = true) }?.value?.trim()?.ifEmpty { null }
}

/** The numbers XTA's constants.dart gives its account policy. */
object XAccountPolicy {
    /** How long an account rate-limited without a reset X named is set aside. */
    const val RATE_LIMIT_FALLBACK_MILLIS = 15 * 60 * 1000L

    /** How long an account whose sign-in looks broken is not asked. */
    const val NOT_FOUND_COOLDOWN_MILLIS = 6 * 60 * 60 * 1000L

    /** Refusals in a row, each proven by another account, before an account is flagged. */
    const val NOT_FOUND_THRESHOLD = 3

    /** How long a probe that settled nothing keeps the next one back. */
    const val PROBE_RETRY_MILLIS = 15 * 60 * 1000L

    /** How long an account X called automated (or locked) is not asked. */
    const val AUTOMATION_PAUSE_MILLIS = 60 * 60 * 1000L

    /** Two accounts called automated within this pause every X read... */
    const val AUTOMATION_WINDOW_MILLIS = 10 * 60 * 1000L

    /** ...for this long. */
    const val GLOBAL_PAUSE_MILLIS = 30 * 60 * 1000L

    /** The latest reset X's headers may name, in seconds: beyond it a header is junk. */
    internal const val LATEST_RESET_SECONDS = 8_640_000_000_000L
}

/**
 * What XTA's accounts table remembers of an account's sign-in: how many
 * refusals in a row other accounts proved were its own, since when it is
 * flagged, and the HTTP status and X error code of the last refusal
 * ([lastStatus], [lastCode], for Diagnostics). A flagged account is not
 * asked at all while its cooldown runs; once it ran out, one probe request
 * decides whether it is back ([needsProbe]). One X called automated rests
 * until [pausedUntil].
 */
data class XAccountHealth(
    val consecutiveNotFound: Int = 0,
    val notFoundAt: Long? = null,
    val lastStatus: Int? = null,
    val lastCode: Int? = null,
    val pausedUntil: Long? = null,
) {
    val isClean: Boolean get() = consecutiveNotFound == 0 && notFoundAt == null && pausedUntil == null

    fun flagged(now: Long): Boolean = notFoundAt?.let { now < it + XAccountPolicy.NOT_FOUND_COOLDOWN_MILLIS } ?: false

    /** Whether X called the account automated and its pause still runs. */
    fun paused(now: Long): Boolean = pausedUntil?.let { now < it } ?: false

    /** Not to be asked now: flagged, or paused. */
    fun resting(now: Long): Boolean = flagged(now) || paused(now)

    /** Flagged once, its cooldown over, and not answered since: one request may find out whether it works again. */
    fun needsProbe(now: Long): Boolean = notFoundAt != null && consecutiveNotFound >= XAccountPolicy.NOT_FOUND_THRESHOLD && !flagged(now)

    /**
     * One more refusal, [status] its HTTP status and [code] X's; the account
     * is flagged once [XAccountPolicy.NOT_FOUND_THRESHOLD] are in a row. A
     * flagged account's count stops there: a refused probe only starts its
     * cooldown again.
     */
    fun afterRefusal(at: Long, status: Int? = lastStatus, code: Int? = null): XAccountHealth {
        if (consecutiveNotFound >= XAccountPolicy.NOT_FOUND_THRESHOLD) return copy(notFoundAt = at, lastStatus = status, lastCode = code)
        val count = consecutiveNotFound + 1
        return copy(consecutiveNotFound = count, notFoundAt = if (count >= XAccountPolicy.NOT_FOUND_THRESHOLD) at else notFoundAt, lastStatus = status, lastCode = code)
    }

    /** X answered [code] from [XErrorCodes.AUTOMATION] with [status] at [at]: the account rests an hour. */
    fun afterAutomation(at: Long, status: Int, code: Int): XAccountHealth =
        copy(lastStatus = status, lastCode = code, pausedUntil = at + XAccountPolicy.AUTOMATION_PAUSE_MILLIS)
}

/**
 * The accounts whose cooldown ran out and that are being probed: one request
 * each, and another only after [retryMillis] when that one settled nothing
 * (a 429, a lost connection).
 */
internal class XProbes(private val retryMillis: Long = XAccountPolicy.PROBE_RETRY_MILLIS) {
    private val started = ConcurrentHashMap<String, Long>()

    /** Whether [account] may send its probe now; true claims it. */
    fun claim(account: String, now: Long): Boolean {
        var claimed = false
        started.compute(account) { _, at -> if (at == null || now - at >= retryMillis) now.also { claimed = true } else at }
        return claimed
    }

    fun settled(account: String) {
        started.remove(account)
    }
}

/**
 * Where [XAccountHealth] is kept: in memory by default, on the device when a
 * store keeps it across launches, as XTA's database does.
 */
interface XAccountHealthStore {
    fun read(accountId: String): XAccountHealth

    fun write(accountId: String, health: XAccountHealth)

    companion object {
        fun inMemory(): XAccountHealthStore = object : XAccountHealthStore {
            private val kept = ConcurrentHashMap<String, XAccountHealth>()
            override fun read(accountId: String) = kept[accountId] ?: XAccountHealth()
            override fun write(accountId: String, health: XAccountHealth) {
                if (health.isClean) kept.remove(accountId) else kept[accountId] = health
            }
        }
    }
}

/**
 * Which account asks X next, as XTA's AccountSelector picks: one not tried yet
 * for this request, healthy ones (not flagged, not rate-limited here) before
 * the rest, which are still asked when nothing else is left; among the
 * healthy, those with quota to spare before those [low] on it. Within that,
 * the [preferred] account keeps the reads while it has fewer than
 * [STICKY_IN_FLIGHT] in flight, so one user action reads with one account;
 * otherwise the least busy, then the one with the most requests left (an
 * account whose quota is unknown ties with the best known one rather than
 * beating it).
 *
 * XTA picks at random among the ties; this takes turns by [turn], which
 * spreads the reads as evenly and can be followed in a test.
 */
class XAccountSelector(
    private val accounts: List<XAccount>,
    private val flagged: (XAccount) -> Boolean = { false },
    private val rateLimited: (XAccount) -> Boolean = { false },
    private val remaining: (XAccount) -> Int? = { null },
    private val inFlight: (XAccount) -> Int = { 0 },
    private val spare: (XAccount) -> Boolean = { false },
    private val low: (XAccount) -> Boolean = { false },
    private val preferred: String? = null,
) {
    /**
     * The next account, healthy ones first; among each, the accounts left on
     * for the feeds before the [spare] ones the reader turned off for them,
     * which stand in once every account left on is limited or flagged.
     */
    fun pick(exclude: Set<String>, turn: Int = 0): XAccount? {
        val tier = best(accounts.filter { it.id !in exclude }) ?: return null
        tier.firstOrNull { it.id == preferred && inFlight(it) < STICKY_IN_FLIGHT }?.let { return it }
        val pool = leastUsed(tier)
        return pool[Math.floorMod(turn, pool.size)]
    }

    /** Whether the reads may stay with [id]: it is among the best accounts there are, however busy. */
    fun keeps(id: String?): Boolean = id != null && best(accounts)?.any { it.id == id } == true

    private fun best(left: List<XAccount>): List<XAccount>? {
        if (left.isEmpty()) return null
        val healthy = left.filter { !flagged(it) && !rateLimited(it) }
        val roomy = healthy.filterNot(low)
        return listOf(roomy.filterNot(spare), healthy.filterNot(spare), roomy, healthy).firstOrNull { it.isNotEmpty() } ?: preferringOn(left)
    }

    private fun preferringOn(pool: List<XAccount>): List<XAccount> = pool.filterNot(spare).ifEmpty { pool }

    private fun leastUsed(pool: List<XAccount>): List<XAccount> {
        val fewest = pool.minOf(inFlight)
        val idle = pool.filter { inFlight(it) == fewest }
        val best = idle.mapNotNull(remaining).maxOrNull() ?: return idle
        return idle.filter { (remaining(it) ?: best) == best }
    }

    companion object {
        /** Requests in flight that one account takes before a parallel read goes to another. */
        const val STICKY_IN_FLIGHT = 2
    }
}

/**
 * Per-account, per-endpoint rate limits, as XTA's RateLimitTracker keeps them
 * in memory: X limits each endpoint on its own, so an account refused 429 on
 * search still serves conversations. Besides 429s, the quota X reports with
 * every answer is followed, so an account is set aside once it is spent,
 * before X refuses it. Times are epoch milliseconds.
 */
class XRateLimits {
    private class Quota(val remaining: Int, val resetAt: Long, val limit: Int?)

    private val resets = HashMap<Pair<String, String>, Long>()
    private val quotas = HashMap<Pair<String, String>, Quota>()

    /** Where X itself refused with 429 and dated the end: asking again before it only spends another refusal. */
    private val refusals = HashMap<Pair<String, String>, Long>()

    @Synchronized
    fun isLimited(account: String, endpoint: String, now: Long): Boolean = (resets[account to endpoint] ?: 0) > now

    /** Whether X refused [account] on [endpoint] with a 429 whose dated reset is still ahead. */
    @Synchronized
    fun isRefused(account: String, endpoint: String, now: Long): Boolean = (refusals[account to endpoint] ?: 0) > now

    /** Requests left in the running window, or null when X reported none that still runs. */
    @Synchronized
    fun remaining(account: String, endpoint: String, now: Long): Int? = active(account, endpoint, now)?.remaining

    /**
     * Whether [account] is into the last [HEADROOM] of its quota on
     * [endpoint]: the reads move on to an account with more to spare before
     * X's limit is near. Unknown while X named no limit.
     */
    @Synchronized
    fun isLow(account: String, endpoint: String, now: Long): Boolean {
        val quota = active(account, endpoint, now) ?: return false
        val limit = quota.limit ?: return false
        return quota.remaining <= Math.ceil(limit * HEADROOM).toInt()
    }

    /** When [account] frees up on [endpoint], or null when it is not limited there. */
    @Synchronized
    fun resetAt(account: String, endpoint: String, now: Long): Long? = resets[account to endpoint]?.takeIf { it > now }

    /**
     * Sets [account] aside on [endpoint] until [resetAt]. [refused] when X
     * answered 429 and named that reset itself: the account is then not asked
     * there again before it, where a guessed or counted limit only orders it last.
     */
    @Synchronized
    fun flag(account: String, endpoint: String, resetAt: Long, refused: Boolean = false) {
        resets[account to endpoint] = resetAt
        if (refused) refusals[account to endpoint] = resetAt
    }

    @Synchronized
    fun clear(account: String, endpoint: String) {
        resets.remove(account to endpoint)
        quotas.remove(account to endpoint)
        refusals.remove(account to endpoint)
    }

    /**
     * Reads the quota X reported with an answer it served. A spent one is
     * flagged until it resets; an answer without one lifts the flag, as a
     * success always did. Requests still in flight were counted down already,
     * so an answer X wrote before they arrived does not hand them back.
     */
    @Synchronized
    fun record(account: String, endpoint: String, header: (String) -> String?, now: Long) {
        val reported = quotaOf(header)
        refusals.remove(account to endpoint)
        if (reported == null) {
            clear(account, endpoint)
            return
        }
        val known = active(account, endpoint, now)
        val left = if (known != null && known.resetAt == reported.resetAt && known.remaining < reported.remaining) known.remaining else reported.remaining
        set(account, endpoint, left, reported.resetAt, reported.limit)
    }

    /** Counts a request against the known quota as it is sent; an unknown quota stays unknown. */
    @Synchronized
    fun consume(account: String, endpoint: String, now: Long) {
        active(account, endpoint, now)?.let { set(account, endpoint, it.remaining - 1, it.resetAt, it.limit) }
    }

    /** The endpoints still limited for [account], with when each frees up. */
    @Synchronized
    fun activeFor(account: String, now: Long): Map<String, Long> =
        resets.filter { (key, reset) -> key.first == account && reset > now }.mapKeys { it.key.second }

    private fun set(account: String, endpoint: String, remaining: Int, resetAt: Long, limit: Int?) {
        quotas[account to endpoint] = Quota(remaining, resetAt, limit)
        if (remaining > 0) resets.remove(account to endpoint) else resets[account to endpoint] = resetAt
    }

    private fun active(account: String, endpoint: String, now: Long): Quota? = quotas[account to endpoint]?.takeIf { it.resetAt > now }

    private fun quotaOf(header: (String) -> String?): Quota? {
        val remaining = header("x-rate-limit-remaining")?.toIntOrNull() ?: return null
        val reset = resetSeconds(header("x-rate-limit-reset")) ?: return null
        return Quota(remaining, reset * 1000, header("x-rate-limit-limit")?.toIntOrNull()?.takeIf { it > 0 })
    }

    companion object {
        /** The share of X's limit left alone: the reads rotate on before it is spent. */
        const val HEADROOM = 0.2

        /** X's `x-rate-limit-reset`, in seconds, when it is one. */
        internal fun resetSeconds(value: String?): Long? =
            value?.toLongOrNull()?.takeIf { it in 1..XAccountPolicy.LATEST_RESET_SECONDS }
    }
}

/**
 * Requests in flight per account and endpoint, so a burst of parallel reads
 * spreads over the accounts instead of piling onto the one the quota alone
 * would pick. It describes this moment, not history.
 */
class XAccountLoad {
    private val inFlight = ConcurrentHashMap<Pair<String, String>, Int>()

    fun of(account: String, endpoint: String): Int = inFlight[account to endpoint] ?: 0

    fun begin(account: String, endpoint: String) {
        inFlight.merge(account to endpoint, 1, Int::plus)
    }

    fun end(account: String, endpoint: String) {
        inFlight.computeIfPresent(account to endpoint) { _, count -> (count - 1).takeIf { it > 0 } }
    }
}
