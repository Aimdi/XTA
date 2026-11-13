package com.aimdi.xtanative.core.x.parse

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.model.XAffiliation
import com.aimdi.xtanative.core.x.model.XArticle
import com.aimdi.xtanative.core.x.model.XCommunityNote
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XTimelinePage
import com.aimdi.xtanative.core.x.model.XTranslation
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.core.x.model.XUserPage
import com.aimdi.xtanative.core.x.model.XVerifiedType
import com.aimdi.xtanative.core.x.model.XVideoVariant
import com.aimdi.xtanative.core.x.model.XWithheld
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
    private val postLink = Regex("""^https?://(?:www\.|mobile\.)?(?:x|twitter)\.com/(\w{1,50})/status(?:es)?/(\d+)""", RegexOption.IGNORE_CASE)

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
        // An account without a handle is kept with an empty one, as XTA keeps it.
        val screenName = core["screen_name"].string ?: legacy["screen_name"].string ?: ""
        val verifiedType = verifiedTypeOf(result)
        return XUser(
            id = id,
            screenName = screenName,
            name = core["name"].string ?: legacy["name"].string ?: screenName,
            avatarUrl = nonEmpty(result["avatar"]["image_url"].string) ?: legacy["profile_image_url_https"].string,
            verified = verifiedType != XVerifiedType.NONE,
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
            verifiedType = verifiedType,
            affiliation = affiliationOf(result),
            followedByReader = result["relationship_perspectives"]["following"].boolean ?: legacy["following"].boolean,
            likes = result["action_counts"]["favorites_count"].long ?: legacy["favourites_count"].long,
            listed = legacy["listed_count"].long,
            sensitive = result["possibly_sensitive"].boolean ?: legacy["possibly_sensitive"].boolean ?: false,
        )
    }

    /**
     * The check X draws, wherever it put the fields (user_verification.dart):
     * a type of business or government, else blue for any blue flag.
     */
    private fun verifiedTypeOf(user: J): XVerifiedType {
        val type = listOf(user["verification"]["verified_type"], user["legacy"]["verified_type"], user["verified_type"]).firstNotNullOfOrNull { it.string }
        val blue = listOf(
            user["is_blue_verified"], user["ext_is_blue_verified"], user["legacy"]["ext_is_blue_verified"],
            user["verification"]["verified"], user["legacy"]["verified"], user["verified"],
        ).any { it.boolean == true }
        return when {
            type == "Business" -> XVerifiedType.BUSINESS
            type == "Government" -> XVerifiedType.GOVERNMENT
            blue -> XVerifiedType.BLUE
            else -> XVerifiedType.NONE
        }
    }

    /** The organisation badge; none without a picture, nor for X's "Automated" label, which is no badge. */
    private fun affiliationOf(user: J): XAffiliation? =
        listOf(user["affiliates_highlighted_label"]["label"], user["legacy"]["affiliates_highlighted_label"]["label"]).firstNotNullOfOrNull { label ->
            val badge = label["badge"]["url"].string
            val type = label["userLabelType"].string
            if (badge.isNullOrEmpty() || type == "AutomatedLabel") null else XAffiliation(badge, label["description"].string, label["url"]["url"].string, type)
        }

    /** The profile in a UserByScreenName or UserByRestId answer, with the ids of its pinned posts. */
    fun profile(body: J): Pair<XUser, List<String>>? {
        val result = body["data"]["user"]["result"]
        val user = user(result) ?: return null
        return user to pinnedIdsOf(result)
    }

    /** Pinned post ids: `pinned_items` on current results, `legacy` on older ones. */
    internal fun pinnedIdsOf(result: J): List<String> {
        val modern = result["pinned_items"]["tweet_ids_str"]
        return (if (modern.exists) modern else result["legacy"]["pinned_tweet_ids_str"]).list.mapNotNull { it.string }
    }

    private fun websiteOf(legacy: J): String? {
        val shortUrl = legacy["url"].string ?: return null
        return legacy["entities"]["url"]["urls"].list.firstOrNull { it["url"].string == shortUrl }?.get("expanded_url")?.string ?: shortUrl
    }

    /**
     * A page of accounts from a timeline of users (Following, Followers,
     * Retweeters, ListMembers, people search): an account per entry, or in a
     * module of entries, under `user_results` or `userResults`. An entry that
     * no longer reads as an account is skipped.
     */
    fun users(instructions: J): XUserPage {
        val entries = instructions.list.filter { it["type"].string == "TimelineAddEntries" }.flatMap { it["entries"].list }
        val cursors = entries.mapNotNull { entry -> cursorOf(entry["content"])?.let { kindOf(entry["entryId"].string.orEmpty(), it) to it.value } }
        return XUserPage(
            users = entries.flatMap(::userResultsIn).mapNotNull(::user),
            bottomCursor = cursors.lastOrNull { it.first == CursorKind.BOTTOM }?.second,
            topCursor = cursors.lastOrNull { it.first == CursorKind.TOP }?.second,
        )
    }

    private fun userResultsIn(entry: J): List<J> {
        val content = entry["content"]
        userResultOf(content["itemContent"])?.let { return listOf(it) }
        return content["items"].list.mapNotNull { item -> userResultOf(item["item"]["itemContent"].takeIf { it.exists } ?: item["itemContent"]) }
    }

    private fun userResultOf(itemContent: J): J? =
        (itemContent["user_results"]["result"].takeIf { it.exists } ?: itemContent["userResults"]["result"]).takeIf { it.exists }

    // ---- posts ----

    /**
     * A tweet result, unwrapped from the layers X adds; a subscriber preview
     * reads as the post it previews. Null for one X withholds: see [withheld].
     */
    fun post(raw: J): XPost? {
        if (raw["__typename"].string == "TweetPreviewDisplay") return preview(raw["tweet"])
        val result = if (raw["__typename"].string == "TweetWithVisibilityResults" || (!raw["legacy"].exists && raw["tweet"].exists)) raw["tweet"] else raw
        val legacy = result["legacy"]
        if (!legacy.exists || result["tombstone"].exists) return null
        val author = user(result["core"]["user_results"]["result"]) ?: return null
        val id = result["rest_id"].string ?: legacy["id_str"].string ?: return null

        val note = result["note_tweet"]["note_tweet_results"]["result"]
        val noteText = note["text"].string
        val range = legacy["display_text_range"].list.mapNotNull { it.int }
        val entities = entitiesOf(if (noteText == null) legacy["entities"] else note["entity_set"])
        val card = result["card"]["legacy"]
        val quotedResult = result["quoted_status_result"]["result"]
        val quoted = post(quotedResult)

        return XPost(
            id = id,
            author = author,
            createdAt = instant(legacy["created_at"].string),
            text = noteText ?: legacy["full_text"].string.orEmpty(),
            displayStart = if (noteText == null) range.getOrNull(0) ?: 0 else 0,
            displayEnd = if (noteText == null) range.getOrNull(1) else null,
            entities = entities,
            media = mediaOf(legacy),
            card = XCards.card(card, entities),
            quoted = quoted,
            reposted = post(legacy["retweeted_status_result"]["result"]),
            replyCount = legacy["reply_count"].long ?: 0,
            repostCount = legacy["retweet_count"].long ?: 0,
            quoteCount = legacy["quote_count"].long ?: 0,
            likeCount = legacy["favorite_count"].long ?: 0,
            bookmarkCount = legacy["bookmark_count"].long ?: 0,
            viewCount = result["views"]["count"].long,
            replyToScreenName = legacy["in_reply_to_screen_name"].string,
            conversationId = legacy["conversation_id_str"].string,
            lang = legacy["lang"].string,
            sensitive = legacy["possibly_sensitive"].boolean ?: false,
            replyToPostId = legacy["in_reply_to_status_id_str"].string,
            communityId = result["community_results"]["result"].let { it["id_str"].string ?: it["rest_id"].string },
            note = noteOf(result["birdwatch_pivot"]),
            article = articleOf(result["article"]),
            live = XLiveParser.liveOf(legacy, note["entity_set"], card, listOf(legacy["full_text"].string, noteText)),
            grokShare = XCards.grokShare(card),
            quotedWithheld = if (quoted == null) quotedWithheld(legacy, quotedResult) else null,
        )
    }

    /**
     * A `TweetPreviewDisplay`: the start of a post its author keeps for paid
     * subscribers. It has no `legacy`; its text, counts and date sit on it.
     */
    private fun preview(tweet: J): XPost? {
        val id = tweet["rest_id"].string ?: return null
        val author = user(tweet["core"]["user_results"]["result"]) ?: return null
        val text = tweet["text"].string.orEmpty()
        val entities = entitiesOf(tweet["entities"])
        return XPost(
            id = id,
            author = author,
            createdAt = instant(tweet["created_at"].string),
            text = text,
            displayEnd = text.codePointCount(0, text.length),
            entities = entities,
            card = XCards.card(tweet["card"]["legacy"], entities),
            replyCount = tweet["reply_count"].long ?: 0,
            repostCount = tweet["retweet_count"].long ?: 0,
            quoteCount = tweet["quote_count"].long ?: 0,
            likeCount = tweet["favorite_count"].long ?: 0,
            bookmarkCount = tweet["bookmark_count"].long ?: 0,
            viewCount = tweet["view_count"]["count"].long,
            conversationId = id,
            replyToPostId = tweet["reply_to_results"]["rest_id"].string,
            subscriberPreview = true,
        )
    }

    /**
     * What a result X would not give says of itself: X's own explanation
     * without its "Learn more", or that its author keeps it for subscribers.
     */
    fun withheld(result: J, id: String): XWithheld {
        val payload = if (result["tombstone"].exists) result["tombstone"] else result
        val explanation = payload["richText"]["text"].string ?: payload["text"]["text"].string
        return XWithheld(id, explanation?.replaceFirst(" Learn more", ""), subscribersOnly = payload["reason"].string == "ExclusiveTweet")
    }

    /**
     * The post a quote names when X would not give it: its id, and its author
     * from the link X keeps, which is what finding it elsewhere takes. A
     * quote that names nothing at all keeps an empty id: the card can only
     * say it could not be read.
     */
    private fun quotedWithheld(legacy: J, quoted: J): XWithheld? {
        val link = legacy["quoted_status_permalink"]["expanded"].string?.let(postLink::find)
        val id = legacy["quoted_status_id_str"].string ?: link?.groupValues?.get(2)
        val screenName = link?.groupValues?.get(1)
        return when {
            id != null && quoted.exists -> withheld(quoted, id).copy(screenName = screenName)
            legacy["is_quote_status"].boolean == true -> XWithheld(id.orEmpty(), null, screenName = screenName)
            else -> null
        }
    }

    /** The community note under a post (`birdwatch_pivot`), its links moved from UTF-16 to code points. */
    private fun noteOf(pivot: J): XCommunityNote? {
        val subtitle = pivot["subtitle"]
        val text = subtitle["text"].string ?: return null
        val links = subtitle["entities"].list.mapNotNull { entity ->
            val from = entity["fromIndex"].int ?: return@mapNotNull null
            val to = entity["toIndex"].int ?: return@mapNotNull null
            val url = entity["ref"]["url"].string ?: return@mapNotNull null
            if (from < 0 || to > text.length || from >= to) return@mapNotNull null
            XEntity.Link(text.codePointCount(0, from), text.codePointCount(0, to), url, url, text.substring(from, to))
        }
        return XCommunityNote(text, links, pivot["title"].string ?: pivot["shorttitle"].string, pivot["destinationUrl"].string)
    }

    private fun articleOf(article: J): XArticle? {
        val result = article["article_results"]["result"].takeIf { it.exists } ?: article["article"].takeIf { it.exists } ?: return null
        return XArticle(
            id = result["rest_id"].string,
            title = result["title"].string.orEmpty(),
            previewText = result["preview_text"].string.orEmpty(),
            coverUrl = result["cover_media"]["media_info"]["original_img_url"].string,
            content = result,
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

    /** A post's (or a translation's) links, mentions, hashtags and cashtags, in text order. */
    internal fun entitiesOf(entities: J): List<XEntity> {
        val links = linksOf(entities["urls"].list)
        val mentions = entities["user_mentions"].list.mapNotNull { mention ->
            val (start, end) = span(mention) ?: return@mapNotNull null
            XEntity.Mention(start, end, mention["screen_name"].string ?: return@mapNotNull null)
        }
        val hashtags = entities["hashtags"].list.mapNotNull { tag ->
            val (start, end) = span(tag) ?: return@mapNotNull null
            XEntity.Hashtag(start, end, tag["text"].string ?: return@mapNotNull null)
        }
        // A cashtag without its symbol opens nothing: XTA's rich text skips it.
        val cashtags = entities["symbols"].list.mapNotNull { symbol ->
            val (start, end) = span(symbol) ?: return@mapNotNull null
            XEntity.Cashtag(start, end, symbol["text"].string?.ifEmpty { null } ?: return@mapNotNull null)
        }
        return (links + mentions + hashtags + cashtags).sortedBy { it.start }
    }

    private fun mediaOf(legacy: J): List<XMedia> =
        legacy["extended_entities"]["media"].list.ifEmpty { legacy["entities"]["media"].list }.mapNotNull(::mediaItem)

    /** One photo, video or GIF, from a post's media or a card's media entities. */
    internal fun mediaItem(item: J): XMedia? {
        val url = item["media_url_https"].string ?: return null
        val kind = when (item["type"].string) {
            "video" -> XMedia.Kind.VIDEO
            "animated_gif" -> XMedia.Kind.GIF
            else -> XMedia.Kind.PHOTO
        }
        val info = item["video_info"]
        return XMedia(
            kind = kind,
            url = url,
            width = item["original_info"]["width"].int ?: item["sizes"]["large"]["w"].int,
            height = item["original_info"]["height"].int ?: item["sizes"]["large"]["h"].int,
            alt = nonEmpty(item["ext_alt_text"].string) ?: nonEmpty(item["additional_media_info"]["description"].string),
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

    /**
     * A Grok translation's answer: its `result`'s text and entities; null when
     * it has none. The links X put in for the post's media come as links too,
     * under their `t.co` address, so a post showing the translation hides them
     * as it hides its own, as XTA's MediaEntity does.
     */
    fun translation(body: J): XTranslation? {
        val result = body["result"]
        val text = result["text"].string ?: return null
        val entities = result["entities"]
        return XTranslation(text, (entitiesOf(entities) + linksOf(entities["media"].list)).sortedBy { it.start })
    }

    /** X's own error, when it answered with one in place of what was asked. */
    fun error(body: J): XAnswerException? {
        val first = body["errors"][0]
        if (!first.exists) return null
        return XAnswerException(first["code"].int, first["message"].string ?: "X answered with an error")
    }

    // ---- timelines ----

    /**
     * A timeline page from any of the answers that carry `instructions`: a
     * profile's posts, a search, a community, the home timeline.
     */
    fun timeline(body: J): XTimelinePage = timelineOf(instructionsOf(body))

    /**
     * A conversation (TweetDetail): as [timeline], and a post X withholds
     * keeps its place in it, as a [XWithheld] in its thread.
     */
    fun conversation(body: J): XTimelinePage = timelineOf(instructionsOf(body), keepWithheld = true)

    internal fun timelineOf(instructions: J, keepWithheld: Boolean = false): XTimelinePage {
        val page = TimelineBuilder(keepWithheld)
        for (instruction in instructions.list) {
            when (instruction["type"].string) {
                "TimelineAddEntries" -> instruction["entries"].list.forEach { page.entry(it) }
                "TimelinePinEntry" -> page.entry(instruction["entry"], isPinned = true)
                "TimelineReplaceEntry" -> page.entry(
                    instruction["entry"].takeIf { it.exists } ?: instruction["replaceEntry"]["entry"],
                    replacing = instruction["entry_id_to_replace"].string ?: instruction["replaceEntry"]["entryIdToReplace"].string,
                )
                "TimelineAddToModule" -> page.moduleItems(instruction["moduleEntryId"].string, instruction["moduleItems"].list)
            }
        }
        return page.build()
    }

    /**
     * [page]'s posts chained as XTA's createUnconversationedChainsGraphql
     * chains a search's or a community's: with [group] the posts of one
     * conversation are one thread, else a thread per post; the newest
     * conversation (or post) first, a thread's posts oldest first. Without
     * [includeReplies] a reply is left out before anything is chained. A
     * pinned post keeps its own thread at the top.
     */
    fun conversations(page: XTimelinePage, includeReplies: Boolean = true, group: Boolean = true): XTimelinePage {
        val pinned = page.threads.filter { it.id in page.pinnedIds }
        val chains = page.threads.filterNot { it.id in page.pinnedIds }
            .flatMap { it.posts }
            .distinctBy { it.id }
            .filter { includeReplies || (it.replyToPostId == null && it.replyToScreenName == null) }
            .groupBy { if (group) it.conversationId ?: it.id else it.id }
            .entries.sortedWith(compareBy(idOrder.reversed()) { it.key })
            .map { (id, posts) -> XThread(id, posts.sortedWith(compareBy(idOrder) { it.id })) }
        return page.copy(threads = pinned + chains)
    }

    /** X's ids in the order they were given: a longer one is newer, as numbers are. */
    private val idOrder: Comparator<String> = compareBy<String> { it.length }.thenBy { it }

    /** A page from a bare list of timeline entries. */
    fun entries(entries: List<J>): XTimelinePage = TimelineBuilder(keepWithheld = false).apply { entries.forEach { entry(it) } }.build()

    /** A paging cursor, in any of the three shapes X has sent one. */
    internal data class Cursor(val value: String, val type: String?)

    internal enum class CursorKind { BOTTOM, TOP, SHOW_MORE, OTHER }

    internal fun cursorOf(content: J): Cursor? {
        val operation = content["operation"]["cursor"]
        val value = content["value"].string ?: operation["value"].string ?: content["itemContent"]["value"].string ?: return null
        return Cursor(value, content["cursorType"].string ?: operation["cursorType"].string ?: content["itemContent"]["cursorType"].string)
    }

    /**
     * What a cursor pages to. X has spelled the "Show additional replies"
     * cursor several ways (`cursor-showMore`, `cursor-showmorethreads`,
     * `…prompt`), so its id is matched in any case.
     */
    internal fun kindOf(entryId: String, cursor: Cursor): CursorKind {
        val id = entryId.lowercase()
        return when {
            id.contains("showmore") || cursor.type?.startsWith("ShowMore") == true -> CursorKind.SHOW_MORE
            cursor.type == "Bottom" || id.contains("cursor-bottom") -> CursorKind.BOTTOM
            cursor.type == "Top" || id.contains("cursor-top") -> CursorKind.TOP
            else -> CursorKind.OTHER
        }
    }

    private class TimelineBuilder(private val keepWithheld: Boolean) {
        private val threads = ArrayList<XThread>()
        private val at = HashMap<String, Int>()
        private val pinned = LinkedHashSet<String>()
        private var bottom: String? = null
        private var top: String? = null
        private var showMore: String? = null
        private var moduleBottom: String? = null
        private var moduleShowMore: String? = null

        // A cursor at the top of the page wins over one X nested in a module.
        fun build() = XTimelinePage(threads, bottom ?: moduleBottom, top, pinned, showMore ?: moduleShowMore)

        /** Adds [thread]; one already on the page takes what it did not have, as a module X adds to. */
        private fun add(thread: XThread) {
            if (thread.posts.isEmpty() && thread.withheld.isEmpty()) return
            val index = at[thread.id]
            if (index == null) {
                at[thread.id] = threads.size
                threads += thread
                return
            }
            val known = threads[index]
            threads[index] = known.copy(
                posts = known.posts + thread.posts.filter { post -> known.posts.none { it.id == post.id } },
                withheld = known.withheld + thread.withheld.filter { gap -> known.withheld.none { it.id == gap.id } },
            )
        }

        fun entry(entry: J, isPinned: Boolean = false, replacing: String? = null) {
            val entryId = entry["entryId"].string ?: replacing ?: return
            val content = entry["content"]
            val cursor = cursorOf(content)
            val results = content["itemContent"]["tweet_results"]
            when {
                entryId.startsWith("promoted-") || content["itemContent"]["promotedMetadata"].exists -> Unit
                cursor != null -> when (kindOf(entryId, cursor)) {
                    CursorKind.BOTTOM -> bottom = cursor.value
                    CursorKind.TOP -> top = cursor.value
                    CursorKind.SHOW_MORE -> if (showMore == null) showMore = cursor.value
                    CursorKind.OTHER -> Unit
                }
                results.exists -> item(entryId, results["result"], isPinned)
                content["items"].exists -> module(entryId, content["items"].list)
            }
        }

        private fun item(entryId: String, result: J, isPinned: Boolean) {
            val post = post(result)
            when {
                post != null -> {
                    if (isPinned) pinned += post.id
                    add(XThread(post.id, listOf(post)))
                }
                // A conversation keeps the place of a post X withholds; a feed skips it.
                keepWithheld && result.exists && entryId.startsWith("tweet-") -> {
                    val id = entryId.removePrefix("tweet-")
                    add(XThread(id, emptyList(), listOf(withheld(result, id))))
                }
            }
        }

        fun moduleItems(moduleId: String?, items: List<J>) {
            val first = items.firstNotNullOfOrNull { it["entryId"].string }.orEmpty()
            module(moduleId ?: first.substringBefore("-tweet-"), items)
        }

        /**
         * A module of entries: a media grid is a post per tile; anything else
         * (a conversation, a profile's thread) one thread, where a post X
         * withholds keeps its place and a cursor nested in it is kept aside.
         */
        private fun module(moduleId: String, items: List<J>) {
            if (moduleId.contains("-grid-")) {
                items.mapNotNull { item -> resultOf(item)?.let { post(it) } }.forEach { add(XThread(it.id, listOf(it))) }
                return
            }
            val posts = ArrayList<XPost>()
            val gaps = ArrayList<XWithheld>()
            for (item in items) {
                val itemId = item["entryId"].string.orEmpty()
                val content = item["item"]["itemContent"]
                val cursor = cursorOf(content) ?: cursorOf(item["item"]) ?: cursorOf(item["content"])
                val result = resultOf(item)
                val type = content["itemType"].string
                when {
                    itemId.contains("promoted") || content["promotedMetadata"].exists -> Unit
                    cursor != null -> moduleCursor(itemId, cursor)
                    result == null || (type != null && type != "TimelineTweet") -> Unit
                    else -> {
                        val post = post(result)
                        // `conversationthread-1-tweet-2` is post 2's place in thread 1.
                        if (post != null) posts += post else gaps += withheld(result, itemId.substringAfterLast("-tweet-", ""))
                    }
                }
            }
            add(XThread(moduleId, posts, gaps))
        }

        private fun moduleCursor(itemId: String, cursor: Cursor) {
            val id = itemId.lowercase()
            when {
                id.contains("showmore") || cursor.type?.startsWith("ShowMore") == true -> if (moduleShowMore == null) moduleShowMore = cursor.value
                cursor.type == "Bottom" || id.contains("cursor-bottom") || id.endsWith("-bottom") || id.contains("cursorbottom") ->
                    if (moduleBottom == null) moduleBottom = cursor.value
            }
        }

        /** The tweet result a module item carries, in any of the shapes X has sent one. */
        private fun resultOf(item: J): J? = listOf(
            item["item"]["itemContent"]["tweet_results"]["result"],
            item["item"]["content"]["tweetResult"]["result"],
            item["item"]["content"]["tweet_results"]["result"],
        ).firstOrNull { it.exists }
    }

    private val instructionPaths = listOf(
        listOf("data", "user", "result", "timeline", "timeline", "instructions"),
        listOf("data", "user", "result", "timeline_v2", "timeline", "instructions"),
        listOf("data", "search_by_raw_query", "search_timeline", "timeline", "instructions"),
        listOf("data", "threaded_conversation_with_injections_v2", "instructions"),
        listOf("data", "home", "home_timeline_urt", "instructions"),
        listOf("data", "communityResults", "result", "ranked_community_timeline", "timeline", "instructions"),
        listOf("data", "retweeters_timeline", "timeline", "instructions"),
        listOf("data", "list", "members_timeline", "timeline", "instructions"),
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
