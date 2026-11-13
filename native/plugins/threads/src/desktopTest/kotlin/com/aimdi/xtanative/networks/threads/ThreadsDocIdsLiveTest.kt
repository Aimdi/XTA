package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.OkHttpXHttp
import java.io.ByteArrayOutputStream
import kotlin.test.Test
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import org.junit.jupiter.api.Assumptions.assumeTrue

/**
 * The doc ids threads.com's JavaScript bundles name, for the next round to
 * read in CI's log (`LIVE Threads doc ids …`): a guest reads a profile, a
 * post and the search page, takes the bundles they name ([ThreadsDocIds.bundles],
 * those the bootloader lists for a watched query's component first), reads
 * at most [MOST_BUNDLES] of them, [BUNDLE_BYTES] of each, a few seconds
 * apart, and logs every `Barcelona…Query` with its doc id, then for each
 * query of the watch list whether it was found and the variables and fields
 * its definition declares, by name. Query names, doc ids and a query's
 * variable names are what the page sends every visitor; no session is sent,
 * and no cookie, token or word of a post is logged. Mutations are never
 * read ([ThreadsDocIds]). It never fails on what it finds. Skipped unless
 * RUN_LIVE=true.
 */
class ThreadsDocIdsLiveTest {
    @Test
    fun whatThreadsBundlesNameAsDocIds(): Unit = runBlocking {
        assumeTrue(System.getenv("RUN_LIVE") == "true", "Set RUN_LIVE=true to read live threads.com")
        try {
            probe()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            println("LIVE Threads doc ids: stopped (${e.javaClass.simpleName})")
        }
    }

    private suspend fun probe() {
        val http = OkHttpXHttp()
        val post = try {
            ThreadsDirectClient(http).guestAccount("zuck").items.firstNotNullOfOrNull { it.url?.let(ThreadsLinks::canonicalPostUrl) }
        } catch (e: Exception) {
            println("LIVE Threads doc ids: no post on @zuck's page (${e.javaClass.simpleName})")
            null
        }
        val pages = listOfNotNull("profile" to ThreadsLinks.web("/@zuck"), post?.let { "post" to it }, "search" to ThreadsLinks.web("/search"))
            .mapNotNull { (label, url) -> page(http, label, url) }
        val found = LinkedHashMap<String, LinkedHashSet<String>>()
        pages.flatMap(ThreadsNextPages::preloads).forEach { preload -> preload.docId?.let { found.getOrPut(preload.name) { LinkedHashSet() } += it } }
        println("LIVE Threads doc ids pages: ${pages.size} read, ${found.size} queries preloaded with an id")

        val bundles = ThreadsDocIds.bundles(pages.joinToString("\n"), WATCHED)
        val declared = WATCHED.associateWith { Declared() }
        val hints = LinkedHashMap<String, String>()
        var read = 0
        var bytes = 0L
        for (address in bundles.take(MOST_BUNDLES)) {
            if (WATCHED.all { it in found }) break
            val js = bundle(address) ?: continue
            read++
            bytes += js.length
            ThreadsDocIds.operations(js).forEach { found.getOrPut(it.name) { LinkedHashSet() } += it.docId }
            WATCHED.forEach { name ->
                declared.getValue(name).add(ThreadsDocIds.variables(js, name), ThreadsDocIds.providedVariables(js, name), ThreadsDocIds.fields(js, name))
                if (name !in hints) hint(js, name)?.let { hints[name] = it }
            }
        }

        found.filterKeys { QUERY.matches(it) }.forEach { (name, ids) -> println("LIVE Threads doc ids $name=${ids.filter { it.all(Char::isDigit) }.joinToString("|")}") }
        WATCHED.forEach { name ->
            val told = declared.getValue(name)
            if (!told.isEmpty) println("LIVE Threads doc ids declared $name: variables ${told(told.variables)}; provided ${told(told.provided)}; fields ${told(told.fields)}")
            // A watched query a bundle names without an id this reads: how its module starts, so its shape can be read next round.
            if (name !in found) hints[name]?.let { println("LIVE Threads doc ids unread $name: $it") }
        }
        println(
            "LIVE Threads doc ids summary: ${bundles.size} bundles named, $read read, $bytes bytes, ${found.size} names found; watched: " +
                WATCHED.joinToString(", ") { "$it ${if (it in found) "found" else if (it in hints) "named without an id" else "missing"}" },
        )
    }

