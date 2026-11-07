package com.aimdi.xtanative.networks.substack

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's substack_plugin_test: the reader's page follows the theme and carries only the sanitized body. */
class SubstackPageThemeTest {
    @Test
    fun thePageFollowsTheThemeAndCarriesOnlyTheSanitizedBody() {
        val post = SubstackPost("1", "Hello <World>", "hello", "https://example.substack.com", "Pub", subtitle = "Sub")
        val body = SubstackArticle.parse("<p>Body</p><button>x</button><svg></svg>").body
        val page = SubstackWebPage.wrap(post, body, SubstackPageColors("#000000", "#FFFFFF", "#AAAAAA", "#1D9BF0", isDark = true), SubstackPageWords())
        assertTrue("color-scheme: dark" in page)
        assertTrue("#000000" in page)
        assertTrue("Hello &lt;World&gt;" in page)
        assertTrue("Body" in page)
        assertFalse("<button" in page)
        assertTrue("Georgia" in page, "the serif font by default")
        assertTrue("XtaTts.postMessage" in page)
    }
}
