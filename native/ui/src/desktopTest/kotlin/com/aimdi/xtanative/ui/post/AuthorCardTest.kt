package com.aimdi.xtanative.ui.post

import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import com.aimdi.xtanative.core.x.model.XEntity
import com.aimdi.xtanative.core.x.model.XUser
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

/** What the author card over a post tells, and where it goes. */
class AuthorCardTest {
    private val maya = XUser(id = "1", screenName = "maya", name = "Maya")

    @Test
    fun aBareAuthorShowsNameAndHandleAlone() {
        val facts = AuthorCard.of(maya)
        assertEquals(AuthorCard.Facts("Maya", "maya", bio = null, following = null, followers = null, locked = false), facts)
    }

    @Test
    fun theBioWritesItsLinksAsXDisplaysThemAndABlankOneIsNone() {
        val text = "Docs at https://t.co/abc ok"
        val user = maya.copy(description = text, descriptionLinks = listOf(XEntity.Link(8, 24, "https://t.co/abc", "https://example.org/docs", "example.org/docs")))
        assertEquals("Docs at example.org/docs ok", AuthorCard.of(user).bio)
        assertNull(AuthorCard.of(maya.copy(description = "  \n ")).bio)
    }

    @Test
    fun countsAreThoseThePostCarriedZeroIncluded() {
        val facts = AuthorCard.of(maya.copy(following = 0, followers = 1200, protected = true))
        assertEquals(0L, facts.following)
        assertEquals(1200L, facts.followers)
        assertEquals(true, facts.locked)
    }

    @Test
    fun aNamelessAuthorIsNamedByTheHandle() {
        assertEquals("maya", AuthorCard.of(maya.copy(name = " ")).name)
    }

    @Test
    fun theCardGoesUnderTheAuthorOrAboveNearTheFoot() {
        val window = IntSize(1000, 800)
        val card = IntSize(300, 200)
        assertEquals(IntOffset(100, 134), AuthorCard.position(IntRect(100, 100, 148, 130), window, card, gap = 4, margin = 8))
        assertEquals(IntOffset(100, 496), AuthorCard.position(IntRect(100, 700, 148, 730), window, card, gap = 4, margin = 8))
    }

    @Test
    fun theCardStaysInsideTheWindow() {
        val window = IntSize(500, 250)
        val card = IntSize(300, 200)
        // Too close to the right side: pulled in; no room above or below: kept inside.
        assertEquals(IntOffset(192, 42), AuthorCard.position(IntRect(400, 100, 448, 130), window, card, gap = 4, margin = 8))
    }
}
