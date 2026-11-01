import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/tweet_skeleton.dart';

/// Digits that line up down a column of prices.
const List<FontFeature> kStockFigures = [FontFeature.tabularFigures()];

/// One quote line: the name over `$SPX · Index` on the left, the price over
/// its move on the right, the day's line between them while the market
/// trades. The move is coloured text, no pill, and grey with the price while
/// the market is closed.
///
/// What the right side shows follows [StockRowState]: grey bars while the row
/// loads, the close and its day while the market is closed, and a retry button
/// beside the reason when there is no data.
class StocksQuoteRow extends StatelessWidget {
  /// The name, or the symbol when no name is known.
  final String title;

  /// `$SPX · Index`, or a token's address cut in the middle.
  final String? subtitle;

  /// A token's network, as a chip beside [title].
  final String? network;
  final TickerQuote? quote;

  /// Whether the last lookup found nothing.
  final bool failed;

  /// Whether a live move says what it is measured over (`24 h`, `1T`), and a
  /// closed one the day of its close. Märkte's sections say it once instead.
  final bool showWindow;
  final bool showCloseDay;
  final VoidCallback onTap;
  final VoidCallback? onRetry;

  /// Two readings of the symbol to choose from, shown under the row.
  final Widget? choices;

  /// A caution in amber in place of the second line, as "Two matches, choose
  /// the source" over [choices].
  final String? warning;

  const StocksQuoteRow({
    super.key,
    required this.title,
    required this.quote,
    required this.onTap,
    this.subtitle,
    this.network,
    this.failed = false,
    this.showWindow = true,
    this.showCloseDay = true,
    this.onRetry,
    this.choices,
    this.warning,
  });

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final state = stockRowState(quote, failed: failed, now: now);
    final retry = state == StockRowState.noData ? onRetry : null;
    final tappable = _tappable(context, state, now);
    final row = retry == null
        ? tappable
        : LayoutBuilder(
            builder: (context, constraints) => Row(
              children: [
                Expanded(child: tappable),
                Padding(
                  padding: const EdgeInsetsDirectional.only(end: 8),
                  child: StocksRetryButton(
                    onPressed: retry,
                    compact: !StocksRetryButton.fits(context, constraints.maxWidth / 2),
                  ),
                ),
              ],
            ),
          );
    if (choices == null) return row;
    return Column(mainAxisSize: MainAxisSize.min, children: [row, choices!]);
  }

  Widget _tappable(BuildContext context, StockRowState state, DateTime now) {
    return Semantics(
      button: true,
      label: _label(context, state, now),
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 56),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            child: LayoutBuilder(builder: (context, constraints) => _row(context, state, now, constraints.maxWidth)),
          ),
        ),
      ),
    );
  }

  String _label(BuildContext context, StockRowState state, DateTime now) {
    final l10n = L10n.of(context);
    final quote = this.quote;
    final price = quote?.displayPrice;
    final percent = quote?.changePercent;
    final shown = state == StockRowState.live || state == StockRowState.closed;
    return [
      title,
      ?network,
      if (warning != null) warning! else if (state == StockRowState.noData) l10n.plugin_stocks_no_price_reason else ?subtitle,
      if (state == StockRowState.loading) l10n.plugin_stocks_loading_price,
      if (shown && price != null) stockPriceSpoken(price),
      if (state == StockRowState.closed) stockClosedTag(context, quote!, now: now),
      if (shown && percent != null) stockPercentLabel(percent),
      if (state == StockRowState.live && percent != null) stockWindowLabel(l10n, quote!.changeWindow),
    ].join(', ');
  }

  Widget _row(BuildContext context, StockRowState state, DateTime now, double width) {
    final points = quote?.points ?? const <TickerPoint>[];
    final roomy = width >= 280 && MediaQuery.textScalerOf(context).scale(14) <= 18;
    final sparkline = roomy && warning == null && state == StockRowState.live && points.length >= 2;
    return Row(
      children: [
        Expanded(child: _identity(context, state)),
        if (sparkline) ...[
          const SizedBox(width: 12),
          SizedBox(width: 64, height: 24, child: StocksSparkline(quote: quote!)),
        ],
        if (state != StockRowState.noData) ...[
          const SizedBox(width: 12),
          ConstrainedBox(
            constraints: BoxConstraints(maxWidth: width * 0.45),
            child: state == StockRowState.loading
                ? const StocksFiguresSkeleton()
                : _Figures(
                    quote: quote!,
                    closed: state == StockRowState.closed,
                    caption: state == StockRowState.closed ? showCloseDay : showWindow,
                    now: now,
                  ),
          ),
        ],
      ],
    );
  }

  Widget _identity(BuildContext context, StockRowState state) {
    final theme = Theme.of(context);
    final second = warning ?? (state == StockRowState.noData ? L10n.of(context).plugin_stocks_no_price_reason : subtitle);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.center,
      mainAxisSize: MainAxisSize.min,
      children: [
        Row(
          children: [
            // A token's symbol and network share the line; at large text each
            // gives way, the network first.
            Flexible(
              flex: 3,
              child: Text(
                title,
                maxLines: 1,
                overflow: TextOverflow.ellipsis,
                style: theme.textTheme.bodyLarge!.copyWith(fontSize: 16, fontWeight: FontWeight.w500, height: 1.3),
              ),
            ),
            if (network != null) ...[
              const SizedBox(width: 8),
              Flexible(flex: 2, child: StocksNetworkChip(label: network!)),
            ],
          ],
        ),
        if (second != null) ...[
          const SizedBox(height: 2),
          Text(
            second,
            // A reason or a caution is read whole; a subtitle is a label.
            maxLines: second == subtitle ? 1 : 2,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodySmall!.copyWith(
              fontSize: 13,
              color: warning != null ? stockWarningColour(context) : theme.colorScheme.onSurfaceVariant,
            ),
          ),
        ],
      ],
    );
  }
}

