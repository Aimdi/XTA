package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.io.IOException
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * Substack's reads, as XTA's substack_client.dart: each publication's public
 * JSON (`/api/v1/posts`, else `/api/v1/archive`), then a Beehiiv site's
 * listing for a publication that moved there, then its RSS feed, then the
 * `{subdomain}.substack.com` twin a custom domain left behind. Read-only:
 * nothing is subscribed to, liked, commented on or restacked.
 *
 * Every request goes through [transport]. A rate limit is never a reason to
 * try the next host or format: it ends the chain and reaches the caller as a
 * 429 [NetworkRequestException]. When no host answered at all, the caller
 * gets the [IOException] instead of "not a publication".
 */
class SubstackClient(val transport: SubstackTransport) {
    constructor(http: XHttp) : this(SubstackTransport(http))

    private val rssPages = LinkedHashMap<String, List<SubstackPost>>()

    // ---- publications ----

    /**
     * The publication at [baseUrl]. [subdomainHint] is a followed publication's
     * stored subdomain: with it, a guessed `{first label}.substack.com` twin is
     * never asked.
     */
    suspend fun publication(baseUrl: String, subdomainHint: String? = null): SubstackPublication = reaching {
        val base = baseUrl.trim().toHttpUrlOrNull() ?: unreachable(Substack.notAPublication())
        publicationAt(base, subdomainHint)
    }

    private suspend fun publicationAt(base: HttpUrl, subdomainHint: String?): SubstackPublication {
        val leftover = SubstackHosts.leftover(base, subdomainHint)
        val followed = SubstackPublication(subdomainHint.orEmpty(), SubstackHosts.origin(base), "")
        for (candidate in SubstackHosts.requested(base, subdomainHint)) {
            val found = publicationOfListing(candidate)
                ?: publicationOfBeehiiv(candidate)
                ?: rss(candidate)?.takeIf { rssIsSubstack(candidate, it) }?.let { publicationOfRss(candidate, it) }
            if (found != null) return bound(found, base)
        }
        leftover.firstNotNullOfOrNull { publicationOfListing(it, followed) }?.let { return bound(it, base) }
        leftover.firstNotNullOfOrNull { candidate -> rss(candidate)?.takeIf { rssIsSubstack(candidate, it) }?.let { publicationOfRss(candidate, it) } }
            ?.let { return bound(it, base) }
        leftover.firstNotNullOfOrNull { publicationOfHomepage(it) }?.let { return bound(it, base) }
        unreachable(Substack.notAPublication())
    }

    /**
     * What a reader typed or pasted: a handle, a share or post link, an
     * `@profile`, a custom domain, the Substack twin a custom domain left behind,
     * or a living Beehiiv site that used to be on Substack.
     */
    suspend fun resolve(input: String): SubstackPublication = reaching {
        val trimmed = input.trim()
        val tried = HashSet<String>()
        suspend fun tryBase(base: HttpUrl?): SubstackPublication? {
            if (base == null || !tried.add(base.host)) return null
            return orNull { publicationAt(base, null) }
        }
        tryBase(SubstackHosts.postRef(trimmed)?.base)
            ?: SubstackHosts.base(trimmed)?.let { base -> SubstackHosts.candidates(base).firstNotNullOfOrNull { tryBase(it) } }
            ?: fromProfile(SubstackHosts.profileHandle(trimmed))
            ?: fromLeftoverCustomDomain(trimmed)
            ?: fromRedirect(trimmed)
            ?: unreachable(Substack.notAPublication())
    }

    /** The primary publication of the profile `@handle`, from `substack.com/api/v1/user/{handle}/public_profile`. */
    suspend fun primaryPublication(handle: String): SubstackPublication? = orNull {
        SubstackParser.publicationOfProfile(json(service("api/v1/user/$handle/public_profile")))
    }

