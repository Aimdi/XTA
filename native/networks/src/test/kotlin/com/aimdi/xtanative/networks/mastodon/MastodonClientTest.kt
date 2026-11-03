package com.aimdi.xtanative.networks.mastodon

import com.aimdi.xtanative.core.x.http.XResponse
import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.JsonArray

/** The client against stand-in instances, as XTA's mastodon_client_test.dart and mastodon_instances_test.dart check it. */
class MastodonClientTest {
    private fun client(fake: FakeMastodon, timeout: Long = 8_000, fallback: Long = 4_000) = MastodonClient(fake, timeout, fallback)

    private fun failureOf(block: suspend () -> Unit): MastodonFailure? {
        val error = assertFailsWith<Exception> { runBlocking { block() } }
        return MastodonFailure.of(error)
    }

    private val seed = MastodonPost(id = "22", acct = "b@other.social", authorName = "B", text = "Root", url = "https://other.social/@b/22")

    // ---- single reads ----

    @Test
    fun verifyAsksTheV2InstanceEndpoint() = runBlocking {
        val fake = FakeMastodon { ok(obj("domain" to "mastodon.social")) }
        client(fake).verify("https://mastodon.social")
        assertEquals(listOf("mastodon.social/api/v2/instance"), fake.paths)
    }

    @Test
    fun verifyFallsBackToV1WhereV2IsMissing() = runBlocking {
        val fake = FakeMastodon { if (it.path == "/api/v2/instance") answer(404, "missing") else ok(obj("uri" to "https://old.example")) }
        client(fake).verify("old.example")
        assertEquals(listOf("old.example/api/v2/instance", "old.example/api/v1/instance"), fake.paths)
    }

    @Test
    fun aLookupSendsXtasHeadersAndReadsTheAccount() = runBlocking {
        val fake = FakeMastodon {
            ok(obj("id" to "1", "username" to "Gargron", "acct" to "Gargron", "display_name" to "Eugen", "note" to "", "url" to "https://mastodon.social/@Gargron"))
        }
        val profile = client(fake).lookup("https://mastodon.social", "Gargron")

        val asked = fake.asked.single()
        assertEquals("GET", asked.method)
        assertEquals("https://mastodon.social/api/v1/accounts/lookup?acct=gargron", asked.url.toString())
        assertEquals(mapOf("User-Agent" to "XTA Mastodon plugin", "Accept" to "application/json"), asked.headers)
        assertEquals("1", profile.id)
        assertEquals("gargron@mastodon.social", profile.acct)
        assertEquals("Eugen", profile.displayName)
    }

    @Test
    fun anAcctThatIsNoAddressIsNotFoundWithoutAsking() {
        val fake = FakeMastodon { error("nothing should be asked") }
        assertEquals(MastodonFailure.NOT_FOUND, failureOf { client(fake).lookup("https://mastodon.social", "two words") })
        assertTrue(fake.asked.isEmpty())
    }

    @Test
    fun aProfileWithoutAnIdIsABadResponse() {
        val fake = FakeMastodon { ok(obj("username" to "ghost")) }
        assertEquals(MastodonFailure.BAD_RESPONSE, failureOf { client(fake).lookup("https://mastodon.social", "ghost") })
    }

    @Test
    fun statusesAskForTheirLimitAndParseThePosts() = runBlocking {
        val fake = FakeMastodon { ok(list(statusJson("9", "https://mastodon.social/@a/9", "Hi", username = "a"))) }
        val page = client(fake).statuses("https://mastodon.social", "1", limit = 5)

        val asked = fake.asked.single()
        assertEquals("/api/v1/accounts/1/statuses", asked.path)
        assertEquals("limit=5&exclude_replies=true", asked.url.encodedQuery)
        assertEquals("Hi", page.items.single().text)
        assertEquals("a@mastodon.social", page.items.single().acct)
        assertNull(page.next, "a page shorter than its limit is the last")
    }

