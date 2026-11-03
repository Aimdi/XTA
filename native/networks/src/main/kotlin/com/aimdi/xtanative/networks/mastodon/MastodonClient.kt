package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import java.io.IOException
import java.net.SocketTimeoutException
import java.util.concurrent.ConcurrentHashMap
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** The instance list was empty, or an address in it was no instance's: nothing could be asked. */
class MastodonNotConfiguredException(message: String) : IllegalArgumentException(message)

/**
 * Why a Mastodon read failed, in the terms a screen explains it, from the
 * least to the most telling: when every instance fails, the most telling
 * failure is the one reported, since a throttle or a refusal explains more
 * than the 404 the last instance ended on.
 */
enum class MastodonFailure {
    NOT_CONFIGURED,
    NOT_FOUND,
    NETWORK,
    BAD_RESPONSE,
    UNAUTHORIZED,
    RATE_LIMITED,
    ;

    companion object {
        /** What [error] stands for; null when it is no failed read but a bug or a cancellation. */
        fun of(error: Throwable): MastodonFailure? = when (error) {
            is MastodonNotConfiguredException -> NOT_CONFIGURED
            is NetworkRequestException -> if (error.network == MastodonClient.NETWORK) ofCode(error.code) else null
            is IOException -> NETWORK
            else -> null
        }

        private fun ofCode(code: Int): MastodonFailure = when (code) {
            404 -> NOT_FOUND
            401, 403 -> UNAUTHORIZED
            429 -> RATE_LIMITED
            else -> BAD_RESPONSE
        }
    }
}

/**
 * Reads public Mastodon and Fediverse data without an account, as XTA's
 * MastodonClient does: guest REST reads on any Mastodon-compatible instance,
 * Misskey's notes API where the public timelines are missing, and walks over
 * several instances for what one of them may not answer. Only reads: nothing
 * is posted, favourited, boosted, bookmarked, followed or voted on.
 *
 * Within a walk the first instance gets [timeoutMillis] to answer and each
 * one after it [fallbackTimeoutMillis], so a hanging host costs one short
 * wait. A non-200 answer is a [NetworkRequestException]; [MastodonFailure.of]
 * says what it means.
 */
