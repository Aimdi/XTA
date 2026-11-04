package com.aimdi.xtanative.store.saved

import com.aimdi.xtanative.store.backup.FeedFilterRow
import com.aimdi.xtanative.store.backup.ProfileNoteRow
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.runBlocking

class ProfileNotesTest {
    private val dir = tempStore()
    private var time = 1_000L

    @Test
    fun aNoteIsKeptTrimmedAndABlankOneRemovesIt(): Unit = runBlocking {
        val notes = ProfileNotesStore(dir) { time++ }
        notes.set("42", "  met at a conference \n")
        assertEquals(ProfileNote("42", "met at a conference", 1_000), notes.of("42"))
        assertEquals("met at a conference", ProfileNotesStore(dir).of("42")?.note, "kept for the next launch")

        notes.set("42", "met at a conference")
        assertEquals(1_000, notes.of("42")?.updatedAt, "the same note is not saved again")

        notes.set("42", "   ")
        assertNull(notes.of("42"))
        assertNull(ProfileNotesStore(dir).of("42"))
    }

    @Test
    fun theBackupCarriesXtasRowsBothWays(): Unit = runBlocking {
        val notes = ProfileNotesStore(dir) { time }
        notes.replace(listOf(ProfileNoteRow("1", "friend", "2025-03-01T10:00:00.000Z"), ProfileNoteRow("2", "  ", null)))
        assertEquals(listOf("1"), notes.content().map { it.profileId }, "a blank note is no note")
        assertEquals(java.time.Instant.parse("2025-03-01T10:00:00Z").toEpochMilli(), notes.of("1")?.updatedAt)
        assertEquals("2025-03-01T10:00:00Z", notes.content().single().updatedAt)
    }

    @Test
    fun hiddenRepostsAndRepliesAreKeptByIdAndMatchedByLowerCaseHandle(): Unit = runBlocking {
        val filters = ProfileFiltersStore(dir)
        filters.setRepostsHidden("1", "Tommy", true)
        filters.setRepliesHidden("2", "Maya", true)
        val read = ProfileFiltersStore(dir).filters()
        assertTrue(read.repostsHidden("1"))
        assertFalse(read.repliesHidden("1"))
        assertEquals(setOf("tommy"), read.hiddenRepostHandles)
        assertEquals(setOf("maya"), read.hiddenReplyHandles)

        filters.setRepostsHidden("1", "Tommy", false)
        assertFalse(filters.filters().repostsHidden("1"))
        assertEquals(emptyList<FeedFilterRow>() to listOf(FeedFilterRow("2", "maya")), filters.content())

        filters.replace(listOf(FeedFilterRow("3", "Ann")), emptyList())
        assertEquals(setOf("ann"), filters.filters().hiddenRepostHandles)
        assertTrue(filters.filters().hiddenReplies.isEmpty())
    }
}
