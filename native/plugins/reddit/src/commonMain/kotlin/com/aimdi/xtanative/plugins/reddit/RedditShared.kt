package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.redditTextParts
import com.aimdi.xtanative.ui.plugins.LinkCard
import com.aimdi.xtanative.ui.plugins.MediaItem
import com.aimdi.xtanative.ui.plugins.NetworkPost
import com.aimdi.xtanative.ui.plugins.PluginPostArchive
import com.aimdi.xtanative.ui.plugins.TextRun
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put

/**
 * A Reddit post as the app's archive keeps it, in XTA's record
 * (reddit_archive.dart): `reddit:<id>`, its community, and
 * `{"xtaPlugin": "reddit", "post": …}` under RedditPost.toJson's keys, so
 * Saved, the likes and a backup read it the same in both apps.
 */
internal object RedditArchive {
    fun id(post: RedditPost): String = "reddit:" + post.id

    fun of(post: RedditPost): PluginPostArchive =
        PluginPostArchive(id(post), post.subreddit, buildJsonObject { put("xtaPlugin", "reddit"); put("post", RedditPostJson.encode(post)) })

    /** The post a record holds; null for one of another network. */
    fun post(content: JsonElement): RedditPost? {
        val root = J(content)
        return if (root["xtaPlugin"].string == "reddit") RedditPostJson.decode(root["post"]) else null
    }
}

/**
 * A Reddit post as the app's shared row draws it among other networks' (in
 * Home's timelines, the groups, Saved): the community and its picture, the
 * author, the title, the start of its words, its media behind the cover the
 * reader's choices put on it, its link, its score and its comments.
 */
internal object RedditShared {
    /** How much of a post's words the shared row shows, as the card's four lines do. */
    const val WORDS = 280

    fun post(post: RedditPost, iconUrl: String?, choices: RedditChoices, language: String): NetworkPost {
        val media = RedditMediaRules.of(post)
        return NetworkPost(
            id = post.id,
            authorId = post.subreddit,
            authorName = "r/" + post.subreddit,
            authorHandle = post.author ?: post.subreddit,
            avatarUrl = iconUrl,
            createdAt = post.createdAt,
            text = words(post),
            media = items(post, media),
            card = (media as? PostMedia.Link)?.let { LinkCard(it.url, RedditMediaRules.domain(post, it.url), imageUrl = post.cardPreviewImage) },
            replies = post.commentCount.toLong(),
            likes = post.score.toLong(),
            warning = choices.gate(post)?.let { warning(it, language) },
            url = RedditThreadRules.postUrl(post),
            title = post.displayTitle.takeIf { post.showsTitle },
        )
    }

    /** The start of a discussion post's words, its links kept; nothing for a post that is its title and media. */
    fun words(post: RedditPost): List<TextRun> {
        val words = post.displaySelfText?.trim()?.takeIf { post.isSelf && post.showsSelfText && it.isNotEmpty() } ?: return emptyList()
        val shown = if (words.length <= WORDS) words else words.take(WORDS).trimEnd() + "…"
        return redditTextParts(shown).map { part -> part.url?.let { TextRun.Link(part.text, it) } ?: TextRun.Plain(part.text) }
    }

    /** The pictures or the video a post shows: a gallery's pictures, its one picture, its video playing in the app's viewer. */
    fun items(post: RedditPost, media: PostMedia?): List<MediaItem> = when (media) {
        is PostMedia.Video -> listOf(
            MediaItem(
                imageUrl = post.cardPreviewImage.orEmpty(),
                kind = MediaItem.Kind.VIDEO,
                videoUrl = media.stream,
                aspectRatio = RedditMediaRules.aspectRatio(post.videoAspectRatio).toFloat(),
                webUrl = RedditThreadRules.postUrl(post),
            ),
        )
        is PostMedia.Gallery -> media.images.map { MediaItem(imageUrl = it) }
        is PostMedia.Image -> listOf(MediaItem(imageUrl = media.url))
        is PostMedia.GalleryLink, is PostMedia.Link, null -> emptyList()
    }

    private fun warning(gate: MediaGate, language: String): String = when (gate) {
        MediaGate.NSFW -> Strings.nsfw.get(language)
        MediaGate.SPOILER -> Strings.spoiler.get(language)
    }
}
