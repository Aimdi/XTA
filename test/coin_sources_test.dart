import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/tweet/ticker/coin_sources.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';

final _start = DateTime.utc(2026, 10, 30);

/// [count] bars [step] apart from [_start], each closing one higher than the
/// last and opening half a unit under its close.
List<CoinBar> _bars(int count, Duration step) => [
  for (var i = 0; i < count; i++)
    (at: _start.add(step * i), open: 99.5 + i, high: 101.0 + i, low: 99.0 + i, close: 100.0 + i),
];

int _seconds(DateTime at) => at.millisecondsSinceEpoch ~/ 1000;

ChartWindow _window(TickerRange range) => ChartWindow.parse(range.range, range.interval)!;

/// A source that answers from [answer] and remembers what it was asked.
class _Source implements CoinSource {
  @override
  final String name;
  final Duration? step;
  final Future<TickerQuote> Function(String coin) answer;
  final asked = <String>[];

  _Source(this.name, this.answer, {this.step = const Duration(minutes: 30)});

  @override
  Duration? stepFor(ChartWindow window) => step;

  @override
  Future<TickerQuote> chart(String coin, ChartWindow window) {
    asked.add(coin);
    return answer(coin);
  }
}

TickerQuote _quote(String source) =>
    TickerQuote(symbol: 'BTC-USD', currency: source, price: 1, previousClose: 1, points: const []);

Future<TickerQuote> _refuse(String _) async => throw TickerException(TickerErrorKind.unavailable, '429');
Future<TickerQuote> _unknown(String _) async => throw TickerException(TickerErrorKind.notFound, 'no pair');

