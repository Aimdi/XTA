package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsFailure.NOT_CONFIGURED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.NO_SUCH_FEED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.PRIVATE_ACCOUNT
import com.aimdi.xtanative.networks.threads.ThreadsFailure.SESSION_SUSPENDED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.SIGN_IN_REQUIRED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.THROTTLED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.UNAUTHORIZED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.UNREACHABLE
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.time.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.coroutineContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Reads Threads from Meta itself, as XTA's threads_direct_client.dart does:
 * public pages and guest GraphQL with no account, and, when the reader pasted
 * a session, the cookie API on threads.com and the Bearer API on
 * i.instagram.com. Read-only: nothing is posted, liked, reposted or followed.
 *
 * Requests leave one at a time, the gap of [pacing] apart, and a session that
 * Meta throttled or parked is not used again until its cooldown, kept in
 * [memory], has passed; public reads spend no session and ignore it. A
 * guest list goes on where its page names a cursor and the query that read
 * it ([ThreadsNextPages]): its [Page.next] is that cursor, and the `more…`
 * reads ask guest GraphQL for the page after it as threads.com's own client
 * does. Elsewhere [Page.next] is null.
 */
class ThreadsDirectClient(
    private val http: XHttp,
    private val session: () -> ThreadsSession = { ThreadsSession() },
    private val memory: ThreadsMemory = ThreadsMemory.InMemory(),
    private val repairs: () -> ThreadsRepairs = { ThreadsRepairs() },
    private val pacing: ThreadsPacing = ThreadsPacing(),
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val departures = Mutex()

    /** How many reads the reader is waiting on (not [ThreadsBackground]) are waiting for their turn to leave. */
    private val waitingInFront = MutableStateFlow(0)

    /** The line [ThreadsBackground] reads take, one at a time, before they queue for a departure. */
    private val backgroundLane = Mutex()
    private var lastDeparture: Long? = null
    private val keeping = Any()
    private val profilePages = SingleFlight<String>()
    private val conversationReads = SingleFlight<ThreadsPostPage>()
    private val conversations = LinkedHashMap<String, Pair<Long, ThreadsPostPage>>()
    private val profileCursors = LinkedHashMap<String, String?>()

    /** The session as the reader pasted it, read afresh on every request. */
    val currentSession: ThreadsSession get() = session()

    /** Meta asked the session to stop (a throttle, `login_required`) and its cooldown has not ended. */
    val isSessionParked: Boolean get() = memory.kept.cooldownUntil?.let { clock() < it } ?: false

    /** Confirms the pasted session: the username the cookies sign in as, else a one-post read with the Bearer. */
    suspend fun verify(): String {
        val current = session()
        if (!current.hasDirectAuth) throw ThreadsException(NOT_CONFIGURED, "no direct session")
        if (current.hasCookies) return currentUser().username
        return followingTimeline(limit = 1).items.firstOrNull()?.handle ?: "ok"
    }

    /** The account the pasted cookies sign in as. */
    suspend fun currentUser(): ThreadsProfile {
        val current = requireCookies()
        val url = ThreadsLinks.web("/api/v1/accounts/current_user/", listOf("edit" to "true"))
        return ThreadsParser.profile(sessionRead(url, cookieHeaders(current))["user"])
            ?: throw ThreadsException(UNREACHABLE, "current_user missing user")
    }

    /**
     * The numeric id behind [handle], remembered once known: finding it costs a
     * people search, and repeating the same search on every refresh both doubles
     * a read and looks exactly like a script.
     */
    suspend fun resolveUserId(handle: String): String {
        val key = handle.lowercase()
        memory.kept.userIds[key]?.ifEmpty { null }?.let { return it }
        requireCookies()
        return searchUserId(key).also { rememberUserId(key, it) }
    }

    /** People search with the cookie session. A guest has none: open an exact `@handle` instead. */
    suspend fun searchUsers(query: String, count: Int = 10): Page<ThreadsProfile> {
        val current = requireCookies()
        val words = query.trim().removePrefix("@")
        if (words.isEmpty()) return Page(emptyList(), null)
        val url = ThreadsLinks.web("/api/v1/users/search/", listOf("q" to words, "count" to "$count"))
        val users = sessionRead(url, cookieHeaders(current))["users"].list.mapNotNull(ThreadsParser::profile)
        users.filter { it.pk.isNotEmpty() }.forEach { rememberUserId(it.username.lowercase(), it.pk) }
        return Page(users, null)
    }

    /**
     * One page of [list] ([ThreadsPeople]) for [target], a user's numeric id or
     * a post's media id, after [cursor], with Meta's own search for [query]
     * where the list has one. A session read: the cookies on threads.com when
     * the reader gave them, else the Bearer on the app API; with neither,
     * [NOT_CONFIGURED]. A read only: no one is followed or liked.
     */
    suspend fun people(list: ThreadsPeopleList, target: String, cursor: String? = null, query: String? = null): Page<ThreadsProfile> {
        val id = target.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: throw ThreadsException(NO_SUCH_FEED, "no id for $list")
        val current = session()
        val path = ThreadsPeople.path(list, id)
        val params = ThreadsPeople.params(list, cursor, query)
        val token = current.bearerToken
        val body = when {
            current.hasCookies -> sessionRead(ThreadsLinks.web(path, params), cookieHeaders(current))
            token != null -> deviceId().let { device -> sessionRead(ThreadsLinks.address(INSTAGRAM, path, params), bearerHeaders(token, device)) }
            else -> throw ThreadsException(NOT_CONFIGURED, "no session for $list")
        }
        return ThreadsPeople.page(body)
    }

    /**
     * [handle]'s numeric id for its lists: remembered, else from its public
     * page, else (with cookies) from people search.
     */
    suspend fun userIdFor(handle: String): String {
        val key = handle.trim().removePrefix("@").lowercase()
        memory.kept.userIds[key]?.ifEmpty { null }?.let { return it }
        val fromPage = try {
            guestProfile(key).pk.ifEmpty { null }
        } catch (e: Exception) {
            if (!e.isReadFailure() || !session().hasCookies) throw e
            null
        }
        return fromPage ?: resolveUserId(key)
    }

    /** [handle]'s profile card: its public page, else the cookie API when the reader opted in to session reads. */
    suspend fun profile(handle: String): ThreadsProfile {
        try {
            return guestProfile(handle)
        } catch (e: Exception) {
            val current = session()
            if (!e.isReadFailure() || !current.useSessionApis || !current.hasCookies) throw e
        }
        return sessionProfile(handle)
    }

    /** [handle]'s profile card from the cookie API. */
    suspend fun sessionProfile(handle: String): ThreadsProfile {
        val current = requireCookies()
        val id = resolveUserId(handle)
        val url = ThreadsLinks.web("/api/v1/users/$id/info/")
        return ThreadsParser.profile(sessionRead(url, cookieHeaders(current))["user"])
            ?: throw ThreadsException(NO_SUCH_FEED, "profile missing: @$handle")
    }

    /** [handle]'s profile card from its public `threads.com/@handle` page, no login. */
    suspend fun guestProfile(handle: String): ThreadsProfile {
        val key = handle.trim().lowercase()
        val page = profilePage(key)
        val profile = ThreadsProfileOwner.enrich(ThreadsPages.guestProfile(page, key), page, key)
            ?: throw ThreadsException(NO_SUCH_FEED, "guest profile missing: @$key")
        if (profile.pk.isNotEmpty()) rememberUserId(key, profile.pk)
        return profile
    }

    /**
     * [handle]'s threads. Guest GraphQL by default; the cookie API only when the
     * reader opted in, and even then the guest read stands in when it fails or
     * comes back empty, so a dead session does not empty the account.
     */
    suspend fun userThreads(handle: String, count: Int = POSTS_PER_ACCOUNT): Page<ThreadsPost> {
        val current = session()
        if (current.useSessionApis && current.hasCookies) {
            val posts = try {
                val id = resolveUserId(handle)
                val url = ThreadsLinks.web("/api/v1/text_feed/$id/profile/", listOf("count" to "$count"))
                ThreadsParser.feed(sessionRead(url, cookieHeaders(current)))
            } catch (e: Exception) {
                if (!e.isReadFailure()) throw e
                emptyList()
            }
            if (posts.isNotEmpty()) return Page(posts, null)
        }
        return guestAccount(handle)
    }

    /** The first page of the Bearer session's For you timeline, at most [limit] threads. */
    suspend fun followingTimeline(limit: Int = 40): Page<ThreadsPost> = Page(forYouTimeline().items.take(limit), null)

    /**
     * One page of the Bearer session's For you timeline, as the Threads app
     * reads its home: the first page, or with [cursor] (the `next_max_id` of
     * the page before) the one after it. [Page.next] is null once Meta says
     * there is no more. A read only: `feed_view_info` is sent empty, so no
     * post is reported as seen.
     */
    suspend fun forYouTimeline(cursor: String? = null): Page<ThreadsPost> {
        val token = session().bearerToken ?: throw ThreadsException(NOT_CONFIGURED, "no bearer")
        val device = deviceId()
        val after = cursor?.trim()?.ifEmpty { null }
        // The parameters threads-go's HomeTimeline sends: `pagination_source` alone now answers 404.
        val url = ThreadsLinks.address(
            INSTAGRAM,
            "/api/v1/feed/text_post_app_timeline/",
            listOfNotNull(
                "feed_type" to "for_you",
                "feed_view_info" to "[]",
                "reason" to (if (after == null) "cold_start_fetch" else "pagination"),
                "client_session_id" to device,
                "pagination_source_module" to "feed_unit",
                after?.let { "max_id" to it },
            ),
        )
        val body = sessionRead(url, bearerHeaders(token, device))
        val next = (body["next_max_id"].string ?: body["next_max_id"].integer?.toString())?.trim()?.ifEmpty { null }
        return Page(ThreadsParser.feed(body), next.takeIf { body["more_available"].boolean != false })
    }

    /**
     * What threads.com's front page shows a logged-out browser: the threads
     * Meta recommends to everyone. One page, since Meta names no cursor a
     * guest can follow; empty when it shows a guest its sign-in page instead.
     * It spends no session, and its LSD token serves the next profile read.
     */
    suspend fun guestForYou(): Page<ThreadsPost> {
        val page = try {
            guestPage(ThreadsLinks.web("/"))
        } catch (e: ThreadsException) {
            if (e.failure != SIGN_IN_REQUIRED) throw e
            return Page(emptyList(), null)
        }
        return Page(ThreadsPages.recommended(page), ThreadsNextPages.first(page, ::isFeed)?.encoded())
    }

    /** The front page's threads after [cursor], as [guestForYou]'s list goes on. */
    suspend fun moreRecommended(cursor: String): ThreadsMore<ThreadsPost> =
        more(cursor, ThreadsLinks.web("/")) { ThreadsPages.profileThreads(ThreadsPages.chainsIn(it), "") }

    /** [handle]'s threads after [cursor], the cursor [profileNext] gave. */
    suspend fun moreThreads(handle: String, cursor: String): ThreadsMore<ThreadsPost> {
        val key = handle.trim().lowercase()
        return more(cursor, profileUrl(key)) { ThreadsPages.profileThreads(ThreadsPages.chainsIn(it), key) }
    }

    /** [handle]'s replies after [cursor], the cursor [guestReplies] gave. */
    suspend fun moreReplies(handle: String, cursor: String): ThreadsMore<ThreadsPost> {
        val key = handle.trim().lowercase()
        return more(cursor, profileUrl(key, "replies")) { ThreadsPages.replies(ThreadsPages.chainsIn(it), key) }
    }

    /** [handle]'s reposts after [cursor], the cursor [guestReposts] gave. */
    suspend fun moreReposts(handle: String, cursor: String): ThreadsMore<ThreadsPost> {
        val key = handle.trim().lowercase()
        return more(cursor, profileUrl(key, "reposts")) { ThreadsPages.reposts(ThreadsPages.chainsIn(it), key) }
    }

    /**
     * A community's own page as a guest is shown it ([ThreadsCommunities.url]):
     * the first post of each thread it carries, the topics they are filed
     * under with their marks, and the cursor after them when the page names
     * one. Read off the caller's thread.
     */
    suspend fun communityPage(url: String): ThreadsSearchFound {
        val page = communityUrl(url) ?: throw ThreadsException(UNREACHABLE, "not a threads.com page: $url")
        val body = guestPage(page)
        return withContext(Dispatchers.Default) {
            val posts = ThreadsPages.profileThreads(ThreadsPages.chains(body), "").distinctBy { it.id }
            ThreadsSearchFound(posts, ThreadsSearchReading.topics(posts, ThreadsSearchReading.marks(body)), ThreadsNextPages.first(body) { true }?.encoded())
        }
    }

    /** A community page's posts after [cursor], the cursor [communityPage] gave. */
    suspend fun moreCommunity(url: String, cursor: String): ThreadsMore<ThreadsPost> {
        val page = communityUrl(url) ?: return ThreadsMore(emptyList(), null, refused = true)
        return more(cursor, page) { ThreadsPages.profileThreads(ThreadsPages.chainsIn(it), "") }
    }

    private fun communityUrl(url: String): HttpUrl? = ThreadsCommunities.threadsPage(url)?.toHttpUrlOrNull()

    /** What a search found after [cursor], the cursor [searchFound] gave. */
    suspend fun moreFound(query: String, tag: Boolean, recent: Boolean, cursor: String): ThreadsMore<ThreadsPost> =
        more(cursor, ThreadsLinks.searchUrl(query, tag, recent)) { ThreadsPages.searchResults(ThreadsPages.chainsIn(it), query, tag) }

    /** The reply threads under the post [targetId] at [postUrl] after [cursor], the cursor its [postPage] gave. */
    suspend fun moreReplyThreads(postUrl: String, targetId: String, cursor: String): ThreadsMore<ThreadsReplyThread> {
        val url = ThreadsLinks.canonicalPostUrl(postUrl) ?: return ThreadsMore(emptyList(), null, refused = true)
        return more(cursor, url) { ThreadsPostPageReader.replyThreads(it, targetId) }
    }

    /** The cursor after the threads [handle]'s page showed when it was last read; null when it named none. */
    fun profileNext(handle: String): String? = synchronized(profileCursors) { profileCursors[handle.trim().lowercase()] }

    /**
     * [handle]'s public threads without a session: guest GraphQL with the LSD
     * token and user id its page carries, else the threads the page itself
     * embeds. A known id and a fresh token skip the page: one request, not two.
     */
    suspend fun guestAccount(handle: String): Page<ThreadsPost> {
        val key = handle.trim().lowercase()
        val knownId = memory.kept.userIds[key]?.ifEmpty { null }
        val lsd = freshGuestLsd()
        if (knownId != null && lsd != null) guestThreadsOrNull(key, knownId, lsd)?.let { return Page(it, null) }

        val page = profilePage(key)
        val pageLsd = ThreadsPages.lsd(page)
        rememberGuestLsd(pageLsd)
        val userId = knownId ?: ThreadsPages.userId(page, key)
        if (pageLsd != null && !userId.isNullOrEmpty()) {
            rememberUserId(key, userId)
            guestThreadsOrNull(key, userId, pageLsd)?.let { return Page(it, null) }
        }
        val posts = ThreadsPages.profileThreads(page, key)
        if (posts.isEmpty() && isPrivate(page, key)) throw ThreadsException(PRIVATE_ACCOUNT, "@$key is private")
        if (posts.isEmpty()) throw ThreadsException(NO_SUCH_FEED, "no posts for @$key (GraphQL+SSR empty)")
        return Page(posts, null)
    }

    /**
     * A post page as [ThreadsPostPage] reads it: the post, what it answers,
     * its replies and the posts Threads suggests beside it. Remembered for ten
     * minutes and shared while in flight, so opening the same post again asks
     * Meta nothing; [force] asks again.
     */
    suspend fun postPage(postUrl: String, force: Boolean = false): ThreadsPostPage {
        val url = ThreadsLinks.canonicalPostUrl(postUrl) ?: throw ThreadsException(UNREACHABLE, "not a threads url: $postUrl")
        val key = ThreadsLinks.shortcode(url.toString()) ?: url.toString()
        if (!force) rememberedConversation(key)?.let { return it }
        return conversationReads.run(key) {
            val body = guestPage(url)
            val next = ThreadsNextPages.first(body) { it.path.endsWith(REPLIES_PAGE_INFO) }?.encoded()
            ThreadsPostPage.read(body).copy(repliesNext = next).also { rememberConversation(key, it) }
        }
    }

    /** A post page's chains: the conversation leading to the post, then each reply thread. */
    suspend fun postChains(postUrl: String, force: Boolean = false): List<List<ThreadsPost>> = postPage(postUrl, force).conversationChains

    /** Every post of a post page, root and replies; empty when Meta embedded none. */
    suspend fun postThread(postUrl: String): List<ThreadsPost> = postChains(postUrl).flatten()

    /** [handle]'s replies, from the public `/@handle/replies` page; empty when Meta did not embed them for a guest. */
    suspend fun guestReplies(handle: String): Page<ThreadsPost> {
        val key = handle.trim().lowercase()
        val page = guestPage(profileUrl(key, "replies"))
        return Page(ThreadsPages.replies(page, key), ThreadsNextPages.first(page) { "RepliesTab" in it.query.orEmpty() }?.encoded())
    }

    /**
     * What [handle] reposted, from the public `/@handle/reposts` page: the
     * profile's own page does not carry its reposts. Empty when Meta embedded
     * none for a guest; the cursor after them when the page named one.
     */
    suspend fun guestReposts(handle: String): Page<ThreadsPost> {
        val key = handle.trim().lowercase()
        val page = guestPage(profileUrl(key, "reposts"))
        val posts = withContext(Dispatchers.Default) { ThreadsPages.reposts(ThreadsPages.chains(page), key) }
        return Page(posts, ThreadsNextPages.first(page) { "RepostsTab" in it.query.orEmpty() }?.encoded())
    }

    /**
     * What Threads' public search finds for [query], as a logged-out browser sees
     * it: the top posts, the most recent with [recent], or with [tag] the posts
     * filed under that topic.
     */
    suspend fun searchPosts(query: String, tag: Boolean = false, recent: Boolean = false): Page<ThreadsPost> =
        Page(ThreadsPages.searchResults(guestPage(ThreadsLinks.searchUrl(query, tag, recent)), query, tag), null)

    /** [searchPosts] with the topics its posts are filed under, the page read off the caller's thread. */
    suspend fun searchFound(query: String, tag: Boolean = false, recent: Boolean = false): ThreadsSearchFound {
        val body = guestPage(ThreadsLinks.searchUrl(query, tag, recent))
        return withContext(Dispatchers.Default) {
            ThreadsSearchReading.read(body, query, tag).copy(next = ThreadsNextPages.first(body) { "Search" in it.query.orEmpty() }?.encoded())
        }
    }

    private fun isPrivate(page: String, handle: String): Boolean =
        ThreadsProfileOwner.enrich(ThreadsPages.guestProfile(page, handle), page, handle)?.isPrivate == true

    /** A profile's page, read once even when its card and its threads are asked for together. */
    private suspend fun profilePage(handle: String): String {
        val key = handle.trim().lowercase()
        return profilePages.run(key) { guestPage(profileUrl(key)).also { rememberProfileNext(key, it) } }
    }

    /**
     * A public page as a logged-out browser sees it. It spends no session, so
     * it ignores the session's cooldown. Redirects are followed here, one
     * departure for all of them, so that one to Meta's sign-in page is told
     * apart from a page that is missing.
     */
    private suspend fun guestPage(url: HttpUrl): String {
        val headers = mapOf(
            "User-Agent" to repairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to ACCEPT_LANGUAGE,
        )
        val response = send(asGuest = true, url) { followed(url, headers) }
        return when (response.code) {
            200 -> response.body.also { rememberGuestLsd(ThreadsPages.lsd(it)) }
            404 -> throw ThreadsException(NO_SUCH_FEED, "$url: 404", 404)
            429 -> throw guestThrottled(url)
            else -> throw ThreadsException(UNREACHABLE, "$url: ${response.code}", response.code)
        }
    }

    /** [url]'s answer after at most [MAX_REDIRECTS] redirects; one to the sign-in page is [SIGN_IN_REQUIRED]. */
    private suspend fun followed(url: HttpUrl, headers: Map<String, String>): XResponse {
        var at = url
        repeat(MAX_REDIRECTS + 1) {
            val response = http.get(at, headers, followRedirects = false)
            if (response.code !in REDIRECTS) return response
            val next = response.header("Location")?.let(at::resolve) ?: return response
            if (ThreadsLinks.isSignInPage(next)) throw ThreadsException(SIGN_IN_REQUIRED, "$url: sent to sign in", response.code)
            at = next
        }
        throw ThreadsException(UNREACHABLE, "$url: too many redirects")
    }

    private suspend fun guestThreadsOrNull(handle: String, userId: String, lsd: String): List<ThreadsPost>? = try {
        guestThreads(handle, userId, lsd).ifEmpty { null }
    } catch (e: Exception) {
        // A rotated doc id or a token that aged out: the page is the fallback.
        if (!e.isReadFailure()) throw e
        null
    }

    /** Guest GraphQL's profile tab (`BarcelonaProfileThreadsTabQuery`), as threads.com's own page asks it. */
    private suspend fun guestThreads(handle: String, userId: String, lsd: String): List<ThreadsPost> {
        val values = repairs()
        val url = ThreadsLinks.web(GRAPHQL)
        val query = GuestQuery(values.guestDocId, values.guestQueryName, buildJsonObject { put("userID", userId) })
        val response = guestQuery(query, lsd, "/@$handle")
        if (response.code == 429) throw guestThrottled(url)
        if (response.code != 200) throw ThreadsException(UNREACHABLE, "$url: ${response.code}", response.code)
        // Guest GraphQL sometimes answers with the HTML shell when its headers are off; a post may well mention `<html>`.
        if (response.body.trimStart().startsWith("<")) throw ThreadsException(UNREACHABLE, "$url: HTML instead of JSON", response.code)
        return ThreadsParser.graphqlFeed(decodeJson(response, url))
    }

    /** One guest GraphQL query, as a logged-out threads.com page at [referer] asks it with its [lsd] token; paced as a guest read. */
    internal suspend fun guestQuery(query: GuestQuery, lsd: String, referer: String, path: String = GRAPHQL): XResponse {
        val values = repairs()
        val url = ThreadsLinks.web(path)
        val form = listOf("lsd" to lsd, "doc_id" to query.docId, "variables" to query.variables.toString()) + query.extraForm
        val body = form.joinToString("&") { (name, value) -> "${ThreadsLinks.formEncode(name)}=${ThreadsLinks.formEncode(value)}" }
        val headers = mapOf(
            "User-Agent" to values.webUserAgent,
            "Content-Type" to FORM,
            "Accept" to "*/*",
            "Accept-Language" to ACCEPT_LANGUAGE,
            "X-IG-App-ID" to values.appId,
            "X-ASBD-ID" to values.asbdId,
            "X-FB-LSD" to lsd,
            "X-FB-Friendly-Name" to query.name,
            "Origin" to ThreadsLinks.WEB,
            "Referer" to "${ThreadsLinks.WEB}$referer",
        )
        return send(asGuest = true, url) { http.post(url, headers, body, FORM) }
    }

    /**
     * The page after [cursor] of the list [page] shows, as threads.com's
     * client asks it, its posts read by [read]. A throttle or a server that
     * failed is thrown, to be retried; an answer in a shape not understood
     * (a refusal, HTML, errors and nothing else) is [ThreadsMore.refused],
     * and the list's rest is left to threads.com.
     */
    private suspend fun <T> more(cursor: String, page: HttpUrl, read: (List<JsonElement>) -> List<T>): ThreadsMore<T> {
        val refused = ThreadsMore<T>(emptyList(), null, refused = true)
        val asked = ThreadsNextPage.decode(cursor) ?: return refused
        val lsd = freshGuestLsd() ?: ThreadsPages.lsd(guestPage(page)) ?: return refused
        val referer = page.encodedPath + (page.encodedQuery?.let { "?$it" } ?: "")
        val url = ThreadsLinks.web(GRAPHQL)
        val response = guestQuery(GuestQuery(asked.docId, asked.query, asked.requestVariables, GuestQuery.relayForm(asked.query)), lsd, referer)
        when {
            response.code == 429 -> throw guestThrottled(url)
            response.code >= 500 -> throw ThreadsException(UNREACHABLE, "$url: ${response.code}", response.code)
            response.code != 200 -> return refused
        }
        val answers = ThreadsNextPages.answer(response.body)
        val roots = answers.filter { (it as? JsonObject)?.get("data") is JsonObject }
        if (roots.isEmpty()) return refused
        val items = withContext(Dispatchers.Default) { read(roots) }
        val next = roots.firstNotNullOfOrNull { ThreadsNextPages.after(asked, it) }?.encoded()
        val failed = items.isEmpty() && next == null && answers.any { (it as? JsonObject)?.get("errors") is JsonArray }
        return ThreadsMore(items, next, refused = failed)
    }

    private fun rememberProfileNext(handle: String, page: String) {
        val next = ThreadsNextPages.first(page) { "ThreadsTab" in it.query.orEmpty() }?.encoded()
        synchronized(profileCursors) {
            profileCursors.remove(handle)
            profileCursors[handle] = next
            while (profileCursors.size > CONVERSATIONS_KEPT) profileCursors.remove(profileCursors.keys.first())
        }
    }

    private fun isFeed(info: ThreadsPageInfo): Boolean = "Feed" in info.query.orEmpty()

    /** A session read: paced, refused while the session cools down, and checked for what Meta said. */
    private suspend fun sessionRead(url: HttpUrl, headers: Map<String, String>): J {
        val response = send(asGuest = false, url) { http.get(url, headers, followRedirects = true) }
        checkSession(response, url)
        return decodeJson(response, url)
    }

    private suspend fun send(asGuest: Boolean, url: HttpUrl, request: suspend () -> XResponse): XResponse {
        departInTurn(asGuest)
        val limit = if (asGuest) GUEST_TIMEOUT_MILLIS else TIMEOUT_MILLIS
        return withTimeoutOrNull(limit) { request() } ?: throw SocketTimeoutException("$url: no answer in ${limit / 1000} s")
    }

    /**
     * Waits for this request's turn to leave, a read run in [ThreadsBackground]
     * (Home's followed accounts) first letting every read the reader is
     * waiting on (a profile, a post, a search) go ahead of it.
     */
    private suspend fun departInTurn(asGuest: Boolean) {
        if (coroutineContext[ThreadsBackground.Key] != null) {
            // One background read at a time queues for a departure, so a read the reader waits on is behind one of them at most.
            return backgroundLane.withLock {
                waitingInFront.first { it == 0 }
                depart(asGuest)
            }
        }
        waitingInFront.update { it + 1 }
        try {
            depart(asGuest)
        } finally {
            waitingInFront.update { it - 1 }
        }
    }

    /**
     * Waits for this request's turn to leave. Departures are serialised and
     * spaced, but the turn is released as the request leaves, not when it is
     * answered: one slow answer must not hold every request behind it.
     */
    private suspend fun depart(asGuest: Boolean) = departures.withLock {
        if (!asGuest) {
            coolingDown()?.let { until ->
                val at = Instant.ofEpochMilli(until)
                throw ThreadsException(SESSION_SUSPENDED, "cooling down until $at", retryAt = at)
            }
        }
        lastDeparture?.let { last ->
            val wait = pacing.gap(asGuest) - (clock() - last)
            if (wait > 0) pacing.sleep(wait)
        }
        lastDeparture = clock()
    }

    /** What Meta said to a session read: a throttle or `login_required` parks the session. */
    private fun checkSession(response: XResponse, url: HttpUrl) {
        val body = response.body
        val loginRequired = "login_required" in body || "logout_reason" in body
        when {
            response.code == 429 || "Please wait a few minutes" in body ->
                throw ThreadsException(THROTTLED, "$url: rate limited", response.code, retryAt = armCooldown())
            response.code == 401 || response.code == 403 || loginRequired -> {
                if (loginRequired) armCooldown()
                throw ThreadsException(if (loginRequired) SESSION_SUSPENDED else UNAUTHORIZED, "$url: ${response.code}", response.code)
            }
            response.code == 404 -> throw ThreadsException(NO_SUCH_FEED, "$url: 404", 404)
            response.code != 200 -> throw ThreadsException(UNREACHABLE, "$url: ${response.code}", response.code)
        }
    }

    private fun decodeJson(response: XResponse, url: HttpUrl): J =
        ThreadsJson.decode(response.body)?.let { J(it) } ?: throw ThreadsException(UNREACHABLE, "$url: not JSON", response.code)

    /** A guest read Meta refused for now: it has no session to park, so it waits as long as any limit without a stated reset. */
    private fun guestThrottled(url: HttpUrl) =
        ThreadsException(THROTTLED, "$url: 429", 429, retryAt = Instant.ofEpochMilli(clock() + GUEST_THROTTLE_MILLIS))

    /** When the session may talk to Meta again, or null when it may now; an ended cooldown is cleared. */
    private fun coolingDown(): Long? = synchronized(keeping) {
        val until = memory.kept.cooldownUntil ?: return null
        if (clock() < until) return until
        memory.kept = memory.kept.copy(cooldownUntil = null)
        null
    }

    /** Backs the session off as Meta asked. Kept with [memory]: a cooldown a restart clears is no cooldown. */
    private fun armCooldown(): Instant {
        val until = clock() + COOLDOWN_MILLIS
        keep { it.copy(cooldownUntil = until) }
        return Instant.ofEpochMilli(until)
    }

    private fun requireCookies(): ThreadsSession =
        session().takeIf { it.hasCookies } ?: throw ThreadsException(NOT_CONFIGURED, "incomplete cookies")

    private suspend fun searchUserId(handle: String): String = searchUsers(handle).items
        .filter { it.username.lowercase() == handle.lowercase() }
        .firstNotNullOfOrNull { it.pk.ifEmpty { null } ?: it.id.ifEmpty { null } }
        ?: throw ThreadsException(NO_SUCH_FEED, "user not found: $handle")

    private fun cookieHeaders(current: ThreadsSession): Map<String, String> {
        val values = repairs()
        val cookies = current.cookies
        return mapOf(
            "User-Agent" to values.webUserAgent,
            "Accept" to "application/json, text/plain, */*",
            "Accept-Language" to ACCEPT_LANGUAGE,
            "X-IG-App-ID" to values.appId,
            "X-CSRFToken" to (cookies["csrftoken"] ?: throw ThreadsException(UNAUTHORIZED, "session cleared")),
            "X-ASBD-ID" to values.asbdId,
            "X-IG-WWW-Claim" to "0",
            "Referer" to "${ThreadsLinks.WEB}/",
            "Cookie" to ThreadsSession.REQUIRED_COOKIES.joinToString("; ") { "$it=${cookies[it]}" },
        )
    }

    private fun bearerHeaders(token: String, device: String): Map<String, String> {
        val values = repairs()
        return mapOf(
            "User-Agent" to values.appUserAgent,
            "Authorization" to "Bearer $token",
            "Accept" to "*/*",
            "Accept-Language" to ACCEPT_LANGUAGE,
            "X-IG-App-ID" to values.appId,
            "X-IG-Capabilities" to "3brTvx0=",
            "X-IG-Connection-Type" to "WIFI",
            "X-IG-Device-ID" to device,
        )
    }

    private fun profileUrl(handle: String, vararg more: String): HttpUrl =
        ThreadsLinks.WEB.toHttpUrl().newBuilder().addPathSegment("@$handle").apply { more.forEach { addPathSegment(it) } }.build()

    private fun deviceId(): String = synchronized(keeping) {
        memory.kept.deviceId?.trim()?.ifEmpty { null } ?: newDeviceId().also { id -> memory.kept = memory.kept.copy(deviceId = id) }
    }

    /** The LSD token of a profile page read in the last ten minutes: page-scoped, so it serves every account's GraphQL call. */
    private fun freshGuestLsd(): String? {
        val kept = memory.kept
        val at = kept.guestLsdAt ?: return null
        return kept.guestLsd?.ifEmpty { null }?.takeIf { clock() - at <= LSD_TTL_MILLIS }
    }

    private fun rememberGuestLsd(lsd: String?) {
        if (lsd.isNullOrEmpty()) return
        keep { it.copy(guestLsd = lsd, guestLsdAt = clock()) }
    }

    private fun rememberUserId(handle: String, id: String) {
        val key = handle.lowercase()
        if (key.isEmpty() || id.isEmpty() || memory.kept.userIds[key] == id) return
        keep { it.copy(userIds = (it.userIds - key + (key to id)).entries.toList().takeLast(USER_IDS_KEPT).associate { (k, v) -> k to v }) }
    }

    private fun rememberedConversation(key: String): ThreadsPostPage? = synchronized(conversations) {
        conversations[key]?.takeIf { (at, _) -> clock() - at < CONVERSATION_TTL_MILLIS }?.second
    }

    private fun rememberConversation(key: String, page: ThreadsPostPage) {
        if (page.conversationChains.isEmpty()) return
        synchronized(conversations) {
            conversations.remove(key)
            conversations[key] = clock() to page
            while (conversations.size > CONVERSATIONS_KEPT) conversations.remove(conversations.keys.first())
        }
    }

    private fun keep(change: (ThreadsKept) -> ThreadsKept) = synchronized(keeping) { memory.kept = change(memory.kept) }

    companion object {
        /** How many posts one account's read brings into a feed (XTA's threadsPostsPerAccount). */
        const val POSTS_PER_ACCOUNT = 20

        private const val INSTAGRAM = "https://i.instagram.com"
        private const val GRAPHQL = "/api/graphql"
        private const val REPLIES_PAGE_INFO = "media.text_post_app_info.direct_replies.page_info"
        private const val FORM = "application/x-www-form-urlencoded"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val TIMEOUT_MILLIS = 25_000L

        /** A guest read that has not answered in this long is given up: a public page answers in a few seconds or not at all. */
        private const val GUEST_TIMEOUT_MILLIS = 12_000L
        private const val MAX_REDIRECTS = 4
        private val REDIRECTS = setOf(301, 302, 303, 307, 308)
        private const val COOLDOWN_MILLIS = 30 * 60 * 1000L

        /** XTA's rateLimitFallback: how long a limit without a stated reset is waited out. */
        private const val GUEST_THROTTLE_MILLIS = 15 * 60 * 1000L
        private const val LSD_TTL_MILLIS = 10 * 60 * 1000L
        private const val CONVERSATION_TTL_MILLIS = 10 * 60 * 1000L
        private const val CONVERSATIONS_KEPT = 40

        /** How many handles' ids are remembered: the ones learnt last, so a reader's searches never grow the settings without end. */
        internal const val USER_IDS_KEPT = 400

        private fun newDeviceId(): String {
            val random = SecureRandom()
            fun hex(bytes: Int) = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            return "${hex(4)}-${hex(2)}-${hex(2)}-${hex(2)}-${hex(6)}"
        }
    }
}

/** A guest GraphQL query: its doc id, the name threads.com sends it under, its variables, and any form fields its client adds. */
internal data class GuestQuery(
    val docId: String,
    val name: String,
    val variables: JsonObject,
    val extraForm: List<Pair<String, String>> = emptyList(),
) {
    companion object {
        /** The form fields threads.com's Relay client adds to a query it names. */
        fun relayForm(name: String) = listOf("fb_api_caller_class" to "RelayModern", "fb_api_req_friendly_name" to name, "server_timestamps" to "true")
    }
}

/**
 * Marks reads nobody is looking at yet (Home's followed accounts and their
 * extras): each waits while a read the reader is waiting on wants its turn,
 * so what was just opened is not queued behind Home.
 */
object ThreadsBackground : CoroutineContext.Element {
    override val key: CoroutineContext.Key<*> get() = Key

    /** How a coroutine is asked whether it reads in the background. */
    object Key : CoroutineContext.Key<ThreadsBackground>
}
