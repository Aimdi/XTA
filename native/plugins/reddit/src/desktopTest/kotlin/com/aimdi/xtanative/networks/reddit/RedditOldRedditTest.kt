package com.aimdi.xtanative.networks.reddit

import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** old.reddit.com's listing, search and sidebar markup, as XTA's reddit_html_test, reddit_search_html_test and reddit_about_search_test read it. */
class RedditOldRedditTest {
    private fun listingPage(things: String, next: String = "") = """
        <!doctype html><html><body><div class="content" role="main">
          <div id="siteTable">$things</div>
          <div class="nav-buttons"><span class="next-button">$next</span></div>
        </div></body></html>"""

    private val link = """
        <div class=" thing id-t3_abc123 link " id="thing_t3_abc123"
             data-fullname="t3_abc123" data-type="link" data-subreddit="dartlang"
             data-author="someone" data-score="42" data-comments-count="7"
             data-timestamp="1767225600000" data-permalink="/r/dartlang/comments/abc123/a_title/"
             data-url="https://example.com/article" data-domain="example.com" data-nsfw="false">
          <a class="thumbnail may-blank" href="https://example.com/article">
            <img src="//b.thumbs.redditmedia.com/x.jpg">
          </a>
          <a class="title may-blank outbound" href="https://example.com/article">A title</a>
        </div>"""

    private val selfPost = """
        <div class=" thing id-t3_self1 link self " data-fullname="t3_self1"
             data-subreddit="dartlang" data-author="writer" data-score="5"
             data-comments-count="0" data-timestamp="1767312000000"
             data-permalink="/r/dartlang/comments/self1/hello/"
             data-url="/r/dartlang/comments/self1/hello/" data-domain="self.dartlang" data-nsfw="false">
          <a class="title" href="/r/dartlang/comments/self1/hello/">Hello</a>
          <div class="expando"><div class="md"><p>Body text</p></div></div>
        </div>"""

    private fun posts(things: String) = RedditOldListing.page(listingPage(things)).posts

    @Test
    fun aLinkPostIsTakenOutOfItsDataAttributes() {
        val post = posts(link).single()

        assertEquals("abc123", post.id, "the t3_ prefix is the fullname, not the id")
        assertEquals("A title", post.title)
        assertEquals("dartlang", post.subreddit)
        assertEquals("someone", post.author)
        assertEquals(42, post.score)
        assertEquals(7, post.commentCount)
        assertEquals("/r/dartlang/comments/abc123/a_title/", post.permalink)
        assertEquals("https://example.com/article", post.url)
        assertFalse(post.isSelf)
        assertEquals(Instant.ofEpochMilli(1767225600000), post.createdAt)
        assertEquals("example.com", post.domain, "the card says where a link leads")
    }

    @Test
    fun aProtocolRelativeThumbnailIsGivenAScheme() {
        assertEquals("https://b.thumbs.redditmedia.com/x.jpg", posts(link).single().thumbnail)
    }

    @Test
    fun aSelfPostIsRecognisedByItsDomainAndKeepsItsBody() {
        val post = posts(selfPost).single()

        assertTrue(post.isSelf)
        assertNull(post.url, "there is no article behind a self post")
        assertEquals("Body text", post.selfText)
    }

    @Test
    fun postsKeepTheirOrderAndAnAdvertIsLeftOut() {
        val promoted = """<div class=" thing " data-fullname="t3_ad" data-promoted="true" data-subreddit="x"
            data-permalink="/r/x/comments/ad/"><a class="title">Buy this</a></div>"""

        assertEquals(listOf("abc123", "self1"), posts(link + selfPost).map { it.id })
        assertEquals(listOf("abc123"), posts(promoted + link).map { it.id })
    }

    @Test
    fun pinnedOver18AndSpoilerPostsCarryTheirFlags() {
        val stickied = """<div class="thing stickied" data-fullname="t3_pin" data-subreddit="x" data-permalink="/r/x/comments/pin/"><a class="title">Read this first</a></div>"""
        val nsfw = """<div class="thing" data-fullname="t3_n" data-subreddit="x" data-nsfw="true" data-permalink="/r/x/comments/n/"><a class="title">Grown up</a></div>"""
        val spoiler = """<div class="thing spoiler" data-fullname="t3_s" data-subreddit="x" data-spoiler="true" data-permalink="/r/x/comments/s/"><a class="title">The ending</a></div>"""

        assertTrue(posts(stickied).single().stickied)
        assertFalse(posts(link).single().stickied)
        assertTrue(posts(nsfw).single().over18)
        assertTrue(posts(spoiler).single().spoiler)
    }

