import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/intl.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/ui/contrast.dart';

/// 2025-10-10 in New York: pre 04:00, regular 09:30–16:00, post until 20:00.
const _pre = 1760083200; // 08:00 UTC
const _open = 1760103000; // 13:30 UTC
const _close = 1760126400; // 20:00 UTC
const _postEnd = 1760140800; // 24:00 UTC

DateTime _at(int seconds) => DateTime.fromMillisecondsSinceEpoch(seconds * 1000, isUtc: true);

/// The documented v8 chart shape for `range=1d&interval=5m&includePrePost=true`,
/// trimmed to the fields read.
Map<String, Object?> _dayChart({
  required List<int> timestamps,
  required List<double?> closes,
  List<double?>? opens,
  String instrumentType = 'EQUITY',
}) => {
  'chart': {
    'result': [
      {
        'meta': {
          'symbol': 'AAPL',
          'currency': 'USD',
          'instrumentType': instrumentType,
          'regularMarketPrice': 200.0,
          'chartPreviousClose': 190.0,
          'previousClose': 190.0,
          'currentTradingPeriod': {
            'pre': {'start': _pre, 'end': _open},
            'regular': {'start': _open, 'end': _close},
            'post': {'start': _close, 'end': _postEnd},
          },
        },
        'timestamp': timestamps,
        'indicators': {
          'quote': [
            {'close': closes, 'open': opens ?? closes},
          ],
        },
      },
    ],
  },
};

