import 'package:flutter/material.dart';
import 'package:intl/intl.dart' show NumberFormat;

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

  /// A tentative tag, e.g. one with too few votes: drawn with a dashed outline
  /// and no fill, at full text contrast.
  final bool weak;

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
    this.weak = false,
    this.semanticsLabel,
    this.longPressHint,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final palette = pluginTagPalette(kind, theme.colorScheme);
    final radius = BorderRadius.circular(10);
    final chip = ActionChip(
      avatar: marker == null ? null : Icon(marker, size: 16, color: palette.text),
      label: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: label,
              style: TextStyle(color: palette.text, fontWeight: weak ? FontWeight.w400 : FontWeight.w600),
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
        // A chip clips its label to one line; a long tag and its translation
        // get a second line, then an ellipsis.
        softWrap: true,
        maxLines: 2,
        overflow: TextOverflow.ellipsis,
      ),
      backgroundColor: weak ? Colors.transparent : palette.fill,
      side: BorderSide(color: weak ? palette.text : palette.border),
      shape: weak ? _DashedChipBorder(borderRadius: radius) : RoundedRectangleBorder(borderRadius: radius),
      // Compact by padding, not density: a denser chip shrinks its tap target too.
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 6),
      visualDensity: VisualDensity.standard,
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

/// A rounded outline drawn in dashes, the way sites mark a tag as unsettled.
class _DashedChipBorder extends RoundedRectangleBorder {
  static const _dash = 4.0;
  static const _gap = 3.0;

  const _DashedChipBorder({super.side, super.borderRadius});

  @override
  _DashedChipBorder copyWith({BorderSide? side, BorderRadiusGeometry? borderRadius}) =>
      _DashedChipBorder(side: side ?? this.side, borderRadius: borderRadius ?? this.borderRadius);

  @override
  _DashedChipBorder scale(double t) => _DashedChipBorder(side: side.scale(t), borderRadius: borderRadius * t);

  @override
  void paint(Canvas canvas, Rect rect, {TextDirection? textDirection}) {
    if (side.style == BorderStyle.none || side.width <= 0) return;
    final outline = borderRadius.resolve(textDirection).toRRect(rect).deflate(side.width / 2);
    final paint = Paint()
      ..color = side.color
      ..style = PaintingStyle.stroke
      ..strokeWidth = side.width;
    for (final metric in (Path()..addRRect(outline)).computeMetrics()) {
      for (var start = 0.0; start < metric.length; start += _dash + _gap) {
        canvas.drawPath(metric.extractPath(start, start + _dash), paint);
      }
    }
  }
}
