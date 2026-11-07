package com.aimdi.xtanative.networks.substack

import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.SubstackColumnWidth
import com.aimdi.xtanative.store.settings.SubstackPrefs
import com.aimdi.xtanative.store.settings.SubstackReaderFont
import com.aimdi.xtanative.ui.plugins.PluginSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's substack_native_article_test, its rules: the reader's settings and what ends a paid preview. */
class SubstackNativeArticleTest {
    private val post = SubstackPost("1", "A native read", "native", "https://fieldnotes.substack.com", "Field Notes", canonicalUrl = "https://fieldnotes.substack.com/p/native")

    @Test
    fun theNativeReaderIsOffByDefaultAndItsFontAndColumnStartAtSerifAndMedium() {
        val settings = Settings()
        assertFalse(settings[SubstackPrefs.nativeReader])
        assertEquals(SubstackReaderFont.SERIF, settings[SubstackPrefs.readerFont])
        assertEquals(SubstackColumnWidth.MEDIUM, settings[SubstackPrefs.columnWidth])
        assertTrue(SubstackPrefs.nativeReader.key.startsWith(PluginSettings.prefixOf("substack")), "erased with the plugin's own settings")
        assertTrue(SubstackPrefs.columnWidth.key.startsWith(PluginSettings.prefixOf("substack")))
    }

    @Test
    fun theColumnIsNarrowOrWideInRem() {
        assertEquals(36, SubstackColumnWidth.NARROW.rem)
        assertEquals(52, SubstackColumnWidth.WIDE.rem)
    }

    @Test
    fun aPaidPostWithItsOpeningIsAPreviewThatEndsWithTheWayToTheSite() {
        val paid = post.copy(audience = "only_paid", bodyHtml = "<p>The free part.</p>")
        assertTrue(SubstackReading.isPreview(paid))
        assertFalse(SubstackReading.isPreview(paid.copy(audience = "everyone")))
        assertEquals("https://fieldnotes.substack.com/p/native", paid.canonicalUrl)
    }
}
