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

    test('an index name asks for its index and nothing else', () {
      // $NDX once fell through to NDX-USD, a token, shown as the Nasdaq-100.
      for (final MapEntry(key: spoken, value: index)
          in kTickerAliases.entries) {
        for (final prefer in TickerInstrument.values) {
          expect(
            tickerCandidates(spoken.toLowerCase(), prefer: prefer),
            [index],
            reason: '$spoken, $prefer',
          );
        }
      }
      expect(tickerCandidates('NDX'), ['^NDX']);
    });
  });

  group('what a symbol is before anything is asked', () {
    test('index names and ^ symbols are indices', () {
      for (final symbol in ['SPX', 'ndx', 'DAX', 'N225', '^GSPC']) {
        expect(knownTickerKind(symbol), TickerKind.marketIndex, reason: symbol);
      }
    });

    test('coins the tape files as pairs are crypto', () {
      for (final symbol in ['BTC', 'eth', 'SHIB-USD']) {
        expect(knownTickerKind(symbol), TickerKind.crypto, reason: symbol);
      }
    });

    test('anything else is for the price service to say', () {
      expect(knownTickerKind('AAPL'), isNull);
      expect(knownTickerKind('POD'), isNull);
    });

    test('indices and the tape coins have names without any data', () {
      expect(
        [for (final symbol in kMarketIndexSymbols) tickerName(symbol)],
        [
          'S&P 500',
          'Nasdaq-100',
          'Dow Jones',
          'Russell 2000',
          'VIX',
          'DAX 40',
          'FTSE 100',
          'Nikkei 225',
          'Bitcoin',
          'Ethereum',
        ],
      );
      expect(tickerName('^GSPC'), 'S&P 500', reason: 'the host spelling too');
      expect(tickerName('AAPL'), isNull);
    });
  });
}
