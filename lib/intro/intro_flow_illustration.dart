import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/intro/intro_illustrations.dart';
import 'package:xta/intro/intro_page_frame.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/motion.dart';

/// Add an X account: posts flow from X into the phone, one direction only.
/// With accounts, the X circle becomes their avatar initials.
class IntroFlowIllustration extends StatelessWidget {
  final List<Account> accounts;

  const IntroFlowIllustration({super.key, this.accounts = const []});

  @override
  Widget build(BuildContext context) {
    return IntroIllustration(
      label: L10n.of(context).intro_illustration_flow,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final box = constraints.biggest;
          final chip = (box.shortestSide * 0.26).clamp(48.0, 72.0);
          final phoneWidth = box.width * 0.32;
          final phoneHeight = math.min(box.height, phoneWidth * 1.9);
          return Row(
            children: [
              accounts.isEmpty ? IntroSourceChip(plugin: coreXPlugin, size: chip) : _AvatarStack(accounts, chip),
              const Expanded(child: IntroFlowArrow()),
              IntroPhone(width: phoneWidth, height: phoneHeight),
            ],
          );
        },
      ),
    );
  }
}

/// Up to three overlapping initials, in the accent.
class _AvatarStack extends StatelessWidget {
  final List<Account> accounts;
  final double size;

  const _AvatarStack(this.accounts, this.size);

  @override
  Widget build(BuildContext context) {
    final shown = accounts.take(3).toList(growable: false);
    final overlap = size * 0.3;
    return SizedBox(
      width: size + (shown.length - 1) * (size - overlap),
      height: size,
      child: Stack(
        children: [
          for (var i = 0; i < shown.length; i++)
            PositionedDirectional(
              start: i * (size - overlap),
              child: IntroAccountAvatar(account: shown[i], size: size, ringed: shown.length > 1),
            ),
        ],
      ),
    );
  }
}

/// The first letter of the handle on an accent disc. [ringed] separates it
/// from a neighbour it overlaps.
class IntroAccountAvatar extends StatelessWidget {
  final Account account;
  final double size;
  final bool ringed;

  const IntroAccountAvatar({super.key, required this.account, this.size = 56, this.ringed = false});

  @override
  Widget build(BuildContext context) {
    final tokens = introTokens(context);
    final accent = tokens.accent;
    final name = account.screenName ?? '';
    return Container(
      width: size,
      height: size,
      alignment: Alignment.center,
      decoration: BoxDecoration(
        color: accent,
        shape: BoxShape.circle,
        border: ringed ? Border.all(color: tokens.background, width: 2) : null,
      ),
      child: Text(
        name.isEmpty ? '' : name.substring(0, 1).toUpperCase(),
        style: Theme.of(context).textTheme.titleMedium!.copyWith(color: contrastingForeground(accent)),
      ),
    );
  }
}

/// A stroke ending in a chevron, drawn in once from X towards the phone;
/// mirrored under RTL so it always points the same way.
class IntroFlowArrow extends StatelessWidget {
  const IntroFlowArrow({super.key});

  @override
  Widget build(BuildContext context) {
    final color = introTokens(context).onBackground.withValues(alpha: 0.5);
    final mirrored = Directionality.of(context) == TextDirection.rtl;
    return TweenAnimationBuilder<double>(
      tween: Tween(begin: 0, end: 1),
      duration: xtaMotionDuration(context, kXtaMotionNavigation),
      curve: Curves.easeOutCubic,
      builder: (context, progress, _) => CustomPaint(painter: _ArrowPainter(progress, color, mirrored)),
    );
  }
}

class _ArrowPainter extends CustomPainter {
  final double progress;
  final Color color;
  final bool mirrored;

  const _ArrowPainter(this.progress, this.color, this.mirrored);

  @override
  void paint(Canvas canvas, Size size) {
    if (progress <= 0) return;
    final y = size.height / 2;
    final inset = kTweetSpace3;
    final head = math.min(10.0, size.width / 4);
    final from = Offset(mirrored ? size.width - inset : inset, y);
    final to = Offset(mirrored ? inset : size.width - inset, y);
    final dir = mirrored ? -1.0 : 1.0;
    final path = Path()
      ..moveTo(from.dx, from.dy)
      ..lineTo(to.dx, to.dy)
      ..moveTo(to.dx - dir * head, y - head)
      ..lineTo(to.dx, to.dy)
      ..lineTo(to.dx - dir * head, y + head);
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 3
      ..strokeCap = StrokeCap.round
      ..strokeJoin = StrokeJoin.round;
    _drawInOrder(canvas, path, paint);
  }

  /// The line first, then the head: progress is spent along the whole length.
  void _drawInOrder(Canvas canvas, Path path, Paint paint) {
    final metrics = path.computeMetrics().toList(growable: false);
    final total = metrics.fold<double>(0, (sum, metric) => sum + metric.length);
    var remaining = total * progress;
    for (final metric in metrics) {
      if (remaining <= 0) return;
      canvas.drawPath(metric.extractPath(0, math.min(remaining, metric.length)), paint);
      remaining -= metric.length;
    }
  }

  @override
  bool shouldRepaint(covariant _ArrowPainter old) =>
      old.progress != progress || old.color != color || old.mirrored != mirrored;
}
