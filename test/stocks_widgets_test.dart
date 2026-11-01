import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_asset_screen.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/stocks/crypto_detail_store.dart';
import 'package:xta/plugins/stocks/stocks_add_sheet.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/plugin_compact_header.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/stocks/stocks_markets.dart';
import 'package:xta/plugins/stocks/stocks_plugin.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_row_state.dart';
import 'package:xta/plugins/stocks/stocks_screen.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_list.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_reel.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';
import 'package:xta/tweet/ticker/ticker_detail_store.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_quote_panel.dart';
import 'package:xta/tweet/ticker/ticker_range.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/contrast.dart';

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
          'instrumentType': 'EQUITY',
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

const _pepeMarket = CryptoMarket(asset: _asset, price: 0.0000123, changePercent: -4.2);

/// When the Nasdaq-100 last closed: three days ago, 16:00 New York.
final _lastClose = _now - 3 * 86400;

/// The Nasdaq-100 with its exchange shut: the last close, no bars, and
/// today's periods all over.
final TickerQuote _closedIndex = TickerQuote.fromChartJson({
  'chart': {
    'result': [
      {
        'meta': {
          'symbol': '^NDX',
          'currency': 'USD',
          'instrumentType': 'INDEX',
          'regularMarketPrice': 24172.03,
          'chartPreviousClose': 24063.4,
          'regularMarketTime': _lastClose,
          'currentTradingPeriod': {
            'pre': {'start': _lastClose - 41400, 'end': _lastClose - 23400},
            'regular': {'start': _lastClose - 23400, 'end': _lastClose},
            'post': {'start': _lastClose, 'end': _lastClose + 14400},
          },
        },
      },
    ],
  },
}, symbol: '^NDX')!;

String _closeTag(String locale, String word) =>
    '$word ${DateFormat.E(locale).format(DateTime.fromMillisecondsSinceEpoch(_lastClose * 1000))}';

