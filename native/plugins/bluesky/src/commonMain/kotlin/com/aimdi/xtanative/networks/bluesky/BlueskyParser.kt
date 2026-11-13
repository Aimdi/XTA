package com.aimdi.xtanative.networks.bluesky

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.Page
import java.time.Instant

/**
 * The AppView's JSON as Bluesky models, as XTA parses it. Nothing here
 * throws: a missing or reshaped field is null or empty, and an item that
 * cannot be shown (no URI, nothing to show, no web link) is left out.
 */
object BlueskyParser {
    private const val UNTIL = "until:"

    /** Replies deeper than this under an opened post are not read. */
    private const val MAX_REPLY_DEPTH = 12

    private val unshownRecords = listOf("viewNotFound", "viewBlocked", "viewDetached")

    /** A profile view (`getProfile`, or a person in any list of people); the name falls back to the handle. */
    fun profile(json: J): BlueskyProfile {
        val handle = json["handle"].string?.trim().orEmpty()
        return BlueskyProfile(
            did = json["did"].string.orEmpty(),
            handle = handle,
            displayName = json["displayName"].nonBlank() ?: handle,
            description = json["description"].string?.trim().orEmpty(),
            avatarUrl = json["avatar"].nonBlank(),
            bannerUrl = json["banner"].nonBlank(),
            createdAt = BlueskyTime.parse(json["createdAt"].string),
            followersCount = json["followersCount"].integer ?: 0,
            followsCount = json["followsCount"].integer ?: 0,
            postsCount = json["postsCount"].integer ?: 0,
            pinnedPostUri = json["pinnedPost"]["uri"].string,
        )
    }

    /** The profiles of [items], without any that names no one. */
    fun people(items: List<J>): List<BlueskyProfile> = items.map(::profile).filter { it.did.isNotEmpty() || it.handle.isNotEmpty() }

    /**
     * A PostView (or an embed's viewRecord, whose text is under `value`).
     * The repost and reply fields come from the feed item around it.
     * Null without a URI or a bsky.app link, or with nothing to show unless
     * [allowEmpty]; [parseQuote] reads the post it quotes.
     */
    fun post(
        view: J,
        repostedByName: String? = null,
        repostedByHandle: String? = null,
        repostedByDid: String? = null,
        repostedAt: Instant? = null,
        replyToHandle: String? = null,
        replyToUri: String? = null,
        allowEmpty: Boolean = false,
        parseQuote: Boolean = true,
    ): BlueskyPost? {
        val uri = view["uri"].string?.ifEmpty { null } ?: return null
        val author = view["author"]
        val handle = author["handle"].string?.trim().orEmpty()
        val record = if (view["record"].exists) view["record"] else view["value"]
        val text = record["text"].string.orEmpty()
        val media = media(view)
        val linkCard = linkCard(view)
        val quoted = if (parseQuote) quotedPost(view) else null
        if (!allowEmpty && text.isBlank() && media.isEmpty() && quoted == null && linkCard == null) return null
        val url = BlueskyUrls.webUrl(handle, uri) ?: return null
        val replyHandle = (replyToHandle ?: view["reply"]["parent"]["author"]["handle"].string?.trim())?.ifEmpty { null }
        val replyFromRecord = record["reply"]["parent"]["uri"].string
        return BlueskyPost(
            uri = uri,
            cid = view["cid"].string.orEmpty(),
            handle = handle,
            did = author["did"].string.orEmpty(),
            authorName = author["displayName"].nonBlank() ?: handle,
            avatarUrl = author["avatar"].nonBlank(),
            text = text,
            url = url,
            media = media,
            facets = BlueskyFacets.of(record),
            publishedAt = BlueskyTime.parse(record["createdAt"].string ?: view["indexedAt"].string),
            repostedAt = repostedAt,
            replyCount = view["replyCount"].integer ?: 0,
            repostCount = view["repostCount"].integer ?: 0,
            likeCount = view["likeCount"].integer ?: 0,
            quoteCount = view["quoteCount"].integer ?: 0,
            repostedByName = repostedByName,
            repostedByHandle = repostedByHandle,
            repostedByDid = repostedByDid,
            quotedPost = quoted,
            linkCard = linkCard,
            replyToHandle = replyHandle,
            isReply = replyHandle != null || !replyFromRecord.isNullOrEmpty() || !replyToUri.isNullOrEmpty(),
            replyToUri = replyFromRecord ?: replyToUri,
            labels = labels(view, record),
        )
    }

