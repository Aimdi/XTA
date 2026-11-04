package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.networks.NetworkRequestException
import java.io.IOException
import java.net.UnknownHostException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * The client against a stand-in for Substack, as XTA's substack_plugin,
 * substack_protocol_regression, substack_audit_probes, substack_profile_nav
 * and substack_custom_domain tests drive it.
 */
class SubstackClientTest {
    private val fieldNotes = publication("fieldnotes", name = "Field Notes")
    private val customFieldNotes = SubstackPublication("fieldnotes", "https://www.fieldnotes.example", "Field Notes")

    private fun bylined(id: Int, slug: String, name: String, subdomain: String, custom: String? = null): String {
        val domain = custom?.let { ""","custom_domain":"$it"""" }.orEmpty()
        return """{"id":$id,"slug":"$slug","title":"$name post $id","publishedBylines":[{"publicationUsers":[{"publication":{"name":"$name","subdomain":"$subdomain"$domain}}]}]}"""
    }

    private fun rss(host: String, count: Int = 6) = buildString {
        append("""<rss version="2.0"><channel><title>Field Notes RSS</title><generator>Substack</generator><link>https://$host</link>""")
        repeat(count) {
            append("""<item><title>RSS $it</title><link>https://$host/p/rss-$it</link><dc:creator>Jordan</dc:creator>""")
            append("""<pubDate>Fri, 25 Sep 2026 10:00:00 GMT</pubDate><content:encoded><![CDATA[<p>Body $it</p>]]></content:encoded>""")
            append("""<enclosure url="https://media.example/episode-$it.mp3" type="audio/mpeg" /></item>""")
        }
        append("</channel></rss>")
    }

    // ---- posts ----

    @Test
    fun aPublicationsPostsAreReadFromItsPublicJsonWithoutBodies() = runBlocking {
        val http = FakeHttp { ok(fixture("archive.json")) }
        val page = clientOf(http).posts(fieldNotes)
        val asked = http.asked.single()
        assertEquals("https://fieldnotes.substack.com/api/v1/posts?limit=12&offset=0", asked.url.toString())
        assertEquals("application/json", asked.headers["Accept"])
        assertEquals(SubstackClient.USER_AGENT, asked.headers["User-Agent"])
        assertEquals(listOf("hello", "members-only", "episode-12", "a-clip"), page.items.map { it.slug })
        assertNull(page.items.first().bodyHtml)
        assertEquals("Field Notes", page.items.first().publicationName)
        assertNull(page.next, "four posts are a short page")
        val full = clientOf(FakeHttp { ok(fixture("archive.json")) }).posts(fieldNotes, limit = 4, offset = 8)
        assertEquals("12", full.next)
    }

    @Test
    fun theArchiveListingAnswersWhenPostsIsGone() = runBlocking {
        val http = FakeHttp { if (it.url.encodedPath == "/api/v1/posts") answer(404) else ok("""[{"id":2,"title":"From archive","slug":"from-archive"}]""") }
        val posts = clientOf(http).posts(fieldNotes, limit = 8, offset = 16).items
        assertEquals("From archive", posts.single().title)
        assertEquals("https://fieldnotes.substack.com/api/v1/archive?sort=new&limit=8&offset=16", http.urls.last().toString())
    }

    @Test
    fun aRateLimitEndsTheFallbackChainAndSaysSo() = runBlocking {
        val http = FakeHttp { answer(429) }
        val failure = assertFailsWith<NetworkRequestException> { clientOf(http).posts(fieldNotes, limit = 8) }
        assertTrue(failure.isSubstackRateLimit)
        assertEquals(SubstackTransport.RETRIES + 1, http.asked.size, "twelve before BUG-03 was fixed")
        assertEquals(setOf("fieldnotes.substack.com/api/v1/posts"), http.asked.map { it.hostPath }.toSet())
    }

    @Test
    fun aLeftoverCustomDomainIsReadOnItsSubstackTwinUnderTheFollowedAddress() = runBlocking {
        val http = FakeHttp {
            if (it.url.host.endsWith("platformer.news")) answer(404)
            else ok("[${bylined(1, "still-here", "Platformer", "platformer", "www.platformer.news")}]")
        }
        val posts = clientOf(http).posts(SubstackPublication("platformer", "https://www.platformer.news", "Platformer")).items
        assertEquals("still-here", posts.single().slug)
        assertEquals("https://www.platformer.news", posts.single().publicationBaseUrl)
        assertEquals("Platformer", posts.single().publicationName)
        assertEquals("platformer.substack.com/api/v1/posts", http.asked.last().hostPath)
    }

    @Test
    fun aFollowSavedAsWwwStillReadsTheTwinAndTakesItsNameFromTheByline() = runBlocking {
        val http = FakeHttp {
            when {
                it.url.host.contains("garbageday.email") -> answer(404)
                it.url.encodedPath == "/api/v1/posts" -> ok("[${bylined(9, "mrbeast", "Garbage Day", "garbageday")}]")
                else -> answer(404)
            }
        }
        val client = clientOf(http)
        val stub = SubstackPublication("www", "https://www.garbageday.email", "www")
        val found = client.publication(stub.baseUrl)
        assertEquals("Garbage Day", found.name)
        assertEquals("garbageday", found.subdomain)
        assertEquals("https://www.garbageday.email", found.baseUrl)
        assertEquals("Garbage Day", client.posts(stub).items.single().publicationName)
    }

    @Test
    fun anUnreachableCustomDomainNeverBorrowsAnotherPublicationsPosts() = runBlocking {
        val followed = SubstackPublication("examplecorp", "https://newsletter.example.com", "Example Weekly")
        val http = FakeHttp {
            when {
                it.url.host == "newsletter.substack.com" -> ok("""[{"id":1,"slug":"welcome","title":"Somebody else"}]""")
                it.url.host == "examplecorp.substack.com" && it.url.encodedPath == "/api/v1/posts" ->
                    ok("[${bylined(2, "own", "Also somebody else", "someone", "elsewhere.example")}]")
                else -> answer(503)
            }
        }
        assertFailsWith<NetworkRequestException> { clientOf(http).posts(followed, limit = 8) }
        assertTrue("examplecorp.substack.com" in http.hosts)
        assertFalse("newsletter.substack.com" in http.hosts, "BUG-04: the guessed twin was read before")
    }

    @Test
    fun theRssFallbackPagesLocallyWithoutAskingAgainAndKeepsEachPostsMetadata() = runBlocking {
        val http = FakeHttp { if (it.hostPath == "www.fieldnotes.example/feed") ok(rss(it.url.host)) else answer(404) }
        val client = clientOf(http)
        val first = client.posts(customFieldNotes, limit = 2)
        assertEquals(listOf("rss-0", "rss-1"), first.items.map { it.slug })
        assertEquals("<p>Body 0</p>", first.items.first().bodyHtml, "the first page comes from the feed itself")
        val asked = http.asked.size
        val second = client.posts(customFieldNotes, limit = 2, offset = 2).items
        assertEquals(listOf("rss-2", "rss-3"), second.map { it.slug })
        assertEquals(asked, http.asked.size)
        val post = second.first()
        assertEquals(customFieldNotes.baseUrl, post.publicationBaseUrl)
        assertEquals("Field Notes RSS", post.publicationName)
        assertEquals("Jordan", post.authorName)
        assertEquals("https://media.example/episode-2.mp3", post.audioUrl)
        assertEquals("podcast", post.type)
        assertTrue(post.publishedAt != null)
        assertNull(post.bodyHtml, "kept pages hold metadata, not whole bodies")
        assertTrue(client.posts(customFieldNotes, limit = 2, offset = 6).items.isEmpty())
    }

    @Test
    fun theRssPagingMemoryBelongsToOneSourceEvenWhenIdsMatch() = runBlocking {
        val client = clientOf(FakeHttp { if (it.hostPath == "www.fieldnotes.example/feed") ok(rss(it.url.host)) else answer(404) })
        client.posts(customFieldNotes, limit = 2)
        val moved = SubstackPublication("fieldnotes", "https://changed.example", "Changed")
        assertFailsWith<NetworkRequestException> { client.posts(moved, limit = 2, offset = 2) }
        Unit
    }

    @Test
    fun aSuccessfulJsonReadReplacesTheRssPagingMemory() = runBlocking {
        var json = false
        val http = FakeHttp {
            when {
                json && it.url.encodedPath == "/api/v1/posts" -> ok("""[{"id":"json-${it.url.queryParameter("offset")}","slug":"s","title":"T"}]""")
                it.hostPath == "www.fieldnotes.example/feed" -> ok(rss(it.url.host))
                else -> answer(404)
            }
        }
        val client = clientOf(http)
        client.posts(customFieldNotes, limit = 2)
        json = true
        assertEquals("json-0", client.posts(customFieldNotes, limit = 2).items.single().id)
        val before = http.asked.size
        assertEquals("json-2", client.posts(customFieldNotes, limit = 2, offset = 2).items.single().id)
        assertTrue(http.asked.size > before)
    }

    @Test
    fun aValidEmptyJsonReadLeavesNoStaleRssPagesButAFailedOneKeepsThem() = runBlocking {
        var emptied = false
        val emptying = clientOf(FakeHttp {
            when {
                emptied && it.url.encodedPath.startsWith("/api/v1/") -> ok("[]")
                !emptied && it.hostPath == "www.fieldnotes.example/feed" -> ok(rss(it.url.host))
                else -> answer(404)
            }
        })
        assertEquals(2, emptying.posts(customFieldNotes, limit = 2).items.size)
        emptied = true
        assertTrue(emptying.posts(customFieldNotes, limit = 2).items.isEmpty())
        assertTrue(emptying.posts(customFieldNotes, limit = 2, offset = 2).items.isEmpty())

        var offline = false
        val failing = clientOf(FakeHttp { if (!offline && it.hostPath == "www.fieldnotes.example/feed") ok(rss(it.url.host)) else answer(503) })
        failing.posts(customFieldNotes, limit = 2)
        offline = true
        assertFailsWith<NetworkRequestException> { failing.posts(customFieldNotes, limit = 2) }
        assertEquals(listOf("rss-2", "rss-3"), failing.posts(customFieldNotes, limit = 2, offset = 2).items.map { it.slug }, "kept for reading offline")
    }

    @Test
    fun aValidEmptyLaterPageIsAnAnswerButAnUnreadableArchiveIsAnError() = runBlocking {
        assertTrue(clientOf(FakeHttp { ok("[]") }).posts(customFieldNotes, offset = 12).items.isEmpty())
        val down = assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { answer(503) }).posts(customFieldNotes, offset = 12) }
        assertTrue(down.message!!.startsWith("Archive page unavailable ("), down.message)
        assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { ok("""{"error":true}""") }).posts(customFieldNotes, offset = 12) }
        Unit
    }

    @Test
    fun aLivingBeehiivSiteIsReadThereAndItsLeftoverTwinNeverAsked() = runBlocking {
        val http = FakeHttp {
            when (it.url.encodedPath) {
                "/posts" -> ok(fixture("beehiiv_posts.json"))
                "/" -> ok(fixture("beehiiv_home.html"))
                else -> answer(404)
            }
        }
        val client = clientOf(http)
        val found = client.publication("https://www.garbageday.email")
        assertFalse(http.hosts.any { it.endsWith("substack.com") }, "the leftover twin must not be asked")
        assertEquals(SubstackPublication("garbageday", "https://www.garbageday.email", "Garbage Day", logoUrl = "https://media.beehiiv.com/logo.png"), found)
        val posts = client.posts(found).items
        assertEquals(listOf("miles-morales-probably-couldn-t-happen-now", "premium-dispatch"), posts.map { it.slug })
        assertEquals("https://www.garbageday.email/p/miles-morales-probably-couldn-t-happen-now", posts.first().canonicalUrl)
        assertEquals("page=1", http.asked.first { it.url.encodedPath == "/posts" }.url.encodedQuery)
        assertEquals(listOf("premium-dispatch"), client.posts(found, offset = 1).items.map { it.slug }, "Beehiiv pages are skipped through to the offset")
    }

    @Test
    fun aBeehiivListingIsReadPageByPageUntilTheLimitOrItsLastPage() = runBlocking {
        val http = FakeHttp {
            when (it.url.queryParameter("page")) {
                "1" -> ok("""{"posts":[{"id":"a","web_title":"A","slug":"a"}],"pagination":{"total_pages":2}}""")
                "2" -> ok("""{"posts":[{"id":"b","web_title":"B","slug":"b"},{"id":"c","web_title":"C","slug":"c"}],"pagination":{"total_pages":2}}""")
                else -> answer(404)
            }
        }
        val beehiiv = SubstackPublication("garbageday", "https://www.garbageday.email", "Garbage Day")
        assertEquals(listOf("a", "b", "c"), clientOf(http).posts(beehiiv).items.map { it.slug })
        assertEquals(listOf("page=1", "page=2"), http.urls.filter { it.encodedPath == "/posts" }.map { it.encodedQuery })
        assertEquals(listOf("b"), clientOf(http).posts(beehiiv, limit = 1, offset = 1).items.map { it.slug })
    }

    // ---- publications ----

    @Test
    fun aSiteThatIsNotSubstackIsNotAPublication() = runBlocking {
        val failure = assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { answer(404) }).publication("https://www.platformer.news") }
        assertEquals(404, failure.code)
        assertTrue(failure.message!!.startsWith("This is not a Substack publication ("), failure.message)
    }

    @Test
    fun aPublicationNotFoundSaysWhatEachRouteAnswered() = runBlocking {
        val foreignFeed = """<rss version="2.0"><channel><title>Platformer</title><item><title>A</title><link>https://platformer.news/p/a</link></item></channel></rss>"""
        val http = FakeHttp {
            val path = it.url.encodedPath
            when {
                path == "/api/v1/posts" -> answer(403, "<html><head><title>Just a moment...</title></head></html>")
                path == "/api/v1/archive" -> ok("[]")
                path == "/posts" -> ok("<html>no</html>")
                path == "/feed" && it.url.host == "www.platformer.news" -> ok("<html><head><title>Platformer</title></head></html>")
                path == "/feed" && it.url.host == "platformer.news" -> ok(foreignFeed)
                path == "/" -> ok("<html><body>nothing</body></html>")
                else -> answer(404)
            }
        }
        val message = assertFailsWith<NetworkRequestException> { clientOf(http).publication("https://www.platformer.news") }.message!!
        val expected = listOf(
            "www.platformer.news/api/v1/posts: HTTP 403 \"Just a moment...\"",
            "www.platformer.news/api/v1/archive: HTTP 200, no posts",
            "www.platformer.news/posts: HTTP 200, no Beehiiv posts",
            "www.platformer.news/feed: HTTP 200, not a feed \"Platformer\"",
            "www.platformer.news/rss: HTTP 404",
            "platformer.news/feed: HTTP 200, a feed, not Substack's",
            "platformer.substack.com/: HTTP 200, no publication in the page",
        )
        expected.forEach { assertTrue(it in message, "$it in $message") }
        assertTrue(message.startsWith("This is not a Substack publication ("), message)
    }

    @Test
    fun aFailureListsAtMostTheFirstAddressesAsked() = runBlocking {
        val message = assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { answer(404) }).resolve("https://www.platformer.news/p/one") }.message!!
        val listed = message.substringAfter('(').substringBeforeLast(')').split("; ")
        assertTrue(listed.size <= SubstackTrail.LIMIT + 1, message)
        assertTrue(listed.all { it.contains(": HTTP 404") || it.endsWith(" more") }, message)
    }

    @Test
    fun aFollowedCustomDomainLooksItselfUpOnItsStoredSubdomainOnly() = runBlocking {
        val http = FakeHttp { answer(404) }
        assertFailsWith<NetworkRequestException> { clientOf(http).publication("https://newsletter.example.com", subdomainHint = "examplecorp") }
        assertTrue(http.hosts.containsAll(listOf("newsletter.example.com", "examplecorp.substack.com")))
        assertFalse("newsletter.substack.com" in http.hosts)
    }

    @Test
    fun twoDeepCustomDomainsFoundThroughRssGetTheirOwnIds() = runBlocking {
        val client = clientOf(FakeHttp { if (it.url.encodedPath == "/feed") ok(rss(it.url.host, 1)) else answer(404) })
        val alpha = client.publication("https://newsletter.alpha.example")
        val beta = client.publication("https://newsletter.beta.example")
        assertEquals(listOf("newsletter.alpha.example", "newsletter.beta.example"), listOf(alpha.id, beta.id), "BUG-06: both newsletter before")
        assertEquals("Field Notes RSS", alpha.name)
    }

    @Test
    fun aShareLinkResolvesToItsPublication() = runBlocking {
        val http = FakeHttp { ok("[${bylined(1, "hello", "Platformer", "platformer")}]") }
        val found = clientOf(http).resolve("https://open.substack.com/pub/platformer/p/hello")
        assertEquals(SubstackPublication("platformer", "https://platformer.substack.com", "Platformer"), found)
        assertEquals("platformer.substack.com/api/v1/posts", http.asked.first().hostPath)
    }

    @Test
    fun aLeftoverCustomDomainResolvesThroughItsTwinUnderItsOwnAddress() = runBlocking {
        val http = FakeHttp {
            when {
                it.url.host == "garbageday.substack.com" && it.url.encodedPath == "/api/v1/posts" -> ok("[${bylined(1, "hello", "Garbage Day", "garbageday")}]")
                else -> answer(404)
            }
        }
        val found = clientOf(http).resolve("https://www.garbageday.email")
        assertEquals("Garbage Day", found.displayName)
        assertEquals("garbageday", found.subdomain)
        assertEquals("https://www.garbageday.email", found.baseUrl)
    }

    @Test
    fun aProfileWhoseHandleIsNoPublicationResolvesThroughItsPrimaryPublication() = runBlocking {
        val http = FakeHttp {
            when {
                it.hostPath == "substack.com/api/v1/user/casey/public_profile" ->
                    ok("""{"handle":"casey","primaryPublication":{"name":"Platformer","subdomain":"platformer","custom_domain":"www.platformer.news"}}""")
                it.hostPath == "www.platformer.news/api/v1/posts" -> ok("[${bylined(1, "archive", "Platformer", "platformer", "www.platformer.news")}]")
                else -> answer(404)
            }
        }
        val found = clientOf(http).resolve("https://substack.com/@casey")
        assertEquals(SubstackPublication("platformer", "https://www.platformer.news", "Platformer"), found)
        assertTrue("casey.substack.com" in http.hosts, "the handle was tried as a subdomain first")
    }

    @Test
    fun aServiceLinkIsFollowedToWhereItsRedirectsLandWhichXtaNeverSaw() = runBlocking {
        val http = FakeHttp {
            when {
                it.url.host == "substack.com" -> answer(301, headers = mapOf("Location" to "https://open.substack.com/pub/harbour/p/tides"))
                it.url.host == "open.substack.com" -> answer(302, headers = mapOf("Location" to "https://harbour.substack.com/p/tides"))
                it.url.host == "harbour.substack.com" && it.url.encodedPath == "/api/v1/posts" -> ok("[${bylined(5, "tides", "Harbour", "harbour")}]")
                else -> answer(404)
            }
        }
        val found = clientOf(http).resolve("https://substack.com/home/post/p-147371717")
        assertEquals(SubstackPublication("harbour", "https://harbour.substack.com", "Harbour"), found)
        val hops = http.asked.filter { !it.followRedirects }
        assertEquals(listOf("substack.com", "open.substack.com", "harbour.substack.com"), hops.map { it.url.host }, "each hop asked without following")
    }

    @Test
    fun withNoConnectionAtAllTheCallerGetsTheIOExceptionNotNotAPublication() = runBlocking {
        val offline = FakeHttp { throw UnknownHostException(it.url.host) }
        assertFailsWith<IOException> { clientOf(offline).resolve("https://www.garbageday.email") }
        assertFailsWith<IOException> { clientOf(offline).posts(fieldNotes) }
        val deadDomain = FakeHttp { if (it.url.host.endsWith("platformer.news")) throw UnknownHostException(it.url.host) else answer(404) }
        val failure = assertFailsWith<NetworkRequestException> { clientOf(deadDomain).publication("https://www.platformer.news") }
        assertEquals(404, failure.code, "a host answered, so the verdict stands")
    }

    @Test
    fun enrichingKeepsTheFollowsIdAndAddressAndFailsQuietly() = runBlocking {
        val followed = SubstackPublication("example", "https://example.substack.com", "A long name", "Independent reporting. ")
        val http = FakeHttp {
            if (it.url.encodedPath == "/api/v1/posts") {
                ok("""[{"id":1,"slug":"s","title":"T","publishedBylines":[{"publicationUsers":[{"publication":{"name":"The enriched name","subdomain":"canonical","hero_text":"A richer biography","logo_url":"https://example.com/logo.png"}}]}]}]""")
            } else {
                answer(404)
            }
        }
        val enriched = clientOf(http).enrich(followed)!!
        assertEquals("example", enriched.id)
        assertEquals(followed.baseUrl, enriched.baseUrl)
        assertEquals("The enriched name", enriched.displayName)
        assertEquals("A richer biography", enriched.description)
        assertEquals("https://example.com/logo.png", enriched.logoUrl)
        assertNull(clientOf(FakeHttp { answer(503) }).enrich(followed))
        assertNull(clientOf(http).enrich(enriched), "nothing is asked for a publication that lacks nothing")
    }

    // ---- one post ----

    @Test
    fun aPostIsReadWholeWithItsPaywallFlag() = runBlocking {
        val http = FakeHttp { ok("""{"id":1,"title":"Hello","slug":"hello","audience":"only_paid","body_html":"","canonical_url":"https://fieldnotes.substack.com/p/hello"}""") }
        val post = clientOf(http).post(fieldNotes, "hello")
        assertEquals("https://fieldnotes.substack.com/api/v1/posts/hello", http.urls.single().toString())
        assertTrue(post.isPaywalled)
        assertEquals(SubstackReaderBody.PAYWALL, SubstackReading.bodyFor(post))
        val whole = clientOf(FakeHttp { ok(fixture("post.json")) }).post(fieldNotes, "a-long-read")
        assertTrue(whole.bodyHtml!!.contains("footnote-anchor"))
        assertEquals("Field Notes", whole.publicationName)
    }

    @Test
    fun aBeehiivPostIsReadFromItsPageNotFromTheLeftoverTwin() = runBlocking {
        val http = FakeHttp { if (it.url.encodedPath == "/p/microdramas") ok(fixture("beehiiv_post.html")) else answer(404) }
        val post = clientOf(http).post(SubstackPublication("garbageday", "https://www.garbageday.email", "Garbage Day"), "microdramas")
        assertFalse(http.hosts.any { it.endsWith("substack.com") }, "the leftover twin must not be asked")
        assertEquals("Microdramas are the death rattle of Hollywood", post.title)
        assertEquals("Ryan Broderick", post.authorName)
        assertEquals("https://www.garbageday.email/p/microdramas", post.canonicalUrl)
        assertEquals("Garbage Day", post.publicationName)
        assertEquals("text/html,application/xhtml+xml", http.asked.first { it.url.encodedPath == "/p/microdramas" }.headers["Accept"])
    }

    @Test
    fun aPostTheJsonCannotGiveComesFromTheFeedElseIsNotFound() = runBlocking {
        val fromFeed = clientOf(FakeHttp { if (it.hostPath == "www.fieldnotes.example/feed") ok(rss(it.url.host)) else answer(404) })
        val post = fromFeed.post(customFieldNotes, "rss-3")
        assertEquals("<p>Body 3</p>", post.bodyHtml)
        assertEquals("rss-3", post.id, "an RSS copy is named by its slug")
        val missing = assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { answer(404) }).post(customFieldNotes, "gone") }
        assertTrue(missing.message!!.startsWith("Post not found: gone ("), missing.message)
        assertTrue("www.fieldnotes.example/api/v1/posts/gone: HTTP 404" in missing.message!!, missing.message)
    }

    @Test
    fun aDomainThatAnswersThePostApiForTheSlugIsAPostAskedOnce() = runBlocking {
        val http = FakeHttp { ok("""{"id":9,"slug":"hello","title":"Hello","body_html":"<p>Hi</p>"}""") }
        val post = clientOf(http).probeCustomDomainPost("https://news.example.org", "hello")
        assertEquals("Hello", post?.title)
        assertEquals("https://news.example.org", post?.publicationBaseUrl)
        assertEquals(listOf("https://news.example.org/api/v1/posts/hello"), http.urls.map { it.toString() })

        val answers = ArrayDeque(listOf(ok("<html>blog</html>"), answer(404), ok("""{"id":1,"slug":"other","title":"Other"}""")))
        val others = FakeHttp { answers.removeFirst() }
        repeat(3) { assertNull(clientOf(others).probeCustomDomainPost("https://news.example.org", "hello")) }
        assertEquals(setOf("news.example.org/api/v1/posts/hello"), others.asked.map { it.hostPath }.toSet())
    }

    // ---- search ----

    @Test
    fun anArchiveSearchKeepsItsQueryOffsetLimitAndTheFollowedAddress() = runBlocking {
        val http = FakeHttp { ok("""[{"id":"one","slug":"article-one","title":"Article one","body_html":"<p>x</p>","canonical_url":"https://fieldnotes.substack.com/p/article-one"}]""") }
        val query = "climate & café / \"notes\""
        val posts = clientOf(http).search(customFieldNotes, query, limit = 7, offset = 21).items
        val url = http.urls.single()
        assertEquals("www.fieldnotes.example/api/v1/archive", "${url.host}${url.encodedPath}")
        assertEquals(listOf("sort" to "new", "search" to query, "limit" to "7", "offset" to "21"), url.queryParameterNames.map { it to url.queryParameter(it) })
        assertEquals(customFieldNotes.baseUrl, posts.single().publicationBaseUrl)
        assertEquals("Field Notes", posts.single().publicationName)
        assertNull(posts.single().bodyHtml)
    }

    @Test
    fun aSearchBoundsItsPageAndAValidEmptyAnswerStopsTheHosts() = runBlocking {
        val http = FakeHttp { ok("[]") }
        val client = clientOf(http)
        client.search(customFieldNotes, "one", limit = 10_000, offset = -5)
        client.search(customFieldNotes, "two", limit = 0, offset = 30)
        assertEquals(listOf("100", "1"), http.urls.map { it.queryParameter("limit") })
        assertEquals(listOf("0", "30"), http.urls.map { it.queryParameter("offset") })
        assertEquals(2, http.asked.size, "an empty answer is an answer")
        assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { answer(503) }).search(customFieldNotes, "none") }
        Unit
    }

    @Test
    fun anotherShapeHandsTheSearchToTheNextHost() = runBlocking {
        val http = FakeHttp { if (it.url.host == "fieldnotes.example") ok("""[{"id":"fallback","slug":"s","title":"T"}]""") else ok("""{"error":"unavailable"}""") }
        assertEquals("fallback", clientOf(http).search(customFieldNotes, "topic").items.single().id)
        assertEquals(listOf("www.fieldnotes.example", "fieldnotes.example"), http.hosts)
        assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { ok("""{"posts":[]}""") }).search(customFieldNotes, "topic") }
        assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { ok("{broken") }).search(customFieldNotes, "topic") }
        Unit
    }

    // ---- discussion and notes ----

    @Test
    fun theDiscussionIsAskedForAllCommentsBestFirst() = runBlocking {
        val http = FakeHttp { ok(fixture("comments.json")) }
        val comments = clientOf(http).comments(fieldNotes, "42")
        assertEquals("https://fieldnotes.substack.com/api/v1/post/42/comments?all_comments=true&sort=best_first", http.urls.single().toString())
        assertEquals(6, comments.size)
        assertTrue(clientOf(FakeHttp { ok("""{"comments":[]}""") }).comments(fieldNotes, "1").isEmpty(), "a readable empty discussion")
        assertFailsWith<NetworkRequestException> { clientOf(FakeHttp { answer(503) }).comments(fieldNotes, "1") }
        Unit
    }

    @Test
    fun anRssCopysCommentsAreAskedUnderTheApisNumber() = runBlocking {
        val http = FakeHttp { if (it.url.encodedPath == "/api/v1/posts/hello") ok("""{"id":777,"slug":"hello","title":"Hello"}""") else ok("""{"comments":[]}""") }
        val rssCopy = SubstackPost("hello", "Hello", "hello", "https://fieldnotes.substack.com", "Field Notes")
        clientOf(http).commentsOf(rssCopy)
        assertEquals(listOf("/api/v1/posts/hello", "/api/v1/post/777/comments"), http.urls.map { it.encodedPath }, "the slug was sent before")
        val apiCopy = rssCopy.copy(id = "4242")
        val direct = FakeHttp { ok("""{"comments":[]}""") }
        clientOf(direct).commentsOf(apiCopy)
        assertEquals(listOf("/api/v1/post/4242/comments"), direct.urls.map { it.encodedPath })
    }

    @Test
    fun notesComeFromAHostsReaderFeedWithTheirCursor() = runBlocking {
        val http = FakeHttp { ok(fixture("reader_feed.json")) }
        val client = clientOf(http)
        val page = client.notes()
        assertEquals(listOf("1", "2", "4"), page.items.map { it.id }, "notes without a readable id or body are left out")
        assertEquals("cursor-2", page.next)
        client.notes(host = "fieldnotes.substack.com", cursor = "cursor-2", limit = 10)
        assertEquals("https://substack.com/api/v1/reader/feed?limit=20", http.urls[0].toString())
        assertEquals("https://fieldnotes.substack.com/api/v1/reader/feed?limit=10&cursor=cursor-2", http.urls[1].toString())
        assertNull(clientOf(FakeHttp { ok("""{"items":[],"nextCursor":4}""") }).notes().next)
    }

    // ---- discovery ----

    @Test
    fun publicationSearchAsksSubstackAndKeepsOnlyAddressablePublications() = runBlocking {
        val http = FakeHttp { ok(fixture("publication_search.json")) }
        val page = clientOf(http).searchPublications(" byte ", page = 2)
        assertEquals("https://substack.com/api/v1/publication/search?query=byte&page=2", http.urls.single().toString())
        assertEquals(listOf("bytebytego", "platformer"), page.items.map { it.id })
        assertEquals("3", page.next)
        assertTrue(clientOf(http).searchPublications("  ").items.isEmpty())
    }

    @Test
    fun findingPublicationsResolvesAddressesAndHandlesButSearchesNames() = runBlocking {
        val asked = ArrayList<String>()
        val http = FakeHttp {
            asked += it.hostPath
            when {
                it.url.encodedPath == "/api/v1/publication/search" -> answer(503)
                it.url.host == "one.substack.com" && it.url.encodedPath == "/api/v1/posts" -> ok("[${bylined(1, "s", "One", "one")}]")
                else -> answer(404)
            }
        }
        val client = clientOf(http)
        assertEquals("one", client.findPublications("https://one.substack.com").items.single().id)
        assertFalse(asked.any { it.endsWith("/publication/search") }, "an address needs no name search")
        assertEquals("one", client.findPublications("one").items.single().id, "a handle is tried when the search fails")
        assertFailsWith<NetworkRequestException> { client.findPublications("Dr. Science") }
        assertTrue(SubstackClient.directInput("@casey"))
        assertTrue(SubstackClient.directInput("www.example.com/p/x"))
        assertFalse(SubstackClient.directInput("Dr. Science"))
        assertFalse(SubstackClient.directInput("climate"))
    }

    @Test
    fun anEmptyNameSearchIsAnAnswerAndAMultiWordNameIsNeverAHandle() = runBlocking {
        val http = FakeHttp { if (it.url.encodedPath == "/api/v1/publication/search") ok("""{"results":[]}""") else answer(404) }
        assertTrue(clientOf(http).findPublications("not a publication").items.isEmpty())
        assertTrue(clientOf(http).discover("climate change weekly").isEmpty())
        assertEquals(listOf("substack.com"), http.hosts.distinct(), "nothing but the search was asked")
    }

    @Test
    fun categoriesAndTheirLeaderboardsAreReadFromSubstack() = runBlocking {
        val http = FakeHttp {
            if (it.url.encodedPath == "/api/v1/categories") ok(fixture("categories.json"))
            else ok("""{"publications":[{"subdomain":"science1","name":"Science One"},{"name":"nameless"}]}""")
        }
        val client = clientOf(http)
        assertEquals(listOf("Science", "Arts"), client.categories().map { it.name }, "top level, not deprecated, named, with a number")
        val page = client.categoryPublications(1, page = 3)
        assertEquals("https://substack.com/api/v1/category/public/1/all?page=3", http.urls.last().toString())
        assertEquals(listOf("science1"), page.items.map { it.id })
        assertEquals("4", page.next)
    }

    @Test
    fun similarPublicationsAreTheAuthorsRecommendationsThenSearchHits() = runBlocking {
        val http = FakeHttp {
            when (it.url.encodedPath) {
                "/recommendations" -> ok(fixture("recommendations.html"))
                "/api/v1/publication/search" -> ok("""{"results":[{"name":"Platformer","subdomain":"platformer"},{"name":"User Mag","subdomain":"usermag"}]}""")
                else -> answer(404)
            }
        }
        val similar = clientOf(http).similar(publication("platformer", name = "Platformer"))
        assertEquals(listOf("peterwildeford", "bigtechnology", "usermag"), similar.map { it.publication.id })
        assertEquals("Platformer", http.urls.last().queryParameter("query"))
        assertEquals("text/html,application/xhtml+xml", http.asked.first().headers["Accept"])
        val failing = FakeHttp { answer(500) }
        assertFailsWith<NetworkRequestException> { clientOf(failing).similar(publication("platformer")) }
        Unit
    }
}
