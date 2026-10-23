import 'dart:math' as math;

import 'package:flutter/widgets.dart';

/// The widest a list of posts grows. Wider than this the posts stay in a
/// centred column: on a 1080p desktop a post and its images used to span all
/// 1,860 px, so a single tall image took the whole screen.
const double kReadingColumnMaxWidth = 720;

/// [base] plus the side margins that centre a column of at most
/// [kReadingColumnMaxWidth] in [width]. A phone, or a narrow deck column, gets
/// [base] unchanged.
EdgeInsets readingColumnPadding(double width, EdgeInsets base) {
  if (!width.isFinite) return base;
  final inset = math.max(0.0, (width - kReadingColumnMaxWidth) / 2);
  return base.copyWith(left: base.left + inset, right: base.right + inset);
}

/// Hands [builder] the list padding that keeps posts in a reading column for
/// the width this list is actually given, so the scrollbar and the wheel still
/// work across the whole window.
class ReadingColumn extends StatelessWidget {
  final EdgeInsetsGeometry padding;
  final Widget Function(BuildContext context, EdgeInsets padding) builder;

  const ReadingColumn({super.key, this.padding = EdgeInsets.zero, required this.builder});

  @override
  Widget build(BuildContext context) {
    final base = padding.resolve(Directionality.of(context));
    return LayoutBuilder(
      builder: (context, constraints) => builder(context, readingColumnPadding(constraints.maxWidth, base)),
    );
  }
}
