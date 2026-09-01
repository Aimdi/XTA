import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

/// The smallest payload the parser accepts.
String _chart(String symbol) => jsonEncode({
  'chart': {
    'result': [
      {
        'meta': {
          'symbol': symbol,
          'currency': 'USD',
          'regularMarketPrice': 20.73,
        },
        'timestamp': [1767225600, 1767312000],
        'indicators': {
          'quote': [
            {
              'close': [19.0, 20.73],
            },
          ],
        },
      },
    ],
  },
});

void main() {
  group('the names a cashtag is tried under', () {
    test('an ordinary share is asked for as written, then as a pair', () {
      expect(TickerClient.candidatesFor('aapl'), ['AAPL', 'AAPL-USD']);
    });

    test('an index is asked for under the name the service quotes it by', () {
      expect(TickerClient.candidatesFor('SPX'), contains('^GSPC'));
      expect(TickerClient.candidatesFor('DAX'), contains('^GDAXI'));
    });

    test('a name that is already a pair or an index is not mangled', () {
      expect(TickerClient.candidatesFor('BTC-USD'), ['BTC-USD']);
      expect(TickerClient.candidatesFor('^GSPC'), ['^GSPC']);
    });
  });

  group('fetching', () {
    test(
      'falls through to the pair form when the bare name is unknown',
      () async {
        final asked = <String>[];
        final client = TickerClient(
          httpClient: MockClient((request) async {
            asked.add(request.url.pathSegments.last);
            if (request.url.path.endsWith('BTC-USD')) {
              return http.Response(_chart('BTC-USD'), 200);
            }
            return http.Response('no', 404);
          }),
        );

        final quote = await client.fetchQuote('BTC');

        expect(quote.symbol, 'BTC-USD');
        expect(asked, ['BTC', 'BTC-USD']);
      },
    );

    test('a network failure is not retried under other names', () async {
      var calls = 0;
      final client = TickerClient(
        httpClient: MockClient((_) async {
          calls++;
          throw http.ClientException('no route');
        }),
      );

      await expectLater(
        client.fetchQuote('BTC'),
        throwsA(
          isA<TickerException>().having(
            (e) => e.kind,
            'kind',
            TickerErrorKind.unavailable,
          ),
        ),
      );
      expect(calls, 1, reason: 'the name was never the problem');
    });

    test('a name nothing answers for reports not-found', () async {
      final client = TickerClient(
        httpClient: MockClient((_) async => http.Response('no', 404)),
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

    test('the range and interval reach the request', () {
      final uri = TickerClient.chartUri('AAPL', range: '5d', interval: '30m');

      expect(uri.queryParameters['range'], '5d');
      expect(uri.queryParameters['interval'], '30m');
    });

    test('search and trending go to the public host', () {
      final search = TickerClient.searchUri('apple', quotes: 8, news: 2);
      expect(search.path, '/v1/finance/search');
      expect(search.queryParameters['q'], 'apple');
      expect(search.queryParameters['newsCount'], '2');

      expect(TickerClient.trendingUri().path, '/v1/finance/trending/US');
    });

    test('trending symbols are the ones the host listed', () async {
      final client = TickerClient(
        httpClient: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'finance': {
                'result': [
                  {
                    'quotes': [
                      {'symbol': 'NVDA'},
                      {'symbol': 'TSLA'},
                    ],
                  },
                ],
              },
            }),
            200,
          ),
        ),
      );

      expect(await client.fetchTrending(), ['NVDA', 'TSLA']);
    });
  });
}
