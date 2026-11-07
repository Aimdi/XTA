package com.aimdi.xtanative.plugins.substack

import com.aimdi.xtanative.networks.substack.SubstackFeedFilter
import com.aimdi.xtanative.networks.substack.SubstackFilters
import com.aimdi.xtanative.networks.substack.SubstackHomeRules
import com.aimdi.xtanative.networks.substack.SubstackLibrarySection
import com.aimdi.xtanative.networks.substack.SubstackLoadedOptions
import com.aimdi.xtanative.networks.substack.SubstackOrder
import com.aimdi.xtanative.networks.substack.SubstackPost
import java.time.LocalDate
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** XTA's substack_home_controls_test, its rules: the loaded search and orders, and the controls the home tab keeps. */
class SubstackHomeControlsTest {
    private fun post(id: Int, likes: Long? = null, date: String? = null) = SubstackPost(
        "$id", "A quiet morning $id", "morning-$id", "https://fieldnotes.substack.com", "Field Notes",
        subtitle = "Observations from the coast", authorName = "Maya Chen", reactionCount = likes,
        postDate = date ?: LocalDate.of(2026, 9, 25).minusDays(id.toLong()).toString() + "T00:00:00Z",
    )

    @Test
    fun theLoadedSearchCombinesPublicationAuthorAndArticleWordsWithoutChangingTheList() {
        val posts = listOf(post(1), post(2))
        assertEquals(listOf("2"), SubstackFilters.loaded(posts, "MAYA coast 2", SubstackOrder.NEWEST).map { it.id })
        assertEquals(listOf("1", "2"), posts.map { it.id })
    }

    @Test
    fun oldestAndPopularAreStableAndUndatedPostsComeLast() {
        val posts = listOf(post(1, likes = 2), post(2, likes = 10), post(3, likes = 10), post(4, date = "invalid"))
        assertEquals(listOf("3", "2", "1", "4"), SubstackFilters.loaded(posts, "", SubstackOrder.OLDEST).map { it.id })
        assertEquals(listOf("2", "3", "1", "4"), SubstackFilters.loaded(posts, "", SubstackOrder.POPULAR).map { it.id })
    }

    @Test
    fun eachFeedKeepsItsOwnSearchAndAResetClearsHomesFilterToo() {
        val controls = HomeControls()
        controls.configure(HomePane.HOME, SubstackLoadedOptions(query = "morning 3"))
        assertEquals("morning 3", controls.options(HomePane.HOME).query)
        assertEquals("", controls.options(HomePane.INBOX).query, "Inbox has its own search")
        controls.configure(HomePane.HOME, controls.home.copy(order = SubstackOrder.OLDEST))
        controls.filter = SubstackFeedFilter.UNREAD
        assertTrue(SubstackHomeRules.filtered(controls.home, controls.filter), "the options button is marked")
        controls.reset(HomePane.HOME)
        assertEquals(SubstackLoadedOptions(), controls.home)
        assertEquals(SubstackFeedFilter.ALL, controls.filter)
        assertFalse(SubstackHomeRules.filtered(controls.home, controls.filter))
    }

    @Test
    fun theLibrarysChoiceAndSearchStayWhileTheHomeTabIsKept() {
        val controls = HomeControls()
        controls.librarySection = SubstackLibrarySection.SAVED
        controls.libraryQuery = "coast"
        controls.reset(HomePane.HOME)
        assertEquals(SubstackLibrarySection.SAVED, controls.librarySection)
        assertEquals("coast", controls.libraryQuery)
    }

    @Test
    fun theSavedAndUnreadFiltersFollowTheMarksAtOnce() {
        val posts = listOf(post(0), post(1))
        val publications = listOf(posts[0].publication)
        val saved = mutableSetOf(posts[0].key)
        fun home(filter: SubstackFeedFilter, read: Set<String> = emptySet()) =
            SubstackHomeRules.home(posts, publications, filter, read, { it.key in saved }, SubstackLoadedOptions()).map { it.id }
        assertEquals(listOf("0"), home(SubstackFeedFilter.SAVED))
        saved.clear()
        assertEquals(emptyList(), home(SubstackFeedFilter.SAVED), "unsaving takes it out at once")
        assertEquals(listOf("1"), home(SubstackFeedFilter.UNREAD, setOf(posts[0].key)))
        assertEquals(listOf("0", "1"), home(SubstackFeedFilter.UNREAD), "marked unread again, it is back")
        assertTrue(SubstackHomeRules.anyUnread(posts, setOf(posts[0].key)))
        assertFalse(SubstackHomeRules.anyUnread(posts, posts.mapTo(HashSet()) { it.key }), "Mark all as read has nothing left to do")
    }
}
