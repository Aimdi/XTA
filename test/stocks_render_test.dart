import 'dart:math' as math;

import 'package:dart_twitter_api/twitter_api.dart' show Entities;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/stocks_header.dart';
import 'package:xta/plugins/stocks/stocks_instruments.dart';
import 'package:xta/plugins/stocks/stocks_markets.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/plugins/stocks/stocks_signal_banner.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_list.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_reel.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/tweet/ticker/ticker_symbol.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/rich_text.dart';

/// Renders the five boards of the Stocks design (Markets, Watchlist, Tweets,
/// Trend, row states) for review beside it:
///   flutter test test/stocks_render_test.dart --dart-define=RENDER_STOCKS=true
const _render = bool.fromEnvironment('RENDER_STOCKS');

final _now = DateTime.now();

List<TickerPoint> _line(double from, double to, {required Duration over}) => [
  for (var i = 0; i <= 48; i++)
    TickerPoint(
      at: _now.subtract(over * ((48 - i) / 48)),
      close: from + (to - from) * i / 48 + math.sin(i / 3) * (to - from).abs() * 0.15,
    ),
];

TickerTradingPeriod _period(Duration start, Duration end) =>
    TickerTradingPeriod(start: _now.add(start), end: _now.add(end));

/// An exchange in session now, or one whose last session ended a day ago.
TickerQuote _listed(
  String symbol,
  double price,
  double before, {
  required bool open,
  String type = 'INDEX',
  String? name,
}) => TickerQuote(
  symbol: symbol,
  currency: 'USD',
  price: price,
  previousClose: before,
  instrumentType: type,
  shortName: name,
  regularMarketTime: open ? _now : _now.subtract(const Duration(days: 1)),
  regularPeriod: open
      ? _period(const Duration(hours: -3), const Duration(hours: 3))
      : _period(const Duration(hours: -30), const Duration(hours: -24)),
  points: open ? _line(before, price, over: const Duration(hours: 3)) : const [],
);

TickerQuote _coin(String symbol, double price, double before) => TickerQuote(
  symbol: symbol,
  currency: 'USD',
  price: price,
  previousClose: before,
  instrumentType: 'CRYPTOCURRENCY',
  tradesAroundTheClock: true,
  changeWindow: TickerChangeWindow.rolling24h,
  points: _line(before, price, over: const Duration(hours: 24)),
);

const _dotAddress = '0x23a2b8f3c1d4e5f60718293a4b5c6d7e8f90fe51';
const _dot = CryptoAsset(chain: 'base', address: _dotAddress, symbol: 'DOT', name: 'Dot');
final _dotQuote = const CryptoMarket(asset: _dot, price: 0.00875, changePercent: 3.98).quote;

final _quotes = <String, TickerQuote>{
  'SPX': _listed('^GSPC', 6738.44, 6712.10, open: false),
  'NDX': _listed('^NDX', 24172.03, 24063.40, open: false),
  'VIX': _listed('^VIX', 16.91, 17.44, open: true),
  'DAX': _listed('^GDAXI', 23958.30, 24021.70, open: true),
  'FTSE': _listed('^FTSE', 9717.25, 9760.06, open: false),
  'N225': _listed('^N225', 52411.34, 51325.61, open: false),
  'BTC': _coin('BTC-USD', 110412.5, 109798.0),
  'ETH': _coin('ETH-USD', 3887.12, 3951.40),
  'SOL': _coin('SOL-USD', 186.40, 179.95),
  'AAPL': _listed('AAPL', 270.37, 262.24, open: true, type: 'EQUITY', name: 'Apple Inc.'),
  'NVDA': _listed('NVDA', 202.49, 207.04, open: true, type: 'EQUITY', name: 'NVIDIA Corporation'),
  'DOT': _dotQuote,
  _dot.id: _dotQuote,
};

/// Serves the quotes above and never reaches the network.
class _Quotes extends TickerQuoteCache {
  _Quotes() {
    for (final MapEntry(:key, :value) in _quotes.entries) {
      update({...state, key: value});
    }
  }

  @override
  Future<void> ensure(Iterable<String> symbols) async {}
}

