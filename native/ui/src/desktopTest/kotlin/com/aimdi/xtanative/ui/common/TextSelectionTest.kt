package com.aimdi.xtanative.ui.common

import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TextSelectionTest {
    private fun selectsAll(
        key: Key = Key.A,
        type: KeyEventType = KeyEventType.KeyDown,
        ctrl: Boolean = true,
        shift: Boolean = false,
        alt: Boolean = false,
        meta: Boolean = false,
    ) = TextSelectionKeys.selectsAll(key, type, ctrl, shift, alt, meta)

    @Test
    fun ctrlAAloneSelectsAllOfABlock() {
        assertTrue(selectsAll())
    }

    @Test
    fun otherKeysModifiersAndReleasesDoNot() {
        assertFalse(selectsAll(key = Key.C))
        assertFalse(selectsAll(ctrl = false))
        assertFalse(selectsAll(shift = true))
        assertFalse(selectsAll(alt = true))
        assertFalse(selectsAll(meta = true))
        assertFalse(selectsAll(type = KeyEventType.KeyUp))
    }

    @Test
    fun aSelectionThatCouldBeCopiedIsOfferedAsThePrimaryOne() {
        assertEquals("tweet", PrimaryText.offered("tweet", copyable = true))
        assertNull(PrimaryText.offered("", copyable = true))
        assertNull(PrimaryText.offered("secret", copyable = false))
    }

    @Test
    fun composeStillHasWhatTheBlocksReach() {
        val manager = Class.forName(SelectionManagers.MANAGER)
        assertNotNull(manager.getMethod(SelectionManagers.SELECT_ALL))
        assertNotNull(manager.getMethod(SelectionManagers.HAS_FOCUS))
    }

    @Test
    fun ctrlASelectsAllOfOnlyTheBlockWithTheKeys() {
        var selected = ""
        val idle = TextBlock(focused = { false }, selectAll = { selected = "idle" })
        val clicked = TextBlock(focused = { true }, selectAll = { selected = "clicked" })
        TextBlocks.add(idle)
        assertFalse(TextBlocks.selectAllInFocused())
        TextBlocks.add(clicked)
        assertTrue(TextBlocks.selectAllInFocused())
        assertEquals("clicked", selected)
        TextBlocks.remove(clicked)
        TextBlocks.remove(idle)
        assertFalse(TextBlocks.selectAllInFocused())
    }

    @Test
    fun aBlockInAWindowInTheBackgroundDoesNotTakeCtrlA() {
        // Compose keeps the post window's block focused after the main window is raised.
        var selected = ""
        var postWindowFocused = true
        val inPostWindow = TextBlock(focused = { true }, selectAll = { selected = "post" }).inWindow { postWindowFocused }
        TextBlocks.add(inPostWindow)
        try {
            postWindowFocused = false
            assertFalse(TextBlocks.selectAllInFocused())
            assertEquals("", selected)
            postWindowFocused = true
            assertTrue(TextBlocks.selectAllInFocused())
            assertEquals("post", selected)
        } finally {
            TextBlocks.remove(inPostWindow)
        }
    }
}
