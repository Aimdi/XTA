import 'package:flutter/material.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/x_look_theme.dart';

/// How much a picked-up row grows while it is dragged.
const double kReorderLiftScale = 1.03;
const double kReorderLiftElevation = 8;
const double kReorderLiftRadius = 12;

/// The surface a lifted row takes: the one menus and sheets float on, which
/// stands clear of a true black page too.
Color reorderLiftSurface(BuildContext context) {
  final tokens = XLookTokens.maybeOf(context);
  return tokens == null ? Theme.of(context).colorScheme.surfaceContainerHigh : xLookFloatingSurface(tokens);
}

/// A dragged row lifting as X's do: it grows a little, rises on a shadow and
/// takes the raised surface colour and rounded corners, and dropping it
/// settles it back. With reduced motion it rises without growing.
///
/// For [ReorderableListView.proxyDecorator] and
/// [SliverReorderableList.proxyDecorator]. It also gives the row the
/// [Material] a [ListTile] needs, which a sliver list's drag overlay lacks.
Widget xtaReorderLift(Widget child, int index, Animation<double> animation) => AnimatedBuilder(
  animation: animation,
  builder: (context, child) {
    final t = Curves.easeOutCubic.transform(animation.value);
    final grow = xtaReduceMotion(context) ? 0.0 : kReorderLiftScale - 1;
    return Transform.scale(
      scale: 1 + grow * t,
      child: Material(
        elevation: kReorderLiftElevation * t,
        color: Color.lerp(Colors.transparent, reorderLiftSurface(context), t),
        borderRadius: BorderRadius.circular(kReorderLiftRadius * t),
        clipBehavior: Clip.antiAlias,
        child: child,
      ),
    );
  },
  child: child,
);
