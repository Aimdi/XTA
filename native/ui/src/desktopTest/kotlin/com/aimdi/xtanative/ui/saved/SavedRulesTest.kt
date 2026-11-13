package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.core.backup.FollowGroup
import com.aimdi.xtanative.store.saved.LocalPost
import com.aimdi.xtanative.store.saved.SavedEmptyKind
import com.aimdi.xtanative.store.saved.SavedFolder
import com.aimdi.xtanative.store.saved.SavedSort
import com.aimdi.xtanative.store.saved.SavedSource
import com.aimdi.xtanative.store.saved.SavedTabs
import com.aimdi.xtanative.store.saved.SavedView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.test.assertTrue

class SavedRulesTest {
    private val later = SavedFolder("later", "Later", position = 1)
    private val art = SavedFolder("art", "Art", position = 0)

    @Test
    fun theChipsFollowTheReadersOrderAndOnlyFoldersCanBeEdited() {
        val chips = savedChips(listOf(later, art), listOf(SavedTabs.NOTES, "later"), showAll = true, showUnfiled = true, showFavorites = false)
        assertEquals(listOf(SavedTabs.NOTES, "later", SavedTabs.ALL, "art", SavedTabs.UNFILED), chips.map { it.token })
        assertEquals(listOf(false, true, false, true, false), chips.map { it.editable })
        assertEquals("Later", chips[1].folder?.name)
    }

    @Test
    fun unfiledLeavesTheStripWithTheLastFolder() {
        val chips = savedChips(emptyList(), emptyList(), showAll = true, showUnfiled = true, showFavorites = true)
        assertEquals(listOf(SavedTabs.ALL, SavedTabs.FAVORITES, SavedTabs.NOTES), chips.map { it.token })
    }

    @Test
    fun filterAndSortOffersWhatTheTabCanUse() {
        assertEquals(SavedFilterMenu(sortable = true, showMedia = true), SavedFilterMenu.of(SavedView()))
        assertEquals(SavedFilterMenu(sortable = false, showMedia = true), SavedFilterMenu.of(SavedView(tab = SavedTabs.FAVORITES)))
        assertFalse(SavedFilterMenu.of(SavedView(tab = SavedTabs.NOTES)).enabled)
    }

    @Test
    fun selectEndsMediaOnlyAndTheOrderTurnsAround() {
        val selecting = SavedView(mediaOnly = true).after(SavedFilterAction.SELECT)
        assertTrue(selecting.selecting)
        assertFalse(selecting.mediaOnly)
        assertEquals(SavedSort.OLDEST, SavedView().after(SavedFilterAction.SORT_OLDEST).sort)
        assertTrue(SavedView().after(SavedFilterAction.MEDIA_ONLY).mediaOnly)
    }

    @Test
    fun theLockSaysWhatTheTabKeeps() {
        assertEquals(SavedNotice.LIKES, SavedNotice.of(SavedTabs.FAVORITES))
        assertEquals(SavedNotice.NOTES, SavedNotice.of(SavedTabs.NOTES))
        assertEquals(SavedNotice.SAVES, SavedNotice.of("later"))
    }

    @Test
    fun mediaOnlyOverXPostsIsAGridOfTheirMedia() {
        val saved = listOf(savedX("1", photo = true), savedX("2"), savedX("3", photo = true))
        val body = savedBody(SavedView(mediaOnly = true), saved, emptyList(), emptyList(), emptyList())
        assertIs<SavedBody.Grid>(body)
        assertEquals(listOf("1", "3"), body.tiles.map { it.postId })
        assertEquals(listOf("1", "3"), body.tiles.map { it.savedId })

        val none = savedBody(SavedView(mediaOnly = true), listOf(savedX("2")), emptyList(), emptyList(), emptyList())
        assertEquals(SavedBody.Grid(emptyList()), none, "the grid says no post has media")
    }

    @Test
    fun mediaOnlyAmongOtherNetworksKeepsThePostsWithMedia() {
        val saved = listOf(savedX("1", photo = true), savedReddit("r1"), savedReddit("r2", picture = true))
        val body = savedBody(SavedView(mediaOnly = true), saved, emptyList(), emptyList(), emptyList())
        assertIs<SavedBody.Posts>(body)
        assertEquals(listOf("1", "reddit:r2"), body.posts.map { it.id })
        assertEquals(listOf("1", "reddit:r2"), visibleIds(body))
    }

    @Test
    fun anEmptyTabSaysWhyItIsEmpty() {
        val inFolder = savedBody(SavedView(tab = "later"), listOf(savedX("1")), emptyList(), emptyList(), emptyList())
        assertEquals(SavedBody.Empty(SavedEmptyKind.FOLDER), inFolder)
        val searched = savedBody(SavedView(query = "nothing like it"), listOf(savedX("1")), emptyList(), emptyList(), emptyList())
        assertEquals(SavedBody.Empty(SavedEmptyKind.SEARCH), searched)
        val noLikes = savedBody(SavedView(tab = SavedTabs.FAVORITES), emptyList(), emptyList(), emptyList(), emptyList())
        assertEquals(SavedBody.Empty(SavedEmptyKind.LIKES), noLikes)
        assertEquals(emptyList(), visibleIds(noLikes))
    }

    @Test
    fun theNetworkPickerKeepsItsNetworksPosts() {
        val saved = listOf(savedX("1"), savedReddit("r1"))
        val body = savedBody(SavedView(source = SavedSource.REDDIT), saved, emptyList(), emptyList(), emptyList())
        assertEquals(listOf("reddit:r1"), (body as SavedBody.Posts).posts.map { it.id })
    }

    @Test
    fun likesByGroupHeadTheirGroupsInOrderThenTheRest() {
        val groups = listOf(FollowGroup("g2", "Art", listOf("a")), FollowGroup("g1", "News", listOf("a", "n")))
        val likes = listOf(likedX("1", author = "a"), likedX("2", author = "n"), likedX("3", author = "x"))
        val body = savedBody(SavedView(tab = SavedTabs.FAVORITES, likesByGroup = true), emptyList(), likes, emptyList(), groups)
        assertIs<SavedBody.LikesByGroup>(body)
        val shape = body.rows.map { row ->
            when (row) {
                is LikesRow.Heading -> "#" + (row.name ?: "none")
                is LikesRow.Like -> row.like.id
            }
        }
        assertEquals(listOf("#Art", "1", "#News", "1", "2", "#none", "3"), shape)
        assertEquals(body.rows.size, body.rows.map { it.key }.toSet().size, "a like under two groups has a key under each")
    }

    @Test
    fun likesWithoutTheBreakdownAreOneList() {
        val body = savedBody(SavedView(tab = SavedTabs.FAVORITES), emptyList(), listOf(likedX("1"), likedX("2")), emptyList(), emptyList())
        assertEquals(listOf("1", "2"), (body as SavedBody.Likes).likes.map { it.id })
    }

    @Test
    fun notesListEachThreadOnceWithItsReplies() {
        val notes = listOf(
            LocalPost("reply", "Re", 3, 3, replyTo = "root"),
            LocalPost("root", "First", 1, 1),
            LocalPost("alone", "Alone", 2, 2),
        )
        val body = savedBody(SavedView(tab = SavedTabs.NOTES), emptyList(), emptyList(), notes, emptyList())
        assertIs<SavedBody.Notes>(body)
        assertEquals(listOf("root" to 1, "alone" to 0), body.notes.map { it.note.id to it.replies })
        assertEquals(listOf("root", "alone"), body.notes.map { it.threadRoot })
        assertNull((body as? SavedBody.Posts))
    }
}
