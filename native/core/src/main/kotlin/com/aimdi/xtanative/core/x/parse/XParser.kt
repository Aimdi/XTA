package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.model.XCard
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XVideoVariant
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Reads X's GraphQL answers, as XTA's timeline_parser.dart and
 * tweet_models.dart do: every step tolerates a missing field, and a post X
 * withholds or a shape it no longer sends is skipped rather than failing the
 * page.
 */
object XParser {
    private val createdAtFormat = DateTimeFormatter.ofPattern("EEE MMM dd HH:mm:ss Z yyyy", Locale.ENGLISH)

    // ---- users ----

    /**
     * A user result, current or older: X keeps moving fields out of `legacy`
     * into `core`, `avatar`, `profile_bio` and the rest, so both are read.
     */
    fun user(result: J): XUser? {
        if (!result.exists || result["__typename"].string == "UserUnavailable") return null
        val legacy = result["legacy"]
        val core = result["core"]
        val id = result["rest_id"].string ?: legacy["id_str"].string ?: return null
        val screenName = core["screen_name"].string ?: legacy["screen_name"].string ?: return null
        return XUser(
            id = id,
            screenName = screenName,
            name = core["name"].string ?: legacy["name"].string ?: screenName,
            avatarUrl = nonEmpty(result["avatar"]["image_url"].string) ?: legacy["profile_image_url_https"].string,
            verified = result["is_blue_verified"].boolean ?: legacy["verified"].boolean ?: false,
            protected = result["privacy"]["protected"].boolean ?: legacy["protected"].boolean ?: false,
            description = result["profile_bio"]["description"].string ?: legacy["description"].string,
            bannerUrl = nonEmpty(result["banner"]["image_url"].string) ?: legacy["profile_banner_url"].string,
            location = nonEmpty(result["location"]["location"].string) ?: nonEmpty(legacy["location"].string),
            website = nonEmpty(result["website"]["url"].string) ?: websiteOf(legacy),
            followers = result["relationship_counts"]["followers"].long ?: legacy["followers_count"].long,
            following = result["relationship_counts"]["following"].long ?: legacy["friends_count"].long,
            posts = result["tweet_counts"]["tweets"].long ?: legacy["statuses_count"].long,
            createdAt = instant(core["created_at"].string ?: legacy["created_at"].string),
            descriptionLinks = linksOf(result["profile_bio"]["entities"]["description"]["urls"].list.ifEmpty { legacy["entities"]["description"]["urls"].list }),
        )
    }

    /** The profile in a UserByScreenName or UserByRestId answer, with the ids of its pinned posts. */
    fun profile(body: J): Pair<XUser, List<String>>? {
        val result = body["data"]["user"]["result"]
        val user = user(result) ?: return null
        val pinned = result["legacy"]["pinned_tweet_ids_str"].list.mapNotNull { it.string }
        return user to pinned
    }

    private fun websiteOf(legacy: J): String? {
        val shortUrl = legacy["url"].string ?: return null
        return legacy["entities"]["url"]["urls"].list.firstOrNull { it["url"].string == shortUrl }?.get("expanded_url")?.string ?: shortUrl
    }

    // ---- posts ----

    /** A tweet result, unwrapped from the layers X adds; null for one it withholds. */
    fun post(raw: J): XPost? {
        var result = raw
        if (result["__typename"].string == "TweetWithVisibilityResults" || (!result["legacy"].exists && result["tweet"].exists)) {
            result = result["tweet"]
        }
        val legacy = result["legacy"]
        if (!legacy.exists || result["tombstone"].exists) return null
        val author = user(result["core"]["user_results"]["result"]) ?: return null
        val id = result["rest_id"].string ?: legacy["id_str"].string ?: return null

        val note = result["note_tweet"]["note_tweet_results"]["result"]
        val noteText = note["text"].string
        val text = noteText ?: legacy["full_text"].string.orEmpty()
        val range = legacy["display_text_range"].list.mapNotNull { it.int }
        val media = mediaOf(legacy)
        val entities = entitiesOf(if (noteText == null) legacy["entities"] else note["entity_set"])

        return XPost(
            id = id,
            author = author,
            createdAt = instant(legacy["created_at"].string),
            text = text,
            displayStart = if (noteText == null) range.getOrNull(0) ?: 0 else 0,
            displayEnd = if (noteText == null) range.getOrNull(1) else null,
            entities = entities,
            media = media,
            card = cardOf(result["card"]["legacy"], entities),
            quoted = post(result["quoted_status_result"]["result"]),
            reposted = post(legacy["retweeted_status_result"]["result"]),
            replyCount = legacy["reply_count"].long ?: 0,
            repostCount = legacy["retweet_count"].long ?: 0,
            quoteCount = legacy["quote_count"].long ?: 0,
            likeCount = legacy["favorite_count"].long ?: 0,
            bookmarkCount = legacy["bookmark_count"].long ?: 0,
            viewCount = result["views"]["count"].long,
            replyToScreenName = legacy["in_reply_to_screen_name"].string,
            conversationId = legacy["conversation_id_str"].string,
        )
    }

