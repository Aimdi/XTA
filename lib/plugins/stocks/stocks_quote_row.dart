import 'package:flutter/material.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/ui/contrast.dart';

/// Digits that line up down a column of prices.
const List<FontFeature> kStockFigures = [FontFeature.tabularFigures()];

/// One dense watchlist line, the shape Apple Stocks, Robinhood and Yahoo
/// Finance agree on: symbol over name on the left, the day's sparkline, then
/// the price over a signed green/red move on the right.
class StocksQuoteRow extends StatelessWidget {
  final String title;
  final String? subtitle;
  final TickerQuote? quote;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  const StocksQuoteRow({
    super.key,
    required this.title,
    required this.quote,
    required this.onTap,
    this.subtitle,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final price = quote?.displayPrice;
    final percent = quote?.changePercent;
    final label = [
      title,
      ?subtitle,
      price == null ? kStockPlaceholder : stockPrice(price),
      if (percent != null) stockPercentLabel(percent),
    ].join(', ');

    return Semantics(
      button: true,
      label: label,
      excludeSemantics: true,
      child: InkWell(
        onTap: onTap,
        onLongPress: onLongPress,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 64),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 10),
            child: LayoutBuilder(builder: (context, constraints) => _row(context, constraints.maxWidth)),
          ),
        ),
      ),
    );
  }

  Widget _row(BuildContext context, double width) {
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
          child: _figures(context),
        ),
      ],
    );
  }

  Widget _identity(BuildContext context) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          title,
          maxLines: 1,
          overflow: TextOverflow.ellipsis,
          style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w800),
        ),
        if (subtitle != null)
          Text(
            subtitle!,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
          ),
      ],
    );
  }

  Widget _figures(BuildContext context) {
    final theme = Theme.of(context);
    final price = quote?.displayPrice;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.end,
      children: [
        FittedBox(
          fit: BoxFit.scaleDown,
          child: Text(
            price == null ? kStockPlaceholder : stockPrice(price),
            maxLines: 1,
            style: theme.textTheme.titleSmall!.copyWith(
              fontWeight: FontWeight.w700,
              fontFeatures: kStockFigures,
              color: price == null ? theme.colorScheme.onSurfaceVariant : null,
            ),
          ),
        ),
        const SizedBox(height: 4),
        StocksChangeChip(percent: quote?.changePercent),
      ],
    );
  }
}

/// The move as a tinted pill: signed, so it reads without colour too.
class StocksChangeChip extends StatelessWidget {
  final double? percent;

  const StocksChangeChip({super.key, required this.percent});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final surface = theme.scaffoldBackgroundColor;
    final tint = stockTrendColour(context, percent);
    final fill = percent == null
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
