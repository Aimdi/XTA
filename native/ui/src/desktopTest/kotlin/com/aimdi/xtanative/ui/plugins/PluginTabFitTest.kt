package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's plugin chrome rules (plugin_home_chrome.dart): when a plugin's tabs keep their labels, their icons, or fold. */
class PluginTabFitTest {
    @Test
    fun labelledTabsFitWhileTheirLabelsIconsAndPaddingDo() {
        // Two tabs of 40 dp labels: 8 + 2 × (40 + 52) = 192.
        assertTrue(PluginTabFit.labelled(listOf(40f, 40f), 192f))
        assertFalse(PluginTabFit.labelled(listOf(40f, 40f), 191f), "one dp short folds them into the section picker")
    }

    @Test
    fun aShortLabelStillTakesATouchTarget() {
        // A label too short to matter cannot make its tab narrower than 48 dp.
        assertFalse(PluginTabFit.labelled(listOf(-20f, -20f), 100f))
        assertTrue(PluginTabFit.labelled(listOf(-20f, -20f), 104f))
    }

    @Test
    fun iconTabsFitWhileEachKeepsATouchTarget() {
        assertTrue(PluginTabFit.icons(4, 192f))
        assertFalse(PluginTabFit.icons(4, 191f))
        assertTrue(PluginTabFit.icons(0, 0f))
    }

    @Test
    fun aFoldedStripNamesItsSectionUntilItIsTooNarrow() {
        assertTrue(PluginTabFit.namesSection(104f))
        assertFalse(PluginTabFit.namesSection(103f), "under 104 dp the icon stands alone")
    }
}
