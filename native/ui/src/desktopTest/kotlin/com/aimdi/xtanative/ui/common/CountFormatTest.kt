package com.aimdi.xtanative.ui.common

import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/** Counts as each language shortens them ([countFormat]), cut down as X cuts them. */
class CountFormatTest {
    private val counts = listOf(999L, 1_234, 12_345, 123_456, 1_234_567, 12_345_678, 1_234_567_890)

    private fun shown(tag: String): List<String> {
        val format = countFormat(Locale.forLanguageTag(tag))
        // CLDR puts a no-break space (or a narrow one) between the number and the word; plain spaces read better here.
        return counts.map { format(it).replace(' ', ' ').replace(' ', ' ') }
    }

    @Test
    fun englishReadsAsXShowsCounts() {
        assertEquals(listOf("999", "1.2K", "12K", "123K", "1.2M", "12M", "1.2B"), shown("en"))
    }

    @Test
    fun germanGroupsThousandsAndShortensMillions() {
        assertEquals(listOf("999", "1.234", "12.345", "123.456", "1,2 Mio.", "12 Mio.", "1,2 Mrd."), shown("de"))
    }

    @Test
    fun japaneseAndChineseCountInTenThousands() {
        assertEquals(listOf("999", "1,234", "1.2万", "12万", "123万", "1234万", "12億"), shown("ja"))
        assertEquals(listOf("999", "1,234", "1.2万", "12万", "123万", "1234万", "12亿"), shown("zh-CN"))
        assertEquals("1.2만", countFormat(Locale.forLanguageTag("ko"))(12_345))
    }

    @Test
    fun otherLanguagesUseTheirOwnWords() {
        assertEquals("1,2 тыс.", shown("ru")[1])
        assertEquals("12 k", shown("fr")[2])
        assertEquals("1,2 mln", shown("pl")[4])
        assertEquals("1.2 लाख", shown("hi")[3], "Hindi counts in lakhs")
    }

    @Test
    fun arabicCountsInLatinDigitsAsTheRestOfTheApp() {
        assertEquals("1.2 ألف", shown("ar")[1])
        assertEquals("999", shown("ar")[0])
    }

    @Test
    fun latinBelarusianKeepsXsLettersNotCyrillicWords() {
        assertEquals("1,2K", countFormat(Locale("be", "XL"))(1_234))
    }
}
