package com.aimdi.xtanative.core.feed

import kotlin.test.Test
import kotlin.test.assertEquals

/** XTA's feed_strip_test (the parts that are rules), for the native port. */
class FeedStripTest {
    private val on = setOf("reddit", "mastodon", "pixiv")
    private val usable = { id: String -> id in on }

    @Test
    fun followingAndForYouAlwaysComeFirstThenThePinsThatAreOn() {
        assertEquals(listOf("following", "x"), FeedStrip.tabs(emptyList(), usable))
        assertEquals(listOf("following", "x", "reddit"), FeedStrip.tabs(listOf("reddit", "bluesky", "reddit", "x"), { it in on || it == "x" }))
    }

    @Test
    fun aNetworkOffTheStripStaysOneAddAway() {
        assertEquals(listOf("mastodon", "pixiv"), FeedStrip.candidates(listOf("reddit", "mastodon", "bluesky", "pixiv"), listOf("reddit"), usable))
    }

    @Test
    fun pinsAreAddedOnceForgottenAndMoved() {
        assertEquals(listOf("a", "b"), FeedStrip.pinned(listOf("a"), "b"))
        assertEquals(listOf("a"), FeedStrip.pinned(listOf("a"), "a"))
        assertEquals(listOf("b"), FeedStrip.forgotten(listOf("a", "b"), "a"))
        assertEquals(listOf("b", "a", "c"), FeedStrip.reorder(listOf("a", "b", "c"), 0, 1))
        assertEquals(listOf("b", "c", "a"), FeedStrip.reorder(listOf("a", "b", "c"), 0, 2))
        assertEquals(listOf("c", "a", "b"), FeedStrip.reorder(listOf("a", "b", "c"), 2, 0))
        assertEquals(listOf("a", "b"), FeedStrip.reorder(listOf("a", "b"), 0, 0))
        assertEquals(listOf("a", "b"), FeedStrip.reorder(listOf("a", "b"), -1, 0))
        assertEquals(listOf("a", "b"), FeedStrip.reorder(listOf("a", "b"), 2, 0))
    }

    @Test
    fun aFirstLaunchStartsWithNoNetworkAndAnOlderInstallKeepsWhatItShowed() {
        assertEquals(emptyList(), FeedStrip.migrated(listOf("mastodon"), firstLaunch = true, enabled = listOf("reddit"), hiddenTab = listOf("substack")))
        assertEquals(listOf("mastodon", "substack"), FeedStrip.migrated(listOf("mastodon"), firstLaunch = false, enabled = listOf("reddit"), hiddenTab = listOf("substack")))
        assertEquals(listOf("reddit", "mastodon"), FeedStrip.migrated(null, firstLaunch = false, enabled = listOf("reddit", "mastodon"), hiddenTab = listOf("reddit")))
    }
}
