package com.aimdi.xtanative.networks.websources

import com.aimdi.xtanative.networks.websources.WebSourceFixtures.obj
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.read
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.source
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.strings
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.with
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.withFeed
import com.aimdi.xtanative.networks.websources.WebSourceFixtures.without
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertSame
import kotlin.test.assertTrue
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.jupiter.api.Timeout

class WebSourceDefinitionTest {
    private val links = WebSourceFixtures.links()
    private val feedA = obj("id" to "a", "name" to "A", "url" to "https://lobste.rs/a.json")

    private fun assertRefused(refusal: WebSourceRefusal, detail: String, file: WebSourceFile, why: String? = null) =
        assertEquals(WebSourceFile.Refused(refusal, detail), file, why)

    @Test
    fun aValidSourceIsReadWithItsDefaultsMergedIntoEachList() {
        val source = source(links)

        assertEquals(listOf("lobsters", "1", "Lobsters", "LINKS"), listOf(source.id, "${source.version}", source.name, source.layout.name))
        assertEquals(0xFFAC130DL, source.color)
        assertEquals(listOf("hottest", "newest"), source.feeds.map { it.id })
        assertEquals(WebSourcePageType.JSON, source.feeds.first().type)
        assertEquals("title", source.feeds.first().fields[WebSourceField.TITLE])
        assertTrue("{query}" in source.search!!.url)
        assertEquals("Newest", source.list("newest")?.name)
        assertSame(source.search, source.list("search"))
        assertEquals("https://lobste.rs/".toHttpUrl(), source.homepage)
        assertEquals(links, source.raw, "the file is kept as read")
    }

    @Test
    fun everyFixtureShapeIsAValidSource() {
        for (json in listOf(WebSourceFixtures.posts(), WebSourceFixtures.html(), WebSourceFixtures.gallery())) {
            assertIs<WebSourceFile.Read>(read(json), "${json["id"]}")
        }
    }

    @Test
    fun aListOverridesTheFieldsItNamesAndKeepsTheRest() {
        val source = source(links.withFeed(feedA.with("fields", obj("title" to "headline"))))

        assertEquals("headline", source.feeds.single().fields[WebSourceField.TITLE])
        assertEquals("url", source.feeds.single().fields[WebSourceField.LINK])
    }

    @Test
    fun refusesWhatIsNotASourceFile() {
        assertRefused(WebSourceRefusal.UNREADABLE, "", WebSources.read("not json"))
        assertRefused(WebSourceRefusal.UNREADABLE, "", WebSources.read("[1, 2]"))
        assertRefused(WebSourceRefusal.TOO_LARGE, "64 KB", WebSources.read("x".repeat(WebSources.MAX_FILE_BYTES + 1)))
    }

    @Test
    fun refusesANewerFormatAndAMissingOne() {
        assertRefused(WebSourceRefusal.NEWER_FORMAT, "format 2", read(links.with("format", JsonPrimitive(2))))
        assertRefused(WebSourceRefusal.INVALID, "format", read(links.without("format")))
    }

    @Test
    fun refusesABadIdVersionNameOrLayout() {
        assertRefused(WebSourceRefusal.INVALID, "id", read(links.with("id", JsonPrimitive("Bad Id"))))
        assertRefused(WebSourceRefusal.INVALID, "version", read(links.with("version", JsonPrimitive(0))))
        assertRefused(WebSourceRefusal.INVALID, "name", read(links.with("name", JsonPrimitive(" "))))
        assertRefused(WebSourceRefusal.INVALID, "layout", read(links.with("layout", JsonPrimitive("video"))))
    }

    @Test
    fun refusesHostsThatAreMissingTooManyOrMalformed() {
        assertRefused(WebSourceRefusal.INVALID, "hosts", read(links.with("hosts", JsonArray(emptyList()))))
        assertRefused(WebSourceRefusal.TOO_LARGE, "hosts: 8", read(links.with("hosts", strings((0 until 9).map { "h$it.org" }))))
        assertRefused(WebSourceRefusal.INVALID, "hosts[0]", read(links.with("hosts", strings(listOf("lobste.rs/x")))))
    }

    @Test
    fun refusesAnAddressOffItsHostsWithoutHttpsOrWithAPortOrCredentials() {
        assertRefused(WebSourceRefusal.INSECURE, "homepage", read(links.with("homepage", JsonPrimitive("https://elsewhere.org"))))
        for (url in listOf("http://lobste.rs/{page}.json", "https://lobste.rs:8443/x", "https://a@lobste.rs/x")) {
            assertRefused(WebSourceRefusal.INSECURE, "feeds[0].url", read(links.withFeed(feedA.with("url", JsonPrimitive(url)))), url)
        }
    }

    @Test
    fun refusesASubdomainUnlessItsHostsAllowSubdomains() {
        val feed = feedA.with("url", JsonPrimitive("https://api.lobste.rs/a.json"))

        assertRefused(WebSourceRefusal.INSECURE, "feeds[0].url", read(links.withFeed(feed)))
        assertIs<WebSourceFile.Read>(read(links.withFeed(feed).with("hosts", strings(listOf("*.lobste.rs")))))
    }

