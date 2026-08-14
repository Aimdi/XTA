import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';

Map<String, dynamic> _chart({
  List<Object?>? timestamps,
  List<Object?>? closes,
  Map<String, Object?> meta = const {'symbol': 'AAPL', 'currency': 'USD'},
}) {
  return {
    'chart': {
      'result': [
        {
          'meta': meta,
          'timestamp': timestamps ?? [1767225600, 1767312000, 1767398400],
          'indicators': {
            'quote': [
              {
                'close': closes ?? [100.0, 110.0, 120.0],
              },
            ],
          },
        },
      ],
      'error': null,
    },
  };
}

void main() {
  group('reading a price history', () {
    test('takes the closes and their dates in order', () {
      final quote = TickerQuote.fromChartJson(_chart(), symbol: 'AAPL')!;

      expect(quote.symbol, 'AAPL');
      expect(quote.currency, 'USD');
      expect(quote.points.map((p) => p.close), [100.0, 110.0, 120.0]);
      expect(quote.points.first.at.isBefore(quote.points.last.at), isTrue);
    });

    test('a day the market was shut is skipped, not counted as zero', () {
      final quote = TickerQuote.fromChartJson(
        _chart(timestamps: [1, 2, 3], closes: [100.0, null, 120.0]),
        symbol: 'AAPL',
      )!;

      expect(quote.points.map((p) => p.close), [100.0, 120.0]);
    });

    test('integers and numeric strings are both prices', () {
      final quote = TickerQuote.fromChartJson(
        _chart(closes: [100, '110.5', 120.0]),
        symbol: 'AAPL',
      )!;

      expect(quote.points.map((p) => p.close), [100.0, 110.5, 120.0]);
    });

    test('mismatched list lengths stop at the shorter one', () {
      final quote = TickerQuote.fromChartJson(
        _chart(timestamps: [1, 2, 3], closes: [1.0]),
        symbol: 'AAPL',
      )!;

      expect(quote.points, hasLength(1));
    });
  });

  group('the day\'s move', () {
    test('is measured against the previous close', () {
      final quote = TickerQuote.fromChartJson(
        _chart(
          meta: {
            'symbol': 'AAPL',
            'regularMarketPrice': 110.0,
            'chartPreviousClose': 100.0,
          },
        ),
        symbol: 'AAPL',
      )!;

      expect(quote.change, closeTo(10, 0.001));
      expect(quote.changePercent, closeTo(10, 0.001));
      expect(quote.isUp, isTrue);
    });

    test('a fall is negative and reads as down', () {
      final quote = TickerQuote.fromChartJson(
        _chart(meta: {'regularMarketPrice': 90.0, 'chartPreviousClose': 100.0}),
        symbol: 'AAPL',
      )!;

      expect(quote.changePercent, closeTo(-10, 0.001));
      expect(quote.isUp, isFalse);
    });

    test('falls back to the last close when no live price is quoted', () {
      final quote = TickerQuote.fromChartJson(
        _chart(
          closes: [100.0, 110.0, 130.0],
          meta: {'chartPreviousClose': 100.0},
        ),
        symbol: 'AAPL',
      )!;

      expect(quote.change, closeTo(30, 0.001));
    });

    test('no previous close means no move rather than a wrong one', () {
      final quote = TickerQuote.fromChartJson(
        _chart(meta: const {}),
        symbol: 'AAPL',
      )!;

      expect(quote.change, isNull);
      expect(quote.changePercent, isNull);
      expect(quote.isUp, isNull, reason: 'unknown is not the same as flat');
    });

    test('a previous close of zero does not divide by it', () {
      final quote = TickerQuote.fromChartJson(
        _chart(meta: {'regularMarketPrice': 5.0, 'chartPreviousClose': 0.0}),
        symbol: 'AAPL',
      )!;

      expect(quote.changePercent, isNull);
    });
  });

  group('what the rest of the market page needs', () {
    test(
      'volume and the year\'s range are read when the response carries them',
      () {
        final quote = TickerQuote.fromChartJson(
          _chart(
            meta: {
              'regularMarketVolume': 48200000,
              'fiftyTwoWeekHigh': 260.1,
              'fiftyTwoWeekLow': '169.21',
              'shortName': 'Apple Inc.',
              'regularMarketDayHigh': 191.2,
              'regularMarketDayLow': 188.4,
            },
          ),
          symbol: 'AAPL',
        )!;

        expect(quote.volume, closeTo(48200000, 0.001));
        expect(quote.yearHigh, closeTo(260.1, 0.001));
        expect(quote.yearLow, closeTo(169.21, 0.001));
        expect(quote.shortName, 'Apple Inc.');
        expect(quote.dayHigh, closeTo(191.2, 0.001));
        expect(quote.dayLow, closeTo(188.4, 0.001));
      },
    );

    test('pre-market is the tape print while that session is live', () {
      final quote = TickerQuote.fromChartJson(
        _chart(
          meta: {
            'regularMarketPrice': 100.0,
            'marketState': 'PRE',
            'preMarketPrice': 101.5,
          },
        ),
        symbol: 'AAPL',
      )!;

      expect(quote.displayPrice, closeTo(101.5, 0.001));
      expect(quote.isPreMarket, isTrue);
    });

    test('a symbol quoted without them still charts', () {
      final quote = TickerQuote.fromChartJson(
        _chart(meta: const {}),
        symbol: 'AAPL',
      )!;

      expect(quote.volume, isNull);
      expect(quote.yearHigh, isNull);
      expect(quote.yearLow, isNull);
      expect(quote.points, hasLength(3));
    });
  });

  group('a payload that no longer fits', () {
    test('gives up rather than throwing', () {
      for (final json in <Object?>[
        null,
        'nonsense',
        const {},
        {'chart': 'nope'},
        {
          'chart': {'result': []},
        },
        {
          'chart': {
            'result': [
              {'meta': {}},
            ],
          },
        },
      ]) {
        expect(
          TickerQuote.fromChartJson(json, symbol: 'AAPL'),
          isNull,
          reason: '$json',
        );
      }
    });

    test('history with no usable closes is nothing to draw', () {
      expect(
        TickerQuote.fromChartJson(
          _chart(closes: [null, null, null]),
          symbol: 'AAPL',
        ),
        isNull,
      );
    });

    test('the symbol asked for is used when the response omits its own', () {
      final quote = TickerQuote.fromChartJson(
        _chart(meta: const {}),
        symbol: 'msft',
      )!;

      expect(quote.symbol, 'msft');
    });
  });

  group('the client', () {
    test('asks for the symbol upper-cased, with a range and interval', () {
      final uri = TickerClient.chartUri('aapl');

      expect(uri.host, 'query1.finance.yahoo.com');
      expect(uri.path, '/v8/finance/chart/AAPL');
      expect(uri.queryParameters['range'], '1mo');
      expect(uri.queryParameters['interval'], '1d');
    });

    test('an unknown symbol is reported as not found', () async {
      final client = TickerClient(
        httpClient: MockClient((_) async => http.Response('{}', 404)),
      );

      await expectLater(
        client.fetchQuote('NOPE'),
        throwsA(
          isA<TickerException>().having(
            (e) => e.kind,
            'kind',
            TickerErrorKind.notFound,
          ),
        ),
      );
    });

    test('a refusal is unavailable, not a broken response', () async {
      final client = TickerClient(
        httpClient: MockClient((_) async => http.Response('', 429)),
      );

      await expectLater(
        client.fetchQuote('AAPL'),
        throwsA(
          isA<TickerException>().having(
            (e) => e.kind,
            'kind',
            TickerErrorKind.unavailable,
          ),
        ),
      );
    });

    test('a 200 that is not JSON is a bad response', () async {
      final client = TickerClient(
        httpClient: MockClient((_) async => http.Response('<html>', 200)),
      );

      await expectLater(
        client.fetchQuote('AAPL'),
        throwsA(
          isA<TickerException>().having(
            (e) => e.kind,
            'kind',
            TickerErrorKind.badResponse,
          ),
        ),
      );
    });

    test('a good response becomes a quote, and carries an agent', () async {
      late http.Request sent;
      final client = TickerClient(
        httpClient: MockClient((request) async {
          sent = request;
          return http.Response(jsonEncode(_chart()), 200);
        }),
      );

      final quote = await client.fetchQuote('AAPL');

      expect(quote.points, hasLength(3));
      expect(sent.headers['User-Agent'], TickerClient.userAgent);
    });
  });
}
