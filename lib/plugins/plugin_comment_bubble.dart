import 'package:flutter/material.dart';
import 'package:xta/ui/contrast.dart';

/// How far each level of replies is indented, and how deep that goes.
///
/// Threads nest without limit; a phone cannot. Past this depth replies keep
/// their colour but stop moving right, so a deep argument stays readable
/// instead of collapsing into a column one word wide.
const double kCommentIndentPerLevel = 12;
const int kCommentMaxIndentDepth = 8;

const double kCommentBubbleRadius = 14;
const double kCommentBubbleGutter = 12;
const EdgeInsets kCommentBubblePadding = EdgeInsets.symmetric(horizontal: 12, vertical: 10);

/// One hue per reply level, cycling — X's own accent family, so the washes sit
/// with the rest of the app and stay apart from each other in every theme.
const commentBubbleHues = <Color>[
  Color(0xFF1D9BF0), // blue
  Color(0xFF7856FF), // violet
  Color(0xFF00BA7C), // green
  Color(0xFFFFAD1F), // amber
  Color(0xFFF91880), // pink
  Color(0xFFFF7A00), // orange
];

double commentIndent(int depth) => kCommentIndentPerLevel * depth.clamp(0, kCommentMaxIndentDepth);

Color commentHue(int depth) => commentBubbleHues[depth.abs() % commentBubbleHues.length];

/// What one bubble is painted with, every foreground already corrected against
/// the tint it sits on.
@immutable
class CommentBubbleColors {
  final Color fill;
  final Color border;
  final Color text;
  final Color muted;
  final Color accent;

  const CommentBubbleColors({
    required this.fill,
    required this.border,
    required this.text,
    required this.muted,
    required this.accent,
  });

  /// A low wash of the depth's hue over the page, opaque so contrast can be
  /// measured; [outlined] keeps only the hairline, for rows that are not a
  /// comment in their own right.
  factory CommentBubbleColors.of(ThemeData theme, int depth, {bool outlined = false}) {
    final dark = theme.brightness == Brightness.dark;
    final hue = commentHue(depth);
    final ground = theme.scaffoldBackgroundColor;
    final fill = outlined ? ground : Color.alphaBlend(hue.withValues(alpha: dark ? 0.17 : 0.09), ground);
    final scheme = theme.colorScheme;
    return CommentBubbleColors(
      fill: fill,
      border: Color.alphaBlend(hue.withValues(alpha: dark ? 0.42 : 0.28), ground),
      text: ensureContrast(scheme.onSurface, fill),
      muted: ensureContrast(scheme.onSurfaceVariant, fill),
      accent: ensureContrast(scheme.primary, fill),
    );
  }
}

/// One comment — header and body — in its own rounded, depth-tinted bubble.
///
/// The bubble is the tap target, so its ripple follows the rounded shape and a
/// folded comment is never smaller than a finger.
class CommentBubble extends StatelessWidget {
  final int depth;
  final VoidCallback? onTap;

  /// Hairline only: held-back replies, deleted comments.
  final bool outlined;

  /// Hug the content instead of spanning the row, for pill-shaped links.
  final bool shrinkWrap;

  final Widget Function(BuildContext context, CommentBubbleColors colors) builder;

  const CommentBubble({
    super.key,
    required this.depth,
    required this.builder,
    this.onTap,
    this.outlined = false,
    this.shrinkWrap = false,
  });

  @override
  Widget build(BuildContext context) {
    final colors = CommentBubbleColors.of(Theme.of(context), depth, outlined: outlined);
    final bubble = Material(
      color: colors.fill,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(kCommentBubbleRadius),
        side: BorderSide(color: colors.border),
      ),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: onTap,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
          child: Padding(
            padding: kCommentBubblePadding,
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              widthFactor: shrinkWrap ? 1 : null,
              child: DefaultTextStyle.merge(
                style: TextStyle(color: colors.text),
                child: builder(context, colors),
              ),
            ),
          ),
        ),
      ),
    );

    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(kCommentBubbleGutter + commentIndent(depth), 3, kCommentBubbleGutter, 3),
      child: shrinkWrap ? Align(alignment: AlignmentDirectional.centerStart, child: bubble) : bubble,
    );
  }
}
