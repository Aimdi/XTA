package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's substack_discovery_tts_test, the part no other test here pins. */
class SubstackDiscoveryTtsTest {
    @Test
    fun thePageLeavesLongPressToSelectionAndReadsFromASelection() {
        val post = SubstackPost("1", "A post", "a-post", "https://example.substack.com", "Example")
        val page = SubstackWebPage.wrap(post, "<p>Text</p>", SubstackPageColors("#000000", "#ffffff", "#888888", "#1d9bf0", isDark = true), SubstackPageWords())
        assertFalse("touch-callout: none" in page, "selection was blocked before XTA's BUG-12")
        assertFalse("addEventListener('contextmenu'" in page)
        assertTrue("window.xtaSpeakFromSelection" in page)
    }
}
