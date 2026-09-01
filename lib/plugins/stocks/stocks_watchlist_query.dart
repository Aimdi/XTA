/// Building an X search for every cashtag on the stocks watchlist.
///
/// StockTwits's home feed is posts about watched symbols; here the same idea
/// uses X's search operators so the Aktien tab reads as a social stream rather
/// than a second price list.
library;

/// How many symbols to put in one search. X truncates very long OR groups, and
/// a watchlist past this size is better browsed one ticker at a time.
const int kWatchlistFeedSymbolCap = 20;

/// `($AAPL OR $TSLA)` — or a single `$AAPL` — for [symbols], uppercased and
/// capped. Empty watchlist → empty query (no search).
String watchlistCashtagQuery(Iterable<String> symbols) {
  final cashtags = [
    for (final symbol in symbols.take(kWatchlistFeedSymbolCap))
      if (symbol.trim().isNotEmpty) '\$${symbol.trim().toUpperCase()}',
  ];
  if (cashtags.isEmpty) {
    return '';
  }
  return cashtags.length == 1 ? cashtags.single : '(${cashtags.join(' OR ')})';
}