/// The price over its move, and beside the move what it is measured over or,
/// for a closed market, the day of the close. Grey while the market is closed.
class _Figures extends StatelessWidget {
  final TickerQuote quote;
  final bool closed;
  final bool caption;
  final DateTime now;

  const _Figures({required this.quote, required this.closed, required this.caption, required this.now});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final percent = quote.changePercent;
    final note = !caption
        ? null
        : closed
        ? stockClosedTag(context, quote, now: now)
        : percent == null
        ? null
        : stockWindowLabel(L10n.of(context), quote.changeWindow);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      mainAxisSize: MainAxisSize.min,
      children: [
        FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(
            stockPrice(quote.displayPrice!),
            maxLines: 1,
            style: theme.textTheme.bodyLarge!.copyWith(
              fontSize: 15,
              fontWeight: closed ? FontWeight.w400 : FontWeight.w500,
              fontFeatures: kStockFigures,
              color: closed ? muted : theme.colorScheme.onSurface,
            ),
          ),
        ),
        if (note != null || percent != null) ...[
          const SizedBox(height: 2),
          Wrap(
            alignment: WrapAlignment.end,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 6,
            children: [
              if (note != null)
                Text(note, maxLines: 1, style: theme.textTheme.labelSmall!.copyWith(fontSize: 11, color: muted)),
              if (percent != null)
                Text(
                  stockPercentLabel(percent),
                  maxLines: 1,
                  style: theme.textTheme.labelMedium!.copyWith(
                    fontSize: 13,
                    fontWeight: FontWeight.w500,
                    fontFeatures: kStockFigures,
                    color: closed ? muted : stockTrendColour(context, percent),
                  ),
                ),
            ],
          ),
        ],
      ],
    );
  }
}

/// A token's network beside its symbol: `DOT [Base]`.
class StocksNetworkChip extends StatelessWidget {
  final String label;

  const StocksNetworkChip({super.key, required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(4),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 1),
        child: Text(
          label,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.labelSmall!.copyWith(
            fontSize: 12,
            fontWeight: FontWeight.w500,
            color: theme.colorScheme.onSurface.withValues(alpha: 0.8),
          ),
        ),
      ),
    );
  }
}

/// "↻ Erneut versuchen" beside a row that has no data, or the arrow alone,
/// with the words as its tooltip, where they would crowd the row out.
class StocksRetryButton extends StatelessWidget {
  final VoidCallback onPressed;
  final bool compact;

  const StocksRetryButton({super.key, required this.onPressed, this.compact = false});

