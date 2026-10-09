import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/motion.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/ui/xta_mark.dart';

const int kIntroPageCount = 5;
const double kIntroGutter = kTweetSpace4;
const double kIntroContentWidth = 560;
const double kIntroTopRowHeight = kTweetTouchTarget;
const double kIntroLogoSize = 56;
const double kIntroCardMinHeight = 160;
const double kIntroCardMaxHeight = 440;
const double kIntroCardPadding = kTweetSpace6;
const double _kCardGap = 20;

/// How much of the height left over goes above the logo; the rest stays
/// under the card, so the group floats a little above the optical centre.
const double _kSurplusAbove = 0.4;

/// The intro only ever runs under the X Look theme; the fallback keeps a bare
/// test harness from asserting.
XLookTokens introTokens(BuildContext context) =>
    XLookTokens.maybeOf(context) ??
    (Theme.of(context).brightness == Brightness.dark ? XLookTokens.lightsOut : XLookTokens.light);

/// Secondary text corrected for the page background: raw `secondary` on
/// Lights Out sits just under the body-text contrast floor.
Color introMutedColor(BuildContext context) {
  final tokens = introTokens(context);
  return ensureContrast(tokens.secondary, tokens.background);
}

TextStyle introHeadlineStyle(BuildContext context) => Theme.of(context).textTheme.headlineMedium!.copyWith(
  fontWeight: FontWeight.w700,
  height: 1.15,
  letterSpacing: -0.56,
  color: introTokens(context).onBackground,
);

TextStyle introBodyStyle(BuildContext context) =>
    Theme.of(context).textTheme.bodyLarge!.copyWith(color: introMutedColor(context));

TextStyle introFootnoteStyle(BuildContext context) =>
    Theme.of(context).textTheme.bodySmall!.copyWith(color: introMutedColor(context));

/// Lays [text] out the way a [Text] of [style] would at [maxWidth], at the
/// current text scale, so the frame can budget the card before building.
Size introTextSize(BuildContext context, String text, TextStyle style, {double maxWidth = double.infinity}) {
  final painter = TextPainter(
    text: TextSpan(text: text, style: style),
    textDirection: Directionality.of(context),
    textScaler: MediaQuery.textScalerOf(context),
  )..layout(maxWidth: maxWidth);
  final size = painter.size;
  painter.dispose();
  return size;
}

/// The tallest card a page this wide gets: a wider phone earns a taller card,
/// so the illustration keeps its share of the screen.
double introCardMaxHeight(double width) => math.max(kIntroCardMinHeight, math.min(kIntroCardMaxHeight, width * 1.15));

/// One card's chrome: Skip row, logo, headline, body, the illustration card,
/// then a pinned footer with the page indicator and the buttons.
///
/// The card takes whatever height is left between [kIntroCardMinHeight] and
/// [introCardMaxHeight]; when even the floor does not fit, the page scrolls
/// behind a hairline over the footer, which stays put so the primary action
/// never leaves the screen. With [cardFitsContent] the card is as tall as its
/// child instead, so a grid that outgrows the screen scrolls with the page
/// rather than inside a card that hides its last rows.
class IntroPageFrame extends StatefulWidget {
  final int page;
  final String title;
  final String body;
  final String? footnote;
  final Widget card;
  final Widget footer;
  final VoidCallback? onSkip;
  final bool cardFitsContent;

  const IntroPageFrame({
    super.key,
    required this.page,
    required this.title,
    required this.body,
    required this.card,
    required this.footer,
    this.footnote,
    this.onSkip,
    this.cardFitsContent = false,
  });

  @override
  State<IntroPageFrame> createState() => _IntroPageFrameState();
}

class _IntroPageFrameState extends State<IntroPageFrame> {
  /// Whether the body is taller than the screen, which is what the footer's
  /// hairline reports; the scroll view's own metrics are the source.
  final ValueNotifier<bool> _overflows = ValueNotifier(false);

  @override
  void dispose() {
    _overflows.dispose();
    super.dispose();
  }

  bool _onMetrics(ScrollMetricsNotification notification) {
    _overflows.value = notification.metrics.maxScrollExtent > 0;
    return false;
  }

