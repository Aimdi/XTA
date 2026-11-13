package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

/** The line a post goes by in the reader's own bookmarks, as XTA's share sheet titles it, and as XTA's tests of that title read. */
class PluginSharedPostTest {
    private fun titleOf(name: String?, handle: String?, text: String) = PluginSharedPost.titleOf(name, handle, text)

    @Test
    fun itNamesTheAuthorThenThePost() {
        assertEquals("Jack (@jack): just setting up my twttr", titleOf("Jack", "jack", "just setting up my twttr"))
    }

    @Test
    fun itFallsBackToTheHandleWhenThereIsNoDisplayName() {
        assertEquals("@jack: hello", titleOf(null, "jack", "hello"))
        assertEquals("@jack: hello", titleOf("  ", "jack", "hello"))
    }

    @Test
    fun itIsJustThePostWhenTheAuthorIsUnknown() {
        assertEquals("hello", titleOf(null, null, "hello"))
    }

    @Test
    fun itIsJustTheBylineForAPostWithNoTextSuchAsAPhoto() {
        assertEquals("Jack (@jack)", titleOf("Jack", "jack", "   "))
    }

    @Test
    fun itCollapsesLineBreaksSoTheBookmarkListStaysReadable() {
        assertEquals("Jack (@jack): line one line two tabbed", titleOf("Jack", "jack", "line one\n\nline two\ttabbed"))
        assertEquals("Jack (@jack): no break here", titleOf("Jack", "jack", "no break here"))
    }
}