    @Test
    fun flairPrefersTheFullLabelOverTheAbbreviatedOne() {
        val flaired = """<div class="thing" data-fullname="t3_f" data-subreddit="x" data-permalink="/r/x/comments/f/">
            <a class="title">Titled</a><span class="linkflairlabel" title="Elon Criticism">Elon Crit…</span></div>"""
        val bare = """<div class="thing" data-fullname="t3_b" data-subreddit="x" data-permalink="/r/x/comments/b/">
            <a class="title">Titled</a><span class="linkflairlabel">Discussion</span></div>"""

        assertEquals("Elon Criticism", posts(flaired).single().flair)
        assertEquals("Discussion", posts(bare).single().flair)
        assertNull(posts(link).single().flair)
    }

    @Test
    fun theAfterCursorComesOffTheNextLinkAndTheLastPageHasNone() {
        val next = """<a href="https://old.reddit.com/r/dartlang/?count=25&amp;after=t3_abc123" rel="nofollow next">next</a>"""

        assertEquals("t3_abc123", RedditOldListing.page(listingPage(link, next)).after)
        assertNull(RedditOldListing.page(listingPage(link)).after)
    }

    @Test
    fun aRelNextLinkOutsideTheButtonStillPagesOn() {
        val page = listingPage(link) + """<a rel="nextish" href="/?after=t3_no">no</a><a rel="nofollow next" href="/r/dartlang/?after=t3_next">next</a>"""

        assertEquals("t3_next", RedditOldListing.page(page).after)
    }

    @Test
    fun markupThatCannotBeReadIsSkippedRatherThanFatal() {
        val broken = """<div class="thing" data-fullname="t3_x"></div>"""
        val odd = """<div class="thing" data-fullname="t3_o" data-subreddit="x" data-score="•"
            data-comments-count="" data-permalink="/r/x/comments/o/"><a class="title">Odd</a></div>"""

        assertTrue(posts(broken).isEmpty(), "a post with no title or permalink is skipped, not guessed at")
        assertTrue(posts("").isEmpty())
        assertTrue(RedditOldListing.page("not html at all").posts.isEmpty())
        assertTrue(RedditOldListing.page("").posts.isEmpty())
        val post = posts(odd).single()
        assertEquals(0, post.score)
        assertEquals(0, post.commentCount)
        assertNull(post.createdAt)
    }

    @Test
    fun eachPageRedditServesInsteadOfAListingIsRecognised() {
        val pages = mapOf(
            LISTING_HTML to OldPageKind.LISTING,
            EMPTY_LISTING_HTML to OldPageKind.LISTING,
            OVER18_GATE to OldPageKind.OVER18_GATE,
            PRIVATE_PAGE to OldPageKind.PRIVATE,
            BANNED_PAGE to OldPageKind.BANNED,
            QUARANTINED_PAGE to OldPageKind.QUARANTINED,
            LOGIN_PAGE to OldPageKind.LOGIN_WALL,
            UNREADABLE_PAGE to OldPageKind.UNREADABLE,
        )

        pages.forEach { (body, kind) -> assertEquals(kind, RedditOldListing.page(body).kind, body) }
    }

    @Test
    fun aCommunitysPictureComesFromItsHeaderImageNotTheSitesLogo() {
        val page = """<html><head><meta property="og:image" content="https://www.redditstatic.com/icon.png"></head>
            <body><a id="header-img" href="/r/x/"><img src="//b.thumbs.redditmedia.com/logo.png"></a></body></html>"""

        assertEquals("https://b.thumbs.redditmedia.com/logo.png", RedditOldListing.subredditIcon(page))
    }

    @Test
    fun aCommunitysPictureFallsBackToItsOgImageWhenThatIsCommunityArtwork() {
        val page = """<html><head><meta property="og:image" content="https://styles.redditmedia.com/t5_2qh0u/styles/communityIcon.png"></head><body></body></html>"""

        assertEquals("https://styles.redditmedia.com/t5_2qh0u/styles/communityIcon.png", RedditOldListing.subredditIcon(page))
        assertNull(RedditOldListing.subredditIcon("<html><body>nothing here</body></html>"))
        assertNull(RedditOldListing.subredditIcon(""))
    }

    @Test
    fun theOver18GateIsRecognisedSoItCanBeAnsweredWithACookie() {
        val gate = """<html><body><div class="content"><form action="/over18?dest=%2Fr%2Fx" method="post">
            <input type="hidden" name="over18" value="yes"><button>Continue</button></form></div></body></html>"""

        assertTrue(RedditOldListing.isOver18Gate(gate))
        assertFalse(RedditOldListing.isOver18Gate(listingPage(link)))
    }

    private val sidebarPage = """
        <!doctype html><html><body>
          <div class="side">
            <div class="titlebox">
              <h1 class="redditname"><a href="/r/dartlang/">dartlang</a></h1>
              <span class="subscribers"><span class="number">104,532</span> readers</span>
              <p class="users-online"><span class="number">1,204</span> users here now</p>
              <form class="usertext"><div class="usertext-body"><div class="md"><p>All things Dart.</p></div></div></form>
            </div>
          </div>
        </body></html>"""

