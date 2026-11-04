package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import java.io.InterruptedIOException
import java.time.Duration
import java.time.Instant
import kotlin.math.pow
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl

/**
 * What a failed price lookup says, in the terms XTA's quote code acts on: the
 * host has no such symbol, it is not answering for now, or it answered with
 * something that is not its format.
 */
enum class TickerErrorKind {
    NOT_FOUND,
    UNAVAILABLE,
    BAD_RESPONSE,
    ;

    companion object {
        /**
         * The kind of [error] a price read threw: a host's refusal
         * ([NetworkRequestException]) is [NOT_FOUND] when its status is that
         * host's "no such symbol" ([PriceHosts.notFoundCodes]), no connection
         * ([IOException]) is [UNAVAILABLE]. Null for anything that is not a
         * price host's failure, such as a cancellation.
         */
        fun of(error: Throwable): TickerErrorKind? = when (error) {
            is TickerException -> error.kind
            is NetworkRequestException -> if (error.code in PriceHosts.notFoundCodes(error.network)) NOT_FOUND else UNAVAILABLE
            is IOException -> UNAVAILABLE
            else -> null
        }
    }
}

/**
 * A price lookup that failed for a reason no single answer carries: an
 * unknown pair said in the body, an answer that is not the host's format, a
 * symbol no candidate name answered for, or every source refusing.
 */
class TickerException(val kind: TickerErrorKind, message: String, cause: Throwable? = null) : Exception(message, cause)

/**
 * The public price hosts by the name their failures carry, and which of their
 * statuses mean "no such symbol" rather than "not answering".
 */
object PriceHosts {
    const val YAHOO = "Yahoo Finance"
    const val COINBASE = "Coinbase"
    const val KRAKEN = "Kraken"
    const val BINANCE = "Binance"
    const val DEX_SCREENER = "DexScreener"
    const val GECKO_TERMINAL = "GeckoTerminal"

    /** Coinbase and Binance answer an unknown pair with 400; DexScreener's 404 is a refusal like any other. */
    fun notFoundCodes(host: String): Set<Int> = when (host) {
        COINBASE, BINANCE -> setOf(400, 404)
        DEX_SCREENER -> emptySet()
        else -> setOf(404)
    }
}

/**
 * Which price hosts are resting after a refusal, and which coins a host does
 * not list. Shared by every client ([shared]), so a host one screen found
 * refusing is left alone by the others too.
 */
class PriceSourceHealth(private val now: () -> Instant = Instant::now) {
    private val lock = Any()
    private val restUntil = HashMap<String, Instant>()
    private val refusals = HashMap<String, Int>()
    private val unlisted = HashSet<String>()

    /** Whether [source] refused lately and is left alone until its rest ends. */
    fun resting(source: String): Boolean = synchronized(lock) { restUntil[source]?.isAfter(now()) ?: false }

    /** Rests [source] for [FIRST_REST], twice as long after each refusal in a row, at most [LONGEST_REST]. */
    fun refused(source: String): Unit = synchronized(lock) {
        val count = (refusals[source] ?: 0) + 1
        refusals[source] = count
        val minutes = minOf(FIRST_REST.toMinutes() * 2.0.pow(count - 1), LONGEST_REST.toMinutes().toDouble()).toLong()
        restUntil[source] = now().plus(Duration.ofMinutes(minutes))
    }

    /** One answer ends the rest and forgets the refusals. */
    fun answered(source: String): Unit = synchronized(lock) {
        refusals.remove(source)
        restUntil.remove(source)
    }

    /** Whether [source] may have trades in [coin]: true until it said it has none. */
    fun lists(source: String, coin: String): Boolean = synchronized(lock) { "$source/$coin" !in unlisted }

    /** [source] has no trades in [coin]: it is not asked for it again this session. */
    fun unlisted(source: String, coin: String): Unit = synchronized(lock) { unlisted.add("$source/$coin") }

    companion object {
        val FIRST_REST: Duration = Duration.ofMinutes(2)
        val LONGEST_REST: Duration = Duration.ofMinutes(30)

        /** The health every client shares unless a test passes its own. */
        val shared = PriceSourceHealth()
    }
}

/** The one GET every public price host is read with, as XTA's price_http.dart sends it. */
internal object PriceHttp {
    /** How long a host may take to answer before it counts as not answering. */
    val TIMEOUT: Duration = Duration.ofSeconds(15)

    /**
     * [url]'s JSON from [host]. A status other than 200 is a
     * [NetworkRequestException]; no answer within [timeout] an
     * [InterruptedIOException]; a 200 that is not JSON a [TickerException]
     * of [TickerErrorKind.BAD_RESPONSE].
     */
    suspend fun getJson(http: XHttp, url: HttpUrl, host: String, userAgent: String, timeout: Duration = TIMEOUT): J {
        val response = withTimeoutOrNull(timeout.toMillis()) { http.get(url, mapOf("User-Agent" to userAgent), true) }
            ?: throw InterruptedIOException("${url.host} did not answer within ${timeout.seconds} s")
        if (response.code != 200) throw NetworkRequestException(host, response.code, "HTTP ${response.code} for ${url.host}${url.encodedPath}")
        return strictJson(response.body) ?: throw TickerException(TickerErrorKind.BAD_RESPONSE, "Response from ${url.host} was not JSON")
    }

    /** [text] as JSON, read strictly as XTA's jsonDecode reads it; null when it is not JSON. */
    fun strictJson(text: String): J? = runCatching { Json.parseToJsonElement(text) }.getOrNull()?.let(::J)
}

/**
 * [block]'s value, or the price host's failure it threw (anything
 * [TickerErrorKind.of] knows) as a failed [Result]. A bug or a cancellation
 * is thrown on.
 */
internal inline fun <T> priceResult(block: () -> T): Result<T> = try {
    Result.success(block())
} catch (error: Exception) {
    if (TickerErrorKind.of(error) == null) throw error
    Result.failure(error)
}

/** The kind of a failure [priceResult] caught. */
internal val Throwable.tickerKind: TickerErrorKind get() = TickerErrorKind.of(this) ?: TickerErrorKind.UNAVAILABLE