    @Test
    fun statusesAskForMediaPinsAndAnOlderPage() = runBlocking {
        val fake = FakeMastodon { ok(list(statusJson("8", "https://mastodon.social/@a/8", "pic"))) }
        val reader = client(fake)
        reader.statuses("https://mastodon.social", "1", onlyMedia = true, maxId = "9")
        reader.statuses("https://mastodon.social", "1", pinned = true)

        val (media, pinned) = fake.asked
        assertEquals("limit=20&exclude_replies=true&only_media=true&max_id=9", media.url.encodedQuery)
        assertEquals("limit=20&exclude_replies=true&pinned=true", pinned.url.encodedQuery)
    }

    @Test
    fun aProfilesTabsAskForTheirOwnStatuses() = runBlocking {
        val fake = FakeMastodon { ok(list()) }
        val reader = client(fake)
        MastodonProfileTab.entries.forEach { reader.profileTab("https://studio.example", "7", it, maxId = "c") }

        val asked = fake.asked.map { listOf(it.query("exclude_replies"), it.query("only_media"), it.query("max_id")) }
        assertEquals(listOf(listOf("true", null, "c"), listOf("false", null, "c"), listOf("false", "true", "c")), asked)
    }

    @Test
    fun accountPostsLookTheIdUpOnceAndReadTheStatusesAgain() = runBlocking {
        val fake = FakeMastodon {
            if (it.path == "/api/v1/accounts/lookup") ok(accountJson("1", "a")) else ok(list())
        }
        val reader = client(fake)
        reader.accountPosts("https://mastodon.social", "a")
        reader.accountPosts("https://mastodon.social", "a")

        assertEquals(1, fake.paths.count { it.endsWith("/api/v1/accounts/lookup") })
        assertEquals(2, fake.paths.count { it.endsWith("/api/v1/accounts/1/statuses") })
    }

    @Test
    fun answersOtherThan200AreTypedByTheirCode() {
        fun failure(code: Int, body: String = "") = failureOf { client(FakeMastodon { answer(code, body) }).statuses("https://mastodon.social", "1") }
        assertEquals(MastodonFailure.NOT_FOUND, failure(404))
        assertEquals(MastodonFailure.UNAUTHORIZED, failure(401))
        assertEquals(MastodonFailure.UNAUTHORIZED, failure(403))
        assertEquals(MastodonFailure.RATE_LIMITED, failure(429))
        assertEquals(MastodonFailure.BAD_RESPONSE, failure(500))
        assertEquals(MastodonFailure.BAD_RESPONSE, failure(204))
        assertEquals(MastodonFailure.BAD_RESPONSE, failure(200, "<html>not json</html>"))
        val error = assertFailsWith<NetworkRequestException> { runBlocking { client(FakeMastodon { answer(429) }).statuses("https://mastodon.social", "1") } }
        assertEquals(MastodonClient.NETWORK, error.network)
        assertEquals(429, error.code)
    }

    @Test
    fun noConnectionStaysAnIOException() {
        val error = assertFailsWith<IOException> { runBlocking { client(FakeMastodon { throw IOException("unreachable") }).trendingTags("https://mastodon.social") } }
        assertEquals(MastodonFailure.NETWORK, MastodonFailure.of(error))
    }

    @Test
    fun anAddressThatIsNoInstanceIsNotConfigured() {
        assertEquals(MastodonFailure.NOT_CONFIGURED, failureOf { client(FakeMastodon { ok(list()) }).trendingTags("not a host") })
    }

    // ---- timelines, search, tags ----

    @Test
    fun theLocalTimelineAsksLocalTrue() = runBlocking {
        val fake = FakeMastodon { ok(list(statusJson("3", "https://mastodon.social/@a/3", "local"))) }
        val page = client(fake).publicTimeline("https://mastodon.social", local = true)
        assertEquals("/api/v1/timelines/public", fake.asked.single().path)
        assertEquals("limit=30&local=true", fake.asked.single().url.encodedQuery)
        assertEquals("local", page.items.single().text)
    }

