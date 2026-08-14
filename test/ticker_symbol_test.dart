import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

void main() {
  group('spokenCashtag', () {
    test('an ordinary share stays as written', () {
      expect(spokenCashtag('aapl'), 'AAPL');
    });

    test('an index is the name FinTwit uses', () {
      expect(spokenCashtag('^GSPC'), 'SPX');
      expect(spokenCashtag('^GDAXI'), 'DAX');
    });

    test('a crypto pair drops the quote currency', () {
      expect(spokenCashtag('BTC-USD'), 'BTC');
      expect(spokenCashtag('eth-usd'), 'ETH');
    });
  });

  group('tickerCandidates', () {
    test('an ordinary share is asked for as written, then as a pair', () {
      expect(tickerCandidates('aapl'), ['AAPL', 'AAPL-USD']);
    });

    test('an index is asked for under the name the service quotes it by', () {
      expect(tickerCandidates('SPX'), contains('^GSPC'));
      expect(tickerCandidates('DAX'), contains('^GDAXI'));
    });
  });
}
