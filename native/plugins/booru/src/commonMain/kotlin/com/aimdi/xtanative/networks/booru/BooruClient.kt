package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.doubleOrNull
import okhttp3.HttpUrl

/**
 * Reads boorus the way XTA's BooruClient does: posts and searches, popular lists, Following, a
 * post's tag kinds, comments, pools, notes, wiki page and artist, and tag suggestions. Read-only:
 * nothing is voted on, favourited, commented on or uploaded.
 *
 * Every read asks [settings] for the site, credentials, rating cap and blacklist in force, so a
 * change of site counts from the next read. Requests to each host are paced by a
 * [BooruRequestGate] of their own. Once a host has said how many tags one search may have, the
 * limit is kept for the session, per site and account, and longer searches are shared out
 * ([BooruTagLimit]).
 *
 * A refusal is a [NetworkRequestException] (network [NETWORK], the HTTP status) whose kind
 * [BooruErrorKind.of] tells; no answer stays an [IOException]. Error text never carries a login or a key.
 */
class BooruClient(
    private val http: XHttp,
    private val settings: () -> BooruSettings = { BooruSettings() },
    private val newGate: () -> BooruRequestGate = { BooruRequestGate() },
) {
    private val gates = ConcurrentHashMap<String, BooruRequestGate>()
    private val tagLimits = ConcurrentHashMap<String, Int>()
    private val splits = ConcurrentHashMap<String, BooruTagSplit>()

    /** The site in use. */
    val site: BooruSite get() = settings().site

    /** What feeds depend on ([BooruSettings.feedKey]). */
    val feedKey: String get() = settings().feedKey

    /** Where [post] came from: its own host and engine, with the credentials only if they belong there. */
    fun siteOf(post: BooruPost): BooruSite {
        val host = BooruHosts.normalise(post.host)
        return if (host.isEmpty()) site else settings().siteAt(host, post.engine)
    }

    /** [host] spoken to as [engine], with the credentials only if they belong there. */
    fun siteAt(host: String, engine: BooruEngine): BooruSite = settings().siteAt(host, engine)

    /** The newest posts of the site in use. */
    suspend fun latest(next: String? = null, limit: Int = PAGE_SIZE): BooruPostPage = posts(emptyList(), next, limit)

    /** The posts a typed search finds, on [site] or the site in use. */
    suspend fun search(query: String, next: String? = null, limit: Int = PAGE_SIZE, site: BooruSite? = null): BooruPostPage =
        posts(BooruQuery.tokens(query), next, limit, site)

    /**
     * A page of the posts with all of [tags], on [site] or the site in use; [next] is the page a
     * previous one named, the first page when null. Pages follow the host's own: the rating cap,
     * the blacklist and the tags checked on the device can empty a page without ending the feed.
     * A host that pages no deeper ends it.
     */
    suspend fun posts(tags: List<String>, next: String? = null, limit: Int = PAGE_SIZE, site: BooruSite? = null): BooruPostPage =
        reading {
            val config = settings()
            val from = site ?: config.site
            val page = pageNumber(next)
            val answer = try {
                postsFor(from, tags, page, limit, config.ratingCap)
            } catch (error: NetworkRequestException) {
                if (error.refusal?.kind != BooruErrorKind.TOO_DEEP) throw error
                return@reading BooruPostPage(emptyList(), next = null)
            }
            val parsed = BooruParser.posts(answer.body, from.engine, from.host)
            val matching = if (answer.onDevice.isEmpty()) parsed else parsed.filter { BooruFilter.matches(it, answer.onDevice) }
            shown(matching, config, next = (page + 1).takeIf { parsed.size >= limit })
        }

    /**
     * The post [ref] names, from its own site; null when the site has no such post or it is over
     * the rating cap. The blacklist does not hide it: it was asked for by name.
     */
    suspend fun postAt(ref: BooruPostRef): BooruPost? =
        posts(listOf("id:${ref.id}"), limit = 1, site = siteAt(ref.host, ref.engine)).allowed.firstOrNull { it.id == ref.id }

    /**
     * The popular posts of [query]'s day, week or month. Only Danbooru has more than one page;
     * Gelbooru keeps no list, so its highest-scored posts stand in.
     */
    suspend fun popular(query: BooruPopularQuery, next: String? = null, limit: Int = PAGE_SIZE): BooruPostPage = reading {
        val config = settings()
        val site = config.site
        val page = pageNumber(next)
        val url = BooruEndpoints.popular(site, query, page, limit)
            ?: return@reading posts(listOf(BooruQuery.scoreOrderMetatag(site.engine)), next, limit)
        val pages = BooruEndpoints.popularPages(site.engine)
        if (page > 1 && !pages) return@reading BooruPostPage(emptyList(), next = null)
        val parsed = BooruParser.posts(fetchJson(url), site.engine, site.host)
        shown(parsed, config, next = (page + 1).takeIf { pages && parsed.size >= limit })
    }

    /**
     * Following: one page of each followed tag and saved search, merged newest first, as one page
     * that counts the queries that failed. When every one failed, the first failure is thrown, so
     * the feed shows it with a retry rather than an empty page.
     */
    suspend fun following(queries: Iterable<String>, next: String? = null): BooruPostPage {
        if (pageNumber(next) > 1) return BooruPostPage(emptyList(), next = null)
        val followed = followedPosts(queries)
        followed.error?.takeIf { followed.failed == followed.asked }?.let { throw it }
        return BooruPostPage(followed.posts, next = null, failedQueries = followed.failed)
    }

    /** The newest [limitPerQuery] posts of each followed tag or saved search, merged newest first; failed queries are counted and left out. */
    suspend fun followedPosts(queries: Iterable<String>, limitPerQuery: Int = 10): BooruFollowedPosts = coroutineScope {
        val unique = queries.mapNotNull(BooruQuery::normalise).distinct()
        val settled = unique.map { query -> async { settle { posts(BooruQuery.tokens(query), limit = limitPerQuery) } } }.awaitAll()
        val errors = settled.mapNotNull { it.exceptionOrNull() }
        BooruFollowedPosts(
            posts = settled.mapNotNull { it.getOrNull() }.flatMap { it.posts }.distinctBy { it.key }
                .sortedByDescending { it.feedDate ?: Instant.EPOCH },
            asked = unique.size,
            failed = errors.size,
            error = errors.firstOrNull(),
        )
    }

    /**
     * The kind and post count of each of [post]'s tags, asked of its own site in one request. The
     * kinds the post carries stand in where the lookup is missing or fails.
     */
    suspend fun tagInfo(post: BooruPost): Map<String, BooruTagInfo> = reading {
        val fromPost = post.tagCategories.mapValues { BooruTagInfo(category = it.value) }
        val from = siteOf(post)
        val url = BooruEndpoints.tagInfo(from, post.id, post.tags) ?: return@reading fromPost
        orDefault(fromPost) {
            val looked = BooruDetailParser.tagInfo(fetchTags(url), from.engine)
            fromPost + looked.mapValues { (tag, info) -> BooruTagInfo(info.category ?: fromPost[tag]?.category, info.postCount) }
        }
    }

    /** The wiki text for [tag] on [site] or the site in use; null when the host has no page for it. */
    suspend fun wiki(tag: String, site: BooruSite? = null): String? = reading {
        val url = BooruEndpoints.wiki(site ?: this.site, tag) ?: return@reading null
        try {
            BooruDetailParser.wiki(fetchJson(url), tag)
        } catch (error: NetworkRequestException) {
            if (error.refusal?.kind == BooruErrorKind.NOT_FOUND) null else throw error
        }
    }

    /** What [site] or the site in use keeps about the artist behind [tag]; null where it keeps none or cannot be reached. */
    suspend fun artist(tag: String, site: BooruSite? = null): BooruArtist? = reading {
        val url = BooruEndpoints.artist(site ?: this.site, tag) ?: return@reading null
        orDefault(null) { BooruDetailParser.artist(fetchJson(url), tag) }
    }

    /** The pools [post] is in, on its own site; none where the host keeps none or cannot be reached. */
    suspend fun pools(post: BooruPost): List<BooruPool> = reading {
        val url = BooruEndpoints.postPools(siteOf(post), post.id) ?: return@reading emptyList()
        orDefault(emptyList()) { BooruDetailParser.pools(fetchJson(url)) }
    }

    /** The translation notes on [post], on its own site; none where the host keeps none or cannot be reached. */
    suspend fun notes(post: BooruPost): List<BooruNote> = reading {
        val url = BooruEndpoints.notes(siteOf(post), post.id) ?: return@reading emptyList()
        orDefault(emptyList()) { BooruDetailParser.notes(fetchJson(url)) }
    }

    /** [post]'s comments, oldest first, on its own site; none where the host answers them only in XML (Gelbooru). */
    suspend fun comments(post: BooruPost): List<BooruComment> = reading {
        val url = BooruEndpoints.comments(siteOf(post), post.id) ?: return@reading emptyList()
        BooruDetailParser.comments(fetchJson(url))
    }

    /** Tags the host finds alongside [tags], most related first; none where it keeps no such list or the request fails. */
    suspend fun relatedTags(tags: List<String>): List<String> = reading {
        val from = site
        val url = BooruEndpoints.relatedTags(from, tags) ?: return@reading emptyList()
        orDefault(emptyList()) { BooruDetailParser.relatedTags(fetchJson(url), from.engine) }
    }

    /**
     * Tags for what is being typed: the site's own autocomplete where it keeps one (Danbooru), else
     * the tags whose names start with it, which is also asked when the autocomplete gives no rows.
     * None when the host cannot be reached.
     */
    suspend fun suggestTags(prefix: String, limit: Int = 12): List<BooruTagSuggestion> = reading {
        val query = prefix.trim()
        if (query.isEmpty()) return@reading emptyList()
        val from = site
        autocomplete(from, query, limit) ?: orDefault(emptyList()) {
            BooruParser.tagSuggestions(fetchTags(BooruEndpoints.tagPrefix(from, query, limit)), from.engine)
        }
    }

    /** The autocomplete for [query]: null where the site keeps none or did not answer with rows, none when it could not be reached. */
    private suspend fun autocomplete(site: BooruSite, query: String, limit: Int): List<BooruTagSuggestion>? {
        val url = BooruEndpoints.autocomplete(site, query, limit) ?: return null
        return try {
            BooruParser.autocomplete(fetchJson(url))
        } catch (error: IOException) {
            emptyList()
        } catch (error: NetworkRequestException) {
            null
        }
    }

    private class PostsAnswer(val body: J, val onDevice: List<String>)

    /**
     * The host's answer for [tags], and the tags left for the device to check. Until the site has
     * said how many tags it takes, a search goes out whole; a 422 that names the limit is kept, and
     * the search is asked again shared out.
     */
    private suspend fun postsFor(site: BooruSite, tags: List<String>, page: Int, limit: Int, cap: BooruRating): PostsAnswer {
        val account = accountOf(site)
        val known = tagLimits[account]
        val split = splitFor(site, tags, known)
        return try {
            val url = BooruEndpoints.posts(site, BooruRatingRules.withCap(split.host, site.engine, site.host, cap), page, limit)
            PostsAnswer(fetchJson(url), split.device)
        } catch (error: NetworkRequestException) {
            val allowed = error.refusal?.allowedTags
            if (allowed == null || (known != null && known <= allowed)) throw error
            tagLimits[account] = allowed
            postsFor(site, tags, page, limit, cap)
        }
    }

    /** How [tags] are shared out under [limit]: whole while it is not known or they fit, else decided once per search, from the host's post counts. */
    private suspend fun splitFor(site: BooruSite, tags: List<String>, limit: Int?): BooruTagSplit {
        if (limit == null || BooruTagLimit.counted(tags) <= limit) return BooruTagSplit(tags, emptyList())
        val key = "${accountOf(site)}\n$limit\n${BooruQuery.text(tags)}"
        splits[key]?.let { return it }
        val uses = tagUses(site, BooruQuery.plainTags(tags))
        val split = BooruTagLimit.split(tags, limit, uses)
            ?: throw booruRefusal(BooruErrorKind.TAG_LIMIT, 422, "${site.host}: more counted metatags than $limit", allowedTags = limit)
        if (splits.size >= KEPT_SPLITS) splits.clear()
        splits[key] = split
        return split
    }

    /** How many posts [site] has for each of [tags], by lower-case name; none when it cannot say. */
    private suspend fun tagUses(site: BooruSite, tags: List<String>): Map<String, Int> {
        val url = BooruEndpoints.tagList(site, tags) ?: return emptyMap()
        return orDefault(emptyMap()) {
            BooruParser.tagSuggestions(fetchTags(url), site.engine).associate { it.name.lowercase() to (it.postCount ?: 0) }
        }
    }

    /** Limits differ by account: a site's guests and Members take two tags, its Gold accounts more. */
    private fun accountOf(site: BooruSite): String = "${site.host}\n${site.login}"

    /** [parsed] with what has no file or is over the cap left out, and what the blacklist hides kept aside in [BooruPostPage.allowed]. */
    private fun shown(parsed: List<BooruPost>, config: BooruSettings, next: Int?): BooruPostPage {
        val cap = config.ratingCap
        val blacklist = config.blacklist
        val allowed = parsed.filter { it.hasFile && BooruRatingRules.allows(it, cap) }
        return BooruPostPage(allowed.filterNot { BooruFilter.muted(it, blacklist) }, next?.toString(), allowed)
    }

    private fun pageNumber(next: String?): Int = next?.toIntOrNull()?.takeIf { it >= 1 } ?: 1

    private suspend fun get(url: HttpUrl): XResponse {
        val gate = gates.computeIfAbsent(url.host) { newGate() }
        gate.admit()
        val response = try {
            http.get(url, HEADERS, followRedirects = true)
        } catch (error: IOException) {
            throw masked(error)
        }
        gate.answered(response.code, BooruRequestGate.retryAfter(response.header("retry-after")))
        return response
    }

    private suspend fun fetchJson(url: HttpUrl): J = decodeJson(get(url), url)

    /** A tag list: JSON, or the XML Gelbooru 0.2 forks send for tags whatever `json=1` asks. */
    private suspend fun fetchTags(url: HttpUrl): BooruTagReply {
        val response = get(url)
        val body = response.body.trimStart()
        return if (response.code == 200 && body.startsWith("<")) BooruTagReply.Xml(body) else BooruTagReply.Json(decodeJson(response, url))
    }

    private fun decodeJson(response: XResponse, url: HttpUrl): J {
        val failure = statusFailure(response, url) ?: BooruErrorKind.BAD_RESPONSE.takeIf { !response.isSuccessful }
        if (failure != null) {
            val allowed = if (failure == BooruErrorKind.TAG_LIMIT) BooruTagLimit.limitIn(response.body) else null
            throw booruRefusal(failure, response.code, "$url: ${response.code}", allowed)
        }
        val body = response.body.trim()
        if (body.isEmpty() || body == "[]" || body == "{}") return EMPTY
        textFailure(body, url)?.let { throw booruRefusal(it, response.code, "$url: $it") }
        return jsonOf(body) ?: throw booruRefusal(BooruErrorKind.BAD_RESPONSE, response.code, "$url: not JSON")
    }

    /** [body] as JSON. A bare word (`nope`, an error page's one word) is not JSON to XTA's `jsonDecode`, though kotlinx takes it for a literal. */
    private fun jsonOf(body: String): J? {
        val element = runCatching { Json.parseToJsonElement(body) }.getOrNull() ?: return null
        val bareWord = element is JsonPrimitive && element !is JsonNull && !element.isString &&
            element.booleanOrNull == null && element.doubleOrNull == null
        return if (bareWord) null else J(element)
    }

    /** What a status says went wrong, when it says more than an unexpected answer. A 403 blames the credentials only if some went out. */
    private fun statusFailure(response: XResponse, url: HttpUrl): BooruErrorKind? = when (response.code) {
        401 -> BooruErrorKind.UNAUTHORIZED
        403 -> if (sentCredentials(url)) BooruErrorKind.UNAUTHORIZED else BooruErrorKind.FORBIDDEN
        404 -> BooruErrorKind.NOT_FOUND
        410 -> BooruErrorKind.TOO_DEEP
        422 -> BooruErrorKind.TAG_LIMIT
        429 -> BooruErrorKind.RATE_LIMITED
        500 -> BooruErrorKind.TIMED_OUT.takeIf { "QueryCanceled" in response.body || "timed out" in response.body }
        503 -> BooruErrorKind.UNAVAILABLE
        else -> null
    }

    /** What a successful answer in plain text says went wrong: Rule34 asks a guest for credentials that way, and gelbooru.com stops paging. */
    private fun textFailure(body: String, url: HttpUrl): BooruErrorKind? {
        val text = body.lowercase()
        return when {
            body.first() in "[{<" -> null
            text.startsWith("missing authentication") ->
                if (sentCredentials(url)) BooruErrorKind.UNAUTHORIZED else BooruErrorKind.NEEDS_CREDENTIALS
            "too deep" in text -> BooruErrorKind.TOO_DEEP
            else -> null
        }
    }

    private fun sentCredentials(url: HttpUrl): Boolean = url.queryParameterNames.any { it in BooruEndpoints.CREDENTIAL_PARAMETERS }

    /** [error] with the credentials its message names masked, as XTA masks a failed request's URL. */
    private fun masked(error: IOException): IOException {
        val message = error.message ?: return error
        val redacted = BooruEndpoints.redact(message)
        return if (redacted == message) error else IOException(redacted)
    }

    /** Answers are read away from the caller's thread, which may be the UI's. */
    private suspend fun <T> reading(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    /** [block]'s answer, or [default] when it fails; a cancelled read still stops. */
    private suspend fun <T> orDefault(default: T, block: suspend () -> T): T = settle(block).getOrDefault(default)

    private suspend fun <T> settle(block: suspend () -> T): Result<T> = try {
        Result.success(block())
    } catch (error: CancellationException) {
        throw error
    } catch (error: Exception) {
        Result.failure(error)
    }

    companion object {
        /** How a booru names itself in a [NetworkRequestException]. */
        const val NETWORK = "booru"

        /** Posts per page, as XTA asks. */
        const val PAGE_SIZE = 40

        private const val KEPT_SPLITS = 200

        /** Danbooru asks API readers to name themselves. */
        private const val USER_AGENT = "XTA-Booru/0.1 (by Aimdi; read-only; +https://github.com/Aimdi/XTA)"

        private val HEADERS = mapOf("User-Agent" to USER_AGENT, "Accept" to "application/json")

        private val EMPTY = J(JsonArray(emptyList()))
    }
}
