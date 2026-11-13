package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import kotlin.test.Test
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * A post page and a reply's page as a guest is shown them, for the next round
 * to read in CI's log: how [ThreadsPostPage] arranged them (counts), and, per
 * preloaded query, the key paths at which posts sit. Key names and counts
 * only; nothing a post says. Skipped unless RUN_LIVE=true.
 */
class ThreadsConversationLiveTest {
    private val plainKey = Regex("[a-z_][a-z0-9_]{0,47}")

    @Test
    fun theShapeOfAConversationAsAGuestIsShownIt(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        val http = OkHttpXHttp()
        val client = ThreadsDirectClient(http)
        val post = client.guestAccount("zuck").items.firstNotNullOfOrNull { it.url }
        val reply = runCatching { client.guestReplies("zuck").items.firstNotNullOfOrNull { it.url } }.getOrNull()
        listOfNotNull(post?.let { "post" to it }, reply?.let { "reply" to it }).forEach { (label, url) ->
            delay(GAP_MILLIS)
            val body = http.get(ThreadsLinks.canonicalPostUrl(url)!!, mapOf("User-Agent" to ThreadsRepairs().webUserAgent, "Accept" to "text/html"), followRedirects = true).body
            log(label, body)
        }
    }

    private fun log(label: String, body: String) = try {
        logShape(label, body)
    } catch (e: Exception) {
        println("LIVE Threads conversation $label: the probe failed (${e.javaClass.simpleName})")
    }

    private fun logShape(label: String, body: String) {
        val page = ThreadsPostPage.read(body)
        val levels = page.replies.flatMap { it.levels.values }.groupingBy { it }.eachCount().toSortedMap()
        println(
            "LIVE Threads conversation $label: target ${page.target != null}, above ${page.above.size}, continuation ${page.continuation.size}, " +
                "${page.replies.size} reply threads of ${page.replies.map { it.posts.size }}, levels $levels, " +
                "${page.replies.count { it.hasMore }} going on, more replies ${page.moreReplies}, related ${page.related.size}, chains ${page.chains.size}",
        )
        ThreadsShape.scripts(body).flatMap { queries(it) }.forEach { (name, result) ->
            val paths = postPaths(result, "").groupingBy { it }.eachCount()
            println("LIVE Threads conversation $label $name posts at: ${ThreadsShape.told(paths)}")
            println("LIVE Threads conversation $label $name direct_replies at: ${ThreadsShape.told(ThreadsShape.paths(listOf(result), Regex("^direct_replies$"), depth = 40, tail = 12))}")
            val nodes = replyNodes(result)
            println("LIVE Threads conversation $label $name reply edge node keys: ${ThreadsShape.told(ThreadsShape.keyCounts(nodes))}")
            println("LIVE Threads conversation $label $name data.media keys: ${ThreadsShape.told(ThreadsShape.keyCounts(listOfNotNull(media(result))))}")
        }
    }

    private fun queries(node: JsonElement): List<Pair<String, JsonElement>> = when (node) {
        is JsonArray -> {
            val name = (node.firstOrNull() as? JsonPrimitive)?.takeIf { it.isString }?.content
            val here = if (name != null && name.startsWith("adp_")) listOfNotNull(node.getOrNull(1)?.let { name.removePrefix("adp_").substringBefore("RelayPreloader") to it }) else emptyList()
            here + node.flatMap(::queries)
        }
        is JsonObject -> node.values.flatMap(::queries)
        else -> emptyList()
    }

    /** Every `direct_replies.edges[].node` object under [node], at any depth. */
    private fun replyNodes(node: JsonElement): List<JsonObject> = when (node) {
        is JsonObject -> {
            val edges = ((node["direct_replies"] as? JsonObject)?.get("edges") as? JsonArray).orEmpty()
            edges.mapNotNull { (it as? JsonObject)?.get("node") as? JsonObject } + node.values.flatMap(::replyNodes)
        }
        is JsonArray -> node.flatMap(::replyNodes)
        else -> emptyList()
    }

    private fun media(result: JsonElement): JsonObject? =
        ((((result as? JsonObject)?.get("__bbox") as? JsonObject)?.get("result") as? JsonObject)?.get("data") as? JsonObject)?.get("media") as? JsonObject

    /** Where each post object sits, as a key path with `[]` for lists, posts inside posts included. */
    private fun postPaths(node: JsonElement, path: String): List<String> = when (node) {
        is JsonObject -> {
            val here = if ("user" in node && "taken_at" in node) listOf(path.ifEmpty { "." }) else emptyList()
            here + node.entries.flatMap { (key, value) -> postPaths(value, "$path.${if (plainKey.matches(key)) key else "<other>"}") }
        }
        is JsonArray -> node.flatMap { postPaths(it, "$path[]") }
        else -> emptyList()
    }

    private companion object {
        const val GAP_MILLIS = 2_500L
    }
}
