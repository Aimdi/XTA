package com.aimdi.xtanative.networks.threads

import com.aimdi.xtanative.core.x.http.XResponse
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

/**
 * What a post carries beside its caption (round 19): its language and
 * whether Meta translates it (seen live), its views, its ghost mark, its
 * attached text and its trends (keys guessed, read under each likely name),
 * and Meta's translation. Each is harmless when gone or in another shape.
 * The values are made up.
 */
class ThreadsPostContentTest {
    private val time = FakeTime()

    private fun read(info: Map<String, Any?>? = null, extra: Map<String, Any?> = emptyMap(), caption: Map<String, Any?>? = null): ThreadsPost {
        val post = postJson("1", info = info, extra = extra).let { if (caption == null) it else it + ("caption" to caption) }
        return ThreadsParser.post(j(post))!!
    }

    @Test
    fun theLanguageAndWhetherMetaTranslatesAreReadAsCiSawThem() {
        val post = read(extra = mapOf("detected_language" to "de"), caption = mapOf("text" to "Hallo", "can_translate" to true, "has_translation" to true, "pk" to 17_900_000_000_000_001L))
        assertEquals("de", post.facts.language)
        assertEquals(true, post.facts.canTranslate)
        assertEquals("17900000000000001", post.facts.captionId)
        val refused = read(caption = mapOf("text" to "x", "can_translate" to false, "lang" to "pt-BR"))
        assertEquals(false, refused.facts.canTranslate)
        assertEquals("pt-BR", refused.facts.language, "the caption's own lang stands in")
        val odd = read(extra = mapOf("detected_language" to "not a language!"), caption = mapOf("text" to "x", "pk" to "abc"))
        assertNull(odd.facts.language)
        assertNull(odd.facts.canTranslate, "Meta said nothing")
        assertNull(odd.facts.captionId)
    }

    @Test
    fun aViewCountIsReadUnderTheNamesItIsLikelyToTakeAndOnlyAsANumber() {
        assertEquals(1234L, read(extra = mapOf("view_count" to 1234)).facts.viewCount)
        assertEquals(56L, read(info = mapOf("impression_count" to "56")).facts.viewCount)
        assertEquals(7L, read(info = mapOf("view_count_info" to mapOf("count" to 7))).facts.viewCount)
        assertNull(read(extra = mapOf("view_count" to -1)).facts.viewCount)
        assertNull(read(extra = mapOf("view_count" to "1.2K")).facts.viewCount, "words are not a count")
        assertNull(read().facts.viewCount)
    }

    @Test
    fun aGhostPostIsMarkedOnlyWhereMetaSaysSoAndEndsWhenMetaWritesOrADayAfter() {
        val written = read(extra = mapOf("is_ghost_post" to true, "expiring_at" to 1_720_003_600L))
        assertEquals(Instant.ofEpochSecond(1_720_003_600L), written.facts.ghost?.endsAt)
        val nested = read(info = mapOf("ghost_post_info" to mapOf("expiration_timestamp" to 1_720_007_200_000L)))
        assertEquals(Instant.ofEpochMilli(1_720_007_200_000L), nested.facts.ghost?.endsAt, "milliseconds are told from seconds")
        val unwritten = read(info = mapOf("is_ghost_post" to true))
        assertEquals(Instant.ofEpochSecond(1_720_000_000L).plusSeconds(24 * 3600), unwritten.facts.ghost?.endsAt, "a day after it was posted")
        assertNull(read(extra = mapOf("expiring_at" to 1_720_003_600L)).facts.ghost, "an expiry alone is no ghost")
        assertNull(read(info = mapOf("ghost_post_info" to mapOf("is_ghost_post" to false))).facts.ghost)
        assertNull(read(extra = mapOf("is_ghost_post" to "true")).facts.ghost, "only a true flag marks a post")
        val ghost = ThreadsGhost(Instant.ofEpochSecond(1_000))
        assertEquals(100L, ghost.left(Instant.ofEpochSecond(900))?.seconds)
        assertEquals(0L, ghost.left(Instant.ofEpochSecond(2_000))?.seconds, "never less than nothing")
        assertNull(ThreadsGhost().left(Instant.EPOCH))
    }

    @Test
    fun attachedTextIsReadAsThePublicApiNamesItItsFragmentsOrPlainWords() {
        val attached = read(info = mapOf("text_attachment" to mapOf("plaintext" to "  A long made-up essay.  ", "title" to "On essays", "link_attachment_url" to "https://l.threads.com/?u=https%3A%2F%2Fexample.com%2Fa")))
        assertEquals(ThreadsTextAttachment("A long made-up essay.", "On essays", "https://example.com/a"), attached.facts.attachment)
        val fragments = read(extra = mapOf("text_attachment" to mapOf("text_fragments" to mapOf("fragments" to listOf(mapOf("plaintext" to "One "), mapOf("plaintext" to "two"))))))
        assertEquals("One two", fragments.facts.attachment?.text)
        assertEquals("Plain", read(info = mapOf("long_text_attachment" to "Plain")).facts.attachment?.text)
        assertNull(read(info = mapOf("text_attachment" to mapOf("link_attachment_url" to "http://example.com"))).facts.attachment, "no words, no attachment")
        assertNull(read(info = mapOf("text_attachment" to mapOf("text" to "x", "link_attachment_url" to "http://example.com"))).facts.attachment?.linkUrl, "https only")
        assertNull(read(info = mapOf("text_attachment" to 3)).facts.attachment)
    }