    @Test
    fun theFederatedTimelinePagesWithMaxId() = runBlocking {
        val fake = FakeMastodon { ok(list(statusJson("2", "https://mastodon.social/@a/2", "older"))) }
        val page = client(fake).publicTimeline("https://mastodon.social", maxId = "3")
        assertEquals("limit=30&max_id=3", fake.asked.single().url.encodedQuery)
        assertEquals("2", page.items.single().id)
    }

    @Test
    fun aPublicTimelineTheMastodonApiRefusesIsReadAsMisskeyFeaturedNotes() = runBlocking {
        val fake = FakeMastodon {
            if (it.path == "/api/v1/timelines/public") answer(404, "no") else ok(list(obj("id" to "mk1", "text" to "from misskey", "user" to obj("username" to "neo", "host" to null))))
        }
        val page = client(fake).publicTimeline("https://misskey.io")

        assertEquals(listOf("GET /api/v1/timelines/public", "POST /api/notes/featured"), fake.asked.map { "${it.method} ${it.path}" })
        val featured = fake.asked.last()
        assertEquals("""{"limit":30}""", featured.body)
        assertEquals("application/json", featured.contentType)
        assertEquals("XTA Mastodon plugin", featured.headers["User-Agent"])
        assertEquals("from misskey", page.items.single().text)
        assertEquals("neo@misskey.io", page.items.single().acct)
    }

    @Test
    fun misskeysLocalTimelinePagesWithUntilId() = runBlocking {
        val fake = FakeMastodon {
            if (it.path == "/api/v1/timelines/public") answer(404) else ok(list(obj("id" to "mk0", "text" to "older note", "user" to obj("username" to "neo"))))
        }
        val page = client(fake).publicTimeline("https://misskey.io", local = true, maxId = "mk1")
        assertEquals("/api/notes/local-timeline", fake.asked.last().path)
        assertEquals("""{"limit":30,"untilId":"mk1"}""", fake.asked.last().body)
        assertEquals("older note", page.items.single().text)
    }

    @Test
    fun misskeysFeaturedNotesDoNotPage() = runBlocking {
        val fake = FakeMastodon { if (it.path == "/api/v1/timelines/public") answer(404) else ok(list()) }
        val page = client(fake).publicTimeline("https://misskey.io", maxId = "mk1")
        assertEquals(listOf("/api/v1/timelines/public"), fake.asked.map { it.path }, "an older page of featured notes is not asked for")
        assertTrue(page.items.isEmpty())
        assertNull(page.next)
    }

    @Test
    fun aRateLimitedPublicTimelineIsNotReadThroughMisskey() {
        val fake = FakeMastodon { answer(429) }
        assertEquals(MastodonFailure.RATE_LIMITED, failureOf { client(fake).publicTimeline("https://mastodon.social") })
        assertEquals(1, fake.asked.size)
    }

    @Test
    fun trendingStatusesFallBackToMisskeyFeaturedNotes() = runBlocking {
        val fake = FakeMastodon {
            if (it.path == "/api/v1/trends/statuses") answer(404) else ok(list(obj("id" to "mk1", "text" to "featured", "user" to obj("username" to "neo"))))
        }
        val posts = client(fake).trendingStatuses("https://misskey.io")
        assertEquals(listOf("/api/v1/trends/statuses", "/api/notes/featured"), fake.asked.map { it.path })
        assertEquals("""{"limit":20}""", fake.asked.last().body)
        assertEquals("featured", posts.single().text)
    }

    @Test
    fun aSearchReadsAccountsStatusesAndHashtags() = runBlocking {
        val fake = FakeMastodon {
            ok(obj("accounts" to list(), "statuses" to list(statusJson("4", "https://mastodon.social/@a/4", "about flutter")), "hashtags" to list(obj("name" to "flutter", "history" to list()))))
        }
        val page = client(fake).search("https://mastodon.social", " flutter ")
        assertEquals("/api/v2/search", fake.asked.single().path)
        assertEquals("flutter", fake.asked.single().query("q"))
        assertEquals("20", fake.asked.single().query("limit"))
        assertEquals("about flutter", page.posts.single().text)
        assertEquals("flutter", page.tags.single().name)
    }

