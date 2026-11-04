package com.aimdi.xtanative.networks.stocks

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.networks.Page
import java.net.URI
import java.time.Instant
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * One entry of the price strip at the foot of a post that names tickers:
 * the cashtag, its row state, and the figures that state shows.
 */
data class CashtagEntry(val symbol: String, val state: StockRowState, val quote: TickerQuote?) {
    /** The price beside the symbol, live or the last close; null while loading or without data ("No price"). */
    val price: Double? get() = quote?.displayPrice?.takeIf { state == StockRowState.LIVE || state == StockRowState.CLOSED }

    /** The move after the price, measured as the quote's [TickerQuote.changeWindow] says. */
    val changePercent: Double? get() = if (price == null) null else quote?.changePercent

    /** The day's line, only for a live market with two bars or more. */
    val sparkline: List<Double> get() = if (state == StockRowState.LIVE && quote != null && quote.points.size >= 2) quote.points.map { it.close } else emptyList()

    /** A closed market's price and move are drawn in the secondary colour: they are not news. */
    val muted: Boolean get() = state == StockRowState.CLOSED
}

/** The cashtags X marks in a post, and the price strip XTA draws under it (concept C of the Stocks design). */
object Cashtags {
    /** How many of a post's cashtags the strip shows: a post listing twenty tickers is an advert. */
    const val STRIP_LIMIT = 4

    /** The cashtags X attached to [post] (its entities, not its letters), upper-cased, each once, in order. */
    fun of(post: XPost): List<String> = post.entities.filterIsInstance<XEntity.Cashtag>().map { it.symbol.uppercase() }.distinct()

    /** The symbols the strip under [post] asks prices for: the first [STRIP_LIMIT] cashtags of the post as shown. */
    fun stripSymbols(post: XPost): List<String> = of(post.shown).take(STRIP_LIMIT)

    /**
     * The strip under [post] at [now], from the quotes at hand and the symbols
     * whose lookup [failed]. Empty for a post without cashtags; a quoted post
     * inside another has none either, which the card decides.
     */
    fun strip(post: XPost, quotes: Map<String, TickerQuote>, failed: Set<String>, now: Instant): List<CashtagEntry> =
        entries(stripSymbols(post), quotes, failed, now)

    /** The strip's entries for [symbols], at most [STRIP_LIMIT], from the quotes at hand and the symbols whose lookup [failed]. */
    fun entries(symbols: List<String>, quotes: Map<String, TickerQuote>, failed: Set<String>, now: Instant): List<CashtagEntry> =
        symbols.take(STRIP_LIMIT).map { symbol -> CashtagEntry(symbol, QuoteRows.state(quotes[symbol], symbol in failed, now), quotes[symbol]) }
}

/**
 * Posts shaped like a trading-signal group's: an invite to a Telegram channel
 * and three or more cashtags. They lead a cashtag feed by volume, not by what
 * they say about any one ticker, so XTA puts them last under a banner.
 */
object SignalGroups {
    /** Where signal groups invite readers to. */
    val TELEGRAM_HOSTS: Set<String> = setOf("t.me", "telegram.me", "telegram.dog")

    const val MIN_CASHTAGS = 3

    /** Whether [post] links a Telegram channel and carries at least [MIN_CASHTAGS] cashtags. */
    fun isSignalPost(post: XPost): Boolean =
        post.entities.filterIsInstance<XEntity.Link>().any { isTelegram(it.expanded) } && Cashtags.of(post).size >= MIN_CASHTAGS

    /** [threads] with the signal-group ones moved to the end, both groups in their order. Nothing is left out. */
    fun last(threads: List<XThread>): List<XThread> {
        val (signal, others) = threads.partition(::isSignal)
        return others + signal
    }

    /** A page of the Stocks posts feed (Tweets, Trend, a token's page) from X's search answer: its signal-group threads last. */
    fun page(timeline: XTimelinePage): Page<XThread> = Page(last(timeline.threads), timeline.bottomCursor)

    /** How many cashtags [thread]'s signal-group post carries, for its banner; null when it has none. */
    fun cashtags(thread: XThread): Int? = thread.posts.firstOrNull(::isSignalPost)?.let { Cashtags.of(it).size }

    private fun isSignal(thread: XThread): Boolean = thread.posts.any(::isSignalPost)

    private fun isTelegram(url: String): Boolean {
        val host = url.toHttpUrlOrNull()?.host ?: runCatching { URI(url).host }.getOrNull() ?: return false
        return host.lowercase().removePrefix("www.") in TELEGRAM_HOSTS
    }
}