void main() {
  group('chart windows', () {
    test("read the chart ranges the app asks for", () {
      expect(_window(TickerRange.day).span, const Duration(days: 1));
      expect(_window(TickerRange.day).step, const Duration(minutes: 5));
      expect(_window(TickerRange.week).span, const Duration(days: 5));
      expect(_window(TickerRange.week).step, const Duration(minutes: 30));
      expect(_window(TickerRange.month).span, const Duration(days: 30));
      expect(_window(TickerRange.sixMonths).span, const Duration(days: 180));
      expect(_window(TickerRange.year).span, const Duration(days: 365));
      expect(_window(TickerRange.fiveYears).step, const Duration(days: 7));
      expect(ChartWindow.parse('max', '1d'), isNull);
    });

    test('each exchange answers only what it covers in one answer', () {
      final client = MockClient((_) async => http.Response('', 500));
      final coinbase = CoinbaseSource(client, userAgent: 'test');
      final kraken = KrakenSource(client, userAgent: 'test');
      final binance = BinanceSource(client, userAgent: 'test');
      expect(coinbase.stepFor(_window(TickerRange.day)), const Duration(minutes: 5));
      expect(coinbase.stepFor(_window(TickerRange.week)), const Duration(hours: 1), reason: 'no 30-minute candles');
      expect(coinbase.stepFor(_window(TickerRange.sixMonths)), const Duration(days: 1));
      expect(coinbase.stepFor(_window(TickerRange.year)), isNull, reason: '365 days are more than 300 candles');
      expect(kraken.stepFor(_window(TickerRange.week)), const Duration(minutes: 30));
      expect(kraken.stepFor(_window(TickerRange.fiveYears)), const Duration(days: 7));
      expect(binance.stepFor(_window(TickerRange.fiveYears)), const Duration(days: 7));
    });
  });

  group('each exchange', () {
    test('Coinbase is asked for the USD product in seconds, newest candles first', () {
      final uri = CoinbaseSource.candlesUri('BTC', const Duration(minutes: 5));
      expect(uri.toString(), 'https://api.exchange.coinbase.com/products/BTC-USD/candles?granularity=300');
      final json = [
        for (final bar in _bars(3, const Duration(minutes: 5)).reversed)
          [_seconds(bar.at), bar.low, bar.high, bar.open, bar.close, 12.5],
      ];
      final bars = coinbaseBars(jsonDecode(jsonEncode(json)));
      final quote = coinQuoteFromBars('BTC-USD', bars, span: const Duration(days: 1), currency: 'USD');
      expect([for (final point in quote.points) point.close], [100, 101, 102]);
      expect(quote.price, 102);
      expect(quote.previousClose, 99.5, reason: 'the open of the first bar, where the chart began');
      expect(quote.tradesAroundTheClock, isTrue);
      expect(quote.shortName, 'Bitcoin');
    });

    test("Kraken is asked under its own names, and an unknown pair is not found", () {
      expect(KrakenSource.ohlcUri('BTC', const Duration(minutes: 30)).queryParameters, {
        'pair': 'XBTUSD',
        'interval': '30',
      });
      expect(KrakenSource.ohlcUri('DOGE', const Duration(days: 1)).queryParameters['pair'], 'XDGUSD');
      expect(KrakenSource.ohlcUri('SOL', const Duration(days: 1)).queryParameters['pair'], 'SOLUSD');
      final json = {
        'error': <String>[],
        'result': {
          'XXBTZUSD': [
            for (final bar in _bars(2, const Duration(minutes: 30)))
              [_seconds(bar.at), '${bar.open}', '${bar.high}', '${bar.low}', '${bar.close}', '0', '3.2', 40],
          ],
          'last': 1761811200,
        },
      };
      expect([for (final bar in krakenBars(json)) bar.close], [100, 101]);
      expect(
        () => krakenBars({
          'error': ['EQuery:Unknown asset pair'],
        }),
        throwsA(isA<TickerException>().having((e) => e.kind, 'kind', TickerErrorKind.notFound)),
      );
      expect(
        () => krakenBars({
          'error': ['EGeneral:Too many requests'],
        }),
        throwsA(isA<TickerException>().having((e) => e.kind, 'kind', TickerErrorKind.unavailable)),
      );
    });

    test('Binance is asked for the USDT pair with as many klines as the span needs', () {
      final week = _window(TickerRange.week);
      final uri = BinanceSource.klinesUri('ETH', const Duration(minutes: 30), week);
      expect(uri.queryParameters, {'symbol': 'ETHUSDT', 'interval': '30m', 'limit': '241'});
      final json = [
        for (final bar in _bars(2, const Duration(minutes: 30)))
          [bar.at.millisecondsSinceEpoch, '${bar.open}', '${bar.high}', '${bar.low}', '${bar.close}', '1', 0],
      ];
      expect([for (final bar in binanceBars(json)) bar.close], [100, 101]);
    });

    test('a chart keeps its span, and the day its figures since midnight UTC', () {
      final bars = _bars(5 * 48, const Duration(minutes: 30));
      final quote = coinQuoteFromBars('ETH-USD', bars, span: const Duration(days: 5), currency: 'USD');
      expect(quote.points.first.at, bars.first.at);
      final day = quote.rolling24h();
      expect(day.changeWindow, TickerChangeWindow.rolling24h);
      expect(day.previousClose, bars[bars.length - 49].close, reason: 'the close 24 hours before the last');
      final midnight = bars.indexWhere((bar) => bar.at == DateTime.utc(2026, 11, 3));
      expect(quote.dayOpen, bars[midnight].open);
      expect(quote.dayHigh, bars.last.high);
      expect(quote.dayLow, bars[midnight].low);
    });

    test("a day's chart starts at midnight UTC, against the close before it, as on Yahoo", () {
      final bars = _bars(48, const Duration(hours: 1));
      final quote = coinQuoteFromBars('BTC-USD', bars, span: const Duration(days: 1), currency: 'USD');
      expect(quote.points.first.at, DateTime.utc(2026, 10, 31));
      expect(quote.points, hasLength(24));
      expect(quote.previousClose, bars[23].close, reason: 'the last close before midnight');
      expect(quote.changeWindow, TickerChangeWindow.previousClose);
    });
  });

  group('the chain', () {
    late DateTime now;
    late PriceSourceHealth health;

    setUp(() {
      now = DateTime.utc(2026, 11, 1, 12);
      health = PriceSourceHealth(now: () => now);
    });

    Future<TickerQuote> ask(CoinSourceChain chain, {String pair = 'BTC-USD', Future<TickerQuote> Function()? yahoo}) =>
        chain.chart(pair, _window(TickerRange.week), yahoo: yahoo ?? () async => _quote('yahoo'));

    test('the first source that answers wins, in order', () async {
      final first = _Source('first', (_) async => _quote('first'));
      final second = _Source('second', (_) async => _quote('second'));
      final quote = await ask(CoinSourceChain([first, second], health: health));
      expect(quote.currency, 'first');
      expect(second.asked, isEmpty);
    });

    test('a refusal rests the source, twice as long each time, until it answers', () async {
      var refusing = true;
      final first = _Source('first', (coin) async => refusing ? _refuse(coin) : _quote('first'));
      final second = _Source('second', (_) async => _quote('second'));
      final chain = CoinSourceChain([first, second], health: health);

      expect((await ask(chain)).currency, 'second');
      expect((await ask(chain)).currency, 'second');
      expect(first.asked, hasLength(1), reason: 'resting: not asked again');

      now = now.add(PriceSourceHealth.firstRest);
      expect((await ask(chain)).currency, 'second');
      expect(first.asked, hasLength(2));
      now = now.add(PriceSourceHealth.firstRest);
      await ask(chain);
      expect(first.asked, hasLength(2), reason: 'the second rest is twice as long');

      now = now.add(PriceSourceHealth.firstRest * 2);
      refusing = false;
      expect((await ask(chain)).currency, 'first');
      expect(health.resting('first'), isFalse);
    });

    test('an answer that is not the source\'s format rests it', () async {
      final garbled = _Source('garbled', (_) async => throw TickerException(TickerErrorKind.badResponse, 'html'));
      final second = _Source('second', (_) async => _quote('second'));
      final chain = CoinSourceChain([garbled, second], health: health);
      expect((await ask(chain)).currency, 'second');
      expect(health.resting('garbled'), isTrue);
    });

    test('a coin a source does not list is not asked of it again, other coins are', () async {
      final first = _Source('first', (coin) async => coin == 'PEPE' ? _unknown(coin) : _quote('first'));
      final second = _Source('second', (_) async => _quote('second'));
      final chain = CoinSourceChain([first, second], health: health);
      await ask(chain, pair: 'PEPE-USD');
      await ask(chain, pair: 'PEPE-USD');
      expect(first.asked, ['PEPE']);
      expect((await ask(chain)).currency, 'first');
      expect(health.resting('first'), isFalse);
    });

    test('a source that cannot cover the chart is skipped without resting', () async {
      final skipped = _Source('short', (_) async => _quote('short'), step: null);
      final chain = CoinSourceChain([skipped], health: health);
      expect((await ask(chain)).currency, 'yahoo');
      expect(skipped.asked, isEmpty);
      expect(health.resting('short'), isFalse);
    });

    test('only when everyone says there is no such coin is it not found', () async {
      final chain = CoinSourceChain([_Source('a', _unknown), _Source('b', _unknown)], health: health);
      Future<TickerQuote> yahooUnknown() async => _unknown('');
      await expectLater(
        ask(chain, pair: 'NOPE-USD', yahoo: yahooUnknown),
        throwsA(isA<TickerException>().having((e) => e.kind, 'kind', TickerErrorKind.notFound)),
      );

      final refusing = CoinSourceChain([_Source('c', _refuse)], health: health);
      await expectLater(
        ask(refusing, pair: 'NOPE-USD', yahoo: yahooUnknown),
        throwsA(isA<TickerException>().having((e) => e.kind, 'kind', TickerErrorKind.unavailable)),
        reason: 'a refusal says nothing about whether the coin exists',
      );
    });
  });

  group('the ticker client', () {
    late List<String> hosts;
    late PriceSourceHealth health;
    late TickerClient client;

    /// Coinbase refuses, Kraken lists only Bitcoin, everyone else has nothing.
    Future<http.Response> exchanges(http.Request request) async {
      hosts.add(request.url.host);
      return switch (request.url.host) {
        'api.exchange.coinbase.com' => http.Response('', 429),
        'api.kraken.com' when request.url.queryParameters['pair'] == 'XBTUSD' => http.Response(
          jsonEncode({
            'error': [],
            'result': {
              'XXBTZUSD': [
                for (final bar in _bars(10, const Duration(minutes: 30)))
                  [_seconds(bar.at), '${bar.open}', '${bar.high}', '${bar.low}', '${bar.close}', '0', '1', 1],
              ],
            },
          }),
          200,
        ),
        'api.kraken.com' => http.Response(
          jsonEncode({
            'error': ['EQuery:Unknown asset pair'],
          }),
          200,
        ),
        _ => http.Response('{}', 404),
      };
    }

    Future<TickerQuote> fetch(String symbol, {TickerInstrument prefer = TickerInstrument.auto}) =>
        client.fetchQuote(symbol, range: TickerRange.week.range, interval: TickerRange.week.interval, prefer: prefer);

    setUp(() {
      hosts = [];
      health = PriceSourceHealth();
      client = TickerClient(health: health, httpClient: MockClient(exchanges));
    });

    test('a coin is asked of the exchanges, and a refusing one rests', () async {
      final quote = await fetch('BTC');
      expect(quote.symbol, 'BTC-USD');
      expect(quote.price, 109);
      expect(hosts, ['api.exchange.coinbase.com', 'api.kraken.com']);
      expect(health.resting('Coinbase'), isTrue);

      hosts.clear();
      await fetch('BTC');
      expect(hosts, ['api.kraken.com'], reason: 'Coinbase rests');
    });

    test('a stock is asked of Yahoo alone', () async {
      await expectLater(fetch('AAPL', prefer: TickerInstrument.stock), throwsA(isA<TickerException>()));
      expect(hosts, ['query1.finance.yahoo.com']);
    });

    test('a name Yahoo does not list as a share is then tried as a coin', () async {
      await expectLater(fetch('NOPE'), throwsA(isA<TickerException>()));
      expect(hosts, [
        'query1.finance.yahoo.com',
        'api.exchange.coinbase.com',
        'api.kraken.com',
        'api.binance.com',
        'query1.finance.yahoo.com',
      ]);
    });
  });
}
