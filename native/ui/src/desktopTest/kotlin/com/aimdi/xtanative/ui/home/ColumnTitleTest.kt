package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.core.session.DeckColumn
import kotlin.test.Test
import kotlin.test.assertEquals

class ColumnTitleTest {
    @Test
    fun aGroupsEmojiStandsApartFromItsName() {
        assertEquals("🧪" to "Tests", ColumnTitle.split("🧪 Tests"))
        assertEquals("🧪" to "Rust news", ColumnTitle.split("🧪 Rust news"))
    }

    @Test
    fun aNameWithoutEmojiIsKeptWhole() {
        assertEquals(null to "Rust news", ColumnTitle.split("Rust news"))
        assertEquals(null to "Tests", ColumnTitle.split("Tests"))
        assertEquals(null to "#1 picks", ColumnTitle.split("#1 picks"))
    }

    @Test
    fun onlyAGroupsLabelLosesItsEmojiToTheMarksPlace() {
        assertEquals("🧪" to "Tests", ColumnTitle.of(DeckColumn.Group("g1"), "🧪 Tests"))
        assertEquals(null to "🧪 Tests", ColumnTitle.of(DeckColumn.Network("feeds"), "🧪 Tests"))
        assertEquals(null to "X", ColumnTitle.of(DeckColumn.ForYou, "X"))
    }
}