    /**
     * [publication] with what it lacks (a real name, a description, a logo)
     * read from its host; null when it lacks nothing or the read fails. Its id
     * and address stay as followed.
     */
    suspend fun enrich(publication: SubstackPublication): SubstackPublication? {
        val complete = !SubstackHosts.nameLooksGeneric(publication.name) &&
            publication.name.lowercase() != publication.subdomain.lowercase() &&
            !publication.description.isNullOrBlank() && !publication.logoUrl.isNullOrEmpty()
        if (complete) return null
        val fresh = try {
            publication(publication.baseUrl, publication.subdomain)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        return publication.copy(
            subdomain = publication.subdomain.ifEmpty { fresh.subdomain },
            name = if (SubstackHosts.nameLooksGeneric(fresh.name)) publication.name else fresh.name,
            description = fresh.description ?: publication.description,
            logoUrl = fresh.logoUrl ?: publication.logoUrl,
        )
    }

    // ---- posts ----

    /**
     * A page of [publication]'s posts, newest first, without bodies. When the
     * JSON fails and the RSS feed answers, later pages are cut from that feed
     * without asking again. [Page.next] is the next offset when the page was full.
     */
    suspend fun posts(publication: SubstackPublication, limit: Int = 12, offset: Int = 0): Page<SubstackPost> = reaching {
        val items = postsAt(publication, limit, offset)
        Page(items, if (items.size >= limit) "${offset + limit}" else null)
    }

    /** One post whole, its body included; a paid post may carry only its free opening, or nothing. */
    suspend fun post(publication: SubstackPublication, slug: String): SubstackPost = reaching {
        val hinted = SubstackHosts.hinted(publication)
        val requested = hinted?.let { SubstackHosts.requested(it, publication.subdomain) }.orEmpty()
        val leftover = hinted?.let { SubstackHosts.leftover(it, publication.subdomain) }.orEmpty()
        requested.firstNotNullOfOrNull { base -> orNull { postFromJson(base, publication, slug) } }
            ?: requested.firstNotNullOfOrNull { postFromBeehiivPage(it, publication, slug) }
            ?: requested.firstNotNullOfOrNull { base -> rss(base, publication)?.posts?.firstOrNull { it.slug == slug } }
            ?: leftover.firstNotNullOfOrNull { base -> orNull { postFromJson(base, publication, slug) } }
            ?: leftover.firstNotNullOfOrNull { base -> rss(base, publication)?.posts?.firstOrNull { it.slug == slug } }
            ?: unreachable(Substack.failure(404, "Post not found: $slug"))
    }

    /**
     * The post at `{origin}/p/{slug}` on a host nobody follows, when that host
     * answers Substack's post API for exactly this slug; else null. Only that
     * one request is made: no twin, no RSS, no other platform.
     */
    suspend fun probeCustomDomainPost(origin: String, slug: String): SubstackPost? {
        val base = origin.trim().toHttpUrlOrNull() ?: return null
        val host = SubstackHosts.withoutWww(base.host)
        val publication = SubstackPublication(host, SubstackHosts.origin(base), host)
        return orNull { postFromJson(base, publication, slug) }?.takeIf { it.slug == slug }
    }

    /**
     * Posts in [publication]'s archive matching [query], newest first, from
     * `/api/v1/archive?search=`. An empty answer is an answer; a host that fails
     * or answers another shape hands the search to the next.
     */
    suspend fun search(publication: SubstackPublication, query: String, limit: Int = 25, offset: Int = 0): Page<SubstackPost> {
        val size = limit.coerceIn(1, 100)
        val from = offset.coerceAtLeast(0)
        val items = acrossBases(publication, "Archive search unavailable") { base -> searchOn(base, publication, query, size, from) }
        return Page(items, if (items.size >= size) "${from + size}" else null)
    }

    // ---- discussion ----

    /**
     * The discussion under the post numbered [postId], in reading order, from
     * `/api/v1/post/{id}/comments?all_comments=true&sort=best_first`. A readable
     * answer of another shape is an empty discussion; the article is already shown.
     */
    suspend fun comments(publication: SubstackPublication, postId: String): List<SubstackComment> =
        acrossBases(publication, "Comments unavailable") { base ->
            val url = endpoint(base, "api/v1/post/$postId/comments", "all_comments" to "true", "sort" to "best_first")
            val body = J.parse(get(url, JSON).body)
            if (!body.exists) throw Substack.failure(200, "Unreadable comments from ${base.host}")
            SubstackParser.comments(body)
        }

    /** The discussion under [post]. Comments are kept under the API's number, so an RSS copy asks for its post first. */
    suspend fun commentsOf(post: SubstackPost): List<SubstackComment> {
        val id = if (post.hasApiId || post.slug.isEmpty()) post.id else post(post.publication, post.slug).id
        return comments(post.publication, id)
    }

    /**
     * A page of public Notes from the reader feed of [host] (else
     * substack.com): the open discovery mix, not a Following timeline, which
     * needs a Substack session. A cursor belongs to the host that gave it.
     */
    suspend fun notes(host: String? = null, cursor: String? = null, limit: Int = 20): Page<SubstackNote> {
        val base = SubstackHosts.https(host?.takeIf { it.isNotEmpty() } ?: "substack.com") ?: throw Substack.failure(404, "No host $host")
        val query = listOfNotNull("limit" to "$limit", cursor?.takeIf { it.isNotEmpty() }?.let { "cursor" to it })
        val root = json(endpoint(base, "api/v1/reader/feed", *query.toTypedArray()))
        if (root.raw !is JsonObject) return Page(emptyList(), null)
        val notes = root["items"].list.filter { it.raw is JsonObject }.map(SubstackParser::note).filter { it.id.isNotEmpty() && it.body.isNotEmpty() }
        return Page(notes, root["nextCursor"].string)
    }

    // ---- discovery ----

    /** Substack's type-ahead publication search. [Page.next] is the next page while one gives results. */
    suspend fun searchPublications(query: String, page: Int = 0): Page<SubstackPublication> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return Page(emptyList(), null)
        val body = json(service("api/v1/publication/search", "query" to trimmed, "page" to "$page"))
        val items = body["results"].list.mapNotNull(SubstackParser::discovered).filter { it.subdomain.isNotEmpty() && it.baseUrl.isNotEmpty() }
        return Page(items, if (items.isEmpty()) null else "${page + 1}")
    }

