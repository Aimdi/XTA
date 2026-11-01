import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_markets.dart';
import 'package:xta/plugins/stocks/stocks_watchlist_reel.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Renders Markets and the watchlist strip in every row state for review:
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
TickerQuote _index(String symbol, double price, double before, {required bool open}) => TickerQuote(
  symbol: symbol,
  currency: 'USD',
  price: price,
  previousClose: before,
  instrumentType: 'INDEX',
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

final _quotes = <String, TickerQuote>{
  'SPX': _index('^GSPC', 6738.44, 6712.10, open: false),
  'NDX': _index('^NDX', 24172.03, 24063.40, open: false),
  'VIX': _index('^VIX', 16.91, 17.44, open: true),
  'DAX': _index('^GDAXI', 23958.30, 24021.70, open: true),
  'FTSE': _index('^FTSE', 9717.25, 9760.06, open: false),
  'N225': _index('^N225', 52411.34, 51325.61, open: false),
  'BTC': _coin('BTC-USD', 110412.5, 109798.0),
  'ETH': _coin('ETH-USD', 3887.12, 3951.40),
};

Future<void> _pump(WidgetTester tester, Widget child, ThemeData theme, Size size, double scale, Locale locale) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    MaterialApp(
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
        body: SafeArea(
          child: RepaintBoundary(key: const ValueKey('stocks-render'), child: child),
        ),
      ),
    ),
  );
  // Loading rows shimmer for as long as they load; a few frames show them.
  await tester.pump(const Duration(milliseconds: 400));
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  for (final (name, theme, size, scale, locale) in [
    ('light', xLookLightTheme(null), const Size(390, 844), 1.0, const Locale('en')),
    ('black', xLookLightsOutTheme(null), const Size(390, 844), 1.0, const Locale('en')),
    ('de', xLookLightTheme(null), const Size(393, 852), 1.0, const Locale('de')),
    ('de-large', xLookLightTheme(null), const Size(320, 740), 1.3, const Locale('de')),
  ]) {
    testWidgets('stocks-markets-$name', skip: !_render, (tester) async {
      await _pump(
        tester,
        Column(
          children: [
            StocksWatchlistReel(
              symbols: const ['AAPL', 'POD', 'ETH', 'NDX'],
              quotes: {'NDX': _quotes['NDX']!, 'ETH': _quotes['ETH']!},
              failed: const {'POD'},
              onSelected: (_) {},
            ),
            const Divider(height: 1),
            Expanded(
              child: StocksMarketsList(quotes: _quotes, failed: const {'RUT'}, onRetry: (_) {}),
            ),
          ],
        ),
        theme,
        size,
        scale,
        locale,
      );
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('stocks-render')),
        matchesGoldenFile('../review-artifacts/renders/stocks-markets-$name.png'),
      );
    });
  }
}
