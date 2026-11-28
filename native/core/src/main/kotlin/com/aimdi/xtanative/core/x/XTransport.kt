package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XRequestBudget
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.core.x.http.withRequestBudget
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl

/**
 * Every request XTA Native sends X, as XTA's transport.dart sends them: over
 * the stored accounts in turn, healthy ones first, a guest's when there is no
 * account. A 429 sets the account aside on that endpoint until X says it
 * resets and asks one more; one X refused with a dated 429 is not asked
 * there again before that reset. Accounts the reader
 * turned off for the feeds stand in once every other one is limited, except
 * for the feeds' own reads ([XFeedRead]). A refusal (401, 403, 404) is tried on one more
 * account, and is only held against the account that gave it once another
 * account was answered on the same endpoint. An account flagged for it gets
 * no request until its cooldown ends, then a single probe. X's own failure
 * (500) is asked of the same account once more after a short pause; any
 * other failure is surfaced at once: another account would get the same.
 * Every read, whatever it tries,
 * has [budgetMillis] in all, as XTA's RequestBudget gives it.
 */
internal class XTransport(
    private val http: XHttp,
    private val accounts: () -> List<XAccount>,
    private val signers: XSigners,
    private val endpoints: XEndpoints,
    private val clock: () -> Long,
    private val guest: XGuest?,
    private val health: XAccountHealthStore,
    private val disabled: () -> Set<String>,
    private val budgetMillis: Long = XRequestBudget.READ_MILLIS,
    private val pacer: XPacer = XPacer(),
) {
    val limits = XRateLimits()
    private val load = XAccountLoad()
    private val probes = XProbes()

    /** Every X read paused at once, once X calls two accounts automated. */
    val pause = XReadPause()

    /** Each read, how it ended and how long it took, for Diagnostics. */
    val reads = XReadLog(clock = clock)

    /** Breaks ties when the reads move to another account. */
    private val turn = AtomicInteger()

    /**
     * The account the reads stay with while it has quota to spare: one user
     * action, one feed refresh, reads as one account, as a browser would.
     */
    @Volatile
    private var current: String? = null

    /** X reads in flight at once, all accounts together. */
    private val gate = Semaphore(MAX_IN_FLIGHT)

    private enum class Kind { ANSWER, LIMITED, REFUSED, OTHER }

    /** What an answer says of the account that asked: [code] is X's first error code, when it sent any. */
    private class Verdict(val kind: Kind, val code: Int?)

    /** A refusal held against [account] once another account is answered. */
    private class Refusal(val account: XAccount, val status: Int, val code: Int?)

    private sealed interface Outcome {
        class Answer(val body: J) : Outcome
        class Refused(val accounts: List<XAccount>) : Outcome
    }

    suspend fun graphql(endpoint: String, params: Map<String, String>): J = get(endpoints.url(endpoint, params), endpoint)

    suspend fun get(url: HttpUrl, what: String): J = reads.track(what) {
        withRequestBudget(budgetMillis, what) {
            pause.activeUntil(clock())?.let { throw XReadsPausedException(it) }
            val off = disabled()
            val pool = pool(off, feedOnly = currentCoroutineContext()[XFeedRead.Key] != null)
            if (pool.isEmpty()) return@withRequestBudget asGuest(what, url)
            // Every account tried refused with 404: surfaced at once, as XTA
            // does; a signer is derived again only when its lifetime ends.
            when (val outcome = rotate(pool, off, url, what)) {
                is Outcome.Answer -> outcome.body
                is Outcome.Refused -> throw refusal(outcome.accounts, what)
            }
        }
    }

    /**
     * [url] read with [account] alone, as XTA's fetchAs reads a chosen
     * account's own home timeline: no other account stands in for it.
     */
    suspend fun getAs(account: XAccount, url: HttpUrl, what: String): J = reads.track(what) {
        withRequestBudget(budgetMillis, what) {
            pause.activeUntil(clock())?.let { throw XReadsPausedException(it) }
            readAs(account, url, what)
        }
    }

    private suspend fun readAs(account: XAccount, url: HttpUrl, what: String): J {
        val path = url.encodedPath
        val now = clock()
        val state = health.read(account.id)
        // A resting account is not asked, not even for its own timeline; past its cooldown, one probe is.
        if (state.resting(now)) throw restingError(listOf(account), now)
        if (state.needsProbe(now) && !probes.claim(account.id, now)) throw XNoWorkingAccountException()
        val response = send(account, url, path)
        val verdict = verdictOf(account, response)
        if (state.needsProbe(now) && verdict.kind == Kind.REFUSED) refusedAgain(account, response.code, verdict.code)
        return when (verdict.kind) {
            Kind.ANSWER -> answered(account, emptyList(), path, response)
            Kind.LIMITED -> {
                flagLimited(account, path, response)
                throw XRateLimitedException(limits.resetAt(account.id, path, clock()))
            }
            Kind.REFUSED, Kind.OTHER -> throw XRequestException(response.code, "X answered $what for @${account.screenName} with ${response.code}")
        }
    }

    /**
     * A POST of [body] with one healthy account, as XTA's one-shot requests
     * (translation) pick one; with no account, the same request without an
     * account's headers, signed without a session, as XTA sends it then. XTA
     * signs it as a GET, as every request it makes; so does this.
     */
    suspend fun post(url: HttpUrl, body: String, contentType: String): XResponse = withRequestBudget(budgetMillis, url.encodedPath) {
        val now = clock()
        pause.activeUntil(now)?.let { throw XReadsPausedException(it) }
        val account = XAccountSelector(accounts().filterNot { health.read(it.id).let { state -> state.resting(now) || state.needsProbe(now) } })
            .pick(emptySet(), turn.getAndIncrement())
        val signer = signers.forSession(account?.cookie)
        val headers = (BASE_HEADERS - "content-type") + account?.headers.orEmpty() + ("x-client-transaction-id" to signer.transactionId("GET", url.encodedPath))
        http.post(url, headers, body, contentType).also { response -> if (account != null) verdictOf(account, response) }
    }

    /**
     * The accounts to read with: every one, those turned [off] for the feeds
     * asked after the rest; for a feed's own read only those left on, or all
     * when none is.
     */
    private fun pool(off: Set<String>, feedOnly: Boolean): List<XAccount> {
        val all = accounts()
        return if (feedOnly) all.filterNot { it.id in off }.ifEmpty { all } else all
    }

    private suspend fun rotate(pool: List<XAccount>, off: Set<String>, url: HttpUrl, what: String): Outcome {
        val path = url.encodedPath
        val now = clock()
        // A flagged or paused account gets no request while that lasts.
        val resting = pool.filter { health.read(it.id).resting(now) }
        if (resting.size == pool.size) throw restingError(resting, now)
        // X dated these refusals itself: another request before then is only refused again.
        val askable = (pool - resting.toSet()).filterNot { limits.isRefused(it.id, path, now) }
        if (askable.isEmpty()) throw XRateLimitedException(earliestReset(pool, path))
        val (probing, regular) = askable.partition { health.read(it.id).needsProbe(now) }
        var probe = probing.firstOrNull { probes.claim(it.id, now) }
        val sticky = current
        val selector = XAccountSelector(
            regular,
            rateLimited = { limits.isLimited(it.id, path, now) },
            remaining = { limits.remaining(it.id, path, now) },
            inFlight = { load.of(it.id, path) },
            spare = { it.id in off },
            low = { limits.isLow(it.id, path, now) },
            preferred = sticky,
        )
        // The reads move on only when the account they stay with is low, limited or gone; not when it is merely busy.
        val keeps = selector.keeps(sticky)
        val first = turn.getAndIncrement()
        val tried = HashSet<String>()
        // Held rather than blamed: only another account's answer proves them broken.
        val refused = ArrayList<Refusal>()
        var last: Kind? = null
        var lastStatus = 0
        // A 429 spends a try as a refusal does: one read never walks the pool on X's limits.
        var failed = 0
        while (failed < MAX_TRIES) {
            val probed = probe
            probe = null
            val account = probed ?: selector.pick(tried, first) ?: break
            tried += account.id
            limits.consume(account.id, path, now)
            val response = sendAgainOnServerError(account, url, path)
            val verdict = verdictOf(account, response)
            when {
                verdict.kind == Kind.ANSWER -> {
                    if (!keeps && probed == null) current = account.id
                    return Outcome.Answer(answered(account, refused, path, response))
                }
                // A probe refused is the old flag again, at no cost to the read.
                probed != null && verdict.kind == Kind.REFUSED -> {
                    refusedAgain(account, response.code, verdict.code)
                    continue
                }
                verdict.kind == Kind.LIMITED -> flagLimited(account, path, response)
                verdict.kind == Kind.REFUSED -> refused += Refusal(account, response.code, verdict.code)
                else -> throw XRequestException(response.code, "X answered $what with ${response.code}")
            }
            if (account.id == current) current = null
            failed++
            last = verdict.kind
            lastStatus = response.code
        }
        return when {
            last == Kind.LIMITED -> throw XRateLimitedException(earliestReset(pool, path))
            last == null -> throw XNoWorkingAccountException()
            lastStatus == 404 -> Outcome.Refused(refused.map { it.account })
            else -> throw XRequestException(lastStatus, "X refused $what ($lastStatus)")
        }
    }

    /**
     * [send], and once more with the same account after [SERVER_RETRY_MILLIS]
     * when X failed on its side with a status [XHttp] did not already try
     * again (500, 505 and up), if the read has the time. Never another
     * account: X's failure is no account's, and spreading it would only
     * spend more of them.
     */
    private suspend fun sendAgainOnServerError(account: XAccount, url: HttpUrl, path: String): XResponse {
        val first = send(account, url, path)
        if (!triedAgainHere(first.code)) return first
        val left = currentCoroutineContext()[XRequestBudget]?.remainingMillis
        if (left != null && left < 2 * SERVER_RETRY_MILLIS) return first
        delay(SERVER_RETRY_MILLIS)
        return send(account, url, path)
    }

    private suspend fun send(account: XAccount, url: HttpUrl, path: String): XResponse {
        val signer = signers.forSession(account.cookie)
        val headers = BASE_HEADERS + account.headers + ("x-client-transaction-id" to signer.transactionId("GET", path))
        load.begin(account.id, path)
        try {
            pacer.await(account.id)
            return gate.withPermit { http.get(url, headers, followRedirects = true) }
        } finally {
            load.end(account.id, path)
        }
    }

    private fun answered(account: XAccount, refused: List<Refusal>, path: String, response: XResponse): J {
        limits.record(account.id, path, response::header, clock())
        probes.settled(account.id)
        if (!health.read(account.id).isClean) health.write(account.id, XAccountHealth())
        refused.forEach { health.write(it.account.id, health.read(it.account.id).afterRefusal(clock(), it.status, it.code)) }
        return J.parse(response.body)
    }

    /** A probed account refused once more: its cooldown starts again, its count stays. */
    private fun refusedAgain(account: XAccount, status: Int, code: Int?) {
        probes.settled(account.id)
        health.write(account.id, health.read(account.id).afterRefusal(clock(), status, code))
    }

    /**
     * What [response] says of [account]: X's error codes before the HTTP
     * status, since X sends some with a 200. One calling the account
     * automated rests it and ends the read: no other account is asked.
     */
    private fun verdictOf(account: XAccount, response: XResponse): Verdict {
        val codes = XErrorCodes.of(response)
        codes.firstOrNull { it in XErrorCodes.AUTOMATION }?.let { throw automated(account, response.code, it) }
        val kind = when {
            XErrorCodes.RATE_LIMITED in codes || response.code == 429 -> Kind.LIMITED
            XErrorCodes.CSRF_MISMATCH in codes || response.code in REFUSALS -> Kind.REFUSED
            response.isSuccessful -> Kind.ANSWER
            else -> Kind.OTHER
        }
        return Verdict(kind, codes.firstOrNull())
    }

    /** [account] rests an hour; a second account within the window pauses every read. */
    private fun automated(account: XAccount, status: Int, code: Int): XRateLimitedException {
        val now = clock()
        probes.settled(account.id)
        health.write(account.id, health.read(account.id).afterAutomation(now, status, code))
        return pause.report(account.id, now)?.let(::XReadsPausedException) ?: XAutomationSuspectedException(code, null)
    }

    /** Why nothing was sent: every account in [resting] is paused or flagged; a pause ends first, so it is named. */
    private fun restingError(resting: List<XAccount>, now: Long): XReadException {
        val paused = resting.map { health.read(it.id) }.filter { it.paused(now) }.minByOrNull { it.pausedUntil!! }
            ?: return XNoWorkingAccountException()
        return XAutomationSuspectedException(paused.lastCode ?: 0, paused.pausedUntil)
    }

    /** No account proved the endpoint works: the accounts are only blamed when each was flagged before. */
    private fun refusal(refused: List<XAccount>, what: String): XReadException {
        val now = clock()
        return if (refused.isNotEmpty() && refused.all { health.read(it.id).flagged(now) }) XNoWorkingAccountException() else XEndpointRefusedException(what)
    }

    /** [account]'s 429 on [path]: until the reset X named, or X's usual fifteen minutes when it named none. */
    private fun flagLimited(account: XAccount, path: String, response: XResponse) {
        val dated = XRateLimits.resetSeconds(response.header("x-rate-limit-reset"))?.times(1000)
        limits.flag(account.id, path, dated ?: (clock() + XAccountPolicy.RATE_LIMIT_FALLBACK_MILLIS), refused = dated != null)
    }

    /** When the first account frees up here; null while one of them is not limited at all. */
    private fun earliestReset(pool: List<XAccount>, path: String): Long? {
        val now = clock()
        val resets = pool.map { limits.resetAt(it.id, path, now) }
        return if (resets.any { it == null }) null else resets.filterNotNull().minOrNull()
    }

    /**
     * The read as a guest, with what XTA's fetchUnauthenticated sends and no
     * more: X's logged-out bearer, a guest token, and no signature. A refused
     * token is replaced once; the quota X reports decides whether the token is
     * kept for the next read. Any answer but X's content, a 429 too, means an
     * account has to be added.
     */
    private suspend fun asGuest(what: String, url: HttpUrl): J {
        val guest = guest ?: throw XNoAccountException()
        suspend fun send(fresh: Boolean) = http.get(url, GUEST_HEADERS + ("x-guest-token" to guest.token(fresh)), followRedirects = true)
        val first = send(fresh = false)
        val response = if (first.code == 401 || first.code == 403) send(fresh = true) else first
        guest.observe(response::header)
        if (!response.isSuccessful) throw XNoAccountException("X did not answer $what for a guest (${response.code})")
        return J.parse(response.body)
    }

    companion object {
        private val REFUSALS = setOf(401, 403, 404)

        /** The pause before a server error is asked of the same account once more. */
        const val SERVER_RETRY_MILLIS = 1_000L

        /** X's own failures this transport tries once more: not 501, which no retry changes, nor 502–504, which [XHttp] already did. */
        internal fun triedAgainHere(code: Int): Boolean = code >= 500 && code != 501 && code !in 502..504

        /** A refusal or a 429 is tried on one more account; then it is surfaced. */
        private const val MAX_TRIES = 2

        /** X reads in flight at once: a refresh's searches queue here rather than reach X in one burst. */
        const val MAX_IN_FLIGHT = 4

        private const val BEARER =
            "Bearer AAAAAAAAAAAAAAAAAAAAANRILgAAAAAAnNwIzUejRCOuH5E6I8xnZz4puTs%3D1Zv7ttfk8LF81IUq16cHjhLTvJu4FA33AGWWjCpTnA"

        /** What every signed-in request carries before the account's own headers (XTA's headers.dart). */
        val BASE_HEADERS = mapOf(
            "accept" to "*/*",
            "accept-language" to "en-US,en;q=0.9",
            "authorization" to BEARER,
            "cache-control" to "no-cache",
            "content-type" to "application/json",
            "pragma" to "no-cache",
            "priority" to "u=1, i",
            "referer" to "https://x.com/",
            "user-agent" to "Mozilla/5.0 (Linux; Android 10; K) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/123.0.0.0 Mobile Safari/537.3",
            "x-twitter-active-user" to "yes",
            "x-twitter-client-language" to "en",
        )

        /** What a guest read carries beside its token (XTA's fetchUnauthenticated). */
        private val GUEST_HEADERS = mapOf(
            "authorization" to XGuest.BEARER,
            "x-twitter-active-user" to "yes",
            "user-agent" to BASE_HEADERS.getValue("user-agent"),
        )
    }
}

/**
 * Marks a read the Following feed or a group sends: it keeps to the accounts
 * the reader left on for the feeds, as the setting promises, where any other
 * read lets the accounts turned off stand in.
 */
internal object XFeedRead : AbstractCoroutineContextElement(XFeedRead.Key) {
    object Key : CoroutineContext.Key<XFeedRead>
}

/** [block] as a feed's read ([XFeedRead]). */
internal suspend fun <T> asFeedRead(block: suspend () -> T): T = withContext(XFeedRead) { block() }
