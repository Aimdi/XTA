package com.aimdi.xtanative.ui.media

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VideoCreditTest {
    @Test
    fun aVideoIsNamedByTheStartOfItsPostOnOneLine() {
        assertEquals("Easy async validation with debouncing", VideoCredit.title("Easy async validation\n\nwith debouncing https://t.co/abc"))
        val long = VideoCredit.title("word ".repeat(40))
        assertTrue(long.length <= VideoCredit.TITLE_CHARS && long.endsWith("…"))
        assertEquals("", VideoCredit.title("https://t.co/only"))
        assertNull(VideoCredit.of(null))
    }
}
