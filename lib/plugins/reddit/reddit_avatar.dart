/// Pixel-art avatars for Reddit accounts.
///
/// Reddit gives a scraped page no avatar, and a row of identical grey circles
/// tells a reader nothing. These are derived from the name instead: the same
/// account always gets the same face and colour, so a thread becomes something
/// you can follow by shape rather than by re-reading every username.
///
/// The pieces are pure functions over a hash so the choosing can be tested
/// without painting anything.
library;

import 'dart:math' as math;

import 'package:flutter/material.dart';

/// Backgrounds. Saturated and distinct from each other, and each one legible
/// against both the light and lights-out themes.
const redditAvatarBackgrounds = <Color>[
  Color(0xFFB25400), // burnt orange
  Color(0xFFE84462), // crimson
  Color(0xFFE9E9E9), // bone
  Color(0xFFFF2D20), // red
  Color(0xFF1D9BF0), // blue
  Color(0xFF00BA7C), // green
  Color(0xFF7856FF), // violet
  Color(0xFFFFB300), // amber
  Color(0xFF00838F), // teal
  Color(0xFFD81B60), // magenta
  Color(0xFF5D4037), // umber
  Color(0xFF37474F), // slate
];

/// Accents for the smaller features, kept bright so they read at 24px.
const redditAvatarAccents = <Color>[
  Color(0xFF00E5D0),
  Color(0xFFFFD400),
  Color(0xFFFF4081),
  Color(0xFF448AFF),
  Color(0xFF69F0AE),
  Color(0xFFFF6E40),
];

/// Faces on an 8×8 grid.
///
/// `.` is the background, `#` the main colour, `o` the accent. Hand-drawn
/// rather than generated: random pixels read as noise, while a small set of
/// deliberate faces reads as a character even at avatar size.
const redditAvatarFaces = <List<String>>[
  [
    '........',
    '.##..##.',
    '.#o..o#.',
    '........',
    '..####..',
    '.#....#.',
    '..####..',
    '........',
  ],
  [
    '........',
    '..#..#..',
    '.o#..#o.',
    '........',
    '.######.',
    '.#.##.#.',
    '.######.',
    '........',
  ],
  [
    '..####..',
    '.######.',
    '.#o..o#.',
    '.######.',
    '.#.##.#.',
    '.######.',
    '..#..#..',
    '........',
  ],
  [
    '........',
    '.#....#.',
    '..o..o..',
    '........',
    '...##...',
    '..####..',
    '........',
    '........',
  ],
  [
    '.##..##.',
    '.##..##.',
    '........',
    '..o..o..',
    '........',
    '.######.',
    '.#....#.',
    '........',
  ],
  [
    '........',
    '.######.',
    '.#o..o#.',
    '.#....#.',
    '.#.##.#.',
    '.######.',
    '..####..',
    '........',
  ],
  [
    '...##...',
    '..####..',
    '.#o##o#.',
    '.######.',
    '..#..#..',
    '.##..##.',
    '........',
    '........',
  ],
  [
    '........',
    '.o#..#o.',
    '.######.',
    '.#....#.',
    '.#....#.',
    '.######.',
    '.o#..#o.',
    '........',
  ],
  [
    '........',
    '..#..#..',
    '..#..#..',
    '........',
    '.o####o.',
    '..####..',
    '...##...',
    '........',
  ],
  [
    '..#..#..',
    '.######.',
    '.#o..o#.',
    '.######.',
    '.#####.#',
    '..####..',
    '.#....#.',
    '........',
  ],
  [
    '........',
    '.##..##.',
    '.#o..o#.',
    '.##..##.',
    '........',
    '..o..o..',
    '.######.',
    '........',
  ],
  [
    '...##...',
    '..#..#..',
    '.#o..o#.',
    '.#....#.',
    '.##..##.',
    '..####..',
    '...##...',
    '........',
  ],
];

