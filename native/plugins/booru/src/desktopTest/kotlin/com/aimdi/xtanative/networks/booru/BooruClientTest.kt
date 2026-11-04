package com.aimdi.xtanative.networks.booru

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import com.aimdi.xtanative.networks.booru.BooruEngine.DANBOORU
import com.aimdi.xtanative.networks.booru.BooruEngine.E621
import com.aimdi.xtanative.networks.booru.BooruEngine.GELBOORU_V2
import com.aimdi.xtanative.networks.booru.BooruEngine.MOEBOORU
import com.aimdi.xtanative.networks.booru.BooruErrorKind.BAD_RESPONSE
import com.aimdi.xtanative.networks.booru.BooruErrorKind.FORBIDDEN
import com.aimdi.xtanative.networks.booru.BooruErrorKind.NEEDS_CREDENTIALS
import com.aimdi.xtanative.networks.booru.BooruErrorKind.NETWORK
import com.aimdi.xtanative.networks.booru.BooruErrorKind.NOT_FOUND
import com.aimdi.xtanative.networks.booru.BooruErrorKind.RATE_LIMITED
import com.aimdi.xtanative.networks.booru.BooruErrorKind.TAG_LIMIT
import com.aimdi.xtanative.networks.booru.BooruErrorKind.TIMED_OUT
import com.aimdi.xtanative.networks.booru.BooruErrorKind.UNAUTHORIZED
import com.aimdi.xtanative.networks.booru.BooruErrorKind.UNAVAILABLE
import com.aimdi.xtanative.networks.booru.BooruRating.EXPLICIT
import com.aimdi.xtanative.networks.booru.BooruRating.GENERAL
import com.aimdi.xtanative.networks.booru.BooruRating.QUESTIONABLE
import com.aimdi.xtanative.networks.booru.BooruRating.SENSITIVE
import com.aimdi.xtanative.networks.booru.BooruTagCategory.ARTIST
import com.aimdi.xtanative.networks.booru.BooruTagCategory.CHARACTER
import java.io.IOException
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Duration.Companion.seconds
import kotlinx.coroutines.runBlocking
import okhttp3.HttpUrl

/** The client against a stand-in for each booru: what it asks, how it pages, and what it makes of the answers. */
class BooruClientTest {
    private val danbooruHost = "https://danbooru.donmai.us"

    private fun client(http: FakeBooru, settings: BooruSettings = BooruSettings(maxRating = EXPLICIT)) =
        BooruClient(http, { settings }, { FakeClock().gate() })

    private fun rows(vararg rows: String) = ok(rows.joinToString(",", "[", "]"))

    private suspend fun failureOf(read: suspend () -> Any?): Throwable =
        assertIs<Throwable>(runCatching { read() }.exceptionOrNull(), "the read should fail")

    private suspend fun refusalOf(read: suspend () -> Any?): NetworkRequestException = assertIs<NetworkRequestException>(failureOf(read))

    @Test
    fun aDanbooruSearchAsksForItsTagsAndPageWithTheCapAndSaysWhoItIs() = runBlocking {
        val http = FakeBooru { ok(Fixtures.text("danbooru_posts.json")) }
        val page = client(http, BooruSettings(maxRating = SENSITIVE)).search("frieren", next = "2", limit = 5)

        val url = http.asked.single()
        assertEquals("danbooru.donmai.us", url.host)
        assertEquals("/posts.json", url.encodedPath)
        assertEquals("frieren -rating:q -rating:e", url.queryParameter("tags"))
        assertEquals("2", url.queryParameter("page"))
        assertEquals("5", url.queryParameter("limit"))
        assertEquals(
            mapOf("User-Agent" to "XTA-Booru/0.1 (by Aimdi; read-only; +https://github.com/Aimdi/XTA)", "Accept" to "application/json"),
            http.headers.single(),
        )
        assertEquals(listOf("7104589", "7104591"), page.posts.map { it.id }, "the banned, explicit and Flash posts are left out")
        assertEquals("3", page.next, "the host sent a full page, whatever was left out of it")
    }

    @Test
    fun aShortPageEndsTheFeedAndLatestNamesNoTagsUnderAnExplicitCap() = runBlocking {
        val http = FakeBooru { ok(Fixtures.text("danbooru_posts.json")) }
        val page = client(http).latest()
        assertNull(page.next)
        assertNull(http.asked.single().queryParameter("tags"))
        assertEquals("1", http.asked.single().queryParameter("page"))
        assertEquals("40", http.asked.single().queryParameter("limit"))
    }

