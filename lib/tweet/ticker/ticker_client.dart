import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_search.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

enum TickerErrorKind { notFound, unavailable, badResponse }

class TickerException implements Exception {
  final TickerErrorKind kind;
  final String message;

  TickerException(this.kind, this.message);

  @override
  String toString() => 'TickerException{$kind: $message}';
}

/// Price history, search, trending, and headlines for a symbol.
///
/// The endpoints are public and need no key or account, which is the only
/// reason they are here: a chart is not worth handing anyone a login for. Like
/// X's, they are undocumented, so parsers treat every field as optional and
/// this class turns a refusal into a typed error rather than letting a screen
/// show a stack trace.
class TickerClient {
  final http.Client httpClient;

  TickerClient({http.Client? httpClient})
    : httpClient = httpClient ?? http.Client();

  static const _host = 'query1.finance.yahoo.com';
  static const _timeout = Duration(seconds: 15);

  /// A browser-ish agent: the host refuses an empty one outright.
  static const userAgent = 'Mozilla/5.0 (Android) XTA';

  /// Every name to try for a cashtag, in order.
  static List<String> candidatesFor(String symbol) => tickerCandidates(symbol);

  static Uri chartUri(
    String symbol, {
    String range = '1mo',
    String interval = '1d',
  }) {
    return Uri.https(
      _host,
      '/v8/finance/chart/${Uri.encodeComponent(symbol.toUpperCase())}',
      {'range': range, 'interval': interval},
    );
  }

  static Uri searchUri(String query, {int quotes = 8, int news = 0}) {
    return Uri.https(_host, '/v1/finance/search', {
      'q': query,
      'quotesCount': '$quotes',
      'newsCount': '$news',
    });
  }

  static Uri trendingUri({String region = 'US'}) {
    return Uri.https(
      _host,
      '/v1/finance/trending/${Uri.encodeComponent(region)}',
    );
  }

  /// The first name that answers with a price history.
  ///
  /// Only a "there is no such symbol" answer moves on to the next candidate: a
  /// timeout or a refusal says nothing about the name, and trying two more
  /// would just make the reader wait three times as long for the same failure.
  Future<TickerQuote> fetchQuote(
    String symbol, {
    String range = '1mo',
    String interval = '1d',
  }) async {
    TickerException? last;

    for (final candidate in candidatesFor(symbol)) {
      try {
        return await _fetchOne(candidate, range: range, interval: interval);
      } on TickerException catch (e) {
        last = e;
        if (e.kind == TickerErrorKind.unavailable) {
          rethrow;
        }
      }
    }

    throw last ??
        TickerException(TickerErrorKind.notFound, 'No such symbol: $symbol');
  }

  Future<List<TickerSearchHit>> searchSymbols(String query) async {
    final decoded = await _getJson(searchUri(query, quotes: 8, news: 0));
    return tickerSearchHitsFromJson(decoded);
  }

  Future<List<TickerNewsItem>> fetchNews(String symbol) async {
    final decoded = await _getJson(searchUri(symbol, quotes: 0, news: 6));
    return tickerNewsFromJson(decoded);
  }

  Future<List<String>> fetchTrending({String region = 'US'}) async {
    final decoded = await _getJson(trendingUri(region: region));
    return tickerTrendsFromJson(decoded);
  }

  Future<TickerQuote> _fetchOne(
    String symbol, {
    required String range,
    required String interval,
  }) async {
    final decoded = await _getJson(
      chartUri(symbol, range: range, interval: interval),
    );
    final quote = TickerQuote.fromChartJson(decoded, symbol: symbol);
    if (quote == null) {
      throw TickerException(
        TickerErrorKind.badResponse,
        'No price history in the response for $symbol',
      );
    }
    return quote;
  }

  Future<Object?> _getJson(Uri uri) async {
    late http.Response response;
    try {
      response = await httpClient
          .get(uri, headers: {'User-Agent': userAgent})
          .timeout(_timeout);
    } catch (e) {
      throw TickerException(
        TickerErrorKind.unavailable,
        'Could not reach the price service: $e',
      );
    }

    if (response.statusCode == 404) {
      throw TickerException(
        TickerErrorKind.notFound,
        'No such symbol: ${uri.path}',
      );
    }
    if (response.statusCode != 200) {
      throw TickerException(
        TickerErrorKind.unavailable,
        'HTTP ${response.statusCode} for ${uri.path}',
      );
    }

    try {
      return jsonDecode(response.body);
    } catch (e) {
      throw TickerException(
        TickerErrorKind.badResponse,
        'Response was not JSON: $e',
      );
    }
  }
}
