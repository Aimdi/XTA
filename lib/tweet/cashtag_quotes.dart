import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/ui/motion.dart';

/// Cashtags X attached to a post, uppercased and de-duplicated.
List<String> tweetCashtags(TweetWithCard tweet) {
  final raw = tweet.noteEntities?.symbols ?? tweet.entities?.symbols;
  if (raw == null || raw.isEmpty) {
    return const [];
  }
  final seen = <String>{};
  return [
    for (final symbol in raw)
      if (symbol.text != null && seen.add(symbol.text!.toUpperCase()))
        symbol.text!.toUpperCase(),
  ];
}

/// Mini tape under a post that mentions tickers — getquin / StockTwits's
/// "the prices for what this is about", not another search.
///
/// Quotes come from [TickerQuoteCache]. Missing cache (tests, a build that
/// forgot the provider) is a no-op, never an error.
class CashtagQuotesBar extends StatelessWidget {
  final List<String> symbols;

  const CashtagQuotesBar({super.key, required this.symbols});

  @override
  Widget build(BuildContext context) {
    if (symbols.isEmpty) {
      return const SizedBox.shrink();
    }

    late final TickerQuoteCache cache;
    try {
      cache = context.read<TickerQuoteCache>();
    } on ProviderNotFoundException {
      return const SizedBox.shrink();
    }

    WidgetsBinding.instance.addPostFrameCallback((_) {
      cache.ensure(symbols.take(3));
    });

    // The prices often land while the post is on screen: the row grows in
    // rather than pushing the media and footer down at once.
    return XtaAnimatedSize(
      child: ScopedBuilder<TickerQuoteCache, Map<String, TickerQuote>>(
        store: cache,
        onState: (_, quotes) {
          final chips = [
            for (final symbol in symbols.take(4))
              if (quotes[symbol] != null)
                _QuoteChip(symbol: symbol, quote: quotes[symbol]!),
          ];
          if (chips.isEmpty) {
            return const SizedBox(width: double.infinity);
          }
          return XtaFadeIn(
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 0, 16, 4),
              child: Wrap(spacing: 6, children: chips),
            ),
          );
        },
      ),
    );
  }
}

/// `$AAPL 200.00 +5.26%` in a small outlined chip, as many to a line as fit:
/// the symbol in the text colour, and only the move in green or red.
class _QuoteChip extends StatelessWidget {
  final String symbol;
  final TickerQuote quote;

  const _QuoteChip({required this.symbol, required this.quote});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final percent = quote.changePercent;
    final price = quote.displayPrice;

    return ActionChip(
      // Narrow, not short: vertical density would shrink the 48 dp target.
      visualDensity: const VisualDensity(horizontal: -4, vertical: 0),
      padding: const EdgeInsets.symmetric(horizontal: 6),
      labelPadding: EdgeInsets.zero,
      backgroundColor: Colors.transparent,
      side: BorderSide(color: theme.colorScheme.outlineVariant),
      shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
      label: Text.rich(
        TextSpan(
          children: [
            TextSpan(
              text: '\$$symbol',
              style: TextStyle(
                color: theme.colorScheme.onSurface,
                fontWeight: FontWeight.w800,
              ),
            ),
            if (price != null) TextSpan(text: ' ${stockPrice(price)}'),
            if (percent != null)
              TextSpan(
                text: ' ${stockPercentLabel(percent)}',
                style: TextStyle(
                  color: stockTrendColour(context, percent),
                  fontWeight: FontWeight.w700,
                ),
              ),
          ],
        ),
        semanticsLabel: [
          '\$$symbol',
          if (price != null) stockPriceSpoken(price),
          if (percent != null) stockPercentLabel(percent),
        ].join(', '),
        style: theme.textTheme.labelMedium!.copyWith(
          color: theme.colorScheme.onSurfaceVariant,
          fontFeatures: kStockFigures,
        ),
      ),
      onPressed: () => openTicker(context, symbol),
    );
  }
}