    @Test
    fun nothingTypedIsNotSearchedFor() = runBlocking {
        val fake = FakeMastodon { error("nothing should be asked") }
        assertTrue(client(fake).search("https://mastodon.social", "   ").isEmpty)
        assertTrue(client(fake).tagTimeline("https://mastodon.social", "#").items.isEmpty())
    }

    @Test
    fun aTagTimelineDropsTheHash() = runBlocking {
        val fake = FakeMastodon { ok(list()) }
        client(fake).tagTimeline("https://mastodon.social", "#Photography", maxId = "5")
        assertEquals("/api/v1/timelines/tag/Photography", fake.asked.single().path)
        assertEquals("limit=30&max_id=5", fake.asked.single().url.encodedQuery)
    }

    @Test
    fun trendingTagsAddUpTheirHistory() = runBlocking {
        val fake = FakeMastodon { ok(list(obj("name" to "art", "history" to list(obj("uses" to "3"), obj("uses" to 4))))) }
        val tags = client(fake).trendingTags("https://mastodon.social", limit = 10)
        assertEquals("limit=10", fake.asked.single().url.encodedQuery)
        assertEquals(MastodonTag("art", null, 7), tags.single())
    }

    // ---- pages ----

    @Test
    fun aTimelinePagesBelowABoostsOwnIdAfterBoostsCollapse() = runBlocking {
        val fake = FakeMastodon { if (it.query("max_id") == null) ok(pageJson(30, "boost", wrapped = true)) else ok(list()) }
        val reader = client(fake)
        val first = reader.publicTimeline("https://mastodon.social", local = true)
        assertEquals("boost-29", first.next)

        val second = reader.publicTimeline("https://mastodon.social", local = true, maxId = first.next)
        assertEquals("boost-29", fake.asked.last().query("max_id"))
        assertNull(second.next, "an empty page is the last")
    }

    @Test
    fun aPageThatRepeatsItsCursorEndsPaging() = runBlocking {
        val fake = FakeMastodon { ok(pageJson(30, "seed")) }
        val page = client(fake).tagTimeline("https://studio.example", "art", maxId = "seed-29")
        assertEquals(30, page.items.size)
        assertNull(page.next)
    }

    @Test
    fun aPageThinnedByTheParserStillPagesOnWhereXtaStopped() = runBlocking {
        val statuses = pageJson(20).toMutableList()
        statuses[7] = obj("id" to "p-7", "url" to "https://example.social/@b/p-7", "content" to "", "account" to accountJson("2", "b"))
        val fake = FakeMastodon { ok(JsonArray(statuses)) }
        val page = client(fake).statuses("https://example.social", "2")
        assertEquals(19, page.items.size, "the status with nothing to show is dropped")
        assertEquals("p-19", page.next, "XTA counted the 19 it kept against a limit of 20 and ended the timeline here")
    }

    @Test
    fun aProfileOpensOnItsPinsThenItsTimeline() = runBlocking {
        val raw = pageJson(20, "post").toMutableList()
        raw[19] = statusJson("post-19", "https://example.social/@b/post-19", "pinned and recent", timelineId = "wrapper-19")
        val fake = FakeMastodon {
            when {
                it.path == "/api/v1/accounts/lookup" -> ok(accountJson("7", "maya", "maya"))
                it.query("pinned") == "true" -> ok(list(statusJson("post-19", "https://example.social/@b/post-19", "pinned and recent")))
                else -> ok(JsonArray(raw))
            }
        }
        val page = client(fake).profileAnywhere(listOf("https://studio.example"), "maya@studio.example")

        assertEquals("maya@studio.example", page.profile.acct)
        assertEquals("https://studio.example", page.instance)
        assertEquals("post-19", page.posts.first().id, "the pin leads")
        assertEquals(20, page.posts.size, "and is not repeated in the timeline")
        assertEquals(setOf("post-19"), page.pinnedIds)
        assertEquals("wrapper-19", page.next, "pins do not move the timeline's cursor")
    }

