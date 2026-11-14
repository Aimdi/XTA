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
 * does. Elsewhere [Page.next] is null. Where that query names no doc id,
 * is refused or brings nothing, the list's refetch query stands in
 * ([ThreadsRefetch]); when it brings nothing either the list ends as it did.
 *
 * A guest request two screens send alike leaves once ([ThreadsGuestAnswers]):
 * one already on its way is joined, and what Home was just answered serves
 * a screen opened a moment later, and the other way round. A profile's card
 * is remembered as long as its threads, a post page for ten minutes.
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
    private val guestAnswers = ThreadsGuestAnswers(clock)
    private val profileCards = LinkedHashMap<String, Pair<Long, ThreadsProfile>>()
    private val conversationReads = SingleFlight<ThreadsPostPage>()
    private val conversations = LinkedHashMap<String, Pair<Long, ThreadsPostPage>>()
    private val profileCursors = LinkedHashMap<String, String?>()
    private val trendReads = SingleFlight<List<ThreadsTrend>>()
    private var trendsRead: Pair<Long, List<ThreadsTrend>>? = null
    private val recommendedReads = SingleFlight<List<ThreadsProfile>>()
    private var recommendedRead: Pair<Long, List<ThreadsProfile>>? = null

    /** The values the pages read gave Relay's provided variables ([ThreadsRelayProviders]), the latest of each. */
    private val relayProviders = LinkedHashMap<String, Boolean>()

    /** Where each reply read sits: the post whose page carried its thread, and where Meta says the thread's rest starts. */
    private val replyPlaces = LinkedHashMap<String, ReplyPlace>()

    /** Which of a session list's paths answered last ([sessionApi]), by the list's kind. */
    private val answeringPaths = HashMap<String, Int>()

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
        return ThreadsPeople.page(sessionApi("people:$list", ThreadsPeople.paths(list, id), ThreadsPeople.params(list, cursor, query)))
    }

    /**
     * One page of the posts that quote the post Meta keeps under [mediaId]
     * ([ThreadsQuotes]), after [cursor]: a session read as [people] is, with
     * neither cookies nor a Bearer [NOT_CONFIGURED]. A read only: nothing is
     * quoted or reposted.
     */
    suspend fun quotes(mediaId: String, cursor: String? = null): Page<ThreadsPost> {
        val id = mediaId.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: throw ThreadsException(NO_SUCH_FEED, "no media id for quotes")
        return ThreadsQuotes.page(sessionApi("quotes", ThreadsQuotes.paths(id), ThreadsQuotes.params(cursor)), id)
    }

    /**
     * The words of the post Meta keeps under [mediaId] (its caption's own id
     * [captionId] where Meta gave one) in [target], a BCP 47 tag, as Meta
     * translates them ([ThreadsTranslation]); null when Meta answered with
     * none. A session read as [people] is, with neither cookies nor a Bearer
     * [NOT_CONFIGURED]; [NO_SUCH_FEED] when no path answers. A read only.
     */
    suspend fun translation(mediaId: String, captionId: String?, target: String): String? {
        val id = mediaId.trim().takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: throw ThreadsException(NO_SUCH_FEED, "no media id for a translation")
        val body = sessionApi("translation", ThreadsTranslation.paths(id), ThreadsTranslation.params(id, captionId), ThreadsTranslation.acceptLanguage(target))
        return ThreadsTranslation.text(body)
    }

    /**
     * One page of the signed-in reader's own [feed] ([ThreadsOwnFeeds]): the
     * Following feed, or the posts they saved or liked on Threads, after
     * [cursor]. A session read as [people] is, with neither cookies nor a
     * Bearer [NOT_CONFIGURED]; [NO_SUCH_FEED] when no path answers. An answer
     * that cannot be the Following feed ([ThreadsOwnFeeds.isFollowing]) is
     * taken for a path that does not serve it. A read only: nothing is
     * followed, saved, liked or marked seen.
     */
    suspend fun ownFeed(feed: ThreadsOwnFeed, cursor: String? = null): Page<ThreadsPost> {
        val accepts: (J) -> Boolean = if (feed == ThreadsOwnFeed.FOLLOWING) ThreadsOwnFeeds::isFollowing else { _ -> true }
        val body = sessionApi("own:$feed", ThreadsOwnFeeds.paths(feed), ThreadsOwnFeeds.params(feed, cursor, deviceId()), accepts = accepts)
        return ThreadsOwnFeeds.page(body)
    }

    /**
     * One page of the signed-in reader's activity ([ThreadsActivity]), the
     * part [filter] names, after [cursor]: a session read as [people] is.
     * Asked never to mark anything seen; nothing is answered or followed.
     */
    suspend fun activity(filter: ThreadsActivityFilter = ThreadsActivityFilter.ALL, cursor: String? = null): Page<ThreadsActivityItem> =
        ThreadsActivity.page(sessionApi("activity", ThreadsActivity.PATHS, ThreadsActivity.params(filter, cursor)))

    /**
     * A session read of the API behind Threads at the first of [paths] that
     * answers: the cookies on threads.com when the reader gave them, else
     * the Bearer on the app API. A path Meta answers 404 gives way to the next
     * ([NO_SUCH_FEED] once none is left); the one that answered is used first
     * for [kind] from then on, so a list whose path is known asks once.
     * [language], when given, is the Accept-Language Meta answers in. An
     * answer [accepts] refuses is taken for a missing path: it is not what
     * [kind] asked for.
     */
    private suspend fun sessionApi(
        kind: String,
        paths: List<String>,
        params: List<Pair<String, String>>,
        language: String? = null,
        accepts: (J) -> Boolean = { true },
    ): J {
        val current = session()
        val token = current.bearerToken
        if (!current.hasCookies && token == null) throw ThreadsException(NOT_CONFIGURED, "no session for $kind")
        val known = synchronized(keeping) { answeringPaths[kind] }
        val tried = paths.indices.sortedBy { if (it == known) -1 else it }
        var missing: ThreadsException? = null
        for (index in tried) {
            val path = paths[index]
            try {
                val inLanguage = { headers: Map<String, String> -> if (language == null) headers else headers + ("Accept-Language" to language) }
                val body = when {
                    current.hasCookies -> sessionRead(ThreadsLinks.web(path, params), inLanguage(cookieHeaders(current)))
                    else -> sessionRead(ThreadsLinks.address(INSTAGRAM, path, params), inLanguage(bearerHeaders(checkNotNull(token), deviceId())))
                }
                if (!accepts(body)) throw ThreadsException(NO_SUCH_FEED, "$path: not $kind")
                synchronized(keeping) { answeringPaths[kind] = index }
                return body
            } catch (e: ThreadsException) {
                if (e.failure != NO_SUCH_FEED) throw e
                missing = e
            }
        }
        throw missing ?: ThreadsException(NO_SUCH_FEED, "no path for $kind")
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

    /**
     * [handle]'s profile card from its public `threads.com/@handle` page, no
     * login. Remembered for [CARD_TTL_MILLIS], as an account's threads are,
     * so a profile opened again asks Meta nothing; [force] reads the page again.
     */
    suspend fun guestProfile(handle: String, force: Boolean = false): ThreadsProfile {
        val key = handle.trim().lowercase()
        if (!force) rememberedCard(key)?.let { return it }
        val page = profilePage(key)
        val profile = ThreadsProfileOwner.enrich(ThreadsPages.guestProfile(page, key), page, key)
            ?: throw ThreadsException(NO_SUCH_FEED, "guest profile missing: @$key")
        if (profile.pk.isNotEmpty()) rememberUserId(key, profile.pk)
        rememberCard(key, profile)
        return profile
    }

    /** How many guest requests were answered without being sent, shared with another screen's read of the same page or query. */
    val sharedAnswers: Int get() = guestAnswers.shared

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
        return Page(ThreadsPages.recommended(page), ThreadsNextPages.first(page, wanted = ::isFeed)?.encoded())
    }

    /** The front page's threads after [cursor], as [guestForYou]'s list goes on. */
    suspend fun moreRecommended(cursor: String): ThreadsMore<ThreadsPost> =
        more(cursor, ThreadsLinks.web("/")) { ThreadsPages.profileThreads(ThreadsPages.chainsIn(it), "") }

    /** [handle]'s threads after [cursor], the cursor [profileNext] gave. */
    suspend fun moreThreads(handle: String, cursor: String): ThreadsMore<ThreadsPost> {
        val key = handle.trim().lowercase()
        return more(cursor, profileUrl(key), ThreadsRefetch.PROFILE_THREADS) { ThreadsPages.profileThreads(ThreadsPages.chainsIn(it), key) }
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
        more(cursor, ThreadsLinks.searchUrl(query, tag, recent), ThreadsRefetch.SEARCH) { ThreadsPages.searchResults(ThreadsPages.chainsIn(it), query, tag) }

    /** The reply threads under the post [targetId] at [postUrl] after [cursor], the cursor its [postPage] gave. */
    suspend fun moreReplyThreads(postUrl: String, targetId: String, cursor: String): ThreadsMore<ThreadsReplyThread> {
        val url = ThreadsLinks.canonicalPostUrl(postUrl) ?: return ThreadsMore(emptyList(), null, refused = true)
        val permalink = ThreadsLinks.shortcode(url.toString())?.let(ThreadsPeople::idOfShortcode)
        return more(cursor, url, ThreadsRefetch.REPLIES) { roots -> ThreadsPostPageReader.replyThreads(roots, targetId).also { rememberReplyPlaces(it, permalink) } }
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
            val read = ThreadsPostPage.read(body)
            val permalink = read.target?.let(ThreadsPeople::mediaId)
            val next = ThreadsNextPages.first(body, refetchBy(ThreadsRefetch.REPLIES) { permalink }) { it.path.endsWith(REPLIES_PAGE_INFO) }?.encoded()
            rememberReplyPlaces(read.replies, permalink)
            read.copy(repliesNext = next).also { rememberConversation(key, it) }
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
            val refetch = refetchBy(ThreadsRefetch.SEARCH) { query.trim().takeIf { !tag && !recent } }
            ThreadsSearchReading.read(body, query, tag).copy(next = ThreadsNextPages.first(body, refetch) { "Search" in it.query.orEmpty() }?.encoded())
        }
    }

    /**
     * Meta's trending topics, as threads.com's empty search page shows a
     * guest ([ThreadsTrends]): one public page, no session. Remembered for
     * [TRENDS_TTL_MILLIS] so the search page asks once however often it is
     * opened; [force] reads again. Empty when the page carries none or sends
     * a guest to sign in. Read off the caller's thread. A page without
     * trends is followed by the query its client asks them with
     * ([ThreadsBundleReads.TRENDING_TOPICS]) once that read's doc id is
     * known; until then nothing more is asked.
     */
    suspend fun trends(force: Boolean = false): List<ThreadsTrend> {
        if (!force) knownTrends()?.let { return it }
        return trendReads.run(TRENDS) {
            val body = try {
                guestPage(ThreadsLinks.web("/search"))
            } catch (e: ThreadsException) {
                if (e.failure != SIGN_IN_REQUIRED) throw e
                ""
            }
            withContext(Dispatchers.Default) { ThreadsTrends.read(body) }.ifEmpty { askedTrends() }
                .also { trends -> synchronized(keeping) { trendsRead = clock() to trends } }
        }
    }

    /** Whether the replies under a reply can be read in place ([deeperReplies]): only once that read's doc id is known. */
    val readsDeeperReplies: Boolean get() = repairs().inlineRepliesDocId.isNotEmpty()

    /**
     * The replies Meta holds under the reply [post] beyond what its
     * conversation's page carried ([ThreadsBundleReads.INLINE_REPLIES]), as
     * one thread: a guest read, asked as the post's own page asks it. Null,
     * with nothing asked, while the read is off ([readsDeeperReplies]) or the
     * post has no link or media id; null too when Meta refuses or answers in
     * a shape not understood, and the thread goes on at the post's own page
     * as before. A throttle or a server that failed is thrown. It is asked
     * with every variable the query declares ([ThreadsBundleReads.inlineReplies]):
     * the post whose page carried the reply and where its thread's rest
     * starts are those of the conversation it was read in, when it was.
     */
    suspend fun deeperReplies(post: ThreadsPost): ThreadsReplyThread? {
        val values = repairs()
        if (values.inlineRepliesDocId.isEmpty()) return null
        val url = post.url?.let(ThreadsLinks::canonicalPostUrl) ?: return null
        val media = ThreadsPeople.mediaId(post) ?: return null
        val place = synchronized(replyPlaces) { replyPlaces[post.id] }
        val variables = ThreadsBundleReads.inlineReplies(values.inlineRepliesVariable, media, place?.permalink, place?.pagingToken, values.inlineRepliesExpansion)
        val roots = bundleRead(values.inlineRepliesDocId, ThreadsBundleReads.INLINE_REPLIES, variables, url) ?: return null
        return withContext(Dispatchers.Default) { ThreadsPostPageReader.deeper(roots, post.id) }
            ?.also { rememberReplyPlaces(listOf(it), place?.permalink) }
    }

    /** Whether what Threads says about a profile can be read ([aboutProfile]): only once that read's doc id is known. */
    val readsAboutProfile: Boolean get() = repairs().aboutProfileDocId.isNotEmpty()

    /**
     * What Threads' "About this profile" says of [handle]
     * ([ThreadsBundleReads.ABOUT_PROFILE]): a guest read, asked as the
     * profile's page asks it, the account's id taken from its card. Null,
     * with nothing asked, while the read is off ([readsAboutProfile]); null
     * too when the account's id is not known, Meta refuses, or the answer
     * says nothing this reads. A throttle or a server that failed is thrown.
     */
    suspend fun aboutProfile(handle: String): ThreadsProfileAbout? {
        val values = repairs()
        if (values.aboutProfileDocId.isEmpty()) return null
        val key = ThreadsLinks.normaliseHandle(handle) ?: return null
        val id = guestProfile(key).pk.takeIf { it.isNotEmpty() && it.all(Char::isDigit) } ?: return null
        val variables = ThreadsBundleReads.variables(values.aboutProfileVariable, id)
        val roots = bundleRead(values.aboutProfileDocId, ThreadsBundleReads.ABOUT_PROFILE, variables, profileUrl(key), provided = emptySet()) ?: return null
        return ThreadsBundleReads.about(roots)
    }

    /** Whether a guest's people search can be asked ([guestPeople]): only once that read's doc id is known. */
    val readsGuestPeople: Boolean get() = repairs().searchUsersDocId.isNotEmpty()

    /**
     * The accounts Threads' people search finds a guest for [query]
     * ([ThreadsBundleReads.SEARCH_USERS]), asked as its search page asks
     * it. Null, with nothing asked, while the read is off
     * ([readsGuestPeople]) or the words are blank; null too when Meta
     * refuses, and empty when its answer names no account. A throttle or a
     * server that failed is thrown. A read only: no one is followed.
     */
    suspend fun guestPeople(query: String): List<ThreadsProfile>? {
        val values = repairs()
        val words = query.trim().removePrefix("@").trim()
        if (values.searchUsersDocId.isEmpty() || words.isEmpty()) return null
        val variables = ThreadsBundleReads.people(values.searchUsersVariable, words)
        return people(bundleRead(values.searchUsersDocId, ThreadsBundleReads.SEARCH_USERS, variables, ThreadsLinks.searchUrl(words, tag = false, recent = false)))
    }

    /** Whether the accounts Threads recommends a guest can be asked ([recommendedPeople]): only once that read's doc id is known. */
    val readsRecommendedPeople: Boolean get() = repairs().recommendedUsersDocId.isNotEmpty()

    /**
     * The accounts Threads recommends a guest on its search page
     * ([ThreadsBundleReads.RECOMMENDED_USERS]). Empty, with nothing asked,
     * while the read is off ([readsRecommendedPeople]); empty too when Meta
     * refuses or fails, since a suggestion is never worth an error.
     * Remembered as the trends are. A read only: no one is followed.
     */
    suspend fun recommendedPeople(): List<ThreadsProfile> {
        val docId = repairs().recommendedUsersDocId
        if (docId.isEmpty()) return emptyList()
        synchronized(keeping) { recommendedRead?.takeIf { (at, _) -> clock() - at < TRENDS_TTL_MILLIS }?.second }?.let { return it }
        return recommendedReads.run(RECOMMENDED) {
            try {
                people(bundleRead(docId, ThreadsBundleReads.RECOMMENDED_USERS, ThreadsBundleReads.people(null, null), ThreadsLinks.web("/search"))).orEmpty()
                    .also { found -> if (found.isNotEmpty()) synchronized(keeping) { recommendedRead = clock() to found } }
            } catch (e: Exception) {
                if (!e.isReadFailure()) throw e
                emptyList()
            }
        }
    }

    /** The accounts a people read's answers name, their ids remembered; null when Meta refused. */
    private suspend fun people(roots: List<JsonElement>?): List<ThreadsProfile>? {
        val found = withContext(Dispatchers.Default) { roots?.let(ThreadsGuestPeople::users) } ?: return null
        found.forEach { rememberUserId(it.username.lowercase(), it.pk) }
        return found
    }

    /** The trends read in the last [TRENDS_TTL_MILLIS], asking Meta nothing; null when there are none that fresh. */
    fun knownTrends(): List<ThreadsTrend>? = synchronized(keeping) { trendsRead?.takeIf { (at, _) -> clock() - at < TRENDS_TTL_MILLIS }?.second }

    private fun isPrivate(page: String, handle: String): Boolean =
        ThreadsProfileOwner.enrich(ThreadsPages.guestProfile(page, handle), page, handle)?.isPrivate == true

    /**
     * A profile's page, read once even when its card and its threads are asked
     * for together. A screen's read never waits on Home's queued one: each
     * lane has its own, and the request itself is shared once it has left.
     */
    private suspend fun profilePage(handle: String): String {
        val key = handle.trim().lowercase()
        val lane = if (coroutineContext[ThreadsBackground.Key] != null) "background:" else "shown:"
        return profilePages.run(lane + key) { guestPage(profileUrl(key)).also { rememberProfileNext(key, it) } }
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
        val response = sendGuest("GET $url", url) { followed(url, headers) }
        return when (response.code) {
            200 -> response.body.also {
                rememberGuestLsd(ThreadsPages.lsd(it))
                rememberProviders(it)
            }
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
        return sendGuest("POST $url $body", url) { http.post(url, headers, body, FORM) }
    }

    /**
     * The page after [cursor] of the list [page] shows, as threads.com's
     * client asks it, its posts read by [read]. A throttle or a server that
     * failed is thrown, to be retried; an answer in a shape not understood
     * (a refusal, HTML, errors and nothing else) is [ThreadsMore.refused],
     * and the list's rest is left to threads.com. Where the list's own query
     * is refused or brings nothing and names no page after, the list's
     * [refetch] query is asked for the same page once, and what it brings is
     * the page; when it brings nothing either, the answer is the first one.
     */
    private suspend fun <T> more(cursor: String, page: HttpUrl, refetch: ThreadsRefetch? = null, read: (List<JsonElement>) -> List<T>): ThreadsMore<T> {
        val asked = ThreadsNextPage.decode(cursor) ?: return ThreadsMore(emptyList(), null, refused = true)
        val lsd = freshGuestLsd() ?: ThreadsPages.lsd(guestPage(page)) ?: return ThreadsMore(emptyList(), null, refused = true)
        val own = morePage(asked, lsd, page, read)
        if (own.items.isNotEmpty() || own.next != null) return own
        val other = refetched(asked, refetch) ?: return own
        val fallen = try {
            morePage(other, lsd, page, read)
        } catch (e: Exception) {
            // The fallback failing is no news: the list ends as it would have without it.
            if (!e.isReadFailure()) throw e
            return own
        }
        return if (fallen.items.isNotEmpty()) fallen else own
    }

    /** One next page asked and read: [asked]'s query with its cursor, a refetch query with Relay's provided variables beside its own. */
    private suspend fun <T> morePage(asked: ThreadsNextPage, lsd: String, page: HttpUrl, read: (List<JsonElement>) -> List<T>): ThreadsMore<T> {
        val refused = ThreadsMore<T>(emptyList(), null, refused = true)
        val variables = if (ThreadsRefetch.named(asked.query) == null) asked.requestVariables else ThreadsRelayProviders.with(asked.requestVariables, providers())
        val answers = answered(GuestQuery(asked.docId, asked.query, variables, GuestQuery.relayForm(asked.query)), lsd, page) ?: return refused
        val roots = answers.filter { (it as? JsonObject)?.get("data") is JsonObject }
        if (roots.isEmpty()) return refused
        val items = withContext(Dispatchers.Default) { read(roots) }
        val next = roots.firstNotNullOfOrNull { ThreadsNextPages.after(asked, it) }?.encoded()
        val failed = items.isEmpty() && next == null && answers.any { (it as? JsonObject)?.get("errors") is JsonArray }
        return ThreadsMore(items, next, refused = failed)
    }

    /**
     * The same page as [asked], asked by [refetch]'s query instead: null when
     * the list has none, its doc id is empty ([ThreadsRepairs.refetchDocId]),
     * or [asked] is that query already.
     */
    private fun refetched(asked: ThreadsNextPage, refetch: ThreadsRefetch?): ThreadsNextPage? {
        if (refetch == null || asked.query == refetch.query) return null
        val docId = repairs().refetchDocId(refetch).ifEmpty { return null }
        return asked.copy(query = refetch.query, docId = docId)
    }

    /** [refetch] as a first page's fallback ([ThreadsNextPages.first]), started from [id] where the page preloaded no variables. */
    private fun refetchBy(refetch: ThreadsRefetch, id: () -> String?) =
        ThreadsRefetchBy(refetch.query, repairs().refetchDocId(refetch)) { refetch.seed(id()) }

    private fun providers(): Map<String, Boolean> = synchronized(relayProviders) { relayProviders.toMap() }

    private fun rememberProviders(page: String) {
        val named = ThreadsRelayProviders.read(page)
        if (named.isNotEmpty()) synchronized(relayProviders) { relayProviders.putAll(named) }
    }

    /** Keeps where each post of [threads] sits, for [deeperReplies]: the post whose page carried it and its thread's token. */
    private fun rememberReplyPlaces(threads: List<ThreadsReplyThread>, permalink: String?) {
        if (threads.isEmpty()) return
        synchronized(replyPlaces) {
            threads.forEach { thread -> thread.posts.forEach { replyPlaces[it.id] = ReplyPlace(permalink, thread.pagingToken) } }
            while (replyPlaces.size > REPLY_PLACES_KEPT) replyPlaces.remove(replyPlaces.keys.first())
        }
    }

    /**
     * What guest GraphQL answered [query], asked as the page at [page] asks
     * it: its JSON objects, none when it is not JSON. A throttle or a server
     * that failed is thrown; any other refusal is null.
     */
    private suspend fun answered(query: GuestQuery, lsd: String, page: HttpUrl): List<JsonElement>? {
        val referer = page.encodedPath + (page.encodedQuery?.let { "?$it" } ?: "")
        val url = ThreadsLinks.web(GRAPHQL)
        val response = guestQuery(query, lsd, referer)
        return when {
            response.code == 429 -> throw guestThrottled(url)
            response.code >= 500 -> throw ThreadsException(UNREACHABLE, "$url: ${response.code}", response.code)
            response.code != 200 -> null
            else -> ThreadsNextPages.answer(response.body)
        }
    }

    /**
     * A read [ThreadsBundleReads] names, asked as the page at [page] asks it:
     * the answers that carry `data`. Null, with nothing asked, while its
     * [docId] is empty ([ThreadsRepairs.WAITING]); null too when the page
     * gives no token (with [readPage] false a token is never fetched for
     * it) or Meta refuses. Relay's provided variables go with [variables]
     * ([ThreadsRelayProviders]): every one seen, or only those of
     * [provided] for a query whose bundle says which it declares.
     */
    private suspend fun bundleRead(
        docId: String,
        name: String,
        variables: JsonObject,
        page: HttpUrl,
        readPage: Boolean = true,
        provided: Set<String>? = null,
    ): List<JsonElement>? {
        if (docId.isEmpty()) return null
        val lsd = freshGuestLsd() ?: (if (readPage) ThreadsPages.lsd(guestPage(page)) else null) ?: return null
        val sent = ThreadsRelayProviders.with(variables, providers(), provided)
        return answered(GuestQuery(docId, name, sent, GuestQuery.relayForm(name)), lsd, page)
            ?.filter { (it as? JsonObject)?.get("data") is JsonObject }
            ?.ifEmpty { null }
    }

    /** The trends guest GraphQL names when the search page carried none; none while the read is off, without a token, or refused. */
    private suspend fun askedTrends(): List<ThreadsTrend> = try {
        bundleRead(repairs().trendingTopicsDocId, ThreadsBundleReads.TRENDING_TOPICS, JsonObject(emptyMap()), ThreadsLinks.web("/search"), readPage = false, provided = ThreadsBundleReads.TRENDING_PROVIDED)
            ?.let(ThreadsTrends::fromRoots).orEmpty()
    } catch (e: Exception) {
        if (!e.isReadFailure()) throw e
        emptyList()
    }

    private fun rememberProfileNext(handle: String, page: String) {
        val refetch = refetchBy(ThreadsRefetch.PROFILE_THREADS) { ThreadsPages.userId(page, handle) }
        val next = ThreadsNextPages.first(page, refetch) { "ThreadsTab" in it.query.orEmpty() }?.encoded()
        synchronized(profileCursors) {
            profileCursors.remove(handle)
            profileCursors[handle] = next
            while (profileCursors.size > CONVERSATIONS_KEPT) profileCursors.remove(profileCursors.keys.first())
        }
    }

    private fun isFeed(info: ThreadsPageInfo): Boolean = "Feed" in info.query.orEmpty()

    /** A session read: paced, refused while the session cools down, and checked for what Meta said. */
    private suspend fun sessionRead(url: HttpUrl, headers: Map<String, String>): J {
        val response = sendSession(url) { http.get(url, headers, followRedirects = true) }
        checkSession(response, url)
        return decodeJson(response, url)
    }

    /** A guest request, shared with an identical one another screen sends or was just answered ([ThreadsGuestAnswers]). */
    private suspend fun sendGuest(key: String, url: HttpUrl, request: suspend () -> XResponse): XResponse {
        val background = coroutineContext[ThreadsBackground.Key] != null
        return guestAnswers.send(key, background, depart = { departInTurn(asGuest = true) }) {
            withTimeoutOrNull(GUEST_TIMEOUT_MILLIS) { request() } ?: throw SocketTimeoutException("$url: no answer in ${GUEST_TIMEOUT_MILLIS / 1000} s")
        }
    }

    /** A session request: paced at the session's own gap, never shared or reused. */
    private suspend fun sendSession(url: HttpUrl, request: suspend () -> XResponse): XResponse {
        departInTurn(asGuest = false)
        return withTimeoutOrNull(TIMEOUT_MILLIS) { request() } ?: throw SocketTimeoutException("$url: no answer in ${TIMEOUT_MILLIS / 1000} s")
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

    private fun rememberedCard(handle: String): ThreadsProfile? = synchronized(profileCards) {
        profileCards[handle]?.takeIf { (at, _) -> clock() - at < CARD_TTL_MILLIS }?.second
    }

    private fun rememberCard(handle: String, profile: ThreadsProfile) {
        synchronized(profileCards) {
            profileCards.remove(handle)
            profileCards[handle] = clock() to profile
            while (profileCards.size > CONVERSATIONS_KEPT) profileCards.remove(profileCards.keys.first())
        }
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

        /** How long a profile's card is reused: as long as its threads ([ThreadsReader.CACHE_TTL_MILLIS]). */
        private const val CARD_TTL_MILLIS = 10 * 60 * 1000L
        private const val TRENDS = "trends"
        private const val RECOMMENDED = "recommended"
        private const val REPLY_PLACES_KEPT = 2_000

        /** How long Meta's trending topics are kept: they move through the day, not by the minute. */
        internal const val TRENDS_TTL_MILLIS = 30 * 60 * 1000L

        /** How many handles' ids are remembered: the ones learnt last, so a reader's searches never grow the settings without end. */
        internal const val USER_IDS_KEPT = 400

        private fun newDeviceId(): String {
            val random = SecureRandom()
            fun hex(bytes: Int) = ByteArray(bytes).also(random::nextBytes).joinToString("") { "%02x".format(it) }
            return "${hex(4)}-${hex(2)}-${hex(2)}-${hex(2)}-${hex(6)}"
        }
    }
}

/** Where a reply read sits: the media id of the post whose page carried it, and its thread's paging token, each when known. */
private data class ReplyPlace(val permalink: String?, val pagingToken: String?)

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