    /**
     * Publications for the search field: an address or `@handle` is looked up
     * directly; else a name search, and when it finds nothing on its first page
     * and the query looks like a handle, that handle is tried.
     */
    suspend fun findPublications(input: String, page: Int = 0): Page<SubstackPublication> {
        val query = input.trim()
        if (query.isEmpty()) return Page(emptyList(), null)
        if (directInput(query)) return Page(listOf(resolve(query)), null)
        var failure: Exception? = null
        val found = try {
            searchPublications(query, page)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failure = e
            Page(emptyList(), null)
        }
        if (found.items.isNotEmpty()) return found
        if (page == 0 && handleLike.matches(query)) {
            try {
                return Page(listOf(resolve(query)), null)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (failure != null) throw failure
                if (!e.isNotAPublication) throw e
            }
        }
        if (failure != null) throw failure
        return Page(emptyList(), null)
    }

    /** What the discover sheet asks: a name search, else the query as a handle or address. */
    suspend fun discover(query: String): List<SubstackPublication> {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) return emptyList()
        var searchError: Exception? = null
        val searched = try {
            searchPublications(trimmed).items
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isSubstackRateLimit) throw e
            searchError = e
            emptyList()
        }
        if (searched.isNotEmpty()) return searched
        // A name of several words is no handle; the search already had its chance.
        if (' ' in trimmed && '.' !in trimmed && '/' !in trimmed) {
            if (searchError != null) throw searchError
            return emptyList()
        }
        return try {
            listOf(resolve(trimmed))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isSubstackRateLimit) throw e
            if (searchError != null) throw searchError
            if ("://" in trimmed || '.' in trimmed) throw e
            emptyList()
        }
    }

    /** Substack's leaderboard categories, top level only. */
    suspend fun categories(): List<SubstackCategory> =
        json(service("api/v1/categories")).list
            .filter { it.raw is JsonObject && !it["parent_tag_id"].exists && it["deprecated"].boolean != true }
            .map(SubstackParser::category)
            .filter { it.id > 0 && it.name.isNotEmpty() }

    /** A category's leaderboard; [tier] `all` mixes free and paid. [Page.next] is the next page while one gives results. */
    suspend fun categoryPublications(categoryId: Long, tier: String = "all", page: Int = 0): Page<SubstackPublication> {
        val body = json(service("api/v1/category/public/$categoryId/$tier", "page" to "$page"))
        val items = body["publications"].list.mapNotNull(SubstackParser::discovered).filter { it.subdomain.isNotEmpty() && it.baseUrl.isNotEmpty() }
        return Page(items, if (items.isEmpty()) null else "${page + 1}")
    }

    /** The publications [publication]'s author recommends on its public `/recommendations` page. */
    suspend fun recommendations(publication: SubstackPublication): List<SubstackRecommendation> =
        acrossBases(publication, "Recommendations unavailable") { base ->
            val response = get(endpoint(base, "recommendations"), HTML)
            if (response.code != 200) throw Substack.failure(response.code, "HTTP ${response.code} loading ${base.host}/recommendations")
            SubstackPages.recommendations(response.body)
        }

    /** The author's recommendations, padded with name-search hits; an error only when both fail and nothing is found. */
    suspend fun similar(publication: SubstackPublication): List<SubstackRecommendation> {
        var recommendationError: Exception? = null
        val recommended = try {
            recommendations(publication)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isSubstackRateLimit) throw e
            recommendationError = e
            emptyList()
        }
        var searchError: Exception? = null
        val searched = try {
            searchPublications(publication.name.trim().ifEmpty { publication.subdomain }).items
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            if (e.isSubstackRateLimit) throw e
            searchError = e
            emptyList()
        }
        val merged = SubstackParser.mergeSimilar(publication, recommended, searched)
        if (merged.isEmpty() && recommendationError != null && searchError != null) throw recommendationError
        return merged
    }

    // ---- the fallback chain behind posts() ----

    private suspend fun postsAt(publication: SubstackPublication, limit: Int, offset: Int): List<SubstackPost> {
        val key = "${publication.id}\n${publication.baseUrl}"
        if (offset > 0) synchronized(rssPages) { rssPages[key] }?.let { return it.drop(offset).take(limit) }
        val hinted = SubstackHosts.hinted(publication) ?: unreachable(Substack.notAPublication())
        val requested = SubstackHosts.requested(hinted, publication.subdomain)
        val leftover = SubstackHosts.leftover(hinted, publication.subdomain)
        if (requested.isEmpty() && leftover.isEmpty()) unreachable(Substack.notAPublication())
        var reachable = false
        suspend fun fromJson(hosts: List<HttpUrl>): List<SubstackPost>? {
            for (base in hosts) {
                val posts = orNull { postsFromJson(base, publication, limit, offset) } ?: continue
                reachable = true
                if (posts.isNotEmpty() || offset > 0) return posts.also { forgetRss(key) }
            }
            return null
        }
        suspend fun fromRss(hosts: List<HttpUrl>): List<SubstackPost>? = hosts.firstNotNullOfOrNull { base ->
            rss(base, publication)?.takeIf { rssIsSubstack(base, it) && it.posts.isNotEmpty() }?.posts
        }?.also { rememberRss(key, it) }?.take(limit)

        fromJson(requested)?.let { return it }
        requested.firstNotNullOfOrNull { postsFromBeehiiv(it, publication, limit, offset) }?.let {
            forgetRss(key)
            return it
        }
        if (offset > 0) {
            fromJson(leftover)?.let { return it }
            if (!reachable) unreachable(Substack.failure(503, "Archive page unavailable"))
            return emptyList()
        }
        fromRss(requested)?.let { return it }
        fromJson(leftover)?.let { return it }
        fromRss(leftover)?.let { return it }
        if (!reachable) unreachable(Substack.notAPublication())
        forgetRss(key)
        return emptyList()
    }

    /** A listing from Substack's JSON at [base], checked to be this publication's when [base] is a twin. */
    private suspend fun postsFromJson(base: HttpUrl, publication: SubstackPublication, limit: Int, offset: Int): List<SubstackPost> {
        val rows = listing(base, limit, offset) ?: throw Substack.failure(404, "No JSON listing at ${base.host}")
        checkTwin(base, publication, rows)
        return SubstackParser.posts(rows, publication.baseUrl) { resolvedName(publication, it, base) }
    }

    /** `/api/v1/posts`, else `/api/v1/archive`: the first that lists something, else an empty one that answered. */
    private suspend fun listing(base: HttpUrl, limit: Int, offset: Int): List<J>? {
        var empty: List<J>? = null
        val reads = listOf(
            endpoint(base, "api/v1/posts", "limit" to "$limit", "offset" to "$offset"),
            endpoint(base, "api/v1/archive", "sort" to "new", "limit" to "$limit", "offset" to "$offset"),
        )
        for (url in reads) {
            val rows = orNull { jsonList(url, "Unexpected archive response") } ?: continue
            if (rows.isNotEmpty()) return rows
            empty = rows
        }
        return empty
    }

    private suspend fun postFromJson(base: HttpUrl, publication: SubstackPublication, slug: String): SubstackPost? {
        val body = json(endpoint(base, "api/v1/posts/$slug"))
        if (body.raw !is JsonObject || !servesFollowed(base, publication, body)) return null
        return SubstackParser.post(body, publication.baseUrl, resolvedName(publication, body, base), includeBody = true)
            .takeIf { it.title.isNotEmpty() && it.slug.isNotEmpty() }
    }

    private suspend fun searchOn(base: HttpUrl, publication: SubstackPublication, query: String, limit: Int, offset: Int): List<SubstackPost> {
        val url = endpoint(base, "api/v1/archive", "sort" to "new", "search" to query, "limit" to "$limit", "offset" to "$offset")
        val rows = jsonList(url, "Unexpected archive search response")
        checkTwin(base, publication, rows)
        return SubstackParser.posts(rows, publication.baseUrl) { publication.displayName }
    }

    // ---- Beehiiv, RSS and home pages ----

    private suspend fun publicationOfListing(base: HttpUrl, followed: SubstackPublication? = null): SubstackPublication? = orNull {
        val first = listing(base, 1, 0)?.firstOrNull() ?: return@orNull null
        if (followed != null && !servesFollowed(base, followed, first)) return@orNull null
        SubstackParser.publicationOfPost(first, base)
    }

    private suspend fun publicationOfBeehiiv(base: HttpUrl): SubstackPublication? {
        beehiivListing(base, 1) ?: return null
        return publicationOfHomepage(base)
            ?: SubstackPublication(SubstackHosts.guessedId(base), SubstackHosts.origin(base), SubstackHosts.subdomainOf(base.host))
    }

    private suspend fun publicationOfHomepage(base: HttpUrl): SubstackPublication? = orNull {
        val response = get(base, HTML)
        if (response.code != 200) return@orNull null
        SubstackPages.publicationOfBeehiivHomepage(response.body, base) ?: SubstackPages.publicationOfHomepage(response.body, base)
    }

    private fun publicationOfRss(base: HttpUrl, rss: SubstackRssChannel) = SubstackPublication(
        subdomain = SubstackHosts.guessedId(base),
        baseUrl = SubstackHosts.origin(base),
        name = rss.title?.trim()?.takeIf { it.isNotEmpty() } ?: SubstackHosts.subdomainOf(base.host),
        description = rss.description,
        logoUrl = rss.imageUrl,
    )

    /** Beehiiv's listing read page by page from `/posts?page=N`, [offset] rows skipped, at most [limit]. */
    private suspend fun postsFromBeehiiv(base: HttpUrl, publication: SubstackPublication, limit: Int, offset: Int): List<SubstackPost>? {
        var current = beehiivListing(base, 1) ?: return null
        val out = ArrayList<SubstackPost>()
        var skipped = 0
        var page = 1
        while (true) {
            for (row in current.posts) {
                val post = SubstackParser.beehiivPost(row, publication.baseUrl, publication.displayName)
                if (post.title.isEmpty() || post.slug.isEmpty()) continue
                if (skipped < offset) {
                    skipped++
                    continue
                }
                out += post
                if (out.size >= limit) return out
            }
            if (page >= current.totalPages || current.posts.isEmpty()) break
            current = beehiivListing(base, ++page) ?: break
        }
        return out
    }

    private class BeehiivListing(val posts: List<J>, val totalPages: Int)

    private suspend fun beehiivListing(base: HttpUrl, page: Int): BeehiivListing? = orNull {
        val response = get(endpoint(base, "posts", "page" to "$page"), BEEHIIV_JSON)
        if (response.code != 200 || !response.body.trimStart().startsWith("{")) return@orNull null
        val body = J.parse(response.body)
        if (!SubstackParser.looksLikeBeehiivPosts(body)) return@orNull null
        val pages = SubstackParser.number(body["pagination"]["total_pages"])?.toInt()?.coerceAtLeast(1) ?: 1
        BeehiivListing(body["posts"].list.filter { it.raw is JsonObject }, pages)
    }

    private suspend fun postFromBeehiivPage(base: HttpUrl, publication: SubstackPublication, slug: String): SubstackPost? = orNull {
        val response = get(endpoint(base, "p/$slug"), HTML)
        if (response.code != 200) return@orNull null
        SubstackPages.beehiivPost(response.body, slug, publication.baseUrl, publication.displayName)
    }

    /** The RSS feed at `/feed`, `/rss` or `/feed.xml`, the first that is one. */
    private suspend fun rss(base: HttpUrl, publication: SubstackPublication? = null): SubstackRssChannel? =
        RSS_PATHS.firstNotNullOfOrNull { path ->
            orNull {
                val response = get(endpoint(base, path), RSS)
                if (response.code != 200 || ("<rss" !in response.body && "<feed" !in response.body)) return@orNull null
                SubstackRss.parse(
                    response.body,
                    publication?.baseUrl ?: SubstackHosts.origin(base),
                    publication?.displayName ?: SubstackHosts.subdomainOf(base.host),
                )
            }
        }

    private fun rssIsSubstack(base: HttpUrl, rss: SubstackRssChannel) = SubstackHosts.isPublicationHost(base.host) || rss.looksLikeSubstack

    /** The posts of an RSS feed, kept without bodies (at most 120, for 64 publications) so later pages need no request. */
    private fun rememberRss(key: String, posts: List<SubstackPost>) = synchronized(rssPages) {
        rssPages.remove(key)
        rssPages[key] = posts.take(120).map { it.copy(bodyHtml = null) }
        while (rssPages.size > 64) rssPages.remove(rssPages.keys.first())
    }

    private fun forgetRss(key: String) {
        synchronized(rssPages) { rssPages.remove(key) }
    }

    // ---- resolving what was typed ----

    private suspend fun fromProfile(handle: String?): SubstackPublication? {
        if (handle.isNullOrEmpty()) return null
        val primary = primaryPublication(handle) ?: return null
        SubstackHosts.fetchBases(primary).firstNotNullOfOrNull { base -> orNull { publicationAt(base, null) } }?.let { return it }
        if (primary.subdomain.isEmpty()) return null
        return SubstackHosts.https("${primary.subdomain}.substack.com")?.let { base -> orNull { publicationAt(base, null) } }
    }

    private suspend fun fromLeftoverCustomDomain(input: String): SubstackPublication? {
        val url = (if ("://" in input) input else "https://$input").toHttpUrlOrNull() ?: return null
        if (SubstackHosts.isPublicationHost(url.host) || SubstackHosts.isServiceHost(url.host) || SubstackHosts.isObviousNonSubstack(url.host)) return null
        return SubstackHosts.candidates(url)
            .filter { !SubstackHosts.same(it.host, url.host) }
            .firstNotNullOfOrNull { candidate -> orNull { bound(publicationAt(candidate, null), url) } }
    }

    /**
     * A Substack address that names no publication itself: followed to where its
     * redirects land, and resolved there. XTA meant to do the same, but its HTTP
     * client reported the address asked, never where it landed.
     */
    private suspend fun fromRedirect(input: String): SubstackPublication? {
        val url = (if ("://" in input) input else "https://$input").toHttpUrlOrNull() ?: return null
        if (!SubstackHosts.isServiceHost(url.host)) return null
        return orNull {
            val landed = landing(url) ?: return@orNull null
            if (SubstackHosts.same(landed.host, url.host)) return@orNull null
            val base = SubstackHosts.postRef(landed.toString())?.base ?: SubstackHosts.base(landed.toString()) ?: return@orNull null
            publicationAt(base, null)
        }
    }

    /** Where [url] lands after at most five redirects; null when it goes on redirecting. */
    private suspend fun landing(url: HttpUrl): HttpUrl? {
        var at = url
        repeat(MAX_REDIRECTS + 1) {
            val response = get(at, HTML, followRedirects = false)
            val next = response.header("Location")?.takeIf { response.code in REDIRECTS }?.let(at::resolve) ?: return at
            at = next
        }
        return null
    }

    /** A publication found at another host, given the host that was asked for, unless that was Substack's own. */
    private fun bound(found: SubstackPublication, requested: HttpUrl): SubstackPublication {
        if (SubstackHosts.isPublicationHost(requested.host) || SubstackHosts.isServiceHost(requested.host)) return found
        return found.copy(subdomain = found.subdomain.ifEmpty { SubstackHosts.guessedId(requested) }, baseUrl = "https://${requested.host}")
    }

    // ---- twins and names ----

    /** Whether [post], served by [base], is [publication]'s: only a twin host can serve someone else. */
    private fun servesFollowed(base: HttpUrl, publication: SubstackPublication, post: J): Boolean =
        !SubstackHosts.isLeftoverTwin(base.host, SubstackHosts.hostOf(publication.baseUrl)) || SubstackHosts.twinMatches(publication, post)

    private fun checkTwin(base: HttpUrl, publication: SubstackPublication, rows: List<J>) {
        val first = rows.firstOrNull() ?: return
        if (!servesFollowed(base, publication, first)) throw Substack.failure(404, "${base.host} serves another publication")
    }

    /** The stored name, unless it is a leftover label and the post's byline names the publication. */
    private fun resolvedName(publication: SubstackPublication, post: J, base: HttpUrl): String {
        val stored = publication.name.trim()
        val fromJson = SubstackParser.publicationOfPost(post, base).name.trim()
        if (fromJson.isEmpty()) return stored
        val generic = SubstackHosts.nameLooksGeneric(stored) || stored.lowercase() == publication.subdomain.lowercase() ||
            stored.lowercase() == SubstackHosts.subdomainOf(base.host)
        return if (generic) fromJson else stored.ifEmpty { fromJson }
    }

    // ---- requests ----

    /** [read] on each of [publication]'s hosts in turn until one answers; the last failure when none does. */
    private suspend fun <T> acrossBases(publication: SubstackPublication, unavailable: String, read: suspend (HttpUrl) -> T): T {
        var last: Exception? = null
        for (base in SubstackHosts.fetchBases(publication)) {
            try {
                return read(base)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                if (e.isSubstackRateLimit) throw e
                last = e
            }
        }
        throw last ?: Substack.failure(404, unavailable)
    }

    private suspend fun json(url: HttpUrl): J {
        val response = get(url, JSON)
        if (response.code != 200) throw Substack.failure(response.code, "HTTP ${response.code} loading $url")
        return J.parse(response.body)
    }

    private suspend fun jsonList(url: HttpUrl, unexpected: String): List<J> {
        val body = json(url)
        if (body.raw !is JsonArray) throw Substack.failure(200, "$unexpected from ${url.host}")
        return body.list.filter { it.raw is JsonObject }
    }

    private suspend fun get(url: HttpUrl, accept: String, followRedirects: Boolean = true): XResponse {
        val reach = coroutineContext[Reach]
        try {
            return transport.get(url, mapOf("Accept" to accept, "User-Agent" to USER_AGENT), followRedirects).also { reach?.answered = true }
        } catch (e: IOException) {
            reach?.offline = e
            throw e
        } catch (e: NetworkRequestException) {
            reach?.answered = true
            throw e
        }
    }

    /** One read's record of whether any host answered at all, shared by the reads it is made of. */
    private class Reach : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<Reach>

        @Volatile var answered = false

        @Volatile var offline: IOException? = null
    }

    private suspend fun <T> reaching(read: suspend () -> T): T =
        if (coroutineContext[Reach] != null) read() else withContext(Reach()) { read() }

    /** [verdict], unless no host answered at all: then the connection failure it was. */
    private suspend fun unreachable(verdict: NetworkRequestException): Nothing {
        val reach = coroutineContext[Reach]
        val offline = reach?.offline
        if (reach != null && offline != null && !reach.answered) throw offline
        throw verdict
    }

    /** [read], or null when it fails for any reason but a rate limit, which ends every fallback chain. */
    private inline fun <T> orNull(read: () -> T?): T? = try {
        read()
    } catch (e: CancellationException) {
        throw e
    } catch (e: Exception) {
        if (e.isSubstackRateLimit) throw e
        null
    }

    private val Exception.isNotAPublication: Boolean get() = this is NetworkRequestException && code == 404

    companion object {
        const val USER_AGENT = "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val JSON = "application/json"
        private const val HTML = "text/html,application/xhtml+xml"
        private const val RSS = "application/rss+xml, application/xml, text/xml, */*"
        private const val BEEHIIV_JSON = "application/json, text/javascript, */*;q=0.1"
        private const val MAX_REDIRECTS = 5
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private val RSS_PATHS = listOf("feed", "rss", "feed.xml")
        private val handleLike = Regex("^@?[A-Za-z0-9_-]+$")
        private val dottedHost = Regex("^[A-Za-z0-9-]+(?:\\.[A-Za-z0-9-]+)+(?:/.*)?$")

        /** An address or `@handle` rather than a name to search for. */
        internal fun directInput(query: String): Boolean =
            SubstackText.spaces.find(query) == null && ("://" in query || query.startsWith("@") || dottedHost.matches(query))

        /** [path] (with `/` between segments) at [base]'s host, with [query] in order. */
        internal fun endpoint(base: HttpUrl, path: String, vararg query: Pair<String, String>): HttpUrl =
            base.newBuilder().encodedPath("/").query(null).fragment(null).apply {
                if (path.isNotEmpty()) addPathSegments(path)
                query.forEach { (name, value) -> addQueryParameter(name, value) }
            }.build()

        private fun service(path: String, vararg query: Pair<String, String>): HttpUrl =
            endpoint(SubstackHosts.https("substack.com")!!, path, *query)
    }
}
