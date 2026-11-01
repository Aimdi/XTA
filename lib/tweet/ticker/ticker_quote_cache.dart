import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/ticker/quote_failures.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/utils/pref_lists.dart';

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
///
/// A cashtag that names both a coin and a listed security (`$SOL`: Solana and
/// Emeren) is read as the coin until the reader picks the other reading; the
/// pick is kept in [prefs] and decides every entry for that symbol.
class TickerQuoteCache extends Store<Map<String, TickerQuote>>
    with QuoteFailures<Map<String, TickerQuote>> {
  TickerQuoteCache({TickerClient? client, this.prefs})
    : _client = client ?? TickerClient(),
      super(const {});

  final TickerClient _client;

  /// Where the picked readings are kept; without it they last the session.
  final BasePrefService? prefs;
  final Set<String> _inflight = {};
  final Map<String, DateTime> _fetchedAt = {};
  final Map<String, TickerQuote> _listings = {};
  final Set<String> _listingsAsked = {};
  late final Map<String, TickerInstrument> _picks = _readPicks();

  static const _ttl = Duration(minutes: 2);
  static const _maxConcurrent = 4;

  TickerQuote? peek(String symbol) => state[symbol.toUpperCase()];

  /// The reading the reader picked for [symbol]; `auto` until they pick.
  TickerInstrument instrumentFor(String symbol) =>
      _picks[symbol.toUpperCase()] ?? TickerInstrument.auto;

  /// The listed security an unpicked coin-first cashtag also names, once
  /// [ensureReadings] found one: the second reading a watchlist row offers.
  TickerQuote? listingFor(String symbol) => _listings[symbol.toUpperCase()];

  /// Keeps [instrument] as the reading of [symbol] and asks for it, showing
  /// it as loading meanwhile rather than the other reading's price.
  Future<void> choose(String symbol, TickerInstrument instrument) async {
    final key = symbol.toUpperCase();
    if (instrumentFor(key) == instrument) return;
    if (instrument == TickerInstrument.auto) {
      _picks.remove(key);
    } else {
      _picks[key] = instrument;
    }
    _listings.remove(key);
    update({...state}..remove(key));
    await prefs?.set(optionTickerInstruments, [
      for (final MapEntry(:key, :value) in _picks.entries) '$key=${value.name}',
    ]);
    await refresh([key]);
  }

  /// Looks once per symbol for the listed security behind each unpicked
  /// coin-first cashtag among [symbols]. Only the watchlist asks: the tape's
  /// coins are coins.
  Future<void> ensureReadings(Iterable<String> symbols) async {
    final keys = [
      for (final raw in symbols)
        if (isAmbiguousTicker(raw) &&
            instrumentFor(raw) == TickerInstrument.auto &&
            _listingsAsked.add(raw.toUpperCase()))
          raw.toUpperCase(),
    ];
    await Future.wait(keys.map(_findListing));
  }

  Future<void> _findListing(String key) async {
    try {
      final listing = await _day(key, prefer: TickerInstrument.stock);
      if (listing.tradesAroundTheClock ||
          instrumentFor(key) != TickerInstrument.auto) {
        return;
      }
      _listings[key] = listing;
      update(state, force: true);
    } on TickerException {
      // One reading only: there is nothing to choose between.
    }
  }

  Map<String, TickerInstrument> _readPicks() {
    final prefs = this.prefs;
    final saved = prefs == null
        ? null
        : stringListPref(prefs, optionTickerInstruments);
    final byName = TickerInstrument.values.asNameMap();
    return {
      for (final entry in saved ?? const <String>[])
        if (entry.split('=') case [
          final symbol,
          final name,
        ] when byName[name] != null && byName[name] != TickerInstrument.auto)
          symbol.toUpperCase(): byName[name]!,
    };
  }

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

  /// A known coin is asked for as a coin, over 24 hours, unless the reader
  /// picked the listed security of the same letters. Anything else gets the
  /// day chart with its extended hours; when that turns out to be a coin after
  /// all, it is asked for again over 24 hours, and the day chart stays if that
  /// fails.
  Future<TickerQuote> _quoteFor(String symbol) async {
    final pick = instrumentFor(symbol);
    if (pick == TickerInstrument.stock) {
      return _day(symbol, prefer: TickerInstrument.stock);
    }
    if (pick == TickerInstrument.crypto ||
        knownTickerKind(symbol) == TickerKind.crypto) {
      return _overLastDay(symbol);
    }
    final day = await _day(symbol);
    if (!day.tradesAroundTheClock) return day;
    try {
      return await _overLastDay(day.symbol);
    } on TickerException {
      return day;
    }
  }

  Future<TickerQuote> _day(
    String symbol, {
    TickerInstrument prefer = TickerInstrument.auto,
  }) {
    return _client.fetchQuote(
      symbol,
      range: TickerRange.day.range,
      interval: TickerRange.day.interval,
      includePrePost: true,
      prefer: prefer,
    );
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
