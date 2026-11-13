package com.aimdi.xtanative.core.session

import com.aimdi.xtanative.core.backup.FollowGroup
import kotlin.math.ceil
import kotlin.math.max

/** A group on the board, and how deep it is nested (to indent it). */
data class BoardEntry(val group: FollowGroup, val depth: Int)

/** The board's two sections: NSFW groups sink below the rest. */
data class BoardSections(val safe: List<BoardEntry>, val nsfw: List<BoardEntry>)

/**
 * The groups board as data, as XTA lays it out: groups in their order with each
 * nested one under its parent, NSFW ones in a section of their own, and how
 * many columns and how tall the tiles are for the window.
 */
object GroupBoard {
    /** The widest a tile grows before the board adds a column. */
    const val TILE_MAX_WIDTH = 320f

    /** A tile's height once a wide window added columns: what the cover, title and count take. */
    const val WIDE_TILE_HEIGHT = 156f

    /** Edge of the member mosaic on a compact tile. */
    const val COMPACT_COVER = 40f

    /** A compact tile at normal text size: the mosaic beside two lines. */
    const val COMPACT_TILE_HEIGHT = 60f

    /** A compact tile's two lines at 1x and the padding and border around them. */
    private const val COMPACT_TEXT_HEIGHT = 44f
    private const val COMPACT_CHROME = 14f

    /** [groups] in board order, each nested group under its parent, split into the safe and the NSFW section. */
    fun sections(groups: List<FollowGroup>, nesting: Map<String, String>): BoardSections {
        val byId = groups.associateBy { it.id }
        val entries = GroupTree.treeOrder(FollowGroup.ordered(groups).map { it.id }, nesting)
            .map { BoardEntry(byId.getValue(it), GroupTree.depthOf(it, nesting)) }
        val (nsfw, safe) = entries.partition { it.group.nsfw }
        return BoardSections(safe, nsfw)
    }

    /**
     * Columns: the reader's two or three on a phone, and as many more as a
     * wide window needs to keep every tile within [TILE_MAX_WIDTH].
     */
    fun columns(chosen: Int, width: Float): Int = max(chosen.coerceIn(2, 3), ceil(width / TILE_MAX_WIDTH).toInt())

    /** Width over height of a tile: two columns are landscape, more are square. */
    fun tileRatio(columns: Int): Float = if (columns == 2) 168f / 132f else 1f

    /** How much taller large text makes a tile, so the title never squeezes the mosaic out. */
    fun textGrowth(textScale: Float): Float = 1 + (textScale.coerceIn(1f, 2f) - 1) * 0.55f

    /** A fixed tile height when the board was widened past the reader's choice; null keeps the phone's ratio. */
    fun tileHeight(chosen: Int, columns: Int, textScale: Float): Float? =
        if (columns > chosen.coerceIn(2, 3)) WIDE_TILE_HEIGHT * textGrowth(textScale) else null

    /** A compact tile's height: its two lines set it once they outgrow the mosaic. */
    fun compactTileHeight(textScale: Float): Float =
        max(COMPACT_TILE_HEIGHT, COMPACT_TEXT_HEIGHT * textScale.coerceIn(1f, 3f) + COMPACT_CHROME)
}
