import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_query.dart';

void main() {
  group('watchlistCashtagQuery', () {
    test('one symbol is a bare cashtag', () {
      expect(watchlistCashtagQuery(['aapl']), r'$AAPL');
    });

    test('several symbols become an OR group', () {
      expect(watchlistCashtagQuery(['AAPL', 'TSLA']), r'($AAPL OR $TSLA)');
    });

    test('an empty watchlist is no query', () {
      expect(watchlistCashtagQuery(const []), '');
    });

    test('the feed caps how many symbols share one search', () {
      final many = List.generate(kWatchlistFeedSymbolCap + 5, (i) => 'T$i');
      final query = watchlistCashtagQuery(many);
      expect('\$T$kWatchlistFeedSymbolCap'.allMatches(query), isEmpty);
      expect(query, contains(r'$T0'));
    });
  });
}