    /** One item of a feed (author, custom, list or timeline): its post, with who reposted it and whom it replies to. */
    fun feedItem(item: J): BlueskyPost? {
        val view = if (item["post"].exists) item["post"] else item
        val reason = item["reason"]
        val by = reason["by"].takeIf { "reasonRepost" in reason["\$type"].string.orEmpty() }
        val handle = by?.let { it["handle"].string?.trim() }
        val did = by?.let { it["did"].string?.trim() }
        return post(
            view,
            repostedByName = by?.let { it["displayName"].nonBlank() ?: handle },
            repostedByHandle = handle,
            repostedByDid = did,
            repostedAt = if (handle == null && did == null) null else BlueskyTime.parse(reason["indexedAt"].string),
            replyToHandle = item["reply"]["parent"]["author"]["handle"].string?.trim(),
        )
    }

    /** The posts of a `feed` answer: `getAuthorFeed`, `getFeed`, `getListFeed`. */
    fun feed(body: J): List<BlueskyPost> = body["feed"].list.mapNotNull(::feedItem)

    /** The posts of a `posts` answer of PostViews: `searchPosts`, `getPosts`, `getQuotes`. */
    fun posts(body: J): List<BlueskyPost> = body["posts"].list.mapNotNull { post(it) }

    /**
     * A `getPostThread` answer flattened: the posts above, root first, and
     * the replies depth first, each knowing the post it sits under. Posts
     * not found or blocked are skipped; null when the opened post is one.
     */
    fun thread(body: J): BlueskyThread? {
        val root = body["thread"].takeIf { it.exists } ?: return null
        val focal = threadPost(root) ?: return null
        val ancestors = generateSequence(root["parent"].takeIf { it.exists }) { node -> node["parent"].takeIf { it.exists } }
            .mapNotNull { threadPost(it) }
            .toList()
            .reversed()
        val replies = ArrayList<BlueskyPost>().also { collectReplies(root["replies"], it, 0, focal.uri) }
        return BlueskyThread(focal, ancestors, replies)
    }

    private fun threadPost(node: J, parentUri: String? = null): BlueskyPost? {
        val type = node["\$type"].string.orEmpty()
        if ("notFoundPost" in type || "blockedPost" in type || !node["post"].exists) return null
        return post(node["post"], replyToUri = parentUri)
    }

    private fun collectReplies(replies: J, into: MutableList<BlueskyPost>, depth: Int, parentUri: String) {
        if (depth > MAX_REPLY_DEPTH) return
        replies.list.forEach { reply ->
            val post = threadPost(reply, parentUri)?.also { into += it }
            collectReplies(reply["replies"], into, depth + 1, post?.uri ?: parentUri)
        }
    }

    /**
     * The pictures and videos of a post's embed, a record-with-media's media
     * included, and of a quoted viewRecord's `embeds`. Full size before the
     * thumbnail, each address once, a video without a poster left out.
     */
    fun media(view: J): List<BlueskyMedia> {
        val found = LinkedHashMap<String, BlueskyMedia>()
        fun add(item: BlueskyMedia) {
            if (item.url.isNotEmpty()) found.putIfAbsent(item.url, item)
        }
        fun addEmbed(embed: J) {
            if (!embed.exists) return
            embed["images"].list.forEach { image ->
                val url = image["fullsize"].string ?: image["thumb"].string
                if (!url.isNullOrEmpty()) add(BlueskyMedia(url, image["aspectRatio"].aspect(), image["alt"].string))
            }
            if (embed["media"].exists) addEmbed(embed["media"])
            if ("embed.video" in embed["\$type"].string.orEmpty()) add(video(embed))
        }
        addEmbed(view["embed"])
        view["embeds"].list.forEach(::addEmbed)
        return found.values.toList()
    }

    /** An `app.bsky.embed.video#view`: the HLS playlist the player opens, with the thumbnail as its poster. */
    fun video(embed: J): BlueskyMedia = BlueskyMedia(
        url = embed["thumbnail"].string?.trim().orEmpty(),
        aspectRatio = embed["aspectRatio"].aspect(),
        alt = embed["alt"].string,
        isVideo = true,
        videoUrl = embed["playlist"].nonBlank(),
        isGif = embed["presentation"].string == "gif",
    )

