package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment.Kind
import java.time.Instant
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.doubleOrNull
import kotlinx.serialization.json.longOrNull
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Reads the post and user objects Meta serves Threads in, as XTA's
 * threads_parse.dart does: the same shape comes from guest GraphQL, the
 * cookie and Bearer APIs and the JSON a public page embeds. Every field is
 * read as if it might be gone; a post without an author or without anything
 * to show is skipped.
 */
object ThreadsParser {
    /** One page of a feed: each thread's root, carrying how many posts its author chained under it. */
    fun feed(body: J): List<ThreadsPost> {
        val buckets = body["threads"].list.ifEmpty { body["items"].list }
        return buckets.mapNotNull(::bucketRoot)
    }

    /** A guest GraphQL profile tab: `data.mediaData.threads`, else the plain feed shape. */
    fun graphqlFeed(body: J): List<ThreadsPost> {
        val threads = body["data"]["mediaData"]["threads"].list
        return if (threads.isEmpty()) feed(body) else threads.mapNotNull(::bucketRoot)
    }

    /**
     * How many of [following] continue [root]'s author's own thread: the run of
     * their posts straight after it. A reply from anyone else ends the run.
     */
    fun selfThreadLength(root: ThreadsPost, following: List<ThreadsPost>): Int =
        following.takeWhile { it.handle == root.handle }.size

    /**
     * One Meta post, a pure repost included: its outer caption is often empty and
     * the original lives under `share_info.reposted_post`, so it is read as the
     * original, reposted by the outer post's author at the outer post's time.
     */
    fun post(post: J): ThreadsPost? {
        if (!post.exists) return null
        val reposted = post["text_post_app_info"]["share_info"]["reposted_post"]
        return if (reposted.exists) repost(outer = post, inner = reposted) else original(post, withQuote = true)
    }

    /** The post [post] quotes, one level deep: Meta has carried it under two names. */
    fun quotedPost(post: J): ThreadsPost? {
        val share = post["text_post_app_info"]["share_info"]
        return listOf("quoted_post", "quoted_attachment_post").firstNotNullOfOrNull { original(share[it], withQuote = false) }
    }

    /** The topic a post was filed under, without its `#`. */
    fun topicTag(post: J): String? {
        val header = post["text_post_app_info"]["tag_header"]
        val name = (header["display_name"].string ?: header["name"].string).orEmpty().trim()
        return name.ifEmpty { null }?.removePrefix("#")
    }

    /**
     * Meta's own split of [caption] into text, mentions, links and tags; only
     * trusted when its pieces spell the caption exactly and some piece is more
     * than text. Anything else is a reshaped payload, and patterns read safer.
     */
    fun fragments(post: J, caption: String): List<ThreadsTextFragment> {
        val raw = post["text_post_app_info"]["text_fragments"]["fragments"].list
        if (raw.isEmpty()) return emptyList()
        val fragments = raw.mapNotNull(::fragment)
        val spelled = fragments.joinToString("") { it.text }.trim()
        return fragments.takeIf { spelled == caption && fragments.any { it.kind != Kind.TEXT } } ?: emptyList()
    }

    /** Every picture or video a post shows, a carousel's in order; posters stand in for videos. */
    fun media(post: J): List<ThreadsMedia> {
        val seen = HashSet<String>()
        return post["carousel_media"].list.ifEmpty { listOf(post) }.mapNotNull { media ->
            val (url, aspect) = bestCandidate(media["image_versions2"])
            if (url.isNullOrEmpty() || !seen.add(url)) return@mapNotNull null
            val fallback = aspectOf(
                media["original_width"].integer ?: post["original_width"].integer,
                media["original_height"].integer ?: post["original_height"].integer,
            )
            ThreadsMedia(url, aspect ?: fallback, media["accessibility_caption"].string?.trim()?.ifEmpty { null }, videoRenditions(media))
        }
    }

