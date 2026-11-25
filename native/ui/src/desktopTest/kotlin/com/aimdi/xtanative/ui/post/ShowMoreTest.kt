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
    fun aFoldedLayoutSaysWhetherTheTextStillFoldsAndAnUnfoldedOneKeepsWhatWasKept() {
        assertTrue(ShowMore.folds(was = false, clipped = true, overflows = true))
        assertTrue(ShowMore.folds(was = true, clipped = true, overflows = true), "a returning screen keeps its Show more")
        assertFalse(ShowMore.folds(was = true, clipped = true, overflows = false), "a wider window or smaller text no longer folds")
        assertTrue(ShowMore.folds(was = true, clipped = false, overflows = false), "unfolded text says nothing")
        assertFalse(ShowMore.folds(was = false, clipped = false, overflows = false))
    }

    @Test
    fun reducedMotionUnfoldsAtOnce() {
        assertFalse(ShowMore.resizes(reduced = true, folds = true, clipped = true, unfolding = false))
        assertFalse(ShowMore.resizes(reduced = true, folds = true, clipped = false, unfolding = true))
    }
}
