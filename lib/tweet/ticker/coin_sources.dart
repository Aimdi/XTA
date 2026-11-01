/// Coin prices from more than one public source, so one host refusing never
/// empties the crypto rows: Coinbase, Kraken and Binance before Yahoo, each
/// asked for the bars that fit the chart under its own name for the coin.
library;

import 'dart:math' as math;

import 'package:flutter/foundation.dart';
import 'package:http/http.dart' as http;
import 'package:xta/tweet/ticker/price_http.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/utils/json.dart';

/// The bars a chart wants: how far back, and how finely.
@immutable
class ChartWindow {
  final Duration span;
  final Duration step;

  const ChartWindow(this.span, this.step);

  /// The window a chart range and interval as Yahoo spells them ask for
  /// (`5d`, `30m`); null when either is not a span this app uses.
  static ChartWindow? parse(String range, String interval) => switch ((_span(range), _span(interval))) {
    (final span?, final step?) => ChartWindow(span, step),
    _ => null,
  };

  static Duration? _span(String text) {
    final match = RegExp(r'^(\d+)(m|h|d|wk|mo|y)$').firstMatch(text);
    if (match == null) return null;
    final count = int.parse(match[1]!);
    return switch (match[2]) {
      'm' => Duration(minutes: count),
      'h' => Duration(hours: count),
      'd' => Duration(days: count),
      'wk' => Duration(days: 7 * count),
      'mo' => Duration(days: 30 * count),
      _ => Duration(days: 365 * count),
    };
  }
}

/// One bar of a coin's chart.
typedef CoinBar = ({DateTime at, double open, double high, double low, double close});

/// The finest of [steps] no finer than [window]'s that covers its span in at
/// most [maxBars] bars; null when none does.
Duration? coarsestFit(List<Duration> steps, ChartWindow window, int maxBars) {
  for (final step in steps) {
    if (step >= window.step && window.span.inSeconds / step.inSeconds <= maxBars) {
      return step;
    }
  }
  return null;
}

/// A coin's chart from [bars] (any order), drawn as Yahoo draws one: a day
/// from midnight UTC, a longer chart [span] back from the last bar, the last
/// close as the price and the close before the chart began as where it
/// started. The day's open, high and low are since midnight UTC whatever the
/// span, as on Yahoo.
TickerQuote coinQuoteFromBars(String pair, List<CoinBar> bars, {required Duration span, required String currency}) {
  final sorted = [...bars]..sort((a, b) => a.at.compareTo(b.at));
  if (sorted.isEmpty) {
    throw TickerException(TickerErrorKind.notFound, 'No trades in $pair');
  }
  final last = sorted.last;
  final midnight = DateTime.utc(last.at.year, last.at.month, last.at.day);
  final start = span <= const Duration(days: 1) ? midnight : last.at.subtract(span);
  final shown = _since(sorted, start);
  final today = _since(sorted, midnight);
  return TickerQuote(
    symbol: pair,
    currency: currency,
    price: last.close,
    previousClose: sorted.where((bar) => bar.at.isBefore(start)).lastOrNull?.close ?? shown.first.open,
    points: [for (final bar in shown) TickerPoint(at: bar.at, close: bar.close)],
    shortName: kTickerNames[spokenCashtag(pair)],
    dayOpen: today.first.open,
    dayHigh: today.map((bar) => bar.high).reduce(math.max),
    dayLow: today.map((bar) => bar.low).reduce(math.min),
    tradesAroundTheClock: true,
    instrumentType: 'CRYPTOCURRENCY',
    regularMarketTime: last.at,
  );
}

List<CoinBar> _since(List<CoinBar> bars, DateTime start) => [
  for (final bar in bars)
    if (!bar.at.isBefore(start)) bar,
];

CoinBar? _bar(DateTime at, Json open, Json high, Json low, Json close) =>
    switch ((open.number, high.number, low.number, close.number)) {
      (final o?, final h?, final l?, final c?) => (at: at, open: o, high: h, low: l, close: c),
      _ => null,
    };

DateTime _seconds(int seconds) => DateTime.fromMillisecondsSinceEpoch(seconds * 1000, isUtc: true);

