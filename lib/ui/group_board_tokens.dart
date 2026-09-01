import 'package:flutter/material.dart';
import 'package:xta/ui/contrast.dart' as contrast;
import 'package:xta/ui/x_look_theme.dart';

/// Surface tokens for the group board.
///
/// Under an X-look preset these come straight from [XLookTokens]. Under every
/// other theme (default light/dark, true black, Fairy Forest, Pitch Black) the
/// same X *structure* — flat surfaces, hairline borders, a tinted accent — is
/// rendered in that theme's own palette, so the board never imports a foreign
/// blue into a cream or green theme.
///
/// [XLookTokens] is deliberately only *read*, never registered: elsewhere in
/// the app its presence is the signal that an X-look preset is active.
@immutable
class GroupBoardTokens {
  /// Page background.
  final Color background;

  /// Tile fill, a small step off [background] so tiles read without shadows.
  final Color tile;

  /// 1px tile border and list hairlines.
  final Color border;

  /// Primary (title) text.
  final Color onSurface;

  /// Metadata text: member counts, hints.
  final Color secondary;

  /// Accent used for selection and the fallback-avatar palette harmonisation.
  final Color accent;

  const GroupBoardTokens({
    required this.background,
    required this.tile,
    required this.border,
    required this.onSurface,
    required this.secondary,
    required this.accent,
  });

  static GroupBoardTokens resolve(BuildContext context) {
    final xLook = XLookTokens.maybeOf(context);
    if (xLook != null) {
      return GroupBoardTokens(
        background: xLook.background,
        // Lights Out uses pure black for both background and card; lift the
        // tile slightly so its border is not the only thing defining it.
        tile: xLook.card == xLook.background ? _lift(xLook.background, xLook.onBackground) : xLook.card,
        border: xLook.border,
        onSurface: xLook.onBackground,
        secondary: xLook.secondary,
        accent: xLook.accent,
      );
    }

    final scheme = Theme.of(context).colorScheme;
    return GroupBoardTokens(
      background: scheme.surface,
      tile: _lift(scheme.surface, scheme.onSurface),
      border: scheme.outlineVariant,
      onSurface: scheme.onSurface,
      secondary: scheme.onSurfaceVariant,
      accent: scheme.primary,
    );
  }

  /// Nudges [surface] a few percent towards [toward] — the flat-design
  /// equivalent of elevation. On pure black this yields X's #16181C-like step.
  static Color _lift(Color surface, Color toward) => Color.alphaBlend(toward.withValues(alpha: 0.055), surface);

  /// X's own secondary grey (#71767B) only clears 4.5:1 against pure black; on
  /// a lifted tile — and more so under a group-colour wash — it drops to ~4.2:1,
  /// so metadata colours are corrected against the background they land on.
  /// Kept here as the board's entry points; the maths lives in `ui/contrast.dart`
  /// because the tweet chrome needs the same correction.
  static double contrastRatio(Color a, Color b) => contrast.contrastRatio(a, b);

  static Color ensureContrast(Color foreground, Color background, {double minRatio = 4.5}) =>
      contrast.ensureContrast(foreground, background, minRatio: minRatio);
}
