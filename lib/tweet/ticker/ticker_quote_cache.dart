import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/tweet/ticker/quote_failures.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// In-memory last prints, shared by the stocks tab, the ticker page, and
/// cashtags in the timeline.
///
/// A quote lives here for a couple of minutes so a `$AAPL` in the feed can
/// show today's move without every tile asking the host again. A failure
/// never becomes an error banner on a post, but it is remembered ([failed]),
/// so a quote row can say it has no data instead of looking like it loads.
///
/// A share's or an index's entry is a one-day quote: its baseline is the
/// previous session's close, so the move beside a price is the day's move,
/// and its bars are the day's intraday line a watchlist row draws as a
/// sparkline. A coin has no close: its entry is measured over the last 24
/// hours of the week chart's bars.
class TickerQuoteCache extends Store<Map<String, TickerQuote>>
    with QuoteFailures<Map<String, TickerQuote>> {
  TickerQuoteCache({TickerClient? client})
    : _client = client ?? TickerClient(),
      super(const {});

  final TickerClient _client;
  final Set<String> _inflight = {};
  final Map<String, DateTime> _fetchedAt = {};

  static const _ttl = Duration(minutes: 2);
  static const _maxConcurrent = 4;

  TickerQuote? peek(String symbol) => state[symbol.toUpperCase()];

  /// Takes a quote fetched elsewhere: the ticker page's day chart. A coin's
  /// day chart is measured from midnight, not over 24 hours as its row is,
  /// so it is left out.
  void remember(String symbol, TickerQuote quote) {
    if (quote.tradesAroundTheClock &&
        quote.changeWindow != TickerChangeWindow.rolling24h) {
      return;
    }
    _store(symbol.toUpperCase(), quote);
  }

  void _store(String key, TickerQuote quote) {
    _fetchedAt[key] = DateTime.now();
    recordAnswer(key);
    update({...state, key: quote});
  }

  /// Fetches every symbol that is missing or stale, [_maxConcurrent] at a
  /// time, so a long watchlist fills in completely rather than four rows.
  Future<void> ensure(Iterable<String> symbols) async {
    final needed = {
      for (final raw in symbols)
        if (_shouldFetch(raw.toUpperCase())) raw.toUpperCase(),
    }.toList();
    for (var i = 0; i < needed.length; i += _maxConcurrent) {
      await Future.wait(needed.skip(i).take(_maxConcurrent).map(_fetchOne));
    }
  }

  /// Pull-to-refresh and a row's retry: forget when [symbols] were fetched,
  /// then fetch them. A row that had no data shows that it loads again.
  Future<void> refresh(Iterable<String> symbols) {
    final keys = [for (final raw in symbols) raw.toUpperCase()];
    keys.forEach(_fetchedAt.remove);
    retrying(keys);
    return ensure(keys);
  }

  bool _shouldFetch(String key) {
    if (_inflight.contains(key)) {
      return false;
    }
    final at = _fetchedAt[key];
    return at == null || DateTime.now().difference(at) >= _ttl;
  }

  Future<void> _fetchOne(String symbol) async {
    _inflight.add(symbol);
    try {
      _store(symbol, await _quoteFor(symbol));
    } on TickerException {
      _fetchedAt[symbol] = DateTime.now();
      recordFailure(symbol);
    } finally {
      _inflight.remove(symbol);
    }
  }

  /// A known coin is asked for as a coin, over 24 hours. Anything else gets
  /// the day chart with its extended hours; when that turns out to be a coin
  /// after all, it is asked for again over 24 hours, and the day chart stays
  /// if that fails.
  Future<TickerQuote> _quoteFor(String symbol) async {
    if (knownTickerKind(symbol) == TickerKind.crypto) {
      return _overLastDay(symbol);
    }
    final day = await _client.fetchQuote(
      symbol,
      range: TickerRange.day.range,
      interval: TickerRange.day.interval,
      includePrePost: true,
    );
    if (!day.tradesAroundTheClock) return day;
    try {
      return await _overLastDay(day.symbol);
    } on TickerException {
      return day;
    }
  }

  Future<TickerQuote> _overLastDay(String symbol) async {
    final week = await _client.fetchQuote(
      symbol,
      range: TickerRange.week.range,
      interval: TickerRange.week.interval,
      prefer: TickerInstrument.crypto,
    );
    return week.rolling24h();
  }
}
