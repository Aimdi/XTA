package com.aimdi.xtanative.services.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class TtsRulesTest {
    @Test
    fun sherpaIsRecognisedByItsPackageAndAnyNameLikeIt() {
        assertTrue(Tts.isSherpa(Tts.SHERPA_ENGINE))
        assertTrue(Tts.isSherpa("com.example.sherpa.tts"))
        assertTrue(Tts.isSherpa("TTS Engine: Next-gen Kaldi"))
        assertTrue(Tts.isSherpa("com.k2fsa.other"))
        assertFalse(Tts.isSherpa("com.google.android.tts"))
        assertFalse(Tts.isSherpa(null))
        assertFalse(Tts.isSherpa(""))
    }

    @Test
    fun theOfficialSherpaPackageIsPreferredOverAnAlias() {
        assertEquals(Tts.SHERPA_ENGINE, Tts.installedSherpa(listOf("com.google.android.tts", "org.example.sherpa", Tts.SHERPA_ENGINE)))
        assertEquals("app.sherpa.onnx", Tts.installedSherpa(listOf("com.google.android.tts", "app.sherpa.onnx")))
        assertNull(Tts.installedSherpa(listOf("com.google.android.tts")))
        assertFalse(Tts.sherpaInstalled(emptyList()))
    }

    @Test
    fun askingForSherpaBindsTheInstalledPackageOrTheOfficialIdToTry() {
        assertEquals("org.woheller69.sherpa", Tts.sherpaEngineId(listOf("org.woheller69.sherpa")))
        assertEquals(Tts.SHERPA_ENGINE, Tts.sherpaEngineId(emptyList()))
        assertEquals("org.woheller69.sherpa", Tts.boundEngine(Tts.SHERPA_ENGINE, listOf("org.woheller69.sherpa"), "com.google.android.tts"))
        assertEquals("com.other.tts", Tts.boundEngine("com.other.tts", emptyList(), "com.google.android.tts"))
        assertEquals("com.google.android.tts", Tts.boundEngine(null, emptyList(), "com.google.android.tts"))
        assertNull(Tts.boundEngine("", emptyList(), ""))
    }

    @Test
    fun thePickerListsSherpaOnceAndTheDefaultEvenWhenHidden() {
        assertEquals(listOf("com.google.android.tts"), Tts.nonSherpa(listOf(Tts.SHERPA_ENGINE, "com.google.android.tts", "app.sherpa")))
        assertEquals(listOf("com.google.android.tts", Tts.SHERPA_ENGINE), Tts.mergeEngines(listOf("com.google.android.tts"), Tts.SHERPA_ENGINE))
        assertTrue(Tts.sherpaInstalled(Tts.mergeEngines(emptyList(), Tts.SHERPA_ENGINE)))
    }

    @Test
    fun aFailureOffersSherpaOrItsInstall() {
        assertEquals(TtsFailureAction.USE_SHERPA, Tts.failureAction(null))
        assertEquals(TtsFailureAction.USE_SHERPA, Tts.failureAction("com.google.android.tts"))
        assertEquals(TtsFailureAction.INSTALL_SHERPA, Tts.failureAction(Tts.SHERPA_ENGINE))
    }

    @Test
    fun shortLocalesBecomeTags() {
        assertEquals("de-DE", Tts.languageTag("de"))
        assertEquals("en-EN", Tts.languageTag("en"))
        assertEquals("zh-CN", Tts.languageTag("zh"))
        assertEquals("en-GB", Tts.languageTag("en_GB"))
        assertEquals("de-DE", Tts.languageTag("de-DE"))
    }

    @Test
    fun languagesAreTriedVoiceThenAppThenEnglish() {
        assertEquals(listOf("en-US", "en", "de-DE", "de"), Tts.languageCandidates(voiceLocale = "en-US", appLocale = "de-DE"))
        val german = Tts.languageCandidates(appLocale = "de-DE")
        assertEquals("de-DE", german.first())
        assertTrue(german.containsAll(listOf("de", "en-US", "en")))
    }

    @Test
    fun theTextsLanguageGoesFirstWithTheVoicesRegion() {
        assertEquals("en-GB", Tts.languageCandidates(textLanguage = "en", voiceLocale = "en-GB", appLocale = "de-DE").first())
        assertEquals("en-US", Tts.languageCandidates(textLanguage = "en", appLocale = "de-DE").first())
        assertEquals("fr-FR", Tts.languageCandidates(textLanguage = "fr", appLocale = "de-DE").first())
    }

    @Test
    fun androidsAvailabilityAnswersAndBooleansAreRead() {
        listOf<Any?>(true, 0, 1, 2).forEach { assertTrue(Tts.languageAvailable(it), "$it") }
        listOf<Any?>(-1, -2, false, null, "1").forEach { assertFalse(Tts.languageAvailable(it), "$it") }
    }

    @Test
    fun anEngineWithOnlyEnglishIsNotAskedForGerman() = runBlocking {
        val picked = Tts.pickLanguage(Tts.languageCandidates(appLocale = "de-DE"), listOf("en-US")) { it.startsWith("en") }
        assertEquals("en-US", picked)
        assertEquals("eng", Tts.pickLanguage(listOf("de-DE"), listOf("eng")) { false })
        assertTrue(Tts.languageListed(listOf("en_US"), "en-US"))
        assertTrue(Tts.languageListed(listOf("en"), "en-GB"))
        assertFalse(Tts.languageListed(listOf("de-DE"), "en"))
    }

    @Test
    fun aVoiceSuitsTextInItsOwnLanguageOnly() {
        assertTrue(Tts.voiceSuits("de-DE", "de"))
        assertTrue(Tts.voiceSuits("de_DE", "de-AT"))
        assertFalse(Tts.voiceSuits("de-DE", "en-US"))
        assertTrue(Tts.voiceSuits("de-DE", null))
    }

    @Test
    fun theSpeechRateMapsOntoSherpaSpeed() {
        assertEquals(1.0, Tts.sherpaSpeed(0.5))
        assertEquals(0.9, Tts.sherpaSpeed(0.45), 1e-9)
        assertEquals(0.5, Tts.sherpaSpeed(0.2))
        assertEquals(2.0, Tts.sherpaSpeed(1.0))
    }

    @Test
    fun storedSettingsReadEmptyAsUnset() {
        assertEquals(TtsChoice(), TtsChoice.of("", "", "", null))
        assertFalse(TtsChoice(voiceName = "de-x-1").hasVoice)
        assertTrue(TtsChoice.of(Tts.SHERPA_ENGINE, "de-x-1", "de-DE", 0.6).hasVoice)
    }
}
