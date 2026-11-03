package com.aimdi.xtanative.networks.karakeep

import kotlin.test.Test
import kotlin.test.assertEquals

/** The title a kept post is saved under: XTA's karakeep_title_test. */
class KarakeepTitleTest {
    @Test
    fun itNamesTheAuthorThenThePost() {
        assertEquals("Jack (@jack): just setting up my twttr", karakeepTitle("Jack", "jack", "just setting up my twttr"))
    }

    @Test
    fun itFallsBackToTheHandleWhenThereIsNoDisplayName() {
        assertEquals("@jack: hello", karakeepTitle(null, "jack", "hello"))
        assertEquals("@jack: hello", karakeepTitle("  ", "jack", "hello"))
    }

    @Test
    fun itIsJustThePostWhenTheAuthorIsUnknown() {
        assertEquals("hello", karakeepTitle(null, null, "hello"))
    }

    @Test
    fun itIsJustTheBylineForAPostWithNoTextSuchAsAPhoto() {
        assertEquals("Jack (@jack)", karakeepTitle("Jack", "jack", "   "))
    }

    @Test
    fun itCollapsesLineBreaksSoTheBookmarkListStaysReadable() {
        assertEquals("Jack (@jack): line one line two tabbed", karakeepTitle("Jack", "jack", "line one\n\nline two\ttabbed"))
        assertEquals("Jack (@jack): no break here", karakeepTitle("Jack", "jack", "no break here"))
    }
}
