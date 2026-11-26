package com.aimdi.xtanative.ui.settings

import androidx.compose.ui.unit.dp
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class ChoiceListTest {
    @Test
    fun aLongListGetsAFilterAndAShortOneNone() {
        assertFalse(ChoiceList.filtered(12))
        assertTrue(ChoiceList.filtered(13))
        assertTrue(ChoiceList.filtered(SettingsChoices.languages.size + 1), "the languages")
    }

    @Test
    fun theDialogTakesAtMostSeventyPercentOfTheWindow() {
        assertEquals(630.dp, ChoiceList.maxHeight(900.dp))
    }

    @Test
    fun theFilterMatchesAnyCaseAnywhereInTheName() {
        val labels = listOf("System", "Deutsch", "English", "español", "eesti")
        assertEquals(listOf(1), ChoiceList.matching(labels, "deu"))
        assertEquals(listOf(3), ChoiceList.matching(labels, " SP"), "trimmed, any case")
        assertEquals(listOf(0, 4), ChoiceList.matching(labels, "st"))
        assertEquals(labels.indices.toList(), ChoiceList.matching(labels, "  "))
        assertEquals(emptyList(), ChoiceList.matching(labels, "xyz"))
    }

    @Test
    fun enterPicksTheOnlyMatchLeft() {
        assertEquals(3, ChoiceList.picked(listOf(3)))
        assertEquals(null, ChoiceList.picked(listOf(0, 4)), "several: nothing")
        assertEquals(null, ChoiceList.picked(emptyList()), "none: nothing")
    }
}