    /** A page as a guest is shown it; null when it is not a page (a redirect to sign in, an error). */
    private suspend fun page(http: OkHttpXHttp, label: String, url: HttpUrl): String? {
        delay(GAP_MILLIS)
        return try {
            val response = http.get(url, HTML, followRedirects = false)
            println("LIVE Threads doc ids page $label: ${response.code}, ${response.body.length} chars, ${ThreadsDocIds.bundles(response.body).size} bundles named")
            response.body.takeIf { response.code == 200 }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("LIVE Threads doc ids page $label: unreachable (${e.javaClass.simpleName})")
            null
        }
    }

    /** At most [BUNDLE_BYTES] of the bundle at [address], one of Meta's static hosts; null when it is not served. */
    private suspend fun bundle(address: String): String? {
        val url = address.toHttpUrlOrNull()?.takeIf { it.isHttps && it.host in STATIC_HOSTS } ?: return null
        delay(BUNDLE_GAP_MILLIS)
        return try {
            withContext(Dispatchers.IO) {
                val request = Request.Builder().url(url).header("User-Agent", ThreadsRepairs().webUserAgent).header("Accept", "*/*").build()
                OkHttpXHttp.defaultClient.newCall(request).execute().use { response ->
                    val stream = response.body?.byteStream()
                    if (response.code != 200 || stream == null) return@use null
                    val kept = ByteArrayOutputStream()
                    val buffer = ByteArray(64 * 1024)
                    while (kept.size() < BUNDLE_BYTES) {
                        val count = stream.read(buffer, 0, minOf(buffer.size, BUNDLE_BYTES - kept.size()))
                        if (count < 0) break
                        kept.write(buffer, 0, count)
                    }
                    kept.toString(Charsets.UTF_8)
                }
            }
        } catch (e: Exception) {
            if (e is CancellationException) throw e
            println("LIVE Threads doc ids bundle: unreachable (${e.javaClass.simpleName})")
            null
        }
    }

    /** How the first module named after [name] starts in [js]: bundle code, cut short and to plain letters. */
    private fun hint(js: String, name: String): String? {
        val at = js.indexOf("__d(\"$name").takeIf { it >= 0 } ?: js.indexOf("\"$name\"").takeIf { it >= 0 } ?: return null
        return js.substring(at, minOf(js.length, at + HINT_CHARS)).map { if (it in ' '..'~') it else '?' }.joinToString("")
    }

    private fun told(names: Set<String>): String = names.joinToString(",").ifEmpty { "none" }

    /** What the bundles declare of one query, by name. */
    private class Declared {
        val variables = LinkedHashSet<String>()
        val provided = LinkedHashSet<String>()
        val fields = LinkedHashSet<String>()
        val isEmpty: Boolean get() = variables.isEmpty() && provided.isEmpty() && fields.isEmpty()

        fun add(variables: List<String>, provided: List<String>, fields: List<String>) {
            this.variables += variables
            this.provided += provided
            this.fields += fields
        }
    }

    private companion object {
        /** The guest queries CI's probes saw named on threads.com's pages with no doc id beside them; all reads. */
        val WATCHED = listOf(
            ThreadsBundleReads.INLINE_REPLIES,
            ThreadsBundleReads.ABOUT_PROFILE,
            ThreadsBundleReads.TRENDING_TOPICS,
            "BarcelonaPostTranslationQuery",
            "BarcelonaProfileUserInterestsDialogUserQuery",
            "BarcelonaCustomFeedPickerPageQuery",
            "BarcelonaGetCommunityCustomFeedQuery",
            "BarcelonaLoggedOutFeedPaginationQuery",
            "BarcelonaPostPageDirectRepliesRefetchQuery",
            "BarcelonaSearchResultsRefetchableQuery",
            "BarcelonaProfileThreadsTabRefetchableDirectQuery",
            "BarcelonaProfileRepostsTabRefetchableDirectQuery",
        )
        val STATIC_HOSTS = setOf("static.cdninstagram.com", "static.threads.com", "static.threads.net")
        val QUERY = Regex("Barcelona[A-Za-z0-9]*Query")
        const val MOST_BUNDLES = 40
        const val BUNDLE_BYTES = 6 * 1024 * 1024
        const val GAP_MILLIS = 2_500L
        const val BUNDLE_GAP_MILLIS = 2_000L
        const val HINT_CHARS = 200
        val HTML = mapOf(
            "User-Agent" to ThreadsRepairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to "en-US,en;q=0.9",
        )
    }
}
