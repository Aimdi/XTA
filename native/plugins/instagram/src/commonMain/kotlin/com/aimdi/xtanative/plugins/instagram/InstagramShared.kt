package com.aimdi.xtanative.plugins.instagram

import com.aimdi.xtanative.networks.instagram.InstagramPost
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun

/**
 * Instagram in the app's archive and its groups' feeds (instagram_group.dart,
 * instagram_post_card.dart): a post kept as XTA keeps one, a link record
 * (showPluginLinkPostActions), and a post as the shared row draws it.
 */
internal object InstagramShared {
    const val SOURCE = "instagram"

    /** [post] in Saved: its page on instagram.com, its author, its caption and its pictures, as XTA's link record. */
    fun archive(post: InstagramPost): PluginPostArchive = PluginPostArchive.link(SOURCE, post.url, post.author.username, post.caption, post.displayUrls)

    /**
     * [post] as the shared row draws it: its author, its caption, its pictures
     * (a video by its cover, which plays on instagram.com) and Instagram's
     * count of its likes.
     */
    fun post(post: InstagramPost): NetworkPost = NetworkPost(
        id = post.id,
        authorId = post.author.username.lowercase(),
        authorName = post.author.displayName,
        authorHandle = post.author.username,
        avatarUrl = post.author.avatarUrl,
        createdAt = post.createdAt,
        text = post.caption.trim().takeIf { it.isNotEmpty() }?.let { listOf(TextRun.Plain(it)) }.orEmpty(),
        media = media(post),
        likes = post.likeCount,
        url = post.url,
    )

    /** A carousel's slides or its one picture; a video as its cover, opening on the site. */
    fun media(post: InstagramPost): List<MediaItem> =
        if (post.isVideo) post.displayUrls.take(1).map { MediaItem(imageUrl = it, kind = MediaItem.Kind.VIDEO, aspectRatio = 1f, webUrl = post.url) }
        else InstagramMedia.photos(post)
}
