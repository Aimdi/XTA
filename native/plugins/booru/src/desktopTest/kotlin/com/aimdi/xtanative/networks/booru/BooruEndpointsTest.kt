package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.networks.booru.BooruEngine.DANBOORU
import com.aimdi.xtanative.networks.booru.BooruEngine.E621
import com.aimdi.xtanative.networks.booru.BooruEngine.GELBOORU_V2
import com.aimdi.xtanative.networks.booru.BooruEngine.MOEBOORU
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import okhttp3.HttpUrl

/** Each engine's URLs, credentials and hosts, as XTA's booru_endpoints.dart and booru_engines.dart write them. */
class BooruEndpointsTest {
    private val danbooru = BooruSite(DANBOORU, "https://danbooru.donmai.us")
    private val moebooru = BooruSite(MOEBOORU, "https://yande.re")
    private val gelbooru = BooruSite(GELBOORU_V2, "https://gelbooru.com")
    private val e621 = BooruSite(E621, "https://e621.net")

    private val HttpUrl?.params: Map<String, String?>
        get() = this?.queryParameterNames.orEmpty().associateWith { this?.queryParameter(it) }

    @Test
    fun eachEngineAsksForAPageOfPostsItsOwnWay() {
        val tags = listOf("blue_sky", "rating:g")
        assertEquals("/posts.json", BooruEndpoints.posts(danbooru, tags, 2, 40).encodedPath)
        assertEquals(mapOf("limit" to "40", "tags" to "blue_sky rating:g", "page" to "2"), BooruEndpoints.posts(danbooru, tags, 2, 40).params)
        assertEquals("/posts.json", BooruEndpoints.posts(e621, tags, 1, 40).encodedPath)
        assertEquals("/post.json", BooruEndpoints.posts(moebooru, tags, 3, 40).encodedPath)
        val dapi = BooruEndpoints.posts(gelbooru, tags, 3, 40)
        assertEquals("/index.php", dapi.encodedPath)
        assertEquals(
            mapOf("page" to "dapi", "s" to "post", "q" to "index", "json" to "1", "limit" to "40", "tags" to "blue_sky rating:g", "pid" to "2"),
            dapi.params,
            "Gelbooru counts pages from 0",
        )
        assertNull(BooruEndpoints.posts(danbooru, emptyList(), 1, 40).queryParameter("tags"), "no tags: no tags parameter")
    }

    @Test
    fun tagsAreLookedUpByTheStartOfTheirNameInEachEnginesWildcard() {
        assertEquals(
            mapOf("search[name_matches]" to "blue*", "search[order]" to "count", "limit" to "12"),
            BooruEndpoints.tagPrefix(danbooru, "blue", 12).params,
        )
        assertEquals("/tag.json", BooruEndpoints.tagPrefix(moebooru, "blue", 12).encodedPath)
        assertEquals(mapOf("name" to "blue*", "order" to "count", "limit" to "12"), BooruEndpoints.tagPrefix(moebooru, "blue", 12).params)
        assertEquals("blue%", BooruEndpoints.tagPrefix(gelbooru, "blue", 12).queryParameter("name_pattern"))
        assertEquals("tag", BooruEndpoints.tagPrefix(gelbooru, "blue", 12).queryParameter("s"))
    }

    @Test
    fun onlyDanbooruKeepsAnAutocomplete() {
        val url = BooruEndpoints.autocomplete(danbooru, "kancolle", 12)!!
        assertEquals("/autocomplete.json", url.encodedPath)
        assertEquals(mapOf("search[query]" to "kancolle", "search[type]" to "tag_query", "limit" to "12"), url.params)
        listOf(moebooru, gelbooru, e621).forEach { assertNull(BooruEndpoints.autocomplete(it, "kancolle", 12), "${it.engine}") }
    }

    @Test
    fun oneRequestNamesEveryTagOfAPost() {
        assertEquals(
            mapOf("search[name_comma]" to "a,b_c", "only" to "name,category,post_count", "limit" to "2"),
            BooruEndpoints.tagInfo(danbooru, "7", listOf("a", "b_c")).params,
        )
        assertEquals(mapOf("search[name]" to "a,b", "limit" to "2"), BooruEndpoints.tagInfo(e621, "7", listOf("a", "b")).params)
        assertEquals(
            mapOf("tags" to "id:7", "api_version" to "2", "include_tags" to "1", "limit" to "1"),
            BooruEndpoints.tagInfo(moebooru, "7", listOf("a")).params,
            "Moebooru names its post's tag kinds, not their counts",
        )
        assertNull(BooruEndpoints.tagInfo(danbooru, "7", emptyList()))
        assertNull(BooruEndpoints.tagList(moebooru, listOf("a")), "Moebooru looks tags up one pattern at a time")
    }

    @Test
    fun rule34IsAskedOnItsApiHost() {
        val url = BooruEndpoints.tagInfo(BooruSite(GELBOORU_V2, "https://rule34.xxx"), "1", listOf("a", "b"))!!
        assertEquals("api.rule34.xxx", url.host)
        assertEquals("a b", url.queryParameter("names"))
        assertEquals("tag", url.queryParameter("s"))
    }

