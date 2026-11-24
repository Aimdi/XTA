package com.aimdi.xtanative.ui

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Going forward again after going back, as a browser does. */
class ForwardRoutesTest {
    private val home = ForwardRoutes.Step("home", "home")
    private val search = ForwardRoutes.Step("search?q=cats", "search?q={q}&tab={tab}&focus={focus}")
    private val post = ForwardRoutes.Step("post/1", "post/{id}?media={media}")

    /** [step] shown as entry [id] over [below]. */
    private fun ForwardRoutes.show(id: String, step: ForwardRoutes.Step?, below: String?, pattern: String = step?.pattern ?: "intro") =
        shown(id, pattern, step, below)

    /** Home, then a search over it, then a post over that. */
    private fun opened() = ForwardRoutes().apply {
        show("h", home, null)
        show("s", search, "h")
        show("p", post, "s")
    }

    @Test
    fun backThenForwardOpensTheScreenLeftAgain() {
        val forward = opened()
        assertFalse(forward.canGoForward)
        assertNull(forward.next())
        forward.show("s", search, "h")
        assertTrue(forward.canGoForward)
        assertEquals(post, forward.next())
        assertFalse(forward.canGoForward, "nothing more, and one is opening")
        forward.show("p2", post, "s")
        assertFalse(forward.canGoForward)
    }

    @Test
    fun severalBacksGoForwardNewestFirst() {
        val forward = opened()
        forward.show("s", search, "h")
        forward.show("h", home, null)
        assertEquals(search, forward.next())
        forward.show("s2", search, "h")
        assertTrue(forward.canGoForward)
        assertEquals(post, forward.next())
        forward.show("p2", post, "s2")
        assertFalse(forward.canGoForward)
    }

    @Test
    fun theSameEntryShownAgainChangesNothing() {
        val forward = opened()
        forward.show("s", search, "h")
        forward.show("s", search, "h")
        assertEquals(post, forward.next())
    }

    @Test
    fun goingSomewhereNewForgetsTheWayForward() {
        val forward = opened()
        forward.show("s", search, "h")
        forward.show("x", ForwardRoutes.Step("profile/a", "profile/{screenName}?id={id}&tab={tab}"), "s")
        assertFalse(forward.canGoForward)
        assertNull(forward.next())
    }

    @Test
    fun aJumpOverSeveralScreensForgetsTheWayForward() {
        val forward = opened()
        forward.show("s", search, "h")
        forward.show("h", home, null)
        forward.show("s2", search, "h")
        forward.show("p2", post, "s2")
        forward.show("h", home, null)
        assertFalse(forward.canGoForward, "Alt+Home is a new page, as a browser's home")
    }

    @Test
    fun anotherScreenShownWhileForwardOpensForgetsTheRest() {
        val forward = opened()
        forward.show("s", search, "h")
        forward.show("h", home, null)
        forward.next()
        forward.show("t", ForwardRoutes.Step("settings", "settings"), "h")
        assertFalse(forward.canGoForward)
    }

    @Test
    fun backFromAScreenThatCannotBeOpenedAgainForgetsTheWayForward() {
        val forward = ForwardRoutes()
        forward.show("h", home, null)
        forward.show("i", null, "h")
        forward.show("p", post, "i")
        forward.show("i", null, "h")
        assertTrue(forward.canGoForward)
        forward.show("h", home, null)
        assertFalse(forward.canGoForward)
    }

    @Test
    fun keepsNoMoreThanItsLimit() {
        val forward = ForwardRoutes(limit = 1)
        forward.show("h", home, null)
        forward.show("s", search, "h")
        forward.show("p", post, "s")
        forward.show("s", search, "h")
        forward.show("h", home, null)
        assertEquals(search, forward.next())
        forward.show("s2", search, "h")
        assertNull(forward.next())
    }

    @Test
    fun aRouteIsFilledInFromItsArguments() {
        assertEquals("home", ForwardRoutes.routeOf("home", emptyMap()))
        assertEquals("post/1%2F2?media=0", ForwardRoutes.routeOf("post/{id}?media={media}", mapOf("id" to "1/2", "media" to 0)))
        assertEquals(
            "search?q=cats%20%26%20dogs&focus=false",
            ForwardRoutes.routeOf("search?q={q}&tab={tab}&focus={focus}", mapOf("q" to "cats & dogs", "tab" to null, "focus" to false)),
        )
        assertEquals("media/9/3", ForwardRoutes.routeOf("media/{postId}/{index}", mapOf("postId" to "9", "index" to 3)))
        assertEquals("search", ForwardRoutes.routeOf("search?q={q}", emptyMap()))
        assertNull(ForwardRoutes.routeOf("post/{id}", emptyMap()), "a path needs its argument")
    }

    @Test
    fun aStepThatDidNotOpenIsTheNextForwardAgain() {
        val forward = opened()
        forward.show("s", search, "h")
        assertEquals(post, forward.next())
        assertFalse(forward.canGoForward, "opening")
        forward.putBack(post)
        assertTrue(forward.canGoForward, "the post's card refused it: not lost, and forward is not stuck")
        assertEquals(post, forward.next())
        forward.show("p2", post, "s")
        forward.putBack(post)
        assertFalse(forward.canGoForward, "one that did open is not put back")
    }

    @Test
    fun screensThatActAsTheyAreShownAreNeverOpenedAgain() {
        assertTrue(Routes.BROWSER in NOT_FORWARD, "the browser screen would send its page to the browser again")
        assertTrue(Routes.LIVE in NOT_FORWARD, "a live room would start playing again")
    }
}
