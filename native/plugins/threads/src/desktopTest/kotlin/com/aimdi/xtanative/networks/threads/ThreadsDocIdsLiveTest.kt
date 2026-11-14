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
 * read in CI's log (`LIVE Threads doc ids …`): a guest reads a profile and
 * its reposts, a post, the front page, the search page and a search, takes
 * the bundles they name ([ThreadsDocIds.bundles], those the bootloader lists
 * for a wanted query's component first: the queries still missing an id,
 * and anything named for quotes, reposts, likers or translation), reads at
 * most [MOST_BUNDLES] of them, [BUNDLE_BYTES] of each, a few seconds apart,
 * and logs every `Barcelona…Query` with its doc id; then, for every query
 * the plugin uses or prepares and every one named for what is still
 * missing, the variables and fields its definition declares, by name; the
 * values its callers' code writes for the variables only code names
 * (`expansionType`, `sortOrder`…), identifiers only; and whether each
 * wanted query was found. Query names, doc ids and a query's variable names
 * are what the page sends every visitor; no session is sent, and no cookie,
 * token or word of a post is logged. Mutations are never
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
        val pages = listOfNotNull(
            "profile" to ThreadsLinks.web("/@zuck"),
            post?.let { "post" to it },
            "search" to ThreadsLinks.web("/search"),
            "results" to ThreadsLinks.searchUrl("threads", tag = false, recent = false),
            "reposts" to ThreadsLinks.web("/@zuck/reposts"),
            "front" to ThreadsLinks.web("/"),
        ).mapNotNull { (label, url) -> page(http, label, url) }
        val found = LinkedHashMap<String, LinkedHashSet<String>>()
        pages.flatMap(ThreadsNextPages::preloads).forEach { preload -> preload.docId?.let { found.getOrPut(preload.name) { LinkedHashSet() } += it } }
        println("LIVE Threads doc ids pages: ${pages.size} read, ${found.size} queries preloaded with an id")

        // The bundles of the components named for what is still missing lead; the rest follow in page order.
        val bundles = ThreadsDocIds.bundles(pages.joinToString("\n"), MISSING + WANTED_WORDS)
        val declared = LinkedHashMap<String, Declared>()
        val values = VALUE_NAMES.associateWith { LinkedHashSet<String>() }
        val hints = LinkedHashMap<String, String>()
        var read = 0
        var bytes = 0L
        for (address in bundles.take(MOST_BUNDLES)) {
            if (read >= ENOUGH_BUNDLES && MISSING.all { it in found }) break
            val js = bundle(address) ?: continue
            read++
            bytes += js.length
            ThreadsDocIds.operations(js).forEach { found.getOrPut(it.name) { LinkedHashSet() } += it.docId }
            // Every query this bundle defines that the plugin uses or prepares, or that is named for what is still missing.
            (WATCHED + ThreadsDocIds.defined(js).filter(::isWanted)).distinct().forEach { name ->
                val told = Declared(ThreadsDocIds.variables(js, name), ThreadsDocIds.providedVariables(js, name), ThreadsDocIds.fields(js, name))
                if (!told.isEmpty) declared.getOrPut(name) { Declared() }.add(told)
            }
            MISSING.forEach { name -> if (name !in hints) hint(js, name)?.let { hints[name] = it } }
            written(js).forEach { (name, value) -> values.getValue(name).takeIf { it.size < MOST_VALUES }?.add(value) }
        }

        found.filterKeys { QUERY.matches(it) }.forEach { (name, ids) -> println("LIVE Threads doc ids $name=${ids.filter { it.all(Char::isDigit) }.joinToString("|")}") }
        declared.forEach { (name, told) ->
            println("LIVE Threads doc ids declared $name: variables ${told(told.variables.filterNot(ThreadsRelayProviders::isProvided))}; provided ${told(told.provided)}; fields ${told(told.fields)}")
        }
        // A missing query a bundle names without an id this reads: how its module starts, so its shape can be read next round.
        MISSING.filter { it !in found }.forEach { name -> hints[name]?.let { println("LIVE Threads doc ids unread $name: $it") } }
        values.filterValues { it.isNotEmpty() }.forEach { (name, written) -> println("LIVE Threads doc ids values $name: ${written.joinToString(",")}") }
        val unseen = declared.values.flatMap { it.provided }.distinct().filter { it !in ThreadsRelayProviders.defaults }
        println("LIVE Threads doc ids provided not sent yet: ${unseen.joinToString(",").ifEmpty { "none" }}")
        val named = found.keys.filter { name -> QUERY.matches(name) && WANTED_WORDS.any { it in name } }
        println("LIVE Threads doc ids named for quotes, reposts, likers or translation: ${named.joinToString(", ").ifEmpty { "none" }}")
        println(
            "LIVE Threads doc ids summary: ${bundles.size} bundles named, $read read, $bytes bytes, ${found.size} names found, ${declared.size} declared; wanted: " +
                MISSING.joinToString(", ") { "$it ${if (it in found) "found" else if (it in hints) "named without an id" else "missing"}" } +
                "; used: " + WATCHED.joinToString(", ") { "$it ${if (it in found) "found" else "missing"}" },
        )
    }

    /** A query named for what the plugin still lacks a guest read of. */
    private fun isWanted(name: String): Boolean = name in MISSING || WANTED_WORDS.any { it in name }

    /** The values [js]'s code writes for the variables only code names, as (variable, value): identifiers, nothing a person wrote. */
    private fun written(js: String): List<Pair<String, String>> = VALUE.findAll(js).map { it.groupValues[1] to it.groupValues[2] }.distinct().toList()

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

    private fun told(names: Collection<String>): String = names.joinToString(",").ifEmpty { "none" }

    /** What the bundles declare of one query, by name. */
    private class Declared(variables: List<String> = emptyList(), provided: List<String> = emptyList(), fields: List<String> = emptyList()) {
        val variables = LinkedHashSet(variables)
        val provided = LinkedHashSet(provided)
        val fields = LinkedHashSet(fields)
        val isEmpty: Boolean get() = variables.isEmpty() && provided.isEmpty() && fields.isEmpty()

        fun add(more: Declared) {
            variables += more.variables
            provided += more.provided
            fields += more.fields
        }
    }

    private companion object {
        /** Every query the plugin asks or prepares, by a bundle's doc id or a page's: what each declares is logged. All reads. */
        val WATCHED = listOf(
            ThreadsBundleReads.INLINE_REPLIES,
            ThreadsBundleReads.ABOUT_PROFILE,
            ThreadsBundleReads.TRENDING_TOPICS,
            ThreadsBundleReads.SEARCH_USERS,
            ThreadsBundleReads.RECOMMENDED_USERS,
        ) + ThreadsRefetch.entries.map { it.query } + listOf(
            "BarcelonaSearchRecommendedUsersQuery",
            "BarcelonaSearchPeopleAlsoSearchedQuery",
            "BarcelonaRightPanelProfileSuggestedUsersQuery",
            "BarcelonaSearchSuggestedTopicsSectionQuery",
            "BarcelonaPostPageThreadViewSectionInlineExpansionQuery",
            "BarcelonaProfileThreadsTabDirectQuery",
            "BarcelonaProfileRepliesTabDirectQuery",
            "BarcelonaProfileRepostsTabDirectQuery",
            "BarcelonaPostPageDownwardQuery",
            "BarcelonaSearchResultsQuery",
            "BarcelonaLoggedOutFeedContainerQuery",
            "BarcelonaProfileUserInterestsDialogUserQuery",
            "BarcelonaCustomFeedPickerPageQuery",
            "BarcelonaPostViewCountQuery",
        )

        /** The queries run 114 found no id for: the bundles of their components are read first, and reading goes on until each is found. */
        val MISSING = listOf(
            "BarcelonaPostTranslationQuery",
            "BarcelonaProfileRepostsTabRefetchableDirectQuery",
            "BarcelonaProfileRepliesTabRefetchableDirectQuery",
            "BarcelonaProfileMediaTabRefetchableDirectQuery",
            "BarcelonaLoggedOutFeedPaginationQuery",
            "BarcelonaGetCommunityCustomFeedQuery",
        )

        /** What a query or a component is named for when it reads what the plugin still lacks a guest read of. */
        val WANTED_WORDS = listOf("Quote", "Repost", "Liker", "Translat")

        /** The variables whose values only the code that asks a query names. */
        val VALUE_NAMES = listOf("expansionType", "filterType", "sortOrder", "requested_subtab", "search_surface")
        val VALUE = Regex("""(?<![A-Za-z0-9_$])"?(${VALUE_NAMES.joinToString("|")})"?\s*:\s*"([A-Za-z][A-Za-z0-9_]{0,40})"""")
        const val MOST_VALUES = 16
        val STATIC_HOSTS = setOf("static.cdninstagram.com", "static.threads.com", "static.threads.net")
        val QUERY = Regex("Barcelona[A-Za-z0-9]*Query")
        const val MOST_BUNDLES = 80

        /** How many bundles are read whatever was found: the names nobody knew to ask for are in the rest. */
        const val ENOUGH_BUNDLES = 40
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