    private fun span(entity: J): Pair<Int, Int>? {
        val indices = entity["indices"].list.mapNotNull { it.int }
        return if (indices.size == 2) indices[0] to indices[1] else null
    }

    private fun linksOf(urls: List<J>): List<XEntity.Link> = urls.mapNotNull { url ->
        val (start, end) = span(url) ?: return@mapNotNull null
        val short = url["url"].string ?: return@mapNotNull null
        XEntity.Link(start, end, short, url["expanded_url"].string ?: short, url["display_url"].string ?: short)
    }

    private fun entitiesOf(entities: J): List<XEntity> {
        val links = linksOf(entities["urls"].list)
        val mentions = entities["user_mentions"].list.mapNotNull { mention ->
            val (start, end) = span(mention) ?: return@mapNotNull null
            XEntity.Mention(start, end, mention["screen_name"].string ?: return@mapNotNull null)
        }
        val hashtags = entities["hashtags"].list.mapNotNull { tag ->
            val (start, end) = span(tag) ?: return@mapNotNull null
            XEntity.Hashtag(start, end, tag["text"].string ?: return@mapNotNull null)
        }
        val cashtags = entities["symbols"].list.mapNotNull { symbol ->
            val (start, end) = span(symbol) ?: return@mapNotNull null
            XEntity.Cashtag(start, end, symbol["text"].string ?: return@mapNotNull null)
        }
        return (links + mentions + hashtags + cashtags).sortedBy { it.start }
    }

    private fun mediaOf(legacy: J): List<XMedia> {
        val items = legacy["extended_entities"]["media"].list.ifEmpty { legacy["entities"]["media"].list }
        return items.mapNotNull { item ->
            val url = item["media_url_https"].string ?: return@mapNotNull null
            val kind = when (item["type"].string) {
                "video" -> XMedia.Kind.VIDEO
                "animated_gif" -> XMedia.Kind.GIF
                else -> XMedia.Kind.PHOTO
            }
            val info = item["video_info"]
            XMedia(
                kind = kind,
                url = url,
                width = item["original_info"]["width"].int ?: item["sizes"]["large"]["w"].int,
                height = item["original_info"]["height"].int ?: item["sizes"]["large"]["h"].int,
                alt = nonEmpty(item["ext_alt_text"].string),
                variants = info["variants"].list.mapNotNull { variant ->
                    XVideoVariant(
                        url = variant["url"].string ?: return@mapNotNull null,
                        bitrate = variant["bitrate"].int,
                        contentType = variant["content_type"].string ?: "",
                    )
                },
                durationMs = info["duration_millis"].long,
                shortUrl = item["url"].string,
            )
        }
    }

    // ---- cards ----

    /** A link preview or a poll, from a card's `binding_values`; null for kinds this reader does not draw. */
    internal fun cardOf(card: J, entities: List<XEntity>): XCard? {
        val name = card["name"].string ?: return null
        val values = card["binding_values"].list.associateBy({ it["key"].string.orEmpty() }, { it["value"] })
        fun string(key: String) = values[key]?.get("string_value")?.string?.takeIf { it.isNotBlank() }
        fun image(key: String) = values[key]?.get("image_value")
        return when {
            name.contains("poll") && name.contains("choice") ->
                poll(::string, ::image, final = values["counts_are_final"]?.get("boolean_value")?.boolean ?: false)
            name == "summary" || name == "summary_large_image" || name == "player" -> {
                val title = string("title") ?: return null
                val large = name != "summary"
                val picture = (if (large) listOf("summary_photo_image_large", "photo_image_full_size_large", "player_image_large", "thumbnail_image_large") else listOf("thumbnail_image_large", "thumbnail_image"))
                    .firstNotNullOfOrNull { key -> image(key)?.takeIf { it["url"].string != null } }
                val short = string("card_url") ?: card["url"].string
                XCard.Link(
                    url = entities.filterIsInstance<XEntity.Link>().firstOrNull { it.url == short }?.expanded ?: short ?: return null,
                    title = title,
                    description = string("description"),
                    domain = string("vanity_url") ?: string("domain"),
                    imageUrl = picture?.get("url")?.string,
                    imageWidth = picture?.get("width")?.int,
                    imageHeight = picture?.get("height")?.int,
                    large = large,
                )
            }
            else -> null
        }
    }