    @Test
    fun aPostsTrendsAreReadAsAListAWrapperOrOneTrendEachOnceAtMostThree() {
        val listed = read(info = mapOf("related_trends_info" to listOf(mapOf("trend_name" to "Made-up Final"), mapOf("title" to "#Cats"), mapOf("trend_name" to "made-up final"), mapOf("nothing" to 1))))
        assertEquals(listOf("Made-up Final", "Cats"), listed.facts.trends.map { it.title })
        assertEquals(ThreadsSearchTerms("Cats", tag = true), listed.facts.trends[1].terms)
        val wrapped = read(info = mapOf("related_trends_info" to mapOf("trends" to listOf(mapOf("node" to mapOf("name" to "One"))), "title" to "Related")))
        assertEquals(listOf("One"), wrapped.facts.trends.map { it.title }, "a list under the wrapper, not the wrapper's own title")
        val single = read(info = mapOf("related_trends_info" to mapOf("query_text" to "solo", "social_context" to "12K threads")))
        assertEquals(ThreadsTrend("solo", ThreadsSearchTerms("solo", tag = false), context = "12K threads"), single.facts.trends.single())
        val many = read(info = mapOf("related_trends_info" to (1..5).map { mapOf("title" to "T$it") }))
        assertEquals(ThreadsPostContent.MAX_TRENDS, many.facts.trends.size)
        assertTrue(read(info = mapOf("related_trends_info" to "x")).facts.trends.isEmpty())
        // The shape CI's live probe saw (13 Nov 2026): one object with trending_keyword and related_threads_count; made-up values.
        val live = read(info = mapOf("related_trends_info" to mapOf("trending_keyword" to "Made-up Cup", "related_threads_count" to 12)))
        assertEquals(listOf("Made-up Cup"), live.facts.trends.map { it.title })
        assertEquals(ThreadsSearchTerms("Made-up Cup", tag = false), live.facts.trends.single().terms)
        assertTrue(read().facts.isEmpty, "a post without the keys has nothing more")
    }

    @Test
    fun aTranslationIsAskedAsACaptionInTheReadersLanguageAndReadUnderItsLikelyNames() {
        assertEquals(listOf("id" to "9", "type" to "1"), ThreadsTranslation.params("42", "9"))
        assertEquals(listOf("id" to "42", "type" to "1"), ThreadsTranslation.params("42", " "))
        assertEquals("de-DE,de;q=0.9,en;q=0.8", ThreadsTranslation.acceptLanguage("de_DE"))
        assertEquals("en-US,en;q=0.9", ThreadsTranslation.acceptLanguage("en-US"))
        assertEquals("ja,en;q=0.9", ThreadsTranslation.acceptLanguage("ja"))
        assertEquals("Hallo", ThreadsTranslation.text(j(mapOf("translation" to " Hallo ", "status" to "ok"))))
        assertEquals("Hej", ThreadsTranslation.text(j(mapOf("comment_translations" to listOf(mapOf("id" to "1", "translation" to "Hej"))))))
        assertEquals("Hola", ThreadsTranslation.text(j(mapOf("data" to mapOf("translated_text" to "Hola")))))
        assertNull(ThreadsTranslation.text(j(mapOf("status" to "ok", "translation" to ""))))
    }

    @Test
    fun theSessionAsksThePathsInTurnInTheReadersLanguageAndANoPathAnswerSaysSo(): Unit = runBlocking {
        val meta = FakeMeta { asked ->
            when (asked.path) {
                "/api/v1/language/translate/" -> XResponse(404, "")
                else -> ok("""{"translation":"Hallo Welt","status":"ok"}""")
            }
        }
        val client = direct(meta, ThreadsSession(COOKIES), time = time)
        assertEquals("Hallo Welt", client.translation("42", "9", "de-DE"))
        val answered = meta.asked.last()
        assertEquals("/api/v1/text_feed/42/translate/", answered.path)
        assertEquals("9", answered.url.queryParameter("id"))
        assertEquals("1", answered.url.queryParameter("type"))
        assertEquals("de-DE,de;q=0.9,en;q=0.8", answered.headers["Accept-Language"])
        assertEquals("GET", answered.method, "a read only")
        meta.asked.clear()
        client.translation("42", null, "de")
        assertEquals(listOf("/api/v1/text_feed/42/translate/"), meta.asked.map { it.path }, "the path that answered is asked first")

        val nowhere = direct(FakeMeta { XResponse(404, "") }, ThreadsSession(COOKIES), time = time)
        assertEquals(ThreadsFailure.NO_SUCH_FEED, assertFailsWith<ThreadsException> { nowhere.translation("42", null, "de") }.failure)
        val guest = direct(FakeMeta { ok("{}") }, time = time)
        assertEquals(ThreadsFailure.NOT_CONFIGURED, assertFailsWith<ThreadsException> { guest.translation("42", null, "de") }.failure)
        assertFalse(runCatching { guest.translation("abc", null, "de") }.isSuccess, "a media id is a number")
    }
}