    @Test
    fun gelbooruCountsItsPagesFromZero() = runBlocking {
        val http = FakeBooru { ok(Fixtures.text("gelbooru_posts.json")) }
        val page = client(http, BooruSettings(GELBOORU_V2, "https://gelbooru.com", maxRating = EXPLICIT)).search("solo", next = "3", limit = 2)

        val url = http.asked.single()
        assertEquals("/index.php", url.encodedPath)
        assertEquals(listOf("dapi", "post", "index", "1", "2"), listOf("page", "s", "q", "json", "pid").map { url.queryParameter(it) })
        assertEquals(listOf("8", "9"), page.posts.map { it.id })
        assertEquals("4", page.next)
    }

    @Test
    fun moebooruAndE621AskTheirOwnPostListsAndAreToldTheCapAsSafe() = runBlocking {
        val moebooru = FakeBooru { ok(Fixtures.text("moebooru_posts.json")) }
        val moe = client(moebooru, BooruSettings(MOEBOORU, "https://yande.re")).search("sky")
        assertEquals("/post.json", moebooru.asked.single().encodedPath)
        assertEquals("sky rating:s", moebooru.asked.single().queryParameter("tags"))
        assertEquals(listOf("1171234"), moe.posts.map { it.id }, "the questionable post is over the cap")

        val e926 = FakeBooru { ok(Fixtures.text("e621_posts.json")) }
        val fox = client(e926, BooruSettings(E621, "https://e926.net")).search("fox")
        assertEquals("/posts.json", e926.asked.single().encodedPath)
        assertEquals("fox rating:s", e926.asked.single().queryParameter("tags"))
        assertEquals(listOf("6617540", "1"), fox.posts.map { it.id }, "the post whose file e621 withholds is left out")
    }

    @Test
    fun gelbooruFamilyHostsAreToldTheCapInTheirOwnWords() = runBlocking {
        suspend fun tagsFor(host: String, cap: BooruRating, query: String = "solo"): String? {
            val http = FakeBooru { ok("[]") }
            client(http, BooruSettings(GELBOORU_V2, host, maxRating = cap)).search(query)
            return http.asked.single().queryParameter("tags")
        }
        assertEquals("solo rating:general", tagsFor("https://gelbooru.com", GENERAL))
        assertEquals("solo rating:safe", tagsFor("https://safebooru.org", GENERAL))
        assertEquals("solo -rating:explicit", tagsFor("https://gelbooru.com", SENSITIVE))
        assertEquals("solo -rating:explicit", tagsFor("https://safebooru.org", SENSITIVE))
        assertEquals("solo -rating:explicit", tagsFor("https://rule34.xxx", QUESTIONABLE))
        assertEquals("solo", tagsFor("https://gelbooru.com", EXPLICIT))
        assertEquals("solo rating:sensitive", tagsFor("https://gelbooru.com", GENERAL, "solo rating:sensitive"), "the search names its own rating")
    }

    @Test
    fun danbooruIsToldTheCapInItsLetters() = runBlocking {
        suspend fun tagsFor(cap: BooruRating): String? {
            val http = FakeBooru { ok("[]") }
            client(http, BooruSettings(maxRating = cap)).search("solo")
            return http.asked.single().queryParameter("tags")
        }
        assertEquals("solo rating:g", tagsFor(GENERAL))
        assertEquals("solo -rating:q -rating:e", tagsFor(SENSITIVE))
        assertEquals("solo -rating:e", tagsFor(QUESTIONABLE))
    }

    @Test
    fun aHostThatPagesNoDeeperEndsTheFeedInsteadOfFailing() = runBlocking {
        val tooDeep = client(FakeBooru { ok("Too deep! Pull it back some.") }, BooruSettings(GELBOORU_V2, "https://gelbooru.com"))
        val gone = client(FakeBooru { XResponse(410, """{"success":false}""") })
        listOf(tooDeep, gone).forEach { client ->
            val page = client.latest(next = "1001")
            assertTrue(page.posts.isEmpty())
            assertNull(page.next)
        }
    }

    @Test
    fun aPageKeepsThePostsTheMutedTagsHidButNeverOneOverTheCap() = runBlocking {
        val http = FakeBooru { rows(danbooruRow(1, "solo"), danbooruRow(2, "smile"), danbooruRow(3, "solo", "e")) }
        val page = client(http, BooruSettings(maxRating = GENERAL, muted = setOf("Solo"))).latest()
        assertEquals(listOf("2"), page.posts.map { it.id })
        assertEquals(listOf("1", "2"), page.allowed.map { it.id })
        assertEquals(listOf("1"), page.hidden.map { it.id })
    }

