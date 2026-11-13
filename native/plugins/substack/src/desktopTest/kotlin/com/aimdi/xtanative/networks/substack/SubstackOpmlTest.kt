package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/** XTA's test/substack_opml_test.dart, without the settings screen. */
class SubstackOpmlTest {
    private fun pub(host: String, name: String) = SubstackPublication(host.substringBefore('.'), "https://$host", name)

    @Test
    fun anExportReadsBackAsTheSameSitesWithNamesEscaped() {
        val opml = SubstackOpml.export(listOf(pub("fieldnotes.substack.com", "Field \"Notes\" & Co"), pub("news.example.org", "Example <News>")), title = "Substack & co")
        assertTrue("text=\"Field &quot;Notes&quot; &amp; Co\"" in opml)
        assertTrue("xmlUrl=\"https://news.example.org/feed\" htmlUrl=\"https://news.example.org\"" in opml)
        assertTrue("<title>Substack &amp; co</title>" in opml)
        assertEquals(listOf("https://fieldnotes.substack.com", "https://news.example.org"), SubstackOpml.sites(opml))
    }

    @Test
    fun anotherReadersOpmlWithNestedOutlinesFeedOnlyEntriesDuplicatesAndJunk() {
        assertEquals(listOf("https://www.astralcodexten.com", "https://one.substack.com"), SubstackOpml.sites(fixture("opml.xml")))
        assertTrue(SubstackOpml.sites("not opml").isEmpty())
    }

    @Test
    fun anImportAddsTheNewLeavesTheFollowedAndARateLimitStopsIt() = runBlocking {
        val followed = pub("fieldnotes.substack.com", "Field Notes")
        val follows = mutableListOf(followed)
        val resolved = ArrayList<String>()
        val answers = mapOf<String, Any>(
            "fieldnotes.substack.com" to followed,
            "new.substack.com" to pub("new.substack.com", "New"),
            "nope.example" to Substack.notAPublication(),
            "slow.substack.com" to Substack.rateLimited("slow.substack.com", 0),
        )
        val progress = ArrayList<SubstackOpmlImport>()
        val result = SubstackOpml.import(
            sites = listOf("fieldnotes.substack.com", "new.substack.com", "nope.example", "slow.substack.com", "later.substack.com").map { "https://$it" },
            resolve = { site ->
                resolved += site
                when (val answer = answers[site.removePrefix("https://")]) {
                    is SubstackPublication -> answer
                    is Exception -> throw answer
                    else -> throw Substack.notAPublication()
                }
            },
            followOf = { SubstackFollows.followOf(follows, it) },
            follow = { follows += SubstackFollows.place(follows, it) },
            progress = { progress += it },
        )
        assertEquals(SubstackOpmlImport(added = 1, alreadyFollowed = 1, failed = 3, total = 5), result)
        assertEquals(4, resolved.size, "nothing is asked after the rate limit")
        assertEquals(listOf("Field Notes", "New"), follows.map { it.name })
        assertEquals(result, progress.last())
        assertEquals(0, progress.first().done)
    }
}

/** XTA's test/substack_body_search_test.dart. */
class SubstackBodySearchTest {
    private fun post(slug: String) = SubstackPost(slug, "Title $slug", slug, "https://fieldnotes.substack.com", "Field Notes", canonicalUrl = "https://fieldnotes.substack.com/p/$slug")

    @Test
    fun aPassageIsCutAroundTheFirstMatchMarkedWhereItWasCut() {
        val text = "One two three. The Quiet Harbour was full of boats that morning. Four five six."
        assertEquals("…hree. The Quiet Harbour was full…", SubstackBodySearch.snippet(text, text.lowercase(), "quiet harbour", around = 10))
        assertEquals(null, SubstackBodySearch.snippet(text, text.lowercase(), "absent"))
        assertEquals("A short harbour", SubstackBodySearch.snippet("A short harbour", "a short harbour", "harbour"), "nothing cut, nothing marked")
    }

    @Test
    fun keptBodiesAreSearchedOnceReadAndOnlyPostsWithAKeptBodyMatch() = runBlocking {
        val reads = ArrayList<String>()
        val search = SubstackBodySearch { post ->
            reads += post.slug
            if (post.slug == "kept") "<p>Notes on the <em>quiet harbour</em> at dawn.</p><script>harbour()</script>" else null
        }
        val posts = listOf(post("kept"), post("remote"))
        val found = search.search("Harbour", posts)
        assertEquals(listOf("fieldnotes.substack.com/p/kept"), found.keys.toList())
        assertTrue("quiet harbour" in found.values.single())
        assertEquals(1, search.search("dawn", posts).size)
        assertEquals(listOf("kept", "remote"), reads, "each body is read once")
        assertTrue(search.search("h", posts).isEmpty(), "one letter is too short to search bodies")
    }

    @Test
    fun aPostKeptAfterASearchIsFoundOnceTheSearchIsClearedAndTypedAgain() = runBlocking {
        var body: String? = null
        val search = SubstackBodySearch { body }
        val posts = listOf(post("later"))
        assertTrue(search.search("harbour", posts).isEmpty())
        body = "<p>The harbour at dawn.</p>"
        assertTrue(search.search("harbour", posts).isEmpty(), "a post found without a body is not asked again")
        search.search("", posts)
        assertEquals(1, search.search("harbour", posts).size)
    }
}
