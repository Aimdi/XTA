package com.aimdi.xtanative.ui.post

import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.ai.TranslationStatus
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class TranslationRunsTest {
    private val post = XPost("1", XUser("u", "u", "U"), null, "hallo")
    private val english = TranslatedText { it.copy(text = "hello") }

    @Test
    fun `a translation goes on when the card that asked for it leaves`() = runBlocking {
        val runs = TranslationRuns(CoroutineScope(Job() + Dispatchers.Default))
        val answer = CompletableDeferred<TranslatedText>()
        val card = CoroutineScope(Job())
        val first = PostTranslation(post, runs.of("k"), runs) { answer.await() }
        first.translate()
        card.cancel()
        answer.complete(english)
        val back = PostTranslation(post, runs.of("k"), runs) { error("asked twice") }
        withTimeout(5_000) { while (back.status != TranslationStatus.TRANSLATED) kotlinx.coroutines.delay(10) }
        assertEquals("hello", back.shown.text)
    }

    @Test
    fun `a translation given is shown again without asking twice`() = runBlocking {
        val runs = TranslationRuns(CoroutineScope(Job() + Dispatchers.Unconfined))
        var asked = 0
        val translation = PostTranslation(post, runs.of("k"), runs) { asked++; english }
        translation.translate()
        translation.showOriginal()
        assertSame(post, translation.shown)
        translation.translate()
        assertEquals(TranslationStatus.TRANSLATED, translation.status)
        assertEquals(1, asked)
    }

    @Test
    fun `a failure says why and can be tried again`() = runBlocking {
        val runs = TranslationRuns(CoroutineScope(Job() + Dispatchers.Unconfined))
        var fail = true
        val translation = PostTranslation(post, runs.of("k"), runs) { if (fail) error("no") else english }
        translation.translate()
        assertEquals(TranslationStatus.FAILED, translation.status)
        fail = false
        translation.translate()
        assertEquals(TranslationStatus.TRANSLATED, translation.status)
    }

    @Test
    fun `the translation is shown on the post as it is now, not as it was when it was asked`() = runBlocking {
        val runs = TranslationRuns(CoroutineScope(Job() + Dispatchers.Unconfined))
        PostTranslation(post, runs.of("k"), runs) { english }.translate()
        val later = post.copy(likeCount = 7)
        val back = PostTranslation(later, runs.of("k"), runs) { error("asked twice") }
        back.translate()
        assertEquals("hello", back.shown.text)
        assertEquals(7L, back.shown.likeCount)
        assertSame(back.shown, back.shown)
    }

    @Test
    fun `clearing forgets every translation, the ones on screen and under way too`() = runBlocking {
        val runs = TranslationRuns(CoroutineScope(Job() + Dispatchers.Unconfined))
        val shown = PostTranslation(post, runs.of("a"), runs) { english }
        shown.translate()
        val hung = CompletableDeferred<TranslatedText>()
        val coming = PostTranslation(post, runs.of("b"), runs) { hung.await() }
        coming.translate()
        assertEquals(TranslationStatus.TRANSLATING, coming.status)
        runs.clear()
        hung.complete(english)
        assertEquals(TranslationStatus.ORIGINAL, shown.status)
        assertSame(post, shown.shown)
        assertEquals(TranslationStatus.ORIGINAL, coming.status)
        assertSame(post, coming.shown)
        var asked = 0
        PostTranslation(post, runs.of("a"), runs) { asked++; english }.translate()
        assertEquals(1, asked)
    }

    @Test
    fun `runs are kept apart by post, target and service, and the oldest go first`() {
        val runs = TranslationRuns(max = 2)
        val a = runs.of(TranslationRuns.key("1", "de", null))
        assertSame(a, runs.of(TranslationRuns.key("1", "de", null)))
        runs.of(TranslationRuns.key("1", "en", null))
        runs.of(TranslationRuns.key("2", "de", null))
        assert(a !== runs.of(TranslationRuns.key("1", "de", null)))
    }
}
