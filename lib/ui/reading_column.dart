import 'dart:math' as math;

import 'package:flutter/rendering.dart';
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
    return _WidthBuilder(builder: (context, width) => builder(context, readingColumnPadding(width, base)));
  }
}

/// A [LayoutBuilder] that builds again when its width changes, and not its height: the column's padding
/// depends on the width alone, and the home header sliding away changes the feed's height on every frame of
/// the slide, which built every post in the list again each time.
class _WidthBuilder extends AbstractLayoutBuilder<double> {
  @override
  final Widget Function(BuildContext context, double width) builder;

  const _WidthBuilder({required this.builder});

  @override
  RenderAbstractLayoutBuilderMixin<double, RenderBox> createRenderObject(BuildContext context) => _RenderWidthBuilder();
}

class _RenderWidthBuilder extends RenderBox
    with
        RenderObjectWithChildMixin<RenderBox>,
        RenderObjectWithLayoutCallbackMixin,
        RenderAbstractLayoutBuilderMixin<double, RenderBox> {
  @override
  double get layoutInfo => constraints.maxWidth;

  @override
  void performLayout() {
    runLayoutCallback();
    final child = this.child;
    if (child == null) {
      size = constraints.biggest;
      return;
    }
    child.layout(constraints, parentUsesSize: true);
    size = constraints.constrain(child.size);
  }

  @override
  bool hitTestChildren(BoxHitTestResult result, {required Offset position}) =>
      child?.hitTest(result, position: position) ?? false;

  @override
  void paint(PaintingContext context, Offset offset) {
    if (child case final child?) context.paintChild(child, offset);
  }
}
