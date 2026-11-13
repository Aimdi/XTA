package com.aimdi.xtanative.ui.post

/**
 * Where a post in a timeline puts its pieces, in dp (tweet_ink_layout.dart,
 * thread_rail.dart, docs/specs/feed-ink.md): the 40 dp avatar 16 dp in and
 * 12 dp down, in a column of its own; the author line, text, media and
 * footer in a second column from 68 dp; translate and ⋯ as 48 dp targets at
 * the end of the author line, the last one reaching the screen's edge; a
 * thread's rail through the avatars' centres.
 */
object PostGeometry {
    private const val COS_45 = 0.70710677f

    const val AVATAR_START = 16f
    const val AVATAR_TOP = 12f
    const val AVATAR_SIZE = 40f
    const val AVATAR_GAP = 12f
    const val COLUMN_START = AVATAR_START + AVATAR_SIZE + AVATAR_GAP
    const val COLUMN_TOP = 12f
    const val END_PADDING = 16f
    const val TEXT_GAP = 2f
    const val TOUCH_TARGET = 48f

    /** How far down its target a header glyph sits, so it is level with the author line. */
    const val GLYPH_TOP = 3f
    const val RAIL_WIDTH = 2f

    /** How far the avatar's 48 dp target reaches past the avatar on each side. */
    const val AVATAR_TARGET_INSET = (TOUCH_TARGET - AVATAR_SIZE) / 2

    /** Where the avatar's target starts and its top, so the avatar centred in it sits [AVATAR_START] in and [AVATAR_TOP] down. */
    const val AVATAR_TARGET_START = AVATAR_START - AVATAR_TARGET_INSET
    const val AVATAR_TARGET_TOP = AVATAR_TOP - AVATAR_TARGET_INSET

    /** The rail's centre, through the avatars'. */
    const val RAIL_CENTER = AVATAR_START + AVATAR_SIZE / 2

    /** Where the end of a header's last target is, from the column's end: past the post's own padding, at the screen's edge. */
    const val TRAILING_BLEED = END_PADDING

    /** What the author line leaves at its end for [buttons] header targets, from the column's end. */
    fun authorLineEnd(buttons: Int): Float = if (buttons == 0) 0f else buttons * TOUCH_TARGET - TRAILING_BLEED

    /** Where the right edge of the last header target is on a screen [width] wide. */
    fun trailingRight(width: Float): Float = width - END_PADDING + TRAILING_BLEED

    /** The follow badge's diameter on an avatar [avatar] wide, ring included (avatar_follow_badge.dart). */
    fun followBadgeSize(avatar: Float): Float = (avatar * 0.42f).coerceIn(14f, 20f)

    /** The page-coloured ring around a follow badge [badge] wide. */
    fun followBadgeRing(badge: Float): Float = if (badge >= 18f) 2f else 1.5f

    /** The badge's centre from the avatar's top-left, on both axes: on the rim, where it crosses the lower-right 45° diagonal. */
    fun followBadgeCenter(avatar: Float): Float = avatar / 2 * (1 + COS_45)
}