    @Test
    fun aSearchPastTheTagLimitGoesOutWholeThenRunsOnTheTwoRarestTagsTheDeviceCheckingTheThird() = runBlocking {
        val danbooru = GuestDanbooru()
        val page = danbooru.client().search("blue_eyes long_hair frieren")

        assertEquals(listOf("1"), page.posts.map { it.id })
        assertEquals(
            listOf("blue_eyes long_hair frieren rating:g", "blue_eyes frieren rating:g"),
            danbooru.searches.map { it.queryParameter("tags") },
        )
        assertEquals(1, danbooru.lookups.size)
    }

    @Test
    fun onceTheHostHasSaidASearchIsSharedOutAtOnceAndKeepsItsSplitOnEveryPage() = runBlocking {
        val danbooru = GuestDanbooru()
        val client = danbooru.client()
        client.search("blue_eyes long_hair frieren")
        danbooru.http.asked.clear()

        val first = client.search("long_hair smile frieren")
        client.search("long_hair smile frieren", next = "2")

        assertEquals(listOf("4"), first.posts.map { it.id })
        assertEquals(1, danbooru.lookups.size, "the split is decided once")
        assertEquals(listOf("smile frieren rating:g", "smile frieren rating:g"), danbooru.searches.map { it.queryParameter("tags") })
        assertEquals(listOf("1", "2"), danbooru.searches.map { it.queryParameter("page") })
    }

    @Test
    fun aSearchThatFitsStillGoesOutWhole() = runBlocking {
        val danbooru = GuestDanbooru()
        val client = danbooru.client()
        client.search("blue_eyes long_hair frieren")
        danbooru.http.asked.clear()

        val page = client.search("blue_eyes long_hair")

        assertEquals(listOf("1", "3"), page.posts.map { it.id })
        assertTrue(danbooru.lookups.isEmpty())
        assertEquals("blue_eyes long_hair rating:g", danbooru.searches.single().queryParameter("tags"))
    }

    @Test
    fun aSavedSearchPastTheLimitWorksInFollowing() = runBlocking {
        val posts = GuestDanbooru().client().followedPosts(listOf("blue_eyes long_hair frieren")).posts
        assertEquals(listOf("1"), posts.map { it.id })
    }

    @Test
    fun theTagLimitStaysWhenTheMetatagsAloneArePastIt() = runBlocking {
        val client = GuestDanbooru().client()
        client.search("blue_eyes long_hair frieren")
        val error = refusalOf { client.search("order:score ordfav:someone fav:someone blue_eyes") }
        assertEquals(TAG_LIMIT, BooruErrorKind.of(error))
        assertEquals(BooruClient.NETWORK, error.network)
    }

    @Test
    fun danboorusTagLimitHasItsOwnKindWhenNothingFits() = runBlocking {
        val http = FakeBooru { XResponse(422, """{"success":false,"message":"You cannot search for more than 2 tags at a time."}""") }
        assertEquals(TAG_LIMIT, BooruErrorKind.of(refusalOf { client(http).search("1girl solo order:score") }))
    }

    @Test
    fun aSearchTheHostsDatabaseGaveUpOnSaysSo() = runBlocking {
        val error = refusalOf { client(FakeBooru { XResponse(500, Fixtures.text("danbooru_query_canceled.json")) }).search("blue_eyes order:score") }
        assertEquals(500, error.code)
        assertEquals(TIMED_OUT, BooruErrorKind.of(error))
    }

    @Test
    fun suggestionsComeFromDanboorusAutocompleteWithTheOtherNameEachTagWasFoundThrough() = runBlocking {
        val http = FakeBooru { ok(Fixtures.text("danbooru_autocomplete.json")) }
        val suggestions = client(http).suggestTags(" kancolle ")

        val url = http.asked.single()
        assertEquals("/autocomplete.json", url.encodedPath)
        assertEquals(listOf("kancolle", "tag_query", "12"), listOf("search[query]", "search[type]", "limit").map { url.queryParameter(it) })
        assertEquals(BooruTagSuggestion("kantai_collection", 548_021, BooruTagCategory.COPYRIGHT, "kancolle"), suggestions[3])
    }