/// The watched token, without a database.
class _Watched extends StocksWatchlistStore {
  @override
  Map<String, CryptoAsset> get cryptoAssets => {_dot.id: _dot};
}

Future<void> _pump(WidgetTester tester, Widget child, ThemeData theme, Size size, double scale, Locale locale) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  final quotes = _Quotes();
  final watched = _Watched();
  addTearDown(quotes.destroy);
  addTearDown(watched.destroy);
  await tester.pumpWidget(
    PrefService(
      service: PrefServiceCache(cache: {optionTickerChart: true, optionPluginStocksFoldSignalPosts: false}),
      child: MultiProvider(
        providers: [
          Provider<TickerQuoteCache>.value(value: quotes),
          Provider<StocksWatchlistStore>.value(value: watched),
        ],
        child: MaterialApp(
          debugShowCheckedModeBanner: false,
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
          home: Scaffold(
            body: RepaintBoundary(
              key: const ValueKey('stocks-render'),
              // The scaffold's colour is painted outside the boundary; the
              // capture would show it as white.
              child: Builder(
                builder: (context) => ColoredBox(color: Theme.of(context).scaffoldBackgroundColor, child: child),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  // Loading rows shimmer for as long as they load; a few frames show them.
  await tester.pump(const Duration(milliseconds: 400));
}

/// The header with [selected] of the four tabs chosen.
Widget _header(int selected) => Builder(
  builder: (context) {
    final l10n = L10n.of(context);
    final labels = [l10n.plugin_stocks_watchlist, l10n.tweets, l10n.plugin_stocks_trending, l10n.plugin_stocks_markets];
    return StocksHeader(
      title: l10n.plugin_stocks_title,
      tabs: [
        for (final (index, label) in labels.indexed)
          PluginHomeTab(icon: Icons.circle, label: label, selected: index == selected, onTap: () {}),
      ],
      actions: [
        IconButton(tooltip: l10n.plugin_stocks_add, icon: const Icon(Icons.add), onPressed: () {}),
        IconButton(tooltip: l10n.plugin_stocks_settings, icon: const Icon(Icons.tune), onPressed: () {}),
      ],
    );
  },
);

Widget _readings(String symbol) => StocksReadingChoices(
  readings: const [
    (instrument: TickerInstrument.crypto, name: 'Solana', kind: TickerKind.crypto),
    (instrument: TickerInstrument.stock, name: 'Emeren Group Ltd', kind: TickerKind.stock),
  ],
  shown: TickerInstrument.crypto,
  onChoose: (_) {},
);

Widget _strip({String? selected}) => StocksWatchlistReel(
  symbols: [_dot.id, 'SOL', 'AAPL', 'POD'],
  quotes: _quotes,
  failed: const {'POD'},
  assets: {_dot.id: _dot},
  selected: selected,
  onSelected: (_) {},
);

/// A post as the feed draws one, enough to show cashtags and addresses in it.
Widget _post(String name, String handle, String text, List<String> cashtags) => Builder(
  builder: (context) {
    final theme = Theme.of(context);
    final entities = Entities.fromJson({
      'symbols': [
        for (final tag in cashtags)
          {
            'text': tag,
            'indices': [text.indexOf('\$$tag'), text.indexOf('\$$tag') + tag.length + 1],
          },
      ],
    });
    final parts = buildRichText(context, text, entities);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          CircleAvatar(radius: 20, backgroundColor: theme.colorScheme.surfaceContainerHighest),
          const SizedBox(width: 12),
          Expanded(
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text.rich(
                  TextSpan(
                    children: [
                      TextSpan(
                        text: name,
                        style: const TextStyle(fontWeight: FontWeight.w700),
                      ),
                      TextSpan(
                        text: '  $handle · 2h',
                        style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
                      ),
                    ],
                  ),
                ),
                const SizedBox(height: 2),
                DefaultTextStyle.merge(
                  style: const TextStyle(fontSize: 15),
                  child: Text.rich(TextSpan(children: displayRichText(parts))),
                ),
              ],
            ),
          ),
        ],
      ),
    );
  },
);

final _boards = <String, Widget Function()>{
  'markets': () => Column(
    children: [
      _header(3),
      Expanded(
        child: StocksMarketsList(quotes: _quotes, failed: const {'RUT'}, onRetry: (_) {}),
      ),
    ],
  ),
  'watchlist': () => Column(
    children: [
      _header(0),
      Expanded(
        child: StocksWatchlistList(
          symbols: ['SOL', _dot.id, 'AAPL', 'MSFT', 'POD'],
          quotes: _quotes,
          failed: const {'POD'},
          assets: {_dot.id: _dot},
          onOpen: (_) {},
          onRetry: (_) {},
          onAdd: () {},
          onReorder: (_, _) {},
          onRefresh: () async {},
          choicesFor: (symbol) => symbol == 'SOL' ? _readings(symbol) : null,
        ),
      ),
    ],
  ),
  'tweets': () => Column(
    children: [
      _header(1),
      _strip(selected: _dot.id),
      Expanded(
        child: ListView(
          children: [
            _post('Ana Ruiz', '@anaruiz', 'Added more \$DOT on the dip, the chart looks clean. CA: $_dotAddress', [
              'DOT',
            ]),
            const Divider(height: 1),
            _post('Ben Okafor', '@benok', 'Into earnings: \$AAPL and \$NVDA, sold \$SOL for cash.', [
              'AAPL',
              'NVDA',
              'SOL',
            ]),
            const Divider(height: 1),
          ],
        ),
      ),
    ],
  ),
  'trend': () => Column(
    children: [
      _header(2),
      _strip(),
      Expanded(
        child: ListView(
          children: [
            const StocksSignalBanner(cashtags: 4, folded: false, onToggle: _nothing),
            _post('Alpha Calls', '@alphacalls', '\$BTC \$ETH \$SOL \$AAPL to the moon. Free signals: t.me/alphacalls', [
              'BTC',
              'ETH',
              'SOL',
              'AAPL',
            ]),
            const Divider(height: 1),
            const StocksSignalBanner(cashtags: 3, folded: true, onToggle: _nothing),
            const Divider(height: 1),
            _post('Ben Okafor', '@benok', 'Rotation into \$NVDA continues.', ['NVDA']),
          ],
        ),
      ),
    ],
  ),
  'rows': () => SingleChildScrollView(
    child: Column(
      children: [
        StocksQuoteRow(title: 'Apple Inc.', subtitle: r'$AAPL · Stock', quote: _quotes['AAPL'], onTap: () {}),
        StocksQuoteRow(title: 'Nasdaq-100', subtitle: r'$NDX · Index', quote: _quotes['NDX'], onTap: () {}),
        StocksQuoteRow(title: 'Microsoft', subtitle: r'$MSFT · Stock', quote: null, onTap: () {}),
        StocksQuoteRow(title: 'POD', subtitle: 'Stock', quote: null, failed: true, onTap: () {}, onRetry: () {}),
        StocksQuoteRow(title: 'DOT', network: 'Base', subtitle: '0x23a2…fe51', quote: _dotQuote, onTap: () {}),
        Builder(
          builder: (context) => StocksQuoteRow(
            title: 'Solana',
            subtitle: r'$SOL · Crypto',
            quote: _quotes['SOL'],
            warning: L10n.of(context).plugin_stocks_two_readings,
            choices: _readings('SOL'),
            onTap: () {},
          ),
        ),
      ],
    ),
  ),
};

void _nothing() {}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  for (final (variant, theme, size, scale, locale) in [
    ('black-de', xLookLightsOutTheme(null), const Size(390, 844), 1.0, const Locale('de')),
    ('light-en', xLookLightTheme(null), const Size(390, 844), 1.0, const Locale('en')),
    ('de-large', xLookLightTheme(null), const Size(320, 740), 1.3, const Locale('de')),
  ]) {
    for (final MapEntry(key: board, value: build) in _boards.entries) {
      testWidgets('stocks-$board-$variant', skip: !_render, (tester) async {
        await _pump(tester, build(), theme, size, scale, locale);
        expect(tester.takeException(), isNull);
        await expectLater(
          find.byKey(const ValueKey('stocks-render')),
          matchesGoldenFile('../review-artifacts/renders/stocks-$board-$variant.png'),
        );
      });
    }
  }
}
