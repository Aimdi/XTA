package com.aimdi.xtanative.ui.links

import com.aimdi.xtanative.services.links.SharedLinks
import com.aimdi.xtanative.services.links.SharedTarget
import kotlinx.coroutines.CancellationException

/** What a piece of text another app shared to this one opens. */
sealed interface SharedStep {
    /** A network's screen already took it. */
    data object Opened : SharedStep

    /** A link, opened as a tapped one: [inPost] as a page a post links to, which XTA's browser screen may show. */
    data class Link(val url: String, val inPost: Boolean) : SharedStep

    /** A link no XTA screen should read: out to the web. */
    data class Outside(val url: String) : SharedStep

    data class Search(val query: String) : SharedStep

    /** A bare number while Pixiv is on: a work's id. */
    data class PixivWork(val id: String) : SharedStep

    /** Nothing that could be opened; the reader is told so. */
    data object Unable : SharedStep
}

/**
 * The route of text shared to the app (main.dart's `_handleSharedText`): a
 * network's screen when one opens a link in it (a Threads post shared from
 * Threads), then an X link (a Pixiv one while [pixiv] is on) with its short
 * link followed, a bare number as a Pixiv work, any other web page as a link
 * a post points to, else a search.
 */
object SharedText {
    /** The most of a share that is read: 64 K characters, as XTA's SharedTextChannel. */
    const val MAX_CHARS = 65_536

    /**
     * The text a share hands over (SharedTextChannel.kt): its EXTRA_TEXT, else
     * the first clip's text, else that clip's URI as text (never opened), cut
     * to [MAX_CHARS]; empty when it carries none.
     */
    fun handed(extra: CharSequence?, clipText: CharSequence?, clipUri: String?): String =
        (extra ?: clipText ?: clipUri)?.let { it.subSequence(0, minOf(it.length, MAX_CHARS)).toString() }.orEmpty()

    /**
     * [openOnNetwork] opens a link on a network's screen when one reads it and
     * answers whether it did; [resolve] follows an X short link
     * ([SharedLinks.resolve]). A failure on the way is [SharedStep.Unable].
     */
    suspend fun step(
        text: String,
        pixiv: Boolean,
        openOnNetwork: (String) -> Boolean,
        resolve: suspend (String) -> String?,
    ): SharedStep = try {
        if (SharedLinks.urls(text).any(openOnNetwork)) {
            SharedStep.Opened
        } else {
            when (val target = SharedLinks.target(text, pixiv)) {
                is SharedTarget.AppLink -> resolve(text)?.let(::appLink) ?: SharedStep.Unable
                is SharedTarget.PixivId -> SharedStep.PixivWork(target.id)
                is SharedTarget.Web -> SharedStep.Link(target.url, inPost = true)
                is SharedTarget.Search -> SharedStep.Search(target.query)
                SharedTarget.Empty -> SharedStep.Unable
            }
        }
    } catch (error: CancellationException) {
        throw error
    } catch (_: Exception) {
        SharedStep.Unable
    }

    /** An X or Pixiv link once followed: X's links open as tapped ones, and a Pixiv page goes to the browser. */
    private fun appLink(url: String): SharedStep = if (SharedLinks.readsAsXLink(url)) SharedStep.Link(url, inPost = false) else SharedStep.Outside(url)
}
