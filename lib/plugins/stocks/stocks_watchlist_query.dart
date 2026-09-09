/// Building an X search for every cashtag on the stocks watchlist.
///
/// StockTwits's home feed is posts about watched symbols; here the same idea
/// uses X's search operators so the Aktien tab reads as a social stream rather
/// than a second price list.
library;

import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

/// How many symbols to put in one search. X truncates very long OR groups, and
/// a watchlist past this size is better browsed one ticker at a time.
const int kWatchlistFeedSymbolCap = 20;

/// `($AAPL OR $TSLA)` — or a single `$AAPL` — for [symbols], uppercased and
/// capped. Empty watchlist → empty query (no search).
String watchlistCashtagQuery(Iterable<String> symbols) {
  final cashtags = <String>[];
  for (final raw in symbols) {
    final symbol = raw.trim();
    if (symbol.isEmpty) continue;
    final contract = CryptoAsset.contractForId(symbol);
    final query = contract == null ? '\$${spokenCashtag(symbol)}' : '"$contract"';
    if (!cashtags.contains(query)) cashtags.add(query);
    if (cashtags.length >= kWatchlistFeedSymbolCap) break;
  }
  if (cashtags.isEmpty) {
    return '';
  }
  return cashtags.length == 1 ? cashtags.single : '(${cashtags.join(' OR ')})';
}