    /**
     * Every file Meta offers for a video, largest first (in Meta's order where
     * sizes tie or are missing), one per file and one per quality, so a quality
     * menu never names the same one twice.
     */
    fun videoRenditions(media: J): List<ThreadsVideoRendition> {
        val offered = media["video_versions"].list.mapNotNull { version ->
            version["url"].string?.takeIf { it.isNotEmpty() }?.let { url ->
                ThreadsVideoRendition(url, positive(version["width"].integer), positive(version["height"].integer))
            }
        }.sortedByDescending { it.area }
        val urls = HashSet<String>()
        val qualities = HashSet<Int>()
        return offered.filter { rendition -> urls.add(rendition.url) && rendition.lines.let { it == null || qualities.add(it) } }
    }

    /** The link preview Meta attached, its `l.threads.com` redirect unwrapped; null when nothing useful is there. */
    fun linkCard(post: J): ThreadsLinkCard? {
        val card = post["text_post_app_info"]["link_preview_attachment"]
        if (!card.exists) return null
        val rawUrl = card["url"].string?.trim().orEmpty()
        val title = card["title"].string?.trim()?.ifEmpty { null }
        val image = card["image_url"].string?.trim()?.ifEmpty { null }
        val display = card["display_url"].string?.trim()?.ifEmpty { null }
        if (rawUrl.isEmpty() && title == null && image == null) return null
        val url = when {
            rawUrl.isNotEmpty() -> ThreadsLinks.unwrapOutbound(rawUrl)
            display == null -> ""
            display.startsWith("http") -> display
            else -> "https://$display"
        }
        if (url.isEmpty()) return null
        return ThreadsLinkCard(
            url = url,
            title = title,
            description = card["description"].string?.trim()?.ifEmpty { null },
            imageUrl = image,
            providerName = display ?: url.toHttpUrlOrNull()?.host,
        )
    }

    /** A user object, from the cookie API or a page's JSON; null without a username. */
    fun profile(user: J): ThreadsProfile? {
        if (!user.exists) return null
        val username = user["username"].string?.trim().orEmpty()
        if (username.isEmpty()) return null
        val pk = idOf(user["pk"]) ?: user["id"].string ?: user["pk_id"].string.orEmpty()
        return ThreadsProfile(
            pk = pk,
            id = user["id"].string ?: pk,
            username = username,
            fullName = user["full_name"].string.orEmpty(),
            isVerified = user["is_verified"].boolean ?: false,
            isPrivate = user["is_private"].boolean ?: false,
            profilePicUrl = user["profile_pic_url"].string ?: user["hd_profile_pic_url_info"]["url"].string.orEmpty(),
            biography = user["biography"].string.orEmpty(),
            followerCount = user["follower_count"].integer ?: 0,
            followingCount = user["following_count"].integer ?: 0,
            mediaCount = user["media_count"].integer ?: 0,
            externalUrl = user["external_url"].string?.trim()?.ifEmpty { null },
        )
    }

    private fun bucketRoot(bucket: J): ThreadsPost? {
        val items = bucket["thread_items"].list
        if (items.isEmpty()) return post(bucket["post"])
        val root = post(items.first()["post"]) ?: return null
        val chain = items.drop(1).mapNotNull { post(it["post"]) }
        return root.withSelfThreadCount(selfThreadLength(root, chain))
    }

    private fun repost(outer: J, inner: J): ThreadsPost? {
        val original = original(inner, withQuote = true) ?: return null
        val user = outer["user"]
        val reposter = user["username"].string.orEmpty().trim().lowercase()
        if (reposter.isEmpty()) return null
        val name = user["full_name"].string.orEmpty().trim()
        return original.repostedBy(
            id = idOf(outer["pk"]) ?: outer["id"].string ?: original.id,
            handle = reposter,
            name = name.ifEmpty { reposter },
            at = takenAt(outer),
        )
    }