    @Test
    fun relatedTagsComeFromDanbooruForAnySearchAndFromE621ForOneTag() {
        val url = BooruEndpoints.relatedTags(danbooru, listOf("cat_ears", "blue_eyes"))!!
        assertEquals("/related_tag.json", url.encodedPath)
        assertEquals(mapOf("search[query]" to "cat_ears blue_eyes", "limit" to "25"), url.params)
        assertEquals(mapOf("search[name]" to "wolf", "limit" to "1"), BooruEndpoints.relatedTags(e621, listOf("wolf")).params)
        assertNull(BooruEndpoints.relatedTags(e621, listOf("wolf", "solo")), "e621 relates one tag at a time")
        assertNull(BooruEndpoints.relatedTags(gelbooru, listOf("cat")))
        assertNull(BooruEndpoints.relatedTags(moebooru, listOf("cat")))
        assertNull(BooruEndpoints.relatedTags(danbooru, emptyList()))
    }

    @Test
    fun artistEntriesComeFromDanbooruAndE621() {
        assertEquals(
            mapOf("search[any_name_matches]" to "kantoku", "only" to "name,is_deleted,other_names,urls", "limit" to "10"),
            BooruEndpoints.artist(danbooru, "kantoku").params,
        )
        assertEquals(mapOf("search[name]" to "wolfy", "limit" to "1"), BooruEndpoints.artist(e621, "wolfy").params)
        assertNull(BooruEndpoints.artist(moebooru, "kantoku"))
    }

    @Test
    fun aPostsPoolsComeFromDanbooruAndItsNotesFromDanbooruAndE621() {
        assertEquals(mapOf("search[post_ids_include_all]" to "42", "limit" to "20"), BooruEndpoints.postPools(danbooru, "42").params)
        assertNull(BooruEndpoints.postPools(e621, "42"))
        assertTrue(BooruEndpoints.supportsPools(DANBOORU))
        assertFalse(BooruEndpoints.supportsPools(E621))
        assertEquals(mapOf("search[post_id]" to "42", "limit" to "200"), BooruEndpoints.notes(danbooru, "42").params)
        assertEquals("/notes.json", BooruEndpoints.notes(e621, "42")?.encodedPath)
        assertNull(BooruEndpoints.notes(gelbooru, "42"))
    }

    @Test
    fun wikiPagesAndComments() {
        assertEquals(mapOf("search[title]" to "fate/stay_night", "limit" to "1"), BooruEndpoints.wiki(danbooru, "fate/stay_night").params)
        assertEquals(
            mapOf("query" to "dress", "limit" to "100"),
            BooruEndpoints.wiki(moebooru, "dress").params,
            "Moebooru's wiki answers a title search; ten titles holding dress can all come before it",
        )
        assertEquals(
            mapOf(
                "group_by" to "comment", "search[post_id]" to "9", "limit" to "100",
                "only" to "id,created_at,score,body,is_deleted,creator[name]",
            ),
            BooruEndpoints.comments(danbooru, "9").params,
        )
        assertEquals(mapOf("group_by" to "comment", "search[post_id]" to "9", "limit" to "100"), BooruEndpoints.comments(e621, "9").params)
        assertEquals("https://yande.re/comment.json?post_id=9", BooruEndpoints.comments(moebooru, "9").toString())
        assertNull(BooruEndpoints.wiki(gelbooru, "a"))
        assertNull(BooruEndpoints.comments(gelbooru, "9"), "Gelbooru answers comments only in XML")
        assertFalse(BooruEndpoints.supportsWiki(GELBOORU_V2))
        assertFalse(BooruEndpoints.supportsComments(GELBOORU_V2))
    }

    @Test
    fun popularListsPerEngine() {
        val week = BooruPopularQuery(BooruPopularScale.WEEK, LocalDate.of(2026, 3, 4))
        val url = BooruEndpoints.popular(danbooru, week, 2, 40)!!
        assertEquals("/explore/posts/popular.json", url.encodedPath)
        assertEquals(mapOf("date" to "2026-03-04", "scale" to "week", "page" to "2", "limit" to "40"), url.params)
        assertEquals("https://e621.net/popular.json?date=2026-03-04&scale=week", BooruEndpoints.popular(e621, week, 1, 40).toString())
        assertEquals(
            "https://yande.re/post/popular_by_week.json?day=4&month=3&year=2026",
            BooruEndpoints.popular(moebooru, week, 1, 40).toString(),
        )
        assertEquals(
            mapOf("month" to "3", "year" to "2026"),
            BooruEndpoints.popular(moebooru, week.withScale(BooruPopularScale.MONTH), 1, 40).params,
        )
        assertNull(BooruEndpoints.popular(gelbooru, week, 1, 40))
        assertFalse(BooruEndpoints.hasPopularList(GELBOORU_V2))
        assertTrue(BooruEndpoints.popularPages(DANBOORU))
        assertFalse(BooruEndpoints.popularPages(MOEBOORU))
    }

