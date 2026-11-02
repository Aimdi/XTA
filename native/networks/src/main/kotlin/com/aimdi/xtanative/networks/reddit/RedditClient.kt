package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.Page
import com.aimdi.xtanative.networks.reddit.RedditHosts.OAUTH
import com.aimdi.xtanative.networks.reddit.RedditHosts.OLD
import com.aimdi.xtanative.networks.reddit.RedditHosts.WWW
import com.aimdi.xtanative.networks.reddit.RedditHosts.WWW_PIECES
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/**
 * How a read reaches Reddit (XTA's `RedditReadSession`). With nothing set it
 * reads what Reddit serves a reader without an account. A [clientId] switches
 * to app-only API reads with Reddit's documented rate limits; a [userToken],
 * from a sign-in, reads as that account. [preferPublic] keeps the
 * account-free route even when a credential is there.
 */
data class RedditSession(
    val clientId: String = "",
    val userToken: String? = null,
    val preferPublic: Boolean = false,
) {
    /** Reads with no credential at all: asked for, or nothing to read with. */
    val anonymous: Boolean get() = preferPublic || (userToken == null && clientId.isBlank())

    companion object {
        val ANONYMOUS = RedditSession()
    }
}

/**
 * The reads XTA Native asks of Reddit (XTA's `RedditClient`). Read-only:
 * nothing is posted, voted on, saved or subscribed to on Reddit.
 *
 * Without an account it reads www.reddit.com, the part of Reddit still
 * served to a reader without one; old.reddit's HTML and the public `.json`
 * endpoints are tried after it, and the route that answered is asked first
 * next time. With a client id or a sign-in it reads the API, and falls back
 * to the website where the API refuses a thread, a post or a search.
 *
 * A refusal is a [NetworkRequestException] whose code is Reddit's status, or
 * the one a refusal in the page itself stands for: 403 refused or blocked,
 * 404 not there (private, banned, missing), 429 throttled. No connection is
 * the [IOException] the [XHttp] threw.
 */