class MastodonClient(
    private val http: XHttp,
    private val timeoutMillis: Long = 8_000,
    private val fallbackTimeoutMillis: Long = 4_000,
) {
    private val instanceByAcct = ConcurrentHashMap<String, String>()
    private val idByAcct = ConcurrentHashMap<String, String>()
    private val calls = CoroutineScope(SupervisorJob() + Dispatchers.Unconfined)

    // ---- walks over instances ----

    /**
     * [read] on each of [instances] in turn until one answers. A miss on one
     * says nothing about the next: a 404 is also what a Misskey-family origin
     * answers on the Mastodon API, and an instance that closed its public
     * timeline leaves every other one worth asking. When all fail, the most
     * telling failure ([MastodonFailure]) is thrown.
     */
    suspend fun <T> firstInstanceThat(instances: List<String>, read: suspend (instance: String) -> T): T {
        if (instances.isEmpty()) throw MastodonNotConfiguredException("no instance to ask")
        var worst: Exception? = null
        for ((attempt, instance) in instances.withIndex()) {
            try {
                return withContext(WalkTimeout(if (attempt == 0) timeoutMillis else fallbackTimeoutMillis)) { read(instance) }
            } catch (failure: Exception) {
                if (MastodonFailure.of(failure) == null) throw failure
                worst = worst?.let { moreTelling(it, failure) } ?: failure
            }
        }
        throw checkNotNull(worst)
    }

    /** [lookup] on the first of [instances] that knows [acct], asking the one that answered last time first. */
    suspend fun lookupAnywhere(instances: List<String>, acct: String): MastodonProfile =
        firstInstanceThat(preferAnswered(acct, instances)) { instance -> lookup(instance, acct).also { remember(acct, instance) } }

    /** [accountPosts] the same way: the lookup and the statuses stay on one instance, an id being meaningless on the next. */
    suspend fun accountPostsAnywhere(instances: List<String>, acct: String, limit: Int = 20): List<MastodonPost> =
        firstInstanceThat(preferAnswered(acct, instances)) { instance -> accountPosts(instance, acct, limit).also { remember(acct, instance) } }

    /**
     * A profile and the first page of its posts, pinned ones first, all from
     * one instance. Pinned posts are a nicety: when they fail the profile still
     * shows, unless the instance is rate limiting, which the walk must hear.
     */
    suspend fun profileAnywhere(instances: List<String>, acct: String): MastodonProfilePage =
        firstInstanceThat(preferAnswered(acct, instances)) { instance ->
            val profile = lookup(instance, acct)
            idByAcct[idKey(instance, acct)] = profile.id
            remember(acct, instance)
            val timeline = statuses(instance, profile.id)
            val pinned = optional { statuses(instance, profile.id, pinned = true).items }.orEmpty()
            MastodonProfilePage(profile, instance, MastodonFeeds.withPinned(pinned, timeline.items), pinned.map { it.id }.toSet(), timeline.next)
        }

    /** [thread] on the first of [instances] that can find [seed]. */
    suspend fun threadAnywhere(instances: List<String>, seed: MastodonPost): MastodonThread = firstInstanceThat(instances) { thread(it, seed) }

    suspend fun trendingTagsAnywhere(instances: List<String>, limit: Int = 20): List<MastodonTag> =
        firstInstanceThat(instances) { trendingTags(it, limit) }

    suspend fun searchAnywhere(instances: List<String>, q: String, limit: Int = 20): MastodonSearchPage =
        firstInstanceThat(instances) { search(it, q, limit) }

    suspend fun searchAccountsAnywhere(instances: List<String>, q: String, limit: Int = 20): List<MastodonProfile> =
        searchAnywhere(instances, q, limit).accounts

    /** A hashtag's first page from the first instance that answers, which its later pages must be read from. */
    suspend fun tagTimelineAnywhere(instances: List<String>, tag: String, limit: Int = 30): FromInstance<Page<MastodonPost>> =
        firstInstanceThat(instances) { FromInstance(it, tagTimeline(it, tag, limit)) }

    /** The local or federated timeline's first page from the first instance that answers, which its later pages must be read from. */
    suspend fun publicTimelineAnywhere(instances: List<String>, local: Boolean = false, limit: Int = 30): FromInstance<Page<MastodonPost>> =
        firstInstanceThat(instances) { FromInstance(it, publicTimeline(it, local, limit)) }

    suspend fun trendingStatusesAnywhere(instances: List<String>, limit: Int = 20): List<MastodonPost> =
        firstInstanceThat(instances) { trendingStatuses(it, limit) }

    /**
     * Trending tags and posts for an Explore tab. The tags are a nicety: when
     * they fail the posts still show, and a failure of the posts is reported
     * at once, without waiting for the tags.
     */
    suspend fun explore(instances: List<String>): MastodonExplorePage = coroutineScope {
        val tags = async {
            try {
                trendingTagsAnywhere(instances)
            } catch (failure: Exception) {
                if (failure is CancellationException) throw failure
                emptyList()
            }
        }
        val posts = trendingStatusesAnywhere(instances)
        MastodonExplorePage(tags.await(), MastodonFeeds.unique(emptyList(), posts))
    }

    /**
     * What the search box asks for, routed as XTA routes it: nothing typed
     * reads the trending tags; a status address reads that status from its
     * origin; a `user@domain` is looked up where it lives; anything else is a
     * guest search over [instances].
     */
    suspend fun find(query: String, instances: List<String>): MastodonSearchPage {
        val q = query.trim()
        if (q.isEmpty()) return MastodonSearchPage(tags = trendingTagsAnywhere(instances))
        MastodonUrls.searchTarget(q)?.let { return MastodonSearchPage(posts = listOf(status(it.instance, it.id))) }
        val acct = MastodonAcct.normalise(q)
        if (acct != null && '@' in q) {
            return MastodonSearchPage(accounts = listOf(lookupAnywhere(MastodonInstances.candidates(acct, instances), acct)))
        }
        return searchAnywhere(instances, q)
    }

    // ---- one instance ----

    /** Whether [instance] answers the public instance endpoint, `/api/v2/instance` or else `/api/v1/instance`. */
    suspend fun verify(instance: String) {
        try {
            get(url(instance, "api/v2/instance"))
        } catch (failure: NetworkRequestException) {
            if (MastodonFailure.of(failure) != MastodonFailure.NOT_FOUND) throw failure
            get(url(instance, "api/v1/instance"))
        }
    }

    /** [acct] (a local name, `user@domain` or a profile address) as [instance] resolves it. */
    suspend fun lookup(instance: String, acct: String): MastodonProfile {
        val address = MastodonAcct.normalise(acct) ?: throw NetworkRequestException(NETWORK, 404, "invalid acct: $acct")
        val answer = get(url(instance, "api/v1/accounts/lookup", "acct" to address))
        val profile = MastodonParser.profile(answer.json, homeDomain(instance))
        if (profile.id.isEmpty() || profile.acct.isEmpty()) throw NetworkRequestException(NETWORK, answer.code, "empty profile for $address")
        return profile
    }

    /** The account [id] is on [instance]. */
    suspend fun account(instance: String, id: String): MastodonProfile =
        MastodonParser.profile(get(url(instance, "api/v1/accounts/$id")).json, homeDomain(instance))

    /** A page of public statuses by the account [accountId] is on [instance], newest first. */
    suspend fun statuses(
        instance: String,
        accountId: String,
        limit: Int = 20,
        excludeReplies: Boolean = true,
        onlyMedia: Boolean = false,
        pinned: Boolean = false,
        maxId: String? = null,
    ): Page<MastodonPost> {
        val answer = get(
            url(
                instance,
                "api/v1/accounts/$accountId/statuses",
                "limit" to "$limit",
                "exclude_replies" to "$excludeReplies",
                "only_media" to "true".takeIf { onlyMedia },
                "pinned" to "true".takeIf { pinned },
                "max_id" to maxId,
            ),
        )
        return timeline(answer, MastodonParser.statuses(answer.json, homeDomain(instance)), limit, maxId)
    }

    /** A page of one of a profile's tabs: Posts leave replies out, Replies keep them, Media has only posts with media. */
    suspend fun profileTab(instance: String, accountId: String, tab: MastodonProfileTab, maxId: String? = null): Page<MastodonPost> =
        statuses(instance, accountId, excludeReplies = tab == MastodonProfileTab.POSTS, onlyMedia = tab == MastodonProfileTab.MEDIA, maxId = maxId)

    /** The first page of [acct]'s statuses on [instance], its id there looked up once: what the Following feed reads per account. */
    suspend fun accountPosts(instance: String, acct: String, limit: Int = 20): List<MastodonPost> {
        val key = idKey(instance, acct)
        val id = idByAcct[key] ?: lookup(instance, acct).id.also { idByAcct[key] = it }
        return statuses(instance, id, limit).items
    }

    /** The status [id] is on [instance]. */
    suspend fun status(instance: String, id: String): MastodonPost {
        val answer = get(url(instance, "api/v1/statuses/$id"))
        return MastodonParser.status(answer.json, homeDomain(instance)) ?: throw NetworkRequestException(NETWORK, answer.code, "empty status $id")
    }

    /** What the status [id] answers and what answers it, on [instance]. */
    suspend fun context(instance: String, id: String): MastodonContext =
        MastodonParser.context(get(url(instance, "api/v1/statuses/$id/context")).json, homeDomain(instance))

    /**
     * [seed] and its public conversation on [instance]. Guest search is
     * refused by most instances, so the status is found first by the id in
     * its address (the origin's own) or the card's id, then by a search where
     * one is allowed, then among its author's recent statuses there.
     */
    suspend fun thread(instance: String, seed: MastodonPost): MastodonThread {
        val status = locate(instance, seed)
        val context = context(instance, status.id)
        return MastodonThread(status, context.ancestors, context.descendants, homeDomain(instance))
    }

    suspend fun trendingTags(instance: String, limit: Int = 20): List<MastodonTag> =
        MastodonParser.tags(get(url(instance, "api/v1/trends/tags", "limit" to "$limit")).json)

    /** A guest search for accounts, statuses and hashtags in one call; nothing typed asks nothing. */
    suspend fun search(instance: String, q: String, limit: Int = 20): MastodonSearchPage {
        val query = q.trim()
        if (query.isEmpty()) return MastodonSearchPage()
        return MastodonParser.search(get(url(instance, "api/v2/search", "q" to query, "limit" to "$limit")).json, homeDomain(instance))
    }

    suspend fun searchAccounts(instance: String, q: String, limit: Int = 20): List<MastodonProfile> = search(instance, q, limit).accounts

    /** A page of a hashtag's public timeline; a leading `#` is dropped, and an empty tag reads nothing. */
    suspend fun tagTimeline(instance: String, tag: String, limit: Int = 30, maxId: String? = null): Page<MastodonPost> {
        val name = tag.replaceFirst(Regex("^#"), "").trim()
        if (name.isEmpty()) return Page(emptyList(), null)
        val answer = get(url(instance, "api/v1/timelines/tag/$name", "limit" to "$limit", "max_id" to maxId))
        return timeline(answer, MastodonParser.statuses(answer.json, homeDomain(instance)), limit, maxId)
    }

    /**
     * A page of the local or federated public timeline. An instance that
     * will not give it (a Misskey-family one, or one that closed it) is read
     * through Misskey's notes API instead; only rate limiting is reported.
     */
    suspend fun publicTimeline(instance: String, local: Boolean = false, limit: Int = 30, maxId: String? = null): Page<MastodonPost> {
        val answer = optional {
            get(url(instance, "api/v1/timelines/public", "limit" to "$limit", "local" to "true".takeIf { local }, "max_id" to maxId))
        } ?: return misskeyPublic(instance, local, limit, maxId)
        return timeline(answer, MastodonParser.statuses(answer.json, homeDomain(instance)), limit, maxId)
    }

    /** Trending statuses, or Misskey's featured notes where the Mastodon API will not answer. */
    suspend fun trendingStatuses(instance: String, limit: Int = 20): List<MastodonPost> {
        val answer = optional { get(url(instance, "api/v1/trends/statuses", "limit" to "$limit")) }
            ?: return misskeyPublic(instance, local = false, limit = limit, untilId = null).items
        return MastodonParser.statuses(answer.json, homeDomain(instance))
    }

    // ---- who boosted, who quoted ----

    /** Who boosted [seed], a page at a time from the first of [instances] that finds it; [cursor] is a [Page.next]. */
    suspend fun rebloggedBy(instances: List<String>, seed: MastodonPost, cursor: String? = null): Page<MastodonProfile> {
        val page = activityPage(instances, seed, "reblogged_by", cursor)
        return Page(page.answer.json.list.map { MastodonParser.profile(it, page.answer.url.host) }.filter { it.acct.isNotEmpty() }, page.next)
    }

    /** The statuses quoting [seed], the same way. */
    suspend fun quotes(instances: List<String>, seed: MastodonPost, cursor: String? = null): Page<MastodonPost> {
        val page = activityPage(instances, seed, "quotes", cursor)
        return Page(MastodonParser.statuses(page.answer.json, page.answer.url.host), page.next)
    }

    private class ActivityPage(val answer: Answer, val next: String?)

    private suspend fun activityPage(instances: List<String>, seed: MastodonPost, kind: String, cursor: String?): ActivityPage {
        if (cursor != null) return readActivity(allowedActivityPage(instances, kind, cursor))
        return firstInstanceThat(instances) { instance ->
            val status = locate(instance, seed)
            readActivity(url(instance, "api/v1/statuses/${status.id}/$kind", "limit" to "40"))
        }
    }

    /** [cursor] when it is a page of this very list on one of [instances]; anything else is refused unread. */
    private fun allowedActivityPage(instances: List<String>, kind: String, cursor: String): HttpUrl {
        val origins = instances.map(::root)
        val path = Regex("^/api/v1/statuses/[^/]+/$kind$")
        return cursor.toHttpUrlOrNull()?.takeIf { url ->
            origins.any { it.scheme == url.scheme && it.host == url.host && it.port == url.port } && path.matches(url.encodedPath)
        } ?: throw NetworkRequestException(NETWORK, 400, "invalid activity page: $cursor")
    }

    private suspend fun readActivity(url: HttpUrl): ActivityPage {
        val answer = get(url)
        if (answer.json.raw !is JsonArray) throw NetworkRequestException(NETWORK, answer.code, "$url: no list")
        return ActivityPage(answer, nextActivityPage(answer.header("link"), url))
    }

    // ---- finding a status on an instance ----

    private suspend fun locate(instance: String, seed: MastodonPost): MastodonPost =
        byKnownIds(instance, seed) ?: bySearch(instance, seed.url) ?: inAuthorsStatuses(instance, seed)
            ?: throw NetworkRequestException(NETWORK, 404, "status not on $instance: ${seed.url}")

    /** `/statuses/:id` with the id in the address, then the card's own; on a proxy a coincidental hit must still be this post. */
    private suspend fun byKnownIds(instance: String, seed: MastodonPost): MastodonPost? =
        listOfNotNull(MastodonUrls.statusId(seed.url), seed.id.trim().ifEmpty { null }).distinct().firstNotNullOfOrNull { id ->
            optional { status(instance, id) }?.takeIf { MastodonUrls.sameStatus(it.url, seed.url) }
        }

    /** A search resolving the address, where the instance still allows that without an account. */
    private suspend fun bySearch(instance: String, address: String): MastodonPost? {
        val q = address.trim().ifEmpty { return null }
        val answer = optional { get(url(instance, "api/v2/search", "q" to q, "resolve" to "true", "type" to "statuses", "limit" to "1")) }
        return answer?.let { MastodonParser.statuses(it.json["statuses"], homeDomain(instance)).firstOrNull() }
    }

    /** The post among its author's recent statuses there, replies included: up to three pages of forty. */
    private suspend fun inAuthorsStatuses(instance: String, seed: MastodonPost): MastodonPost? {
        val profile = lookup(instance, seed.acct)
        var maxId: String? = null
        repeat(3) {
            val path = "api/v1/accounts/${profile.id}/statuses"
            val answer = get(url(instance, path, "limit" to "40", "exclude_replies" to "false", "max_id" to maxId))
            val posts = MastodonParser.statuses(answer.json, homeDomain(instance))
            if (posts.isEmpty()) return null
            posts.firstOrNull { MastodonUrls.sameStatus(it.url, seed.url) }?.let { return it }
            maxId = posts.last().pagingId
        }
        return null
    }

    // ---- Misskey ----

    /** Misskey's local timeline for [local] while it has notes, else its featured notes, which do not page. */
    private suspend fun misskeyPublic(instance: String, local: Boolean, limit: Int, untilId: String?): Page<MastodonPost> {
        if (local) {
            val notes = optional { misskeyNotes(instance, "api/notes/local-timeline", limit, untilId) }
            if (notes != null && notes.items.isNotEmpty()) return notes
        }
        if (untilId != null) return Page(emptyList(), null)
        return misskeyNotes(instance, "api/notes/featured", limit, null)
    }

    private suspend fun misskeyNotes(instance: String, path: String, limit: Int, untilId: String?): Page<MastodonPost> {
        val body = buildJsonObject {
            put("limit", limit)
            untilId?.let { put("untilId", it) }
        }
        val answer = post(url(instance, path), body.toString())
        return timeline(answer, MastodonParser.misskeyNotes(answer.json, instance), limit, untilId)
    }

    // ---- requests ----

    private class Answer(val url: HttpUrl, val json: J, private val response: XResponse) {
        val code: Int get() = response.code

        fun header(name: String): String? = response.header(name)
    }

    private suspend fun get(url: HttpUrl): Answer = answer(url, send(url) { http.get(url, headers, followRedirects = true) })

    private suspend fun post(url: HttpUrl, body: String): Answer = answer(url, send(url) { http.post(url, headers, body, "application/json") })

    /** A 200 holding JSON, strictly read as XTA's `jsonDecode` reads it; any other answer is a [NetworkRequestException] with its code. */
    private fun answer(url: HttpUrl, response: XResponse): Answer {
        if (response.code != 200) throw NetworkRequestException(NETWORK, response.code, "$url: ${response.code}")
        val json = runCatching { Json.parseToJsonElement(response.body) }.getOrNull()
            ?: throw NetworkRequestException(NETWORK, response.code, "$url: no JSON")
        return Answer(url, J(json), response)
    }

    /**
     * [request] within the walk's time limit. Past it the read fails as a
     * [SocketTimeoutException] and the request is left behind, as Dart's
     * `timeout` leaves it: OkHttp's blocking call cannot be cut short, and the
     * walk must not wait for it.
     */
    private suspend fun send(url: HttpUrl, request: suspend () -> XResponse): XResponse {
        val millis = currentCoroutineContext()[WalkTimeout]?.millis ?: timeoutMillis
        val call = calls.async { request() }
        try {
            return withTimeoutOrNull(millis) { call.await() } ?: throw SocketTimeoutException("$url: no answer in $millis ms")
        } finally {
            call.cancel()
        }
    }

    /**
     * The page a timeline answer makes. The next page starts below the last
     * entry the instance sent (a boost's own id) while pages come full and the
     * cursor moves. Entries are counted as sent, not as kept: XTA counted the
     * posts it kept, so one status it could not show ended the timeline.
     */
    private fun timeline(answer: Answer, posts: List<MastodonPost>, limit: Int, asked: String?): Page<MastodonPost> {
        val entries = answer.json.list
        val last = entries.mapNotNull(MastodonParser::entryId).lastOrNull()
        return Page(posts, last?.takeIf { entries.size >= limit && it != asked })
    }

    /** [read], or null when it failed in a way the caller can do without: anything but rate limiting, which a walk must hear. */
    private inline fun <T> optional(read: () -> T): T? = try {
        read()
    } catch (failure: Exception) {
        when (MastodonFailure.of(failure)) {
            null, MastodonFailure.RATE_LIMITED -> throw failure
            else -> null
        }
    }

    private fun moreTelling(a: Exception, b: Exception): Exception {
        fun rank(failure: Exception) = MastodonFailure.of(failure)?.ordinal ?: -1
        return if (rank(b) > rank(a)) b else a
    }

    private fun root(instance: String): HttpUrl =
        MastodonInstances.normalise(instance)?.toHttpUrlOrNull() ?: throw MastodonNotConfiguredException("bad instance: $instance")

    private fun url(instance: String, path: String, vararg query: Pair<String, String?>): HttpUrl = root(instance).newBuilder()
        .addPathSegments(path)
        .apply { query.forEach { (name, value) -> if (value != null) addQueryParameter(name, value) } }
        .build()

    private fun homeDomain(instance: String): String? = MastodonInstances.domain(MastodonInstances.normalise(instance) ?: instance)

    private fun preferAnswered(acct: String, instances: List<String>): List<String> {
        val known = instanceByAcct[acctKey(acct)]?.takeIf { it in instances } ?: return instances
        return listOf(known) + instances.filter { it != known }
    }

    private fun remember(acct: String, instance: String) {
        instanceByAcct[acctKey(acct)] = instance
    }

    private fun acctKey(acct: String): String = (MastodonAcct.normalise(acct) ?: acct.trim()).lowercase()

    private fun idKey(instance: String, acct: String): String = "${MastodonInstances.normalise(instance) ?: instance}\u0000${acctKey(acct)}"

    /** The time limit of the walk a read is part of, kept with the read rather than on the client shared by all of them. */
    private class WalkTimeout(val millis: Long) : AbstractCoroutineContextElement(Key) {
        companion object Key : CoroutineContext.Key<WalkTimeout>
    }

    companion object {
        /** The name its failures carry ([NetworkRequestException.network]). */
        const val NETWORK = "mastodon"

        private val headers = mapOf("User-Agent" to "XTA Mastodon plugin", "Accept" to "application/json")
        private val nextLink = Regex("<([^>]+)>;\\s*rel=\"?next\"?")

        /**
         * The `rel="next"` page in a `Link` header, followed only to the next
         * page of [current]'s own list, on its own instance.
         */
        internal fun nextActivityPage(header: String?, current: HttpUrl): String? = header.orEmpty().split(',').firstNotNullOfOrNull { part ->
            nextLink.find(part.trim())?.groupValues?.get(1)?.let(current::resolve)?.takeIf { next ->
                next.scheme == current.scheme && next.host == current.host && next.port == current.port &&
                    next.encodedPath == current.encodedPath && next != current
            }?.toString()
        }
    }
}
