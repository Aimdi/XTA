import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
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

/// How many of a post's cashtags the strip shows. A post listing twenty
/// tickers is an advert, and would ask for twenty prices.
const kCashtagStripLimit = 4;

/// The price strip at the foot of a post that names tickers: each of its
/// first cashtags with the day's line, the price and the move, one line that
/// scrolls sideways from the text's start to the card's edge.
///
/// Quotes come from [TickerQuoteCache]. Without one (tests, a build that
/// forgot the provider) there is no strip, never an error.
class CashtagStrip extends StatelessWidget {
  final List<String> symbols;

  const CashtagStrip({super.key, required this.symbols});

  @override
  Widget build(BuildContext context) {
    final shown = symbols.take(kCashtagStripLimit).toList();
    final cache = shown.isEmpty ? null : _cacheOf(context);
    if (cache == null) {
      return const SizedBox.shrink();
    }
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(cache.ensure(shown));
    });
    return ScopedBuilder<TickerQuoteCache, Map<String, TickerQuote>>(
      store: cache,
      distinct: (quotes) => [
        for (final symbol in shown) quotes[symbol],
        for (final symbol in shown) cache.failed.contains(symbol),
      ],
      onState: (context, quotes) => _strip(context, [
        for (final (index, symbol) in shown.indexed)
          _StripEntry(
            symbol: symbol,
            quote: quotes[symbol],
            failed: cache.failed.contains(symbol),
            first: index == 0,
          ),
      ]),
    );
  }

  Widget _strip(BuildContext context, List<Widget> entries) {
    final colour = Theme.of(context).dividerColor;
    final line = BorderSide(color: colour);
    return Padding(
      padding: const EdgeInsetsDirectional.only(start: 16, top: 8),
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: Border(top: line, bottom: line),
        ),
        child: SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: IntrinsicHeight(
            child: Row(
              children: [
                for (final (index, entry) in entries.indexed) ...[
                  if (index > 0)
                    VerticalDivider(
                      width: 1,
                      indent: 14,
                      endIndent: 14,
                      color: colour,
                    ),
                  entry,
                ],
              ],
            ),
          ),
        ),
      ),
    );
  }
}

TickerQuoteCache? _cacheOf(BuildContext context) {
  try {
    return context.read<TickerQuoteCache>();
  } on ProviderNotFoundException {
    return null;
  }
}

/// `AAPL ⌇ 344.63 −1.23%`: the symbol, the day's line, the price and the
/// move; a grey bar while it loads, "No price" when there is none, and a
/// closed market in the secondary colour.
class _StripEntry extends StatelessWidget {
  final String symbol;
  final TickerQuote? quote;
  final bool failed;

  /// The first entry starts where the text does.
  final bool first;

  const _StripEntry({
    required this.symbol,
    required this.quote,
    required this.failed,
    this.first = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final state = stockRowState(quote, failed: failed, now: DateTime.now());
    return Semantics(
      button: true,
      label: _label(L10n.of(context), state),
      excludeSemantics: true,
      child: InkWell(
        onTap: () => openTicker(context, symbol),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: 48),
          child: Padding(
            padding: EdgeInsetsDirectional.only(start: first ? 0 : 12, end: 12),
            child: Row(
              mainAxisSize: MainAxisSize.min,
              children: [
                Text(
                  symbol,
                  style: theme.textTheme.bodyMedium!.copyWith(
                    fontSize: 14,
                    fontWeight: FontWeight.w700,
                  ),
                ),
                const SizedBox(width: 6),
                ..._figures(context, state),
              ],
            ),
          ),
        ),
      ),
    );
  }

  List<Widget> _figures(BuildContext context, StockRowState state) {
    final theme = Theme.of(context);
    final muted = theme.colorScheme.onSurfaceVariant;
    final quote = this.quote;
    if (state == StockRowState.loading) {
      return const [StocksFiguresSkeleton(inline: true)];
    }
    if (state == StockRowState.noData || quote == null) {
      return [
        Text(
          L10n.of(context).plugin_stocks_no_price,
          style: theme.textTheme.bodySmall!.copyWith(
            fontSize: 13,
            color: muted,
          ),
        ),
      ];
    }
    final closed = state == StockRowState.closed;
    final percent = quote.changePercent;
    return [
      if (!closed && quote.points.length >= 2) ...[
        SizedBox(
          width: 32,
          height: 14,
          child: StocksSparkline(quote: quote, showBaseline: false),
        ),
        const SizedBox(width: 6),
      ],
      Text(
        stockPrice(quote.displayPrice!),
        style: theme.textTheme.bodyMedium!.copyWith(
          fontSize: 14,
          fontFeatures: kStockFigures,
          color: closed ? muted : theme.colorScheme.onSurface,
        ),
      ),
      if (percent != null) ...[
        const SizedBox(width: 6),
        Text(
          stockPercentLabel(percent),
          style: theme.textTheme.bodySmall!.copyWith(
            fontSize: 13,
            fontWeight: FontWeight.w500,
            fontFeatures: kStockFigures,
            color: closed ? muted : stockTrendColour(context, percent),
          ),
        ),
      ],
    ];
  }

  String _label(L10n l10n, StockRowState state) {
    final quote = this.quote;
    final percent = quote?.changePercent;
    return [
      '\$$symbol',
      ...switch (state) {
        StockRowState.loading => [l10n.plugin_stocks_loading_price],
        StockRowState.noData => [l10n.plugin_stocks_no_price],
        StockRowState.closed || StockRowState.live => [
          stockPriceSpoken(quote!.displayPrice!),
          if (percent != null) stockPercentLabel(percent),
        ],
      },
    ].join(', ');
  }
}
