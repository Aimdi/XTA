import 'dart:async';
import 'dart:convert';
import 'dart:io';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/plugins/stocks/stocks_instruments.dart';
import 'package:xta/plugins/stocks/stocks_header.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/crypto_asset_screen.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/stocks/crypto_detail_store.dart';
import 'package:xta/plugins/stocks/stocks_add_sheet.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
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
  double width = 320,
  ThemeData? theme,
  Locale? locale,
  BasePrefService? prefs,
  bool settle = true,
}) async {
  tester.view.physicalSize = Size(width, 640);
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    PrefService(
      service: prefs ?? _prefs(),
      child: MaterialApp(
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
    ),
  );
  settle ? await tester.pumpAndSettle() : await tester.pump(const Duration(milliseconds: 100));
}

/// The settings the Stocks screen reads, as a fresh install has them.
PrefServiceCache _prefs() =>
    PrefServiceCache(cache: {optionTickerChart: true, optionPluginStocksFoldSignalPosts: false});

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

  testWidgets('the Stocks home leads with the watchlist under its title and count', (tester) async {
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
    expect(find.text('My watchlist'), findsOneWidget);
    expect(find.text('2 tickers'), findsOneWidget);
    expect(find.byType(StocksQuoteRow), findsNWidgets(2));
    expect(find.text('Apple Inc. Common Stock With A Long Name'), findsOneWidget, reason: 'the name leads');
    expect(find.text(r'$AAPL · Stock'), findsOneWidget, reason: 'over the cashtag and what it is');
    expect(find.text('+5.26%'), findsOneWidget);
    expect(find.text('PEPE'), findsOneWidget, reason: 'a token goes by its symbol');
    expect(find.text('Ethereum'), findsOneWidget, reason: 'beside its network');
    final token = tester.widget<StocksQuoteRow>(find.ancestor(of: find.text('PEPE'), matching: find.byType(StocksQuoteRow)));
    expect(token.subtitle, '0x6982…1933', reason: 'over its address cut in the middle');
    expect(find.byType(FloatingActionButton), findsNothing, reason: 'adding is in the header and under the list');
    await tester.pumpWidget(const SizedBox());
    await watchlist.destroy();
    await quotes.destroy();
  });

  group('the header', () {
    Future<(_Watchlist, _Quotes)> watching(WidgetTester tester) async {
      final watchlist = _Watchlist();
      final quotes = _Quotes()..remember('AAPL', _quote);
      await tester.runAsync(() async {
        final database = await Repository.writable();
        await database.delete(tableStockSubscription);
        await watchlist.add('AAPL');
        await watchlist.preload();
      });
      addTearDown(() async {
        await watchlist.destroy();
        await quotes.destroy();
      });
      return (watchlist, quotes);
    }

    Widget screen(_Watchlist watchlist, _Quotes quotes, ScrollController scroll, {Widget? child}) => MultiProvider(
      providers: [
        Provider<StocksWatchlistStore>.value(value: watchlist),
        Provider<TickerQuoteCache>.value(value: quotes),
      ],
      child: child ?? StocksScreen(scrollController: scroll),
    );

    testWidgets('is the title, + and the settings over the four tabs as text', (tester) async {
      final (watchlist, quotes) = await watching(tester);
      final prefs = _prefs();
      final scroll = ScrollController();
      addTearDown(scroll.dispose);
      await _pump(tester, screen(watchlist, quotes, scroll), width: 640, prefs: prefs);

      expect(find.text('Stocks'), findsOneWidget);
      expect(find.byKey(const ValueKey('stocks-add')), findsOneWidget);
      expect(find.byTooltip('Stocks settings'), findsOneWidget);
      expect(find.byType(FloatingActionButton), findsNothing);
      expect(find.byType(PluginSectionPicker), findsNothing, reason: 'the labels fit');
      final context = tester.element(find.byType(StocksScreen));
      expect(stocksAccent(context), isNot(StocksPlugin().brandColor), reason: 'selection is the app accent');
      expect(pluginMarkTint(context, StocksPlugin()), Theme.of(context).colorScheme.onSurface, reason: 'a neutral mark');
      for (final tab in ['Watchlist', 'Posts', 'Trending', 'Markets']) {
        expect(
          tester.widget<Text>(find.text(tab)).style!.color,
          tab == 'Watchlist' ? stocksAccent(context) : Theme.of(context).colorScheme.onSurfaceVariant,
          reason: '$tab: only the selected tab is in the accent',
        );
      }

      await tester.tap(find.byTooltip('Stocks settings'));
      await tester.pumpAndSettle();
      expect(find.text('Manage watchlist'), findsOneWidget);
      expect(find.text('Stock chart'), findsOneWidget);
      await tester.tap(find.text('Fold signal-group posts'));
      await tester.pumpAndSettle();
      expect(prefs.get<bool>(optionPluginStocksFoldSignalPosts), isTrue);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets('becomes the labelled section picker when the tabs do not fit', (tester) async {
      final (watchlist, quotes) = await watching(tester);
      final scroll = ScrollController();
      addTearDown(scroll.dispose);
      await _pump(tester, screen(watchlist, quotes, scroll), scale: 1.3, locale: const Locale('de'));

      expect(tester.takeException(), isNull);
      expect(find.byType(PluginSectionPicker), findsOneWidget);
      await tester.pumpWidget(const SizedBox());
    });

    testWidgets("in Home: the title opens the sources, the sliders Home's sheet for Stocks", (tester) async {
      final (watchlist, quotes) = await watching(tester);
      final dock = PluginHomeDockStore();
      final scroll = ScrollController();
      addTearDown(scroll.dispose);
      var picked = 0;
      await _pump(
        tester,
        screen(
          watchlist,
          quotes,
          scroll,
          child: PluginHomeDockScope(
            store: dock,
            source: pluginIdStocks,
            enabled: true,
            compact: true,
            openClientLabel: '',
            onOpenClient: null,
            child: Column(
              children: [
                StocksHomeHeader(store: dock, onPickSource: () => picked++),
                Expanded(child: StocksScreen(scrollController: scroll)),
              ],
            ),
          ),
        ),
        width: 640,
      );

      expect(find.text('Stocks'), findsOneWidget, reason: 'one header, not two');
      await tester.tap(find.byKey(const ValueKey('plugin-source-picker-stocks')));
      expect(picked, 1);
      expect(find.byKey(const ValueKey('stocks-add')), findsOneWidget);
      expect(find.text('Watchlist'), findsOneWidget, reason: 'the tabs as text');

      await tester.tap(find.byTooltip('Stocks settings'));
      await tester.pumpAndSettle();
      expect(find.text('Manage watchlist'), findsOneWidget);
      expect(find.byKey(const ValueKey('home-section-0')), findsNothing, reason: 'the tabs are not listed again');
      await tester.pumpWidget(const SizedBox());
      await dock.destroy();
    });
  });

  group('a watchlist row', () {
    List<Widget> rows({String stock = 'Stock', String index = 'Index'}) => [
      StocksQuoteRow(title: 'Apple Inc.', subtitle: '\$AAPL · $stock', quote: _quote, onTap: () {}),
      StocksQuoteRow(
        title: 'PEPE',
        network: 'Ethereum',
        subtitle: '0x6982…1933',
        quote: _pepeMarket.quote,
        onTap: () {},
      ),
      StocksQuoteRow(title: 'Nasdaq-100', subtitle: '\$NDX · $index', quote: _closedIndex, onTap: () {}),
      StocksQuoteRow(title: 'POD', subtitle: stock, quote: null, failed: true, onTap: () {}, onRetry: () {}),
    ];

    for (final entry in _themes.entries) {
      for (final scale in [1.0, 2.0]) {
        testWidgets('fits 320dp at ${scale}x text in ${entry.key}', (tester) async {
          await _pump(tester, Column(children: rows()), scale: scale, theme: entry.value);

          expect(tester.takeException(), isNull);
          expect(find.text('200.00'), findsOneWidget);
          expect(find.text('+5.26%'), findsOneWidget);
          expect(find.text('0.0₄1230'), findsOneWidget);
          expect(find.text('The data source returns no price'), findsOneWidget);
          expect(scale == 1 ? find.text('Retry') : find.byTooltip('Retry'), findsOneWidget, reason: 'the words fit');
          expect(find.byType(StocksSparkline), scale == 1 ? findsOneWidget : findsNothing, reason: 'room to spare');
          for (final row in find.byType(StocksQuoteRow).evaluate()) {
            expect(row.size!.height, greaterThanOrEqualTo(56), reason: 'a row is a touch target');
          }
        });
      }

      testWidgets('German fits 320dp at 1.3x text in ${entry.key}', (tester) async {
        await _pump(
          tester,
          Column(children: rows(stock: 'Aktie')),
          scale: 1.3,
          theme: entry.value,
          locale: const Locale('de'),
        );

        expect(tester.takeException(), isNull);
        expect(find.text('24.172,03'), findsOneWidget);
        expect(find.text(_closeTag('de', 'Schluss')), findsOneWidget);
        expect(find.text('1T'), findsOneWidget);
        expect(find.text('24 h'), findsOneWidget);
        expect(find.text('0,0₄1230'), findsOneWidget);
        expect(find.text('Datenquelle liefert keinen Kurs'), findsOneWidget);
        expect(find.byTooltip('Erneut versuchen'), findsOneWidget, reason: 'the arrow alone where the words crowd the row');
      });
    }

    testWidgets('a long press lifts a row to move it, and copies nothing', (tester) async {
      final copied = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied.add((call.arguments as Map)['text'] as String);
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      final moves = <(int, int)>[];
      await _pump(
        tester,
        StocksWatchlistList(
          symbols: [_asset.id, 'AAPL'],
          quotes: {'AAPL': _quote, _asset.id: _pepeMarket.quote},
          assets: {_asset.id: _asset},
          onOpen: (_) {},
          onRefresh: () async {},
          onReorder: (from, to) => moves.add((from, to)),
        ),
      );
      expect(find.text('Press and hold to reorder'), findsOneWidget);

      final drag = await tester.startGesture(tester.getCenter(find.text('PEPE')));
      await tester.pump(kLongPressTimeout + const Duration(milliseconds: 100));
      await drag.moveBy(const Offset(0, 90));
      await tester.pump();
      await drag.moveBy(const Offset(0, 30));
      await tester.pump();
      await drag.up();
      await tester.pumpAndSettle();

      expect(moves, [(0, 1)], reason: 'PEPE ends up second');
      expect(copied, isEmpty, reason: 'the token page still has Copy contract');
    });
  });

  group('the watchlist order', () {
    test('keeps the order it was dragged into, and files anything new after it', () {
      expect(stocksOrdered(['AAPL', 'MSFT', 'NVDA', 'TSLA'], ['NVDA', 'GONE', 'AAPL']), ['NVDA', 'AAPL', 'MSFT', 'TSLA']);
      expect(stocksOrdered(['AAPL', 'MSFT'], null), ['AAPL', 'MSFT']);
    });

    test('a row dropped elsewhere is still there after a reload', () async {
      final prefs = _prefs();
      final first = StocksWatchlistStore(prefs: prefs);
      final database = await Repository.writable();
      await database.delete(tableStockSubscription);
      for (final symbol in ['AAPL', 'MSFT', 'NVDA']) {
        await first.add(symbol);
      }
      await first.load();
      expect(first.state, ['AAPL', 'MSFT', 'NVDA']);

      await first.reorder(0, 2);
      expect(first.state, ['MSFT', 'NVDA', 'AAPL']);

      final again = StocksWatchlistStore(prefs: prefs);
      await again.load();
      expect(again.state, ['MSFT', 'NVDA', 'AAPL']);
      await first.destroy();
      await again.destroy();
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

    testWidgets('loading: grey bars where the figures go, the row already named', (tester) async {
      await _pump(
        tester,
        StocksQuoteRow(title: 'Nasdaq-100', subtitle: r'$NDX · Index', quote: null, onTap: () {}),
        settle: false,
      );

      expect(find.byType(StocksFiguresSkeleton), findsOneWidget);
      expect(find.text(kStockPlaceholder), findsNothing, reason: 'no dash');
      expect(find.text(r'$NDX · Index'), findsOneWidget);
      expect(find.bySemanticsLabel(RegExp('Loading price')), findsOneWidget);
    });

    testWidgets('market closed: the close and its day, price and move in the secondary colour', (tester) async {
      await _pump(
        tester,
        StocksQuoteRow(title: 'Nasdaq-100', subtitle: r'$NDX · Index', quote: _closedIndex, onTap: () {}),
      );

      final muted = Theme.of(tester.element(find.byType(StocksQuoteRow))).colorScheme.onSurfaceVariant;
      expect(tester.widget<Text>(find.text('24,172.03')).style!.color, muted);
      expect(tester.widget<Text>(find.text('+0.45%')).style!.color, muted, reason: 'no green for a stale move');
      expect(find.text(_closeTag('en', 'Close')), findsOneWidget);
      expect(find.text('1D'), findsNothing, reason: 'the day of the close already says what the move is');
      expect(find.byType(StocksSparkline), findsNothing, reason: 'only a live row draws its day');
    });

    testWidgets('no data: the reason, no figures, and a retry button', (tester) async {
      var retried = 0;
      var opened = 0;
      await _pump(
        tester,
        StocksQuoteRow(
          title: 'POD',
          subtitle: 'Stock',
          quote: null,
          failed: true,
          onTap: () => opened++,
          onRetry: () => retried++,
        ),
      );

      expect(find.text('The data source returns no price'), findsOneWidget);
      expect(find.byType(StocksFiguresSkeleton), findsNothing);
      await tester.tap(find.text('Retry'));
      expect((retried, opened), (1, 0));
      await tester.tap(find.text('POD'));
      expect((retried, opened), (1, 1), reason: 'the rest of the row still opens the page');
    });

    testWidgets('a live move is coloured and says what it is measured over', (tester) async {
      await _pump(
        tester,
        Column(
          children: [
            StocksQuoteRow(title: 'Apple Inc.', quote: _quote, onTap: () {}),
            StocksQuoteRow(title: 'PEPE', quote: _pepeMarket.quote, onTap: () {}),
          ],
        ),
      );

      final context = tester.element(find.byType(Column).first);
      expect(tester.widget<Text>(find.text('+5.26%')).style!.color, stockTrendColour(context, 5.26));
      expect(find.text('1D'), findsOneWidget, reason: 'a share against the previous close');
      expect(find.text('24h'), findsOneWidget, reason: 'a token over the last 24 hours');
    });

    testWidgets('two readings: the amber line and both choices, the one shown in the accent', (tester) async {
      final chosen = <TickerInstrument>[];
      await _pump(
        tester,
        StocksQuoteRow(
          title: 'Solana',
          subtitle: r'$SOL · Crypto',
          quote: _pepeMarket.quote,
          warning: 'Two matches, choose the source',
          choices: StocksReadingChoices(
            readings: const [
              (instrument: TickerInstrument.crypto, name: 'Solana', kind: TickerKind.crypto),
              (instrument: TickerInstrument.stock, name: 'Emeren Group Ltd', kind: TickerKind.stock),
            ],
            shown: TickerInstrument.crypto,
            onChoose: chosen.add,
          ),
          onTap: () {},
        ),
      );

      final context = tester.element(find.byType(StocksQuoteRow));
      expect(tester.takeException(), isNull);
      expect(tester.widget<Text>(find.text('Two matches, choose the source')).style!.color, stockWarningColour(context));
      expect(find.text(r'$SOL · Crypto'), findsNothing, reason: 'the caution takes the second line');
      expect(find.text('Emeren Group Ltd'), findsOneWidget);
      expect(find.text('Stock'), findsOneWidget);
      BorderSide side(TickerInstrument instrument) => tester
          .widget<OutlinedButton>(find.byKey(ValueKey('stocks-reading-${instrument.name}')))
          .style!
          .side!
          .resolve({})!;
      expect(side(TickerInstrument.crypto).color, stocksAccent(context));
      expect(side(TickerInstrument.stock).color, isNot(stocksAccent(context)));

      await tester.tap(find.text('Emeren Group Ltd'));
      expect(chosen, [TickerInstrument.stock]);
    });
  });

  group('the quote cache and two readings', () {
    Map<String, Object?> listing(String symbol) => {
      'chart': {
        'result': [
          {
            'meta': {
              'symbol': symbol,
              'currency': 'USD',
              'instrumentType': 'EQUITY',
              'shortName': 'Emeren Group Ltd',
              'regularMarketPrice': 1.9,
              'chartPreviousClose': 2.0,
            },
            'timestamp': [_now - 60],
            'indicators': {
              'quote': [
                {
                  'close': [1.9],
                },
              ],
            },
          },
        ],
      },
    };
    Map<String, Object?> coin(String symbol) => {
      'chart': {
        'result': [
          {
            'meta': {'symbol': symbol, 'currency': 'USD', 'instrumentType': 'CRYPTOCURRENCY', 'regularMarketPrice': 180.0},
            'timestamp': [_now - 60],
            'indicators': {
              'quote': [
                {
                  'close': [180.0],
                },
              ],
            },
          },
        ],
      },
    };

    TickerClient client(List<String> asked) => TickerClient(
      httpClient: MockClient((request) async {
        final symbol = request.url.pathSegments.last;
        asked.add(symbol);
        return http.Response(jsonEncode(symbol.endsWith('-USD') ? coin(symbol) : listing(symbol)), 200);
      }),
    );

    test('a coin-first cashtag on the watchlist finds its listing, once', () async {
      final asked = <String>[];
      final cache = TickerQuoteCache(client: client(asked), prefs: _prefs());
      await cache.ensureReadings(['SOL', 'AAPL']);
      await cache.ensureReadings(['SOL']);

      expect(asked, ['SOL'], reason: r'$AAPL names one thing; $SOL is asked for once');
      expect(cache.listingFor('sol')?.shortName, 'Emeren Group Ltd');
      expect(stocksReadings(cache, 'SOL')?.map((reading) => reading.instrument), [
        TickerInstrument.crypto,
        TickerInstrument.stock,
      ]);
      await cache.destroy();
    });

    test('the pick is kept, and decides what the symbol is asked for as', () async {
      final asked = <String>[];
      final prefs = _prefs();
      final cache = TickerQuoteCache(client: client(asked), prefs: prefs);
      await cache.ensureReadings(['SOL']);
      await cache.choose('SOL', TickerInstrument.stock);

      expect(prefs.getStringList(optionTickerInstruments), ['SOL=stock']);
      expect(cache.peek('SOL')?.kind, TickerKind.stock);
      expect(stocksReadings(cache, 'SOL'), isNull, reason: 'nothing left to choose');

      asked.clear();
      final later = TickerQuoteCache(client: client(asked), prefs: prefs);
      expect(later.instrumentFor('sol'), TickerInstrument.stock);
      await later.ensure(['SOL']);
      expect(asked, ['SOL'], reason: 'not SOL-USD');
      expect(stockRowName('SOL', later.peek('SOL'), reading: later.instrumentFor('SOL')), 'Emeren Group Ltd');
      await cache.destroy();
      await later.destroy();
    });
  });

  testWidgets('Markets: Indices and Crypto, each saying its state, and the row without data retries', (tester) async {
    final retried = <String>[];
    await _pump(
      tester,
      StocksMarketsList(
        quotes: {'NDX': _closedIndex, 'BTC': _pepeMarket.quote},
        failed: const {'SPX'},
        onRetry: retried.add,
      ),
      settle: false,
    );

    expect(find.text('Indices'), findsOneWidget);
    expect(find.text('Crypto', skipOffstage: false), findsOneWidget);
    final close = DateTime.fromMillisecondsSinceEpoch(_lastClose * 1000);
    expect(find.textContaining('Closed, last close ${DateFormat.MEd('en').format(close)}'), findsOneWidget);
    expect(find.textContaining('Live, 24h change', skipOffstage: false), findsOneWidget);
    expect(find.text('S&P 500'), findsOneWidget, reason: 'named before any price');
    expect(find.text(r'$NDX · Index'), findsOneWidget);
    expect(find.text('The data source returns no price'), findsOneWidget, reason: r'$SPX has none');
    expect(find.text(_closeTag('en', 'Close')), findsNothing, reason: 'the section says it once');
    expect(find.text(kStockPlaceholder), findsNothing);
    expect(find.byType(StocksFiguresSkeleton), findsWidgets);
    await tester.tap(find.text('Retry'));
    expect(retried, ['SPX']);
  });

  testWidgets('the strip is flat: symbol over figures, No price, a bar while loading, the filter in the accent', (
    tester,
  ) async {
    await _pump(
      tester,
      StocksWatchlistReel(
        symbols: const ['AAPL', 'POD', 'ETH', 'NDX'],
        quotes: {'AAPL': _quote, 'NDX': _closedIndex},
        failed: const {'POD'},
        selected: 'AAPL',
        onSelected: (_) {},
      ),
      settle: false,
    );

    expect(tester.takeException(), isNull);
    expect(find.byType(Chip), findsNothing);
    expect(find.byType(StocksFiguresSkeleton, skipOffstage: false), findsOneWidget, reason: r'$ETH loads');
    expect(find.text('No price', skipOffstage: false), findsOneWidget, reason: r'$POD has none');
    final context = tester.element(find.byType(StocksWatchlistReel));
    Color? labelColour(String label) {
      final text = tester.widget<Text>(find.text(label, skipOffstage: false));
      return ((text.textSpan! as TextSpan).children!.first as TextSpan).style!.color;
    }

    expect(labelColour('AAPL'), stocksAccent(context), reason: 'the filter');
    expect(labelColour('POD'), isNull, reason: 'the others in the text colour');
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