    @Test
    fun theSidebarYieldsNameCountsAndDescription() {
        val side = RedditOldListing.sidebar(sidebarPage)

        assertEquals(OldSidebar("dartlang", "All things Dart.", 104532, 1204), side)
        assertEquals(OldSidebar(null, null, null, null), RedditOldListing.sidebar("<html><body><p>hi</p></body></html>"))
    }

    private val searchPost = """
        <div class="search-result search-result-link" data-fullname="t3_abc123">
          <header class="search-result-header">
            <a class="search-title may-blank" href="https://old.reddit.com/r/dartlang/comments/abc123/a_title/">A title</a>
            <span class="search-result-meta">
              <span class="search-score">42 points</span>
              <span class="search-time">submitted <time datetime="2026-07-01T10:00:00+00:00">1 hour ago</time></span>
              <a class="author may-blank" href="/user/someone">someone</a>
              <a class="search-subreddit-link may-blank" href="/r/dartlang/">r/dartlang</a>
              <a class="search-comments may-blank" href="#">7 comments</a>
            </span>
          </header>
        </div>"""

    private fun html(body: String) = "<!doctype html><html><body>$body</body></html>"

    @Test
    fun searchedPostsAreReadOutOfTheSearchMarkup() {
        val post = RedditOldSearch.posts(html(searchPost)).single()

        assertEquals("abc123", post.id)
        assertEquals("A title", post.title)
        assertEquals("dartlang", post.subreddit)
        assertEquals("someone", post.author)
        assertEquals(42, post.score)
        assertEquals(7, post.commentCount)
        assertEquals("/r/dartlang/comments/abc123/a_title/", post.permalink)
        assertEquals(Instant.parse("2026-07-01T10:00:00Z"), post.createdAt)
    }

    @Test
    fun aSearchPageRenderedAsAListingIsReadAsOne() {
        val listed = """<div class="thing" data-fullname="t3_zzz" data-subreddit="dartlang"
            data-permalink="/r/dartlang/comments/zzz/x/" data-score="3" data-comments-count="1">
            <a class="title" href="/r/dartlang/comments/zzz/x/">From a listing</a></div>"""

        val post = RedditOldSearch.posts(html(listed)).single()
        assertEquals("zzz", post.id)
        assertEquals("From a listing", post.title)
    }

    @Test
    fun aSearchedPostsIdSurvivesWithoutItsFullnameAndItsScoreWithAComma() {
        assertEquals("abc123", RedditOldSearch.posts(html(searchPost.replace("data-fullname=\"t3_abc123\"", ""))).single().id, "the comments path carries it")
        assertEquals(1234, RedditOldSearch.posts(html(searchPost.replace("42 points", "1,234 points"))).single().score)
        assertTrue(RedditOldSearch.posts(html(searchPost.replaceFirst("search-result-link", "search-result-link spoiler"))).single().spoiler)
    }

    @Test
    fun aSearchResultWithNoTitleIsSkipped() {
        assertTrue(RedditOldSearch.posts(html("""<div class="search-result-link"></div>""")).isEmpty())
        assertTrue(RedditOldSearch.posts("not html at all").isEmpty())
        assertTrue(RedditOldSearch.posts("").isEmpty())
    }

    @Test
    fun searchedCommunitiesAreReadFromEitherMarkupEachOnce() {
        val results = """
            <div class="search-result search-result-subreddit">
              <a class="search-subreddit-link" href="/r/dartlang/">r/dartlang</a>
              <span class="search-subscribers">12,345 subscribers</span>
              <div class="search-result-body">A place for Dart</div>
            </div>
            <div class="subreddit thing"><a class="title" href="/r/dartlang/">r/dartlang</a></div>
            <div class="subreddit thing"><a class="title" href="/r/flutterdev/">r/flutterdev</a></div>"""

        val parsed = RedditOldSearch.subreddits(html(results))
        assertEquals(listOf("dartlang", "flutterdev"), parsed.map { it.name })
        assertEquals(RedditSubredditResult("dartlang", 12345, "A place for Dart", null), parsed.first())
        assertTrue(RedditOldSearch.subreddits(html("""<div class="search-result-subreddit"></div>""")).isEmpty())
    }

    @Test
    fun searchedAccountsAreReadEachOnce() {
        val results = """
            <div class="search-result search-result-user">
              <a href="/user/someone">someone</a>
              <span class="search-result-user-karma">3,210 karma</span>
            </div>
            <div class="search-result search-result-user"><a href="/user/someone">someone</a></div>
            <div class="search-result search-result-user"><a href="/user/another">another</a></div>"""

        assertEquals(listOf(RedditUserResult("someone", 3210), RedditUserResult("another", null)), RedditOldSearch.users(html(results)))
        assertTrue(RedditOldSearch.users(html("")).isEmpty())
        assertTrue(RedditOldSearch.users("junk").isEmpty())
    }
}
