import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/ticker/ticker_search.dart';

void main() {
  group('search hits', () {
    test('keeps a useful equity and drops a numbered depositary receipt', () {
      final hits = tickerSearchHitsFromJson({
        'quotes': [
          {
            'symbol': 'AAPL',
            'shortname': 'Apple Inc.',
            'quoteType': 'EQUITY',
            'exchDisp': 'NASDAQ',
          },
          {
            'symbol': 'AAPL01.BK',
            'shortname': 'AAPL DR',
            'quoteType': 'EQUITY',
            'exchDisp': 'SET',
          },
          {'symbol': 'EURUSD=X', 'quoteType': 'CURRENCY'},
        ],
      });

      expect(hits.map((h) => h.symbol), ['AAPL']);
      expect(hits.single.name, 'Apple Inc.');
    });

    test('a reshaped payload is an empty list, not a throw', () {
      expect(tickerSearchHitsFromJson(null), isEmpty);
      expect(tickerSearchHitsFromJson('nope'), isEmpty);
      expect(tickerSearchHitsFromJson({'quotes': 'nope'}), isEmpty);
    });
  });

  group('news', () {
    test('keeps stories that have a title', () {
      final items = tickerNewsFromJson({
        'news': [
          {
            'title': 'Apple builds in Texas',
            'link': 'https://example.com/a',
            'publisher': 'WSJ',
          },
          {'title': '  ', 'link': 'https://example.com/b'},
          {'link': 'https://example.com/c'},
        ],
      });

      expect(items, hasLength(1));
      expect(items.single.publisher, 'WSJ');
    });
  });

  group('trending', () {
    test('reads the symbols the host lists', () {
      final symbols = tickerTrendsFromJson({
        'finance': {
          'result': [
            {
              'quotes': [
                {'symbol': 'NVDA'},
                {'symbol': 'btc-usd'},
                {'symbol': ''},
              ],
            },
          ],
        },
      });

      expect(symbols, ['NVDA', 'BTC-USD']);
    });

    test('a missing result is nothing to show', () {
      expect(tickerTrendsFromJson({'finance': {}}), isEmpty);
    });
  });
}
