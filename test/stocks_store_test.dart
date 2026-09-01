import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';

void main() {
  group('normaliseTicker', () {
    test('accepts what a reader is likely to type', () {
      expect(StocksWatchlistStore.normaliseTicker(r'$aapl'), 'AAPL');
      expect(StocksWatchlistStore.normaliseTicker(' msft '), 'MSFT');
      expect(StocksWatchlistStore.normaliseTicker('BRK.B'), 'BRK.B');
      expect(StocksWatchlistStore.normaliseTicker('^GSPC'), '^GSPC');
    });

    test('rejects anything that is not a symbol', () {
      expect(StocksWatchlistStore.normaliseTicker(''), isNull);
      expect(StocksWatchlistStore.normaliseTicker('a very long symbol name'), isNull);
      expect(StocksWatchlistStore.normaliseTicker('AA PL'), isNull);
    });
  });
}