    @Test
    fun aProfileWithOnlyPinsHasNoFurtherPage() = runBlocking {
        val fake = FakeMastodon {
            when {
                it.path == "/api/v1/accounts/lookup" -> ok(accountJson("7", "maya"))
                it.query("pinned") == "true" -> ok(list(statusJson("pin", "https://example.social/@b/pin", "pin")))
                else -> ok(list())
            }
        }
        val page = client(fake).profileAnywhere(listOf("https://studio.example"), "maya@studio.example")
        assertEquals(listOf("pin"), page.posts.map { it.id })
        assertNull(page.next)
    }

    @Test
    fun pinsThatFailLeaveTheProfileStandingUnlessTheInstanceIsThrottling() {
        fun reader(pinned: XResponse) = client(
            FakeMastodon {
                when {
                    it.path == "/api/v1/accounts/lookup" -> ok(accountJson("7", "maya"))
                    it.query("pinned") == "true" -> pinned
                    else -> ok(list(statusJson("1", "https://example.social/@b/1", "post")))
                }
            },
        )
        val page = runBlocking { reader(answer(500)).profileAnywhere(listOf("https://studio.example"), "maya") }
        assertEquals(listOf("1"), page.posts.map { it.id })
        assertTrue(page.pinnedIds.isEmpty())
        assertEquals(MastodonFailure.RATE_LIMITED, failureOf { reader(answer(429)).profileAnywhere(listOf("https://studio.example"), "maya") })
    }

    // ---- walks over instances ----

    @Test
    fun aMissOnOneInstanceMovesToTheNext() = runBlocking {
        val fake = FakeMastodon { if (it.host == "example.social") answer(404) else ok(accountJson("9", "reader", "reader@example.social")) }
        val profile = client(fake).lookupAnywhere(listOf("https://example.social", "https://fallback.place"), "reader@example.social")
        assertEquals(listOf("example.social", "fallback.place"), fake.asked.map { it.host })
        assertEquals("reader@example.social", profile.acct)
    }

    @Test
    fun whenEveryInstanceFailsTheMostTellingFailureIsReported() {
        val fake = FakeMastodon { answer(if (it.host == "a.place") 429 else 404) }
        assertEquals(MastodonFailure.RATE_LIMITED, failureOf { client(fake).lookupAnywhere(listOf("https://a.place", "https://b.place"), "reader") })
        val refusing = FakeMastodon { answer(if (it.host == "a.place") 404 else 403) }
        assertEquals(MastodonFailure.UNAUTHORIZED, failureOf { client(refusing).lookupAnywhere(listOf("https://a.place", "https://b.place"), "reader") })
    }

    @Test
    fun noInstanceToAskIsNotConfigured() {
        assertEquals(MastodonFailure.NOT_CONFIGURED, failureOf { client(FakeMastodon { ok(list()) }).lookupAnywhere(emptyList(), "reader") })
    }

    @Test
    fun onlyBadAddressesFailAsNotConfiguredWhereXtaHitANullCheck() {
        val error = assertFailsWith<MastodonNotConfiguredException> {
            runBlocking { client(FakeMastodon { ok(list()) }).lookupAnywhere(listOf("not a host", "ftp://x.y"), "reader") }
        }
        assertEquals(MastodonFailure.NOT_CONFIGURED, MastodonFailure.of(error))
    }

    @Test
    fun aHangingFirstInstanceFailsOverInsteadOfBlocking() = runBlocking {
        val fake = FakeMastodon {
            if (it.host == "slow.social") delay(5_000)
            ok(accountJson("9", "reader", "reader@slow.social"))
        }
        val started = System.nanoTime()
        val profile = client(fake, timeout = 100, fallback = 1_000).lookupAnywhere(listOf("https://slow.social", "https://ok.social"), "reader@slow.social")
        assertEquals("reader@slow.social", profile.acct)
        assertEquals(listOf("slow.social", "ok.social"), fake.asked.map { it.host })
        assertTrue((System.nanoTime() - started) / 1_000_000 < 2_000, "the walk waited for the hanging instance")
    }