    @Test
    fun refusesListsThatAreMissingTooManyRepeatedOrUnknownInTheirAddress() {
        assertRefused(WebSourceRefusal.INVALID, "feeds", read(links.with("feeds", JsonArray(emptyList()))))
        assertRefused(WebSourceRefusal.TOO_LARGE, "feeds: 12", read(links.with("feeds", JsonArray(List(13) { feedA }))))
        assertRefused(WebSourceRefusal.INVALID, "feeds[1].id", read(links.with("feeds", JsonArray(listOf(feedA, feedA)))))
        assertRefused(
            WebSourceRefusal.INVALID,
            "feeds[0].url",
            read(links.withFeed(feedA.with("url", JsonPrimitive("https://lobste.rs/{user}.json")))),
        )
    }

    @Test
    fun refusesASearchWithoutAQuery() {
        assertRefused(WebSourceRefusal.INVALID, "search.url", read(links.with("search", obj("url" to "https://lobste.rs/search.json"))))
    }

    @Test
    fun refusesFieldsThatCannotNameOrFindAPostOrAGalleryWithoutPictures() {
        assertRefused(
            WebSourceRefusal.INVALID,
            "feeds[0].fields",
            read(links.withFeed(feedA.with("fields", obj())).with("fields", obj())),
        )
        val gallery = WebSourceFixtures.gallery()
        val feed = gallery["feeds"]!!.jsonArray.single().jsonObject
            .with("fields", obj("id" to "data[0].nasa_id", "title" to "data[0].title"))
        assertRefused(WebSourceRefusal.INVALID, "feeds[0].fields", read(gallery.withFeed(feed)))
    }

    @Test
    fun refusesARuleThisPageTypeCannotRead() {
        assertRefused(WebSourceRefusal.INVALID, "feeds[0].items", read(links.withFeed(feedA.with("items", JsonPrimitive("a..b")))))
        val html = WebSourceFixtures.html()
        assertRefused(WebSourceRefusal.INVALID, "feeds[0].items", read(html.with("items", JsonPrimitive("article[["))))
        val fields = JsonObject(html["fields"]!!.jsonObject + ("title" to JsonPrimitive("h1[[")))
        assertRefused(WebSourceRefusal.INVALID, "feeds[0].fields.title", read(html.with("fields", fields)))
    }

    @Test
    @Timeout(10)
    fun refusesARuleLongerThan300CharactersOrOneWrittenToFailSlowly() {
        assertRefused(WebSourceRefusal.INVALID, "feeds[0].items", read(links.withFeed(feedA.with("items", JsonPrimitive("a".repeat(301))))))
        assertRefused(WebSourceRefusal.INVALID, "feeds[0].next", read(links.withFeed(feedA.with("next", JsonPrimitive("a".repeat(301))))))
        assertRefused(WebSourceRefusal.INVALID, "feeds[0].items", read(links.withFeed(feedA.with("items", JsonPrimitive("a".repeat(299) + "{")))))
    }

    @Test
    fun refusesAnArticleAddressThatNamesNoFieldOfThePost() {
        val posts = WebSourceFixtures.posts()
        assertRefused(
            WebSourceRefusal.INVALID,
            "article.url",
            read(posts.with("article", obj("url" to "https://lemmy.world/api/v3/post", "html" to "post.body"))),
        )
        assertRefused(
            WebSourceRefusal.INVALID,
            "article.url",
            read(posts.with("article", obj("url" to "https://lemmy.world/{nope}", "html" to "post.body"))),
        )
    }

    @Test
    fun refusesCssXtaCannotReadAndTheRegularExpressionsJsoupWouldRun() {
        val html = WebSourceFixtures.html()
        for (title in listOf("h1:has(a)", "h1:contains(x)", "a[class~=x]", "a[lang|=en]", "a[href!=x]", "p:first-of-type", "p:matches(.*)", "> a", "svg|a", "p::before")) {
            val fields = JsonObject(html["fields"]!!.jsonObject + ("title" to JsonPrimitive(title)))
            assertRefused(WebSourceRefusal.INVALID, "feeds[0].fields.title", read(html.with("fields", fields)), title)
        }
        for (title in listOf("h1:not(.hidden) a", "li:first-child > a", "a[href*=\"after=\"]", "p:nth-child(2) + p ~ span", "div:empty, span:only-child")) {
            val fields = JsonObject(html["fields"]!!.jsonObject + ("title" to JsonPrimitive(title)))
            assertIs<WebSourceFile.Read>(read(html.with("fields", fields)), title)
        }
    }

    @Test
    fun wholeNumbersAreReadAsXtaReadsThemFractionsDropped() {
        val source = source(links.with("format", JsonPrimitive(1.0)).with("version", JsonPrimitive(2.7)))
        assertEquals(2L, source.version)
    }

    @Test
    fun aHostCheckTakesListedHostsAndTheirSubdomainsWhenAsked() {
        assertTrue(WebSources.hostAllowed(listOf("lobste.rs"), "https://lobste.rs/a".toHttpUrl()))
        assertTrue(!WebSources.hostAllowed(listOf("lobste.rs"), "https://evil-lobste.rs/a".toHttpUrl()))
        assertTrue(WebSources.hostAllowed(listOf("*.lobste.rs"), "https://a.b.lobste.rs/a".toHttpUrl()))
        assertTrue(WebSources.hostAllowed(listOf("*.lobste.rs"), "https://lobste.rs/a".toHttpUrl()))
        assertTrue(!WebSources.hostAllowed(listOf("*.lobste.rs"), "https://notlobste.rs/a".toHttpUrl()))
        assertTrue(!WebSources.hostAllowed(listOf("lobste.rs"), "http://lobste.rs/a".toHttpUrl()))
        assertTrue(WebSources.hostAllowed(listOf("lobste.rs"), "https://lobste.rs:443/a".toHttpUrl()), "the standard port written out")
    }
}
