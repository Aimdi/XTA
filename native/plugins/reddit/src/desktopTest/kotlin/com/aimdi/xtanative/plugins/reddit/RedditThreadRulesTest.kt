package com.aimdi.xtanative.plugins.reddit

import com.aimdi.xtanative.networks.reddit.RedditComment
import com.aimdi.xtanative.networks.reddit.RedditCommentsCursor
import com.aimdi.xtanative.networks.reddit.RedditPost
import com.aimdi.xtanative.networks.reddit.flattenComments
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class RedditThreadRulesTest {
    private fun comment(id: String, vararg replies: RedditComment) = RedditComment(id = id, body = "text $id", replies = replies.toList())

    /** a (b (c), d), e, f (g) */
    private val tree = flattenComments(
        listOf(
            comment("a", comment("b", comment("c")), comment("d")),
            comment("e"),
            comment("f", comment("g")),
        ),
    )

    private val post = RedditPost(id = "p", title = "t", subreddit = "s", permalink = "/r/s/comments/p/t/", commentCount = 3)

    @Test
    fun aFoldedCommentKeepsItsRowAndHidesEverythingUnderIt() {
        val rows = RedditThreadRules.visible(tree, setOf("a"))
        assertEquals(listOf("a", "e", "f", "g"), rows.map { it.entry.comment.id })
        assertEquals(3, rows.first().hidden)
        assertEquals(listOf("a", "b", "d", "e", "f", "g"), RedditThreadRules.visible(tree, setOf("b")).map { it.entry.comment.id })
        assertEquals(tree.size, RedditThreadRules.visible(tree, emptySet()).size)
    }

    @Test
    fun collapseAllFoldsTheTopLevelCommentsWithRepliesAndExpandAllUndoesIt() {
        assertEquals(setOf("a", "f"), RedditThreadRules.foldable(tree))
        assertEquals(false, RedditThreadRules.allFolded(tree, setOf("a")))
        val folded = RedditThreadRules.toggledAll(tree, setOf("b"))
        assertEquals(setOf("a", "b", "f"), folded)
        assertEquals(true, RedditThreadRules.allFolded(tree, folded))
        assertEquals(setOf("b"), RedditThreadRules.toggledAll(tree, folded))
        assertNull(RedditThreadRules.allFolded(flattenComments(listOf(comment("x"))), emptySet()))
    }

    @Test
    fun theJumpLandsOnTheNextTopLevelCommentAndStopsAfterTheLast() {
        val rows = RedditThreadRules.visible(tree, emptySet())
        assertEquals(listOf(0, 4, 5), RedditThreadRules.topLevelRows(rows))
        assertEquals(0, RedditThreadRules.nextTopLevel(rows, -1))
        assertEquals(4, RedditThreadRules.nextTopLevel(rows, 0))
        assertEquals(4, RedditThreadRules.nextTopLevel(rows, 2))
        assertNull(RedditThreadRules.nextTopLevel(rows, 5))
    }

    @Test
    fun theCountIsRedditsOrWhatLoadedWhenThatIsMore() {
        assertEquals(7, RedditThreadRules.commentCount(post, tree))
        assertEquals(30, RedditThreadRules.commentCount(post.copy(commentCount = 30), tree))
        assertEquals(3, RedditThreadRules.commentCount(post, null))
    }

    @Test
    fun heldBackCommentsLoadHereUntilRedditRefusesThenOpenOnReddit() {
        val root = RedditComment(id = "more", body = "", permalink = "/r/s/comments/p/t", moreCount = -1, nextPage = RedditCommentsCursor("/svc/x", "c1"))
        assertEquals(HeldBack.LOAD_HERE, RedditThreadRules.heldBack(root, post, moreRefused = false))
        assertEquals(HeldBack.OPEN_ON_REDDIT, RedditThreadRules.heldBack(root, post, moreRefused = true))
        assertEquals(HeldBack.OPEN_ON_REDDIT, RedditThreadRules.heldBack(root.copy(nextPage = null), post, moreRefused = false))
        val deep = RedditComment(id = "continue", body = "", permalink = "/r/s/comments/p/t/c9/", moreCount = -1)
        assertEquals(HeldBack.OPEN_SUBTREE, RedditThreadRules.heldBack(deep, post, moreRefused = false))
        assertEquals(HeldBack.NOTHING, RedditThreadRules.heldBack(RedditComment(id = "x", body = "", moreCount = 3), post, false))
    }

    @Test
    fun aLoadedPageOfHeldBackCommentsTakesTheStubsPlace() {
        val stub = RedditComment(id = "more", body = "", permalink = "/r/s/comments/p/t/", moreCount = -1)
        val shown = flattenComments(listOf(comment("a"), stub))
        val rows = flattenComments(listOf(comment("n1"), comment("n2")))
        assertEquals(listOf("a", "n1", "n2"), RedditThreadRules.replacing(shown, stub, rows).map { it.comment.id })
    }

    @Test
    fun aRemovedCommentSaysWhoTookItsWords() {
        assertEquals(Removed.BY_MODERATORS, RedditThreadRules.removed(RedditComment(id = "1", body = "[removed]")))
        assertEquals(Removed.BY_REDDIT, RedditThreadRules.removed(RedditComment(id = "1", body = " [ Removed by Reddit ] ")))
        assertEquals(Removed.UNAVAILABLE, RedditThreadRules.removed(RedditComment(id = "1", body = "[unavailable]")))
        assertEquals(Removed.DELETED, RedditThreadRules.removed(RedditComment(id = "1", body = "[deleted]")))
    }

    @Test
    fun linksLeadToReddit() {
        assertEquals("https://www.reddit.com/r/s/comments/p/t/", RedditThreadRules.postUrl(post))
        assertEquals("https://www.reddit.com/r/s/comments/p/t/c/", RedditThreadRules.commentUrl(RedditComment(id = "c", body = "", permalink = "/r/s/comments/p/t/c/")))
        assertEquals("https://old.reddit.com/x", RedditThreadRules.commentUrl(RedditComment(id = "c", body = "", permalink = "https://old.reddit.com/x")))
        assertNull(RedditThreadRules.commentUrl(RedditComment(id = "c", body = "")))
    }

    @Test
    fun aPostFromSearchTakesItsLinkAndPicturesFromItsOwnPage() {
        val bare = post.copy(isSelf = true)
        val linked = RedditThreadRules.withPageMedia(bare, "https://example.com/article", emptyList())
        assertEquals("https://example.com/article", linked.url)
        assertFalse(linked.isSelf)
        val gallery = RedditThreadRules.withPageMedia(bare, null, listOf("https://i.redd.it/1.jpg", "https://preview.redd.it/1.jpg?width=640", "https://i.redd.it/2.jpg"))
        assertEquals(listOf("https://i.redd.it/1.jpg", "https://i.redd.it/2.jpg"), gallery.galleryImages)
        assertEquals(bare, RedditThreadRules.withPageMedia(bare, "https://www.reddit.com/r/s/comments/p/t/", emptyList()))
        val picture = RedditThreadRules.withPageMedia(bare, "https://i.redd.it/x.jpg", listOf("https://preview.redd.it/x.jpg?width=108"))
        assertEquals("https://i.redd.it/x.jpg", picture.url)
        assertTrue(picture.galleryImages.isEmpty())
    }

    @Test
    fun theWordsUnderAPictureLeaveOutWhatThePictureShows() {
        val picture = post.copy(url = "https://i.redd.it/x.jpg")
        assertNull(RedditThreadRules.visibleSelfText(picture, "https://i.redd.it/x.jpg"))
        assertNull(RedditThreadRules.visibleSelfText(picture, "[image]"))
        assertEquals("Look at this", RedditThreadRules.visibleSelfText(picture, "Look at this https://i.redd.it/x.jpg"))
        assertEquals("Plain words", RedditThreadRules.visibleSelfText(post, "Plain words"))
        assertNull(RedditThreadRules.visibleSelfText(post, ""))
    }
}