    @Test
    fun suggestionsFallBackToTheTagsStartingWithWhatIsTypedWhenAutocompleteFails() = runBlocking {
        val http = FakeBooru { url ->
            if (url.encodedPath == "/autocomplete.json") XResponse(404, "<html>no</html>") else ok(Fixtures.text("danbooru_tags.json"))
        }
        val suggestions = client(http).suggestTags("blue")
        assertEquals(listOf("/autocomplete.json", "/tags.json"), http.paths)
        assertEquals("blue*", http.asked.last().queryParameter("search[name_matches]"))
        assertEquals("blue_eyes", suggestions.first().name)
    }

    @Test
    fun anAutocompleteAnswerOfNoRowsFallsBackToo() = runBlocking {
        val http = FakeBooru { url -> if (url.encodedPath == "/autocomplete.json") ok("""{"error":"nope"}""") else ok("[]") }
        client(http).suggestTags("blue")
        assertEquals(listOf("/autocomplete.json", "/tags.json"), http.paths)
    }

    @Test
    fun suggestionsAreNoneWithNoSecondRequestWhenTheHostCannotBeReached() = runBlocking {
        val http = FakeBooru { throw IOException("offline") }
        assertTrue(client(http).suggestTags("blue").isEmpty())
        assertEquals(listOf("/autocomplete.json"), http.paths)
        assertTrue(client(http).suggestTags("  ").isEmpty())
        assertEquals(1, http.asked.size, "nothing typed, nothing asked")
    }

    @Test
    fun otherEnginesKeepTheirTagLookup() = runBlocking {
        val http = FakeBooru { ok("[]") }
        client(http, BooruSettings(E621, "https://e621.net")).suggestTags("blue")
        assertEquals(listOf("/tags.json"), http.paths)
    }

    @Test
    fun aGelbooruForksXmlTagReplyGivesSuggestionsKindsAndCounts() = runBlocking {
        val http = FakeBooru { ok(Fixtures.text("safebooru_tags.xml")) }
        val client = client(http, BooruSettings(GELBOORU_V2, "https://safebooru.org"))

        val suggestions = client.suggestTags("b")
        val info = client.tagInfo(post(engine = GELBOORU_V2, host = "https://safebooru.org", tags = listOf("blue_eyes", "girls'_frontline")))

        assertEquals(listOf("blue_eyes", "girls'_frontline"), suggestions.map { it.name })
        assertEquals(81_234, suggestions.first().postCount)
        assertEquals(CHARACTER, suggestions.last().category)
        assertEquals(BooruTagInfo(BooruTagCategory.GENERAL, 81_234), info["blue_eyes"])
        assertEquals(CHARACTER, info["girls'_frontline"]?.category)
        assertEquals("b%", http.asked.first().queryParameter("name_pattern"))
        assertEquals("blue_eyes girls'_frontline", http.asked.last().queryParameter("names"))
    }

    @Test
    fun aRefusalWithNoKeySentABusyHostAndALimitEachSaySo() = runBlocking {
        val keyed = BooruCredentials(danbooruHost, "reader", "secret")
        suspend fun kindFor(status: Int, credentials: BooruCredentials? = null): BooruErrorKind {
            val error = refusalOf { client(FakeBooru { XResponse(status, "") }, BooruSettings(credentials = credentials)).latest() }
            assertEquals(status, error.code)
            assertEquals("booru", error.network)
            return BooruErrorKind.of(error)
        }
        assertEquals(FORBIDDEN, kindFor(403), "no key was sent to check")
        assertEquals(UNAUTHORIZED, kindFor(403, keyed))
        assertEquals(UNAUTHORIZED, kindFor(401, keyed))
        assertEquals(UNAVAILABLE, kindFor(503))
        assertEquals(RATE_LIMITED, kindFor(429))
        assertEquals(NOT_FOUND, kindFor(404))
        assertEquals(BAD_RESPONSE, kindFor(500))
        assertEquals(BAD_RESPONSE, kindFor(418))
    }

    @Test
    fun anAnswerThatIsNotJsonIsABadResponseAndNoAnswerStaysAnIOException() = runBlocking {
        val challenge = refusalOf { client(FakeBooru { ok("<html>Just a moment...</html>") }).latest() }
        assertEquals(BAD_RESPONSE, BooruErrorKind.of(challenge))
        assertEquals(BAD_RESPONSE, BooruErrorKind.of(refusalOf { client(FakeBooru { ok("nope") }).latest() }))
        val offline = failureOf { client(FakeBooru { throw IOException("unreachable") }).latest() }
        assertIs<IOException>(offline)
        assertEquals(NETWORK, BooruErrorKind.of(offline))
    }

