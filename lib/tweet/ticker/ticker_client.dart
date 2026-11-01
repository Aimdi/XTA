import 'package:http/http.dart' as http;
import 'package:xta/plugins/plugin_repairs.dart';
import 'package:xta/tweet/ticker/coin_sources.dart';
import 'package:xta/tweet/ticker/price_http.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_search.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

export 'package:xta/tweet/ticker/price_http.dart' show TickerErrorKind, TickerException;

/// Price history, search, trending, and headlines for a symbol.
///
/// The endpoints are public and need no key or account, which is the only
/// reason they are here: a chart is not worth handing anyone a login for. Like
/// X's, they are undocumented, so parsers treat every field as optional and
/// this class turns a refusal into a typed error rather than letting a screen
/// show a stack trace.
///
/// A coin (`BTC-USD`) is asked of the exchanges in [CoinSourceChain] first and
/// of this host last, so the stocks keep its budget; [coinSources] replaces
/// the exchanges, and an empty list leaves coins to this host alone.
class TickerClient {
  final http.Client httpClient;
  late final CoinSourceChain _coins;

  TickerClient({http.Client? httpClient, List<CoinSource>? coinSources, PriceSourceHealth? health})
    : httpClient = httpClient ?? http.Client() {
    _coins = CoinSourceChain(coinSources ?? _exchanges(), health: health);
  }

  List<CoinSource> _exchanges() => [
    CoinbaseSource(httpClient, userAgent: userAgent),
    KrakenSource(httpClient, userAgent: userAgent),
    BinanceSource(httpClient, userAgent: userAgent),
  ];

  static const _host = 'query1.finance.yahoo.com';
  static const _timeout = Duration(seconds: 15);

  /// A browser-ish agent: the host refuses an empty one outright.
  static String get userAgent => PluginRepairs.stocksUserAgent.value;

  /// Every name to try for a cashtag, in order.
  static List<String> candidatesFor(String symbol) => tickerCandidates(symbol);

  /// [includePrePost] adds the pre-market and after-hours bars, which is how
  /// an extended-hours print reaches a tape that only has the chart host.
  ///
  /// The symbol is a path segment, encoded once: `^NDX` goes out as `%5ENDX`.
  /// Encoding it by hand before handing it to [Uri.https] sent `%255ENDX`,
  /// which names no symbol, so every index failed and fell through to a
  /// `-USD` token of the same letters.
  static Uri chartUri(String symbol, {String range = '1mo', String interval = '1d', bool includePrePost = false}) {
    return Uri(
      scheme: 'https',
      host: _host,
      pathSegments: ['v8', 'finance', 'chart', symbol.toUpperCase()],
      queryParameters: {'range': range, 'interval': interval, if (includePrePost) 'includePrePost': 'true'},
    );
  }

  static Uri searchUri(String query, {int quotes = 8, int news = 0}) {
    return Uri.https(_host, '/v1/finance/search', {'q': query, 'quotesCount': '$quotes', 'newsCount': '$news'});
  }

  static Uri trendingUri({String region = 'US'}) {
    return Uri(scheme: 'https', host: _host, pathSegments: ['v1', 'finance', 'trending', region]);
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
    bool includePrePost = false,
    TickerInstrument prefer = TickerInstrument.auto,
  }) async {
    TickerException? last;

    for (final candidate in tickerCandidates(symbol, prefer: prefer)) {
      try {
        final uri = chartUri(candidate, range: range, interval: interval, includePrePost: includePrePost);
        final window = CoinSourceChain.isCoinPair(candidate) ? ChartWindow.parse(range, interval) : null;
        return window == null
            ? await _fetchOne(candidate, uri)
            : await _coins.chart(candidate, window, yahoo: () => _fetchOne(candidate, uri));
      } on TickerException catch (e) {
        last = e;
        if (e.kind == TickerErrorKind.unavailable) {
          rethrow;
        }
      }
    }

    throw last ?? TickerException(TickerErrorKind.notFound, 'No such symbol: $symbol');
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

  Future<TickerQuote> _fetchOne(String symbol, Uri uri) async {
    final decoded = await _getJson(uri);
    final quote = TickerQuote.fromChartJson(decoded, symbol: symbol);
    if (quote == null) {
      throw TickerException(TickerErrorKind.badResponse, 'No price history in the response for $symbol');
    }
    return quote;
  }

  Future<Object?> _getJson(Uri uri) => fetchPriceJson(httpClient, uri, userAgent: userAgent, timeout: _timeout);
}
