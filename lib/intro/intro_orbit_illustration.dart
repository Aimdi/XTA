import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/intro/intro_illustrations.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/x/x_plugin.dart' show pluginIdX;
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/xta_mark.dart';

/// Welcome: the brand mark with source chips sitting on its ellipse paths,
/// drifting a few degrees back and forth. Still under reduced motion.
class IntroOrbitIllustration extends StatefulWidget {
  const IntroOrbitIllustration({super.key});

  @override
  State<IntroOrbitIllustration> createState() => _IntroOrbitIllustrationState();
}

typedef _OrbitSlot = ({String pluginId, double angle, double t});

/// X sits where the two ellipses cross at the top (t solves tan θ = a / b on
/// the +45° ellipse); the others take the four tips, the farthest-apart
/// points the paths offer.
const List<_OrbitSlot> _orbitSlots = [
  (pluginId: pluginIdX, angle: math.pi / 4, t: 0.692),
  (pluginId: pluginIdBluesky, angle: -math.pi / 4, t: 0),
  (pluginId: pluginIdMastodon, angle: math.pi / 4, t: 0.5),
  (pluginId: pluginIdReddit, angle: math.pi / 4, t: 0),
  (pluginId: pluginIdSubstack, angle: -math.pi / 4, t: 0.5),
];

class _IntroOrbitIllustrationState extends State<IntroOrbitIllustration> with SingleTickerProviderStateMixin {
  static const double _chip = 44;
  static const double _driftTurns = 3 / 360;

  late final AnimationController _drift = AnimationController(vsync: this, duration: const Duration(seconds: 6));

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (xtaReduceMotion(context)) {
      _drift.stop();
      _drift.value = 0;
    } else if (!_drift.isAnimating) {
      _drift.repeat();
    }
  }

  @override
  void dispose() {
    _drift.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return IntroIllustration(
      label: L10n.of(context).intro_illustration_orbit,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final box = constraints.biggest;
          final mark = box.shortestSide * 0.62;
          return AnimatedBuilder(
            animation: _drift,
            builder: (context, _) => Stack(
              clipBehavior: Clip.none,
              children: [
                Center(child: XtaMark(size: mark)),
                for (final slot in _orbitSlots) _chipAt(slot, mark, box.center(Offset.zero)),
              ],
            ),
          );
        },
      ),
    );
  }

  Widget _chipAt(_OrbitSlot slot, double mark, Offset centre) {
    final t = slot.t + _driftTurns * math.sin(2 * math.pi * _drift.value);
    final point = centre + xtaMarkOrbitPoint(mark, slot.angle, t);
    return Positioned(
      left: point.dx - _chip / 2,
      top: point.dy - _chip / 2,
      child: IntroSourceChip(plugin: pluginById(slot.pluginId)!, size: _chip),
    );
  }
}