  @override
  Widget build(BuildContext context) {
    return XtaSystemBars(
      child: Scaffold(
        backgroundColor: introTokens(context).background,
        body: SafeArea(
          bottom: false,
          child: NotificationListener<ScrollMetricsNotification>(
            onNotification: _onMetrics,
            child: _centred(LayoutBuilder(builder: _buildBody)),
          ),
        ),
        bottomNavigationBar: SafeArea(top: false, child: _footer(context)),
      ),
    );
  }

  /// Centred and capped in width only: a `Center` would claim every
  /// pixel of height, and in the footer slot that is the whole screen.
  Widget _centred(Widget child) => Align(
    alignment: Alignment.topCenter,
    heightFactor: 1,
    child: ConstrainedBox(
      constraints: const BoxConstraints(maxWidth: kIntroContentWidth),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: kIntroGutter),
        child: child,
      ),
    ),
  );

  /// The footer with, when the page scrolls, a hairline across its top edge
  /// saying so.
  Widget _footer(BuildContext context) {
    final tokens = introTokens(context);
    return ValueListenableBuilder<bool>(
      valueListenable: _overflows,
      builder: (context, overflows, child) => DecoratedBox(
        decoration: BoxDecoration(
          border: Border(
            top: BorderSide(
              color: overflows ? ensureContrast(tokens.border, tokens.background, minRatio: 1.5) : Colors.transparent,
              width: kTweetDividerThickness,
            ),
          ),
        ),
        child: child,
      ),
      child: _centred(
        Padding(
          padding: const EdgeInsets.only(top: kTweetSpace2, bottom: kIntroGutter),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              IntroPageIndicator(current: widget.page, total: kIntroPageCount),
              const SizedBox(height: kTweetSpace4),
              widget.footer,
            ],
          ),
        ),
      ),
    );
  }

  Widget _buildBody(BuildContext context, BoxConstraints constraints) {
    final available = constraints.maxHeight - _fixedHeight(context, constraints.maxWidth);
    final cardHeight = available.clamp(kIntroCardMinHeight, introCardMaxHeight(constraints.maxWidth));
    final surplus = widget.cardFitsContent ? 0.0 : math.max(0.0, available - cardHeight);
    return SingleChildScrollView(
      physics: const ClampingScrollPhysics(),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _topRow(context),
          SizedBox(height: kTweetSpace2 + surplus * _kSurplusAbove),
          const Center(child: XtaMark(size: kIntroLogoSize)),
          const SizedBox(height: kTweetSpace6),
          ..._heading(context),
          const SizedBox(height: _kCardGap),
          if (widget.cardFitsContent)
            ConstrainedBox(
              constraints: const BoxConstraints(minHeight: kIntroCardMinHeight),
              child: IntroCard(child: widget.card),
            )
          else
            SizedBox(
              height: cardHeight,
              child: IntroCard(child: widget.card),
            ),
          const SizedBox(height: kTweetSpace4),
        ],
      ),
    );
  }

  List<Widget> _heading(BuildContext context) => [
    Semantics(
      header: true,
      child: Text(widget.title, textAlign: TextAlign.center, style: introHeadlineStyle(context)),
    ),
    const SizedBox(height: kTweetSpace3),
    Text(widget.body, textAlign: TextAlign.center, style: introBodyStyle(context)),
    if (widget.footnote != null) ...[
      const SizedBox(height: kTweetSpace2),
      Text(widget.footnote!, textAlign: TextAlign.center, style: introFootnoteStyle(context)),
    ],
  ];

  /// Everything in the body except the card, measured for [width].
  double _fixedHeight(BuildContext context, double width) {
    double textHeight(String? text, TextStyle style, double gap) =>
        text == null ? 0 : gap + introTextSize(context, text, style, maxWidth: width).height;
    return kIntroTopRowHeight +
        kTweetSpace2 +
        kIntroLogoSize +
        textHeight(widget.title, introHeadlineStyle(context), kTweetSpace6) +
        textHeight(widget.body, introBodyStyle(context), kTweetSpace3) +
        textHeight(widget.footnote, introFootnoteStyle(context), kTweetSpace2) +
        _kCardGap +
        kTweetSpace4;
  }

  Widget _topRow(BuildContext context) {
    final onSkip = widget.onSkip;
    return SizedBox(
      height: kIntroTopRowHeight,
      child: onSkip == null
          ? null
          : Align(
              alignment: AlignmentDirectional.centerEnd,
              child: TextButton(
                key: const ValueKey('intro-skip'),
                onPressed: onSkip,
                style: TextButton.styleFrom(
                  minimumSize: const Size(kTweetTouchTarget, kTweetTouchTarget),
                  padding: const EdgeInsets.symmetric(horizontal: kTweetSpace2),
                ),
                child: Text(L10n.of(context).skip),
              ),
            ),
    );
  }
}

