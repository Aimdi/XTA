package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.core.json.J
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue
import okhttp3.HttpUrl.Companion.toHttpUrl

/** XTA's host, follow and post identity rules: substack_plugin, substack_profile_nav, substack_follow_identity and substack_post_identity tests. */
class SubstackHostsTest {
    private fun hosts(urls: List<okhttp3.HttpUrl>) = urls.map { it.host }

    @Test
    fun aTypedHandleOrAddressNamesItsPublication() {
        assertEquals("astralcodexten.substack.com", SubstackHosts.base("astralcodexten")?.host)
        assertEquals("astralcodexten.substack.com", SubstackHosts.base("https://astralcodexten.substack.com/p/x")?.host)
        assertEquals("www.astralcodexten.com", SubstackHosts.base("www.astralcodexten.com")?.host)
        assertNull(SubstackHosts.base(""))
    }

    @Test
    fun shareLinksAndProfilesBecomeTheSubdomainAndSubstacksOwnPagesNameNothing() {
        assertEquals("platformer.substack.com", SubstackHosts.base("https://open.substack.com/pub/platformer")?.host)
        assertEquals("platformer.substack.com", SubstackHosts.base("https://open.substack.com/pub/platformer/p/the-deal?utm=1")?.host)
        assertEquals("platformer.substack.com", SubstackHosts.base("https://substack.com/@platformer")?.host)
        assertEquals("platformer.substack.com", SubstackHosts.base("@platformer")?.host)
        assertNull(SubstackHosts.base("https://substack.com/"))
        assertNull(SubstackHosts.base("https://open.substack.com/"))
        assertNull(SubstackHosts.base("https://medium.com/@someone"))
        assertEquals("casey", SubstackHosts.profileHandle("substack.com/@casey"))
        assertNull(SubstackHosts.profileHandle("@not/a/handle"))
        assertNull(SubstackHosts.profileHandle("https://open.substack.com/@casey"))
    }

    @Test
    fun aPastedPostGivesItsPublicationAndSlug() {
        val ref = SubstackHosts.postRef("https://astralcodexten.substack.com/p/hello-world?utm=1")
        assertEquals("astralcodexten.substack.com", ref?.base?.host)
        assertEquals("hello-world", ref?.slug)
        assertNull(SubstackHosts.postRef("astralcodexten"))
        val share = SubstackHosts.postRef("https://open.substack.com/pub/platformer/p/the-deal?utm_source=share")
        assertEquals("platformer.substack.com", share?.base?.host)
        assertEquals("the-deal", share?.slug)
        assertNull(SubstackHosts.postRef("https://substack.com/home/post/p-147371717"), "BUG-10: an app share link stays unread, its shape unverified")
        assertNull(SubstackHosts.postRef("https://medium.com/p/x"))
    }

    @Test
    fun aFollowedCustomDomainIsReadAtItsHostThenItsTwin() {
        val platformer = SubstackPublication("platformer", "https://www.platformer.news", "Platformer")
        assertEquals(listOf("www.platformer.news", "platformer.news", "platformer.substack.com"), hosts(SubstackHosts.fetchBases(platformer)))
        val garbage = "https://www.garbageday.email".toHttpUrl()
        assertEquals(listOf("www.garbageday.email", "garbageday.email", "garbageday.substack.com"), hosts(SubstackHosts.candidates(garbage)))
        assertEquals(listOf("www.garbageday.email", "garbageday.email"), hosts(SubstackHosts.requested(garbage)))
        assertEquals(listOf("garbageday.substack.com"), hosts(SubstackHosts.leftover(garbage)))
        assertFalse("www.substack.com" in hosts(SubstackHosts.candidates(garbage)))
        assertEquals(listOf("fieldnotes.substack.com"), hosts(SubstackHosts.fetchBases(SubstackPublication("fieldnotes", "", "Field Notes"))).take(1))
    }