/// A stable hash of the name. Deliberately not `hashCode`, which Dart is free
/// to change between runs — an avatar that moved on restart would be worse
/// than none.
int redditAvatarSeed(String name) {
  var hash = 0x811c9dc5;
  for (final unit in name.toLowerCase().codeUnits) {
    hash ^= unit;
    hash = (hash * 0x01000193) & 0x7fffffff;
  }
  return hash;
}

/// Which face, background and accent a name gets.
///
/// Each is taken from a different part of the hash so two names that share a
/// face rarely share a colour as well.
({List<String> face, Color background, Color accent}) redditAvatarFor(String name) {
  final seed = redditAvatarSeed(name);

  return (
    face: redditAvatarFaces[seed % redditAvatarFaces.length],
    background: redditAvatarBackgrounds[(seed ~/ 7) % redditAvatarBackgrounds.length],
    accent: redditAvatarAccents[(seed ~/ 101) % redditAvatarAccents.length],
  );
}

/// Black on a light background, white on a dark one, so the face is always the
/// thing you see first.
Color redditAvatarInk(Color background) =>
    background.computeLuminance() > 0.55 ? const Color(0xFF15202B) : Colors.white;

/// The corner of every Reddit avatar: a square with rounded edges rather than a
/// circle, so a square logo is not cropped into a disc.
BorderRadius redditAvatarBorder(double size) => BorderRadius.circular(size / 4);

class RedditAvatar extends StatelessWidget {
  final String? name;
  final double size;

  /// A disc rather than the rounded square: how a thread marks the person who
  /// wrote the post.
  final bool round;

  const RedditAvatar({super.key, required this.name, this.size = 28, this.round = false});

  BorderRadius get _border => round ? BorderRadius.circular(size / 2) : redditAvatarBorder(size);

  @override
  Widget build(BuildContext context) {
    // A deleted account has no name and no identity to represent; give it a
    // neutral tile rather than inventing a face for it.
    final value = name;
    if (value == null || value.isEmpty) {
      return Container(
        width: size,
        height: size,
        decoration: BoxDecoration(
          color: Theme.of(context).colorScheme.surfaceContainerHighest,
          borderRadius: _border,
        ),
      );
    }

    final avatar = redditAvatarFor(value);

    return ClipRRect(
      borderRadius: _border,
      child: SizedBox(
        width: size,
        height: size,
        child: CustomPaint(
          painter: _RedditAvatarPainter(
            face: avatar.face,
            background: avatar.background,
            ink: redditAvatarInk(avatar.background),
            accent: avatar.accent,
          ),
        ),
      ),
    );
  }
}

class _RedditAvatarPainter extends CustomPainter {
  final List<String> face;
  final Color background;
  final Color ink;
  final Color accent;

  _RedditAvatarPainter({
    required this.face,
    required this.background,
    required this.ink,
    required this.accent,
  });

  @override
  void paint(Canvas canvas, Size size) {
    canvas.drawRect(Offset.zero & size, Paint()..color = background);

    final rows = face.length;
    final columns = face.first.length;
    final cell = math.min(size.width / columns, size.height / rows);
    // Centred, so a non-square box does not push the face into a corner.
    final left = (size.width - cell * columns) / 2;
    final top = (size.height - cell * rows) / 2;

    final inkPaint = Paint()..color = ink;
    final accentPaint = Paint()..color = accent;

    for (var y = 0; y < rows; y++) {
      final row = face[y];
      for (var x = 0; x < row.length; x++) {
        final paint = switch (row[x]) {
          '#' => inkPaint,
          'o' => accentPaint,
          _ => null,
        };
        if (paint == null) {
          continue;
        }
        // A hair of overlap: exact edges leave seams between cells when the
        // cell size lands on a fraction of a pixel.
        canvas.drawRect(
          Rect.fromLTWH(left + x * cell, top + y * cell, cell + 0.5, cell + 0.5),
          paint,
        );
      }
    }
  }

  @override
  bool shouldRepaint(covariant _RedditAvatarPainter old) =>
      old.face != face || old.background != background || old.accent != accent;
}
