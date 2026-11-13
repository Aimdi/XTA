package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import java.io.IOException
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.HttpUrl
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * What a guest is given of round 20's profiles, posts and media, for the
 * next round to read in CI's log (`LIVE Threads guest …`): every key of a
 * profile's own user object with its kind (false and null included, which
 * the other probes leave out), the shapes of `profile_tags`,
 * `platform_podcast_info` and the bio's stickers, any key path naming the
 * fediverse, a join date or "about this profile", and the page's queries
 * about them; on posts, `share_info`'s keys with their kinds (where a
 * vanished quote would be flagged), `community_badge`'s types, the keys
 * that may count a video's plays, how many pictures carry a description,
 * `has_audio`; and how many of each [ThreadsProfileOwner] and
 * [ThreadsParser] read. Key names, kinds, one-word types and counts only:
 * no value a profile or post holds, no token and no address. It never fails
 * on what it finds. Skipped unless RUN_LIVE=true.
 */
class ThreadsGuestLiveTest {
    @Test
    fun whatAGuestIsGivenOfProfilesPostsAndMedia(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        for (handle in PROFILES) {
            val html = page(http, ThreadsLinks.web("/@$handle"), "@$handle") ?: continue
            profile(handle, html)
            posts("@$handle", ThreadsShape.posts(ThreadsShape.scripts(html)))
        }
        var community: HttpUrl? = null
        for ((label, path) in SEARCHES) {
            val html = page(http, ThreadsLinks.web("/").resolve(path)!!, label) ?: continue
            val posts = ThreadsShape.posts(ThreadsShape.scripts(html))
            posts(label, posts)
            community = community ?: posts.firstOrNull(::inCommunity)?.let { ThreadsParser.post(J(it))?.url }?.let(ThreadsLinks::canonicalPostUrl)
        }
        community?.let { url -> page(http, url, "community post")?.let { posts("community post", ThreadsShape.posts(ThreadsShape.scripts(it))) } }
            ?: println("LIVE Threads guest: no community post on the pages read")
    }

    /** The profile's own user objects: every key's kind, the shapes of what round 20 reads, and what it read. */
    private fun profile(handle: String, html: String) {
        val roots = ThreadsShape.scripts(html)
        val owners = ThreadsShape.users(roots).filter { (it["username"] as? JsonPrimitive)?.content.equals(handle, ignoreCase = true) }
        val kinds = owners.flatMap { owner -> owner.entries.map { (key, value) -> "${name(key)}:${kind(value)}" } }.groupingBy { it }.eachCount()
        println("LIVE Threads guest @$handle owner: ${owners.size} objects, keys ${ThreadsShape.told(kinds, limit = 80)}")
        for (key in listOf("profile_tags", "platform_podcast_info", "bio_links")) {
            val under = owners.mapNotNull { it[key] }.filter { it !is JsonNull }
            println("LIVE Threads guest @$handle $key: kinds ${ThreadsShape.told(under.groupingBy(::kind).eachCount())}, paths ${ThreadsShape.told(leaves(under), limit = 60)}")
        }
        val stickers = owners.flatMap { owner -> fragments(owner).mapNotNull { it["inline_sticker_fragment"] } }
        println("LIVE Threads guest @$handle bio stickers: ${stickers.size}, paths ${ThreadsShape.told(leaves(stickers), limit = 30)}")
        println("LIVE Threads guest @$handle fediverse paths: ${ThreadsShape.told(roots.flatMap { kindsAt(it, "", FEDIVERSE, 10) }.groupingBy { it }.eachCount())}")
        println("LIVE Threads guest @$handle about paths: ${ThreadsShape.told(owners.flatMap { kindsAt(it, "", ABOUT, 3) }.groupingBy { it }.eachCount())}")
        val queries = QUERY.findAll(html).map { it.value }.distinct().toList()
        println("LIVE Threads guest @$handle queries: ${queries.joinToString(", ").ifEmpty { "none" }}")
        val read = ThreadsProfileOwner.owner(html, handle)?.facts
        val podcast = read?.podcast
        println(
            "LIVE Threads guest @$handle read: owner ${read != null}, tags ${read?.tags?.size ?: 0} (communities ${read?.tags?.count { it.community } ?: 0}, " +
                "marked ${read?.tags?.count { it.emoji != null || it.iconUrl != null } ?: 0}), podcast ${podcast != null} (title ${podcast?.title != null}, " +
                "url ${podcast?.url != null}, platform ${podcast?.platform != null}), fediverse ${read?.fediverse != null}, links ${read?.links?.size ?: 0}",
        )
    }

