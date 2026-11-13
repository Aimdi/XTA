package com.aimdi.xtanative.plugins.tiktok

import com.aimdi.xtanative.networks.tiktok.TikTokPost
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.PluginPostMenu
import com.aimdi.xtanative.ui.plugins.TextRun

/**
 * A video as the app's shared row draws it among other networks' posts (a
 * group's feed): who posted it and when, its caption, its cover, its counts
 * and its link; and as the archive keeps it, XTA's link record
 * (plugin_link_post.dart), as XTA's TikTok card files it.
 */
internal object TikTokShared {
    fun post(post: TikTokPost): NetworkPost = NetworkPost(
        id = post.id,
        authorId = post.author.uniqueId,
        authorName = post.author.displayName,
        authorHandle = post.author.uniqueId,
        avatarUrl = post.author.avatarUrl,
        createdAt = post.createdAt,
        text = if (post.desc.isBlank()) emptyList() else listOf(TextRun.Plain(post.desc)),
        media = listOfNotNull(post.coverUrl?.let { cover -> cover(post, cover) }),
        replies = post.commentCount,
        likes = post.diggCount,
        views = post.playCount.takeIf { it > 0 },
        url = post.url,
    )

    /** XTA's record of a video in Saved: its link, its author's handle, its caption and its cover. */
    fun archive(post: TikTokPost): PluginPostArchive =
        PluginPostArchive.link(SOURCE, post.url, post.author.uniqueId, post.desc, listOfNotNull(post.coverUrl))

    /** XTA's sheet on a video's long press: saved or taken out, the note, Share link, Save to folder…, Open in browser. */
    fun menu(post: TikTokPost): PluginPostMenu = PluginPostMenu(archive(post), post.url)

    private fun cover(post: TikTokPost, url: String): MediaItem =
        if (post.isPhoto) {
            MediaItem(url, aspectRatio = post.aspectRatio)
        } else {
            MediaItem(url, MediaItem.Kind.VIDEO, aspectRatio = post.aspectRatio, durationMs = post.durationSeconds.takeIf { it > 0 }?.times(1000L), webUrl = post.url)
        }

    /** The source XTA's link records name TikTok by. */
    private const val SOURCE = "tiktok"
}

/** The last [capacity] videos the app's shared rows drew, by id: what a tap on one of those rows opens. */
internal class TikTokDrawn(private val capacity: Int = 200) {
    private val posts = object : LinkedHashMap<String, TikTokPost>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, TikTokPost>?): Boolean = size > capacity
    }

    @Synchronized
    fun put(post: TikTokPost) {
        posts[post.id] = post
    }

    @Synchronized
    fun get(id: String): TikTokPost? = posts[id]
}
