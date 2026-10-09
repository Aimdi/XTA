import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/intro/intro_illustrations.dart';
import 'package:xta/intro/intro_page_frame.dart';
import 'package:xta/settings/_theme.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Pick a look: the card is the control. Four mini phones painted with each
/// preset's real tokens, then the six accents. Every tap writes the pref and
/// the whole intro re-themes through the app's own listeners.
class IntroAppearancePage extends StatelessWidget {
  final int page;
  final VoidCallback onNext;
  final VoidCallback onSkip;

  const IntroAppearancePage({super.key, required this.page, required this.onNext, required this.onSkip});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return IntroPageFrame(
      page: page,
      title: l10n.intro_appearance_title,
      body: l10n.intro_appearance_body,
      onSkip: onSkip,
      card: const IntroLookPicker(),
      footer: IntroButtonRow.next(l10n, onNext),
    );
  }
}

/// The swatch quartet and the accent row, reading and writing the two X Look
/// prefs. Listens to the pref service so the selection follows a write even
/// when nothing above re-themes.
class IntroLookPicker extends StatelessWidget {
  const IntroLookPicker({super.key});

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);
    final background = prefs.get<String>(optionXLookBackground) ?? xLookBackgroundSystem;
    final accent = prefs.get<String>(optionXLookAccent) ?? xLookAccentBlue;
    return Semantics(
      container: true,
      label: L10n.of(context).intro_illustration_looks,
      explicitChildNodes: true,
      // The card paints its own fill, so the ink needs a surface of its own
      // above it or the ripples land underneath.
      child: Material(
        type: MaterialType.transparency,
        child: LayoutBuilder(
          builder: (context, constraints) => Center(
            child: SingleChildScrollView(
              physics: const ClampingScrollPhysics(),
              child: Column(
                mainAxisSize: MainAxisSize.min,
                children: [
                  _swatchRow(prefs, background, accent, introSwatchWidth(constraints)),
                  const SizedBox(height: kTweetSpace3),
                  for (final row in _accentRows(constraints.maxWidth))
                    Row(
                      mainAxisAlignment: MainAxisAlignment.center,
                      spacing: kTweetSpace2,
                      children: [
                        for (final name in row)
                          IntroAccentDot(
                            name: name,
                            color: xLookAccents[name]!,
                            selected: name == accent,
                            onTap: () => prefs.set(optionXLookAccent, name),
                          ),
                      ],
                    ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  Widget _swatchRow(BasePrefService prefs, String background, String accent, double width) => Row(
    mainAxisAlignment: MainAxisAlignment.center,
    spacing: kTweetSpace2,
    children: [
      for (final preset in xLookBackgrounds)
        IntroLookSwatch(
          preset: preset,
          accent: xLookAccentColor(accent),
          selected: preset == background,
          width: width,
          onTap: () => prefs.set(optionXLookBackground, preset),
        ),
    ],
  );
}

/// All six accents on one line when they fit, otherwise two even rows of
/// three rather than five and an orphan.
List<List<String>> _accentRows(double rowWidth) {
  final names = xLookAccents.keys.toList(growable: false);
  if (rowWidth >= introAccentRowWidth) return [names];
  final half = (names.length / 2).ceil();
  return [names.sublist(0, half), names.sublist(half)];
}

/// Six 48 dp targets and their gaps.
const double introAccentRowWidth = 6 * kTweetTouchTarget + 5 * kTweetSpace2;

/// What a swatch can be: 72 dp at most, narrower on a 320 dp phone, and
/// shorter when a scroll-mode card leaves little height above the accents.
double introSwatchWidth(BoxConstraints box) {
  final byWidth = (box.maxWidth - 3 * kTweetSpace2) / 4 - IntroLookSwatch.ring;
  final accentRows = box.maxWidth >= introAccentRowWidth ? 1 : 2;
  final accentHeight = accentRows * kTweetTouchTarget + kTweetSpace3;
  final byHeight = (box.maxHeight - accentHeight - IntroLookSwatch.ring) / IntroLookSwatch.aspect;
  return [
    IntroLookSwatch.maxWidth,
    byWidth,
    byHeight,
  ].reduce(math.min).clamp(IntroLookSwatch.minWidth, double.infinity);
}

/// The name of a background preset, as Settings calls it.
String introLookName(L10n l10n, String preset) => switch (preset) {
  xLookBackgroundLight => l10n.light,
  xLookBackgroundDim => l10n.theme_background_dim,
  xLookBackgroundLightsOut => l10n.theme_background_lights_out,
  _ => l10n.system,
};

/// A mini phone painted with the preset's own tokens; System is split
/// diagonally between Light and Lights Out, which is what it resolves to.
///
/// Unselected, a hairline that registers on the card; selected, the 2 dp
/// accent ring and a check. The padding gives way to the ring so the phone
/// never jumps.
class IntroLookSwatch extends StatelessWidget {
  static const double maxWidth = 72;
  static const double minWidth = 40;
  static const double aspect = 112 / 72;

  /// Border plus padding on both sides.
  static const double ring = 8;

  final String preset;
  final Color accent;
  final bool selected;
  final double width;
  final VoidCallback onTap;

  const IntroLookSwatch({
    super.key,
    required this.preset,
    required this.accent,
    required this.selected,
    required this.onTap,
    this.width = maxWidth,
  });

  @override
  Widget build(BuildContext context) {
    final primary = Theme.of(context).colorScheme.primary;
    final tokens = introTokens(context);
    final size = Size(width, width * aspect);
    final duration = xtaMotionDuration(context, kXtaMotionFast);
    final edge = selected ? 2.0 : 1.0;
    return Semantics(
      button: true,
      selected: selected,
      label: introLookName(L10n.of(context), preset),
      child: ExcludeSemantics(
        child: InkWell(
          key: ValueKey('intro-look-$preset'),
          onTap: onTap,
          borderRadius: BorderRadius.circular(14),
          child: AnimatedContainer(
            duration: duration,
            padding: EdgeInsets.all(ring / 2 - edge),
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(14),
              border: Border.all(
                color: selected ? primary : introHairline(tokens, introCardFill(context)),
                width: edge,
              ),
            ),
            child: Stack(
              clipBehavior: Clip.none,
              children: [
                CustomPaint(
                  size: size,
                  painter: IntroLookSwatchPainter(preset: preset, accent: accent),
                ),
                if (selected)
                  PositionedDirectional(end: -6, bottom: -6, child: IntroCheckBadge(size: 20, color: primary)),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class IntroLookSwatchPainter extends CustomPainter {
  final String preset;
  final Color accent;

  const IntroLookSwatchPainter({required this.preset, required this.accent});

  @override
  void paint(Canvas canvas, Size size) {
    final rect = Offset.zero & size;
    canvas.clipRRect(RRect.fromRectAndRadius(rect, const Radius.circular(12)));
    if (preset != xLookBackgroundSystem) {
      _paintPreset(canvas, size, xLookTokensFor(preset, xLookAccentBlue));
      return;
    }
    _paintPreset(canvas, size, XLookTokens.light);
    canvas
      ..save()
      ..clipPath(
        Path()
          ..moveTo(size.width, 0)
          ..lineTo(size.width, size.height)
          ..lineTo(0, size.height)
          ..close(),
      );
    _paintPreset(canvas, size, XLookTokens.lightsOut);
    canvas.restore();
  }

  void _paintPreset(Canvas canvas, Size size, XLookTokens tokens) {
    final pad = size.width * 0.14;
    final bone = Paint()..color = introBoneColor(tokens.onBackground, tokens.background);
    canvas.drawRect(Offset.zero & size, Paint()..color = tokens.background);
    canvas.drawRRect(
      RRect.fromRectAndRadius(Rect.fromLTWH(pad, pad, size.width - 2 * pad, 6), const Radius.circular(3)),
      Paint()..color = tokens.secondary,
    );
    for (var row = 0; row < 2; row++) {
      final top = pad + 16 + row * (size.height * 0.26);
      canvas.drawCircle(Offset(pad + 5, top + 5), 5, bone);
      _bar(canvas, Rect.fromLTWH(pad + 14, top + 1, (size.width - 2 * pad - 14) * 0.9, 4), bone);
      _bar(canvas, Rect.fromLTWH(pad + 14, top + 8, (size.width - 2 * pad - 14) * 0.55, 4), bone);
    }
    _bar(canvas, Rect.fromLTWH(pad, size.height - pad - 8, 20, 8), Paint()..color = accent);
  }

  void _bar(Canvas canvas, Rect rect, Paint paint) =>
      canvas.drawRRect(RRect.fromRectAndRadius(rect, Radius.circular(rect.height / 2)), paint);

  @override
  bool shouldRepaint(covariant IntroLookSwatchPainter old) => old.preset != preset || old.accent != accent;
}

/// A 32 dp colour disc inside a 48 dp target.
class IntroAccentDot extends StatelessWidget {
  final String name;
  final Color color;
  final bool selected;
  final VoidCallback onTap;

  const IntroAccentDot({
    super.key,
    required this.name,
    required this.color,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final tokens = introTokens(context);
    return Semantics(
      button: true,
      selected: selected,
      label: xLookAccentName(L10n.of(context), name),
      child: ExcludeSemantics(
        child: InkWell(
          key: ValueKey('intro-accent-$name'),
          onTap: onTap,
          customBorder: const CircleBorder(),
          child: SizedBox(
            width: kTweetTouchTarget,
            height: kTweetTouchTarget,
            child: Center(
              child: AnimatedContainer(
                duration: xtaMotionDuration(context, kXtaMotionFast),
                width: 32,
                height: 32,
                decoration: BoxDecoration(
                  color: color,
                  shape: BoxShape.circle,
                  border: selected ? Border.all(color: tokens.onBackground, width: 2) : null,
                ),
                child: selected ? Icon(Icons.check, size: 18, color: contrastingForeground(color)) : null,
              ),
            ),
          ),
        ),
      ),
    );
  }
}
