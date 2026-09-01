import 'package:flutter/widgets.dart';
import 'package:xta/generated/l10n.dart';

/// How much history a chart shows, and how finely.
///
/// The interval is tied to the range rather than chosen separately: a year of
/// five-minute bars is tens of thousands of points to draw a line nobody can
/// read, and a day of daily closes is one point.
enum TickerRange {
  day(range: '1d', interval: '5m'),
  week(range: '5d', interval: '30m'),
  month(range: '1mo', interval: '1d'),
  sixMonths(range: '6mo', interval: '1d'),
  year(range: '1y', interval: '1d'),
  fiveYears(range: '5y', interval: '1wk');

  final String range;
  final String interval;

  const TickerRange({required this.range, required this.interval});
}

/// The short label a range goes by. Abbreviated per language — a German reader
/// expects `1T` and `1J`, not `1D` and `1Y`.
String tickerRangeLabel(BuildContext context, TickerRange range) {
  final l10n = L10n.of(context);

  return switch (range) {
    TickerRange.day => l10n.ticker_range_day,
    TickerRange.week => l10n.ticker_range_week,
    TickerRange.month => l10n.ticker_range_month,
    TickerRange.sixMonths => l10n.ticker_range_six_months,
    TickerRange.year => l10n.ticker_range_year,
    TickerRange.fiveYears => l10n.ticker_range_five_years,
  };
}
