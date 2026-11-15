package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.data.ReaderTranslation
import com.aimdi.xtanative.data.plugins.PluginTranslations
import com.aimdi.xtanative.services.ai.AiConfig
import com.aimdi.xtanative.services.ai.AiProvider
import com.aimdi.xtanative.services.translation.TranslationConfig
import com.aimdi.xtanative.services.translation.TranslationFailure
import com.aimdi.xtanative.services.translation.TranslationService
import com.aimdi.xtanative.store.settings.AiPrefs
import com.aimdi.xtanative.store.settings.ReadingPrefs
import com.aimdi.xtanative.store.settings.SecretPrefs
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.ui.plugins.PluginTranslationProblem
import com.aimdi.xtanative.ui.plugins.PluginTranslator
import com.aimdi.xtanative.ui.post.ServiceTranslation
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Settings › Translation, the plugins' view of the service, and an X post's words for it. */
class TranslationSettingsTest {
    @Test
    fun nothingIsSetUntilTheReaderChoosesAService() {
        val config = ReaderTranslation.configOf(Settings())
        assertEquals(TranslationService.OFF, config.service)
        assertNull(PluginTranslations.translator(config, "de"))
    }

    @Test
    fun aLibreTranslateServerIsSavedWithItsKeyApart() {
        val form = TranslationForm(TranslationService.LIBRE).withExample(TranslationForm.LIBRE_EXAMPLES[1]).copy(apiKey = " k ")
        assertTrue(form.asksServer)
        assertTrue(form.canTest)
        val saved = assertNotNull(form.saved(Settings(), TranslationConfig()))
        assertEquals("k", saved[ReadingPrefs.translationApiKey])
        assertFalse("k\"" in saved[ReadingPrefs.translation], "the key is not in the setting an export carries")
        assertTrue(SecretPrefs.isSecret(ReadingPrefs.translationApiKey.key))
        val config = ReaderTranslation.configOf(saved)
        assertEquals(TranslationConfig(TranslationService.LIBRE, "https://translate.fedilab.app", "k"), config)
        assertEquals(PluginTranslator("LibreTranslate", "pt-BR"), PluginTranslations.translator(config, "pt-BR"))
        assertEquals(PluginTranslator("LibreTranslate", "zh-Hant"), PluginTranslations.translator(config.copy(target = "zh_Hant"), "en"))
    }

    @Test
    fun whatCannotBeUsedIsNotSaved() {
        assertNull(TranslationForm(TranslationService.LIBRE).saved(Settings(), TranslationConfig()), "no address")
        assertFalse(TranslationForm(TranslationService.LIBRE).canTest)
        assertNull(TranslationForm(TranslationService.DEEPL).saved(Settings(), TranslationConfig()), "DeepL needs a key")
        assertNull(TranslationForm(TranslationService.AI).saved(Settings(), TranslationConfig()), "no AI set up")
        val ai = Settings().with(AiPrefs.baseUrl, AiProvider.OPENAI.baseUrl).with(AiPrefs.apiKey, "sk").with(AiPrefs.model, "gpt-4o-mini")
        val current = ReaderTranslation.configOf(ai)
        val saved = assertNotNull(TranslationForm(TranslationService.AI).saved(ai, current))
        assertEquals("OpenAI", ReaderTranslation.configOf(saved).translatorName)
        assertEquals(AiConfig(AiProvider.OPENAI.baseUrl, "sk", "gpt-4o-mini"), ReaderTranslation.configOf(saved).ai)
    }

    @Test
    fun anotherServiceForgetsTheLastTest() {
        val tested = TranslationForm(TranslationService.LIBRE, test = TranslationTest.Reached(30))
        assertNull(tested.withService(TranslationService.DEEPL).test)
        assertFalse(tested.copy(endpoint = "https://lt.example.org", test = TranslationTest.Testing).canTest, "one test at a time")
    }

    @Test
    fun theServicesFailuresReachPluginsByName() {
        assertEquals(PluginTranslationProblem.REFUSED, PluginTranslations.problem(TranslationFailure.REFUSED))
        assertEquals(PluginTranslationProblem.NOT_SET_UP, PluginTranslations.problem(TranslationFailure.NOT_CONFIGURED))
        assertEquals(PluginTranslationProblem.FAILED, PluginTranslations.problem(TranslationFailure.BAD_ANSWER))
    }

    @Test
    fun anXPostSendsTheWordsItShowsWithItsLinksWrittenOut() {
        val text = "Fish &amp; chips https://t.co/a https://t.co/media"
        val post = XPost(
            id = "1", author = XUser(id = "u", screenName = "a", name = "A"), createdAt = null, text = text, displayEnd = 31,
            entities = listOf(XEntity.Link(17, 31, "https://t.co/a", "https://example.com/menu", "example.com/menu")),
        )
        assertEquals("Fish & chips https://example.com/menu", ServiceTranslation.text(post))
        val shown = ServiceTranslation.applied(post, "Fisch & Pommes https://example.com/menu")
        assertEquals("Fisch &amp; Pommes https://example.com/menu", shown.text)
        assertEquals(emptyList(), shown.entities)
        assertNull(shown.displayEnd)
    }
}
