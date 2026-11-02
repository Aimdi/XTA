package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.threads.ThreadsFailure.NOT_CONFIGURED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.NO_SUCH_FEED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.SESSION_SUSPENDED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.THROTTLED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.UNAUTHORIZED
import com.aimdi.xtanative.networks.threads.ThreadsFailure.UNREACHABLE
import java.net.SocketTimeoutException
import java.security.SecureRandom
import java.time.Instant
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl

/**
 * Reads Threads from Meta itself, as XTA's threads_direct_client.dart does:
 * public pages and guest GraphQL with no account, and, when the reader pasted
 * a session, the cookie API on threads.com and the Bearer API on
 * i.instagram.com. Read-only: nothing is posted, liked, reposted or followed.
 *
 * Requests leave one at a time, the gap of [pacing] apart, and a session that
 * Meta throttled or parked is not used again until its cooldown, kept in
 * [memory], has passed; public reads spend no session and ignore it. Every
 * list read is one page: Meta names no cursor a guest can follow, so
 * [Page.next] is always null.
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
    private var lastDeparture: Long? = null
    private val keeping = Any()
    private val profilePages = SingleFlight<String>()
    private val conversationReads = SingleFlight<List<List<ThreadsPost>>>()
    private val conversations = LinkedHashMap<String, Pair<Long, List<List<ThreadsPost>>>>()

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
        val profile = ThreadsPages.guestProfile(profilePage(key), key)
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

    /** The Bearer session's home timeline ("For you"), at most [limit] threads. */
    suspend fun followingTimeline(limit: Int = 40): Page<ThreadsPost> {
        val token = session().bearerToken ?: throw ThreadsException(NOT_CONFIGURED, "no bearer")
        val device = deviceId()
        // The parameters threads-go's HomeTimeline sends: `pagination_source` alone now answers 404.
        val url = ThreadsLinks.address(
            INSTAGRAM,
            "/api/v1/feed/text_post_app_timeline/",
            listOf(
                "feed_type" to "for_you",
                "feed_view_info" to "[]",
                "reason" to "cold_start_fetch",
                "client_session_id" to device,
                "pagination_source_module" to "feed_unit",
            ),
        )
        return Page(ThreadsParser.feed(sessionRead(url, bearerHeaders(token, device))).take(limit), null)
    }

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
        if (posts.isEmpty()) throw ThreadsException(NO_SUCH_FEED, "no posts for @$key (GraphQL+SSR empty)")
        return Page(posts, null)
    }

    /**
     * A post page's chains: the conversation leading to the post, then each
     * reply thread. Remembered for ten minutes and shared while in flight, so
     * opening the same post again asks Meta nothing; [force] asks again.
     */
    suspend fun postChains(postUrl: String, force: Boolean = false): List<List<ThreadsPost>> {
        val url = ThreadsLinks.canonicalPostUrl(postUrl) ?: throw ThreadsException(UNREACHABLE, "not a threads url: $postUrl")
        val key = ThreadsLinks.shortcode(url.toString()) ?: url.toString()
        if (!force) rememberedConversation(key)?.let { return it }
        return conversationReads.run(key) { ThreadsPages.chains(guestPage(url)).also { rememberConversation(key, it) } }
    }

    /** Every post of a post page, root and replies; empty when Meta embedded none. */
    suspend fun postThread(postUrl: String): List<ThreadsPost> = postChains(postUrl).flatten()

    /** [handle]'s replies, from the public `/@handle/replies` page; empty when Meta did not embed them for a guest. */
    suspend fun guestReplies(handle: String): Page<ThreadsPost> {
        val key = handle.trim().lowercase()
        return Page(ThreadsPages.replies(guestPage(profileUrl(key, "replies")), key), null)
    }

    /**
     * What Threads' public search finds for [query], as a logged-out browser sees
     * it: the top posts, the most recent with [recent], or with [tag] the posts
     * filed under that topic.
     */
    suspend fun searchPosts(query: String, tag: Boolean = false, recent: Boolean = false): Page<ThreadsPost> =
        Page(ThreadsPages.searchResults(guestPage(ThreadsLinks.searchUrl(query, tag, recent)), query, tag), null)

    /** A profile's page, read once even when its card and its threads are asked for together. */
    private suspend fun profilePage(handle: String): String {
        val key = handle.trim().lowercase()
        return profilePages.run(key) { guestPage(profileUrl(key)) }
    }

    /** A public page as a logged-out browser sees it. It spends no session, so it ignores the session's cooldown. */
    private suspend fun guestPage(url: HttpUrl): String {
        val headers = mapOf(
            "User-Agent" to repairs().webUserAgent,
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8",
            "Accept-Language" to ACCEPT_LANGUAGE,
        )
        val response = send(asGuest = true, url) { http.get(url, headers, followRedirects = true) }
        return when (response.code) {
            200 -> response.body
            404 -> throw ThreadsException(NO_SUCH_FEED, "$url: 404", 404)
            429 -> throw guestThrottled(url)
            else -> throw ThreadsException(UNREACHABLE, "$url: ${response.code}", response.code)
        }
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
        val url = ThreadsLinks.web("/api/graphql")
        val form = listOf("lsd" to lsd, "doc_id" to values.guestDocId, "variables" to buildJsonObject { put("userID", userId) }.toString())
        val body = form.joinToString("&") { (name, value) -> "${ThreadsLinks.formEncode(name)}=${ThreadsLinks.formEncode(value)}" }
        val headers = mapOf(
            "User-Agent" to values.webUserAgent,
            "Content-Type" to FORM,
            "Accept" to "*/*",
            "Accept-Language" to ACCEPT_LANGUAGE,
            "X-IG-App-ID" to values.appId,
            "X-ASBD-ID" to values.asbdId,
            "X-FB-LSD" to lsd,
            "X-FB-Friendly-Name" to values.guestQueryName,
            "Origin" to ThreadsLinks.WEB,
            "Referer" to "${ThreadsLinks.WEB}/@$handle",
        )
        val response = send(asGuest = true, url) { http.post(url, headers, body, FORM) }
        if (response.code == 429) throw guestThrottled(url)
        if (response.code != 200) throw ThreadsException(UNREACHABLE, "$url: ${response.code}", response.code)
        val decoded = decodeJson(response, url)
        // Guest GraphQL sometimes answers with the HTML shell when its headers are off.
        if (response.body.trimStart().startsWith("<!") || response.body.contains("<html")) {
            throw ThreadsException(UNREACHABLE, "$url: HTML instead of JSON", response.code)
        }
        return ThreadsParser.graphqlFeed(decoded)
    }

    /** A session read: paced, refused while the session cools down, and checked for what Meta said. */
    private suspend fun sessionRead(url: HttpUrl, headers: Map<String, String>): J {
        val response = send(asGuest = false, url) { http.get(url, headers, followRedirects = true) }
        checkSession(response, url)
        return decodeJson(response, url)
    }

    private suspend fun send(asGuest: Boolean, url: HttpUrl, request: suspend () -> XResponse): XResponse {
        depart(asGuest)
        return withTimeoutOrNull(TIMEOUT_MILLIS) { request() } ?: throw SocketTimeoutException("$url: no answer in ${TIMEOUT_MILLIS / 1000} s")
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
        keep { it.copy(userIds = it.userIds + (key to id)) }
    }

    private fun rememberedConversation(key: String): List<List<ThreadsPost>>? = synchronized(conversations) {
        conversations[key]?.takeIf { (at, _) -> clock() - at < CONVERSATION_TTL_MILLIS }?.second
    }

    private fun rememberConversation(key: String, chains: List<List<ThreadsPost>>) {
        if (chains.isEmpty()) return
        synchronized(conversations) {
            conversations.remove(key)
            conversations[key] = clock() to chains
            while (conversations.size > CONVERSATIONS_KEPT) conversations.remove(conversations.keys.first())
        }
    }

    private fun keep(change: (ThreadsKept) -> ThreadsKept) = synchronized(keeping) { memory.kept = change(memory.kept) }

    companion object {
        /** How many posts one account's read brings into a feed (XTA's threadsPostsPerAccount). */
        const val POSTS_PER_ACCOUNT = 20

        private const val INSTAGRAM = "https://i.instagram.com"
        private const val FORM = "application/x-www-form-urlencoded"
        private const val ACCEPT_LANGUAGE = "en-US,en;q=0.9"
        private const val TIMEOUT_MILLIS = 25_000L
        private const val COOLDOWN_MILLIS = 30 * 60 * 1000L

        /** XTA's rateLimitFallback: how long a limit without a stated reset is waited out. */
        private const val GUEST_THROTTLE_MILLIS = 15 * 60 * 1000L
        private const val LSD_TTL_MILLIS = 10 * 60 * 1000L
        private const val CONVERSATION_TTL_MILLIS = 10 * 60 * 1000L
        private const val CONVERSATIONS_KEPT = 40

        private fun newDeviceId(): String {
            val random = SecureRandom()
            fun hex(bytes: Int) = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            return "${hex(4)}-${hex(2)}-${hex(2)}-${hex(2)}-${hex(6)}"
        }
    }
}
