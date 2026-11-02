package com.aimdi.xtanative.core.x

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import java.util.concurrent.atomic.AtomicInteger
import okhttp3.HttpUrl

/**
 * Every request XTA Native sends X, as XTA's transport.dart sends them: over
 * the stored accounts in turn, healthy ones first, a guest's when there is no
 * account. A 429 sets the account aside on that endpoint until X says it
 * resets and asks the next; a refusal (401, 403, 404) is tried on one more
 * account, and is only held against the account that gave it once another
 * account was answered on the same endpoint. Any other failure is surfaced at
 * once: another account would get the same.
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
) {
    val limits = XRateLimits()
    private val load = XAccountLoad()

    /** Reads take turns across the accounts, so no one account reaches X's limits first. */
    private val turn = AtomicInteger()

    private sealed interface Outcome {
        class Answer(val body: J) : Outcome
        class Refused(val accounts: List<XAccount>) : Outcome
    }

    suspend fun graphql(endpoint: String, params: Map<String, String>): J = get(endpoints.url(endpoint, params), endpoint)

    suspend fun get(url: HttpUrl, what: String): J {
        val pool = pool()
        if (pool.isEmpty()) return asGuest(what, url)
        return when (val first = rotate(pool, url, what)) {
            is Outcome.Answer -> first.body
            // Every account tried was refused with 404: X answers a stale
            // signature that way, so the signers are derived again once.
            is Outcome.Refused -> {
                signers.clear()
                when (val second = rotate(pool, url, what)) {
                    is Outcome.Answer -> second.body
                    is Outcome.Refused -> throw refusal(second.accounts, what)
                }
            }
        }
    }

    /**
     * [url] read with [account] alone, as XTA's fetchAs reads a chosen
     * account's own home timeline: no other account stands in for it.
     */
    suspend fun getAs(account: XAccount, url: HttpUrl, what: String): J {
        val path = url.encodedPath
        val response = send(account, url, path)
        return when {
            response.isSuccessful -> answered(account, emptyList(), path, response)
            response.code == 429 -> {
                limits.flag(account.id, path, resetOf(response))
                throw XRateLimitedException(limits.resetAt(account.id, path, clock()))
            }
            else -> throw XRequestException(response.code, "X answered $what for @${account.screenName} with ${response.code}")
        }
    }

    /**
     * A POST of [body] with one healthy account, as XTA's one-shot requests
     * (translation) pick one. XTA signs it as a GET, as every request it
     * makes; so does this.
     */
    suspend fun post(url: HttpUrl, body: String, contentType: String): XResponse {
        val now = clock()
        val account = XAccountSelector(accounts(), flagged = { health.read(it.id).flagged(now) }).pick(emptySet(), turn.getAndIncrement())
            ?: throw XNoAccountException()
        val signer = signers.forSession(account.cookie)
        val headers = (BASE_HEADERS - "content-type") + account.headers + ("x-client-transaction-id" to signer.transactionId("GET", url.encodedPath))
        return http.post(url, headers, body, contentType)
    }

    /** The accounts to read with: those the reader left on for feeds, or all when none is. */
    private fun pool(): List<XAccount> {
        val all = accounts()
        val off = disabled()
        return all.filterNot { it.id in off }.ifEmpty { all }
    }

    private suspend fun rotate(pool: List<XAccount>, url: HttpUrl, what: String): Outcome {
        val path = url.encodedPath
        val now = clock()
        val selector = XAccountSelector(
            pool,
            flagged = { health.read(it.id).flagged(now) },
            rateLimited = { limits.isLimited(it.id, path, now) },
            remaining = { limits.remaining(it.id, path, now) },
            inFlight = { load.of(it.id, path) },
        )
        val first = turn.getAndIncrement()
        val tried = HashSet<String>()
        // Held rather than blamed: only another account's answer proves them broken.
        val refused = ArrayList<XAccount>()
        var last: XResponse? = null
        while (refused.size < MAX_REFUSALS) {
            val account = selector.pick(tried, first) ?: break
            tried += account.id
            limits.consume(account.id, path, now)
            val response = send(account, url, path)
            when {
                response.isSuccessful -> return Outcome.Answer(answered(account, refused, path, response))
                response.code == 429 -> limits.flag(account.id, path, resetOf(response))
                response.code in REFUSALS -> refused += account
                else -> throw XRequestException(response.code, "X answered $what with ${response.code}")
            }
            last = response
        }
        return when (val code = last?.code) {
            429 -> throw XRateLimitedException(earliestReset(pool, path))
            404 -> Outcome.Refused(refused)
            else -> throw XRequestException(code ?: 0, "X refused $what ($code)")
        }
    }

    private suspend fun send(account: XAccount, url: HttpUrl, path: String): XResponse {
        val signer = signers.forSession(account.cookie)
        val headers = BASE_HEADERS + account.headers + ("x-client-transaction-id" to signer.transactionId("GET", path))
        load.begin(account.id, path)
        try {
            return http.get(url, headers, followRedirects = true)
        } finally {
            load.end(account.id, path)
        }
    }

    private fun answered(account: XAccount, refused: List<XAccount>, path: String, response: XResponse): J {
        limits.record(account.id, path, response::header, clock())
        if (!health.read(account.id).isClean) health.write(account.id, XAccountHealth())
        refused.forEach { health.write(it.id, health.read(it.id).afterRefusal(clock())) }
        return J.parse(response.body)
    }

    /** No account proved the endpoint works: the accounts are only blamed when each was flagged before. */
    private fun refusal(refused: List<XAccount>, what: String): XReadException {
        val now = clock()
        return if (refused.isNotEmpty() && refused.all { health.read(it.id).flagged(now) }) XNoWorkingAccountException() else XEndpointRefusedException(what)
    }

    private fun resetOf(response: XResponse): Long =
        XRateLimits.resetSeconds(response.header("x-rate-limit-reset"))?.times(1000) ?: (clock() + XAccountPolicy.RATE_LIMIT_FALLBACK_MILLIS)

    /** When the first account frees up here; null while one of them is not limited at all. */
    private fun earliestReset(pool: List<XAccount>, path: String): Long? {
        val now = clock()
        val resets = pool.map { limits.resetAt(it.id, path, now) }
        return if (resets.any { it == null }) null else resets.filterNotNull().minOrNull()
    }

    /**
     * The read as a guest: X's logged-out bearer, a guest token, and a
     * signature derived without a session. A refused token is replaced once;
     * the quota X reports decides whether the token is kept for the next read.
     */
    private suspend fun asGuest(what: String, url: HttpUrl): J {
        val guest = guest ?: throw XNoAccountException()
        val signer = signers.forSession(null)
        suspend fun send(fresh: Boolean) = http.get(
            url,
            BASE_HEADERS + mapOf(
                "authorization" to XGuest.BEARER,
                "x-guest-token" to guest.token(fresh),
                "x-client-transaction-id" to signer.transactionId("GET", url.encodedPath),
            ),
            followRedirects = true,
        )
        val first = send(fresh = false)
        val response = if (first.code == 401 || first.code == 403) send(fresh = true) else first
        guest.observe(response::header)
        return when {
            response.isSuccessful -> J.parse(response.body)
            response.code == 429 -> throw XRateLimitedException()
            else -> throw XNoAccountException("X did not answer $what for a guest (${response.code})")
        }
    }

    companion object {
        private val REFUSALS = setOf(401, 403, 404)

        /** A refusal is tried on one more account; then it is surfaced, as XTA does. */
        private const val MAX_REFUSALS = 2

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
    }
}
