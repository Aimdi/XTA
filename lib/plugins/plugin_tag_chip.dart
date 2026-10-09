import 'package:flutter/material.dart';
import 'package:intl/intl.dart';

/// The kinds of tag image boards colour apart. A source without kinds leaves
/// its tags unkinded (null), which reads neutral.
enum PluginTagKind { artist, copyright, character, species, general, meta }

/// Text colour for a tag kind, after the usual booru colours, tuned to stay
/// readable on light, dim and black surfaces.
Color? pluginTagKindColor(PluginTagKind? kind, ColorScheme scheme) {
  final dark = scheme.brightness == Brightness.dark;
  return switch (kind) {
    PluginTagKind.artist => dark ? const Color(0xFFFF8A80) : const Color(0xFFC62828),
    PluginTagKind.copyright => dark ? const Color(0xFFD7A6F5) : const Color(0xFF7B1FA2),
    PluginTagKind.character => dark ? const Color(0xFF7ED68A) : const Color(0xFF2E7D32),
    PluginTagKind.species => dark ? const Color(0xFFFFB74D) : const Color(0xFFB34700),
    PluginTagKind.meta => dark ? const Color(0xFFFFD54F) : const Color(0xFF7A5C00),
    PluginTagKind.general => dark ? const Color(0xFF6CB8F5) : const Color(0xFF0B5FBF),
    null => null,
  };
}

typedef PluginTagPalette = ({Color text, Color fill, Color border});

/// A chip tinted with its kind's colour; neutral without a kind.
PluginTagPalette pluginTagPalette(PluginTagKind? kind, ColorScheme scheme) {
  final color = pluginTagKindColor(kind, scheme);
  if (color == null) {
    return (text: scheme.onSurface, fill: scheme.surfaceContainerHighest, border: scheme.outlineVariant);
  }
  final dark = scheme.brightness == Brightness.dark;
  return (
    text: color,
    fill: color.withValues(alpha: dark ? 0.16 : 0.09),
    border: color.withValues(alpha: dark ? 0.5 : 0.4),
  );
}

/// 5.45M, 36.3K, 50 — short enough to sit beside a tag.
String pluginCompactCount(int count, String locale) => NumberFormat.compact(locale: locale).format(count);

/// A compact tag chip: the name in its kind's colour, then a quieter [detail]
/// such as a post count or a translation. The visible chip stays small; the
/// touch target keeps its 48 dp.
class PluginTagChip extends StatelessWidget {
  final String label;
  final String? detail;
  final PluginTagKind? kind;

  /// A small icon before the name, e.g. followed or hidden.
  final IconData? marker;

  /// Read aloud instead of [label] and [detail], e.g. with the full count.
  final String? semanticsLabel;
  final String? longPressHint;
  final VoidCallback onPressed;
  final VoidCallback? onLongPress;

  const PluginTagChip({
    super.key,
    required this.label,
    required this.onPressed,
    this.detail,
    this.kind,
    this.marker,
    this.semanticsLabel,
    this.longPressHint,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final palette = pluginTagPalette(kind, theme.colorScheme);
    final chip = ActionChip(
      avatar: marker == null ? null : Icon(marker, size: 16, color: palette.text),
      label: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: label,
              style: TextStyle(color: palette.text, fontWeight: FontWeight.w600),
            ),
            if (detail != null)
              TextSpan(
                text: '  $detail',
                style: theme.textTheme.labelMedium?.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontFeatures: const [FontFeature.tabularFigures()],
                ),
              ),
          ],
        ),
        semanticsLabel: semanticsLabel,
      ),
      backgroundColor: palette.fill,
      side: BorderSide(color: palette.border),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
      visualDensity: VisualDensity.compact,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      onPressed: onPressed,
    );
    final longPress = onLongPress;
    if (longPress == null) return chip;
    return Semantics(
      onLongPressHint: longPressHint,
      child: GestureDetector(onLongPress: longPress, child: chip),
    );
  }
}