    @Test
    fun aStoredSubdomainReplacesTheGuessedTwinAndOnlyApexDomainsGuess() {
        fun twins(url: String, subdomain: String?) = hosts(SubstackHosts.leftover(url.toHttpUrl(), subdomain))
        assertEquals(listOf("examplecorp.substack.com"), twins("https://newsletter.example.com", "examplecorp"))
        assertEquals(listOf("garbageday.substack.com"), twins("https://www.garbageday.email", "www"))
        assertEquals(emptyList(), twins("https://newsletter.example.com", null))
        assertEquals(emptyList(), twins("https://newsletter.example.com", "newsletter"))
        assertEquals(listOf("example.substack.com"), twins("https://www.example.com", null))
    }

    @Test
    fun aDeepHostFoundWithoutJsonIsFollowedUnderItsHost() {
        assertEquals("newsletter.example.com", SubstackHosts.guessedId("https://www.newsletter.example.com".toHttpUrl()))
        assertEquals("garbageday", SubstackHosts.guessedId("https://www.garbageday.email".toHttpUrl()))
        assertEquals("fieldnotes", SubstackHosts.guessedId("https://fieldnotes.substack.com".toHttpUrl()))
    }

    @Test
    fun aTwinMustNotNameAnotherCustomDomainOrSubdomain() {
        val followed = SubstackPublication("platformer", "https://www.platformer.news", "Platformer")
        val legacy = SubstackPublication("www", "https://www.platformer.news", "Platformer")
        fun post(subdomain: String, custom: String? = null): J {
            val domain = custom?.let { ""","custom_domain":"$it"""" }.orEmpty()
            return J.parse("""{"publishedBylines":[{"publicationUsers":[{"publication":{"subdomain":"$subdomain"$domain}}]}]}""")
        }
        assertTrue(SubstackHosts.twinMatches(followed, post("platformer", "platformer.news")))
        assertTrue(SubstackHosts.twinMatches(followed, post("platformer")))
        assertTrue(SubstackHosts.twinMatches(followed, J.parse("""{"id":1}""")))
        assertFalse(SubstackHosts.twinMatches(followed, post("someone")))
        assertFalse(SubstackHosts.twinMatches(followed, post("platformer", "moved.example")))
        assertTrue(SubstackHosts.twinMatches(legacy, post("anyone", "www.platformer.news")))
        assertFalse(SubstackHosts.twinMatches(legacy, post("anyone", "elsewhere.example")))
    }

    @Test
    fun wwwIsNeverAPublicationTitleOrSubdomain() {
        assertEquals("garbageday", SubstackPublication("www", "https://www.garbageday.email", "www").displayName)
        assertEquals("garbageday", SubstackHosts.subdomainOf("www.garbageday.email"))
        assertEquals("garbageday", SubstackHosts.subdomainOf("garbageday.email"))
        assertEquals("astralcodexten", SubstackHosts.subdomainOf("astralcodexten.substack.com"))
        assertTrue(SubstackHosts.nameLooksGeneric("www"))
        assertTrue(SubstackHosts.nameLooksGeneric(" WWW2 "))
        assertFalse(SubstackHosts.nameLooksGeneric("Garbage Day"))
        assertTrue(SubstackHosts.isObviousNonSubstack("someone.beehiiv.com"))
        assertTrue(SubstackHosts.isServiceHost("Open.Substack.com"))
        assertFalse(SubstackHosts.isPublicationHost("www.substack.com"))
        assertFalse(SubstackHosts.isPublicationHost("evilsubstack.com"))
    }

    @Test
    fun theSameHostIsTheSameFollowWhateverTheIdsSay() {
        val follow = publication("pragmaticengineer", "https://newsletter.pragmaticengineer.com")
        assertSame(follow, SubstackFollows.followOf(listOf(follow), publication("newsletter", "https://newsletter.pragmaticengineer.com")))
        assertSame(follow, SubstackFollows.followOf(listOf(follow), publication("x", "https://www.newsletter.pragmaticengineer.com")))
        val legacy = publication("www", "https://www.garbageday.email")
        val current = publication("garbageday", "https://garbageday.email")
        assertSame(current, SubstackFollows.followOf(listOf(legacy, current), publication("garbageday", "https://www.garbageday.email")))
    }

    @Test
    fun equalIdsOnTwoHostsMatchOnlyWhenNeitherIsAGuess() {
        val platformer = publication("platformer")
        val theNewsletter = publication("newsletter")
        assertSame(platformer, SubstackFollows.followOf(listOf(platformer), publication("platformer", "https://www.platformer.news")))
        assertNull(SubstackFollows.followOf(listOf(theNewsletter), publication("newsletter", "https://newsletter.example.com")), "BUG-16")
        assertNull(SubstackFollows.followOf(listOf(publication("newsletter", "https://newsletter.alpha.example")), theNewsletter))
        assertNull(SubstackFollows.followOf(listOf(publication("www", "https://www.one.example")), publication("www", "https://www.two.example")))
    }

    @Test
    fun placingKeepsTheFollowsIdOrMovesATakenIdToTheHost() {
        val theNewsletter = publication("newsletter")
        assertEquals("newsletter.example.com", SubstackFollows.place(listOf(theNewsletter), publication("newsletter", "https://newsletter.example.com")).id, "BUG-06")
        assertEquals("pe", SubstackFollows.place(listOf(publication("pe", "https://pe.example")), publication("other", "https://www.pe.example")).id)
        assertEquals("legacy", SubstackFollows.place(listOf(publication("", "https://legacy.example")), publication("legacy", "https://legacy.example")).id)
        assertEquals("fresh", SubstackFollows.place(listOf(theNewsletter), publication("fresh")).id)
    }

    @Test
    fun aPostsKeyIsItsCanonicalUrlWithoutSchemeWwwQueryFragmentOrTrailingSlash() {
        val post = SubstackPost(
            id = "1", title = "Hello", slug = "hello", publicationBaseUrl = "https://platformer.substack.com", publicationName = "Platformer",
            canonicalUrl = "https://www.platformer.news/p/hello/?utm_source=x#comments",
        )
        assertEquals("platformer.news/p/hello", post.key)
        assertEquals("example.com/p/hello", SubstackPost("x", "Hello", "hello", "https://www.example.com", "Example").key)
        assertEquals("99", SubstackPost("99", "Note", "", "", "").key)
    }

    @Test
    fun theApiCopyAndTheRssCopyOfAPostAreOnePost() {
        val api = SubstackPost("4242", "Hello", "hello", "https://fieldnotes.substack.com", "Field Notes", canonicalUrl = "https://fieldnotes.substack.com/p/hello")
        val rss = api.copy(id = "hello")
        val elsewhere = SubstackPost("welcome", "Welcome", "welcome", "https://harbour.substack.com", "Harbour")
        assertTrue(SubstackPosts.same(rss, api), "two posts before BUG-02 was fixed")
        assertFalse(SubstackPosts.same(elsewhere, rss.copy(id = "welcome", slug = "welcome", canonicalUrl = "https://fieldnotes.substack.com/p/welcome")))
        assertTrue(SubstackPosts.same(api, api.copy(slug = "renamed", canonicalUrl = "https://fieldnotes.substack.com/p/renamed")), "the same number")
        assertEquals(listOf(api), SubstackPosts.mergeCopies(listOf(api, rss)), "an RSS copy never replaces the API copy")
        assertEquals(listOf(api), SubstackPosts.mergeCopies(listOf(rss, api)), "the API copy replaces an RSS copy in place")
    }

    @Test
    fun readStateFindsEitherCopyAndIdsKeptBeforeKeys() {
        val api = SubstackPost("4242", "Hello", "hello", "https://fieldnotes.substack.com", "Field Notes", canonicalUrl = "https://fieldnotes.substack.com/p/hello")
        val rss = api.copy(id = "hello")
        assertTrue(SubstackPosts.isRead(setOf(rss.key), api))
        assertTrue(SubstackPosts.isRead(setOf("4242"), api), "an id kept before posts had keys")
        assertFalse(SubstackPosts.isRead(setOf("4242"), rss), "a number names no slug")
        assertNotEquals(api.key, SubstackPost("7", "Other", "other", "https://fieldnotes.substack.com", "Field Notes").key)
    }
}