    @Test
    fun theInstanceThatAnsweredIsAskedFirstNextTime() = runBlocking {
        val fake = FakeMastodon { if (it.host == "miss.social") answer(404) else ok(accountJson("9", "reader", "reader@miss.social")) }
        val reader = client(fake)
        val instances = listOf("https://miss.social", "https://ok.social")
        reader.lookupAnywhere(instances, "reader@miss.social")
        fake.asked.clear()
        reader.lookupAnywhere(instances, "reader@miss.social")
        assertEquals(listOf("ok.social"), fake.asked.map { it.host })
    }

    @Test
    fun publicAndTagTimelinesSayWhichInstanceAnswered() = runBlocking {
        val fake = FakeMastodon { if (it.host == "closed.social") answer(401) else ok(pageJson(30)) }
        val reader = client(fake)
        val public = reader.publicTimelineAnywhere(listOf("https://closed.social", "https://open.social"), local = true)
        assertEquals("https://open.social", public.instance)
        assertEquals("p-29", public.value.next)
        val tag = reader.tagTimelineAnywhere(listOf("https://open.social"), "art")
        assertEquals(FromInstance("https://open.social", tag.value), tag)
    }

    // ---- conversations ----

    @Test
    fun aThreadResolvesTheStatusAddressThenReadsItsContext() = runBlocking {
        val fake = FakeMastodon {
            when (it.path) {
                "/api/v2/search" -> ok(obj("statuses" to list(statusJson("100", "https://other.social/@b/22", "Root", acct = "b@other.social"))))
                "/api/v1/statuses/100/context" ->
                    ok(obj("ancestors" to list(), "descendants" to list(statusJson("101", "https://other.social/@c/101", "Reply", username = "c", acct = "c@other.social"))))
                else -> answer(500, "unexpected ${it.url}")
            }
        }
        val thread = client(fake).thread("https://mastodon.social", seed)

        val search = fake.asked.first { it.path == "/api/v2/search" }
        assertEquals("q=https%3A%2F%2Fother.social%2F%40b%2F22&resolve=true&type=statuses&limit=1", search.url.encodedQuery)
        assertEquals("100", thread.status.id)
        assertEquals("Reply", thread.descendants.single().text)
        assertEquals("mastodon.social", thread.homeDomain)
    }

    @Test
    fun onItsOriginAThreadIsReadByTheIdInItsAddressWhenSearchIsRefused() = runBlocking {
        val fake = FakeMastodon {
            when (it.path) {
                "/api/v2/search" -> answer(401, """{"error":"Search queries that resolve remote resources are not allowed"}""")
                "/api/v1/statuses/22" -> ok(statusJson("22", "https://other.social/@b/22", "Root"))
                "/api/v1/statuses/22/context" -> ok(obj("ancestors" to list(), "descendants" to list(statusJson("23", "https://other.social/@c/23", "Reply", username = "c"))))
                else -> answer(500)
            }
        }
        val thread = client(fake).thread("https://other.social", seed.copy(id = "999"))
        assertEquals("22", thread.status.id)
        assertEquals("Reply", thread.descendants.single().text)
        assertTrue(fake.paths.none { it.endsWith("/api/v2/search") }, "found by its id, nothing else is asked")
    }

