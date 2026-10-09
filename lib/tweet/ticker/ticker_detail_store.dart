import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';
import 'package:xta/tweet/ticker/ticker_search.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

const Object _keep = Object();

/// Everything a ticker page shows above its posts.
class TickerDetailState {
  final TickerRange range;
  final TickerInstrument instrument;

  /// The chart for [range]. Its move is measured from the range's start.
  final TickerQuote? quote;

  /// Today's quote, for the key statistics and the market session —
  /// unchanged while the reader flips the chart between ranges.
  final TickerQuote? dayQuote;
  final bool loading;
  final bool failed;
  final List<TickerNewsItem> news;

  /// The point under the reader's finger, if any.
  final TickerPoint? scrubbed;

  const TickerDetailState({
    this.range = TickerRange.day,
    this.instrument = TickerInstrument.auto,
    this.quote,
    this.dayQuote,
    this.loading = false,
    this.failed = false,
    this.news = const [],
    this.scrubbed,
  });

  TickerDetailState copyWith({
    TickerRange? range,
    TickerInstrument? instrument,
    Object? quote = _keep,
    Object? dayQuote = _keep,
    bool? loading,
    bool? failed,
    List<TickerNewsItem>? news,
    Object? scrubbed = _keep,
  }) => TickerDetailState(
    range: range ?? this.range,
    instrument: instrument ?? this.instrument,
    quote: identical(quote, _keep) ? this.quote : quote as TickerQuote?,
    dayQuote: identical(dayQuote, _keep)
        ? this.dayQuote
        : dayQuote as TickerQuote?,
    loading: loading ?? this.loading,
    failed: failed ?? this.failed,
    news: news ?? this.news,
    scrubbed: identical(scrubbed, _keep)
        ? this.scrubbed
        : scrubbed as TickerPoint?,
  );
}

/// Loads a ticker page's chart, day quote and headlines. A newer request
/// always wins: switching range mid-flight never lets the old answer land.
class TickerDetailStore extends Store<TickerDetailState> {
  final String symbol;
  final TickerClient client;

  /// Hands a fresh one-day quote to whoever shares them (the quote cache).
  final void Function(TickerQuote quote)? onDayQuote;
  int _generation = 0;
  bool _closed = false;

  TickerDetailStore({
    required this.symbol,
    required this.client,
    this.onDayQuote,
  }) : super(const TickerDetailState());

  bool _stale(int generation) => _closed || generation != _generation;

  /// [silent] refreshes keep what is on screen and never show an error over
  /// a chart that is merely a minute old.
  Future<void> load({bool silent = false}) async {
    final generation = ++_generation;
    final range = state.range;
    final instrument = state.instrument;
    if (!silent) update(state.copyWith(loading: true, failed: false));
    try {
      final quote = await _fetch(range, instrument);
      final day = range == TickerRange.day
          ? quote
          : state.dayQuote ?? await _dayOrNull(instrument);
      if (_stale(generation)) return;
      update(state.copyWith(quote: quote, dayQuote: day, loading: false));
      if (range == TickerRange.day && instrument == TickerInstrument.auto) {
        onDayQuote?.call(quote);
      }
    } on TickerException {
      if (_stale(generation)) return;
      update(state.copyWith(loading: false, failed: state.quote == null));
    }
  }

  Future<TickerQuote> _fetch(TickerRange range, TickerInstrument instrument) {
    return client.fetchQuote(
      symbol,
      range: range.range,
      interval: range.interval,
      includePrePost: range == TickerRange.day,
      prefer: instrument,
    );
  }

  Future<TickerQuote?> _dayOrNull(TickerInstrument instrument) async {
    try {
      return await _fetch(TickerRange.day, instrument);
    } on TickerException {
      return null;
    }
  }

  Future<void> selectRange(TickerRange range) async {
    if (range == state.range) return;
    update(state.copyWith(range: range, quote: null, scrubbed: null));
    await load();
  }

  /// Switching between the coin and the listed security is a different
  /// instrument altogether, so today's figures go too.
  Future<void> selectInstrument(TickerInstrument instrument) async {
    if (instrument == state.instrument) return;
    update(
      state.copyWith(
        instrument: instrument,
        quote: null,
        dayQuote: null,
        scrubbed: null,
      ),
    );
    await load();
  }

  void scrub(TickerPoint? point) => update(state.copyWith(scrubbed: point));

  /// Headlines are extra; failing to get them changes nothing on screen.
  Future<void> loadNews() async {
    try {
      final news = await client.fetchNews(symbol);
      if (!_closed) update(state.copyWith(news: news));
    } on TickerException {
      return;
    }
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
