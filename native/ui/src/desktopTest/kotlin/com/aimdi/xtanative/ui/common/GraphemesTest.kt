package com.aimdi.xtanative.ui.common

import kotlin.test.Test
import kotlin.test.assertEquals

class GraphemesTest {
    @Test
    fun anEmojiKeepsItsSkinToneAndWhatItJoins() {
        assertEquals("👍🏽", Graphemes.first("👍🏽 and more"))
        assertEquals("👨‍👩‍👧", Graphemes.first("👨‍👩‍👧 family"))
        assertEquals("🏳️‍🌈", Graphemes.first("🏳️‍🌈!"))
        assertEquals("❤️", Graphemes.first("❤️❤️"))
    }

    @Test
    fun flagsAreAPairOfLettersOrATagSequence() {
        assertEquals(listOf("🇫🇷", "🇩🇪"), Graphemes.of("🇫🇷🇩🇪"))
        assertEquals("🏴󠁧󠁢󠁥󠁮󠁧󠁿", Graphemes.first("🏴󠁧󠁢󠁥󠁮󠁧󠁿 England"))
    }

    @Test
    fun aLetterKeepsItsAccentsAndAKeycapItsFrame() {
        assertEquals(listOf("é", "t", "é"), Graphemes.of("été"))
        assertEquals("1️⃣", Graphemes.first("1️⃣2"))
        assertEquals(listOf("イ", "ラ"), Graphemes.of("イラ"))
    }

    @Test
    fun emptyTextHasNoGraphemes() {
        assertEquals("", Graphemes.first(""))
        assertEquals(emptyList(), Graphemes.of(""))
        assertEquals(listOf("\r\n", "a"), Graphemes.of("\r\na"))
    }
}
