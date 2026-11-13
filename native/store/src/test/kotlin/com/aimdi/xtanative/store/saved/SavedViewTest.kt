package com.aimdi.xtanative.store.saved

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SavedViewTest {
    private val later = SavedFolder("later", "Later")
    private val art = SavedFolder("art", "Art", 1)

    private fun tweet(id: String, text: String, media: Boolean = false) = SavedPost(
        id,
        "u$id",
        json(
            """{"id_str":"$id","full_text":"$text","user":{"id_str":"u$id","screen_name":"user$id","name":"User"}${
                if (media) ""","extended_entities":{"media":[{"id_str":"m$id","type":"photo","media_url_https":"https://pbs.twimg.com/m$id.jpg"}]}""" else ""
            }}""",
        ),
    )

    private val reddit = SavedPost("reddit:r1", "pics", json("""{"xtaPlugin":"reddit","post":{"id":"r1","title":"A Reddit cat","subreddit":"pics","permalink":"/r/pics/r1","url":"https://i.redd.it/cat.jpg"}}"""))

    @Test
    fun notesIsAlwaysATabAfterLikesAndNeverAFolder() {
        val order = SavedTabs.ordered(emptyList(), null)
        assertEquals(listOf(SavedTabs.ALL, SavedTabs.UNFILED, SavedTabs.FAVORITES, SavedTabs.NOTES), order)
        assertTrue(SavedTabs.isBuiltIn(SavedTabs.NOTES))
        assertFalse(SavedTabs.isBuiltIn("later"))
    }

    @Test
    fun aStoredOrderIsKeptWithGoneFoldersDroppedAndNewTabsAfterIt() {
        val stored = SavedTabs.decodeOrder("""["notes","all","gone","later","unfiled","favorites"]""")
        assertEquals(listOf("notes", "all", "later", "unfiled", "favorites", "art"), SavedTabs.ordered(listOf(later, art), stored))
        assertEquals(emptyList(), SavedTabs.decodeOrder("{not a list"))
        assertEquals("""["all","notes"]""", SavedTabs.encodeOrder(listOf("all", "notes")))
        assertEquals(listOf("b", "c", "a"), SavedTabs.moved(listOf("a", "b", "c"), 0, 2))
    }

    @Test
    fun unfiledShowsOnlyBesideFoldersAndAHiddenTabFallsBackToAll() {
        val order = SavedTabs.ordered(emptyList(), null)
        assertEquals(listOf(SavedTabs.ALL, SavedTabs.FAVORITES, SavedTabs.NOTES), SavedTabs.shown(order, emptyList(), showAll = true, showUnfiled = true, showFavorites = true))
        assertEquals(SavedTabs.ALL, SavedView(tab = "deleted").reconciled(listOf(later), showUnfiled = true, showFavorites = true).tab)
        assertEquals("later", SavedView(tab = "later").reconciled(listOf(later), showUnfiled = true, showFavorites = true).tab)
        assertEquals(SavedTabs.ALL, SavedView(tab = SavedTabs.FAVORITES).reconciled(emptyList(), showUnfiled = true, showFavorites = false).tab)
        assertEquals(SavedTabs.NOTES, SavedView(tab = SavedTabs.NOTES).reconciled(emptyList(), showUnfiled = false, showFavorites = false).tab)
    }

    @Test
    fun likesTappedAgainBreakTheLikesDownByGroupAndAnotherTabEndsASelection() {
        val likes = SavedView().select(SavedTabs.FAVORITES)
        assertFalse(likes.likesByGroup)
        assertTrue(likes.select(SavedTabs.FAVORITES).likesByGroup)
        assertFalse(likes.select(SavedTabs.FAVORITES).select(SavedTabs.FAVORITES).likesByGroup)

        val selecting = SavedView(mediaOnly = true).beginSelection("1").toggle("2")
        assertEquals(setOf("1", "2"), selecting.selected)
        assertFalse(selecting.mediaOnly, "the grid gives way to the list it selects in")
        assertTrue(selecting.allSelected(listOf("1", "2")))
        assertEquals(emptySet(), selecting.selectAll(listOf("1", "2")).selected)
        assertEquals(setOf("1", "2", "3"), selecting.selectAll(listOf("1", "2", "3")).selected)
        assertFalse(selecting.select("later").selecting)
    }

    @Test
    fun theOpenTabShowsItsFolderSearchNetworkOrderAndMedia() {
        val one = tweet("1", "Cats in Kyoto", media = true).copy(folderId = "later")
        val two = tweet("2", "Dogs").copy(note = "about KYOTO trips")
        val three = tweet("3", "Birds")
        val saved = listOf(one, two, three, reddit)

        assertEquals(listOf("1"), SavedView(tab = "later").posts(saved).map { it.id })
        assertEquals(listOf("2", "3", "reddit:r1"), SavedView(tab = SavedTabs.UNFILED).posts(saved).map { it.id })
        assertEquals(listOf("1", "2"), SavedView(query = "  kyoto ").posts(saved).map { it.id }, "the post or the reader's note")
        assertEquals(listOf("reddit:r1"), SavedView(source = SavedSource.REDDIT).posts(saved).map { it.id })
        assertEquals(listOf("reddit:r1", "3", "2", "1"), SavedView(sort = SavedSort.OLDEST).posts(saved).map { it.id })
        assertEquals(listOf("1", "reddit:r1"), SavedView(mediaOnly = true).posts(saved).map { it.id })
    }

    @Test
    fun likesAreSearchedByTheirPostAndKeepTheirOrder() {
        val likes = listOf(LikedPost("1", "u1", tweet("1", "Cats").content), LikedPost("2", "u2", tweet("2", "Dogs").content))
        assertEquals(listOf("2"), SavedView(tab = SavedTabs.FAVORITES, query = "dog").likes(likes).map { it.id })
        assertFalse(SavedView(tab = SavedTabs.FAVORITES).sortable)
        assertEquals(listOf("1", "2"), SavedView(tab = SavedTabs.FAVORITES, sort = SavedSort.OLDEST).likes(likes).map { it.id })
    }

    @Test
    fun notesListEachThreadOnceByTheNotesTheSearchFinds() {
        val root = LocalPost("a", "Trip plans", 1, 1)
        val reply = LocalPost("b", "Kyoto first", 2, 2, replyTo = "a")
        val photo = LocalPost("c", "", 3, 3, media = listOf(NoteMedia("m", "Kyoto.jpg", "image/jpeg")))
        val notes = listOf(photo, reply, root)
        assertEquals(listOf("c", "a"), SavedView(tab = SavedTabs.NOTES).notes(notes).map { it.id })
        assertEquals(listOf("c", "b"), SavedView(tab = SavedTabs.NOTES, query = "kyoto").notes(notes).map { it.id })
    }

    @Test
    fun theEmptyMessageFollowsSearchLikesNotesAllThenFolder() {
        assertEquals(SavedEmptyKind.SEARCH, SavedView(query = "cats").emptyKind)
        assertEquals(SavedEmptyKind.LIKES, SavedView(tab = SavedTabs.FAVORITES).emptyKind)
        assertEquals(SavedEmptyKind.SAVED, SavedView().emptyKind)
        assertEquals(SavedEmptyKind.NOTES, SavedView(tab = SavedTabs.NOTES).emptyKind)
        assertEquals(SavedEmptyKind.FOLDER, SavedView(tab = "folder-1").emptyKind)
    }

    @Test
    fun aProfilesSavedTabListsItsSavesThenItsLikesOnceEach() {
        val saved = listOf(SavedPost("s1", "u1", null), SavedPost("both", "u1", null), SavedPost("s2", "other", null))
        val liked = listOf(LikedPost("l1", "u1", null), LikedPost("both", "u1", null), LikedPost("l2", "other", null))
        assertEquals(listOf("s1", "both"), profileArchive(saved, liked, "u1", ArchiveFilter.BOOKMARKS).map { it.id })
        assertEquals(listOf("l1", "both"), profileArchive(saved, liked, "u1", ArchiveFilter.LIKES).map { it.id })
        assertEquals(listOf("s1", "both", "l1"), profileArchive(saved, liked, "u1", ArchiveFilter.ALL).map { it.id })
        assertTrue(profileArchive(saved, liked, "nobody", ArchiveFilter.ALL).isEmpty())
    }
}
