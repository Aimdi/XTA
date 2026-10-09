import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/intro/intro_illustrations.dart';
import 'package:xta/intro/intro_page_frame.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/x_look_theme.dart';

/// One placeholder post: avatar circle, two bars, optional trailing glyph.
class IntroSkeletonRow extends StatelessWidget {
  final double avatar;
  final Color color;
  final Widget? trailing;

  const IntroSkeletonRow({super.key, required this.avatar, required this.color, this.trailing});

  @override
  Widget build(BuildContext context) {
    final bar = avatar / 2;
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Container(
          width: avatar,
          height: avatar,
          decoration: BoxDecoration(color: color, shape: BoxShape.circle),
        ),
        SizedBox(width: bar),
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              _bar(0.7, bar),
              SizedBox(height: bar / 2),
              _bar(0.45, bar),
            ],
          ),
        ),
        if (trailing != null) ...[SizedBox(width: bar), trailing!],
      ],
    );
  }

  Widget _bar(double share, double height) => FractionallySizedBox(
    widthFactor: share,
    alignment: AlignmentDirectional.centerStart,
    child: Container(
      height: height,
      decoration: BoxDecoration(color: color, borderRadius: BorderRadius.circular(height / 2)),
    ),
  );
}

/// A phone outline holding placeholder posts, as many as fit its screen.
/// [trailing] glyphs sit on the matching rows; [badge] puts the accent check
/// on the bottom corner.
class IntroPhone extends StatelessWidget {
  final double width;
  final double height;
  final List<Widget?> trailing;
  final bool badge;

  const IntroPhone({
    super.key,
    required this.width,
    required this.height,
    this.trailing = const [],
    this.badge = false,
  });

  /// The avatar size, and the module every other measure follows.
  double get _unit => width * 0.135;

  int get rows => rowsFor(width, height);

  /// Rows that fit between the notch and the bottom edge of a phone this
  /// size: a row is its two bars (1.25 units) plus a one-unit gap.
  static int rowsFor(double width, double height) {
    final unit = width * 0.135;
    // Padding, notch and gap, the border, and a little slack for rounding.
    final screen = height - unit * 3.45 - 4;
    return ((screen + unit) / (2.25 * unit)).floor().clamp(2, 5);
  }

  @override
  Widget build(BuildContext context) {
    final tokens = introTokens(context);
    final surface = xLookFloatingSurface(tokens);
    final bone = introBoneColor(tokens.onBackground, surface);
    final unit = _unit;
    final phone = Container(
      width: width,
      height: height,
      padding: EdgeInsets.fromLTRB(unit * 0.8, unit * 1.4, unit * 0.8, unit * 0.8),
      clipBehavior: Clip.antiAlias,
      decoration: BoxDecoration(
        color: surface,
        borderRadius: BorderRadius.circular(width * 0.2),
        border: Border.all(color: introHairline(tokens, surface)),
      ),
      child: _screen(tokens, bone, unit),
    );
    if (!badge) return phone;
    return Stack(
      clipBehavior: Clip.none,
      children: [
        phone,
        PositionedDirectional(
          bottom: -kTweetSpace2,
          end: -kTweetSpace2,
          child: IntroCheckBadge(size: unit * 2.5, color: tokens.accent),
        ),
      ],
    );
  }

  Widget _screen(XLookTokens tokens, Color bone, double unit) => Column(
    crossAxisAlignment: CrossAxisAlignment.center,
    children: [
      Container(
        width: width * 0.3,
        height: 2,
        decoration: BoxDecoration(color: tokens.secondary, borderRadius: BorderRadius.circular(1)),
      ),
      SizedBox(height: unit),
      for (var i = 0; i < rows; i++) ...[
        if (i > 0) SizedBox(height: unit),
        IntroSkeletonRow(avatar: unit, color: bone, trailing: i < trailing.length ? trailing[i] : null),
      ],
    ],
  );
}

/// Scales its child 1 → 1.15 → 1 once when it appears; static under reduced
/// motion.
class IntroPulseOnce extends StatefulWidget {
  final Widget child;

  const IntroPulseOnce({super.key, required this.child});

  @override
  State<IntroPulseOnce> createState() => _IntroPulseOnceState();
}

class _IntroPulseOnceState extends State<IntroPulseOnce> with SingleTickerProviderStateMixin {
  late final AnimationController _controller = AnimationController(vsync: this, duration: kXtaMotionStandard * 2);
  late final Animation<double> _scale = TweenSequence<double>([
    TweenSequenceItem(tween: Tween(begin: 1, end: 1.15), weight: 1),
    TweenSequenceItem(tween: Tween(begin: 1.15, end: 1), weight: 1),
  ]).animate(CurvedAnimation(parent: _controller, curve: Curves.easeOutCubic));
  bool _started = false;

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    if (_started) return;
    _started = true;
    if (xtaReduceMotion(context)) {
      _controller.value = 1;
    } else {
      _controller.forward();
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScaleTransition(scale: _scale, child: widget.child);
  }
}

/// Stays on this device: a phone with its posts, a like, a bookmark and the
/// accent check. Nothing leaves it, and the empty space says so.
class IntroPhoneIllustration extends StatelessWidget {
  const IntroPhoneIllustration({super.key});

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    return IntroIllustration(
      label: L10n.of(context).intro_illustration_phone,
      child: LayoutBuilder(
        builder: (context, constraints) {
          final box = constraints.biggest;
          final height = box.height * 0.82;
          final width = math.min(box.width * 0.44, height / 1.75);
          final rows = IntroPhone.rowsFor(width, height);
          // No taller than the row it sits on, or a small phone overflows.
          final glyph = math.min(16.0, width * 0.135 * 1.25);
          return Center(
            child: IntroPhone(
              width: width,
              height: height,
              trailing: [
                IntroPulseOnce(
                  child: Icon(Icons.favorite, size: glyph, color: primary),
                ),
                for (var i = 2; i < rows; i++) null,
                Icon(Icons.bookmark, size: glyph, color: primary),
              ],
              badge: true,
            ),
          );
        },
      ),
    );
  }
}
