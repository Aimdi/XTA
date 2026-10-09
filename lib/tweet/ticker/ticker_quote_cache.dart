import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';

/// In-memory last prints, shared by the stocks tab, the ticker page, and
/// cashtags in the timeline.
///
/// A quote lives here for a couple of minutes so a `$AAPL` in the feed can
/// show today's move without every tile asking the host again. Failures are
/// swallowed: a missing price must not become an error banner on a post.
///
/// Every entry is a one-day quote: its baseline is the previous session's
/// close, so the move beside a price is the day's move, and its bars are the
/// day's intraday line a watchlist row draws as a sparkline.
class TickerQuoteCache extends Store<Map<String, TickerQuote>> {
  TickerQuoteCache({TickerClient? client})
    : _client = client ?? TickerClient(),
      super(const {});

  final TickerClient _client;
  final Set<String> _inflight = {};
  final Map<String, DateTime> _fetchedAt = {};

  static const _ttl = Duration(minutes: 2);
  static const _maxConcurrent = 4;

  TickerQuote? peek(String symbol) => state[symbol.toUpperCase()];

  void remember(String symbol, TickerQuote quote) {
    final key = symbol.toUpperCase();
    _fetchedAt[key] = DateTime.now();
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

  /// Pull-to-refresh: forget when [symbols] were fetched, then fetch them.
  Future<void> refresh(Iterable<String> symbols) {
    for (final raw in symbols) {
      _fetchedAt.remove(raw.toUpperCase());
    }
    return ensure(symbols);
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
      remember(
        symbol,
        await _client.fetchQuote(
          symbol,
          range: TickerRange.day.range,
          interval: TickerRange.day.interval,
          includePrePost: true,
        ),
      );
    } on TickerException {
      _fetchedAt[symbol] = DateTime.now();
    } finally {
      _inflight.remove(symbol);
    }
  }
}
