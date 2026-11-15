package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.networks.threads.ThreadsPost
import com.aimdi.xtanative.networks.threads.ThreadsPostFacts
import com.aimdi.xtanative.networks.threads.ThreadsTextFragment
import com.aimdi.xtanative.services.ai.TranslationStatus
import com.aimdi.xtanative.ui.plugins.PluginTranslationException
import com.aimdi.xtanative.ui.plugins.PluginTranslationProblem
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking

/** Translate through the reader's translation service (PluginHost.translate), where Threads' own translation is not known to answer. */
class ThreadsServiceTranslationTest {
    private val german = ThreadsPost(
        id = "3456", handle = "zuck", authorName = "Mark", text = "Hallo Welt", url = "https://www.threads.com/@zuck/post/ABC",
        facts = ThreadsPostFacts(language = "de"),
    )

    @Test
    fun theServiceIsOfferedForWordsNotInItsLanguage() {
        val via = { post: ThreadsPost, serviceTarget: String? -> ThreadsTranslationRules.translator(post, "en-US", signedIn = false, available = true, serviceTarget = serviceTarget) }
        assertEquals(ThreadsTranslator.SERVICE, via(german, "en"), "a guest too: the service needs no Threads session")
        assertNull(via(german, null), "no service set")
        assertNull(via(german, "de"), "already in the service's language")
        assertEquals(ThreadsTranslator.SERVICE, via(german.copy(facts = ThreadsPostFacts()), "en"), "a post whose language Meta did not name keeps the offer")
        assertEquals(ThreadsTranslator.SERVICE, via(german.copy(facts = ThreadsPostFacts(language = "de", canTranslate = false)), "en"), "Meta's can_translate is Meta's own")
        assertNull(via(german.copy(text = " "), "en"), "no words")
        assertNull(via(german.copy(facts = ThreadsPostFacts(language = "zxx")), "en"), "marked as having no words")
        val spoiler = german.copy(fragments = listOf(ThreadsTextFragment(ThreadsTextFragment.Kind.TEXT, "Hallo Welt", spoiler = true)))
        assertNull(via(spoiler, "en"), "a translation could not keep a spoiler covered")
    }

    @Test
    fun threadsOwnTranslationStaysFirstWhereItIsKnownToAnswer() {
        assertEquals(
            ThreadsTranslator.THREADS,
            ThreadsTranslationRules.translator(german, "en-US", signedIn = true, available = true, serviceTarget = "en", pathKnown = true),
        )
        assertEquals(
            ThreadsTranslator.SERVICE,
            ThreadsTranslationRules.translator(german, "en-US", signedIn = true, available = true, serviceTarget = "en"),
            "while no path of Meta's is known, the service translates",
        )
        assertEquals(
            ThreadsTranslator.SERVICE,
            ThreadsTranslationRules.translator(german, "en-US", signedIn = false, available = true, serviceTarget = "en", pathKnown = true),
            "a guest has no Threads translation",
        )
    }

    @Test
    fun theServicesTranslationIsCreditedToItAndKept(): Unit = runBlocking {
        var asked = 0
        val translations = ThreadsTranslations(CoroutineScope(Dispatchers.Unconfined)) { post, _, viaService ->
            asked++
            if (viaService) ThreadsWords("Hello world", "LibreTranslate") else error("Threads is not asked")
        }
        translations.translate(german, "en", viaService = true, by = "LibreTranslate")?.join()
        val shown = translations.state.value.getValue(ThreadsTranslations.key(german))
        assertEquals(ThreadsTranslated(TranslationStatus.TRANSLATED, "en", "Hello world", "LibreTranslate"), shown)
        translations.showOriginal(german)
        translations.translate(german, "en", viaService = true, by = "LibreTranslate")
        assertEquals(1, asked, "back and forth asks once")
        assertEquals(TranslationStatus.TRANSLATED, translations.state.value.getValue(ThreadsTranslations.key(german)).status)
    }

    @Test
    fun aRefusalSaysWhichServiceFailedAndThreadsStaysAvailable(): Unit = runBlocking {
        val translations = ThreadsTranslations(CoroutineScope(Dispatchers.Unconfined)) { _, _, _ ->
            throw PluginTranslationException(PluginTranslationProblem.RATE_LIMITED)
        }
        translations.translate(german, "en", viaService = true, by = "DeepL")?.join()
        assertEquals(ThreadsTranslated(TranslationStatus.FAILED, "en", by = "DeepL"), translations.state.value.getValue(ThreadsTranslations.key(german)))
        assertEquals(true, translations.available.value)
    }
}