/// Coinbase's candles: `[time, low, high, open, close, volume]`, newest first.
List<CoinBar> coinbaseBars(Object? json) => [
  for (final row in Json(json).list)
    if (row.list case [final time, final low, final high, final open, final close, ...] when time.integer != null)
      ?_bar(_seconds(time.integer!), open, high, low, close),
];

/// Kraken's OHLC bars `[time, open, high, low, close, vwap, volume, count]`,
/// under a key that is Kraken's own name for the pair. An unknown pair is
/// an error in the body, not a status.
List<CoinBar> krakenBars(Object? json) {
  final errors = [for (final error in Json(json)['error'].list) ?error.string];
  if (errors.any((error) => error.contains('Unknown asset pair'))) {
    throw TickerException(TickerErrorKind.notFound, 'Kraken lists no such pair');
  }
  if (errors.isNotEmpty) {
    throw TickerException(TickerErrorKind.unavailable, 'Kraken: ${errors.join(', ')}');
  }
  final result = Json(json)['result'].raw;
  final rows = result is Map ? result.entries.where((entry) => entry.value is List).firstOrNull?.value : null;
  return [
    for (final row in Json(rows).list)
      if (row.list case [final time, final open, final high, final low, final close, ...] when time.integer != null)
        ?_bar(_seconds(time.integer!), open, high, low, close),
  ];
}

/// Binance's klines `[openTime ms, open, high, low, close, ...]`, oldest first.
List<CoinBar> binanceBars(Object? json) => [
  for (final row in Json(json).list)
    if (row.list case [final time, final open, final high, final low, final close, ...] when time.integer != null)
      ?_bar(DateTime.fromMillisecondsSinceEpoch(time.integer!, isUtc: true), open, high, low, close),
];

/// A public price host the chain can ask for a coin's chart.
abstract class CoinSource {
  /// How the source goes by: its own name, never translated.
  String get name;

  /// The step it would answer [window] in, or null when it cannot cover the
  /// span in one answer.
  Duration? stepFor(ChartWindow window);

  /// [coin] (`BTC`) over [window]; a [TickerException] when it cannot say.
  Future<TickerQuote> chart(String coin, ChartWindow window);
}

/// Coinbase Exchange: candles of 1, 5 and 15 minutes, an hour, six hours or
/// a day, at most 300 to an answer.
class CoinbaseSource implements CoinSource {
  final http.Client client;
  final String userAgent;

  CoinbaseSource(this.client, {required this.userAgent});

  static const _steps = [
    Duration(minutes: 1),
    Duration(minutes: 5),
    Duration(minutes: 15),
    Duration(hours: 1),
    Duration(hours: 6),
    Duration(days: 1),
  ];

  @override
  String get name => 'Coinbase';

  @override
  Duration? stepFor(ChartWindow window) => coarsestFit(_steps, window, 300);

  static Uri candlesUri(String coin, Duration step) =>
      Uri.https('api.exchange.coinbase.com', '/products/$coin-USD/candles', {'granularity': '${step.inSeconds}'});

  @override
  Future<TickerQuote> chart(String coin, ChartWindow window) async {
    final json = await fetchPriceJson(
      client,
      candlesUri(coin, stepFor(window)!),
      userAgent: userAgent,
      notFound: {400, 404},
    );
    return coinQuoteFromBars('$coin-USD', coinbaseBars(json), span: window.span, currency: 'USD');
  }
}

/// Kraken: bars of 1 minute to 15 days, the latest 720 to an answer, under
/// Kraken's own names for a few coins.
class KrakenSource implements CoinSource {
  final http.Client client;
  final String userAgent;

  KrakenSource(this.client, {required this.userAgent});

  static const _minutes = [1, 5, 15, 30, 60, 240, 1440, 10080, 21600];
  static const _names = {'BTC': 'XBT', 'DOGE': 'XDG'};

  @override
  String get name => 'Kraken';

  @override
  Duration? stepFor(ChartWindow window) =>
      coarsestFit([for (final minutes in _minutes) Duration(minutes: minutes)], window, 720);

  static Uri ohlcUri(String coin, Duration step) => Uri.https('api.kraken.com', '/0/public/OHLC', {
    'pair': '${_names[coin] ?? coin}USD',
    'interval': '${step.inMinutes}',
  });