    @Test
    fun aRemotePostIsFoundAmongItsAuthorsStatusesWhenSearchIsClosed() = runBlocking {
        val fake = FakeMastodon {
            when {
                it.host == "closed.social" && it.path == "/api/v2/search" -> answer(401)
                it.host == "closed.social" -> answer(404, "gone")
                it.path == "/api/v2/search" -> answer(401)
                it.path == "/api/v1/accounts/lookup" -> ok(accountJson("7", "b", "b@closed.social"))
                it.path == "/api/v1/accounts/7/statuses" -> ok(list(statusJson("100", "https://closed.social/@b/22", "Root", acct = "b@closed.social")))
                it.path == "/api/v1/statuses/100/context" -> ok(obj("ancestors" to list(), "descendants" to list(statusJson("101", "https://closed.social/@c/101", "Via open", username = "c"))))
                it.path.startsWith("/api/v1/statuses/") -> answer(404, "missing")
                else -> answer(500)
            }
        }
        val thread = client(fake).threadAnywhere(listOf("https://closed.social", "https://open.social"), seed.copy(acct = "b@closed.social", url = "https://closed.social/@b/22"))

        assertEquals("100", thread.status.id)
        assertEquals("Via open", thread.descendants.single().text)
        assertTrue(fake.paths.any { it.startsWith("closed.social") } && fake.paths.any { it.startsWith("open.social") })
        val statuses = fake.asked.first { it.path == "/api/v1/accounts/7/statuses" }
        assertEquals("limit=40&exclude_replies=false", statuses.url.encodedQuery)
    }

    @Test
    fun aCoincidentalIdOnAnotherInstanceIsNotTakenForThePost() {
        val fake = FakeMastodon {
            when (it.path) {
                "/api/v1/statuses/22" -> ok(statusJson("22", "https://elsewhere.social/@z/22", "Someone else's post"))
                "/api/v1/accounts/lookup" -> answer(404)
                else -> answer(401)
            }
        }
        assertEquals(MastodonFailure.NOT_FOUND, failureOf { client(fake).thread("https://proxy.social", seed) })
    }

    // ---- who boosted, who quoted ----

    @Test
    fun boostersArePagedFromTheLinkHeaderOfTheInstanceThatHasThePost() = runBlocking {
        val next = "https://other.social/api/v1/statuses/22/reblogged_by?limit=40&max_id=77"
        val fake = FakeMastodon {
            when (it.path) {
                "/api/v1/statuses/22" -> ok(statusJson("22", "https://other.social/@b/22", "Root"))
                "/api/v1/statuses/22/reblogged_by" ->
                    XResponse(200, list(accountJson("5", "carol", "carol@x.example"), accountJson("6", "dave")).toString(), mapOf("Link" to listOf("<$next>; rel=\"next\", <https://other.social/api/v1/statuses/22/reblogged_by?min_id=99>; rel=\"prev\"")))
                else -> answer(404)
            }
        }
        val reader = client(fake)
        val first = reader.rebloggedBy(listOf("https://other.social"), seed)
        assertEquals(listOf("carol@x.example", "dave@other.social"), first.items.map { it.acct })
        assertEquals("limit=40", fake.asked.last().url.encodedQuery)
        assertEquals(next, first.next)

        reader.rebloggedBy(listOf("https://other.social"), seed, cursor = first.next)
        assertEquals(next, fake.asked.last().url.toString(), "the next page is read as the instance gave it")
    }

    @Test
    fun aLinkHeaderIsOnlyFollowedToTheNextPageOfTheSameList() {
        val current = okhttp3.HttpUrl.Builder().scheme("https").host("other.social").addPathSegments("api/v1/statuses/22/quotes").addQueryParameter("limit", "40").build()
        fun next(header: String) = MastodonClient.nextActivityPage(header, current)
        assertEquals("https://other.social/api/v1/statuses/22/quotes?max_id=5", next("<https://other.social/api/v1/statuses/22/quotes?max_id=5>; rel=\"next\""))
        assertEquals("https://other.social/api/v1/statuses/22/quotes?max_id=5", next("</api/v1/statuses/22/quotes?max_id=5>; rel=next"))
        assertNull(next("<https://evil.example/api/v1/statuses/22/quotes?max_id=5>; rel=\"next\""))
        assertNull(next("<https://other.social/api/v1/accounts/1/statuses?max_id=5>; rel=\"next\""))
        assertNull(next("<https://other.social/api/v1/statuses/22/quotes?limit=40>; rel=\"next\""), "a page leading to itself")
        assertNull(next("<https://other.social/api/v1/statuses/22/quotes?min_id=5>; rel=\"prev\""))
        assertNull(MastodonClient.nextActivityPage(null, current))
    }