    /** The link card of an external embed, or of the media beside a quote. */
    fun linkCard(view: J): BlueskyLinkCard? {
        val embed = view["embed"]
        val external = embed["external"].takeIf { it.exists } ?: embed["media"]["external"].takeIf { it.exists } ?: return null
        val url = external["uri"].nonBlank() ?: return null
        return BlueskyLinkCard(url, external["title"].nonBlank(), external["description"].nonBlank(), external["thumb"].nonBlank())
    }

    /** The post a record embed quotes (under `record.record` beside media); none when it was deleted, blocked or detached. */
    fun quotedPost(view: J): BlueskyPost? {
        val embedded = view["embed"]["record"].takeIf { it.exists } ?: return null
        val record = if (embedded["record"].exists) embedded["record"] else embedded
        val type = record["\$type"].string.orEmpty()
        if (unshownRecords.any { it in type }) return null
        return post(record, allowEmpty = true, parseQuote = false)
    }

    /** The labels of a post, its author and its record, once each; a negation (`neg`) is not a label. */
    fun labels(view: J, record: J): List<String> =
        (view["labels"].list + view["author"]["labels"].list + record["labels"]["values"].list)
            .filter { it["neg"].boolean != true }
            .mapNotNull { it["val"].string }
            .distinct()

    /** A list view (`app.bsky.graph.defs#listView`). */
    fun listInfo(json: J): BlueskyListInfo = BlueskyListInfo(
        uri = json["uri"].string?.trim().orEmpty(),
        name = json["name"].string?.trim().orEmpty(),
        itemCount = json["listItemCount"].integer ?: 0,
        description = json["description"].string?.trim().orEmpty(),
        avatarUrl = json["avatar"].nonBlank(),
        creatorHandle = json["creator"]["handle"].nonBlank(),
    )

    /** A `getLists` answer, lists without a URI left out. */
    fun lists(body: J): Page<BlueskyListInfo> =
        Page(body["lists"].list.map(::listInfo).filter { it.uri.isNotEmpty() }, body["cursor"].string)

    /** A `getList` answer: the list, and a page of its members. */
    fun listMembers(body: J): BlueskyListMembers = BlueskyListMembers(
        list = body["list"].takeIf { it.exists }?.let(::listInfo),
        members = Page(people(body["items"].list.map { it["subject"] }), body["cursor"].string),
    )

    /** A custom feed view (`app.bsky.feed.defs#generatorView`); unnamed, it is called by its record key. */
    fun feedGenerator(json: J): BlueskyFeedGenerator {
        val uri = json["uri"].string?.trim().orEmpty()
        return BlueskyFeedGenerator(
            uri = uri,
            displayName = json["displayName"].nonBlank() ?: BlueskyUrls.rkeyOf(uri) ?: uri,
            description = json["description"].string?.trim().orEmpty(),
            avatarUrl = json["avatar"].nonBlank(),
            creatorHandle = json["creator"]["handle"].nonBlank(),
        )
    }

    /** The feeds of a `feeds` answer, or the one `view` of `getFeedGenerator`; those without a URI left out. */
    fun feedGenerators(body: J): List<BlueskyFeedGenerator> {
        val items = when {
            body["feeds"].exists -> body["feeds"].list
            body["view"].exists -> listOf(body["view"])
            else -> emptyList()
        }
        return items.map(::feedGenerator).filter { it.uri.isNotEmpty() }
    }

    /** The list a `getStarterPack` answer points at: `list` as a URI or a view, or the record's `list`. */
    fun starterPackListUri(body: J): String? {
        val pack = body["starterPack"]
        return pack["list"].string ?: pack["list"]["uri"].string ?: pack["record"]["list"].string
    }

    /**
     * The next-page cursor of a search answered by [Bluesky.SEARCH_APP_VIEW],
     * which refuses its own cursors: `until:` the oldest post's time
     * (indexed, else written), so the next page asks for posts before it.
     * Null on the last page.
     */
    fun searchCursor(body: J): String? {
        if (body["cursor"].string == null) return null
        val oldest = body["posts"].list
            .mapNotNull { BlueskyTime.parse(it["indexedAt"].string ?: it["record"]["createdAt"].string) }
            .minOrNull() ?: return null
        return UNTIL + BlueskyTime.iso(oldest)
    }

    /** The time a [searchCursor] asks for posts before; null for any other cursor. */
    fun searchUntil(cursor: String?): String? = cursor?.takeIf { it.startsWith(UNTIL) }?.removePrefix(UNTIL)
}
