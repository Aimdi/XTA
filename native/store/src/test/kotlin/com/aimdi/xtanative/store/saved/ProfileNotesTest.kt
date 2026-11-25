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

    @Test
    fun anAccountsPostsAreHiddenAndShownAgainOnThisDevice(): Unit = runBlocking {
        val filters = ProfileFiltersStore(dir)
        assertFalse(filters.filters().allHidden("1"))
        filters.setAllHidden("1", "Tommy", true)
        filters.setRepliesHidden("2", "Maya", true)
        val written = filters.filters()
        filters.setAllHidden("1", "Tommy", true)
        assertEquals(written, filters.filters(), "hiding twice changes nothing")

        val read = ProfileFiltersStore(dir).filters()
        assertTrue(read.allHidden("1"))
        assertFalse(read.allHidden("2"))
        assertEquals(setOf("tommy"), read.hiddenAllHandles, "matched by lower-case handle, like the other filters")
        assertFalse(read.repostsHidden("1"), "the other filters are their own")
        assertEquals(listOf(FeedFilterRow("1", "tommy")), filters.allHiddenContent())

        filters.replace(listOf(FeedFilterRow("3", "Ann")), emptyList())
        assertTrue(filters.filters().allHidden("1"), "a backup's reposts and replies leave the hidden accounts alone")
        filters.replaceAllHidden(listOf(FeedFilterRow("4", "Bo"), FeedFilterRow("", "nobody")))
        assertEquals(mapOf("4" to "bo"), filters.filters().hiddenAll)
        assertEquals(setOf("ann"), filters.filters().hiddenRepostHandles)

        filters.setAllHidden("4", "Bo", false)
        filters.setAllHidden("9", "never", false)
        assertTrue(ProfileFiltersStore(dir).filters().hiddenAll.isEmpty())
    }

    @Test
    fun aFileFromBeforeAccountsCouldBeHiddenHidesNoOneAndKeepsItsFilters(): Unit = runBlocking {
        val file = java.io.File(dir.root, "saved/profile-filters.json").apply { parentFile.mkdirs() }
        file.writeText("""{"v":1,"reposts":{"1":"tommy"},"replies":{"2":"maya"}}""")
        val filters = ProfileFiltersStore(dir)
        assertEquals(ProfileFilters(hiddenReposts = mapOf("1" to "tommy"), hiddenReplies = mapOf("2" to "maya")), filters.filters())

        filters.setAllHidden("3", "Ann", true)
        val text = file.readText()
        assertTrue(""""v":1""" in text && """"reposts":{"1":"tommy"}""" in text, "an older build still reads the file: $text")
        assertTrue(""""all":{"3":"ann"}""" in text, text)
    }
}
