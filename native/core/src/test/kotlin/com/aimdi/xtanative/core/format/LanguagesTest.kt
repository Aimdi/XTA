package com.aimdi.xtanative.core.format

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LanguagesTest {
    @Test
    fun aLanguageIsItsCodeWhateverTheRegionOrSeparator() {
        assertEquals(listOf("pt", "de", "zh", "en"), listOf("pt-BR", "de_AT", "zh-Hant-TW", " EN ").map(Languages::of))
    }

    @Test
    fun theOldCodesXAndAndroidStillWriteAreTodaysLanguages() {
        assertEquals(listOf("id", "he", "yi"), listOf("in", "iw_IL", "ji").map(Languages::of))
        assertTrue(Languages.same("in", "id-ID"))
        assertTrue(Languages.same("iw", "he"))
        assertFalse(Languages.same("in", "it"))
    }
}