    @Test
    fun aHostThatAsksAGuestForCredentialsInPlainTextSaysSo() = runBlocking {
        val missing = "Missing authentication. Go to api.rule34.xxx for more information"
        suspend fun kindFor(credentials: BooruCredentials?) = BooruErrorKind.of(
            refusalOf { client(FakeBooru { ok(missing) }, BooruSettings(GELBOORU_V2, "https://rule34.xxx", credentials)).latest() },
        )
        assertEquals(NEEDS_CREDENTIALS, kindFor(null))
        assertEquals(UNAUTHORIZED, kindFor(BooruCredentials("https://rule34.xxx", "123", "secret")))
    }

    @Test
    fun errorTextNeverCarriesTheLoginOrTheKey() = runBlocking {
        val sites = mapOf(DANBOORU to danbooruHost, MOEBOORU to "https://yande.re", GELBOORU_V2 to "https://safebooru.org", E621 to "https://e621.net")
        sites.forEach { (engine, host) ->
            val settings = BooruSettings(engine, host, BooruCredentials(host, "the-reader", "secret-key"))
            val refused = failureOf { client(FakeBooru { XResponse(401, "") }, settings).latest() }
            val unreachable = failureOf { client(FakeBooru { url -> throw IOException("Connection refused: $url") }, settings).latest() }
            listOf(refused, unreachable).forEach { error ->
                val text = "$error ${error.cause}"
                assertTrue(host.removePrefix("https://") in text, "still names what failed: $text")
                assertFalse("secret-key" in text, "$engine: $text")
                assertFalse("the-reader" in text, "$engine: $text")
            }
        }
    }

    @Test
    fun afterA429TheNextRequestToThatHostWaitsAndAnotherHostDoesNot() = runBlocking {
        val clock = FakeClock()
        val calls = AtomicInteger()
        val http = FakeBooru { if (calls.incrementAndGet() == 1) XResponse(429, "", mapOf("retry-after" to listOf("3"))) else ok("[]") }
        val client = BooruClient(http, { BooruSettings(maxRating = EXPLICIT) }, { clock.gate() })

        failureOf { client.latest() }
        client.latest()
        client.search("solo", site = BooruSite(DANBOORU, "https://safebooru.donmai.us"))

        assertEquals(listOf(3.seconds), clock.waits, "the other host has a gate of its own")
        assertEquals(3, calls.get())
    }

    @Test
    fun gelbooruPopularShowsItsHighestScoredPosts() = runBlocking {
        val http = FakeBooru { ok("[]") }
        client(http, BooruSettings(GELBOORU_V2, "https://safebooru.org", maxRating = EXPLICIT)).popular(BooruPopularQuery.today())
        assertEquals("sort:score", http.asked.single().queryParameter("tags"))
    }

    @Test
    fun enginesWithOnePopularPageStopAfterIt() = runBlocking {
        val http = FakeBooru { ok("""{"posts":[]}""") }
        val client = client(http, BooruSettings(E621, "https://e621.net"))
        val first = client.popular(BooruPopularQuery.today())
        val second = client.popular(BooruPopularQuery.today(), next = "2")
        assertNull(first.next)
        assertTrue(second.posts.isEmpty())
        assertEquals(1, http.asked.size)
    }

    @Test
    fun danboorusPopularListPagesOn() = runBlocking {
        val http = FakeBooru { ok(Fixtures.text("danbooru_posts.json")) }
        val page = client(http).popular(BooruPopularQuery(BooruPopularScale.MONTH, LocalDate.of(2026, 3, 4)), limit = 5)
        val url = http.asked.single()
        assertEquals("/explore/posts/popular.json", url.encodedPath)
        assertEquals(listOf("2026-03-04", "month", "1", "5"), listOf("date", "scale", "page", "limit").map { url.queryParameter(it) })
        assertEquals("2", page.next)
    }

    @Test
    fun popularPostsStillPassTheRatingCapAndTheBlacklist() = runBlocking {
        val http = FakeBooru { rows(danbooruRow(1, "a", "e"), danbooruRow(2, "muted"), danbooruRow(3, "kept")) }
        val page = client(http, BooruSettings(maxRating = GENERAL, muted = setOf("muted"))).popular(BooruPopularQuery.today())
        assertEquals(listOf("3"), page.posts.map { it.id })
    }

    @Test
    fun aMissingWikiPageIsNoPageNotAnError() = runBlocking {
        assertNull(client(FakeBooru { XResponse(404, "{}") }).wiki("nothing"))
        val http = FakeBooru { ok(Fixtures.text("danbooru_wiki_pages.json")) }
        assertTrue(client(http).wiki("blue_eyes")!!.startsWith("h4. Blue eyes"))
        assertEquals("blue_eyes", http.asked.single().queryParameter("search[title]"))
    }