/// [settle] waits for every animation to end, which a loading shimmer never
/// does: rows that load are pumped a few frames instead.
Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double scale = 1,
  ThemeData? theme,
  Locale? locale,
  bool settle = true,
}) async {
  tester.view.physicalSize = const Size(320, 640);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
      theme: theme,
      locale: locale,
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      builder: (context, app) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
        child: app!,
      ),
      home: Scaffold(body: child),
    ),
  );
  settle ? await tester.pumpAndSettle() : await tester.pump(const Duration(milliseconds: 100));
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
    expect(find.text('Stock'), findsOneWidget);
    expect(find.text('Crypto · Ethereum'), findsOneWidget, reason: 'a token is badged with its network');
    expect(find.textContaining('Pepe'), findsOneWidget, reason: 'and named, not addressed');
    await tester.pumpWidget(const SizedBox());
    await watchlist.destroy();
    await quotes.destroy();
  });

  testWidgets('the Stocks bar holds only its tabs and a menu; adding floats over the lists it adds to', (tester) async {
    final watchlist = _Watchlist();
    final quotes = _Quotes()..remember('AAPL', _quote);
    await tester.runAsync(() async {
      final database = await Repository.writable();
      await database.delete(tableStockSubscription);
      await watchlist.add('AAPL');
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
    );

    for (final tab in ['Watchlist', 'Posts', 'Trending', 'Markets']) {
      expect(find.byTooltip(tab), findsOneWidget, reason: tab);
    }
    expect(find.byKey(const ValueKey('stocks-more')), findsOneWidget);
    expect(find.widgetWithIcon(IconButton, Icons.add), findsNothing, reason: 'adding left the bar');
    expect(find.byKey(const ValueKey('stocks-add')), findsOneWidget);

    final context = tester.element(find.byType(StocksScreen));
    final selected = tester.widget<Icon>(find.byIcon(Icons.star_outline));
    final accent = ensureContrast(tweetReadableAccentColor(context), Theme.of(context).scaffoldBackgroundColor);
    expect(selected.color, accent, reason: 'selection is the app accent');
    expect(selected.color, isNot(StocksPlugin().brandColor));
    expect(pluginMarkTint(context, StocksPlugin()), Theme.of(context).colorScheme.onSurface, reason: 'a neutral mark');

    await tester.tap(find.byKey(const ValueKey('stocks-more')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Manage watchlist'));
    await tester.pumpAndSettle();
    expect(find.text('Manage watchlist'), findsOneWidget, reason: 'the sheet it opens');
    Navigator.pop(tester.element(find.text('Manage watchlist')));
    await tester.pumpAndSettle();

    await tester.tap(find.byTooltip('Markets'));
    await tester.pump(const Duration(milliseconds: 100));
    expect(find.byKey(const ValueKey('stocks-add')), findsNothing, reason: 'nothing to add to on Markets');
    await tester.pumpWidget(const SizedBox());
    await watchlist.destroy();
    await quotes.destroy();
  });

  testWidgets('in Home the Stocks bar is its tabs and a ⋮, whose sheet does not repeat them', (tester) async {
    final watchlist = _Watchlist();
    final quotes = _Quotes()..remember('AAPL', _quote);
    final dock = PluginHomeDockStore();
    await tester.runAsync(() async {
      final database = await Repository.writable();
      await database.delete(tableStockSubscription);
      await watchlist.add('AAPL');
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
        child: PluginHomeDockScope(
          store: dock,
          source: StocksPlugin().id,
          enabled: true,
          compact: true,
          openClientLabel: '',
          onOpenClient: null,
          child: Column(
            children: [
              PluginCompactHeader(plugin: StocksPlugin(), store: dock),
              Expanded(child: StocksScreen(scrollController: scroll)),
            ],
          ),
        ),
      ),
    );

    final options = find.byKey(const ValueKey('home-plugin-options'));
    expect(find.descendant(of: options, matching: find.byIcon(Icons.more_vert)), findsOneWidget);
    expect(find.descendant(of: options, matching: find.byIcon(Icons.tune)), findsNothing);
    expect(find.widgetWithIcon(IconButton, Icons.add), findsNothing, reason: 'adding floats over the list instead');
    await tester.tap(options);
    await tester.pumpAndSettle();
    expect(find.byKey(const ValueKey('home-section-0')), findsNothing);
    expect(find.text('Manage watchlist'), findsOneWidget);
    expect(find.text('Watchlist'), findsNothing, reason: 'the tab is not listed again under its own name');
    await tester.pumpWidget(const SizedBox());
    await dock.destroy();
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
                StocksQuoteRow(
                  title: r'$AAPL',
                  subtitle: _quote.shortName,
                  badge: 'Stock',
                  quote: _quote,
                  onTap: () {},
                ),
                StocksQuoteRow(
                  title: r'$PEPE',
                  subtitle: 'Pepe',
                  badge: 'Crypto · Ethereum',
                  quote: _pepeMarket.quote,
                  onTap: () {},
                ),
                StocksQuoteRow(
                  title: r'$NDX',
                  subtitle: 'Nasdaq-100',
                  badge: 'Index',
                  quote: _closedIndex,
                  onTap: () {},
                ),
                StocksQuoteRow(title: r'$POD', badge: 'Stock', quote: null, failed: true, onTap: () {}, onRetry: () {}),
              ],
            ),
            scale: scale,
            theme: entry.value,
          );

          expect(tester.takeException(), isNull);
          expect(find.text(r'$AAPL'), findsOneWidget);
          expect(find.text('200.00'), findsOneWidget);
          expect(find.text('+5.26%'), findsOneWidget);
          expect(find.text('0.0₄1230'), findsOneWidget);
          expect(find.text('Crypto · Ethereum'), findsOneWidget);
          expect(find.text('No data · Tap to retry'), findsOneWidget);
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
          quotes: {'AAPL': _quote, _asset.id: _pepeMarket.quote},
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

  group('what a row shows where its figures go', () {
    final now = DateTime.now();

    test('nothing yet is loading; a failed lookup, or a quote without a price, is no data', () {
      expect(stockRowState(null, failed: false, now: now), StockRowState.loading);
      expect(stockRowState(null, failed: true, now: now), StockRowState.noData);
      expect(stockRowState(const CryptoMarket(asset: _asset).quote, failed: false, now: now), StockRowState.noData);
      expect(stockRowState(_quote, failed: true, now: now), StockRowState.live, reason: 'an answer beats a failure');
    });

    test('outside every session the market is closed; a coin never is', () {
      expect(stockRowState(_closedIndex, failed: false, now: now), StockRowState.closed);
      expect(stockRowState(_quote, failed: false, now: now), StockRowState.live);
      expect(stockRowState(_pepeMarket.quote, failed: false, now: now), StockRowState.live);
    });

    testWidgets('loading: a shimmer where the figures go, the row already named and badged', (tester) async {
      await _pump(
        tester,
        StocksQuoteRow(title: r'$NDX', subtitle: 'Nasdaq-100', badge: 'Index', quote: null, onTap: () {}),
        settle: false,
      );

      expect(find.byType(StocksFiguresSkeleton), findsOneWidget);
      expect(find.text(kStockPlaceholder), findsNothing, reason: 'no dash');
      expect(find.byType(StocksChangeChip), findsNothing);
      expect(find.text('Index'), findsOneWidget);
      expect(find.textContaining('Nasdaq-100'), findsOneWidget);
      expect(find.bySemanticsLabel(RegExp('Loading price')), findsOneWidget);
    });

    testWidgets('market closed: the last close, the day of it and a muted move', (tester) async {
      await _pump(
        tester,
        StocksQuoteRow(title: r'$NDX', subtitle: 'Nasdaq-100', badge: 'Index', quote: _closedIndex, onTap: () {}),
      );

      expect(find.text('24,172.03'), findsOneWidget);
      expect(find.text(_closeTag('en', 'Close')), findsOneWidget);
      expect(find.text('+0.45%'), findsOneWidget);
      expect(tester.widget<StocksChangeChip>(find.byType(StocksChangeChip)).muted, isTrue);
      expect(find.text('1D'), findsNothing, reason: 'the tag already says what the move is');
    });

    testWidgets('no data: one line, no pill, and tapping it asks again', (tester) async {
      var retried = 0;
      var opened = 0;
      await _pump(
        tester,
        StocksQuoteRow(
          title: r'$POD',
          badge: 'Stock',
          quote: null,
          failed: true,
          onTap: () => opened++,
          onRetry: () => retried++,
        ),
      );

      expect(find.byType(StocksChangeChip), findsNothing, reason: 'no empty pill');
      expect(find.byType(StocksFiguresSkeleton), findsNothing);
      await tester.tap(find.text('No data · Tap to retry'));
      expect((retried, opened), (1, 0));
      await tester.tap(find.text(r'$POD'));
      expect((retried, opened), (1, 1), reason: 'the rest of the row still opens the page');
    });

    testWidgets('each move says what it is measured over', (tester) async {
      await _pump(
        tester,
        Column(
          children: [
            StocksQuoteRow(title: r'$AAPL', quote: _quote, onTap: () {}),
            StocksQuoteRow(title: r'$PEPE', quote: _pepeMarket.quote, onTap: () {}),
          ],
        ),
      );

      expect(find.text('1D'), findsOneWidget, reason: 'a share against the previous close');
      expect(find.text('24h'), findsOneWidget, reason: 'a token over the last 24 hours');
    });

    for (final entry in _themes.entries) {
      testWidgets('German fits 320dp at 1.3x text in ${entry.key}', (tester) async {
        await _pump(
          tester,
          Column(
            children: [
              StocksQuoteRow(title: r'$NDX', subtitle: 'Nasdaq-100', badge: 'Index', quote: _closedIndex, onTap: () {}),
              StocksQuoteRow(title: r'$AAPL', subtitle: _quote.shortName, badge: 'Aktie', quote: _quote, onTap: () {}),
              StocksQuoteRow(
                title: r'$PEPE',
                subtitle: 'Pepe',
                badge: 'Krypto · Ethereum',
                quote: _pepeMarket.quote,
                onTap: () {},
              ),
              StocksQuoteRow(title: r'$POD', badge: 'Aktie', quote: null, failed: true, onTap: () {}, onRetry: () {}),
            ],
          ),
          scale: 1.3,
          theme: entry.value,
          locale: const Locale('de'),
        );

        expect(tester.takeException(), isNull);
        expect(find.text('24.172,03'), findsOneWidget);
        expect(find.text(_closeTag('de', 'Schluss')), findsOneWidget);
        expect(find.text('1T'), findsOneWidget);
        expect(find.text('0,0₄1230'), findsOneWidget);
        expect(find.text('Keine Daten · Tippen zum Wiederholen'), findsOneWidget);
      });
    }

    testWidgets('Markets names and badges every row before any price, and retries the one without data', (
      tester,
    ) async {
      final retried = <String>[];
      await _pump(
        tester,
        StocksMarketsList(quotes: const {}, failed: const {'SPX'}, onRetry: retried.add),
        settle: false,
      );

      expect(find.textContaining('S&P 500'), findsOneWidget);
      expect(find.textContaining('Nasdaq-100'), findsOneWidget);
      expect(find.text('Index'), findsWidgets);
      expect(find.text(kStockPlaceholder), findsNothing);
      expect(find.byType(StocksFiguresSkeleton), findsWidgets);
      await tester.tap(find.text('No data · Tap to retry'));
      expect(retried, ['SPX']);
    });

    testWidgets('the watchlist strip shows the same states', (tester) async {
      await _pump(
        tester,
        StocksWatchlistReel(
          symbols: const ['AAPL', 'POD', 'ETH', 'NDX'],
          quotes: {'AAPL': _quote, 'NDX': _closedIndex},
          failed: const {'POD'},
          onSelected: (_) {},
        ),
        settle: false,
      );

      expect(tester.takeException(), isNull);
      expect(find.byType(StocksFiguresSkeleton, skipOffstage: false), findsOneWidget, reason: r'$ETH loads');
      expect(find.text('No data', skipOffstage: false), findsOneWidget, reason: r'$POD has none');
      final chips = tester.widgetList<StocksChangeChip>(find.byType(StocksChangeChip, skipOffstage: false));
      expect([for (final chip in chips) chip.muted], [false, true], reason: r'$NDX is closed');
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

    testWidgets('a new range loads under the line over the chart before, so nothing below it moves', (tester) async {
      Completer<http.Response>? next;
      final store = TickerDetailStore(
        symbol: 'AAPL',
        client: TickerClient(
          httpClient: MockClient((request) async => next?.future ?? http.Response(jsonEncode(_chart('AAPL')), 200)),
        ),
      );
      addTearDown(store.destroy);
      await store.load();
      await _pump(
        tester,
        SingleChildScrollView(
          child: Column(
            children: [
              TickerQuotePanel(store: store),
              const Text('first post'),
            ],
          ),
        ),
      );
      final idle = tester.getTopLeft(find.text('first post'));

      next = Completer<http.Response>();
      await tester.tap(find.text('1W'));
      await tester.pump();
      expect(find.byType(LinearProgressIndicator), findsOneWidget);
      expect(find.text('200.00'), findsWidgets, reason: 'the price stays while the new range loads');
      expect(tester.getTopLeft(find.text('first post')), idle);

      next.complete(http.Response(jsonEncode(_chart('AAPL')), 200));
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pumpAndSettle();
      expect(find.byType(LinearProgressIndicator), findsNothing);
      expect(tester.getTopLeft(find.text('first post')), idle);
    });

    test('a silent refresh never overtakes a new range the reader is waiting on', () async {
      final requests = <String>[];
      Completer<http.Response>? next;
      final store = TickerDetailStore(
        symbol: 'AAPL',
        client: TickerClient(
          httpClient: MockClient((request) async {
            requests.add(request.url.queryParameters['range'] ?? '');
            return next?.future ?? http.Response(jsonEncode(_chart('AAPL')), 200);
          }),
        ),
      );
      addTearDown(store.destroy);
      await store.load();

      next = Completer<http.Response>();
      final switching = store.selectRange(TickerRange.week);
      await Future<void>.delayed(Duration.zero);
      await store.load(silent: true);
      expect(requests, ['1d', '5d'], reason: 'the live refresh waits for the range being loaded');

      next.complete(http.Response('busy', 503));
      await switching;
      expect((store.state.failed, store.state.quote), (true, null));
    });

    testWidgets('a range that fails to load says so rather than keeping the chart before', (tester) async {
      var busy = false;
      final store = TickerDetailStore(
        symbol: 'AAPL',
        client: TickerClient(
          httpClient: MockClient(
            (request) async => busy ? http.Response('busy', 503) : http.Response(jsonEncode(_chart('AAPL')), 200),
          ),
        ),
      );
      addTearDown(store.destroy);
      await store.load();
      await _pump(tester, SingleChildScrollView(child: TickerQuotePanel(store: store)));

      busy = true;
      await tester.tap(find.text('1W'));
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pumpAndSettle();
      expect(find.text('Market data could not be loaded'), findsOneWidget);
      expect(find.text('200.00'), findsNothing);

      busy = false;
      await tester.tap(find.text('Retry'));
      await tester.runAsync(() => Future<void>.delayed(const Duration(milliseconds: 10)));
      await tester.pumpAndSettle();
      expect(find.text('Market data could not be loaded'), findsNothing);
      expect(store.state.range.name, 'week');
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
        expect(find.text('0.0₄1230'), findsOneWidget);
        expect(find.bySemanticsLabel('0.00001230'), findsOneWidget, reason: 'read with its zeros');
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
