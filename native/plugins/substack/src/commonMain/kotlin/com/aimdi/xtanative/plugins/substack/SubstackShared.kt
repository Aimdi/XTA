package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.substack.SubstackHosts
import com.aimdi.xtanative.networks.substack.SubstackLinks
import com.aimdi.xtanative.networks.substack.SubstackNote
import com.aimdi.xtanative.networks.substack.SubstackPost
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Substack's posts among other networks' (a group's feed) and in the app's
 * Saved: an article or a note as XTA's link record (plugin_link_post.dart),
 * as its card's long press files it, and drawn by the shared row as
 * substack_post_card.dart shows it.
 */
internal object SubstackShared {
    const val SOURCE = "substack"

    /** Where [post] lives: its canonical page, else its page on its publication. */
    fun urlOf(post: SubstackPost): String = post.canonicalUrl ?: "${post.publicationBaseUrl}/p/${post.slug}"

    /** [post] as its card files it: its page, its author or publication, its title and excerpt, its cover. */
    fun archive(post: SubstackPost): PluginPostArchive = PluginPostArchive.link(
        SOURCE,
        urlOf(post),
        post.authorName ?: post.publicationName,
        listOfNotNull(post.title, post.excerpt).joinToString("\n"),
        listOfNotNull(post.coverImage?.takeIf { it.isNotEmpty() }),
    )

    /** A note as its sheet files it (substack_note_content.dart); null for one without its own page. */
    fun archive(note: SubstackNote): PluginPostArchive? {
        val url = SubstackLinks.webUrl(note.url) ?: return null
        val author = listOfNotNull(note.authorName, note.authorHandle).firstOrNull { it.isNotBlank() }.orEmpty()
        return PluginPostArchive.link(SOURCE, url, author, note.body, listOfNotNull(SubstackLinks.webUrl(note.imageUrl)))
    }

    /** [post] in the shared row: its publication and [logoUrl], when it went out, its headline and excerpt, its cover, its hearts and comments. */
    fun post(post: SubstackPost, logoUrl: String?): NetworkPost = NetworkPost(
        id = post.key,
        authorId = post.publication.id,
        authorName = post.publicationName,
        authorHandle = SubstackHosts.hostOf(post.publicationBaseUrl),
        avatarUrl = logoUrl,
        createdAt = post.publishedAt,
        text = post.excerpt?.let { listOf(TextRun.Plain(it)) }.orEmpty(),
        media = listOfNotNull(post.coverImage?.takeIf { it.isNotEmpty() }?.let { MediaItem(it, aspectRatio = COVER) }),
        replies = post.commentCount ?: 0,
        likes = post.reactionCount ?: 0,
        url = urlOf(post),
        title = post.title,
    )

    /**
     * A post or note Saved kept ([archive], its link record), as the shared
     * row draws it: who wrote it, an article's headline over its excerpt or a
     * note's words, its pictures.
     */
    fun saved(archive: PluginPostArchive): NetworkPost {
        val record = J(archive.content)
        val url = record["url"].string.orEmpty()
        val words = record["text"].string.orEmpty()
        val article = url.toHttpUrlOrNull()?.pathSegments?.firstOrNull() == "p"
        val text = if (article) words.substringAfter('\n', "") else words
        return NetworkPost(
            id = archive.id,
            authorId = archive.authorId.orEmpty(),
            authorName = record["author"].string.orEmpty(),
            authorHandle = SubstackHosts.hostOf(url),
            text = text.takeIf { it.isNotBlank() }?.let { listOf(TextRun.Plain(it)) }.orEmpty(),
            media = record["images"].list.mapNotNull { it.string?.takeIf(String::isNotEmpty)?.let { image -> MediaItem(image) } },
            url = url,
            title = words.substringBefore('\n').takeIf { article && it.isNotBlank() },
        )
    }

    /** The shape the card gives a cover. */
    private const val COVER = 16f / 9f
}