void main() {
  group('the session a quote is in', () {
    final quote = TickerQuote.fromChartJson(
      _dayChart(timestamps: [_open, _open + 300], closes: [195, 200]),
      symbol: 'AAPL',
    )!;

    test('follows the trading periods the chart host sends', () {
      expect(quote.sessionAt(_at(_pre + 60)), TickerSession.pre);
      expect(quote.sessionAt(_at(_open + 60)), TickerSession.regular);
      expect(quote.sessionAt(_at(_close + 60)), TickerSession.post);
      expect(quote.sessionAt(_at(_postEnd + 60)), TickerSession.closed);
      expect(quote.sessionAt(_at(_pre - 60)), TickerSession.closed);
    });

    test('a coin is never closed', () {
      final coin = TickerQuote.fromChartJson(
        _dayChart(timestamps: [_open, _open + 300], closes: [1, 2], instrumentType: 'CRYPTOCURRENCY'),
        symbol: 'BTC-USD',
      )!;
      expect(coin.sessionAt(_at(_postEnd + 3600)), TickerSession.regular);
      expect(coin.extendedPrint, isNull);
    });

    test('without periods it says nothing rather than guessing', () {
      final bare = TickerQuote.fromChartJson({
        'chart': {
          'result': [
            {
              'meta': {'regularMarketPrice': 1},
              'timestamp': [_open, _open + 60],
              'indicators': {
                'quote': [
                  {
                    'close': [1, 2],
                  },
                ],
              },
            },
          ],
        },
      }, symbol: 'X')!;
      expect(bare.sessionAt(_at(_open)), isNull);
      expect(bare.dayOpen, isNull);
    });
  });

  group('extended hours', () {
    test('a bar after the close is the after-hours print, apart from the price', () {
      final quote = TickerQuote.fromChartJson(
        _dayChart(timestamps: [_pre + 300, _open, _close - 300, _close + 600], closes: [191, 195, 200, 202]),
        symbol: 'AAPL',
      )!;
      expect(quote.displayPrice, 200, reason: 'the regular close, measured against the previous close');
      expect(quote.changePercent, closeTo(10 / 190 * 100, 1e-9));
      expect(quote.isAfterHours, isTrue);
      expect(quote.extendedPrint?.price, 202);
      expect(quote.extendedChangePercent, closeTo(1, 1e-9));
      expect(quote.dayOpen, 195, reason: 'the first open inside the regular session');
    });

    test('a bar inside today\'s pre-market is the early print', () {
      final quote = TickerQuote.fromChartJson(
        _dayChart(timestamps: [_pre + 300, _pre + 600], closes: [191, 192]),
        symbol: 'AAPL',
      )!;
      expect(quote.isPreMarket, isTrue);
      expect(quote.extendedPrint?.price, 192);
    });

    test('during the regular session there is no extended print', () {
      final quote = TickerQuote.fromChartJson(
        _dayChart(timestamps: [_open, _open + 300], closes: [195, 200]),
        symbol: 'AAPL',
      )!;
      expect(quote.extendedPrint, isNull);
    });
  });

  group('cashtags that are also coins', () {
    test('a major coin is asked for as the coin first', () {
      expect(tickerCandidates('eth'), ['ETH-USD', 'ETH']);
      expect(tickerCandidates('BTC'), ['BTC-USD', 'BTC']);
      expect(isAmbiguousTicker('sol'), isTrue);
      expect(isAmbiguousTicker('AAPL'), isFalse);
    });

    test('the reader can insist on either instrument', () {
      expect(tickerCandidates('ETH', prefer: TickerInstrument.stock), ['ETH']);
      expect(tickerCandidates('ETH', prefer: TickerInstrument.crypto), ['ETH-USD']);
      expect(tickerCandidates('SPX', prefer: TickerInstrument.stock), ['SPX', '^GSPC']);
    });

    test('ordinary symbols are unchanged', () {
      expect(tickerCandidates('AAPL'), ['AAPL', 'AAPL-USD']);
      expect(tickerCandidates('^GSPC'), ['^GSPC']);
    });
  });

  group('the shared quote cache', () {
    test('fills every symbol, not only the first few, with one-day quotes', () async {
      final asked = <Uri>[];
      final cache = TickerQuoteCache(
        client: TickerClient(
          httpClient: MockClient((request) async {
            asked.add(request.url);
            final symbol = request.url.pathSegments.last;
            final chart = _dayChart(timestamps: [_open, _open + 300], closes: [1, 2]);
            ((chart['chart'] as Map)['result'] as List).first['meta']['symbol'] = symbol;
            return http.Response(jsonEncode(chart), 200);
          }),
        ),
      );
      final symbols = ['AAPL', 'MSFT', 'NVDA', 'TSLA', 'AMZN', 'META', 'GOOG', 'NFLX', 'AMD', 'INTC'];

      await cache.ensure(symbols);

      expect(cache.state.keys.toSet(), symbols.toSet());
      expect(asked.every((uri) => uri.queryParameters['range'] == '1d'), isTrue);
      expect(asked.every((uri) => uri.queryParameters['includePrePost'] == 'true'), isTrue);

      await cache.ensure(['AAPL']);
      expect(asked, hasLength(10), reason: 'a fresh quote is not asked for again');
      await cache.refresh(['AAPL']);
      expect(asked, hasLength(11), reason: 'pull-to-refresh asks again');
      await cache.destroy();
    });
  });

  group('formatting', () {
    tearDown(() => Intl.defaultLocale = null);

    test('moves are signed, rounded to flat, and use a real minus', () {
      expect(stockPercentLabel(1.234), '+1.23%');
      expect(stockPercentLabel(-0.4), '−0.40%');
      expect(stockPercentLabel(-0.001), '0.00%');
      expect(stockChangeLabel(-2.5), '−2.50');
    });

    test('prices keep useful digits at every size', () {
      expect(stockPrice(1234.5), '1,234.50');
      expect(stockPrice(0.35121), '0.3512');
      expect(stockPrice(0.0000123), '0.0000123');
    });

    test('the reader\'s locale groups digits; one intl lacks falls back', () {
      Intl.defaultLocale = 'de';
      expect(stockPrice(1234.5), '1.234,50');
      expect(stockPercentLabel(1.5), '+1,50%');
      Intl.defaultLocale = 'eo';
      expect(stockPrice(1234.5), '1,234.50', reason: 'Esperanto has no number symbols in intl');
    });

    testWidgets('gain and loss colours stay readable and unknown is neutral', (tester) async {
      for (final theme in [
        ThemeData.light(),
        ThemeData.dark(),
        ThemeData.dark().copyWith(scaffoldBackgroundColor: Colors.black),
      ]) {
        late BuildContext captured;
        await tester.pumpWidget(
          MaterialApp(
            theme: theme,
            home: Builder(
              builder: (context) {
                captured = context;
                return const SizedBox();
              },
            ),
          ),
        );
        await tester.pumpAndSettle();
        final background = Theme.of(captured).scaffoldBackgroundColor;
        expect(contrastRatio(stockTrendColour(captured, 2), background), greaterThanOrEqualTo(4.5));
        expect(contrastRatio(stockTrendColour(captured, -2), background), greaterThanOrEqualTo(4.5));
        expect(stockTrendColour(captured, null), theme.colorScheme.onSurfaceVariant);
        expect(stockTrendColour(captured, 0.001), theme.colorScheme.onSurfaceVariant);
      }
    });
  });
}