    private fun original(post: J, withQuote: Boolean): ThreadsPost? {
        if (!post.exists) return null
        val user = post["user"]
        val handle = user["username"].string.orEmpty().trim().lowercase()
        val text = post["caption"]["text"].string.orEmpty().trim()
        val media = media(post)
        val linkCard = linkCard(post)
        val quoted = if (withQuote) quotedPost(post) else null
        if (handle.isEmpty() || (text.isEmpty() && media.isEmpty() && linkCard == null && quoted == null)) return null
        val code = post["code"].string
        val id = idOf(post["pk"]) ?: post["id"].string ?: code
        if (id.isNullOrEmpty()) return null
        val info = post["text_post_app_info"]
        val replyTo = info["reply_to_author"]["username"].string?.trim()?.ifEmpty { null }?.lowercase()
        return ThreadsPost(
            id = id,
            handle = handle,
            authorName = user["full_name"].string.orEmpty().trim().ifEmpty { handle },
            text = text,
            avatarUrl = user["profile_pic_url"].string ?: user["hd_profile_pic_url_info"]["url"].string,
            media = media,
            publishedAt = takenAt(post),
            url = code?.let { "${ThreadsLinks.WEB}/@$handle/post/$it" },
            likeCount = post["like_count"].integer,
            replyCount = info["direct_reply_count"].integer,
            repostCount = info["repost_count"].integer,
            quoteCount = info["quote_count"].integer,
            linkCard = linkCard,
            quoted = quoted,
            topicTag = topicTag(post),
            fragments = fragments(post, text),
            isVerified = user["is_verified"].boolean ?: false,
            replyToHandle = replyTo,
            isReply = replyTo != null || info["is_reply"].boolean == true || info["reply_to_author"].exists,
        )
    }

    private fun fragment(fragment: J): ThreadsTextFragment? {
        val text = fragment["plaintext"].string ?: return null
        val (kind, target) = when (fragment["fragment_type"].string) {
            "mention" -> Kind.MENTION to fragment["mention_fragment"]["mentioned_user"]["username"].string
            "link" -> Kind.LINK to linkTarget(fragment["link_fragment"]["uri"].string)
            "tag" -> Kind.TAG to tagTarget(fragment["tag_fragment"], text)
            else -> Kind.TEXT to null
        }
        return if (kind == Kind.TEXT || target.isNullOrEmpty()) ThreadsTextFragment(Kind.TEXT, text) else ThreadsTextFragment(kind, text, target)
    }

    private fun linkTarget(uri: String?): String? {
        val value = uri?.trim()?.ifEmpty { null } ?: return null
        return ThreadsLinks.unwrapOutbound(value).takeIf(ThreadsLinks::hasScheme)
    }

    private fun tagTarget(tag: J, text: String): String? =
        (tag["display_name"].string ?: tag["tag_name"].string ?: text).trim().removePrefix("#").ifEmpty { null }

    /** The largest picture Meta offers, and its shape when sized. */
    private fun bestCandidate(versions: J): Pair<String?, Double?> {
        var best: String? = null
        var bestArea = -1L
        var aspect: Double? = null
        for (candidate in versions["candidates"].list) {
            val url = candidate["url"].string?.takeIf { it.isNotEmpty() } ?: continue
            val width = candidate["width"].integer ?: 0
            val height = candidate["height"].integer ?: 0
            if (width * height < bestArea) continue
            bestArea = width * height
            best = url
            if (width > 0 && height > 0) aspect = width.toDouble() / height
        }
        return best to aspect
    }

    private fun aspectOf(width: Long?, height: Long?): Double? =
        if (width != null && height != null && width > 0 && height > 0) width.toDouble() / height else null

    private fun positive(value: Long?): Int? = value?.takeIf { it > 0 }?.toInt()

    /** An id Meta may send as a string or, from the app API, as a number. */
    private fun idOf(value: J): String? =
        value.string ?: (value.raw as? JsonPrimitive)?.takeIf { !it.isString && it !is JsonNull }?.let { value.integer?.toString() }

    private fun takenAt(post: J): Instant? = post["taken_at"].integer?.let { runCatching { Instant.ofEpochSecond(it) }.getOrNull() }
}

/**
 * A whole number as XTA's `Json.integer` reads one: a number, cut to its whole
 * part, or a string of digits. Meta's ids and counts arrive either way.
 */
internal val J.integer: Long?
    get() {
        val primitive = raw as? JsonPrimitive ?: return null
        if (primitive is JsonNull) return null
        return if (primitive.isString) primitive.content.toLongOrNull() else primitive.longOrNull ?: primitive.doubleOrNull?.toLong()
    }
