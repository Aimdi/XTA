package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.parse.XText
import com.aimdi.xtanative.services.links.XLinks

/** An entry of an X post's share sheet of its own, before what the plugins add. */
enum class ShareEntry { CONTENT, LINK, CONTENT_AND_LINK, IMAGE }

/**
 * What an X post's share sheet offers and hands on, as XTA's does
 * (tweet_footer.dart's _showShareSheet and shareableTweetText): nothing
 * of it is posted anywhere; it goes to the platform's share sheet.
 */
object PostShare {
    /** The sheet's own entries, in XTA's order: an article shares its link and its picture alone; without a [picture], none. */
    fun entries(article: Boolean, picture: Boolean = true): List<ShareEntry> =
        (if (article) listOf(ShareEntry.LINK, ShareEntry.IMAGE) else ShareEntry.entries).filter { picture || it != ShareEntry.IMAGE }

    /**
     * [post]'s words as they are shared: its text as X sent it, its t.co
     * links the pages they lead to, without their trackers unless the reader
     * turned that off ([clean]), so the share skips X's click tracking.
     */
    fun text(post: XPost, clean: Boolean): String {
        val links = post.entities.filterIsInstance<XEntity.Link>().map { it.url to XLinks.prepare(it.expanded, clean) }
        val media = post.media.mapNotNull { item -> item.shortUrl?.let { it to mediaPage(post, item) } }
        return (links + media).filter { it.first.isNotEmpty() }.fold(XText.unescape(post.text)) { text, (short, page) -> text.replace(short, page) }
    }

    /** Its words, a blank line, then [url]: "Share post content and link". */
    fun textAndLink(post: XPost, url: String, clean: Boolean): String = text(post, clean) + "\n\n" + url

    /** The name of [post]'s picture as it is shared: its author's handle and its id. */
    fun imageName(post: XPost): String = "${post.author.screenName.ifEmpty { "x" }}-${post.id}.png"

    /** Where X's link to a post's media leads, as its expanded address: the post's photo or video. */
    private fun mediaPage(post: XPost, media: XMedia): String = post.url + if (media.kind == XMedia.Kind.PHOTO) "/photo/1" else "/video/1"
}
