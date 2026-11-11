package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking
import org.jsoup.Jsoup

/** Reading a page of a megabyte without paying for it twice, and surviving what Meta reshapes. */
class ThreadsReliabilityTest {
    private val marker = "\"thread_items\""

    @Test
    fun theScriptsScannedAreTheOnesADocumentParserFinds() {
        listOf("post_page.html", "profile_page.html", "replies_page.html", "search_page.html").forEach { name ->
            val html = fixture(name)
            val parsed = Jsoup.parse(html).select("script[data-sjs]").map { it.data().trim() }.filter { marker in it }
            assertEquals(parsed, ThreadsScripts.embedded(html, marker), name)
        }
    }

    @Test
    fun onlyAnEmbeddedScriptThatCarriesTheMarkerIsCopied() {
        val html = """
            <script>{"thread_items":["plain script"]}</script>
            <script data-sjs-other>{"thread_items":["another attribute"]}</script>
            <script data-sjs>{"nothing":"here"}</script>
            <SCRIPT type="application/json" DATA-SJS="1">{"thread_items":["upper"]}</SCRIPT>
            <script data-sjs>{"thread_items":["unclosed"]}
        """
        assertEquals(listOf("""{"thread_items":["upper"]}""", """{"thread_items":["unclosed"]}"""), ThreadsScripts.embedded(html, marker))
        assertEquals(emptyList(), ThreadsScripts.embedded("<script data-sjs>{}</script>", marker))
        assertEquals(emptyList(), ThreadsScripts.embedded("<script data-sjs", marker))
        assertEquals(emptyList(), ThreadsScripts.embedded("", marker))
    }

    @Test
    fun aPageOfAMegabyteAndFiftyScriptsGivesItsChainsOnce() {
        val filler = (1..48).joinToString("") { "<script data-sjs>{\"define\":[\"${"x".repeat(20_000)}\",$it]}</script>" }
        val chains = (1..5).map { n -> listOf(postJson("$n", user = "zuck"), postJson("${n}r", user = "maya")) }
        val html = filler + page(chains) + page(chains.take(2)) + "<script>${"y".repeat(40_000)}</script>"
        assertTrue(html.length > 1_000_000)
        val read = ThreadsPages.chains(html)
        assertEquals((1..5).map { listOf("$it", "${it}r") }, read.map { chain -> chain.map { it.id } }, "a post repeated by a later script is not read twice")
    }

    @Test
    fun theProfileCardIsReadFromTheMetaTagsInOnePass() {
        val page = """
            <head><META NAME="description" content="ignored, og:description comes first" />
            <meta property="og:description" content="12 Followers • 3 Threads • Pots > plates" />
            <meta property="og:title" content="First (@maya)" /><meta property="og:title" content="Second (@maya)" />
            <meta content="https://cdn.example/a.jpg" name="twitter:image"></head>
        """
        val profile = ThreadsPages.guestProfile(page, "maya")!!
        assertEquals("First", profile.fullName, "the first tag of a name wins")
        assertEquals("Pots > plates", profile.biography, "a > inside a quoted value does not end the tag")
        assertEquals(12L, profile.followerCount)
        assertEquals("https://cdn.example/a.jpg", profile.profilePicUrl)
    }

    @Test
    fun jsonNestedBeyondReasonIsNoAnswerRatherThanACrash() {
        val deep = "[".repeat(5_000) + "]".repeat(5_000)
        assertNull(ThreadsJson.decode(deep))
        val page = "<script data-sjs>{\"thread_items\":[],\"x\":${"[".repeat(3_000)}${"]".repeat(3_000)}}</script>"
        assertEquals(emptyList(), ThreadsPages.chains(page))
    }

    @Test
    fun aSizeNoPictureHasIsNoSize() {
        val post = j(
            postJson("1", extra = mapOf("image_versions2" to mapOf("candidates" to listOf(mapOf("url" to "https://cdn/a.jpg", "width" to 5_000_000_000L, "height" to 600))))),
        )
        val media = ThreadsParser.media(post).single()
        assertNull(media.images.single().width)
        assertEquals(600, media.images.single().height)
    }

    @Test
    fun aGraphqlPostThatMentionsHtmlIsStillRead() = runBlocking {
        val meta = FakeMeta { asked ->
            if (asked.method == "GET") ok(profilePage()) else ok(graphql(postJson("1", user = "a", text = "my <html> lesson")))
        }
        val posts = direct(meta).guestAccount("a").items
        assertEquals(listOf("my <html> lesson"), posts.map { it.text })
        assertEquals(2, meta.asked.size, "not read again from the page")
    }

    @Test
    fun theHandlesWhoseIdsAreRememberedAreTheLatestFewHundred() = runBlocking {
        val ids = (1..ThreadsDirectClient.USER_IDS_KEPT).associate { "h$it" to "$it" }
        val memory = ThreadsMemory.InMemory(ThreadsKept(userIds = ids))
        val meta = FakeMeta { asked -> if (asked.method == "GET") ok(profilePage(userId = "777")) else ok(graphql(postJson("1", user = "new"))) }
        direct(meta, memory = memory).guestAccount("new")
        val kept = memory.kept.userIds
        assertEquals(ThreadsDirectClient.USER_IDS_KEPT, kept.size)
        assertEquals("777", kept["new"])
        assertNull(kept["h1"], "the one learnt longest ago makes room")
        assertEquals("2", kept["h2"])
    }
}
