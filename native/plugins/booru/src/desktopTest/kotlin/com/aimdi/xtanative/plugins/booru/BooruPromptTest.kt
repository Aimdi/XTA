package com.aimdi.xtanative.plugins.booru

import com.aimdi.xtanative.networks.booru.BooruTagCategory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class BooruPromptTest {
    @Test
    fun theDefaultOrderDrawsWhoAndWhatFirstAndLeavesMetaOut() {
        val order = listOf(BooruTagCategory.CHARACTER, BooruTagCategory.COPYRIGHT, BooruTagCategory.ARTIST, BooruTagCategory.SPECIES, BooruTagCategory.GENERAL)
        assertEquals(order, BooruPrompt.order(""))
        assertEquals("character,copyright,artist,species,general,-meta", BooruPrompt.DEFAULT_STORED)
    }

    @Test
    fun kindsTheStoredOrderDoesNotNameFollowIt() {
        val kinds = BooruPrompt.kinds("artist,-general,bogus")
        assertEquals(listOf(BooruTagCategory.ARTIST, BooruTagCategory.GENERAL), kinds.take(2).map { it.category })
        assertFalse(kinds[1].included)
        assertEquals(BooruTagCategory.entries.toSet(), kinds.map { it.category }.toSet())
        assertFalse(kinds.single { it.category == BooruTagCategory.META }.included)
        assertTrue(kinds.single { it.category == BooruTagCategory.CHARACTER }.included)
    }

    @Test
    fun aKindCanBeMovedAndLeftOut() {
        val kinds = BooruPrompt.kinds("")
        val moved = BooruPrompt.moved(kinds, from = 4, to = 0)
        assertEquals(BooruTagCategory.GENERAL, moved.first().category)
        assertEquals(kinds, BooruPrompt.moved(kinds, from = 9, to = 0))

        val edited = BooruPrompt.toggled(moved, BooruTagCategory.ARTIST, included = false)
        val stored = BooruPrompt.stored(edited)
        assertEquals("general,character,copyright,-artist,species,-meta", stored)
        assertEquals(edited, BooruPrompt.kinds(stored))
    }

    @Test
    fun aPromptReadsTheTagsByKindWithSpacesAndEscapedParentheses() {
        val tags = listOf("smile", "hatsune_miku", "vocaloid", "^_^", "highres", "kagamine_rin_(cosplay)")
        val kinds = mapOf(
            "hatsune_miku" to BooruTagCategory.CHARACTER,
            "vocaloid" to BooruTagCategory.COPYRIGHT,
            "highres" to BooruTagCategory.META,
        )
        assertEquals("hatsune miku, vocaloid, smile, ^_^, kagamine rin \\(cosplay\\)", BooruPrompt.text(tags, kinds, BooruPrompt.order("")))
    }

    @Test
    fun switchingMetaOnKeepsItInThePromptAfterTheOthers() {
        val stored = BooruPrompt.stored(BooruPrompt.toggled(BooruPrompt.kinds(""), BooruTagCategory.META, included = true))
        assertEquals("character,copyright,artist,species,general,meta", stored)
        assertEquals(BooruTagCategory.META, BooruPrompt.order(stored).last())
    }
}
