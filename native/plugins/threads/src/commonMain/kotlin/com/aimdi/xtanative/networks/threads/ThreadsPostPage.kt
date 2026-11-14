package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.jsoup.Jsoup

/** One reply under a post and the replies Meta nested under it, in page order. */
data class ThreadsReplyThread(
    val posts: List<ThreadsPost>,
    /** Each post's `reply_level` by id: 1 answers the post, 2 answers a reply. Missing where Meta did not say. */
    val levels: Map<String, Int> = emptyMap(),
    /** Meta holds more of this thread than the page carried. */
    val hasMore: Boolean = false,
    /** Where Meta says the rest of this thread starts, when the page named it ([ThreadsDirectClient.deeperReplies] sends it back). */
    val pagingToken: String? = null,
) {
    /** This thread with [more] of it read after: its posts not here yet on the end, and unfinished only if [more] is. */
    fun then(more: ThreadsReplyThread): ThreadsReplyThread {
        val ids = posts.mapTo(HashSet()) { it.id }
        return ThreadsReplyThread(posts + more.posts.filter { ids.add(it.id) }, levels + more.levels, more.hasMore, more.pagingToken)
    }
}

/**
 * What a post page tells a guest. Meta now preloads it as separate queries
 * (`BarcelonaPostPageTargetQuery`, `…UpwardQuery`, `…DownwardQuery`,
 * `BarcelonaLoggedOutRelatedPostsQuery`): the post, what it answers, its
 * replies, and posts that are not part of it at all. A page in the older
 * shape keeps its `thread_items` [chains], the related posts left out.
 */
data class ThreadsPostPage(
    /** The post the page is about; null on a page in the older shape. */
    val target: ThreadsPost? = null,
    /** The posts [target] answers, oldest first. */
    val above: List<ThreadsPost> = emptyList(),
    /** [target]'s author's own posts chained under it. */
    val continuation: List<ThreadsPost> = emptyList(),
    val replies: List<ThreadsReplyThread> = emptyList(),
    /** Meta holds more replies than the page carried (`direct_replies.page_info.has_next_page`). */
    val moreReplies: Boolean = false,
    /** Where the replies after these start, when the page named a cursor and the query to follow it ([ThreadsNextPage]). */
    val repliesNext: String? = null,
    /** Posts Threads suggests beside this one: never part of the conversation. */
    val related: List<ThreadsPost> = emptyList(),
    /** A page in the older shape: every `thread_items` chain, the post's own first. */
    val chains: List<List<ThreadsPost>> = emptyList(),
) {
    val isEmpty: Boolean get() = target == null && chains.isEmpty() && related.isEmpty()

    /** The conversation as chains, as the older shape gave it: the post's own chain, then each reply thread. */
    val conversationChains: List<List<ThreadsPost>>
        get() = target?.let { listOf(above + it + continuation) + replies.map(ThreadsReplyThread::posts) } ?: chains

    companion object {
        /** A post page's body read; a page with nothing Meta embedded is [isEmpty]. */
        fun read(body: String): ThreadsPostPage = ThreadsPostPageReader.read(body)
    }
}

/** Reads [ThreadsPostPage] from a page's `data-sjs` scripts, every field as if it might be gone. */
internal object ThreadsPostPageReader {
    private const val PRELOAD = "adp_"
    private val THREAD_SKIPS = setOf("share_info", "direct_replies")
    private val REPLY_SKIPS = setOf("share_info")

    private class Query(val name: String, val result: J)

    fun read(body: String): ThreadsPostPage {
        val roots = Jsoup.parse(body).select("script[data-sjs]").map { it.data().trim() }
            .filter { PRELOAD in it || "thread_items" in it }
            .mapNotNull(ThreadsJson::decode)
        val queries = roots.flatMap(::queries)
        val (relatedQueries, own) = queries.partition { "Related" in it.name }
        val related = relatedQueries.flatMap { posts(it.result.raw, THREAD_SKIPS) }.map { it.first }
        val target = own.firstNotNullOfOrNull { target(it) }
        if (target == null) {
            val relatedIds = related.mapTo(HashSet()) { it.id }
            val chains = ThreadsPages.chains(body).map { chain -> chain.filter { it.id !in relatedIds } }.filter { it.isNotEmpty() }
            return ThreadsPostPage(related = related, chains = chains)
        }
        return arranged(target, own, related).orChains(body)
    }

    /**
     * The older reading when the newer found no reply where Meta's page
     * carries more than the post's own chain of `thread_items`: replies a
     * guest could read before must not vanish because Meta moved them.
     */
    private fun ThreadsPostPage.orChains(body: String): ThreadsPostPage {
        if (replies.isNotEmpty()) return this
        val relatedIds = related.mapTo(HashSet()) { it.id }
        val chains = ThreadsPages.chains(body).map { chain -> chain.filter { it.id !in relatedIds } }.filter { it.isNotEmpty() }
        return if (chains.size > 1) ThreadsPostPage(related = related, chains = chains) else this
    }

