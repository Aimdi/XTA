package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowGroup
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** XTA's group_tree_test, combined_groups_test, group_board_layout_test and the group picker and deck rules, for the native port. */
class GroupsTest {
    /** news ─ tech ─ ai, news ─ sport; friends */
    private val parents = mapOf<String, String?>("news" to null, "tech" to "news", "ai" to "tech", "sport" to "news", "friends" to null)

    @Test
    fun aGroupsFeedIsItselfAndEverythingNestedInItAndALoopStillEnds() {
        assertEquals(setOf("news", "tech", "ai", "sport"), GroupTree.withDescendants("news", parents))
        assertEquals(setOf("tech", "ai"), GroupTree.withDescendants("tech", parents))
        assertEquals(setOf("friends"), GroupTree.withDescendants("friends", parents))
        assertEquals(setOf("nowhere"), GroupTree.withDescendants("nowhere", parents))
        assertEquals(setOf("a", "b"), GroupTree.withDescendants("a", mapOf("a" to "b", "b" to "a")))
        assertEquals(setOf("tech", "sport"), GroupTree.childrenOf("news", parents).toSet())
        assertTrue(GroupTree.childrenOf("ai", parents).isEmpty())
    }

    @Test
    fun theBoardShowsTopGroupsAndAGroupWhoseParentIsGoneComesBackToTheTop() {
        assertEquals(listOf("news", "friends"), GroupTree.topLevel(parents.keys.toList(), parents))
        assertEquals(listOf("orphan", "kept"), GroupTree.topLevel(listOf("orphan", "kept"), mapOf("orphan" to "deleted-group", "kept" to null)))
        assertEquals(listOf("self"), GroupTree.topLevel(listOf("self"), mapOf("self" to "self")))
    }

    @Test
    fun aNestingThatWouldLoopIsSeenBeforeItIsMade() {
        assertTrue(GroupTree.wouldLoop("news", "news", parents))
        assertTrue(GroupTree.wouldLoop("news", "ai", parents), "ai already sits under news")
        assertFalse(GroupTree.wouldLoop("friends", "tech", parents))
        assertFalse(GroupTree.wouldLoop("ai", "sport", parents))
    }

    @Test
    fun depthCountsFromTheTopAndStopsAtALoop() {
        assertEquals(listOf(0, 1, 2), listOf("news", "tech", "ai").map { GroupTree.depthOf(it, parents) })
        assertTrue(GroupTree.depthOf("a", mapOf("a" to "b", "b" to "a")) < 3)
    }

    @Test
    fun aChildFollowsItsParentAndEveryGroupAppearsOnce() {
        assertEquals(listOf("a", "b", "c"), GroupTree.treeOrder(listOf("a", "b", "c"), mapOf("a" to null, "b" to "a", "c" to null)))
        assertEquals(listOf("d", "a", "b", "c"), GroupTree.treeOrder(listOf("d", "c", "b", "a"), mapOf("a" to null, "b" to "a", "c" to "b", "d" to null)))
        val looped = GroupTree.treeOrder(listOf("a", "b", "c"), mapOf("a" to "b", "b" to "a", "c" to null))
        assertEquals(setOf("a", "b", "c"), looped.toSet())
        assertEquals(3, looped.size)
        assertEquals(listOf("b"), GroupTree.treeOrder(listOf("b"), mapOf("a" to null, "b" to "a")), "a group whose parent is filtered out is still shown")
    }

    @Test
    fun groupsAreCombinedOnAWhimAndBringWhatIsNestedInThem() {
        val combined = CombinedGroups().toggled("a").toggled("b")
        assertEquals(setOf("a", "b"), combined.ids)
        assertEquals(setOf("b"), combined.toggled("a").ids)
        assertTrue(combined.cleared().ids.isEmpty())
        assertEquals(setOf("b"), combined.released("a").ids, "a group is not read alongside itself")
        assertEquals(combined, combined.released("c"))
        val nested = mapOf("child" to "a", "grandchild" to "child", "other" to null, "b" to null, "a" to null)
        assertEquals(setOf("other", "a", "child", "grandchild", "b"), combined.covers("other", nested))
        assertEquals(setOf("other"), CombinedGroups().covers("other", nested))
    }