  @override
  Future<TickerQuote> chart(String coin, ChartWindow window) async {
    final json = await fetchPriceJson(client, ohlcUri(coin, stepFor(window)!), userAgent: userAgent);
    return coinQuoteFromBars('$coin-USD', krakenBars(json), span: window.span, currency: 'USD');
  }
}

/// Binance: klines of 1 minute to a week, up to 1000 to an answer, quoted in
/// USDT. It refuses some regions outright (451), which rests it.
class BinanceSource implements CoinSource {
  final http.Client client;
  final String userAgent;

  BinanceSource(this.client, {required this.userAgent});

  static const _intervals = [
    (Duration(minutes: 1), '1m'),
    (Duration(minutes: 3), '3m'),
    (Duration(minutes: 5), '5m'),
    (Duration(minutes: 15), '15m'),
    (Duration(minutes: 30), '30m'),
    (Duration(hours: 1), '1h'),
    (Duration(hours: 2), '2h'),
    (Duration(hours: 4), '4h'),
    (Duration(hours: 6), '6h'),
    (Duration(hours: 12), '12h'),
    (Duration(days: 1), '1d'),
    (Duration(days: 3), '3d'),
    (Duration(days: 7), '1w'),
  ];

  @override
  String get name => 'Binance';

  @override
  Duration? stepFor(ChartWindow window) => coarsestFit([for (final (step, _) in _intervals) step], window, 1000);

  static Uri klinesUri(String coin, Duration step, ChartWindow window) =>
      Uri.https('api.binance.com', '/api/v3/klines', {
        'symbol': '${coin}USDT',
        'interval': _intervals.firstWhere((entry) => entry.$1 == step).$2,
        'limit': '${math.min(1000, (window.span.inSeconds / step.inSeconds).ceil() + 1)}',
      });

  @override
  Future<TickerQuote> chart(String coin, ChartWindow window) async {
    final uri = klinesUri(coin, stepFor(window)!, window);
    final json = await fetchPriceJson(client, uri, userAgent: userAgent, notFound: {400, 404});
    return coinQuoteFromBars('$coin-USD', binanceBars(json), span: window.span, currency: 'USDT');
  }
}

/// Asks [sources] in order for a coin's chart, then [yahoo] — the host the
/// stocks need, kept last and never rested. The first answer wins.
///
/// Only when every source that covers the chart said it has no such coin is
/// the answer "not found", so the cashtag may still be a listed security; a
/// refusal or a rest along the way makes it "unavailable" instead.
class CoinSourceChain {
  final List<CoinSource> sources;
  final PriceSourceHealth health;

  CoinSourceChain(this.sources, {PriceSourceHealth? health}) : health = health ?? PriceSourceHealth.shared;

  /// Whether [symbol] names a coin as Yahoo spells one: `BTC-USD`.
  static bool isCoinPair(String symbol) => symbol.endsWith('-USD') && !symbol.startsWith('^') && symbol.length > 4;

  Future<TickerQuote> chart(String pair, ChartWindow window, {required Future<TickerQuote> Function() yahoo}) async {
    final coin = pair.substring(0, pair.length - 4);
    var refused = false;
    for (final source in sources) {
      if (source.stepFor(window) == null || !health.lists(source.name, coin)) {
        continue;
      }
      if (health.resting(source.name)) {
        refused = true;
        continue;
      }
      try {
        final quote = await source.chart(coin, window);
        health.answered(source.name);
        return quote;
      } on TickerException catch (e) {
        refused |= _settle(source.name, coin, e);
      }
    }
    return _lastResort(pair, yahoo, refused: refused);
  }

  /// Notes what [error] says about [source]: a coin it has no trades in is
  /// not asked of it again, and any other failure rests it. True when it
  /// rested, which says nothing about whether the coin exists.
  bool _settle(String source, String coin, TickerException error) {
    if (error.kind == TickerErrorKind.notFound) {
      health.unlisted(source, coin);
      return false;
    }
    health.refused(source);
    return true;
  }

  Future<TickerQuote> _lastResort(String pair, Future<TickerQuote> Function() yahoo, {required bool refused}) async {
    try {
      return await yahoo();
    } on TickerException catch (e) {
      if (refused && e.kind != TickerErrorKind.unavailable) {
        throw TickerException(TickerErrorKind.unavailable, 'No source could price $pair: ${e.message}');
      }
      rethrow;
    }
  }
}