class RedditClient(
    http: XHttp,
    /** The browser the website is asked as: XTA's repairable `reddit.web_user_agent`. */
    webUserAgent: String = WEB_USER_AGENT,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val transport = RedditTransport(http, webUserAgent, clock)
    private val routes = PublicRoutes(clock)

    /** www.reddit.com's pages are a megabyte each, mostly styles: parsed two at a time, so a refresh of many communities does not hold a dozen. */
    private val parses = Semaphore(2)

    /** Community pictures noticed on pages fetched for another reason, so a feed rarely pays for its icons. */
    private val icons = ConcurrentHashMap<String, String>()

    /** Whether an app-only token is cached and still good. */
    val hasToken: Boolean get() = transport.hasToken

    /** Drops the app-only token: the client id changed, or the reader who had it left. */
    fun forgetToken() = transport.forgetToken()

    /**
     * Confirms [clientId] buys a token, for a settings screen.
     *
     * @throws IllegalArgumentException when [clientId] is blank.
     */
    suspend fun verify(clientId: String): Boolean = reading {
        forgetToken()
        transport.authorize(clientId)
        true
    }

    /**
     * A page of [subreddit]'s posts (XTA's `fetchSubreddit`); `popular` and
     * `all` are Reddit's site-wide rails. [after] is the [Page.next] of the
     * page before. A private, banned or missing community is a 404, a
     * quarantined one a 403; an empty one is an empty page.
     */
    suspend fun listing(
        subreddit: String,
        sort: RedditSort = RedditSort.HOT,
        timeFilter: RedditTimeFilter = RedditTimeFilter.DAY,
        after: String? = null,
        limit: Int = PAGE_SIZE,
        session: RedditSession = RedditSession.ANONYMOUS,
    ): Page<RedditPost> = reading {
        val name = normaliseSubreddit(subreddit) ?: throw notFound("Not a subreddit name: $subreddit")
        val query = buildMap {
            put("limit", "$limit")
            // Real characters instead of HTML entities in titles and text.
            put("raw_json", "1")
            if (sort.usesTimeFilter) put("t", timeFilter.query)
            if (!after.isNullOrEmpty()) put("after", after)
        }
        if (session.anonymous) {
            publicListing(name, sort, query)
        } else {
            val token = transport.token(session)
            listingOf(transport.read(redditUrl("$OAUTH/r/$name/${sort.path}", query), token))
        }
    }

    /**
     * Routes are tried most likely first, and the first that answers ends the
     * search, so the common case costs one request. A verdict (private,
     * banned, quarantined, missing) ends it too: no other host will disagree.
     * A login page is no verdict: it answers for its host alone.
     */
    private suspend fun publicListing(name: String, sort: RedditSort, query: Map<String, String>): Page<RedditPost> {
        var worst: Exception? = null
        for (route in routes.inOrder()) {
            when (val attempt = attempt(route, name, sort, query)) {
                is RouteAttempt.Hit -> return attempt.page.also { routes.remember(route) }
                is RouteAttempt.Verdict -> throw attempt.failure.also { routes.remember(route) }
                is RouteAttempt.Miss -> {
                    routes.refuse(route)
                    worst = moreTelling(worst, attempt.failure)
                }
            }
        }
        throw publicFailure(worst, name)
    }

    /** One route's turn: only what the answer says comes back; a failed connection is a miss like a refusal. */
    private suspend fun attempt(route: PublicRoute, name: String, sort: RedditSort, query: Map<String, String>): RouteAttempt =
        orElse({
            when (route) {
                PublicRoute.WWW -> wwwListing(name, sort, query)
                PublicRoute.HTML -> scrapedListing(name, sort, query)
                PublicRoute.WWW_JSON -> publicJsonListing(WWW, name, sort, query)
                PublicRoute.OLD_JSON -> publicJsonListing(OLD, name, sort, query)
            }
        }) { RouteAttempt.Miss(it) }

    /** A community's posts from the piece of www.reddit.com that loads them. */
    private suspend fun wwwListing(name: String, sort: RedditSort, query: Map<String, String>): RouteAttempt {
        val url = redditUrl("$WWW_PIECES/community-more-posts/${sort.path}/", wwwFeedQuery(name, query["t"], query["after"]))
        val response = transport.read(url)
        if (response.code != 200) return RouteAttempt.Miss(transport.failureOf(response, url))
        val page = wwwParse(response.body, RedditWww::listing)
        return if (page.kind == WwwPageKind.READ) RouteAttempt.Hit(Page(page.posts, page.after)) else RouteAttempt.Miss(wwwRefusal(page.kind, url))
    }

    /** A feed piece's query: whose, which window, and after which post, in the cursor form the site's own paging carries. */
    private fun wwwFeedQuery(name: String, window: String?, after: String?): Map<String, String?> = mapOf(
        "name" to name,
        "t" to window?.uppercase(),
        "after" to after?.ifEmpty { null }?.let(RedditWww::cursorParam),
    )

    /**
     * A listing off old.reddit's HTML. The over-18 gate is answered with the
     * cookie its own form sets: Reddit wants consent recorded, not an
     * account, and the cookie is kept for the rest of the session.
     */
    private suspend fun scrapedListing(name: String, sort: RedditSort, query: Map<String, String>): RouteAttempt {
        val url = redditUrl("$OLD/r/$name/${sort.path}", query - "raw_json")
        val first = transport.read(url)
        statusVerdict(first, name, url)?.let { return it }
        val firstPage = RedditOldListing.page(first.body)
        val (response, page) = if (firstPage.kind == OldPageKind.OVER18_GATE) {
            transport.read(url, consent = OVER18).let { it to RedditOldListing.page(it.body) }
        } else {
            first to firstPage
        }
        rememberIcon(name, page.icon)
        pageVerdict(page.kind, name)?.let { return it }
        return if (response.code == 200 && page.kind == OldPageKind.LISTING) {
            RouteAttempt.Hit(Page(page.posts, page.after))
        } else {
            RouteAttempt.Miss(transport.failureOf(response, url))
        }
    }

    /** The `.json` listings, in case their shutdown is not total for this reader. */
    private suspend fun publicJsonListing(base: String, name: String, sort: RedditSort, query: Map<String, String>): RouteAttempt {
        val url = redditUrl("$base/r/$name/${sort.path}.json", query)
        val response = transport.read(url)
        statusVerdict(response, name, url)?.let { return it }
        if (response.code != 200) return RouteAttempt.Miss(transport.failureOf(response, url))
        return RouteAttempt.Hit(listingOf(response))
    }

    /**
     * What a status alone settles, or null when the body must be read. A
     * redirect to the login page refuses this host, not the community; 403
     * carries the private and quarantined pages, so its body is read.
     */
    private fun statusVerdict(response: XResponse, name: String, url: HttpUrl): RouteAttempt? {
        if (response.code == 404) return RouteAttempt.Verdict(notFound("HTTP 404 from ${url.host}: no r/$name"))
        val login = response.header("location")?.takeIf { response.code in 300..399 && "/login" in it }
        if (login != null) return RouteAttempt.Miss(blocked("HTTP ${response.code} to a login page ($login)"))
        return if (response.code == 200 || response.code == 403) null else RouteAttempt.Miss(transport.failureOf(response, url))
    }

    /** The verdict a page carries, or null when it is a listing to be used. */
    private fun pageVerdict(kind: OldPageKind, name: String): RouteAttempt? = when (kind) {
        OldPageKind.PRIVATE -> RouteAttempt.Verdict(notFound("r/$name is private"))
        OldPageKind.BANNED -> RouteAttempt.Verdict(notFound("r/$name has been banned"))
        OldPageKind.QUARANTINED -> RouteAttempt.Verdict(blocked("r/$name is quarantined"))
        OldPageKind.LOGIN_WALL -> RouteAttempt.Miss(blocked("Reddit answered with a login page"))
        OldPageKind.LISTING, OldPageKind.OVER18_GATE, OldPageKind.UNREADABLE -> null
    }

    /** Which of two failures is worth reporting: a throttle and a refusal say more than a reshaped page. */
    private fun moreTelling(a: Exception?, b: Exception?): Exception? = if (rank(b) > rank(a)) b else a

    private fun rank(failure: Exception?): Int = when {
        failure == null -> 0
        failure is NetworkRequestException && failure.code == 429 -> 4
        failure is NetworkRequestException && failure.code == 403 -> 3
        failure is IOException -> 2
        else -> 1
    }

    /** What to report when no route served a listing: what actually happened, which usually passes. */
    private fun publicFailure(worst: Exception?, name: String): Exception {
        worst ?: return unreadable("No public route served r/$name")
        val code = (worst as? NetworkRequestException)?.code?.takeIf { it == 403 || it == 429 }
        return if (code != null && worst.message.orEmpty().startsWith("HTTP $code")) refused(code, "HTTP $code from both public hosts") else worst
    }

    /**
     * What [subreddit] says about itself (XTA's `fetchSubredditAbout`): its
     * `about.json` over the API; without an account, what www.reddit.com's
     * community search says, then the public `about.json`, then the
     * old.reddit sidebar. The search shows no subscriber count.
     */
    suspend fun about(subreddit: String, session: RedditSession = RedditSession.ANONYMOUS): RedditSubredditAbout = reading {
        val name = normaliseSubreddit(subreddit) ?: throw notFound("Not a subreddit name: $subreddit")
        if (session.anonymous) {
            aboutFromWww(name) ?: aboutFromPublicJson(name) ?: aboutFromSidebar(name)
        } else {
            val token = transport.token(session)
            aboutOf(transport.objectOf(transport.read(redditUrl("$OAUTH/r/$name/about.json", RAW_JSON), token))["data"], name)
        }
    }

    private fun aboutOf(data: J, name: String) = RedditJson.about(data, name).also { rememberIcon(name, it.iconUrl) }

    private suspend fun aboutFromWww(name: String): RedditSubredditAbout? {
        val found = recovering { wwwSearch(wwwSearchUrl(name, "communities"), RedditWwwSearch::communities) } ?: return null
        val match = found.firstOrNull { it.name.equals(name, ignoreCase = true) } ?: return null
        rememberIcon(name, match.iconUrl)
        return RedditSubredditAbout(name = match.name, description = match.description, iconUrl = match.iconUrl)
    }

    /** The account-free `about.json`; www is often refused, and old.reddit is a second door onto the same JSON. */
    private suspend fun aboutFromPublicJson(name: String): RedditSubredditAbout? = listOf(WWW, OLD).firstNotNullOfOrNull { base ->
        recovering {
            val response = transport.read(redditUrl("$base/r/$name/about.json", RAW_JSON))
            if (response.code != 200) null else transport.objectOf(response)["data"].takeIf { it.exists }?.let { aboutOf(it, name) }
        }
    }

    private suspend fun aboutFromSidebar(name: String): RedditSubredditAbout {
        val body = scrape(redditUrl("$OLD/r/$name/", mapOf("limit" to "1"))) ?: throw unreadable("No sidebar for r/$name")
        val side = RedditOldListing.sidebar(body)
        val icon = RedditOldListing.subredditIcon(body)
        rememberIcon(name, icon)
        return RedditSubredditAbout(name, side.title, side.description, side.subscribers, side.activeUsers, iconUrl = icon)
    }

    /**
     * [subreddit]'s own picture, or null when it has none to find (XTA's
     * `fetchSubredditIcon`): one noticed before, its `about`, or its old.reddit
     * page. Never throws: a feed must not fail because a picture did.
     */
    suspend fun icon(subreddit: String, session: RedditSession = RedditSession.ANONYMOUS): String? = reading {
        val name = normaliseSubreddit(subreddit) ?: return@reading null
        icons[name.lowercase()]?.let { return@reading it.ifEmpty { null } }
        quietly { about(name, session).iconUrl }?.let { return@reading it }
        quietly {
            rememberIcon(name, scrape(redditUrl("$OLD/r/$name/", mapOf("limit" to "1")))?.let(RedditOldListing::subredditIcon))
            icons[name.lowercase()]
        }
    }

    /** Keeps [icon] as [subreddit]'s picture; `about.json`'s community icon is not replaced by an old header image. */
    private fun rememberIcon(subreddit: String, icon: String?) {
        val url = RedditMedia.communityIconUrl(icon, icon) ?: return
        icons.compute(subreddit.lowercase()) { _, existing ->
            if (existing != null && STYLES in existing && STYLES !in url) existing else url
        }
    }

    /**
     * Posts matching [query], across Reddit or within [subreddit]. The API
     * with an account; without one www.reddit.com's search, then the public
     * `.json`, then old.reddit's results. One page: XTA does not page search.
     */
    suspend fun searchPosts(
        query: String,
        subreddit: String? = null,
        sort: RedditSearchSort = RedditSearchSort.RELEVANCE,
        session: RedditSession = RedditSession.ANONYMOUS,
    ): Page<RedditPost> = reading {
        val name = subreddit?.let(::normaliseSubreddit)
        val params = buildMap {
            put("q", query)
            put("sort", sort.query)
            put("t", "all")
            put("raw_json", "1")
            put("type", "link")
            if (name != null) put("restrict_sr", "on")
        }
        val routes = SearchRoutes(
            path = if (name == null) "/search" else "/r/$name/search",
            params = params,
            fromJson = RedditJson::searchPosts,
            fromHtml = RedditOldSearch::posts,
            wwwUrl = wwwSearchUrl(query, "posts", name, sort.query),
            fromWww = RedditWwwSearch::posts,
        )
        Page(search(routes, session), null)
    }

    /** Communities matching [query]. */
    suspend fun searchSubreddits(query: String, session: RedditSession = RedditSession.ANONYMOUS): Page<RedditSubredditResult> = reading {
        val routes = SearchRoutes(
            path = "/subreddits/search",
            params = mapOf("q" to query, "raw_json" to "1"),
            fromJson = RedditJson::searchSubreddits,
            fromHtml = RedditOldSearch::subreddits,
            wwwUrl = wwwSearchUrl(query, "communities"),
            fromWww = RedditWwwSearch::communities,
        )
        Page(search(routes, session), null)
    }

    /** Accounts matching [query]. */
    suspend fun searchUsers(query: String, session: RedditSession = RedditSession.ANONYMOUS): Page<RedditUserResult> = reading {
        val routes = SearchRoutes(
            path = "/users/search",
            params = mapOf("q" to query, "raw_json" to "1"),
            fromJson = RedditJson::searchUsers,
            fromHtml = RedditOldSearch::users,
            wwwUrl = wwwSearchUrl(query, "people"),
            fromWww = RedditWwwSearch::people,
            htmlPath = "/search",
            htmlParams = mapOf("q" to query, "type" to "user"),
        )
        Page(search(routes, session), null)
    }

    /** Where one search is asked on each host, and how each answer is read. */
    private class SearchRoutes<T>(
        val path: String,
        val params: Map<String, String>,
        val fromJson: (J) -> List<T>,
        val fromHtml: (String) -> List<T>,
        val wwwUrl: HttpUrl,
        val fromWww: (String) -> List<T>,
        val htmlPath: String = path,
        val htmlParams: Map<String, String> = params - "raw_json",
    )

    /** www.reddit.com's search for [type] (`posts`, `communities`, `people`), across Reddit or within [subreddit]. */
    private fun wwwSearchUrl(query: String, type: String, subreddit: String? = null, sort: String? = null): HttpUrl {
        val base = if (subreddit == null) "$WWW_PIECES/search/" else "$WWW_PIECES/r/$subreddit/search/"
        return redditUrl(base, buildMap {
            put("q", query)
            put("type", type)
            if (sort != null) {
                put("sort", sort)
                put("t", "all")
            }
            if (subreddit != null) put("restrict_sr", "1")
        })
    }

    private suspend fun <T> search(routes: SearchRoutes<T>, session: RedditSession): List<T> {
        if (!session.anonymous) {
            val token = transport.token(session)
            // The public hosts still answer when the API is flaky.
            recovering { routes.fromJson(transport.objectOf(transport.read(redditUrl("$OAUTH${routes.path}", routes.params), token))) }
                ?.let { return it }
        }
        return publicSearch(routes)
    }

    /** www's search, then the public `.json`, then old.reddit's page; www's failure is the one reported. */
    private suspend fun <T> publicSearch(routes: SearchRoutes<T>): List<T> {
        val wwwFailure = orElse({ return wwwSearch(routes.wwwUrl, routes.fromWww) }) { it }
        for (base in listOf(WWW, OLD)) {
            recovering {
                val response = transport.read(redditUrl("$base${routes.path}.json", routes.params))
                if (response.code == 200) routes.fromJson(transport.objectOf(response)) else null
            }?.let { return it }
        }
        // old.reddit's login page has no results on it either.
        recovering { scrape(redditUrl("$OLD${routes.htmlPath}", routes.htmlParams))?.let(routes.fromHtml).orEmpty() }
            ?.takeIf { it.isNotEmpty() }?.let { return it }
        throw wwwFailure
    }

    private suspend fun <T> wwwSearch(url: HttpUrl, read: (String) -> List<T>): List<T> {
        val response = transport.read(url)
        if (response.code != 200) throw transport.failureOf(response, url)
        val kind = RedditWww.pageKind(response.body)
        if (kind != WwwPageKind.READ) throw wwwRefusal(kind, url)
        return wwwParse(response.body, read)
    }

    /**
     * A page of [user]'s posts, newest first (XTA's `fetchUserPosts`):
     * www.reddit.com's profile, and old.reddit's where that is still served.
     */
    suspend fun userPosts(user: String, after: String? = null, limit: Int = PAGE_SIZE): Page<RedditPost> = reading {
        val name = user.replace(userPrefix, "").trim().ifEmpty { throw notFound("Not a username: $user") }
        orElse({ userPostsFromWww(name, after) }) { www ->
            recovering { userPostsFromOldReddit(name, after, limit) }?.takeIf { it.items.isNotEmpty() } ?: throw www
        }
    }

    private suspend fun userPostsFromWww(name: String, after: String?): Page<RedditPost> {
        val url = redditUrl("$WWW_PIECES/profiles/profile_posts-more-posts/new/", wwwFeedQuery(name, null, after))
        val response = transport.read(url)
        if (response.code != 200) throw transport.failureOf(response, url)
        val page = wwwParse(response.body, RedditWww::listing)
        if (page.kind != WwwPageKind.READ) throw wwwRefusal(page.kind, url)
        return Page(page.posts, page.after)
    }

    /** The account's comments share the page, have no title, and are skipped by the listing reader, as wanted. */
    private suspend fun userPostsFromOldReddit(name: String, after: String?, limit: Int): Page<RedditPost> {
        val url = OLD.toHttpUrl().newBuilder().addPathSegment("user").addPathSegment(name).addPathSegment("submitted")
            .addQueryParameter("limit", "$limit")
            .apply { if (!after.isNullOrEmpty()) addQueryParameter("after", after) }
            .build()
        val body = scrape(url) ?: throw notFound("No such account: $name")
        return RedditOldListing.page(body).let { Page(it.posts, it.after) }
    }

    /**
     * A post's comments (XTA's `fetchComments`), with the post's own words and
     * pictures when its page has them. The API with an account, falling back
     * to the website when it refuses; www.reddit.com's comments piece without
     * one, then old.reddit's page. A thread whose top comments Reddit held
     * back ends with a stub whose [RedditComment.nextPage] [moreComments] loads.
     */
    suspend fun thread(
        permalink: String,
        sort: RedditCommentSort = RedditCommentSort.BEST,
        session: RedditSession = RedditSession.ANONYMOUS,
    ): RedditThread = reading {
        if (session.anonymous) {
            threadWithoutAccount(permalink, sort)
        } else {
            orElse({ threadFromApi(permalink, sort, transport.token(session)) }) { threadWithoutAccount(permalink, sort) }
        }
    }

    /** old.reddit answers a reader it will not serve with its login page, which holds no comments: then www's failure is the one to report. */
    private suspend fun threadWithoutAccount(permalink: String, sort: RedditCommentSort): RedditThread =
        orElse({ threadFromWww(permalink, sort) }) { www ->
            if (www is NetworkRequestException && www.code == 404) throw www
            recovering { threadFromOldReddit(permalink, sort) }?.takeIf { it.comments.isNotEmpty() || it.selfText != null } ?: throw www
        }

    /** It names the post rather than the page and carries no post, so the post's words and media are what the listing had. */
    private suspend fun threadFromWww(permalink: String, sort: RedditCommentSort): RedditThread {
        val url = RedditLinks.wwwCommentsUrl(permalink, sort) ?: throw notFound("Not a post: $permalink")
        val response = transport.read(url)
        if (response.code != 200) throw transport.failureOf(response, url)
        val thread = wwwParse(response.body, RedditWwwComments::thread)
        if (thread.kind != WwwPageKind.READ) throw wwwRefusal(thread.kind, url)
        return RedditThread(thread.comments + listOfNotNull(moreStub(permalink, thread.next).takeIf { thread.moreAtRoot }))
    }

    /**
     * The next comments at a thread's top, for the stub [thread] or a former
     * call ended with: asked for as www.reddit.com's own page asks, by POST
     * with the cursor and the CSRF token the site set as a cookie. They end
     * with a stub of their own when Reddit holds back more still. Throws when
     * Reddit will not serve them; the post's page on Reddit is the way on then.
     */
    suspend fun moreComments(stub: RedditComment): List<RedditComment> = reading {
        val next = stub.nextPage
        val permalink = stub.permalink
        if (next == null || permalink == null) throw notFound("Reddit named no further comments")
        val url = "$WWW${next.path}".toHttpUrlOrNull() ?: throw notFound("Reddit named no further comments")
        val form = listOfNotNull("cursor" to next.cursor, transport.cookie("csrf_token")?.let { "csrf_token" to it }).toMap()
        val response = transport.submit(url, form)
        if (response.code != 200) throw transport.failureOf(response, url)
        val page = wwwParse(response.body, RedditWwwComments::more)
        if (page.comments.isEmpty()) throw wwwRefusal(page.kind, url)
        page.comments + listOfNotNull(moreStub(permalink, page.next).takeIf { page.moreAtRoot })
    }

    /** The row for comments held back at a thread's top: it loads them where Reddit said they are, or opens the post when it did not say. */
    private fun moreStub(permalink: String, next: RedditCommentsCursor?) =
        RedditComment(id = "more-$permalink-${next?.cursor.orEmpty()}", body = "", permalink = permalink, moreCount = -1, nextPage = next)

    private suspend fun threadFromApi(permalink: String, sort: RedditCommentSort, token: String): RedditThread {
        val url = redditUrl("$OAUTH${RedditLinks.commentsJsonPath(permalink)}", threadQuery(sort))
        return RedditJson.thread(transport.arrayOf(transport.read(url, token)), permalink)
    }

    /** One page is all a thread gets, so a deep one is asked for: truncation the exception rather than every big thread's end. */
    private fun threadQuery(sort: RedditCommentSort) = mapOf("raw_json" to "1", "limit" to "500", "sort" to sort.query.ifEmpty { null })

    private suspend fun threadFromOldReddit(permalink: String, sort: RedditCommentSort): RedditThread {
        val url = "$OLD${if (permalink.startsWith("/")) permalink else "/$permalink"}".toHttpUrlOrNull()?.newBuilder()?.query(null)
            ?.addQueryParameter("limit", "500")
            ?.apply { if (sort.query.isNotEmpty()) addQueryParameter("sort", sort.query) }
            ?.build() ?: throw notFound("Not a post: $permalink")
        val first = transport.read(url)
        val response = if (first.code == 200 && RedditOldListing.isOver18Gate(first.body)) transport.read(url, consent = OVER18) else first
        if (response.code == 404) throw notFound("No such post: $permalink")
        if (response.code != 200) throw transport.failureOf(response, url)
        val media = RedditOldComments.postMedia(response.body)
        val scraped = RedditThread(
            comments = RedditOldComments.comments(response.body, permalink),
            selfText = RedditOldComments.selfText(response.body),
            postUrl = media.url,
            postImages = media.images,
        )
        // old.reddit sometimes serves a thread without its comment markup; the public JSON still carries the tree.
        return if (scraped.comments.isNotEmpty()) scraped else recovering { threadFromPublicJson(permalink, sort) } ?: scraped
    }

    private suspend fun threadFromPublicJson(permalink: String, sort: RedditCommentSort): RedditThread {
        var last: Exception? = null
        for (base in listOf(WWW, OLD)) {
            val url = redditUrl("$base${RedditLinks.commentsJsonPath(permalink)}", threadQuery(sort))
            try {
                val response = transport.read(url)
                if (response.code == 200) return RedditJson.thread(transport.arrayOf(response), permalink)
                last = transport.failureOf(response, url)
            } catch (e: NetworkRequestException) {
                last = e
            } catch (e: IOException) {
                last = e
            }
        }
        throw last ?: unreadable("No comments: $permalink")
    }

    /**
     * A post on its own (XTA's `fetchPost`), for one that arrived without its
     * words or pictures: from search, which lists none, or from a link, which
     * names only an id. The API's `by_id` with an account; the post's embed
     * card without one or when the API refuses. Null when neither has it, as
     * for an NSFW post whose card Reddit refuses. Never throws: this fills in
     * a post already open.
     */
    suspend fun post(permalink: String, session: RedditSession = RedditSession.ANONYMOUS): RedditPost? = reading {
        val fromApi = if (session.anonymous) null else recovering { postFromApi(permalink, transport.token(session)) }
        fromApi ?: postFromEmbed(permalink)
    }

    private suspend fun postFromApi(permalink: String, token: String): RedditPost? {
        val id = RedditLinks.postPageOf(permalink)?.post ?: return null
        return listingOf(transport.read(redditUrl("$OAUTH/by_id/t3_$id", RAW_JSON), token)).items.firstOrNull()
    }

    private suspend fun postFromEmbed(permalink: String): RedditPost? {
        val url = RedditLinks.wwwEmbedUrl(permalink) ?: return null
        return recovering {
            val response = transport.read(url)
            if (response.code != 200) null else wwwParse(response.body) { RedditWwwEmbed.post(it, permalink) }
        }
    }

    /**
     * The pictures of a gallery post, from the post's own public JSON, for a
     * gallery an old.reddit listing carried as a link and a thumbnail. Never
     * throws: a refusal costs that card its pictures, not the feed its posts.
     */
    suspend fun galleryImages(permalink: String): List<String> = reading {
        quietly {
            val response = transport.read(redditUrl("$WWW${RedditLinks.commentsJsonPath(permalink)}", RAW_JSON))
            if (response.code != 200) emptyList() else RedditJson.thread(transport.arrayOf(response)).postImages
        }.orEmpty()
    }

    /** One page of old.reddit with the over-18 gate answered, or null when even the answered gate is all it shows. */
    private suspend fun scrape(url: HttpUrl): String? {
        val first = transport.read(url)
        val response = if (first.code == 200 && RedditOldListing.isOver18Gate(first.body)) transport.read(url, consent = OVER18) else first
        if (response.code != 200) throw transport.failureOf(response, url)
        return response.body.takeUnless(RedditOldListing::isOver18Gate)
    }

    private fun listingOf(response: XResponse): Page<RedditPost> =
        RedditJson.listing(transport.objectOf(response)) ?: throw unreadable("Listing has no data", response.code)

    /** What a www.reddit.com page that was not the answer amounts to. */
    private fun wwwRefusal(kind: WwwPageKind, url: HttpUrl): NetworkRequestException = when (kind) {
        WwwPageKind.BLOCKED -> blocked("${url.host} blocked the request by network security")
        WwwPageKind.BROWSER_CHECK -> blocked("${url.host} answered with its browser check")
        else -> unreadable("${url.host} answered with a page this app cannot read")
    }

    /** [read] of a www.reddit.com page, cut to what a reader needs first, a couple at a time. */
    private suspend fun <T> wwwParse(body: String, read: (String) -> T): T = parses.withPermit { read(RedditWww.strip(body)) }

    /** Runs a read off the caller's thread: the pages are parsed where it runs. */
    private suspend fun <T> reading(block: suspend () -> T): T = withContext(Dispatchers.Default) { block() }

    companion object {
        /**
         * How many posts a listing page asks for: one number for every
         * surface, since a page fetched for one is handed to the next. Reddit
         * allows 100; 50 fills a timeline without starving the account-free
         * rate budget when several communities load at once.
         */
        const val PAGE_SIZE = 50

        /** For the API and its token endpoint: the shape Reddit's rules ask for, as XTA sends it. */
        const val API_USER_AGENT = "android:com.aimdi.xta:1.0 (read-only, account-free)"

        /** For the website, which turns away anything announcing itself as a bot: XTA's shipped `reddit.web_user_agent`. */
        const val WEB_USER_AGENT =
            "Mozilla/5.0 (Linux; Android 14; Mobile) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Mobile Safari/537.36"

        /** Reddit's documented value for "do not associate this with a device". */
        const val DEVICE_ID = "DO_NOT_TRACK_THIS_DEVICE"

        private val RAW_JSON = mapOf("raw_json" to "1")
        private val OVER18 = mapOf("over18" to "1")
        private const val STYLES = "styles.redditmedia.com"
        private val userPrefix = Regex("^/?u(?:ser)?/", RegexOption.IGNORE_CASE)
    }
}