    private fun arranged(target: ThreadsPost, queries: List<Query>, related: List<ThreadsPost>): ThreadsPostPage {
        val thread = distinct(queries.flatMap { posts(it.result.raw, THREAD_SKIPS) }.map { it.first }).filter { it.id != target.id }
        val (above, below) = split(thread, target)
        val continuation = below.filter { it.handle == target.handle }
        val seen = HashSet<String>().apply { add(target.id); addAll(above.map { it.id }); addAll(continuation.map { it.id }) }
        val connections = queries.flatMap { replyConnections(it.result.raw, target.id) }
            .ifEmpty { queries.mapNotNull { mediaReplies(it.result) } }
            .ifEmpty { queries.firstNotNullOfOrNull { anyReplyConnection(it.result.raw) }?.let(::listOf).orEmpty() }
        val replies = connections.flatMap { edges(it) }.mapNotNull { thread(it, seen) }
        return ThreadsPostPage(
            target = target,
            above = above,
            continuation = continuation,
            replies = replies,
            moreReplies = connections.firstOrNull()?.let { hasNextPage(J(it)["page_info"]) } ?: false,
            related = related.filter { it.id !in seen },
        )
    }

    /** Every preloaded query: an array `["adp_<Name>RelayPreloader_<n>", {"__bbox": {"result": …}}]`. */
    private fun queries(node: JsonElement): List<Query> = when (node) {
        is JsonArray -> {
            val name = (node.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
            val result = J(node.getOrNull(1))["__bbox"]["result"]
            val here = if (name != null && name.startsWith(PRELOAD) && result.exists) listOf(Query(queryName(name), result)) else emptyList()
            here + node.flatMap(::queries)
        }
        is JsonObject -> node.values.flatMap(::queries)
        else -> emptyList()
    }

    /**
     * The reply threads a next page of [targetId]'s replies brings: its
     * `direct_replies` edges, else those of the first `direct_replies` it
     * holds, should Meta answer the next page around another node.
     */
    fun replyThreads(roots: List<JsonElement>, targetId: String): List<ThreadsReplyThread> {
        val connections = roots.flatMap { replyConnections(it, targetId) }
            .ifEmpty { roots.mapNotNull { mediaReplies(J(it)) } }
            .ifEmpty { roots.firstNotNullOfOrNull(::anyReplyConnection)?.let(::listOf).orEmpty() }
        val seen = HashSet<String>().apply { add(targetId) }
        return connections.flatMap { edges(it) }.mapNotNull { thread(it, seen) }
    }

    /**
     * The replies an inline expansion brings under the reply [targetId]
     * ([ThreadsBundleReads.INLINE_REPLIES]), as one thread in the answer's
     * order: its reply connections where the answer is shaped as a post
     * page's next replies are, else every post it holds but the reply itself.
     * Null when it holds none.
     */
    fun deeper(roots: List<JsonElement>, targetId: String): ThreadsReplyThread? {
        val threads = replyThreads(roots, targetId).ifEmpty {
            val found = distinctPosts(roots.flatMap { posts(it, REPLY_SKIPS) }).filter { (post, _) -> post.id != targetId }
            listOf(ThreadsReplyThread(found.map { it.first }, found.mapNotNull { (post, level) -> level?.let { post.id to it } }.toMap(), roots.any(::morePages)))
        }
        val posts = threads.flatMap { it.posts }
        if (posts.isEmpty()) return null
        return ThreadsReplyThread(posts, threads.fold(emptyMap()) { all, thread -> all + thread.levels }, threads.any { it.hasMore }, threads.firstNotNullOfOrNull { it.pagingToken })
    }

    private fun distinctPosts(found: List<Pair<ThreadsPost, Int?>>): List<Pair<ThreadsPost, Int?>> {
        val ids = HashSet<String>()
        return found.filter { ids.add(it.first.id) }
    }

    private fun queryName(key: String): String = key.removePrefix(PRELOAD).substringBefore("RelayPreloader").substringBefore('_')

    /** The post a query is about: its `data.media`, the shape the Target and Downward queries share. */
    private fun target(query: Query): ThreadsPost? {
        val media = query.result["data"]["media"]
        return if (isPost(media.raw)) ThreadsParser.post(media) else null
    }

    /** Ancestors before [target], the rest after it: by time when both are dated, else by page order. */
    private fun split(thread: List<ThreadsPost>, target: ThreadsPost): Pair<List<ThreadsPost>, List<ThreadsPost>> {
        val at = target.publishedAt ?: return thread to emptyList()
        val (dated, undated) = thread.partition { it.publishedAt != null }
        val (before, after) = dated.sortedBy { it.publishedAt }.partition { it.publishedAt!! <= at }
        return (undated + before) to after
    }

    /**
     * The `direct_replies` connections of the post [targetId] names, wherever a
     * query put it. Another post's replies (the root's, beside this one) are
     * not this post's, and are left out.
     */
    private fun replyConnections(node: JsonElement?, targetId: String): List<JsonObject> = when (node) {
        is JsonObject -> when {
            !isPost(node) -> node.values.flatMap { replyConnections(it, targetId) }
            ThreadsParser.post(J(node))?.id != targetId -> emptyList()
            else -> listOfNotNull((node["text_post_app_info"] as? JsonObject)?.get("direct_replies") as? JsonObject)
                .filter { it["edges"] is JsonArray }
        }
        is JsonArray -> node.flatMap { replyConnections(it, targetId) }
        else -> emptyList()
    }

    /**
     * A query's own `data.media.text_post_app_info.direct_replies`, whatever
     * `media` holds beside it: Meta's Downward query may answer its media
     * without what makes it a post here (an author, a caption), or under an
     * id spelled otherwise, and its replies are still the post's.
     */
    private fun mediaReplies(result: J): JsonObject? =
        (result["data"]["media"]["text_post_app_info"]["direct_replies"].raw as? JsonObject)?.takeIf { it["edges"] is JsonArray }

    /** The first `direct_replies` connection anywhere under [node], a quoted or reposted post's left out. */
    private fun anyReplyConnection(node: JsonElement?): JsonObject? = when (node) {
        is JsonObject -> (node["direct_replies"] as? JsonObject)?.takeIf { it["edges"] is JsonArray }
            ?: node.entries.filter { it.key != "share_info" }.firstNotNullOfOrNull { anyReplyConnection(it.value) }
        is JsonArray -> node.firstNotNullOfOrNull(::anyReplyConnection)
        else -> null
    }

    private fun edges(connection: JsonObject): List<JsonElement> = (connection["edges"] as? JsonArray).orEmpty()

    /** One edge as a reply thread: the posts it holds that no earlier part of the page showed. */
    private fun thread(edge: JsonElement, seen: MutableSet<String>): ThreadsReplyThread? {
        val found = posts(edge, REPLY_SKIPS).filter { (post, _) -> seen.add(post.id) }
        if (found.isEmpty()) return null
        val levels = found.mapNotNull { (post, level) -> level?.let { post.id to it } }.toMap()
        return ThreadsReplyThread(found.map { it.first }, levels, hasMore = morePages(edge), pagingToken = pagingToken(J(edge)))
    }

    /**
     * Every post under [node] in page order, with its `reply_level`. A post's
     * own `text_post_app_info` is searched on for the posts chained under it,
     * leaving out [skips] (the post it quotes or reposts, and on the post's own
     * chain its replies), here and in whatever holds a post; nothing else of a post is.
     */
    private fun posts(node: JsonElement?, skips: Set<String>): List<Pair<ThreadsPost, Int?>> = when (node) {
        is JsonObject -> if (isPost(node)) postAndBelow(node, skips) else node.entries.filter { it.key !in skips }.flatMap { posts(it.value, skips) }
        is JsonArray -> node.flatMap { posts(it, skips) }
        else -> emptyList()
    }

    private fun postAndBelow(node: JsonObject, skips: Set<String>): List<Pair<ThreadsPost, Int?>> {
        val json = J(node)
        val info = node["text_post_app_info"] as? JsonObject
        val below = info?.entries?.filter { it.key !in skips }?.flatMap { posts(it.value, skips) }.orEmpty()
        val post = ThreadsParser.post(json) ?: return below
        val level = json["text_post_app_info"]["reply_level"].integer?.toInt()?.takeIf { it > 0 }
        return listOf(post to level) + below
    }

    /** A Meta post: an author with a username, and a caption or a code. */
    private fun isPost(node: JsonElement?): Boolean {
        val obj = node as? JsonObject ?: return false
        val user = obj["user"] as? JsonObject ?: return false
        return "username" in user && ("caption" in obj || "code" in obj)
    }

    private fun morePages(node: JsonElement?): Boolean = when (node) {
        is JsonObject -> node.entries.any { (key, value) -> (key == "page_info" && hasNextPage(J(value))) || (key != "share_info" && morePages(value)) }
        is JsonArray -> node.any(::morePages)
        else -> false
    }

    /**
     * Where a reply thread's rest starts, as its edge names it: the cursor
     * after its own posts when they go on (`node.posts.page_info`, the shape
     * CI's probe saw), else a token under the names one might go by. Null
     * when the edge names none.
     */
    private fun pagingToken(edge: J): String? {
        val node = edge["node"].takeIf { it.exists } ?: edge
        val info = node["posts"]["page_info"]
        return listOfNotNull(
            info["end_cursor"].string?.takeIf { hasNextPage(info) },
            node["paging_token"].string,
            node["paging_tokens"]["downwards"].string,
            node["fetch_token"].string,
        ).firstOrNull { it.isNotBlank() && it.length <= LONGEST_TOKEN }
    }

    private const val LONGEST_TOKEN = 4_000

    private fun hasNextPage(info: J): Boolean = info["has_next_page"].boolean == true

    private fun distinct(posts: List<ThreadsPost>): List<ThreadsPost> {
        val ids = HashSet<String>()
        return posts.filter { ids.add(it.id) }
    }
}
