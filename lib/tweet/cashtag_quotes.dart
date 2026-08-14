import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';

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

    return ScopedBuilder<TickerQuoteCache, Map<String, TickerQuote>>(
      store: cache,
      onState: (_, quotes) {
        final chips = [
          for (final symbol in symbols.take(4))
            if (quotes[symbol] != null)
              _QuoteChip(symbol: symbol, quote: quotes[symbol]!),
        ];
        if (chips.isEmpty) {
          return const SizedBox.shrink();
        }
        return Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: Wrap(spacing: 8, runSpacing: 6, children: chips),
        );
      },
    );
  }
}

class _QuoteChip extends StatelessWidget {
  final String symbol;
  final TickerQuote quote;

  const _QuoteChip({required this.symbol, required this.quote});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final percent = quote.changePercent;
    final colour = percent == null
        ? theme.colorScheme.outline
        : stockChangeColour(quote.isUp);
    final price = quote.displayPrice;

    return ActionChip(
      visualDensity: VisualDensity.compact,
      label: Text(
        [
          '\$$symbol',
          if (price != null) stockMoneyFormat.format(price),
          if (percent != null) stockPercentLabel(percent),
        ].join('  '),
      ),
      labelStyle: theme.textTheme.labelMedium!.copyWith(
        fontWeight: FontWeight.w700,
        color: colour,
      ),
      onPressed: () => openTicker(context, symbol),
    );
  }
}
