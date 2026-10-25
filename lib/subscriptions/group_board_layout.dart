import 'dart:math' as math;

/// The widest a group tile grows before the board adds a column.
const double kGroupTileMaxWidth = 320;

/// Tile height on a board that gained columns for a wide window: what the
/// cover, title and count take, as on a phone, instead of a mostly empty square.
const double kGroupTileWideHeight = 156;

/// Columns for the groups board: the reader's choice of two or three on a
/// phone, and as many more as a wide window needs to keep each tile within
/// [kGroupTileMaxWidth]. Two fixed columns on a 1080p desktop made every tile
/// about 900 px wide, with the avatars and title lost in one corner.
int groupBoardColumns({required int chosen, required double width}) =>
    math.max(chosen.clamp(2, 3), (width / kGroupTileMaxWidth).ceil());

/// Width over height of a tile: two columns are landscape, more are square.
double groupTileBaseRatio(int columns) => columns == 2 ? 168 / 132 : 1.0;

/// How much taller large text makes a tile, so the title and count never
/// squeeze the avatar mosaic out of it.
double groupTileTextGrowth(double textScale) => 1 + (textScale.clamp(1.0, 2.0) - 1) * 0.55;

/// A fixed tile height when the board was widened past the reader's choice,
/// or null to keep the phone's aspect ratio.
double? groupTileExtent({required int chosen, required int columns, required double textScale}) =>
    columns > chosen.clamp(2, 3) ? kGroupTileWideHeight * groupTileTextGrowth(textScale) : null;

/// Edge of the member mosaic on a Kompakt tile.
const double kCompactGroupCoverExtent = 40;

/// Height of a Kompakt tile at normal text size: the mosaic beside two lines.
const double kCompactGroupTileHeight = 60;

/// The name and count lines of a Kompakt tile at 1x (16 and 13 sp, with room
/// for a tall line height), and its padding and border above and below them.
const double _compactTextHeight = 44;
const double _compactChrome = 14;

/// A Kompakt tile's height at [textScale]: its two lines set it once they
/// outgrow the mosaic.
double compactGroupTileExtent(double textScale) =>
    math.max(kCompactGroupTileHeight, _compactTextHeight * textScale.clamp(1.0, 3.0) + _compactChrome);
