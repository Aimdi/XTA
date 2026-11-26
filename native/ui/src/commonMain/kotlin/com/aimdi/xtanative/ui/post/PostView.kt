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
import kotlin.math.roundToInt

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

    /** X said the post quotes another and named none: it could not be read. */
    data object Missing : QuoteBlock
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

    /** [lines] as a community's own page shows them: its posts do not each say they are in [community], the page it is. */
    fun inCommunityPage(lines: List<ContextLine>, community: String?): List<ContextLine> =
        if (community == null) lines else lines.filterNot { it is ContextLine.Community && it.id == community }

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
        return if (withheld.id.isEmpty()) {
            QuoteBlock.Missing
        } else if (withheld.reason == null && !withheld.subscribersOnly) {
            QuoteBlock.Unsent(withheld.id)
        } else {
            QuoteBlock.Withheld(withheld.reason, withheld.subscribersOnly, withheld.screenName?.let { XLinks.waybackUrl(it, withheld.id) })
        }
    }

    /**
     * Whether X sent a post with nothing to draw: no text and nothing under
     * it, which XTA says ("did not contain any text") rather than leave an
     * empty card.
     */
    fun textMissing(shown: XPost): Boolean =
        shown.text.isBlank() && shown.media.isEmpty() && shown.card == null && shown.quoted == null && shown.quotedWithheld == null &&
            shown.article == null && shown.live == null && shown.grokShare == null && shown.note == null

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
 * fit, the buttons wrap onto a second line rather than shrink. It is decided
 * from the counts as the footer's layout measured them, so no card measures
 * text or composes twice to know its width; all widths here are pixels.
 */
data class FooterFit(val showCounts: Boolean, val showViews: Boolean, val wraps: Boolean) {
    companion object {
        /** Around the views' glyph and number, as the counts that are buttons pad theirs. */
        const val PADDING = 4f

        /** The bookmark's and the share button's side. */
        const val BUTTON = 40f

        /** Between the counts and the bookmark and share at the end. */
        const val GROUP_GAP = 8f

        /** A footer on one line: the [counts], the [gap], then the bookmark and share, [end] wide together. */
        fun rowWidth(counts: List<Int>, end: Int, gap: Int): Int = counts.sum() + gap + end

        /**
         * The fit in [available] of counts drawn with their numbers, [counts]
         * wide, and the views [views] wide (null for none). Not showing the
         * counts means their numbers must go; whether the bare glyphs then
         * wrap is [bare]'s to say, once they are drawn so.
         */
        fun withNumbers(available: Int, counts: List<Int>, views: Int?, end: Int, gap: Int): FooterFit = when {
            views != null && rowWidth(counts + views, end, gap) <= available -> FooterFit(showCounts = true, showViews = true, wraps = false)
            rowWidth(counts, end, gap) <= available -> FooterFit(showCounts = true, showViews = false, wraps = false)
            else -> FooterFit(showCounts = false, showViews = false, wraps = false)
        }

        /** The fit in [available] of counts drawn without numbers, [counts] wide: on one line, or wrapped. */
        fun bare(available: Int, counts: List<Int>, end: Int, gap: Int): FooterFit =
            FooterFit(showCounts = false, showViews = false, wraps = rowWidth(counts, end, gap) > available)
    }
}

/** Where a footer's items go, in pixels from its start, as its Row and FlowRow placed them. */
object FooterPlacement {
    /**
     * The start of each of [counts] then each of [end] on one line
     * [available] wide: the counts spread from the start across what the end
     * buttons and the [gap] leave, the first at the start and the last at
     * that area's end; the end buttons at the end.
     */
    fun row(available: Int, counts: List<Int>, end: List<Int>, gap: Int): List<Int> {
        val area = (available - gap - end.sum()).coerceAtLeast(counts.sum())
        val between = if (counts.size > 1) (area - counts.sum()).toFloat() / (counts.size - 1) else 0f
        val countStarts = counts.indices.map { index -> (counts.take(index).sum() + between * index).roundToInt() }
        val endStarts = end.indices.map { index -> available - end.drop(index).sum() }
        return countStarts + endStarts
    }

    /**
     * [widths] wrapped onto lines [available] wide, each line as full as it
     * goes and its items spaced evenly, as a FlowRow with SpaceEvenly lays
     * them: per line, the indices it holds and their starts.
     */
    fun flow(available: Int, widths: List<Int>): List<List<Pair<Int, Int>>> {
        val lines = ArrayList<List<Int>>()
        var line = ArrayList<Int>()
        widths.indices.forEach { index ->
            if (line.isNotEmpty() && line.sumOf { widths[it] } + widths[index] > available) {
                lines += line
                line = ArrayList()
            }
            line += index
        }
        if (line.isNotEmpty()) lines += line
        return lines.map { indices ->
            val space = (available - indices.sumOf { widths[it] }).coerceAtLeast(0).toFloat() / (indices.size + 1)
            indices.mapIndexed { at, index -> index to (space * (at + 1) + indices.take(at).sumOf { widths[it] }).roundToInt() }
        }
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