/** What one route made of a listing request. */
internal sealed interface RouteAttempt {
    data class Hit(val page: Page<RedditPost>) : RouteAttempt

    /** An answer no other route will contradict. */
    data class Verdict(val failure: NetworkRequestException) : RouteAttempt

    /** A failure worth trying the next route after. */
    data class Miss(val failure: Exception) : RouteAttempt
}

/**
 * The ways to a listing that need no credential, in the order they are tried
 * before anything is known: www.reddit.com leads because it is what Reddit
 * still serves; old.reddit's HTML and the `.json` pages follow, since neither
 * 2026 shutdown has been total.
 */
internal enum class PublicRoute { WWW, HTML, WWW_JSON, OLD_JSON }

/**
 * Which account-free route answered last and which refused recently. Memory
 * only: which host Reddit serves today is worth nothing tomorrow.
 */
internal class PublicRoutes(private val clock: () -> Long) {
    @Volatile private var lastGood: PublicRoute? = null
    private val refusedUntil = ConcurrentHashMap<PublicRoute, Long>()

    /**
     * The routes to try, most likely first. One that refused recently goes
     * last rather than away: a refusal is a wait, not a verdict. When all of
     * them refused, only the one whose wait ends first is asked: asking every
     * route for every community is how one refresh becomes forty refusals.
     */
    fun inOrder(): List<PublicRoute> {
        val good = lastGood
        val ordered = listOfNotNull(good) + PublicRoute.entries.filter { it != good }
        val (fresh, refused) = ordered.partition { !isRefused(it) }
        return if (fresh.isEmpty()) listOf(ordered.minBy { refusedUntil[it] ?: 0L }) else fresh + refused
    }

    private fun isRefused(route: PublicRoute) = (refusedUntil[route] ?: 0L) > clock()

    fun refuse(route: PublicRoute) {
        refusedUntil[route] = clock() + COOLDOWN_MILLIS
    }

    fun remember(route: PublicRoute) {
        lastGood = route
        refusedUntil.remove(route)
    }

    companion object {
        /** Long enough that a refresh does not find the same refusal once per community, short enough that a passing throttle is forgotten. */
        const val COOLDOWN_MILLIS = 5 * 60 * 1000L
    }
}

/** [block]'s answer, or what [fallback] makes of the read failure it ended in. Anything else, cancellation included, goes on up. */
internal inline fun <T> orElse(block: () -> T, fallback: (Exception) -> T): T = try {
    block()
} catch (e: NetworkRequestException) {
    fallback(e)
} catch (e: IOException) {
    fallback(e)
}

/** [block]'s answer, or null when it ended in a read failure. */
internal inline fun <T> recovering(block: () -> T?): T? = orElse(block) { null }

/** [block]'s answer, or null when it failed in any way but being cancelled. */
internal inline fun <T> quietly(block: () -> T?): T? = try {
    block()
} catch (e: CancellationException) {
    throw e
} catch (e: Exception) {
    null
}
