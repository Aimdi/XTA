import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_asset_screen.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/stocks/crypto_detail_store.dart';
import 'package:xta/plugins/stocks/stocks_add_sheet.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_screen.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_list.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_detail_store.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_quote_panel.dart';

const _pepe = '0x6982508145454ce325ddbe47a25d4ec3d2311933';
const _asset = CryptoAsset(chain: 'ethereum', address: _pepe, symbol: 'PEPE', name: 'Pepe');

final _themes = <String, ThemeData>{
  'light': ThemeData.light(),
  'dark': ThemeData.dark(),
  'true black': ThemeData.dark().copyWith(
    scaffoldBackgroundColor: Colors.black,
    colorScheme: const ColorScheme.dark(surface: Colors.black),
  ),
};

final _now = DateTime.now().toUtc().millisecondsSinceEpoch ~/ 1000;

/// A one-day chart whose regular session is happening now.
Map<String, Object?> _chart(String symbol) => {
  'chart': {
    'result': [
      {
        'meta': {
          'symbol': symbol,
          'currency': 'USD',
          'shortName': 'Apple Inc. Common Stock With A Long Name',
          'regularMarketPrice': 200.0,
          'chartPreviousClose': 190.0,
          'regularMarketVolume': 48200000,
          'regularMarketDayHigh': 201.0,
          'regularMarketDayLow': 189.5,
          'fiftyTwoWeekHigh': 260.1,
          'fiftyTwoWeekLow': 169.2,
          'currentTradingPeriod': {
            'pre': {'start': _now - 7200, 'end': _now - 3600},
            'regular': {'start': _now - 3600, 'end': _now + 3600},
            'post': {'start': _now + 3600, 'end': _now + 7200},
          },
        },
        'timestamp': [_now - 3000, _now - 2000, _now - 1000],
        'indicators': {
          'quote': [
            {
              'open': [192.0, 196.0, 199.0],
              'close': [195.0, 198.0, 200.0],
            },
          ],
        },
      },
    ],
  },
};

final TickerQuote _quote = TickerQuote.fromChartJson(_chart('AAPL'), symbol: 'AAPL')!;

Future<void> _pump(WidgetTester tester, Widget child, {double scale = 1, ThemeData? theme}) async {
  tester.view.physicalSize = const Size(320, 640);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: theme,
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      builder: (context, app) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
        child: app!,
      ),
      home: Scaffold(body: child),
    ),
  );
  await tester.pumpAndSettle();
}

/// Read once from the database, then left alone by the screen.
class _Watchlist extends StocksWatchlistStore {
  Future<void> preload() => super.load();
  @override
  Future<void> load() async {}
}

/// Serves remembered quotes and never reaches the network.
class _Quotes extends TickerQuoteCache {
  @override
  Future<void> ensure(Iterable<String> symbols) async {}
  @override
  Future<void> refresh(Iterable<String> symbols) async {}
}

