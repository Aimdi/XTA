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
/// capped. A token is searched by its quoted contract address, plus its
/// cashtag when the reader opted in on [assets]. Empty watchlist → empty query.
String watchlistCashtagQuery(Iterable<String> symbols, {Map<String, CryptoAsset> assets = const {}}) {
  final terms = <String>[];
  for (final raw in symbols) {
    final symbol = raw.trim();
    if (symbol.isEmpty) continue;
    for (final term in _termsFor(symbol, assets[symbol])) {
      if (!terms.contains(term)) terms.add(term);
    }
    if (terms.length >= kWatchlistFeedSymbolCap) break;
  }
  if (terms.isEmpty) {
    return '';
  }
  return terms.length == 1 ? terms.single : '(${terms.join(' OR ')})';
}

List<String> _termsFor(String symbol, CryptoAsset? asset) {
  if (asset != null) return asset.searchTerms;
  final contract = CryptoAsset.contractForId(symbol);
  return [contract == null ? '\$${spokenCashtag(symbol)}' : '"$contract"'];
}
