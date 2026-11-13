package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsException
import com.aimdi.xtanative.networks.threads.ThreadsFailure
import com.aimdi.xtanative.networks.threads.ThreadsGhost
import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.networks.threads.ThreadsSearchTerms
import com.aimdi.xtanative.networks.threads.ThreadsTextAttachment
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.networks.threads.ThreadsTrend
import com.aimdi.xtanative.services.ai.TranslationStatus
import java.time.Duration
import java.time.Instant
import java.util.concurrent.atomic.AtomicInteger
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Round 19's post content as the screens decide it: translation, the ghost line, attached text, and what a kept post keeps. */
class ThreadsPostContentViewTest {
    private val post = ThreadsPost(id = "3456", handle = "zuck", authorName = "Mark", text = "Hallo Welt", url = "https://www.threads.com/@zuck/post/ABC")

    private fun translations(answer: suspend (ThreadsPost, String) -> String?) = ThreadsTranslations(CoroutineScope(Dispatchers.Unconfined), answer)

    @Test
    fun translationIsOfferedToASignedInReaderForWordsNotInTheirLanguage() {
        val german = post.copy(facts = ThreadsPostFacts(language = "de"))
        assertTrue(ThreadsTranslationRules.offers(german, "en-US", signedIn = true, available = true, pathKnown = true))
        assertTrue(ThreadsTranslationRules.offers(post, "en-US", signedIn = true, available = true, pathKnown = true), "a post whose language Meta did not name keeps the offer")
        assertFalse(ThreadsTranslationRules.offers(german, "de-DE", signedIn = true, available = true, pathKnown = true), "the reader's own language")
        assertFalse(ThreadsTranslationRules.offers(german, "en", signedIn = false, available = true, pathKnown = true), "Meta translates for a session only")
        assertFalse(ThreadsTranslationRules.offers(german, "en", signedIn = true, available = false, pathKnown = true), "no path answered this run")
        assertFalse(ThreadsTranslationRules.offers(german.copy(facts = ThreadsPostFacts(language = "de", canTranslate = false)), "en", signedIn = true, available = true, pathKnown = true))
        assertFalse(ThreadsTranslationRules.offers(post.copy(facts = ThreadsPostFacts(language = "und")), "en", signedIn = true, available = true, pathKnown = true))
        assertFalse(ThreadsTranslationRules.offers(german.copy(text = " "), "en", signedIn = true, available = true, pathKnown = true))
        val spoiler = german.copy(fragments = listOf(ThreadsTextFragment(ThreadsTextFragment.Kind.TEXT, "Hallo Welt", spoiler = true)))
        assertFalse(ThreadsTranslationRules.offers(spoiler, "en", signedIn = true, available = true, pathKnown = true), "a translation could not keep a spoiler covered")
        assertFalse(ThreadsTranslationRules.offers(german.copy(url = null, id = "x"), "en", signedIn = true, available = true, pathKnown = true), "no media id to ask by")
        assertFalse(ThreadsTranslationRules.offers(german, "en-US", signedIn = true, available = true), "no translation path Meta was seen to answer")
    }

    @Test
    fun aTranslationIsAskedOnceAndTheReaderGoesBackAndForthWithoutAskingAgain(): Unit = runBlocking {
        val asked = AtomicInteger()
        val kept = translations { _, target -> asked.incrementAndGet(); "Hello world ($target)" }
        kept.translate(post, "en")?.join()
        val key = ThreadsTranslations.key(post)
        assertEquals("ABC", key, "a post and its repost are one")
        assertEquals(ThreadsTranslated(TranslationStatus.TRANSLATED, "en", "Hello world (en)"), kept.state.value[key])
        assertEquals("Hello world (en)", kept.state.value[key]?.shown)
        kept.showOriginal(post)
        assertEquals(TranslationStatus.ORIGINAL, kept.state.value[key]?.status)
        assertNull(kept.state.value[key]?.shown)
        assertNull(kept.translate(post, "en"), "the kept translation shows at once")
        assertEquals(TranslationStatus.TRANSLATED, kept.state.value[key]?.status)
        assertEquals(1, asked.get())
        kept.translate(post, "fr")?.join()
        assertEquals("Hello world (fr)", kept.state.value[key]?.shown, "another language is asked for")
        assertEquals(2, asked.get())
    }

