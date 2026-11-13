package com.aimdi.xtanative.ui.speech

import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.services.speech.Tts
import com.aimdi.xtanative.services.speech.TtsChoice
import com.aimdi.xtanative.services.speech.VoiceFailure
import com.aimdi.xtanative.services.speech.VoiceInstall
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SpeechPrefs
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VoiceSettingsTest {
    @Test
    fun choosingAnEngineClearsTheVoiceThatBelongedToTheLastOne() {
        val withVoice = VoiceChoices.withVoice(Settings(), SystemVoice("en-us-x-sfg", "en-US"))
        assertEquals(TtsChoice(voiceName = "en-us-x-sfg", voiceLocale = "en-US"), VoiceChoices.choiceOf(withVoice))
        val engine = VoiceChoices.withEngine(withVoice, "org.woheller69.ttsengine")
        assertEquals(TtsChoice(engine = "org.woheller69.ttsengine"), VoiceChoices.choiceOf(engine))
        assertEquals(Tts.SHERPA_ENGINE, VoiceChoices.preferSherpa(withVoice)[SpeechPrefs.engine])
        assertEquals(TtsChoice(), VoiceChoices.choiceOf(VoiceChoices.withEngine(engine, null)))
        assertNull(VoiceChoices.choiceOf(VoiceChoices.withVoice(withVoice, null)).voiceName)
    }

    @Test
    fun theSpeedStaysOnTheSlider() {
        assertEquals(1.0, VoiceChoices.withRate(Settings(), 3.0)[SpeechPrefs.rate])
        assertEquals(0.2, VoiceChoices.withRate(Settings(), 0.0)[SpeechPrefs.rate])
        assertEquals("0.45", VoiceChoices.rateText(0.45))
    }

    @Test
    fun sherpaIsListedOnceWhateverItIsCalled() {
        val engines = listOf(
            SystemEngine("com.google.android.tts", "Speech Services by Google"),
            SystemEngine("com.k2fsa.sherpa.onnx.tts.engine.en", "TTS Engine: Next-gen Kaldi"),
        )
        assertEquals(
            listOf(EngineRow.SystemDefault, EngineRow.Sherpa(installed = true), EngineRow.Engine(engines[0])),
            VoiceChoices.engineRows(engines),
        )
        assertEquals("com.k2fsa.sherpa.onnx.tts.engine.en", VoiceChoices.sherpaPackage(engines))
        assertEquals(EngineRow.Sherpa(installed = false), VoiceChoices.engineRows(engines.take(1))[1])
        assertEquals(Tts.SHERPA_ENGINE, VoiceChoices.sherpaPackage(emptyList()))
        assertEquals(Tts.SHERPA_ENGINE, VoiceChoices.chosenEngine(TtsChoice(engine = "com.k2fsa.sherpa.onnx.tts.engine.en")))
        assertEquals("a.b", VoiceChoices.chosenEngine(TtsChoice(engine = "a.b")))
    }

    @Test
    fun voicesAreListedByLocale() {
        val voices = listOf(SystemVoice("b", "fr-FR"), SystemVoice("a", "de-DE"), SystemVoice("c", "de-DE"))
        assertEquals(listOf("a", "c", "b"), VoiceChoices.sortedVoices(voices).map { it.name })
    }

    @Test
    fun eachDownloadStateOffersWhatCanBeDoneNow() {
        assertEquals(VoiceAction.DOWNLOAD, VoiceRows.action(VoiceInstall.Absent))
        assertEquals(VoiceAction.CANCEL, VoiceRows.action(VoiceInstall.Downloading(1, 2)))
        assertEquals(VoiceAction.WAIT, VoiceRows.action(VoiceInstall.Installing))
        assertEquals(VoiceAction.DELETE, VoiceRows.action(VoiceInstall.Ready(File("v"), 9)))
        assertEquals(VoiceAction.RETRY, VoiceRows.action(VoiceInstall.Failed(VoiceFailure.NETWORK)))
        assertNull(VoiceRows.status(VoiceInstall.Absent, 10))
        assertEquals(VoiceStatus.Progress(5, 100), VoiceRows.status(VoiceInstall.Downloading(5, null), 100))
        assertEquals(VoiceStatus.Ready(9), VoiceRows.status(VoiceInstall.Ready(File("v"), 9), 100))
        assertEquals(VoiceStatus.Failed(VoiceFailure.CHECKSUM), VoiceRows.status(VoiceInstall.Failed(VoiceFailure.CHECKSUM), 1))
        assertEquals(0.25f, VoiceRows.progress(VoiceInstall.Downloading(25, 100)))
        assertNull(VoiceRows.progress(VoiceInstall.Downloading(25, null)))
        assertNull(VoiceRows.progress(VoiceInstall.Installing))
    }

    @Test
    fun aVoiceLocaleIsNamedInItsOwnLanguage() {
        assertEquals("Deutsch (Deutschland)", VoiceRows.localeName("de_DE"))
        assertEquals("English (United States)", VoiceRows.localeName("en_US"))
        assertEquals("", VoiceRows.localeName(""))
    }

    @Test
    fun aPostIsReadWithoutItsLinks() {
        val text = "Read this  https://t.co/abc\n\n\n\nit is good"
        val link = XEntity.Link(11, 27, "https://t.co/abc", "https://example.com/story", "example.com/story")
        val post = XPost("1", XUser("1", "maya", "Maya"), null, text, entities = listOf(link))
        assertEquals("Read this\n\nit is good", SpeechTexts.post(post))
        assertEquals("Maya", SpeechTexts.title(post))
        val nameless = post.copy(author = XUser("2", "anon", " "))
        assertEquals("@anon", SpeechTexts.title(nameless))
        val repost = XPost("2", XUser("3", "sam", "Sam"), null, "", reposted = post)
        assertEquals("Maya", SpeechTexts.title(repost))
        assertEquals("Read this\n\nit is good\n\nSecond", SpeechTexts.thread(listOf(post, XPost("3", post.author, null, "Second"), XPost("4", post.author, null, ""))))
    }
}
