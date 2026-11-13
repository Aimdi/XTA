package com.aimdi.xtanative.ui.plugins

import kotlin.test.Test
import kotlin.test.assertEquals

class PluginSearchHistoryTest {
    @Test
    fun aSearchGoesFirstInPlaceOfTheSameSearchBefore() {
        assertEquals(listOf("b", "a", "c"), PluginSearchHistory.remembered(listOf("a", "b", "c"), " b "))
        assertEquals(listOf("a", "b"), PluginSearchHistory.remembered(listOf("a", "b"), "   "), "a blank search changes nothing")
    }

    @Test
    fun theSameSearchIsWhatTheIdentitySays() {
        val lowercase = { query: String -> query.lowercase() }
        assertEquals(listOf("NBA", "tiktok"), PluginSearchHistory.remembered(listOf("nba", "tiktok"), "NBA", lowercase))
    }

    @Test
    fun theHistoryKeepsTwentyAndForgetsOne() {
        val full = (1..20).map { "q$it" }
        assertEquals(listOf("new") + (1..19).map { "q$it" }, PluginSearchHistory.remembered(full, "new"))
        assertEquals(listOf("q1", "q3"), PluginSearchHistory.forgotten(listOf("q1", "q2", "q3"), "q2"))
    }
}