    @Test
    fun moebooruKindsComeFromOnePostLookup() = runBlocking {
        val http = FakeBooru { ok(Fixtures.text("moebooru_post_tags.json")) }
        val info = client(http, BooruSettings(MOEBOORU, "https://yande.re"))
            .tagInfo(post("1171234", engine = MOEBOORU, host = "https://yande.re", tags = listOf("kantoku", "clouds")))
        assertEquals(ARTIST, info["kantoku"]?.category)
        assertEquals("id:1171234", http.asked.single().queryParameter("tags"))
        assertEquals("/post.json", http.asked.single().encodedPath)
    }

    @Test
    fun countsFromTheLookupJoinTheKindsThePostCarries() = runBlocking {
        val http = FakeBooru { ok("""[{"name":"solo","post_count":4330000}]""") }
        val info = client(http).tagInfo(post(categories = mapOf("solo" to BooruTagCategory.GENERAL, "smile" to BooruTagCategory.GENERAL)))
        assertEquals(BooruTagInfo(BooruTagCategory.GENERAL, 4_330_000), info["solo"])
        assertEquals(BooruTagInfo(BooruTagCategory.GENERAL, null), info["smile"])
    }

    @Test
    fun aFailedTagLookupLeavesTheTagsUngroupedAndUncounted() = runBlocking {
        val xml = client(FakeBooru { ok("<xml/>") }, BooruSettings(GELBOORU_V2, "https://safebooru.org"))
        assertTrue(xml.tagInfo(post(engine = GELBOORU_V2)).isEmpty())
        val down = client(FakeBooru { XResponse(503, "") })
        assertEquals(mapOf("solo" to BooruTagInfo(BooruTagCategory.GENERAL)), down.tagInfo(post(categories = mapOf("solo" to BooruTagCategory.GENERAL))))
    }

    @Test
    fun aPostsDetailsComeFromItsOwnSiteWithThatSitesKeyOnly() = runBlocking {
        val http = FakeBooru { ok("[]") }
        val settings = BooruSettings(E621, "https://e621.net", BooruCredentials(danbooruHost, "reader", "secret-key"))
        val client = BooruClient(http, { settings }, { FakeClock().gate() })
        val post = post(host = danbooruHost)
        val site = client.siteOf(post)

        client.tagInfo(post)
        client.comments(post)
        client.wiki("kantoku", site)
        client.search("kantoku", site = site)
        client.pools(post)
        client.notes(post)

        assertEquals(setOf("danbooru.donmai.us"), http.asked.map { it.host }.toSet())
        assertEquals(listOf("/tags.json", "/comments.json", "/wiki_pages.json", "/posts.json", "/pools.json", "/notes.json"), http.paths)
        assertEquals(setOf<String?>("secret-key"), http.asked.map { it.queryParameter("api_key") }.toSet(), "the key is that site's")

        http.asked.clear()
        client.tagInfo(post(host = "https://e621.net", engine = E621))
        client.latest()
        assertEquals(setOf("e621.net"), http.asked.map { it.host }.toSet())
        assertEquals(setOf<String?>(null), http.asked.map { it.queryParameter("api_key") }.toSet(), "Danbooru's key stays home")
    }

    @Test
    fun aPostsCommentsPoolsAndNotesAreReadFromItsSite() = runBlocking {
        val http = FakeBooru { url ->
            ok(Fixtures.text(when (url.encodedPath) {
                "/comments.json" -> "danbooru_comments.json"
                "/pools.json" -> "danbooru_pools.json"
                else -> "danbooru_notes.json"
            }))
        }
        val client = client(http)
        val post = post("7104589", host = danbooruHost)
        assertEquals(listOf("a", "b"), client.comments(post).map { it.author })
        assertEquals(listOf("my_series"), client.pools(post).map { it.name })
        assertEquals(listOf("Hello & bye"), client.notes(post).map { it.text })
        assertEquals("7104589", http.asked.first().queryParameter("search[post_id]"))
        assertEquals("7104589", http.asked[1].queryParameter("search[post_ids_include_all]"))
    }