/// The illustration surface: lifted fill, hairline, house radius, no shadow.
class IntroCard extends StatelessWidget {
  final Widget child;

  const IntroCard({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return DecoratedBox(
      decoration: quoteCardDecoration(context),
      child: Padding(padding: const EdgeInsets.all(kIntroCardPadding), child: child),
    );
  }
}

/// The card's own fill, for anything drawn directly on it.
Color introCardFill(BuildContext context) => quoteCardDecoration(context).color ?? introTokens(context).background;

/// Five dots, the current one a pill. Announced as "Page n of 5" on change.
class IntroPageIndicator extends StatelessWidget {
  final int current;
  final int total;

  const IntroPageIndicator({super.key, required this.current, required this.total});

  @override
  Widget build(BuildContext context) {
    final active = Theme.of(context).colorScheme.primary;
    final inactive = introTokens(context).secondary;
    final duration = xtaMotionDuration(context, kXtaMotionStandard);
    return Semantics(
      liveRegion: true,
      label: L10n.of(context).intro_page_of(current + 1, total),
      child: ExcludeSemantics(
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            for (var i = 0; i < total; i++)
              Padding(
                padding: EdgeInsetsDirectional.only(start: i == 0 ? 0 : kTweetSpace2),
                child: AnimatedContainer(
                  duration: duration,
                  curve: Curves.easeOutCubic,
                  width: i == current ? 24 : 8,
                  height: 8,
                  decoration: BoxDecoration(
                    color: i == current ? active : inactive,
                    borderRadius: BorderRadius.circular(4),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}

/// A labelled button; null [onPressed] disables it.
class IntroAction {
  final String label;
  final VoidCallback? onPressed;
  final Key? key;

  const IntroAction(this.label, this.onPressed, {this.key});
}

/// One full-width primary, or outlined secondary beside filled primary. The
/// pair stacks when either label would not fit its half at the current text
/// scale, so a button never clips its label.
class IntroButtonRow extends StatelessWidget {
  final IntroAction primary;
  final IntroAction? secondary;

  const IntroButtonRow({super.key, required this.primary, this.secondary});

  /// The plain "Next" footer most pages end with.
  IntroButtonRow.next(L10n l10n, VoidCallback onNext, {super.key})
    : primary = IntroAction(l10n.next, onNext, key: const ValueKey('intro-next')),
      secondary = null;

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final secondary = this.secondary;
        final filled = _filled(primary);
        if (secondary == null) return filled;
        final outlined = _outlined(secondary);
        if (introLabelsFitSideBySide(context, constraints.maxWidth, [primary.label, secondary.label])) {
          return Row(
            children: [
              Expanded(child: outlined),
              const SizedBox(width: kTweetSpace3),
              Expanded(child: filled),
            ],
          );
        }
        return Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            filled,
            const SizedBox(height: kTweetSpace2),
            outlined,
          ],
        );
      },
    );
  }

  static const _padding = EdgeInsets.symmetric(horizontal: kTweetSpace4, vertical: kTweetSpace2);

  Widget _filled(IntroAction action) => FilledButton(
    key: action.key,
    onPressed: action.onPressed,
    style: FilledButton.styleFrom(minimumSize: const Size(0, kTweetTouchTarget), padding: _padding),
    child: Text(action.label, textAlign: TextAlign.center),
  );

  Widget _outlined(IntroAction action) => OutlinedButton(
    key: action.key,
    onPressed: action.onPressed,
    style: OutlinedButton.styleFrom(minimumSize: const Size(0, kTweetTouchTarget), padding: _padding),
    child: Text(action.label, textAlign: TextAlign.center),
  );
}

/// Whether every label fits one half of [width] on a single line.
bool introLabelsFitSideBySide(BuildContext context, double width, List<String> labels) {
  final style = Theme.of(context).textTheme.labelLarge!.copyWith(fontWeight: FontWeight.w700);
  final slot = (width - kTweetSpace3) / 2 - 2 * kTweetSpace4;
  return labels.every((label) => introTextSize(context, label, style).width <= slot);
}
