import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';

/// In-memory last prints, shared by the stocks tab, the ticker page, and
/// cashtags in the timeline.
///
/// A quote lives here for a couple of minutes so a `$AAPL` in the feed can
/// show today's move without every tile asking the host again. Failures are
/// swallowed: a missing price must not become an error banner on a post.
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

  Future<void> ensure(Iterable<String> symbols) async {
    final needed = [
      for (final raw in symbols)
        if (_shouldFetch(raw.toUpperCase())) raw.toUpperCase(),
    ];
    if (needed.isEmpty) {
      return;
    }

    final queued = <Future<void>>[];
    for (final symbol in needed) {
      if (queued.length >= _maxConcurrent) {
        break;
      }
      queued.add(_fetchOne(symbol));
    }
    await Future.wait(queued);
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
        await _client.fetchQuote(symbol, range: '5d', interval: '1d'),
      );
    } on TickerException {
      _fetchedAt[symbol] = DateTime.now();
    } finally {
      _inflight.remove(symbol);
    }
  }
}