  /// Whether the words and the arrow fit in [width].
  static bool fits(BuildContext context, double width) =>
      stocksTextWidth(context, L10n.of(context).retry, Theme.of(context).textTheme.labelLarge!) + 18 + 8 + 24 <= width;

  @override
  Widget build(BuildContext context) {
    final label = L10n.of(context).retry;
    if (compact) {
      return IconButton(onPressed: onPressed, tooltip: label, icon: const Icon(Icons.refresh, size: 20));
    }
    return TextButton.icon(
      onPressed: onPressed,
      style: TextButton.styleFrom(minimumSize: const Size(48, 48), padding: const EdgeInsets.symmetric(horizontal: 12)),
      icon: const Icon(Icons.refresh, size: 18),
      label: Text(label),
    );
  }
}

/// The grey bars where a loading row's price and move will be: stacked for a
/// row, side by side for an entry of the strip ([inline]).
class StocksFiguresSkeleton extends StatefulWidget {
  final bool inline;

  const StocksFiguresSkeleton({super.key, this.inline = false});

  @override
  State<StocksFiguresSkeleton> createState() => _StocksFiguresSkeletonState();
}

class _StocksFiguresSkeletonState extends State<StocksFiguresSkeleton> with SingleTickerProviderStateMixin {
  late final AnimationController _pulse = skeletonPulseController(this);

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    applySkeletonPulse(context, _pulse);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final scale = MediaQuery.textScalerOf(context);
    return ExcludeSemantics(
      child: RepaintBoundary(
        child: AnimatedBuilder(
          animation: _pulse,
          builder: (context, _) {
            final color = skeletonBoneColor(context, _pulse.value);
            if (widget.inline) return SkeletonBone(width: 72, height: scale.scale(12), color: color);
            return Column(
              crossAxisAlignment: CrossAxisAlignment.end,
              mainAxisSize: MainAxisSize.min,
              children: [
                SkeletonBone(width: 76, height: scale.scale(14), color: color),
                const SizedBox(height: 6),
                SkeletonBone(width: 52, height: scale.scale(12) + 6, radius: 6, color: color),
              ],
            );
          },
        ),
      ),
    );
  }
}

/// The day's line at thumbnail size, with the previous close as a faint
/// baseline — above it is green, below it red, as on the row's chip.
class StocksSparkline extends StatelessWidget {
  final TickerQuote quote;

  const StocksSparkline({super.key, required this.quote});

  @override
  Widget build(BuildContext context) {
    return ExcludeSemantics(
      child: RepaintBoundary(
        child: CustomPaint(
          painter: _SparklinePainter(
            closes: [for (final point in quote.points) point.close],
            baseline: quote.previousClose,
            line: stockTrendColour(context, quote.changePercent),
            guide: Theme.of(context).colorScheme.outlineVariant,
          ),
        ),
      ),
    );
  }
}

class _SparklinePainter extends CustomPainter {
  final List<double> closes;
  final double? baseline;
  final Color line;
  final Color guide;

  _SparklinePainter({required this.closes, required this.baseline, required this.line, required this.guide});

  @override
  void paint(Canvas canvas, Size size) {
    final values = [...closes, ?baseline];
    final low = values.reduce((a, b) => a < b ? a : b);
    final high = values.reduce((a, b) => a > b ? a : b);
    final span = high - low == 0 ? 1.0 : high - low;
    double y(double value) => size.height - (value - low) / span * size.height;

    if (baseline != null) {
      final paint = Paint()
        ..color = guide
        ..strokeWidth = 1;
      for (var x = 0.0; x < size.width; x += 4) {
        canvas.drawLine(Offset(x, y(baseline!)), Offset(x + 2, y(baseline!)), paint);
      }
    }
    final path = Path();
    for (var i = 0; i < closes.length; i++) {
      final point = Offset(i / (closes.length - 1) * size.width, y(closes[i]));
      i == 0 ? path.moveTo(point.dx, point.dy) : path.lineTo(point.dx, point.dy);
    }
    canvas.drawPath(
      path,
      Paint()
        ..color = line
        ..style = PaintingStyle.stroke
        ..strokeWidth = 1.5
        ..strokeJoin = StrokeJoin.round,
    );
  }

  @override
  bool shouldRepaint(_SparklinePainter old) =>
      old.closes != closes || old.baseline != baseline || old.line != line || old.guide != guide;
}
