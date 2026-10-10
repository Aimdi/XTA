import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';

/// One labelled figure under a chart: `Open  189.33`.
typedef TickerStat = ({String label, String? value});

/// Key statistics as label/value rows in as many columns as fit — Google
/// Finance's grid. A figure the source did not send shows a dash, so the
/// grid keeps its shape from symbol to symbol.
class TickerStatGrid extends StatelessWidget {
  final List<TickerStat> stats;

  const TickerStatGrid({super.key, required this.stats});

  @override
  Widget build(BuildContext context) {
    return LayoutBuilder(
      builder: (context, constraints) {
        final cell = MediaQuery.textScalerOf(context).scale(150);
        final columns = (constraints.maxWidth / cell).floor().clamp(1, 3);
        return Column(
          children: [
            for (var i = 0; i < stats.length; i += columns)
              _row(context, stats.skip(i).take(columns).toList(), columns),
          ],
        );
      },
    );
  }

  Widget _row(BuildContext context, List<TickerStat> cells, int columns) {
    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        for (var i = 0; i < columns; i++) ...[
          if (i > 0) const SizedBox(width: 24),
          Expanded(
            child: i < cells.length
                ? _cell(context, cells[i])
                : const SizedBox.shrink(),
          ),
        ],
      ],
    );
  }

  Widget _cell(BuildContext context, TickerStat stat) {
    final theme = Theme.of(context);
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 8),
      decoration: BoxDecoration(
        border: Border(
          bottom: BorderSide(color: theme.colorScheme.outlineVariant),
        ),
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.baseline,
        textBaseline: TextBaseline.alphabetic,
        children: [
          Expanded(
            child: Text(
              stat.label,
              style: theme.textTheme.bodySmall!.copyWith(
                color: theme.colorScheme.onSurfaceVariant,
              ),
            ),
          ),
          const SizedBox(width: 8),
          Text(
            stat.value ?? kStockPlaceholder,
            style: theme.textTheme.bodyMedium!.copyWith(
              fontWeight: FontWeight.w600,
              fontFeatures: kStockFigures,
            ),
          ),
        ],
      ),
    );
  }
}

/// What the line cannot say: where the day opened and closed before, its
/// range, how much changed hands, and where the price sits inside the year.
///
/// Read from a one-day quote, so "Prev. close" is the previous session's
/// close whatever range the chart above is showing.
class TickerStats extends StatelessWidget {
  final TickerQuote quote;

  const TickerStats({super.key, required this.quote});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    String? price(double? value) => value == null ? null : stockPrice(value);

    return TickerStatGrid(
      stats: [
        (label: l10n.plugin_stocks_open, value: price(quote.dayOpen)),
        (
          label: l10n.plugin_stocks_prev_close,
          value: price(quote.previousClose),
        ),
        (label: l10n.plugin_stocks_day_high, value: price(quote.dayHigh)),
        (label: l10n.plugin_stocks_day_low, value: price(quote.dayLow)),
        (
          label: l10n.plugin_stocks_volume,
          value: quote.volume == null ? null : stockCompact(quote.volume!),
        ),
        (label: l10n.plugin_stocks_year_high, value: price(quote.yearHigh)),
        (label: l10n.plugin_stocks_year_low, value: price(quote.yearLow)),
      ],
    );
  }
}
