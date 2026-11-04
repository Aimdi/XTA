package com.aimdi.xtanative.ui.home

import com.aimdi.xtanative.ui.common.ReadingColumn
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Mastodon, Bluesky and Threads as one entry, the timelines turned by a drag, and the reading column (lib/home/alt_microblogging.dart, lib/ui/reader_swipe_navigation.dart, lib/ui/reading_column.dart). */
class AltMicrobloggingTest {
    @Test
    fun theMembersGiveWayToOneEntryWhereTheFirstOfThemStood() {
        val ids = listOf("following", "x", "reddit", "bluesky", "pixiv", "mastodon", "threads")
        assertEquals(listOf("following", "x", "reddit", AltMicroblogging.SECTION, "pixiv"), AltMicroblogging.listed(ids, grouped = true))
        assertEquals(ids, AltMicroblogging.listed(ids, grouped = false))
        assertEquals(listOf("reddit"), AltMicroblogging.listed(listOf("reddit"), grouped = true), "no member, no entry")
    }

    @Test
    fun theEntryOpensTheOneShownElseTheOneOpenedLastElseTheFirst() {
        val ids = listOf("reddit", "bluesky", "mastodon")
        assertEquals("mastodon", AltMicroblogging.destination(ids, selected = "mastodon", remembered = "bluesky"))
        assertEquals("bluesky", AltMicroblogging.destination(ids, selected = "reddit", remembered = "bluesky"))
        assertEquals("bluesky", AltMicroblogging.destination(ids, selected = null, remembered = "threads"), "a member no longer there")
        assertNull(AltMicroblogging.destination(listOf("reddit"), selected = null, remembered = "mastodon"))
        assertEquals(listOf("bluesky", "mastodon"), AltMicroblogging.members(ids))
    }

    @Test
    fun theNetworksSheetListsTheMembersAsOneEntryRecentWhenOneOfThemIs() {
        val bar = listOf("reddit", "mastodon", "pixiv", "bluesky")
        assertEquals(listOf(AltMicroblogging.SECTION) to listOf("reddit", "pixiv"), AltMicroblogging.sections(bar, listOf("bluesky", "gone"), grouped = true))
        assertEquals(listOf("pixiv") to listOf("reddit", AltMicroblogging.SECTION), AltMicroblogging.sections(bar, listOf("pixiv"), grouped = true))
        assertEquals(listOf("bluesky") to listOf("reddit", "mastodon", "pixiv"), AltMicroblogging.sections(bar, listOf("bluesky"), grouped = false))
    }

    @Test
    fun onlyAMemberIsRememberedAsTheOneOpenedLast() {
        assertEquals("threads", AltMicroblogging.remembered("threads", "bluesky"))
        assertEquals("bluesky", AltMicroblogging.remembered("reddit", "bluesky"))
        assertTrue(AltMicroblogging.isMember("mastodon"))
        assertFalse(AltMicroblogging.isMember(null))
    }

    @Test
    fun aDragNamesTheNextTimelineInTheWayItGoes() {
        assertEquals(2, TimelineSwipe.target(1, 3, distance = -30f))
        assertEquals(0, TimelineSwipe.target(1, 3, distance = 30f))
        assertEquals(0, TimelineSwipe.target(1, 3, distance = -30f, rtl = true))
        assertNull(TimelineSwipe.target(2, 3, distance = -30f), "past the last")
        assertNull(TimelineSwipe.target(0, 3, distance = 30f), "before the first")
        assertNull(TimelineSwipe.target(1, 3, distance = 0.5f), "not moved yet")
        assertEquals(0.5f, TimelineSwipe.progress(-36f))
        assertEquals(1f, TimelineSwipe.progress(200f))
    }

    @Test
    fun aDragThatTurnsBackOnItselfIsCalledOff() {
        assertEquals(0f, TimelineSwipe.direction(10f), "not clear yet")
        assertEquals(-1f, TimelineSwipe.direction(-30f))
        assertTrue(TimelineSwipe.reversed(-1f, 5f))
        assertFalse(TimelineSwipe.reversed(-1f, -50f))
        assertFalse(TimelineSwipe.reversed(0f, 5f))
    }

    @Test
    fun aDragStartingAtTheEdgesIsTheSystems() {
        assertFalse(TimelineSwipe.startsInside(10f, 400f))
        assertFalse(TimelineSwipe.startsInside(390f, 400f))
        assertTrue(TimelineSwipe.startsInside(200f, 400f))
    }

    @Test
    fun wideWindowsKeepThePostsInACentredColumn() {
        assertEquals(0f, ReadingColumn.inset(400f), "a phone keeps its width")
        assertEquals(0f, ReadingColumn.inset(720f))
        assertEquals(600f, ReadingColumn.inset(1920f))
        assertEquals(0f, ReadingColumn.inset(Float.POSITIVE_INFINITY))
    }
}
