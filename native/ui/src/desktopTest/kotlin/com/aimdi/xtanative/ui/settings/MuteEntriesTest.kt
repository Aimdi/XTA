package com.aimdi.xtanative.ui.settings

import com.aimdi.xtanative.core.feed.MuteList
import java.time.Duration
import java.time.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class MuteEntriesTest {
    private val now = Instant.parse("2026-11-27T12:00:00Z")
    private val mutes = MuteList().withWord("Cats").withWord("old", until = now.minusSeconds(1)).withAccount("Noisy")

    @Test
    fun `accounts come first, run-out entries are left out`() {
        assertEquals(listOf("@Noisy", "Cats"), MuteEntries.shown(mutes, "", now).map { it.label })
    }

    @Test
    fun `the search finds in any case`() {
        assertEquals(listOf("Cats"), MuteEntries.shown(mutes, " cAT ", now).map { it.label })
        assertTrue(MuteEntries.shown(mutes, "zzz", now).isEmpty())
    }

    @Test
    fun `an entry is retimed or removed alone`() {
        val cats = MuteEntries.shown(mutes, "cats", now).single()
        val retimed = MuteEntries.retimed(mutes, cats, Duration.ofDays(1), now)
        assertEquals(now.plus(Duration.ofDays(1)), retimed.words.first { it.term == "Cats" }.until)
        assertEquals(listOf("old"), MuteEntries.removed(mutes, cats).words.map { it.term })
    }
}
