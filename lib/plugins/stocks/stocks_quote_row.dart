import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/contrast.dart';

/// Digits that line up down a column of prices.
const List<FontFeature> kStockFigures = [FontFeature.tabularFigures()];

/// One dense watchlist line, the shape Apple Stocks, Robinhood and Yahoo
/// Finance agree on: symbol over badge and name on the left, the day's
/// sparkline, then the price over a signed green/red move on the right.
///
/// The right side says which [StockRowState] the row is in: a shimmer while
/// it loads, the close with its day and a muted move while the market is
/// closed, and one line that asks again when there is no data.
class StocksQuoteRow extends StatelessWidget {
  final String title;
  final String? subtitle;

  /// What the instrument is ("Index", "Crypto · Base"), before the name.
  final String? badge;
  final TickerQuote? quote;

  /// Whether the last lookup found nothing.
  final bool failed;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  /// Asks again for a row with no data.
  final VoidCallback? onRetry;

  const StocksQuoteRow({
    super.key,
    required this.title,
    required this.quote,
    required this.onTap,
    this.subtitle,
    this.badge,
    this.failed = false,
    this.onLongPress,
    this.onRetry,
  });

  @override
  Widget build(BuildContext context) {
    final now = DateTime.now();
    final state = stockRowState(quote, failed: failed, now: now);
    return LayoutBuilder(
      builder: (context, constraints) {
        final width = constraints.maxWidth;
        if (state != StockRowState.noData) return _tappable(context, state, now, width);
        return Row(
          children: [
            Expanded(child: _tappable(context, state, now, width)),
            ConstrainedBox(
              constraints: BoxConstraints(maxWidth: width * 0.55),
              child: StocksNoDataLine(onRetry: onRetry),
            ),
          ],
        );
      },
    );
  }

  /// What opens the row's page: all of it, but a no-data row's retry line.
  Widget _tappable(BuildContext context, StockRowState state, DateTime now, double width) {
    final noData = state == StockRowState.noData;
    return Semantics(
      button: true,
      label: _label(context, state, now),
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 64),
          child: Padding(
            padding: EdgeInsetsDirectional.fromSTEB(16, 10, noData ? 8 : 16, 10),
            child: noData ? _identity(context) : _row(context, state, now, width - 32),
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
      ?badge,
      ?subtitle,
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
    return Row(
      children: [
        Expanded(child: _identity(context)),
        if (roomy) ...[
          const SizedBox(width: 12),
          SizedBox(width: 56, height: 28, child: points.length < 2 ? null : StocksSparkline(quote: quote!)),
        ],
        const SizedBox(width: 12),
        ConstrainedBox(
          constraints: BoxConstraints(maxWidth: width * 0.45),
          child: state == StockRowState.loading
              ? const StocksFiguresSkeleton()
              : _Figures(quote: quote!, closed: state == StockRowState.closed, now: now),
        ),
      ],
    );
  }

  Widget _identity(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      mainAxisAlignment: MainAxisAlignment.center,
      mainAxisSize: MainAxisSize.min,
      children: [
        Text(
          title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w800),
        ),
        if (badge != null || subtitle != null)
          Text.rich(
            TextSpan(
              children: [
                if (badge != null) ...[
                  WidgetSpan(
                    alignment: PlaceholderAlignment.middle,
                    child: StocksKindBadge(label: badge!),
                  ),
                  const WidgetSpan(child: SizedBox(width: 6)),
                ],
                if (subtitle != null) TextSpan(text: subtitle),
              ],
            ),
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
      ],
    );
  }
}

/// The price over its move, with what the move is measured over beside it,
/// or for a closed market the day of the close and a muted move.
class _Figures extends StatelessWidget {
  final TickerQuote quote;
  final bool closed;
  final DateTime now;