    @Test
    fun credentialsAreNamedTheWayEachEngineExpects() {
        fun auth(engine: BooruEngine) = BooruSite(engine, "https://x.org", "me", "k").auth
        assertEquals(mapOf("login" to "me", "api_key" to "k"), auth(DANBOORU))
        assertEquals(mapOf("login" to "me", "api_key" to "k"), auth(E621))
        assertEquals(mapOf("user_id" to "me", "api_key" to "k"), auth(GELBOORU_V2))
        assertEquals(mapOf("login" to "me", "password_hash" to "k"), auth(MOEBOORU))
    }

    @Test
    fun halfALoginIsNeverSent() {
        BooruEngine.entries.forEach { engine ->
            assertTrue(BooruSite(engine, "https://x.org", "me", "").auth.isEmpty(), "$engine: a login without its key")
            assertTrue(BooruSite(engine, "https://x.org", "", "k").auth.isEmpty(), "$engine: a key without its login")
            assertEquals(2, BooruSite(engine, "https://x.org", "me", "k").auth.size, "$engine")
        }
    }

    @Test
    fun credentialsGoAfterTheQueryAndEveryEngineNamesThemSoErrorTextMasksThem() {
        val url = BooruEndpoints.posts(BooruSite(DANBOORU, "https://danbooru.donmai.us", "reader", "secret"), listOf("solo"), 1, 40)
        assertEquals(listOf("limit", "tags", "page", "login", "api_key"), url.queryParameterNames.toList())
        BooruEngine.entries.forEach { engine ->
            assertTrue(BooruEndpoints.CREDENTIAL_PARAMETERS.containsAll(BooruSite(engine, "https://x.org", "a", "b").auth.keys), "$engine")
        }
        val masked = BooruEndpoints.redact("$url: 401")
        assertFalse("secret" in masked || "reader" in masked, masked)
        assertTrue("login=***" in masked && "api_key=***" in masked && "danbooru.donmai.us" in masked, masked)
        assertFalse("secret" in BooruSite(DANBOORU, "https://danbooru.donmai.us", "reader", "secret").toString())
        assertFalse("secret" in BooruCredentials("https://danbooru.donmai.us", "reader", "secret").toString())
    }

    @Test
    fun aSiteWithAPathOfItsOwnKeepsIt() {
        val url = BooruEndpoints.posts(BooruSite(DANBOORU, "https://example.com/booru/"), listOf("solo"), 1, 40)
        assertEquals("/booru/posts.json", url.encodedPath)
    }

    @Test
    fun hostsAreNormalisedAndRule34IsAskedOnItsApiHostButOpenedOnItsSite() {
        assertEquals("https://danbooru.donmai.us", BooruHosts.normalise("danbooru.donmai.us"))
        assertEquals("https://danbooru.donmai.us", BooruHosts.normalise(" HTTPS://Danbooru.Donmai.us:443/ "))
        assertEquals("http://example.com:8080/booru", BooruHosts.normalise("http://example.com:8080/booru/?q=1"))
        assertEquals("", BooruHosts.normalise("   "))
        assertEquals("https://api.rule34.xxx", BooruHosts.requestHost("https://rule34.xxx"))
        assertEquals("https://api.rule34.xxx", BooruHosts.requestHost("www.rule34.xxx"))
        assertEquals("https://rule34.xxx", BooruHosts.pageHost("https://api.rule34.xxx"))
        assertEquals("https://danbooru.donmai.us", BooruHosts.requestHost("https://danbooru.donmai.us"))
        assertEquals("yande.re", BooruHosts.displayName("https://yande.re/"))
    }

    @Test
    fun theEngineIsGuessedFromTheHostsName() {
        assertEquals(GELBOORU_V2, BooruHosts.guessEngine("rule34.xxx"))
        assertEquals(GELBOORU_V2, BooruHosts.guessEngine("https://xbooru.com"))
        assertEquals(DANBOORU, BooruHosts.guessEngine("danbooru.donmai.us"))
        assertEquals(MOEBOORU, BooruHosts.guessEngine("yande.re"))
        assertEquals(GELBOORU_V2, BooruHosts.guessEngine("https://hypnohub.net"), "a Gelbooru 0.2 site, not Moebooru")
        assertEquals(E621, BooruHosts.guessEngine("e621.net"))
        assertNull(BooruHosts.guessEngine("rule34.paheal.net"))
        assertNull(BooruHosts.guessEngine(""))
        assertEquals(GELBOORU_V2, BooruEngine.parse("gelbooru"))
        assertEquals(E621, BooruEngine.parse(" E926 "))
        assertNull(BooruEngine.parse("sankaku"))
    }

    @Test
    fun everyPresetHasAHostItsEngineReads() {
        assertEquals(12, BooruPresets.ALL.size)
        assertEquals("https://danbooru.donmai.us", BooruPresets.DEFAULT.host)
        BooruPresets.ALL.forEach { preset -> assertEquals(preset.host, BooruHosts.normalise(preset.host), preset.name) }
    }
}