    @Test
    fun thePickerOpensWithTheAccountsGroupsThenTheLastPickedThenTheRest() {
        assertEquals(listOf("c", "b", "a", "d"), GroupPicks.order(listOf("a", "b", "c", "d"), selected = setOf("c"), recent = listOf("gone", "b", "c")))
        val recent = GroupPicks.remembered((1..12).map { "g$it" }, listOf("new", "g3"))
        assertEquals(12, recent.size)
        assertEquals(listOf("new", "g3", "g1", "g2", "g4"), recent.take(5))
        assertEquals(listOf("a"), GroupPicks.remembered(listOf("a"), emptyList()))
    }

    @Test
    fun theDeckKeepsXtasCommaSeparatedPins() {
        assertEquals(listOf("a", "b"), DeckGroups.parse(" a, ,b "))
        assertTrue(DeckGroups.parse(null).isEmpty())
        assertEquals("a,b", DeckGroups.join(listOf("a", "b")))
        assertEquals(listOf("a", "b"), DeckGroups.toggled(listOf("a"), "b"))
        assertEquals(listOf("b"), DeckGroups.toggled(listOf("a", "b"), "a"))
        assertTrue(DeckGroups.fits(widthDp = 400f, shortestSideDp = 600f))
        assertTrue(DeckGroups.fits(widthDp = 900f, shortestSideDp = 400f))
        assertFalse(DeckGroups.fits(widthDp = 411f, shortestSideDp = 411f))
    }

    @Test
    fun aPhoneKeepsTheReadersColumnsAndAWideWindowAddsColumns() {
        assertEquals(2, GroupBoard.columns(chosen = 2, width = 390f))
        assertEquals(3, GroupBoard.columns(chosen = 3, width = 390f))
        assertEquals(2, GroupBoard.columns(chosen = 1, width = 390f))
        assertEquals(3, GroupBoard.columns(chosen = 7, width = 390f))
        for (width in listOf(800f, 1280f, 1868f, 2536f)) {
            assertTrue(width / GroupBoard.columns(chosen = 2, width = width) <= GroupBoard.TILE_MAX_WIDTH, "width $width")
        }
        assertEquals(6, GroupBoard.columns(chosen = 2, width = 1868f))
    }

    @Test
    fun aWidenedBoardHasContentHeightTilesThatGrowWithLargeText() {
        assertNull(GroupBoard.tileHeight(chosen = 2, columns = 2, textScale = 1f))
        assertNull(GroupBoard.tileHeight(chosen = 3, columns = 3, textScale = 1f))
        assertEquals(GroupBoard.WIDE_TILE_HEIGHT, GroupBoard.tileHeight(chosen = 2, columns = 6, textScale = 1f))
        assertTrue(GroupBoard.tileHeight(chosen = 2, columns = 6, textScale = 2f)!! > GroupBoard.WIDE_TILE_HEIGHT)
        assertTrue(GroupBoard.tileRatio(2) > 1)
        assertEquals(1f, GroupBoard.tileRatio(3))
        assertEquals(GroupBoard.COMPACT_TILE_HEIGHT, GroupBoard.compactTileHeight(1f))
        assertTrue(GroupBoard.compactTileHeight(2f) > GroupBoard.COMPACT_TILE_HEIGHT)
    }

    @Test
    fun theBoardPutsNestedGroupsUnderTheirParentAndNsfwOnesInASectionOfTheirOwn() {
        val groups = listOf(
            FollowGroup("z", "Zulu", emptyList()),
            FollowGroup("a", "Alpha", emptyList()),
            FollowGroup("c", "Child", emptyList()),
            FollowGroup("n", "Night", emptyList(), nsfw = true),
            FollowGroup("p", "Pinned", emptyList(), pinned = true),
        )
        val board = GroupBoard.sections(groups, mapOf("c" to "z"))
        assertEquals(listOf("p" to 0, "a" to 0, "z" to 0, "c" to 1), board.safe.map { it.group.id to it.depth })
        assertEquals(listOf("n"), board.nsfw.map { it.group.id })
    }
}
