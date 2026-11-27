package com.aimdi.xtanative.ui.saved

import com.aimdi.xtanative.ui.common.RowHighlight
import com.aimdi.xtanative.ui.post.PostGeometry
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/** Where a saved post's note line puts its pieces. */
class SavedNoteLookTest {
    @Test
    fun theNotesPencilAndTheAddButtonsGlyphLineUpWithThePostsText() {
        assertEquals(PostGeometry.COLUMN_START, SavedNoteLook.ROW_START + SavedNoteLook.PADDING)
        assertEquals(PostGeometry.COLUMN_START, SavedNoteLook.ROW_START + SavedNoteLook.BUTTON_START + SavedNoteLook.TEXT_BUTTON_PADDING)
    }

    @Test
    fun theDesktopsLitBlockLeavesAMarginRoundThePencil() {
        assertTrue(SavedNoteLook.PADDING - RowHighlight.SIDE_INSET >= 8f)
    }
}