void main() {
  setUpAll(() async {
    sqfliteFfiInit();
    databaseFactory = databaseFactoryFfi;
    final dir = await Directory.systemTemp.createTemp('xta_stocks_widgets');
    await databaseFactory.setDatabasesPath(dir.path);
    await Repository().migrate();
  });

  testWidgets('the Stocks home leads with the watchlist as quote rows', (tester) async {
    final watchlist = _Watchlist();
    final quotes = _Quotes()..remember('AAPL', _quote);
    await tester.runAsync(() async {
      final database = await Repository.writable();
      await database.delete(tableStockSubscription);
      await watchlist.add('AAPL');
      await watchlist.add(_asset.encode());
      await watchlist.preload();
    });
    final scroll = ScrollController();
    addTearDown(scroll.dispose);

    await _pump(
      tester,
      MultiProvider(
        providers: [
          Provider<StocksWatchlistStore>.value(value: watchlist),
          Provider<TickerQuoteCache>.value(value: quotes),
        ],
        child: StocksScreen(scrollController: scroll),
      ),
      scale: 2,
      theme: _themes['true black'],
    );

    expect(tester.takeException(), isNull);
    expect(find.byType(StocksQuoteRow), findsNWidgets(2));
    expect(find.text('+5.26%'), findsOneWidget);
    expect(find.text('Ethereum · 0x6982…1933'), findsOneWidget);
    await tester.pumpWidget(const SizedBox());
    await watchlist.destroy();
    await quotes.destroy();
  });

  group('a watchlist row', () {
    for (final entry in _themes.entries) {
      for (final scale in [1.0, 2.0]) {
        testWidgets('fits 320dp at ${scale}x text in ${entry.key}', (tester) async {
          await _pump(
            tester,
            Column(
              children: [
                StocksQuoteRow(title: r'$AAPL', subtitle: _quote.shortName, quote: _quote, onTap: () {}),
                StocksQuoteRow(title: r'$PEPE', subtitle: _asset.subtitle, quote: null, onTap: () {}),
              ],
            ),
            scale: scale,
            theme: entry.value,
          );

          expect(tester.takeException(), isNull);
          expect(find.text(r'$AAPL'), findsOneWidget);
          expect(find.text('200.00'), findsOneWidget);
          expect(find.text('+5.26%'), findsOneWidget);
          expect(find.text('Ethereum · 0x6982…1933'), findsOneWidget);
          expect(find.byType(StocksSparkline), scale == 1 ? findsOneWidget : findsNothing);
          for (final row in find.byType(StocksQuoteRow).evaluate()) {
            expect(row.size!.height, greaterThanOrEqualTo(48), reason: 'a row is a touch target');
          }
        });
      }
    }

    testWidgets('long-pressing a token copies its full contract address', (tester) async {
      final copied = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied.add((call.arguments as Map)['text'] as String);
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));

      await _pump(
        tester,
        StocksWatchlistList(
          symbols: [_asset.id, 'AAPL'],
          quotes: {'AAPL': _quote},
          assets: {_asset.id: _asset},
          onOpen: (_) {},
          onRefresh: () async {},
        ),
      );
      await tester.longPress(find.text(r'$PEPE'));
      await tester.pump();

      expect(copied, [_pepe]);
      expect(find.text('Contract address copied'), findsOneWidget);
    });
  });

  group('the ticker page', () {
    for (final entry in _themes.entries) {
      testWidgets('shows price, session and key stats at 320dp and 2x text in ${entry.key}', (tester) async {
        final store = TickerDetailStore(
          symbol: 'AAPL',
          client: TickerClient(
            httpClient: MockClient((request) async => http.Response(jsonEncode(_chart('AAPL')), 200)),
          ),
        );
        addTearDown(store.destroy);
        await store.load();
        await _pump(
          tester,
          SingleChildScrollView(child: TickerQuotePanel(store: store)),
          scale: 2,
          theme: entry.value,
        );

        expect(tester.takeException(), isNull);
        expect(find.text('200.00'), findsWidgets);
        expect(find.text('+10.00 (+5.26%)'), findsOneWidget);
        expect(find.text('Market open'), findsOneWidget);
        expect(find.text('Open'), findsOneWidget);
        expect(find.text('Prev. close'), findsOneWidget);
        expect(find.text('190.00'), findsOneWidget);
        expect(find.text('48.2M'), findsOneWidget);
        expect(find.text('Crypto'), findsNothing, reason: r'$AAPL is not ambiguous');
      });
    }

    testWidgets(r'$ETH offers the coin and the listed stock', (tester) async {
      final asked = <String>[];
      final store = TickerDetailStore(
        symbol: 'ETH',
        client: TickerClient(
          httpClient: MockClient((request) async {
            asked.add(request.url.pathSegments.last);
            return http.Response(jsonEncode(_chart(request.url.pathSegments.last)), 200);
          }),
        ),
      );
      addTearDown(store.destroy);
      await store.load();
      await _pump(tester, SingleChildScrollView(child: TickerQuotePanel(store: store)));
      expect(asked, ['ETH-USD']);

      await tester.tap(find.text('Stocks'));
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pumpAndSettle();

      expect(asked.last, 'ETH');
      expect(store.state.instrument.name, 'stock');
      expect(tester.takeException(), isNull);
    });

    testWidgets('a failed price load offers a retry instead of a blank', (tester) async {
      final store = TickerDetailStore(
        symbol: 'AAPL',
        client: TickerClient(httpClient: MockClient((_) async => http.Response('busy', 503))),
      );
      addTearDown(store.destroy);
      await store.load();
      await _pump(tester, SingleChildScrollView(child: TickerQuotePanel(store: store)));

      expect(find.text('Market data could not be loaded'), findsOneWidget);
      expect(find.text('Retry'), findsOneWidget);
    });
  });

  group('a token followed by contract', () {
    const market = CryptoMarket(
      asset: _asset,
      pairAddress: '0xpool',
      price: 0.0000123,
      changePercent: -4.2,
      change1h: 0.5,
      change6h: -1.25,
      liquidity: 32000000,
      volume24h: 4200000,
      marketCap: 4900000000,
      fdv: 5100000000,
    );

    for (final entry in _themes.entries) {
      testWidgets('header fits 320dp at 2x text in ${entry.key}', (tester) async {
        final choices = <bool>[];
        await _pump(
          tester,
          SingleChildScrollView(
            child: CryptoAssetHeader(
              asset: _asset,
              state: const CryptoDetailState(market: market),
              onRetry: () async {},
              onIncludeCashtag: choices.add,
            ),
          ),
          scale: 2,
          theme: entry.value,
        );

        expect(tester.takeException(), isNull);
        expect(find.text('Ethereum · 0x6982…1933'), findsOneWidget);
        expect(find.text('0.0000123'), findsOneWidget);
        expect(find.text('−4.20%'), findsOneWidget);
        expect(find.text('Market cap'), findsOneWidget);
        expect(find.text(r'$4.9B'), findsOneWidget);
        expect(find.text('6h change'), findsOneWidget);
        await tester.ensureVisible(find.text(r'Include $PEPE posts'));
        await tester.tap(find.text(r'Include $PEPE posts'));
        expect(choices, [true]);
      });
    }
  });

  group('the add sheet', () {
    testWidgets('a pasted address lists the token by network and short address', (tester) async {
      final crypto = CryptoClient(
        httpClient: MockClient(
          (_) async => http.Response(
            jsonEncode({
              'pairs': [
                {
                  'chainId': 'ethereum',
                  'pairAddress': '0xpool',
                  'baseToken': {'address': _pepe, 'symbol': 'PEPE', 'name': 'Pepe'},
                  'quoteToken': {'address': '0xc02aaa39b223fe8d0a0e5c4f27ead9083c756cc2', 'symbol': 'WETH'},
                  'priceUsd': '0.0000123',
                  'priceChange': {'h24': 3.1},
                  'liquidity': {'usd': 1000},
                },
              ],
            }),
            200,
          ),
        ),
      );
      final ticker = TickerClient(httpClient: MockClient((_) async => http.Response('{}', 200)));
      await _pump(tester, StocksAddSheet(client: ticker, cryptoClient: crypto), scale: 1.5);

      await tester.enterText(find.byType(TextField), _pepe.toUpperCase().replaceFirst('0X', '0x'));
      await tester.pumpAndSettle();

      expect(tester.takeException(), isNull);
      expect(find.text(r'$PEPE · Pepe'), findsOneWidget);
      expect(find.text('Ethereum · 0x6982…1933'), findsOneWidget);
      expect(find.text('+3.10%'), findsOneWidget);
    });
  });
}