    @Test
    fun aCursorForAnotherListOrInstanceIsRefusedUnread() {
        val fake = FakeMastodon { ok(list()) }
        val reader = client(fake)
        assertEquals(MastodonFailure.BAD_RESPONSE, failureOf { reader.quotes(listOf("https://other.social"), seed, cursor = "https://evil.example/api/v1/statuses/22/quotes") })
        assertEquals(MastodonFailure.BAD_RESPONSE, failureOf { reader.quotes(listOf("https://other.social"), seed, cursor = "https://other.social/api/v1/statuses/22/reblogged_by") })
        assertTrue(fake.asked.isEmpty())
    }

    @Test
    fun quotesAreReadOnTheInstanceThatHasThePost() = runBlocking {
        val fake = FakeMastodon {
            when (it.path) {
                "/api/v1/statuses/22" -> ok(statusJson("22", "https://other.social/@b/22", "Root"))
                "/api/v1/statuses/22/quotes" -> ok(list(statusJson("30", "https://other.social/@q/30", "Quoting it", username = "q")))
                else -> answer(404)
            }
        }
        val page = client(fake).quotes(listOf("https://other.social"), seed)
        assertEquals("q@other.social", page.items.single().acct)
        assertNull(page.next)
    }

    // ---- search box and Explore ----

    @Test
    fun aPastedStatusAddressIsReadFromItsOriginFirst() = runBlocking {
        val fake = FakeMastodon { ok(statusJson("123", "https://studio.example/@reader/123", "Found")) }
        val page = client(fake).find(" https://studio.example/@reader/123 ", listOf("https://configured.example"))
        assertEquals(listOf("studio.example/api/v1/statuses/123"), fake.paths)
        assertEquals("123", page.posts.single().id)
    }

    @Test
    fun anAddressIsLookedUpWhereItLivesAndAnythingElseSearched() = runBlocking {
        val fake = FakeMastodon {
            when (it.path) {
                "/api/v1/accounts/lookup" -> ok(accountJson("5", "reader", "reader@studio.example"))
                else -> ok(obj("accounts" to list(accountJson("6", "maya")), "statuses" to list(), "hashtags" to list()))
            }
        }
        val reader = client(fake)
        val found = reader.find("reader@studio.example", listOf("https://configured.example"))
        assertEquals("reader@studio.example", found.accounts.single().acct)
        assertEquals("studio.example/api/v1/accounts/lookup", fake.paths.single(), "the account's own instance first")

        reader.find("photography", listOf("https://configured.example"))
        assertEquals("configured.example/api/v2/search", fake.paths.last())
    }

    @Test
    fun nothingTypedReadsTheTrendingTags() = runBlocking {
        val fake = FakeMastodon { ok(list(obj("name" to "art", "history" to list()))) }
        val page = client(fake).find("  ", listOf("https://studio.example"))
        assertEquals("/api/v1/trends/tags", fake.asked.single().path)
        assertEquals(listOf("art"), page.tags.map { it.name })
    }

    @Test
    fun exploreKeepsItsPostsWhenTheTagsFail() = runBlocking {
        val fake = FakeMastodon {
            if (it.path == "/api/v1/trends/tags") answer(500) else ok(list(statusJson("1", "https://studio.example/@a/1", "trending"), statusJson("1", "https://studio.example/@a/1/", "again")))
        }
        val page = client(fake).explore(listOf("https://studio.example"))
        assertTrue(page.tags.isEmpty())
        assertEquals(listOf("trending"), page.posts.map { it.text }, "one post per address")
    }

    @Test
    fun exploreFailsWhenItsPostsFail() {
        val fake = FakeMastodon { if (it.path == "/api/v1/trends/tags") ok(list()) else answer(429) }
        assertEquals(MastodonFailure.RATE_LIMITED, failureOf { client(fake).explore(listOf("https://studio.example")) })
    }
}
