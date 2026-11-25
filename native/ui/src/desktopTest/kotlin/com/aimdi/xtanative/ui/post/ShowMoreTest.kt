package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** A folding text's size animates only for the unfolding the reader asked for. */
class ShowMoreTest {
    @Test
    fun aFoldedTextWithShowMoreIsReadyToAnimateAndKeepsDoingSoWhileItUnfolds() {
        assertTrue(ShowMore.resizes(reduced = false, folds = true, clipped = true, unfolding = false))
        assertTrue(ShowMore.resizes(reduced = false, folds = true, clipped = false, unfolding = true))
    }

    @Test
    fun aTextThatNeverFoldsOrHasUnfoldedDoesNotCarryTheAnimation() {
        assertFalse(ShowMore.resizes(reduced = false, folds = false, clipped = true, unfolding = false))
        assertFalse(ShowMore.resizes(reduced = false, folds = true, clipped = false, unfolding = false))
    }

    @Test
    fun reducedMotionUnfoldsAtOnce() {
        assertFalse(ShowMore.resizes(reduced = true, folds = true, clipped = true, unfolding = false))
        assertFalse(ShowMore.resizes(reduced = true, folds = true, clipped = false, unfolding = true))
    }
}