    private fun poll(string: (String) -> String?, image: (String) -> J?, final: Boolean): XCard.Poll? {
        val choices = (1..4).mapNotNull { i ->
            val label = string("choice${i}_label") ?: return@mapNotNull null
            XCard.Poll.Choice(label, string("choice${i}_count")?.toLongOrNull() ?: 0, image("choice${i}_image_large")?.get("url")?.string)
        }
        if (choices.size < 2) return null
        val ends = string("end_datetime_utc")?.let { runCatching { Instant.parse(it) }.getOrNull() }
        return XCard.Poll(choices, ends, final)
    }

    // ---- timelines ----

    /**
     * A timeline page from any of the answers that carry `instructions`: a
     * profile's posts, a search, a conversation, the home timeline.
     */
    fun timeline(body: J): XTimelinePage {
        val page = TimelineBuilder()
        for (instruction in instructionsOf(body).list) {
            when (instruction["type"].string) {
                "TimelineAddEntries" -> instruction["entries"].list.forEach { page.entry(it) }
                "TimelinePinEntry" -> page.entry(instruction["entry"], isPinned = true)
                "TimelineReplaceEntry" -> page.entry(instruction["entry"])
                "TimelineAddToModule" -> page.moduleItems(instruction["moduleEntryId"].string, instruction["moduleItems"].list)
            }
        }
        return page.build()
    }

    /** A page from a bare list of timeline entries. */
    fun entries(entries: List<J>): XTimelinePage = TimelineBuilder().apply { entries.forEach { entry(it) } }.build()

    private class TimelineBuilder {
        private val threads = ArrayList<XThread>()
        private val seen = HashSet<String>()
        private val pinned = HashSet<String>()
        private var bottom: String? = null
        private var top: String? = null

        fun build() = XTimelinePage(threads, bottom, top, pinned)

        private fun add(thread: XThread) {
            if (thread.posts.isNotEmpty() && seen.add(thread.id)) threads.add(thread)
        }

        fun entry(entry: J, isPinned: Boolean = false) {
            val entryId = entry["entryId"].string ?: return
            val content = entry["content"]
            val cursor = content["value"].string ?: content["itemContent"]["value"].string
            val cursorType = content["cursorType"].string ?: content["itemContent"]["cursorType"].string
            when {
                entryId.startsWith("promoted-") || content["itemContent"]["promotedMetadata"].exists -> Unit
                cursor != null && (cursorType == "Bottom" || entryId.startsWith("cursor-bottom")) -> bottom = cursor
                cursor != null && (cursorType == "Top" || entryId.startsWith("cursor-top")) -> top = cursor
                content["itemContent"]["tweet_results"].exists -> {
                    val post = post(content["itemContent"]["tweet_results"]["result"]) ?: return
                    if (isPinned) pinned.add(post.id)
                    add(XThread(post.id, listOf(post)))
                }
                content["items"].exists -> moduleItems(entryId, content["items"].list)
            }
        }

        fun moduleItems(moduleId: String?, items: List<J>) {
            val posts = items
                .filterNot { it["item"]["itemContent"]["promotedMetadata"].exists }
                .mapNotNull { post(it["item"]["itemContent"]["tweet_results"]["result"]) }
            add(XThread(moduleId ?: posts.firstOrNull()?.id.orEmpty(), posts))
        }
    }

    private val instructionPaths = listOf(
        listOf("data", "user", "result", "timeline", "timeline", "instructions"),
        listOf("data", "user", "result", "timeline_v2", "timeline", "instructions"),
        listOf("data", "search_by_raw_query", "search_timeline", "timeline", "instructions"),
        listOf("data", "threaded_conversation_with_injections_v2", "instructions"),
        listOf("data", "home", "home_timeline_urt", "instructions"),
    )

    /** The `instructions` at a known path, else wherever X has moved them. */
    internal fun instructionsOf(body: J): J {
        for (path in instructionPaths) {
            val found = path.fold(body) { node, key -> node[key] }
            if (found.exists) return found
        }
        return findKey(body, "instructions", 10) ?: J(null)
    }

    private fun findKey(node: J, key: String, depth: Int): J? {
        if (depth < 0) return null
        node[key].takeIf { it.exists }?.let { return it }
        for (child in node.keys) findKey(node[child], key, depth - 1)?.let { return it }
        return null
    }

    private fun nonEmpty(value: String?): String? = value?.takeIf { it.isNotBlank() }

    internal fun instant(value: String?): Instant? =
        value?.let { runCatching { ZonedDateTime.parse(it, createdAtFormat).toInstant() }.getOrNull() }
}
