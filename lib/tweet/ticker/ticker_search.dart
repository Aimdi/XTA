/// Hits and headlines from the public search/trending endpoints.
///
/// Same host as the chart, same rule: every field is optional and a payload
/// that no longer fits yields an empty list rather than throwing.
library;

import 'package:xta/utils/json.dart';

class TickerSearchHit {
  final String symbol;
  final String? name;
  final String? type;
  final String? exchange;

  const TickerSearchHit({
    required this.symbol,
    this.name,
    this.type,
    this.exchange,
  });

  /// Equity / ETF / index / crypto — the things a FinTwit watchlist wants.
  /// Depositary receipts like `AAPL01.BK` are dropped when a plain ticker
  /// already answered the same query.
  bool get isUseful {
    const types = {'EQUITY', 'ETF', 'INDEX', 'CRYPTOCURRENCY', 'MUTUALFUND'};
    if (type != null && !types.contains(type)) {
      return false;
    }
    if (RegExp(r'\d').hasMatch(symbol) && symbol.contains('.')) {
      return false;
    }
    return symbol.isNotEmpty;
  }
}

class TickerNewsItem {
  final String title;
  final String? url;
  final String? publisher;

  const TickerNewsItem({required this.title, this.url, this.publisher});
}

List<TickerSearchHit> tickerSearchHitsFromJson(Object? json) {
  final hits = <TickerSearchHit>[];
  for (final quote in Json(json)['quotes'].list) {
    final symbol = quote['symbol'].string?.trim();
    if (symbol == null || symbol.isEmpty) {
      continue;
    }
    final hit = TickerSearchHit(
      symbol: symbol.toUpperCase(),
      name: quote['shortname'].string ?? quote['longname'].string,
      type: quote['quoteType'].string,
      exchange: quote['exchDisp'].string ?? quote['exchange'].string,
    );
    if (hit.isUseful) {
      hits.add(hit);
    }
  }
  return hits;
}

List<TickerNewsItem> tickerNewsFromJson(Object? json) {
  final items = <TickerNewsItem>[];
  for (final story in Json(json)['news'].list) {
    final title = story['title'].string?.trim();
    if (title == null || title.isEmpty) {
      continue;
    }
    items.add(
      TickerNewsItem(
        title: title,
        url: story['link'].string,
        publisher: story['publisher'].string,
      ),
    );
  }
  return items;
}

List<String> tickerTrendsFromJson(Object? json) {
  final symbols = <String>[];
  for (final quote in Json(json)['finance']['result'][0]['quotes'].list) {
    final symbol = quote['symbol'].string?.trim();
    if (symbol == null || symbol.isEmpty) {
      continue;
    }
    symbols.add(symbol.toUpperCase());
  }
  return symbols;
}
