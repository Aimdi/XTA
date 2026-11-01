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
/// else what the symbol is known to be before anything is asked.
TickerKind? stockKindOf(String symbol, TickerQuote? quote) => quote?.kind ?? knownTickerKind(symbol);

/// A row's badge: what the instrument is and, for a token, its network
/// ("Crypto · Base"). Null when nothing says.
String? stockBadge(L10n l10n, TickerKind? kind, {String? network}) {
  if (kind == null) return null;
  final word = switch (kind) {
    TickerKind.marketIndex => l10n.plugin_stocks_type_index,
    TickerKind.stock => l10n.plugin_stocks_type_stock,
    TickerKind.etf => l10n.plugin_stocks_type_etf,
    TickerKind.crypto => l10n.plugin_stocks_type_crypto,
    TickerKind.fund => l10n.plugin_stocks_type_fund,
    TickerKind.future => l10n.plugin_stocks_type_future,
    TickerKind.currency => l10n.plugin_stocks_type_currency,
  };
  return network == null ? word : '$word · $network';
}
