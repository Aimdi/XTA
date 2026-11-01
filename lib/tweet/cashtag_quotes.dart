import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
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

/// How many cashtags of one post are followed by their move. A post listing
/// twenty tickers is an advert, and would ask for twenty prices.
const kCashtagMovesPerPost = 4;

/// The design's 13 sp move beside 15 sp post text, kept in proportion when
/// the reader enlarges the text.
const _moveScale = 13 / 15;

/// A cashtag as a post reads it, `$DOT +3.98%`: with [showMove], its move
/// follows in green or red once the price arrives.
///
/// It sits in a line of text, which already scales it by the reader's text
/// size, so its own text is not scaled again. Quotes come from
/// [TickerQuoteCache]. Without one (tests, a build that forgot the provider)
/// the cashtag is its text alone, never an error.
class CashtagText extends StatelessWidget {
  final String symbol;
  final TextStyle style;
  final bool showMove;

  const CashtagText({
    super.key,
    required this.symbol,
    required this.style,
    this.showMove = false,
  });

  @override
  Widget build(BuildContext context) {
    final cache = showMove ? _cacheOf(context) : null;
    if (cache == null) {
      return Text('\$$symbol', style: style, textScaler: TextScaler.noScaling);
    }
    final key = symbol.toUpperCase();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      unawaited(cache.ensure([key]));
    });
    return ScopedBuilder<TickerQuoteCache, Map<String, TickerQuote>>(
      store: cache,
      distinct: (quotes) => quotes[key],
      onState: (context, quotes) => _text(context, quotes[key]),
    );
  }

  Widget _text(BuildContext context, TickerQuote? quote) {
    final percent = quote?.changePercent;
    if (percent == null) {
      return Text('\$$symbol', style: style, textScaler: TextScaler.noScaling);
    }
    final size = DefaultTextStyle.of(context).style.fontSize ?? 15;
    return Text.rich(
      TextSpan(
        text: '\$$symbol',
        style: style,
        children: [
          TextSpan(
            text: ' ${stockPercentLabel(percent)}',
            style: TextStyle(
              fontSize: size * _moveScale,
              fontWeight: FontWeight.w500,
              color: stockTrendColour(context, percent),
              fontFeatures: kStockFigures,
            ),
          ),
        ],
      ),
      textScaler: TextScaler.noScaling,
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
