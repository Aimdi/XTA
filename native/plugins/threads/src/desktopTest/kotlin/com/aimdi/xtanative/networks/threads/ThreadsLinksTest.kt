package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/** Handles, links and addresses, as XTA's threads_test, plugin_url_test and threads_search_query_test pin them. */
class ThreadsLinksTest {
    @Test
    fun aBareHandleAnAtHandleAndAProfileLinkAreTheSameAccount() {
        assertEquals("zuck", ThreadsLinks.normaliseHandle("zuck"))
        assertEquals("zuck", ThreadsLinks.normaliseHandle("@zuck"))
        assertEquals("zuck", ThreadsLinks.normaliseHandle("  @Zuck "))
        assertEquals("zuck", ThreadsLinks.normaliseHandle("https://www.threads.com/@zuck"))
        assertEquals("zuck", ThreadsLinks.normaliseHandle("https://www.threads.net/@zuck/post/abc"))
        assertEquals("some.one_else", ThreadsLinks.normaliseHandle("@some.one_else"))
    }

    @Test
    fun whatIsNotAHandleAtAllIsRefused() {
        listOf("", "   ", "@", "two words", "https://example.org/nothing").forEach {
            assertNull(ThreadsLinks.normaliseHandle(it), "'$it'")
        }
    }

    @Test
    fun theShortCodeIsReadOffPostAndShortLinksOnEitherHost() {
        assertEquals("Dabc", ThreadsLinks.shortcode("https://www.threads.net/@zuck/post/Dabc?igshid=1"))
        assertEquals("Xyz", ThreadsLinks.shortcode("https://threads.com/t/Xyz"))
        assertNull(ThreadsLinks.shortcode("https://www.threads.com/@zuck"))
        assertNull(ThreadsLinks.shortcode("https://example.com/post/abc"))
        assertNull(ThreadsLinks.shortcode(null))
    }

    @Test
    fun aPostLinkIsAskedOfWwwThreadsComWithoutTheShareSheetsQuery() {
        assertEquals("https://www.threads.com/@zuck/post/C1", ThreadsLinks.canonicalPostUrl("https://threads.net/@zuck/post/C1?igshid=abc").toString())
        assertEquals("https://www.threads.com/t/Short", ThreadsLinks.canonicalPostUrl(" https://m.threads.com/t/Short ").toString())
        assertNull(ThreadsLinks.canonicalPostUrl("https://example.com/@zuck/post/C1"))
        assertNull(ThreadsLinks.canonicalPostUrl("https://www.threads.com/"))
        assertNull(ThreadsLinks.canonicalPostUrl("not a link"))
    }

    @Test
    fun threadsOutboundRedirectIsUnwrappedAndOtherLinksAreLeftAlone() {
        assertEquals("https://example.org/story", ThreadsLinks.unwrapOutbound("https://l.threads.com/?u=https%3A%2F%2Fexample.org%2Fstory"))
        assertEquals("https://example.org/net", ThreadsLinks.unwrapOutbound("https://l.threads.net/?u=https%3A%2F%2Fexample.org%2Fnet&e=AT"))
        assertEquals("https://example.org/direct", ThreadsLinks.unwrapOutbound("https://example.org/direct"))
        assertEquals("https://l.threads.com/?x=1", ThreadsLinks.unwrapOutbound("https://l.threads.com/?x=1"))
    }

    @Test
    fun aLinkOpensAProfileOrAPost() {
        assertEquals(ThreadsLink.Profile("zuck"), ThreadsLinks.parse("https://www.threads.net/@zuck"))
        assertEquals(ThreadsLink.Post("https://www.threads.com/@Zuck/post/Dabc123", "zuck"), ThreadsLinks.parse("https://www.threads.com/@Zuck/post/Dabc123"))
        assertEquals(ThreadsLink.Profile("zuck"), ThreadsLinks.parse("threads.net/@zuck"), "an address without its scheme")
        assertNull(ThreadsLinks.parse("https://example.org/@zuck"))
        assertNull(ThreadsLinks.parse("https://www.threads.com/"))
        assertNull(ThreadsLinks.parse("mailto:zuck@threads.net"))
    }

