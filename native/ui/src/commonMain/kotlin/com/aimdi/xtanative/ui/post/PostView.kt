package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.ai.XTranslator
import com.aimdi.xtanative.services.links.LinkPreviewLayout
import com.aimdi.xtanative.services.links.LinkPreviews
import com.aimdi.xtanative.services.links.XLinks

/** A line above a card's author, as XTA's tweet.dart draws them, in its order. */
sealed interface ContextLine {
    /** "Maya reposted 3h": the reposter's profile opens on a tap. */
    data class Reposted(val by: XUser) : ContextLine

    data object Pinned : ContextLine

    /** The first post of a thread X groups. */
    data object Thread : ContextLine

    /** The community the post was written in; X names only its id. */
    data class Community(val id: String) : ContextLine {
        val url: String get() = "https://x.com/i/communities/$id"
    }
}

/** What stands in a card for the post it quotes. */
sealed interface QuoteBlock {
    data class Shown(val post: XPost) : QuoteBlock

    /**
     * X would not give it: [message] is X's own reason, or null for the
     * plain "unavailable"; [archiveUrl] finds it in the Wayback Machine when
     * its author is known.
     */
    data class Withheld(val message: String?, val subscribersOnly: Boolean, val archiveUrl: String?) : QuoteBlock

    /** X named the quoted post without sending it: a tap opens it. */
    data class Unsent(val id: String) : QuoteBlock
}

/** The decisions behind one post card, as XTA's tweet.dart makes them. */
object PostView {
    /** How many of a post's cashtags the price strip shows: a post listing twenty is an advert. */
    const val CASHTAG_LIMIT = 4

    /** Lines a card draws before which, from what XTA shows: the repost, pinned, the thread, the community. */
    fun contextLines(post: XPost, pinned: Boolean, firstOfThread: Boolean, quoted: Boolean): List<ContextLine> = listOfNotNull(
        post.reposted?.let { ContextLine.Reposted(post.author) },
        ContextLine.Pinned.takeIf { pinned },
        ContextLine.Thread.takeIf { firstOfThread },
        post.shown.communityId?.takeIf { !quoted && it.isNotEmpty() }?.let(ContextLine::Community),
    )

    /**
     * Whom [shown] answers, for "Replying to @name" under its header: never in
     * a quote, nor mid-thread, where the post answered is just above.
     */
    fun replyingTo(shown: XPost, quoted: Boolean, midThread: Boolean): String? =
        shown.replyToScreenName?.takeIf { !quoted && !midThread && it.isNotEmpty() }

    /** An X article link the post carries without its article: shown as a card, not a cut-off address. */
    fun articleLink(shown: XPost): String? =
        if (shown.article != null) null else XLinks.firstArticleLink(shown.entities.filterIsInstance<XEntity.Link>().map { it.expanded })

    /** The tickers the price strip under a post names: its first cashtags, upper-cased, each once; none in a quote. */
    fun cashtags(shown: XPost, quoted: Boolean): List<String> =
        if (quoted) emptyList() else shown.entities.filterIsInstance<XEntity.Cashtag>().map { it.symbol.uppercase() }.distinct().take(CASHTAG_LIMIT)

    /** What stands for the post [shown] quotes, or null when it quotes none; a quote's own quote is never drawn. */
    fun quote(shown: XPost, quoted: Boolean): QuoteBlock? {
        if (quoted) return null
        shown.quoted?.let { return QuoteBlock.Shown(it) }
        val withheld = shown.quotedWithheld ?: return null
        return if (withheld.reason == null && !withheld.subscribersOnly) {
            QuoteBlock.Unsent(withheld.id)
        } else {
            QuoteBlock.Withheld(withheld.reason, withheld.subscribersOnly, withheld.screenName?.let { XLinks.waybackUrl(it, withheld.id) })
        }
    }

    /** Whether a post offers translation into [appLanguage]: never an article, nor a post already in it. */
    /**
     * Whether a link card draws its picture large (_card.dart): X's own
     * website and event cards always; a large-image preview, or another
     * network's link, only where the picture is what the link is (a video
     * site, LinkPreviews); a player as a row beside its picture, never.
     */
    fun drawsLargeCard(card: XCard.Link): Boolean {
        if (!card.large || card.imageUrl == null) return false
        return when (card.kind) {
            "player" -> false
            "summary_large_image", "link" -> LinkPreviews.layoutFor(card.url, hasImage = true) == LinkPreviewLayout.LARGE
            else -> true
        }
    }

    fun offersTranslation(shown: XPost, appLanguage: String): Boolean = shown.article == null && XTranslator.offers(shown.lang, appLanguage)

    /**
     * Whether the post's broadcast or Space shows as its own block. A
     * broadcast whose recording is the post's video is that video already.
     */
    fun showsLive(shown: XPost): Boolean {
        val live = shown.live ?: return false
        return live.isSpace || shown.media.none { it.kind == XMedia.Kind.VIDEO }
    }