    @Test
    fun noTranslationFailsThatPostAndNoPathAtAllStopsTheOffer(): Unit = runBlocking {
        val empty = translations { _, _ -> null }
        empty.translate(post, "en")?.join()
        assertEquals(TranslationStatus.FAILED, empty.state.value["ABC"]?.status)
        assertTrue(empty.available.value)

        val nowhere = translations { _, _ -> throw ThreadsException(ThreadsFailure.NO_SUCH_FEED, "no path") }
        nowhere.translate(post, "en")?.join()
        assertEquals(TranslationStatus.FAILED, nowhere.state.value["ABC"]?.status)
        assertFalse(nowhere.available.value, "not offered again this run")

        val signedOut = translations { _, _ -> throw ThreadsException(ThreadsFailure.NOT_CONFIGURED, "no session") }
        signedOut.translate(post, "en")?.join()
        assertEquals(TranslationStatus.ORIGINAL, signedOut.state.value["ABC"]?.status)

        val throttled = translations { _, _ -> throw ThreadsException(ThreadsFailure.THROTTLED, "wait") }
        throttled.translate(post, "en")?.join()
        assertEquals(TranslationStatus.FAILED, throttled.state.value["ABC"]?.status)
        assertTrue(throttled.available.value, "a throttle says nothing of the path")
    }

    @Test
    fun onlyTheNewestTranslationsAreKept() {
        val kept = translations { p, _ -> p.id }
        (1..ThreadsTranslations.MAX + 5).forEach { kept.translate(post.copy(id = "p$it", url = null), "en") }
        assertEquals(ThreadsTranslations.MAX, kept.state.value.size)
        assertFalse("p1" in kept.state.value)
        assertTrue("p${ThreadsTranslations.MAX + 5}" in kept.state.value)
    }

    @Test
    fun aGhostPostSaysWholeHoursFromAnHourOnAndMinutesUnderIt() {
        assertEquals(ThreadsGhostLeft.Hours(3), ThreadsContentRules.ghostLeft(Duration.ofMinutes(170)))
        assertEquals(ThreadsGhostLeft.Hours(1), ThreadsContentRules.ghostLeft(Duration.ofMinutes(60)))
        assertEquals(ThreadsGhostLeft.Hours(24), ThreadsContentRules.ghostLeft(Duration.ofHours(24)))
        assertEquals(ThreadsGhostLeft.Minutes(45), ThreadsContentRules.ghostLeft(Duration.ofSeconds(44 * 60 + 1)))
        assertEquals(ThreadsGhostLeft.Minutes(1), ThreadsContentRules.ghostLeft(Duration.ofSeconds(5)))
        assertEquals(ThreadsGhostLeft.Unknown, ThreadsContentRules.ghostLeft(Duration.ZERO))
        assertEquals(ThreadsGhostLeft.Unknown, ThreadsContentRules.ghostLeft(null))
    }

    @Test
    fun attachedTextFoldsInAListWhenItIsLong() {
        assertFalse(ThreadsContentRules.attachmentFolds("A few words."))
        assertTrue(ThreadsContentRules.attachmentFolds("x".repeat(400)))
        assertTrue(ThreadsContentRules.attachmentFolds((1..7).joinToString("\n") { "line $it" }))
    }

    @Test
    fun aKeptPostKeepsItsLanguageViewsGhostMarkAttachedTextAndTrends() {
        val facts = ThreadsPostFacts(
            language = "de",
            canTranslate = true,
            captionId = "9",
            viewCount = 1234,
            ghost = ThreadsGhost(Instant.parse("2026-11-14T10:00:00Z")),
            attachment = ThreadsTextAttachment("A long made-up essay.", "On essays", "https://example.com/a"),
            trends = listOf(ThreadsTrend("Cats", ThreadsSearchTerms("Cats", tag = true), context = "12K threads")),
        )
        assertEquals(facts, ThreadsFactsJson.facts(J(ThreadsFactsJson.json(facts))))
        val unknownEnd = ThreadsPostFacts(ghost = ThreadsGhost(), canTranslate = false)
        assertEquals(unknownEnd, ThreadsFactsJson.facts(J(ThreadsFactsJson.json(unknownEnd))))
    }
}