    @Test
    fun anArtistAndRelatedTagsComeFromTheHostOrNotAtAll() = runBlocking {
        val http = FakeBooru { url ->
            ok(Fixtures.text(if (url.encodedPath == "/artists.json") "danbooru_artists.json" else "danbooru_related_tag.json"))
        }
        val client = client(http)
        assertEquals(listOf("カントク", "kantoku_art"), client.artist("kantoku")?.otherNames)
        assertEquals(listOf("cat_ears", "animal_ears"), client.relatedTags(listOf("cat_ears")))

        val down = client(FakeBooru { XResponse(503, "") })
        assertNull(down.artist("kantoku"))
        assertTrue(down.relatedTags(listOf("cat_ears")).isEmpty())
        assertTrue(down.pools(post(host = danbooruHost)).isEmpty())
        assertTrue(down.notes(post(host = danbooruHost)).isEmpty())
        assertEquals(UNAVAILABLE, BooruErrorKind.of(refusalOf { down.comments(post(host = danbooruHost)) }), "comments say why they did not load")
    }

    @Test
    fun whatAHostKeepsNoneOfIsNotAskedFor() = runBlocking {
        val http = FakeBooru { ok("[]") }
        val client = client(http, BooruSettings(GELBOORU_V2, "https://gelbooru.com"))
        val post = post(engine = GELBOORU_V2, host = "https://gelbooru.com")
        assertTrue(client.comments(post).isEmpty())
        assertTrue(client.pools(post).isEmpty())
        assertTrue(client.notes(post).isEmpty())
        assertNull(client.wiki("solo"))
        assertNull(client.artist("solo"))
        assertTrue(client.relatedTags(listOf("solo")).isEmpty())
        assertTrue(http.asked.isEmpty())
    }

    @Test
    fun credentialsAreSentOnlyToTheHostTheyWereEnteredFor() = runBlocking {
        val http = FakeBooru { ok("[]") }
        var settings = BooruSettings(DANBOORU, danbooruHost, BooruCredentials(danbooruHost, "reader", "secret-key"), EXPLICIT)
        val client = BooruClient(http, { settings }, { FakeClock().gate() })

        client.latest()
        settings = settings.copy(engine = E621, host = "https://e621.net")
        client.latest()
        settings = settings.copy(engine = DANBOORU, host = danbooruHost)
        client.latest()

        assertEquals(listOf("secret-key", null, "secret-key"), http.asked.map { it.queryParameter("api_key") })
        assertEquals(listOf("reader", null, "reader"), http.asked.map { it.queryParameter("login") })
    }

    @Test
    fun aLinkedPostIsAskedOfItsOwnSiteById() = runBlocking {
        val http = FakeBooru { ok("""{"posts":[]}""") }
        assertNull(client(http, BooruSettings(maxRating = SENSITIVE)).postAt(BooruPostRef("https://e621.net", E621, "12")))
        val url = http.asked.single()
        assertEquals("e621.net", url.host)
        assertTrue("id:12" in url.queryParameter("tags").orEmpty().split(' '))
        assertEquals("1", url.queryParameter("limit"))
    }

    @Test
    fun aLinkedPostComesBackEvenWhenTheBlacklistWouldHideItInAFeed() = runBlocking {
        val http = FakeBooru { rows(danbooruRow(7, "solo spoilers", "s")) }
        val found = client(http, BooruSettings(maxRating = SENSITIVE, muted = setOf("spoilers"))).postAt(BooruPostRef(danbooruHost, DANBOORU, "7"))
        assertEquals("7", found?.id)
        assertEquals(danbooruHost, found?.host)
    }

    @Test
    fun aLinkedPostOverTheCapOrAnotherPostTheSiteSendsIsNotTakenForIt() = runBlocking {
        val ref = BooruPostRef(danbooruHost, DANBOORU, "7")
        assertNull(client(FakeBooru { rows(danbooruRow(7, "solo", "e")) }, BooruSettings(maxRating = SENSITIVE)).postAt(ref))
        assertNull(client(FakeBooru { rows(danbooruRow(8, "solo", "s")) }, BooruSettings(maxRating = SENSITIVE)).postAt(ref))
    }

    @Test
    fun theSavedLoginGoesOnlyToItsOwnSite() = runBlocking {
        val http = FakeBooru { ok("[]") }
        val client = client(http, BooruSettings(credentials = BooruCredentials(danbooruHost, "reader", "secret"), maxRating = SENSITIVE))
        client.postAt(BooruPostRef(danbooruHost, DANBOORU, "7"))
        client.postAt(BooruPostRef("https://aibooru.online", DANBOORU, "7"))
        assertEquals("reader", http.asked.first().queryParameter("login"))
        assertEquals("aibooru.online", http.asked.last().host)
        assertNull(http.asked.last().queryParameter("login"))
        assertNull(http.asked.last().queryParameter("api_key"))
    }