  const _Figures({required this.quote, required this.closed, required this.now});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final percent = quote.changePercent;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      mainAxisSize: MainAxisSize.min,
      children: [
        FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(
            stockPrice(quote.displayPrice!),
            maxLines: 1,
            style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700, fontFeatures: kStockFigures),
          ),
        ),
        if (closed || percent != null) ...[
          const SizedBox(height: 4),
          Wrap(
            alignment: WrapAlignment.end,
            crossAxisAlignment: WrapCrossAlignment.center,
            spacing: 6,
            runSpacing: 2,
            children: [
              Text(
                closed
                    ? stockClosedTag(context, quote, now: now)
                    : stockWindowLabel(L10n.of(context), quote.changeWindow),
                maxLines: 1,
                style: theme.textTheme.labelSmall!.copyWith(
                  color: theme.colorScheme.onSurfaceVariant,
                  fontWeight: FontWeight.w600,
                ),
              ),
              if (percent != null) StocksChangeChip(percent: percent, muted: closed),
            ],
          ),
        ],
      ],
    );
  }
}

/// "No data · Tap to retry" where a row's figures would be; without
/// [onRetry] only "No data".
class StocksNoDataLine extends StatelessWidget {
  final VoidCallback? onRetry;

  const StocksNoDataLine({super.key, this.onRetry});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final line = ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 64),
      child: Padding(
        padding: const EdgeInsetsDirectional.fromSTEB(8, 10, 16, 10),
        child: Align(
          alignment: AlignmentDirectional.centerEnd,
          widthFactor: 1,
          child: Text(
            onRetry == null ? l10n.plugin_stocks_no_data : l10n.plugin_stocks_no_data_retry,
            textAlign: TextAlign.end,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.labelMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
        ),
      ),
    );
    if (onRetry == null) return line;
    return Semantics(
      button: true,
      child: InkWell(onTap: onRetry, child: line),
    );
  }
}

/// What an instrument is, as a small outlined label, so an index and a token
/// spelled alike are told apart at a glance. Neutral: colour on a row means
/// a price went up or down.
class StocksKindBadge extends StatelessWidget {
  final String label;

  const StocksKindBadge({super.key, required this.label});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 4),
      decoration: BoxDecoration(
        border: Border.all(color: theme.colorScheme.outlineVariant),
        borderRadius: BorderRadius.circular(4),
      ),
      child: Text(
        label,
        maxLines: 1,
        style: theme.textTheme.labelSmall!.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
          fontWeight: FontWeight.w600,
        ),
      ),
    );
  }
}

/// The shimmer where a loading row's price and move will be: stacked for a
/// row, side by side for a chip ([inline]).
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
            final price = Padding(
              padding: const EdgeInsets.symmetric(vertical: 2),
              child: SkeletonBone(width: 56, height: scale.scale(16), color: color),
            );
            final move = SkeletonBone(width: 56, height: scale.scale(16) + 6, radius: 6, color: color);
            return widget.inline
                ? Row(mainAxisSize: MainAxisSize.min, children: [price, const SizedBox(width: 6), move])
                : Column(
                    crossAxisAlignment: CrossAxisAlignment.end,
                    mainAxisSize: MainAxisSize.min,
                    children: [price, const SizedBox(height: 4), move],
                  );
          },
        ),
      ),
    );
  }
}

/// The move as a tinted pill: signed, so it reads without colour too. A
/// [muted] pill, for a closed market's move, keeps the colour of its figure
/// on a plain fill.
class StocksChangeChip extends StatelessWidget {
  final double? percent;
  final bool muted;

  const StocksChangeChip({super.key, required this.percent, this.muted = false});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final surface = theme.scaffoldBackgroundColor;
    final tint = stockTrendColour(context, percent);
    final fill = percent == null || muted
        ? theme.colorScheme.surfaceContainerHighest
        : Color.alphaBlend(tint.withValues(alpha: 0.14), surface);

    return Container(
      constraints: const BoxConstraints(minWidth: 68),
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(color: fill, borderRadius: BorderRadius.circular(6)),
      alignment: Alignment.centerRight,
      child: FittedBox(
        fit: BoxFit.scaleDown,
        child: Text(
          percent == null ? kStockPlaceholder : stockPercentLabel(percent!),
          maxLines: 1,
          style: theme.textTheme.labelMedium!.copyWith(
            fontWeight: FontWeight.w800,
            fontFeatures: kStockFigures,
            color: ensureContrast(tint, fill),
          ),
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
