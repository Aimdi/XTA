package com.aimdi.xtanative.networks.reddit

import com.aimdi.xtanative.core.x.http.XHttp
import com.aimdi.xtanative.core.x.http.XResponse
import okhttp3.HttpUrl

/** A page XTA's tests recorded from Reddit (test/fixtures/Reddit in the Flutter app). */
internal fun fixture(name: String): String =
    RedditTestSupport::class.java.getResource("/reddit/$name")!!.readText()

internal object RedditTestSupport

/** One request the client made. */
internal data class Asked(
    val method: String,
    val url: HttpUrl,
    val headers: Map<String, String>,
    val body: String? = null,
    val contentType: String? = null,
) {
    /** A piece of www.reddit.com's pages, the first route without an account. */
    val isWww: Boolean get() = url.encodedPath.startsWith("/svc/shreddit/")

    /** The decoded form fields of a POST body. */
    val form: Map<String, String>
        get() = body.orEmpty().split('&').filter { it.isNotEmpty() }
            .associate { java.net.URLDecoder.decode(it.substringBefore('='), Charsets.UTF_8) to java.net.URLDecoder.decode(it.substringAfter('='), Charsets.UTF_8) }
}

/** Reddit as a test answers it, keeping every request it was asked. */
internal class FakeReddit(private val answer: suspend (Asked) -> XResponse) : XHttp {
    private val log = mutableListOf<Asked>()

    val asked: List<Asked> get() = synchronized(log) { log.toList() }
    val urls: List<HttpUrl> get() = asked.map { it.url }
    val hosts: List<String> get() = asked.map { it.url.host }

    override suspend fun get(url: HttpUrl, headers: Map<String, String>, followRedirects: Boolean): XResponse =
        record(Asked("GET", url, headers))

    override suspend fun post(url: HttpUrl, headers: Map<String, String>, body: String, contentType: String): XResponse =
        record(Asked("POST", url, headers, body, contentType))

    private suspend fun record(asked: Asked): XResponse {
        synchronized(log) { log += asked }
        return answer(asked)
    }
}

internal fun page(body: String, code: Int = 200, headers: Map<String, List<String>> = emptyMap()) = XResponse(code, body, headers)

/** [answer] behind a www.reddit.com that refuses, which is how the routes after it are reached. */
internal fun wwwRefused(answer: suspend (Asked) -> XResponse): suspend (Asked) -> XResponse =
    { asked -> if (asked.isWww) page("", 403) else answer(asked) }

internal fun tokenBody(expiresIn: Int = 3600) = """{"access_token":"tok_123","token_type":"bearer","expires_in":$expiresIn,"scope":"*"}"""

internal const val DART_POST =
    """{"kind":"t3","data":{"id":"abc123","title":"Dart 4 is out","subreddit":"dartlang","author":"someone","score":412,""" +
        """"num_comments":37,"created_utc":1769000000,"permalink":"/r/dartlang/comments/abc123/dart_4_is_out/",""" +
        """"url":"https://dart.dev/blog","is_self":false,"over_18":false,"stickied":false,"thumbnail":"https://b.thumbs.redditmedia.com/x.jpg"}}"""

internal fun listingBody(after: String? = null, children: String = DART_POST) =
    """{"kind":"Listing","data":{"after":${after?.let { "\"$it\"" } ?: "null"},"children":[$children]}}"""

internal const val EMPTY_LISTING_JSON = """{"kind":"Listing","data":{"after":null,"children":[]}}"""

/** A listing shaped like old.reddit's: only the `data-*` attributes the reader uses. */
internal const val LISTING_HTML = """
<!doctype html><html><body><div class="content" role="main"><div id="siteTable">
  <div class=" thing id-t3_abc123 link " data-fullname="t3_abc123" data-subreddit="dartlang"
       data-author="someone" data-score="412" data-comments-count="37"
       data-timestamp="1769000000000" data-permalink="/r/dartlang/comments/abc123/dart_4_is_out/"
       data-url="https://dart.dev/blog" data-domain="dart.dev" data-nsfw="false">
    <a class="title" href="https://dart.dev/blog">Dart 4 is out</a>
  </div>
</div></div></body></html>
"""

/** Reddit's age gate, which wants a cookie rather than an account. */
internal const val OVER18_GATE = """
<!doctype html><html><body><div class="content">
  <form action="/over18?dest=%2Fr%2Fdartlang" method="post">
    <input type="hidden" name="over18" value="yes">
  </form>
</div></body></html>
"""

/** A community with nothing in it: the listing table, with no posts inside. */
internal const val EMPTY_LISTING_HTML = """
<!doctype html><html><body><div class="content" role="main"><div id="siteTable">
  <p class="title"><em>there doesn't seem to be anything here.</em></p>
</div></div></body></html>
"""

internal const val PRIVATE_PAGE = """
<!doctype html><html><body><div class="content">
  <h3>This is a private community</h3>
  <p>you must be invited to visit this community</p>
</div></body></html>
"""

internal const val BANNED_PAGE = """
<!doctype html><html><body><div class="content">
  <h3>This subreddit was banned due to a violation of Reddit's content policy.</h3>
</div></body></html>
"""

internal const val QUARANTINED_PAGE = """
<!doctype html><html><body><div class="content">
  <h3>Are you sure you want to view this community?</h3>
  <form action="/api/quarantine_optin" method="post"><button>Continue</button></form>
</div></body></html>
"""

internal const val LOGIN_PAGE = """
<!doctype html><html><body><div class="content">
  <form id="login_login-main" action="//old.reddit.com/post/login" method="post"></form>
</div></body></html>
"""

/** Markup no reader knows: a page that changed shape, the one case where another route is worth asking. */
internal const val UNREADABLE_PAGE = "<html><body>nothing familiar</body></html>"

/** A community feed piece shaped like www.reddit.com's, with only the attributes the reader uses. */
internal const val WWW_LISTING = """
<!doctype html><html><body><shreddit-app><shreddit-feed>
  <shreddit-post id="t3_abc123" permalink="/r/dartlang/comments/abc123/dart_4_is_out/"
      content-href="https://dart.dev/blog" post-title="Dart 4 is out" author="someone"
      score="412" comment-count="37" created-timestamp="2026-01-21T12:53:20.000000+0000"
      domain="dart.dev" post-type="link" subreddit-name="dartlang">
  </shreddit-post>
  <faceplate-partial slot="load-after" loading="programmatic"
      src="/svc/shreddit/community-more-posts/hot/?name=dartlang&amp;after=dDNfYWJjMTIz&amp;t=DAY"></faceplate-partial>
</shreddit-feed></shreddit-app></body></html>
"""

/** What www.reddit.com's feed piece answers for an empty community, and for one that does not exist. */
internal const val WWW_EMPTY = "<!doctype html><html><body><shreddit-app></shreddit-app></body></html>"
