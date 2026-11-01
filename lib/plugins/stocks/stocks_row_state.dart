import 'package:flutter/widgets.dart';
import 'package:intl/intl.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/utils/formatting_locale.dart';

/// What a quote row or chip shows where its figures go. A dash used to stand
/// for the first three alike.
enum StockRowState {
  /// No answer yet: a shimmer where the price and its move will be.
  loading,

  /// A price during a session, regular or extended.
  live,

  /// The last close while the exchange has no session (night, weekend,
  /// holiday), with the day of that close and a muted move.
  closed,

  /// The lookup found nothing to show.
  noData,
}

/// The state of a row showing [quote]; [failed] is whether its last lookup
/// found nothing. A quote without a price has nothing to show either.
StockRowState stockRowState(TickerQuote? quote, {required bool failed, required DateTime now}) {
  if (quote?.displayPrice == null) {
    return failed || quote != null ? StockRowState.noData : StockRowState.loading;
  }
  return quote!.sessionAt(now) == TickerSession.closed ? StockRowState.closed : StockRowState.live;
}

/// "Close Fri": the day of the close a closed market shows, or its date when
/// that close is a week old. "Market closed" when the quote does not say.
String stockClosedTag(BuildContext context, TickerQuote quote, {required DateTime now}) {
  final l10n = L10n.of(context);
  final at = quote.regularMarketTime ?? quote.points.lastOrNull?.at;
  if (at == null) return l10n.plugin_stocks_market_closed;
  final locale = formattingLocale(context);
  final day = now.difference(at) < const Duration(days: 6) ? DateFormat.E(locale) : DateFormat.MMMd(locale);
  return l10n.plugin_stocks_closed_on(day.format(at));
}

/// The caption beside a move: "24h" over a rolling day, "1D" against the
/// previous session's close.
String stockWindowLabel(L10n l10n, TickerChangeWindow window) => switch (window) {
  TickerChangeWindow.rolling24h => l10n.plugin_stocks_window_24h,
  TickerChangeWindow.previousClose => l10n.ticker_range_day,
};

/// What [symbol]'s row says it is: the quote's own word when it has one,
/// else what the symbol is known to be before anything is asked, unless the
/// reader picked the listed security behind a coin's letters ([reading]).
TickerKind? stockKindOf(String symbol, TickerQuote? quote, {TickerInstrument reading = TickerInstrument.auto}) =>
    quote?.kind ?? (reading == TickerInstrument.stock ? TickerKind.stock : knownTickerKind(symbol));

/// The name over [symbol]'s row: an index's or a coin's own name, else the
/// quote's. A picked listing ([reading]) goes by the quote's alone: `$BTC`
/// read as the listed fund is not "Bitcoin".
String? stockRowName(String symbol, TickerQuote? quote, {TickerInstrument reading = TickerInstrument.auto}) =>
    reading == TickerInstrument.stock ? quote?.shortName : tickerName(symbol) ?? quote?.shortName;

/// The word for what an instrument is: Index, Aktie, Krypto, …
String? stockKindWord(L10n l10n, TickerKind? kind) => switch (kind) {
  null => null,
  TickerKind.marketIndex => l10n.plugin_stocks_type_index,
  TickerKind.stock => l10n.plugin_stocks_type_stock,
  TickerKind.etf => l10n.plugin_stocks_type_etf,
  TickerKind.crypto => l10n.plugin_stocks_type_crypto,
  TickerKind.fund => l10n.plugin_stocks_type_fund,
  TickerKind.future => l10n.plugin_stocks_type_future,
  TickerKind.currency => l10n.plugin_stocks_type_currency,
};

/// A row's second line under its name: `$SPX · Index`. A row titled by its
/// symbol already shows the cashtag, so only the kind follows.
String? stockSubtitle(L10n l10n, String symbol, TickerKind? kind, {required bool named}) {
  final parts = [if (named) '\$${spokenCashtag(symbol)}', ?stockKindWord(l10n, kind)];
  return parts.isEmpty ? null : parts.join(' · ');
}

/// What a Märkte section says about the markets in it.
enum StocksSectionKind {
  /// Nothing has answered yet.
  unknown,

  /// Coins: trading now, moves over 24 hours.
  live24h,

  /// At least one exchange in the section trades now.
  liveDay,

  /// None trades now: the rows show their last closes.
  closed,
}

/// The state of a section showing [quotes] at [now], and for a closed one the
/// time of its newest close.
({StocksSectionKind kind, DateTime? lastClose}) stocksSectionState(
  Iterable<TickerQuote?> quotes, {
  required DateTime now,
}) {
  final known = [for (final quote in quotes) if (quote?.displayPrice != null) quote!];
  if (known.isEmpty) return (kind: StocksSectionKind.unknown, lastClose: null);
  if (known.every((quote) => quote.tradesAroundTheClock)) return (kind: StocksSectionKind.live24h, lastClose: null);
  if (known.any((quote) => quote.sessionAt(now) != TickerSession.closed)) {
    return (kind: StocksSectionKind.liveDay, lastClose: null);
  }
  final closes = [for (final quote in known) ?quote.regularMarketTime];
  final newest = closes.isEmpty ? null : closes.reduce((a, b) => a.isAfter(b) ? a : b);
  return (kind: StocksSectionKind.closed, lastClose: newest);
}

/// A section's state as its header says it, or null while nothing answered.
String? stocksSectionStatus(BuildContext context, ({StocksSectionKind kind, DateTime? lastClose}) section) {
  final l10n = L10n.of(context);
  return switch (section) {
    (kind: StocksSectionKind.unknown, lastClose: _) => null,
    (kind: StocksSectionKind.live24h, lastClose: _) => l10n.plugin_stocks_status_live_24h,
    (kind: StocksSectionKind.liveDay, lastClose: _) => l10n.plugin_stocks_status_live_day,
    (kind: StocksSectionKind.closed, lastClose: final close?) => l10n.plugin_stocks_status_closed(
      DateFormat.MEd(formattingLocale(context)).format(close),
    ),
    (kind: StocksSectionKind.closed, lastClose: null) => l10n.plugin_stocks_market_closed,
  };
}
