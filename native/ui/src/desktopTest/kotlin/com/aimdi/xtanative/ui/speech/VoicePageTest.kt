package com.aimdi.xtanative.ui.speech

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class VoicePageTest {
    @Test
    fun theDesktopWithItsVoiceEngineOffersOnlyTheVoices() {
        val page = VoicePage.of(systemEngine = false, synthesis = true)
        assertTrue(page.voicesOnlyIntro)
        assertTrue(page.downloads)
        assertFalse(page.offlineSwitch, "no engine below to fall back to: the switch would only stop reading")
        assertFalse(page.synthesisMissing)
        assertFalse(page.cannotRead)
        assertFalse(page.systemEngine)
    }

    @Test
    fun theDesktopWithoutItSaysSoAndNothingElse() {
        assertEquals(
            VoicePage(voicesOnlyIntro = false, offlineSwitch = false, downloads = false, synthesisMissing = false, cannotRead = true, systemEngine = false),
            VoicePage.of(systemEngine = false, synthesis = false),
        )
    }

    @Test
    fun androidKeepsTheSwitchAndItsEngines() {
        val full = VoicePage.of(systemEngine = true, synthesis = true)
        assertTrue(full.offlineSwitch && full.downloads && full.systemEngine)
        assertFalse(full.voicesOnlyIntro || full.synthesisMissing || full.cannotRead)
        assertTrue(VoicePage.of(systemEngine = true, synthesis = false).synthesisMissing, "an APK without the shim still reads with the system engine")
    }
}
