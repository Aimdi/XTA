package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import kotlin.math.roundToLong
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import org.jsoup.parser.Parser

/**
 * Reads threads.com's public pages as a logged-out browser gets them, as
 * XTA's threads_parse.dart does: the posts their `data-sjs` scripts embed,
 * the profile card their meta tags carry, and the tokens guest GraphQL needs.
 */
object ThreadsPages {
    private val lsdToken = Regex(""""LSD",\[\],\{"token":"([^"]+)"\}""")
    private val propsUserId = Regex(""""user_id"\s*:\s*"(\d+)"""")
    private val modalUserId = Regex(""""userID"\s*:\s*"(\d+)"""")
    private val compactNumber = Regex("""([\d.,]+)\s*([KMB])?""", RegexOption.IGNORE_CASE)
    private val countPart = Regex("""([\d.,]+[KMB]?)\s+(Followers?|Threads?)""", RegexOption.IGNORE_CASE)
    private val partSeparator = Regex("""\s*[•·]\s*""")
    private val metaTag = Regex("""<meta\b(?:[^>"]|"[^"]*")*>""", RegexOption.IGNORE_CASE)
    private val metaAttribute = Regex("""([A-Za-z:-]+)\s*=\s*"([^"]*)"""")
    private const val USERNAME_KEY = "\"username\""
    private const val NEAR = 480
    private const val META_LONGEST = 8_192
    private const val PK_LENGTH = 32
    private val usernameValue = Regex(""""username"\s*:\s*"([^"]*)"""")
    private val pkValue = Regex(""""pk"\s*:\s*"(\d+)"""")
    private val nameBeforeHandle = Regex("""^(.*?)\s*\(@""", RegexOption.IGNORE_CASE)

    /** The LSD token a page embeds, which guest GraphQL sends back. */
    fun lsd(html: String): String? = lsdToken.find(html)?.groupValues?.get(1)

    /**
     * The numeric user id of [handle]'s profile page: the page's `props.user_id`,
     * else a `pk` next to the handle, else the `userID` the page names most
     * (never the logged-out stub `0`).
     */
    fun userId(html: String, handle: String): String? {
        val key = handle.trim().lowercase()
        if (key.isEmpty()) return null
        propsUserId.find(html)?.groupValues?.get(1)?.takeIf { it != "0" }?.let { return it }
        return userIdNearUsername(html, key) ?: mostMentionedUserId(html)
    }

    /** `5.7M` / `1.4K` / `380` as a number; null when it is not one. */
    fun compactCount(raw: String?): Long? {
        val match = raw?.trim()?.let(compactNumber::matchEntire) ?: return null
        val number = match.groupValues[1].replace(",", "").toDoubleOrNull() ?: return null
        val scale = when (match.groupValues[2].uppercase()) {
            "K" -> 1_000L
            "M" -> 1_000_000L
            "B" -> 1_000_000_000L
            else -> 1L
        }
        return (number * scale).roundToLong()
    }

    /**
     * The profile card a logged-out browser is shown on `threads.com/@handle`:
     * the name, picture and the description with its counts. It tells no
     * following count and no link; null when the page has none of its tags.
     */
    fun guestProfile(html: String, handle: String): ThreadsProfile? {
        val key = (ThreadsLinks.normaliseHandle(handle) ?: handle).trim().lowercase()
        if (key.isEmpty()) return null
        val tags = metas(html)
        val title = tags["og:title"] ?: tags["twitter:title"]
        val description = tags["og:description"] ?: tags["description"]
        val image = tags["og:image"] ?: tags["twitter:image"]
        if (title == null && description == null && image == null) return null
        val (followers, posts, biography) = guestDescription(description?.let(::decode).orEmpty())
        val name = nameBeforeHandle.find(title?.let(::decode).orEmpty())?.groupValues?.get(1)?.trim()?.ifEmpty { null }
        val pk = userId(html, key).orEmpty()
        return ThreadsProfile(
            pk = pk,
            id = pk,
            username = key,
            fullName = name ?: key,
            profilePicUrl = image?.let { decode(it).replace("&amp;", "&") }.orEmpty(),
            biography = biography,
            followerCount = followers,
            mediaCount = posts,
        )
    }

    /**
     * Every `thread_items` list a page embeds, in page order: a thread's root
     * and its author's continuation on a profile, or the conversation leading
     * to a post and each reply thread under it on a post page. A post repeated
     * across scripts stays where it first appeared, so no chain shows it twice.
     */
    fun chains(body: String): List<List<ThreadsPost>> =
        chainsIn(ThreadsScripts.embedded(body, "\"thread_items\"").mapNotNull(ThreadsJson::decode))

    /** [chains] of JSON already decoded: a page's scripts, or a GraphQL answer. */
    fun chainsIn(roots: List<JsonElement>): List<List<ThreadsPost>> {
        val chains = mutableListOf<List<ThreadsPost>>()
        val seen = HashSet<String>()
        roots.forEach { collectChains(it, chains, seen, depth = 0) }
        return chains
    }

    /**
     * A profile page's threads: one card per chain the profile wrote or reposted,
     * keeping its chain's length. A repost keeps the original author as its
     * handle, so the profile is matched as either.
     */
    fun profileThreads(body: String, handle: String): List<ThreadsPost> = chains(body)
        .filter { handle.isEmpty() || it.first().handle == handle || it.first().repostedByHandle == handle }
        .map { it.first().withSelfThreadCount(ThreadsParser.selfThreadLength(it.first(), it.drop(1))) }

    /**
     * The front page's threads, as a logged-out browser is recommended them:
     * each chain's first post, keeping its chain's length, whoever wrote it.
     */
    fun recommended(body: String): List<ThreadsPost> = profileThreads(body, "")

    /** Every post a post page embeds, root and replies, flattened. */
    fun thread(body: String): List<ThreadsPost> = chains(body).flatten()

    /** A profile's replies page: each of [handle]'s replies, without the other people's posts it is shown under. */
    fun replies(body: String, handle: String): List<ThreadsPost> = chains(body).flatMap { chain ->
        chain.filterIndexed { index, post -> post.handle == handle && (index > 0 || post.isReply) }
    }

    /**
     * A search page's results: in each chain, the post the page found it for
     * (the one whose text holds [query], or with [tag] the one filed under it),
     * else the chain's first.
     */
    fun searchResults(body: String, query: String, tag: Boolean = false): List<ThreadsPost> {
        val needle = query.trim().removePrefix("#").lowercase()
        val found = { post: ThreadsPost ->
            if (tag) post.topicTag?.lowercase() == needle else post.text.lowercase().contains(needle)
        }
        return chains(body).map { chain -> chain.firstOrNull(found) ?: chain.first() }
    }

    private fun collectChains(node: JsonElement, out: MutableList<List<ThreadsPost>>, seen: MutableSet<String>, depth: Int) {
        if (depth > ThreadsJson.MAX_DEPTH) return
        when (node) {
            is JsonArray -> node.forEach { collectChains(it, out, seen, depth + 1) }
            is JsonObject -> {
                val items = node["thread_items"]
                if (items is JsonArray && items.isNotEmpty()) {
                    val chain = J(items).list.mapNotNull { item -> ThreadsParser.post(item["post"])?.takeIf { seen.add(it.id) } }
                    if (chain.isNotEmpty()) out += chain
                }
                node.values.forEach { collectChains(it, out, seen, depth + 1) }
            }
            else -> Unit
        }
    }

    /**
     * A `pk` within [NEAR] characters after a `"username":"<key>"`, else before
     * one. The page is scanned for the key name as it is spelled, and only
     * the text around each one is matched: a whole-page pattern that ignored
     * case cost a phone tens of milliseconds a profile.
     */
    private fun userIdNearUsername(html: String, key: String): String? {
        val spots = usernameSpots(html, key)
        return spots.firstNotNullOfOrNull { (_, end) -> pkIn(html, end, minOf(html.length, end + NEAR + PK_LENGTH))?.groupValues?.get(1) }
            ?: spots.firstNotNullOfOrNull { (start, _) -> pkIn(html, maxOf(0, start - NEAR - PK_LENGTH), start)?.groupValues?.get(1) }
    }

    /** Where each `"username":"<key>"` of [html] starts and ends, the handle's case ignored. */
    private fun usernameSpots(html: String, key: String): List<Pair<Int, Int>> {
        val spots = ArrayList<Pair<Int, Int>>()
        var at = html.indexOf(USERNAME_KEY)
        while (at >= 0) {
            usernameValue.find(html.subSequence(at, minOf(html.length, at + USERNAME_KEY.length + key.length + 16)))
                ?.takeIf { it.range.first == 0 && it.groupValues[1].equals(key, ignoreCase = true) }
                ?.let { spots += at to at + it.value.length }
            at = html.indexOf(USERNAME_KEY, at + USERNAME_KEY.length)
        }
        return spots
    }

    private fun pkIn(html: String, start: Int, end: Int): MatchResult? = pkValue.find(html.subSequence(start, end))

    private fun mostMentionedUserId(html: String): String? = modalUserId.findAll(html)
        .map { it.groupValues[1] }
        .filter { it != "0" }
        .groupingBy { it }
        .eachCount()
        .entries
        .sortedByDescending { it.value }
        .firstOrNull()
        ?.key

    /** Every `<meta>` tag's `property` or `name` (lower-cased) and its `content`, read where each `<meta` stands; the first of a name wins. */
    private fun metas(html: String): Map<String, String> {
        val found = HashMap<String, String>()
        var at = ThreadsScripts.tag(html, "<meta", 0)
        while (at >= 0) {
            val tag = metaTag.find(html.subSequence(at, minOf(html.length, at + META_LONGEST)))?.takeIf { it.range.first == 0 }
            at = ThreadsScripts.tag(html, "<meta", at + 1)
            if (tag == null) continue
            val attributes = metaAttribute.findAll(tag.value).associate { it.groupValues[1].lowercase() to it.groupValues[2] }
            val name = (attributes["property"] ?: attributes["name"])?.lowercase() ?: continue
            attributes["content"]?.let { found.putIfAbsent(name, it) }
        }
        return found
    }

    private fun decode(value: String): String = Parser.unescapeEntities(value, false)

    private data class GuestDescription(val followers: Long?, val posts: Long?, val biography: String)

    /** `5.7M Followers • 380 Threads • Bio text` as its three parts; a count the page leaves out is null. */
    private fun guestDescription(description: String): GuestDescription =
        description.split(partSeparator).map { it.trim() }.fold(GuestDescription(null, null, "")) { read, part ->
            val count = countPart.matchEntire(part)
            when {
                count == null -> if (part.isNotEmpty() && read.biography.isEmpty()) read.copy(biography = part) else read
                count.groupValues[2].lowercase().startsWith("follower") ->
                    read.copy(followers = compactCount(count.groupValues[1]) ?: read.followers)
                else -> read.copy(posts = compactCount(count.groupValues[1]) ?: read.posts)
            }
        }
}
