package com.aimdi.xtanative.ui.post

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

/** feed_ink_test.dart and thread_rail_test.dart: where a post in a timeline puts its pieces. */
class PostGeometryTest {
    @Test
    fun aPostTakesTwoColumnsTheAvatarAt16AndEverythingElseAt68() {
        assertEquals(16f, PostGeometry.AVATAR_START)
        assertEquals(40f, PostGeometry.AVATAR_SIZE)
        assertEquals(68f, PostGeometry.COLUMN_START)
        // The avatar sits in a 48 dp target, 4 dp down from the target's top.
        assertEquals(12f, PostGeometry.AVATAR_TARGET_START)
        assertEquals(8f, PostGeometry.AVATAR_TARGET_TOP)
        assertEquals(PostGeometry.AVATAR_TARGET_TOP + 4f, PostGeometry.AVATAR_TOP)
    }

    @Test
    fun translateAndTheMenuAre48DpTargetsOnTheAuthorLineTheLastAtTheScreensEdge() {
        assertEquals(48f, PostGeometry.TOUCH_TARGET)
        assertEquals(390f, PostGeometry.trailingRight(390f))
        // The author line leaves room for each target, counted from the screen's edge.
        assertEquals(48f - 16f, PostGeometry.authorLineEnd(1))
        assertEquals(96f - 16f, PostGeometry.authorLineEnd(2))
        assertEquals(PostGeometry.COLUMN_TOP, PostGeometry.AVATAR_TOP)
    }

    @Test
    fun aSelfThreadsRailRunsThroughTheAvatarsCentres() {
        assertEquals(PostGeometry.AVATAR_START + PostGeometry.AVATAR_SIZE / 2, PostGeometry.RAIL_CENTER)
        assertEquals(2f, PostGeometry.RAIL_WIDTH)
    }
}

/** feed_ink_test.dart's author line (tweet_header.dart's TweetAuthorLine). */
class AuthorLineLayoutTest {
    @Test
    fun aFediverseHandlesInstanceIsFoundAndNoOtherHandleHasOne() {
        assertEquals(5, AuthorLineLayout.instanceAt("alice@example.social"))
        assertEquals(null, AuthorLineLayout.instanceAt("alice.bsky.social"))
        assertEquals(null, AuthorLineLayout.instanceAt("@alice"), "a handle written with its own @ has no instance")
    }

    @Test
    fun theNameHandleAndTimeShareOneLineWhenTheyFit() {
        val widths = AuthorLineLayout.widths(line = 300f, name = 90f, badge = 0f, handle = 80f, time = 30f)
        assertFalse(widths.twoLines)
        assertEquals(90f, widths.name)
        assertEquals(80f, widths.handle)
        assertEquals(30f, widths.time)
    }

    @Test
    fun theHandleGivesWayFirstThenTheNameAndTheTimeIsKept() {
        // A name that cannot fit: the handle goes, the name is cut, the time stays whole.
        val long = AuthorLineLayout.widths(line = 236f, name = 400f, badge = 17f, handle = 90f, time = 30f)
        assertEquals(0f, long.handle)
        assertEquals(30f, long.time)
        assertEquals(236f - 30f - 17f, long.name)
        // A handle with less than 40 dp left is left out rather than cut to an ellipsis alone.
        val tight = AuthorLineLayout.widths(line = 200f, name = 140f, badge = 0f, handle = 90f, time = 30f)
        assertEquals(0f, tight.handle)
        val cut = AuthorLineLayout.widths(line = 220f, name = 140f, badge = 0f, handle = 90f, time = 30f)
        assertEquals(50f, cut.handle)
    }

    @Test
    fun aLongTimeIsCutOnlyPastWhatTheNameLeavesAndNeverBelowItsShare() {
        val widths = AuthorLineLayout.widths(line = 200f, name = 190f, badge = 0f, handle = 50f, time = 150f)
        assertEquals(200f * AuthorLineLayout.TIME_SHARE, widths.time)
        assertEquals(200f - widths.time, widths.name)
    }

    @Test
    fun withLargeTextTheHandleAndTimeTakeASecondLine() {
        assertFalse(AuthorLineLayout.twoLines(1.29f))
        assertTrue(AuthorLineLayout.twoLines(1.3f))
        val widths = AuthorLineLayout.widths(line = 300f, name = 120f, badge = 17f, handle = 120f, time = 40f, fontScale = 1.3f)
        assertTrue(widths.twoLines)
        assertEquals(120f, widths.name)
        assertEquals(120f, widths.handle)
        assertEquals(40f, widths.time)
    }
}
