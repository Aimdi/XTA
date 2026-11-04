package com.aimdi.xtanative.networks.tiktok

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TikTokSearchTest {
    private fun user(uniqueId: String, nickname: String = uniqueId) = TikTokSearchUser(uniqueId = uniqueId, nickname = nickname)

    @Test
    fun aDisplayNameIsGluedIntoLikelyUsernames() {
        val candidates = TikTokSearch.handleCandidates("Charli D'Amelio")
        assertEquals(listOf("charlidamelio", "charli.damelio", "charli_damelio", "charli"), candidates)
    }

    @Test
    fun aTypedUsernameIsItsOwnCandidate() {
        assertEquals(listOf("nba"), TikTokSearch.handleCandidates("@NBA"))
    }

    @Test
    fun suggestionsAddThemselvesAndTheirGluedWords() {
        val candidates = TikTokSearch.handleCandidates("charli", suggestions = listOf("charli damelio"))
        assertEquals(listOf("charli", "charlidamelio", "charli.damelio", "charli_damelio"), candidates)
    }

    @Test
    fun candidatesStopAtEight() {
        val candidates = TikTokSearch.handleCandidates("one two", suggestions = listOf("three four", "five six", "seven eight"))
        assertEquals(8, candidates.size)
        assertEquals(listOf("onetwo", "one.two", "one_two", "one"), candidates.take(4))
    }

    @Test
    fun fiveWordsAreNotGlued() {
        assertTrue(TikTokSearch.gluedHandles(listOf("a", "b", "c", "d", "e")).isEmpty())
        assertTrue(TikTokSearch.gluedHandles(listOf("solo")).isEmpty())
    }

    @Test
    fun wordsDropApostrophesAndPunctuation() {
        assertEquals(listOf("charli", "damelio"), TikTokSearch.words("Charli D’Amelio!"))
        assertEquals(listOf("the", "rock"), TikTokSearch.words("  The   Rock  "))
        assertTrue(TikTokSearch.words("!!!").isEmpty())
    }

    @Test
    fun aPersonMatchesByUsernameOrNicknameGluedOrNot() {
        val rock = user("therock", "The Rock")
        assertTrue(TikTokSearch.matches(rock, "rock"))
        assertTrue(TikTokSearch.matches(rock, "@TheRock"))
        assertTrue(TikTokSearch.matches(rock, "the rock"))
        assertFalse(TikTokSearch.matches(rock, "cats"))
        assertFalse(TikTokSearch.matches(rock, "  "))
        assertTrue(TikTokSearch.matches(user("charlidamelio", "charli"), "Charli D'Amelio"), "glued, the words are the username")
    }

    @Test
    fun peopleFoundByUsernameComeFirstThenMatchingDiscoverPeopleEachOnce() {
        val people = TikTokSearch.people(
            query = "rock",
            found = listOf(user("rockets")),
            discover = listOf(user("TheRock", "The Rock"), user("rockets"), user("cats")),
        )
        assertEquals(listOf("rockets", "TheRock"), people.map { it.uniqueId })
    }
}
