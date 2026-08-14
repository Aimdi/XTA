import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';

/// What the line cannot say: how much changed hands, today's range, and where
/// the price sits inside the year.
///
/// A chart alone answers "which way" and nothing else. These are the numbers
/// every market page puts under one, and both places that draw a price — the
/// ticker screen and the watchlist — want the same set.
class TickerStats extends StatelessWidget {
  final TickerQuote quote;

  /// Shown at the end of the row when there is one, since the prices above are
  /// bare numbers.
  final bool showCurrency;

  const TickerStats({super.key, required this.quote, this.showCurrency = true});

  /// Volume runs to nine figures, which no row has room for, so it is shortened
  /// the way every market page shortens it.
  static final NumberFormat _volume = NumberFormat.compact();
  static final NumberFormat _price = NumberFormat.decimalPatternDigits(
    decimalDigits: 2,
  );

  /// Stands in for a number the response did not carry. Punctuation rather than
  /// a sentence, so it needs no translation.
  static const String placeholder = '—';

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final currency = quote.currency;

    return Wrap(
      spacing: 20,
      runSpacing: 8,
      children: [
        _stat(
          theme,
          l10n.plugin_stocks_volume,
          quote.volume,
          format: _volume.format,
        ),
        _stat(theme, l10n.plugin_stocks_day_high, quote.dayHigh),
        _stat(theme, l10n.plugin_stocks_day_low, quote.dayLow),
        _stat(theme, l10n.plugin_stocks_year_high, quote.yearHigh),
        _stat(theme, l10n.plugin_stocks_year_low, quote.yearLow),
        if (showCurrency && currency != null)
          Text(currency, style: theme.textTheme.labelSmall),
      ],
    );
  }

  Widget _stat(
    ThemeData theme,
    String label,
    double? value, {
    String Function(double)? format,
  }) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: theme.textTheme.labelSmall!.copyWith(
            color: theme.colorScheme.outline,
          ),
        ),
        Text(
          value == null ? placeholder : (format ?? _price.format)(value),
          style: theme.textTheme.bodyMedium,
        ),
      ],
    );
  }
}