    /** A page's posts: the keys round 20 reads on them, with their kinds, and what [ThreadsParser] read. */
    private fun posts(label: String, posts: List<JsonObject>) {
        val infos = posts.mapNotNull { it["text_post_app_info"] as? JsonObject }
        val shares = infos.mapNotNull { it["share_info"] as? JsonObject }
        val shareKinds = shares.flatMap { share -> share.entries.map { (key, value) -> "${name(key)}:${kind(value)}" } }.groupingBy { it }.eachCount()
        println("LIVE Threads guest $label: ${posts.size} posts, share_info keys ${ThreadsShape.told(shareKinds, limit = 60)}")
        val badges = infos.mapNotNull { it["community_badge"] }.filter { it !is JsonNull }
        val types = badges.mapNotNull { ((it as? JsonObject)?.get("badge_type") as? JsonPrimitive)?.content?.let { type -> if (WORD.matches(type)) type else "<other>" } }
        println("LIVE Threads guest $label community_badge: ${badges.size}, keys ${ThreadsShape.told(ThreadsShape.keyCounts(badges.filterIsInstance<JsonObject>()))}, badge_type ${ThreadsShape.told(types.groupingBy { it }.eachCount())}")
        val items = posts + posts.flatMap { (it["carousel_media"] as? JsonArray).orEmpty().filterIsInstance<JsonObject>() }
        val counts = items.flatMap { item -> item.entries.filter { PLAYS.containsMatchIn(it.key) }.map { (key, value) -> "${name(key)}:${kind(value)}" } }
        println("LIVE Threads guest $label play keys: ${ThreadsShape.told(counts.groupingBy { it }.eachCount())}")
        val pictures = items.filter { "image_versions2" in it }
        val described = pictures.count { (it["accessibility_caption"] as? JsonPrimitive)?.content?.isNotBlank() == true }
        val audio = items.mapNotNull { it["has_audio"]?.let(::kind) }.groupingBy { it }.eachCount()
        println("LIVE Threads guest $label media: ${pictures.size} with pictures, $described described, has_audio ${ThreadsShape.told(audio)}")
        val read = posts.mapNotNull { ThreadsParser.post(J(it)) }
        println(
            "LIVE Threads guest $label read: ${read.size} posts, badges ${ThreadsShape.told(read.mapNotNull { it.facts.communityBadge?.name }.groupingBy { it }.eachCount())}, " +
                "quote unavailable ${read.count { it.facts.quoteUnavailable }}, plays ${read.count { it.facts.playCount != null }}, quotes ${read.count { it.quoted != null }}",
        )
    }

    private fun inCommunity(post: JsonObject): Boolean {
        val header = (post["text_post_app_info"] as? JsonObject)?.get("tag_header") as? JsonObject ?: return false
        return listOf("community_emoji", "community_favicon_url").any { (header[it] as? JsonPrimitive)?.isString == true }
    }

    private fun fragments(owner: JsonObject): List<JsonObject> =
        ((((owner["text_app_biography"] as? JsonObject)?.get("text_fragments") as? JsonObject)?.get("fragments")) as? JsonArray).orEmpty().filterIsInstance<JsonObject>()

    /** Every leaf under [nodes] by its key path (the last four keys) and kind. */
    private fun leaves(nodes: List<JsonElement>): Map<String, Int> = nodes.flatMap { walk(it, "", 6) }
        .map { (path, value) -> "${path.split('.').filter(String::isNotEmpty).takeLast(4).joinToString(".")}:${kind(value)}" }
        .groupingBy { it }.eachCount()

    private fun walk(node: JsonElement, path: String, depth: Int): List<Pair<String, JsonElement>> = when {
        depth < 0 -> emptyList()
        node is JsonObject -> node.entries.flatMap { (key, value) -> walk(value, "$path.${name(key)}", depth - 1) }
        node is JsonArray -> node.flatMap { walk(it, "$path[]", depth - 1) }
        else -> listOf(path to node)
    }

    /** The key paths under [node] whose last key matches [pattern], with the kind each holds. */
    private fun kindsAt(node: JsonElement, path: String, pattern: Regex, depth: Int): List<String> = when {
        depth < 0 -> emptyList()
        node is JsonObject -> node.entries.flatMap { (key, value) ->
            val here = "$path.${name(key)}"
            val mine = if (pattern.containsMatchIn(key)) listOf("${here.split('.').filter(String::isNotEmpty).takeLast(4).joinToString(".")}:${kind(value)}") else emptyList()
            mine + kindsAt(value, here, pattern, depth - 1)
        }
        node is JsonArray -> node.flatMap { kindsAt(it, "$path[]", pattern, depth - 1) }
        else -> emptyList()
    }

    private fun kind(node: JsonElement): String = when (node) {
        is JsonNull -> "null"
        is JsonObject -> "object"
        is JsonArray -> "list"
        is JsonPrimitive -> when {
            node.isString && node.content.startsWith("https://") -> "url"
            node.isString && node.content.all(Char::isDigit) && node.content.isNotEmpty() -> "digits"
            node.isString -> "text"
            node.content == "true" || node.content == "false" -> node.content
            else -> "number"
        }
    }

    private fun name(key: String): String = if (PLAIN.matches(key)) key else "<other>"

    private suspend fun page(http: OkHttpXHttp, url: HttpUrl, label: String): String? {
        delay(GAP_MILLIS)
        val response = try {
            http.get(url, HTML, followRedirects = false)
        } catch (e: IOException) {
            println("LIVE Threads guest page $label: unreachable (${e.javaClass.simpleName})")
            return null
        }
        if (response.code != 200) println("LIVE Threads guest page $label: ${response.code}")
        return response.body.takeIf { response.code == 200 }
    }

    private companion object {
        val PROFILES = listOf("threads", "zuck", "instagram")
        val SEARCHES = listOf("search?q=threads&serp_type=tags" to "/search?q=threads&serp_type=tags", "search?q=video" to "/search?q=video")
        const val GAP_MILLIS = 2_500L
        val PLAIN = Regex("[a-z_][a-z0-9_]{0,47}")
        val WORD = Regex("[a-z_]{1,40}", RegexOption.IGNORE_CASE)
        val FEDIVERSE = Regex("fedivers|federat|activitypub", RegexOption.IGNORE_CASE)
        val ABOUT = Regex("about|joined|join_date|date_joined|created|account_type|country|former_username", RegexOption.IGNORE_CASE)
        val PLAYS = Regex("play|view|impression", RegexOption.IGNORE_CASE)
        val QUERY = Regex("Barcelona[A-Za-z]*(About|Joined|Fedivers|Federat|Podcast|ProfileTag|Interest|Sticker)[A-Za-z]*Query")
        val HTML = mapOf(
            "User-Agent" to ThreadsRepairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
        )
    }
}
