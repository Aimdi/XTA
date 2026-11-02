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

    /** How long an account whose sign-in looks broken is tried last. */
    const val NOT_FOUND_COOLDOWN_MILLIS = 6 * 60 * 60 * 1000L

    /** Refusals in a row, each proven by another account, before an account is flagged. */
    const val NOT_FOUND_THRESHOLD = 3

    /** The latest reset X's headers may name, in seconds: beyond it a header is junk. */
    internal const val LATEST_RESET_SECONDS = 8_640_000_000_000L
}

/**
 * What XTA's accounts table remembers of an account's sign-in: how many
 * refusals in a row other accounts proved were its own, and since when it is
 * flagged. Flags only order accounts; a flagged one is still asked when no
 * other is left.
 */
data class XAccountHealth(val consecutiveNotFound: Int = 0, val notFoundAt: Long? = null) {
    val isClean: Boolean get() = consecutiveNotFound == 0 && notFoundAt == null

    fun flagged(now: Long): Boolean = notFoundAt?.let { now < it + XAccountPolicy.NOT_FOUND_COOLDOWN_MILLIS } ?: false

    /** One more refusal; the account is flagged once [XAccountPolicy.NOT_FOUND_THRESHOLD] are in a row. */
    fun afterRefusal(at: Long): XAccountHealth {
        val count = consecutiveNotFound + 1
        return XAccountHealth(count, if (count >= XAccountPolicy.NOT_FOUND_THRESHOLD) at else notFoundAt)
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
 * the rest, which are still asked when nothing else is left. Among those, the
 * least busy, then the one with the most requests left; an account whose
 * quota is unknown ties with the best known one rather than beating it.
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
) {
    fun pick(exclude: Set<String>, turn: Int = 0): XAccount? {
        val left = accounts.filter { it.id !in exclude }
        if (left.isEmpty()) return null
        val healthy = left.filter { !flagged(it) && !rateLimited(it) }
        val pool = leastUsed(healthy.ifEmpty { left })
        return pool[Math.floorMod(turn, pool.size)]
    }

    private fun leastUsed(pool: List<XAccount>): List<XAccount> {
        val fewest = pool.minOf(inFlight)
        val idle = pool.filter { inFlight(it) == fewest }
        val best = idle.mapNotNull(remaining).maxOrNull() ?: return idle
        return idle.filter { (remaining(it) ?: best) == best }
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
    private class Quota(val remaining: Int, val resetAt: Long)

    private val resets = HashMap<Pair<String, String>, Long>()
    private val quotas = HashMap<Pair<String, String>, Quota>()

    @Synchronized
    fun isLimited(account: String, endpoint: String, now: Long): Boolean = (resets[account to endpoint] ?: 0) > now

    /** Requests left in the running window, or null when X reported none that still runs. */
    @Synchronized
    fun remaining(account: String, endpoint: String, now: Long): Int? = active(account, endpoint, now)?.remaining

    /** When [account] frees up on [endpoint], or null when it is not limited there. */
    @Synchronized
    fun resetAt(account: String, endpoint: String, now: Long): Long? = resets[account to endpoint]?.takeIf { it > now }

    @Synchronized
    fun flag(account: String, endpoint: String, resetAt: Long) {
        resets[account to endpoint] = resetAt
    }

    @Synchronized
    fun clear(account: String, endpoint: String) {
        resets.remove(account to endpoint)
        quotas.remove(account to endpoint)
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
        if (reported == null) {
            clear(account, endpoint)
            return
        }
        val known = active(account, endpoint, now)
        val left = if (known != null && known.resetAt == reported.resetAt && known.remaining < reported.remaining) known.remaining else reported.remaining
        set(account, endpoint, left, reported.resetAt)
    }

    /** Counts a request against the known quota as it is sent; an unknown quota stays unknown. */
    @Synchronized
    fun consume(account: String, endpoint: String, now: Long) {
        active(account, endpoint, now)?.let { set(account, endpoint, it.remaining - 1, it.resetAt) }
    }

    /** The endpoints still limited for [account], with when each frees up. */
    @Synchronized
    fun activeFor(account: String, now: Long): Map<String, Long> =
        resets.filter { (key, reset) -> key.first == account && reset > now }.mapKeys { it.key.second }

    private fun set(account: String, endpoint: String, remaining: Int, resetAt: Long) {
        quotas[account to endpoint] = Quota(remaining, resetAt)
        if (remaining > 0) resets.remove(account to endpoint) else resets[account to endpoint] = resetAt
    }

    private fun active(account: String, endpoint: String, now: Long): Quota? = quotas[account to endpoint]?.takeIf { it.resetAt > now }

    private fun quotaOf(header: (String) -> String?): Quota? {
        val remaining = header("x-rate-limit-remaining")?.toIntOrNull() ?: return null
        val reset = resetSeconds(header("x-rate-limit-reset")) ?: return null
        return Quota(remaining, reset * 1000)
    }

    companion object {
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