    /** Whether the post's media, card and quote stay behind the sensitive gate. */
    fun gated(shown: XPost, hidden: Boolean): Boolean = hidden && shown.sensitive && shown.media.isNotEmpty()

    /** The address a post is shared and opened in a browser as. */
    fun url(post: XPost, prefs: PostPrefs): String = XLinks.shareUrl(post.shown, prefs.shareBase) ?: post.shown.url

    /** Whether [post]'s author is drawn: not with XTA's non-confirmation-bias mode, except on their own profile. */
    fun showsAuthor(post: XPost, prefs: PostPrefs, profileScreenName: String?): Boolean =
        !prefs.hideAuthors || post.author.screenName.equals(profileScreenName, ignoreCase = true)
}

/** The numbers under a card, as XTA's footer writes them; null for one not shown. */
data class FooterCounts(val replies: Long?, val reposts: Long?, val likes: Long?, val views: Long?) {
    companion object {
        /**
         * The counts of [post]: none in zen or calm mode. Reposts and quotes
         * count together, as X counts them. Views show only when X counted
         * some, and not on an opened post, whose line above says them.
         */
        fun of(post: XPost, prefs: PostPrefs, showViews: Boolean): FooterCounts {
            if (prefs.hideCounts) return FooterCounts(null, null, null, null)
            return FooterCounts(
                replies = post.replyCount,
                reposts = post.repostCount + post.quoteCount,
                likes = post.likeCount,
                views = post.viewCount?.takeIf { showViews && it > 0 },
            )
        }
    }
}

/**
 * What a post's footer affords at the width it has, as XTA's
 * resolveFooterFit decides it (tweet_footer.dart): the views go first, for
 * they open nothing, then the counts' numbers; when not even the bare glyphs
 * fit, the buttons wrap onto a second line rather than shrink.
 */
data class FooterFit(val showCounts: Boolean, val showViews: Boolean, val wraps: Boolean) {
    companion object {
        /** Around a count's glyph and number, as the app's counts draw them (NetworkCount). */
        const val PADDING = 4f
        const val GLYPH = 17f

        /** Between a count's glyph and its number. */
        const val GAP = 4f

        /** The bookmark's and the share button's side. */
        const val BUTTON = 40f

        /** Between the counts and the bookmark and share at the end. */
        const val GROUP_GAP = 8f

        /** How wide a count is with a number [label] wide (0 for none). */
        fun countWidth(label: Float): Float = PADDING * 2 + GLYPH + if (label > 0f) GAP + label else 0f

        /**
         * The fit in [available] width of counts whose numbers are [labels]
         * wide and a view count [views] wide (null for none), all in dp.
         */
        fun of(available: Float, labels: List<Float>, views: Float?): FooterFit {
            if (views != null && width(labels + views) <= available) return FooterFit(showCounts = true, showViews = true, wraps = false)
            if (width(labels) <= available) return FooterFit(showCounts = true, showViews = false, wraps = false)
            return FooterFit(showCounts = false, showViews = false, wraps = width(labels.map { 0f }) > available)
        }

        private fun width(labels: List<Float>): Float = labels.sumOf { countWidth(it).toDouble() }.toFloat() + 2 * BUTTON + GROUP_GAP
    }
}

/**
 * Posts in a row that read as one reposted run, as XTA's boost_runs.dart
 * folds them when "collapse reposts" is on: two or more reposts in a row.
 */
object BoostRuns {
    fun isBoost(thread: com.aimdi.xtanative.core.x.model.XThread): Boolean = thread.posts.firstOrNull()?.isRepost == true

    /** A row of the list: a thread, or a run of reposts drawn as one carousel. */
    sealed interface Item {
        data class Single(val thread: com.aimdi.xtanative.core.x.model.XThread) : Item

        data class Run(val threads: List<com.aimdi.xtanative.core.x.model.XThread>) : Item {
            val key: String get() = "boosts-" + threads.first().id
        }
    }

    fun collapse(threads: List<com.aimdi.xtanative.core.x.model.XThread>): List<Item> {
        val out = ArrayList<Item>()
        var index = 0
        while (index < threads.size) {
            if (!isBoost(threads[index])) {
                out += Item.Single(threads[index++])
                continue
            }
            val end = (index until threads.size).firstOrNull { !isBoost(threads[it]) } ?: threads.size
            val run = threads.subList(index, end)
            out += if (run.size >= 2) Item.Run(run.toList()) else Item.Single(run.single())
            index = end
        }
        return out
    }

    /** The first line or so of a reposted post, as text: X's escapes undone, line breaks made spaces. */
    fun preview(text: String): String {
        val trimmed = com.aimdi.xtanative.core.x.parse.XText.unescape(text).replace('\n', ' ').trim()
        return if (trimmed.length <= 80) trimmed else trimmed.take(77) + "…"
    }
}