    @Test
    fun aShortLinkOpensAPostWhoseAuthorIsReadLater() {
        assertEquals(ThreadsLink.Post("https://www.threads.com/t/Dabc123", ""), ThreadsLinks.parse("https://www.threads.net/t/Dabc123"))
        assertNull(ThreadsLinks.parse("https://www.threads.net/t/"))
    }

    @Test
    fun theSearchPageIsAskedForTopRecentOrATag() {
        assertEquals("https://www.threads.com/search?q=cats&serp_type=default", ThreadsLinks.searchUrl("cats").toString())
        assertEquals("https://www.threads.com/search?q=cats&serp_type=default&filter=recent", ThreadsLinks.searchUrl("cats", recent = true).toString())
        assertEquals("https://www.threads.com/search?q=Books&serp_type=tags", ThreadsLinks.searchUrl("Books", tag = true).toString())
        val words = ThreadsLinks.searchUrl("glaze recipes #1")
        assertEquals("q=glaze+recipes+%231&serp_type=default", words.encodedQuery, "encoded as Dart's Uri encodes it")
        assertEquals("glaze recipes #1", words.queryParameter("q"))
    }

    @Test
    fun formsAreEncodedAsDartEncodesThem() {
        assertEquals("%7B%22userID%22%3A%2263404918397%22%7D", ThreadsLinks.formEncode("""{"userID":"63404918397"}"""))
        assertEquals("a+b-._~%2A%C3%A9", ThreadsLinks.formEncode("a b-._~*é"))
        assertEquals("a%20b-_.!~*'()%2F", ThreadsLinks.componentEncode("a b-_.!~*'()/"))
    }

    @Test
    fun aPostLinkNamesThePostAndIsNotKeptInTheHistory() {
        val query = assertIs<ThreadsQuery.Post>(ThreadsQuery.of(" https://www.threads.com/@maya.makes/post/DAbc123 "))
        assertEquals("maya.makes", query.link.handle)
        assertNull(query.remembered)
        assertNull(query.terms, "it opens the post rather than searching")
    }

    @Test
    fun aTagNamesThePostsFiledUnderItSpacesAndAll() {
        assertEquals(ThreadsQuery.Tag("Keramik"), ThreadsQuery.of("#Keramik"))
        val club = ThreadsQuery.of("# Book Club ")!!
        assertEquals(ThreadsSearchTerms("Book Club", tag = true), club.terms)
        assertEquals("#Book Club", club.remembered)
        assertIs<ThreadsQuery.Words>(ThreadsQuery.of("#"), "a # alone names no tag")
    }

    @Test
    fun anAtHandleOrAProfileLinkNamesTheProfile() {
        assertEquals(ThreadsQuery.Profile("maya.makes"), ThreadsQuery.of("@Maya.Makes"))
        val linked = ThreadsQuery.of("https://www.threads.net/@ola")!!
        assertEquals(ThreadsQuery.Profile("ola"), linked)
        assertEquals("ola", linked.remembered)
        assertNull(linked.terms)
    }

    @Test
    fun otherWordsSearchPostsOfferingTheHandleTheyAlsoSpell() {
        assertEquals(ThreadsSearchTerms("maya.makes", tag = false, profile = "maya.makes"), ThreadsQuery.of("maya.makes")!!.terms)
        assertEquals(ThreadsSearchTerms("glaze recipes", tag = false), ThreadsQuery.of("glaze recipes")!!.terms)
        assertNull(ThreadsQuery.of("   "))
    }

    @Test
    fun aPastedSearchOrTopicLinkSearchesWhatItAsked() {
        assertEquals(ThreadsQuery.Tag("Keramik"), ThreadsQuery.of("https://www.threads.com/search?q=%23Keramik"))
        assertEquals(ThreadsQuery.Tag("Book Club"), ThreadsQuery.of("https://www.threads.com/search?q=Book%20Club&serp_type=tags"))
        assertEquals(ThreadsQuery.Words("cats"), ThreadsQuery.of("https://threads.net/search?q=cats"))
        assertEquals(ThreadsLink.Search(ThreadsSearchTerms("cats", tag = false)), ThreadsLinks.parse("threads.com/search?q=cats&serp_type=default"))
    }

    @Test
    fun theFieldShowsATagWithItsHash() {
        assertEquals("#Keramik", ThreadsSearchTerms("Keramik", tag = true).text)
        assertEquals("kiln", ThreadsSearchTerms("kiln", tag = false).text)
    }
}