    @Test
    fun aSavedSearchFeedsFollowingAsOneMultiTagQueryAskedOnce() = runBlocking {
        val http = FakeBooru { ok("[]") }
        client(http).followedPosts(listOf("blue_eyes solo", "night", " Night "))
        assertEquals(setOf("blue_eyes solo", "night"), http.asked.map { it.queryParameter("tags") }.toSet())
        assertEquals(2, http.asked.size, "a query is asked once however it was written")
        assertEquals(setOf("10"), http.asked.map { it.queryParameter("limit") }.toSet())
    }

    @Test
    fun followedPostsAreMergedNewestFirstAndOncePerPost() = runBlocking {
        fun row(id: Int, tags: String, day: String) =
            """{"id":$id,"tag_string":"$tags","rating":"g","file_url":"https://cdn.example/$id.png","created_at":"2024-$day"}"""
        val http = FakeBooru { url ->
            when (url.queryParameter("tags")) {
                "a" -> rows(row(1, "a b", "01-01T00:00:00Z"), row(2, "a", "03-01T00:00:00Z"))
                else -> rows(row(1, "a b", "01-01T00:00:00Z"), row(3, "b", "02-01T00:00:00Z"))
            }
        }
        val followed = client(http).followedPosts(listOf("a", "b"))
        assertEquals(listOf("2", "3", "1"), followed.posts.map { it.id })
        assertEquals(2 to 0, followed.asked to followed.failed)
    }

    @Test
    fun followingCountsTheQueriesThatFailedAndFailsOnlyWhenAllDid() = runBlocking {
        val failing = ConcurrentHashMap.newKeySet<String>()
        val http = FakeBooru { url ->
            val tags = url.queryParameter("tags").orEmpty()
            if (tags in failing) XResponse(503, "") else rows(danbooruRow(tags.length, tags))
        }
        val client = client(http)

        failing += "night"
        val some = client.following(listOf("blue_eyes", "night"))
        assertEquals(listOf("blue_eyes"), some.posts.map { it.tagLine })
        assertEquals(1, some.failedQueries)
        assertNull(some.next)

        failing += "blue_eyes"
        assertEquals(UNAVAILABLE, BooruErrorKind.of(refusalOf { client.following(listOf("blue_eyes", "night")) }))
    }

    @Test
    fun followingHasOnePage() = runBlocking {
        val http = FakeBooru { ok("[]") }
        val page = client(http).following(listOf("blue_eyes"), next = "2")
        assertTrue(page.posts.isEmpty())
        assertNull(page.next)
        assertTrue(http.asked.isEmpty())
        assertTrue(client(http).following(emptyList()).posts.isEmpty())
    }

    /** Danbooru as it answers a guest, from XTA's live checks of 2026-10-29: two counted tags per search, rating terms free. */
    private class GuestDanbooru {
        private val posts = mapOf(
            1 to "blue_eyes long_hair frieren",
            2 to "blue_eyes frieren",
            3 to "blue_eyes long_hair",
            4 to "long_hair frieren smile",
        )
        private val uses = mapOf("blue_eyes" to 2_524_807, "long_hair" to 6_357_605, "frieren" to 16_526, "smile" to 3_000_000)

        val http = FakeBooru(::answer)
        val searches: List<HttpUrl> get() = http.asked.filter { it.encodedPath == "/posts.json" }
        val lookups: List<HttpUrl> get() = http.asked.filter { it.encodedPath == "/tags.json" }

        fun client() = BooruClient(http, { BooruSettings() }, { FakeClock().gate() })

        private fun answer(url: HttpUrl): XResponse = when (url.encodedPath) {
            "/posts.json" -> search(url.queryParameter("tags").orEmpty())
            "/tags.json" -> lookup(url.queryParameter("search[name_comma]").orEmpty())
            else -> XResponse(404, "")
        }

        private fun search(query: String): XResponse {
            val counted = query.split(' ').filter { it.isNotEmpty() && "rating:" !in it }
            if (counted.size > 2) return XResponse(422, Fixtures.text("danbooru_tag_limit.json"))
            val matching = posts.filterValues { tags -> counted.all { it in tags.split(' ') } }
            return ok(matching.map { (id, tags) -> danbooruRow(id, tags) }.joinToString(",", "[", "]"))
        }

        private fun lookup(names: String): XResponse = ok(
            names.split(',').mapNotNull { name -> uses[name]?.let { """{"name":"$name","post_count":$it,"category":0}""" } }
                .joinToString(",", "[", "]"),
        )
    }
}
