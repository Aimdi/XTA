package com.aimdi.xtanative.networks.threads

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ThreadsSearchFoundTest {
    private fun tagged(pk: String, user: String, topic: String, mark: Map<String, Any?> = emptyMap()) =
        postJson(pk, user = user, info = mapOf("tag_header" to mapOf("display_name" to topic) + mark))

    @Test
    fun aSearchPageNamesTheTopicsItsPostsAreFiledUnderWithTheirMarks() {
        val body = page(
            listOf(
                listOf(tagged("1", "maya", "Ceramics", mapOf("community_emoji" to "🏺"))),
                listOf(tagged("2", "ola", "#Pottery", mapOf("community_favicon_url" to "https://cdn.example/p.png"))),
                listOf(tagged("3", "lee", "ceramics")),
                listOf(postJson("4", user = "dana")),
            ),
        )
        val found = ThreadsSearchReading.read(body, "ceramics", tag = false)
        assertEquals(listOf("1", "2", "3", "4"), found.posts.map { it.id })
        assertEquals(
            listOf(ThreadsTopic("Ceramics", emoji = "🏺"), ThreadsTopic("Pottery", iconUrl = "https://cdn.example/p.png")),
            found.topics,
            "each topic once, the first spelling kept, with the mark any of its headers carried",
        )
    }

    @Test
    fun aMarkThatIsNotTextOrNotASecureLinkIsLeftOut() {
        val body = page(listOf(listOf(tagged("1", "maya", "Books", mapOf("community_emoji" to mapOf("x" to 1), "community_favicon_url" to "http://plain/p.png")))))
        assertEquals(listOf(ThreadsTopic("Books")), ThreadsSearchReading.read(body, "books", tag = true).topics)
    }

    @Test
    fun aPageWithoutTopicsOrWithBrokenScriptsStillGivesItsPosts() {
        val body = page(listOf(listOf(postJson("1")))) + "<script data-sjs>{\"tag_header\": oops</script>"
        val found = ThreadsSearchReading.read(body, "post", tag = false)
        assertEquals(listOf("1"), found.posts.map { it.id })
        assertTrue(found.topics.isEmpty())
    }

    @Test
    fun theWordsAreOfferedAsATopicFirstThenTheTopicsTheyName() {
        val found = ThreadsSearchFound(emptyList(), listOf(ThreadsTopic("Gardening"), ThreadsTopic("Kiln firing"), ThreadsTopic("Kiln")))
        assertEquals(listOf("Kiln", "Kiln firing", "Gardening"), found.related("kiln", tag = false).map { it.name }, "kiln is a topic already")
        assertEquals(listOf("glaze", "Gardening", "Kiln firing", "Kiln"), found.related("glaze", tag = false).map { it.name })
        assertEquals(listOf("Kiln firing", "Gardening"), found.related("#kiln", tag = true).map { it.name }, "a topic's page leaves itself out")
        assertEquals(ThreadsTopic("Kiln"), found.topic("#KILN"))
        assertEquals(ThreadsTopic("Raku"), found.topic("#Raku"))
    }

    @Test
    fun peopleAreTheAuthorsOfTheResultsThoseTheWordsNameFirst() {
        val posts = listOf(
            ThreadsPost(id = "1", handle = "ola", authorName = "Ola", text = "kiln"),
            ThreadsPost(id = "2", handle = "ola", authorName = "Ola", text = "kiln again"),
            ThreadsPost(id = "3", handle = "maya.kiln", authorName = "Maya", text = "x", isVerified = true, avatarUrl = "https://a/m.jpg"),
            ThreadsPost(id = "4", handle = "dana", authorName = "Dana", text = "y", quoted = ThreadsPost(id = "5", handle = "lee", authorName = "Kiln Lee", text = "z")),
        )
        val people = ThreadsSearchReading.people(posts, "Kiln")
        assertEquals(listOf("maya.kiln", "lee", "ola", "dana"), people.map { it.username })
        assertEquals(true, people.first().isVerified)
        assertEquals("https://a/m.jpg", people.first().profilePicUrl)
    }

    @Test
    fun theClientReadsTheSearchPageOnceForPostsAndTopics() = runBlocking {
        val meta = FakeMeta { ok(page(listOf(listOf(tagged("9", "maya", "Ceramics", mapOf("community_emoji" to "🏺")))))) }
        val found = direct(meta).searchFound("Ceramics", tag = true)
        assertEquals("https://www.threads.com/search?q=Ceramics&serp_type=tags", meta.asked.single().url.toString())
        assertEquals(listOf("9"), found.posts.map { it.id })
        assertEquals("🏺", found.topic("ceramics").emoji)
    }
}
