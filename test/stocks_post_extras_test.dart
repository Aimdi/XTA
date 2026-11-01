import 'package:dart_twitter_api/twitter_api.dart' show Entities;
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/crypto_asset.dart';
import 'package:xta/plugins/stocks/stocks_format.dart';
import 'package:xta/plugins/stocks/stocks_signal_banner.dart';
import 'package:xta/plugins/stocks/stocks_signal_posts.dart';
import 'package:xta/plugins/stocks/stocks_store.dart';
import 'package:xta/plugins/stocks/stocks_quote_row.dart';
import 'package:xta/tweet/cashtag_quotes.dart';
import 'package:xta/tweet/ticker_screen.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/rich_text.dart';

const _evm = '0x23a2b8f3c1d4e5f60718293a4b5c6d7e8f90fe51';
const _mint = 'EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm';

Widget _app(Widget child, {Locale? locale, ThemeData? theme}) => MaterialApp(
  theme: theme ?? xLookLightTheme(null),
  locale: locale,
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(body: child),
);

TextSpan? _spanShowing(List<RichTextPart> parts, String text) =>
    parts.map((part) => part.entity).whereType<TextSpan>().where((span) => span.text == text).firstOrNull;

/// Serves remembered quotes and never reaches the network.
class _Quotes extends TickerQuoteCache {
  @override
  Future<void> ensure(Iterable<String> symbols) async {}

  /// As a lookup that found nothing would leave it.
  void failFor(String symbol) {
    recordFailure(symbol);
    update(state, force: true);
  }
}

/// A watchlist of tokens, without a database.
class _Watched extends StocksWatchlistStore {
  final Map<String, CryptoAsset> assets;

  _Watched(this.assets);

  @override
  Map<String, CryptoAsset> get cryptoAssets => assets;
}

TweetChain _chain(String id, {List<String> cashtags = const [], List<String> links = const []}) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..entities = Entities.fromJson({
      'symbols': [
        for (final tag in cashtags)
          {
            'text': tag,
            'indices': [0, tag.length + 1],
          },
      ],
      'urls': [
        for (final link in links)
          {
            'url': 'https://t.co/x',
            'expanded_url': link,
            'display_url': link,
            'indices': [0, 1],
          },
      ],
    });
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  setUpAll(() async {
    // The app's own font, so widths are what a phone draws, not test squares.
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
  });

  group('a contract address in a post', () {
    testWidgets('is cut in the middle, in the secondary colour, and copies whole on tap', (tester) async {
      final copied = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied.add((call.arguments as Map)['text'] as String);
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      late List<RichTextPart> parts;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) {
              parts = buildRichText(context, 'CA: $_evm, and on Solana $_mint.', null);
              return Text.rich(TextSpan(children: displayRichText(parts)));
            },
          ),
        ),
      );

      expect(find.textContaining('0x23a2…\u2060fe51', findRichText: true), findsOneWidget, reason: 'unbroken by the cut');
      expect(find.textContaining(_evm, findRichText: true), findsNothing, reason: 'the 42 characters took two lines');
      expect(find.textContaining('EKpQ…\u2060zcjm', findRichText: true), findsOneWidget);
      final span = _spanShowing(parts, '0x23a2…\u2060fe51')!;
      expect(span.style?.color, Theme.of(tester.element(find.byType(Scaffold))).colorScheme.onSurfaceVariant);

      (span.recognizer! as TapGestureRecognizer).onTap!();
      await tester.pump();
      expect(copied, [_evm]);
      expect(find.text('Contract address copied'), findsOneWidget);
      disposeRichTextParts(parts);
    });

    testWidgets('a long word without a digit is only a word', (tester) async {
      late List<RichTextPart> parts;
      const word = 'Supercalifragilisticexpialidociousness';
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) {
              parts = buildRichText(context, 'So $word', null);
              return const SizedBox.shrink();
            },
          ),
        ),
      );

      expect(parts.map((part) => part.plainText).join(), 'So $word');
      expect(parts.every((part) => part.entity == null), isTrue);
    });
  });

  testWidgets("a watched token's address leads with its network", (tester) async {
    final watched = _Watched({
      'base': const CryptoAsset(chain: 'base', address: _evm, symbol: 'DOT', name: 'Dot'),
    });
    addTearDown(watched.destroy);
    late List<RichTextPart> parts;
    await tester.pumpWidget(
      _app(
        Provider<StocksWatchlistStore>.value(
          value: watched,
          child: Builder(
            builder: (context) {
              parts = buildRichText(context, 'CA: ${_evm.toUpperCase().replaceFirst('0X', '0x')} and $_mint', null);
              return Text.rich(TextSpan(children: displayRichText(parts)));
            },
          ),
        ),
      ),
    );

    expect(find.textContaining('Base\u00a0·\u00a00x23A2…\u2060FE51', findRichText: true), findsOneWidget, reason: 'any casing');
    expect(find.textContaining('·\u00a0EKpQ', findRichText: true), findsNothing, reason: 'not watched: no network');
    disposeRichTextParts(parts);
  });

  group('a cashtag in a post', () {
    TickerQuote quote(String symbol, double price) =>
        TickerQuote(symbol: symbol, currency: 'USD', price: price, previousClose: 100, points: const []);
    const symbols = ['AAPL', 'NVDA', 'TSLA', 'MSFT', 'AMZN'];
    final text = symbols.map((symbol) => '\$$symbol').join(' ');
    final entities = Entities.fromJson({
      'symbols': [
        for (final (index, symbol) in symbols.indexed)
          {
            'text': symbol,
            'indices': [index * 6, index * 6 + 5],
          },
      ],
    });

    testWidgets('reads as a plain link, without its move', (tester) async {
      final cache = _Quotes();
      addTearDown(cache.destroy);
      for (final symbol in symbols) {
        cache.remember(symbol, quote(symbol, 105));
      }
      late List<RichTextPart> parts;
      await tester.pumpWidget(
        _app(
          Provider<TickerQuoteCache>.value(
            value: cache,
            child: Builder(
              builder: (context) {
                parts = buildRichText(context, text, entities);
                return Text.rich(TextSpan(children: displayRichText(parts)));
              },
            ),
          ),
        ),
      );

      expect(find.text(r'$AAPL'), findsOneWidget);
      expect(find.textContaining('%', findRichText: true), findsNothing, reason: 'the strip carries the moves');
      disposeRichTextParts(parts);
    });

    testWidgets('is drawn at the size of the words around it, at any text size', (tester) async {
      late List<RichTextPart> parts;
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(context).copyWith(textScaler: const TextScaler.linear(1.3)),
              child: Builder(
                builder: (context) {
                  parts = buildRichText(context, text, entities);
                  return Column(
                    children: [
                      Text.rich(TextSpan(children: displayRichText(parts))),
                      const Text('AAPL'),
                    ],
                  );
                },
              ),
            ),
          ),
        ),
      );

      final words = tester.getRect(find.text('AAPL')).height;
      expect(tester.getRect(find.text(r'$AAPL')).height, closeTo(words, words * 0.1), reason: 'not scaled twice');
      disposeRichTextParts(parts);
    });
  });

  group('the price strip', () {
    final now = DateTime.now();
    TickerQuote live(String symbol, double price) => TickerQuote(
      symbol: symbol,
      currency: 'USD',
      price: price,
      previousClose: 100,
      regularPeriod: TickerTradingPeriod(
        start: now.subtract(const Duration(hours: 2)),
        end: now.add(const Duration(hours: 2)),
      ),
      points: [
        for (var i = 0; i < 6; i++)
          TickerPoint(at: now.subtract(Duration(minutes: 60 - i * 10)), close: 100 + (price - 100) * i / 5),
      ],
    );
    TickerQuote closed(String symbol, double price) => TickerQuote(
      symbol: symbol,
      currency: 'USD',
      price: price,
      previousClose: 100,
      regularMarketTime: now.subtract(const Duration(days: 1)),
      regularPeriod: TickerTradingPeriod(
        start: now.subtract(const Duration(hours: 30)),
        end: now.subtract(const Duration(hours: 24)),
      ),
      points: const [],
    );

    Future<void> pumpStrip(WidgetTester tester, TickerQuoteCache cache, List<String> symbols, {double scale = 1}) async {
      tester.view.physicalSize = const Size(320, 640);
      tester.view.devicePixelRatio = 1;
      addTearDown(tester.view.reset);
      await tester.pumpWidget(
        _app(
          Builder(
            builder: (context) => MediaQuery(
              data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
              child: Provider<TickerQuoteCache>.value(
                value: cache,
                child: Align(alignment: Alignment.topCenter, child: CashtagStrip(symbols: symbols)),
              ),
            ),
          ),
        ),
      );
      await tester.pump();
    }

    testWidgets('lists the first four cashtags in order, each with its price and move', (tester) async {
      final cache = _Quotes();
      addTearDown(cache.destroy);
      for (final symbol in ['AAPL', 'NVDA', 'TSLA', 'MSFT', 'AMZN']) {
        cache.remember(symbol, live(symbol, symbol == 'NVDA' ? 95 : 105));
      }
      await pumpStrip(tester, cache, ['AAPL', 'NVDA', 'TSLA', 'MSFT', 'AMZN']);

      final xs = [for (final symbol in ['AAPL', 'NVDA', 'TSLA', 'MSFT']) tester.getTopLeft(find.text(symbol)).dx];
      expect(xs, orderedEquals([...xs]..sort()), reason: 'in the order the post names them');
      expect(find.text('AMZN'), findsNothing, reason: 'four at most');
      expect(find.text('+5.00%'), findsNWidgets(3));
      expect(find.text('−5.00%'), findsOneWidget);
      final context = tester.element(find.byType(CashtagStrip));
      expect(tester.widget<Text>(find.text('−5.00%')).style!.color, stockTrendColour(context, -5));
      expect(find.bySemanticsLabel(RegExp(r'^\$AAPL, ')), findsOneWidget);
    });

    testWidgets('a grey bar while loading, No price without one, a closed market in grey', (tester) async {
      final cache = _Quotes();
      addTearDown(cache.destroy);
      cache
        ..remember('MSFT', closed('MSFT', 105))
        ..failFor('NVDA');
      await pumpStrip(tester, cache, ['TSLA', 'NVDA', 'MSFT']);

      expect(find.byType(StocksFiguresSkeleton), findsOneWidget, reason: r'$TSLA loads');
      expect(find.text('No price'), findsOneWidget, reason: r'$NVDA has none');
      final muted = Theme.of(tester.element(find.byType(CashtagStrip))).colorScheme.onSurfaceVariant;
      expect(tester.widget<Text>(find.text('+5.00%')).style!.color, muted, reason: 'a closed market is not news');
      expect(find.byType(StocksSparkline), findsNothing, reason: 'no line for a closed market');
    });

    testWidgets('a tap opens the ticker page', (tester) async {
      final cache = _Quotes();
      addTearDown(cache.destroy);
      cache.remember('AAPL', live('AAPL', 105));
      final opened = <Object?>[];
      await tester.pumpWidget(
        MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
          ],
          onGenerateRoute: (settings) {
            if (settings.name == routeTicker) opened.add((settings.arguments as TickerScreenArguments).symbol);
            return MaterialPageRoute(builder: (_) => const SizedBox.shrink(), settings: settings);
          },
          home: Scaffold(
            body: Provider<TickerQuoteCache>.value(value: cache, child: const CashtagStrip(symbols: ['AAPL'])),
          ),
        ),
      );
      await tester.pump();

      await tester.tap(find.text('AAPL'));
      await tester.pumpAndSettle();
      expect(opened, ['AAPL']);
    });

    testWidgets('fits 320 dp at 1.3x text, scrolling sideways', (tester) async {
      final cache = _Quotes();
      addTearDown(cache.destroy);
      for (final symbol in ['GOOGL', 'NVDA', 'TSLA', 'MSFT']) {
        cache.remember(symbol, live(symbol, 1234.56));
      }
      await pumpStrip(tester, cache, ['GOOGL', 'NVDA', 'TSLA', 'MSFT'], scale: 1.3);

      expect(tester.takeException(), isNull);
      expect(tester.getSize(find.byType(CashtagStrip)).height, greaterThanOrEqualTo(48));
    });

    testWidgets('a post without cashtags has none', (tester) async {
      final cache = _Quotes();
      addTearDown(cache.destroy);
      await pumpStrip(tester, cache, const []);

      expect(find.byType(SingleChildScrollView), findsNothing);
    });
  });

  group('the signal-group banner', () {
    Widget feed(StocksSignalFolds folds, TweetChain chain, {bool fold = false, ThemeData? theme}) => PrefService(
      service: PrefServiceCache(cache: {optionPluginStocksFoldSignalPosts: fold}),
      child: _app(stocksSignalChain(folds, chain, const Text('the post')), theme: theme),
    );
    TweetChain signal({int cashtags = 4}) => _chain(
      's',
      cashtags: ['BTC', 'ETH', 'SOL', 'DOGE', 'XRP'].take(cashtags).toList(),
      links: ['https://t.me/pumps'],
    );

    testWidgets('sits over a signal post; Hide folds the post behind the line, a tap opens it', (tester) async {
      final folds = StocksSignalFolds();
      addTearDown(folds.destroy);
      await tester.pumpWidget(feed(folds, signal()));

      expect(find.text('Possible signal group: Telegram invite and 4 cashtags'), findsOneWidget);
      expect(find.text('the post'), findsOneWidget);
      await tester.tap(find.text('Hide'));
      await tester.pumpAndSettle();
      expect(find.text('the post'), findsNothing);
      expect(find.text('Possible signal group: Telegram invite and 4 cashtags'), findsOneWidget, reason: 'the line stays');

      await tester.tap(find.byKey(const ValueKey('stocks-signal-open')));
      await tester.pumpAndSettle();
      expect(find.text('the post'), findsOneWidget);
      expect(folds.state, {'s': false}, reason: 'the choice is kept for the post, not the tile');
    });

    testWidgets('with Fold signal-group posts on, they arrive folded', (tester) async {
      final folds = StocksSignalFolds();
      addTearDown(folds.destroy);
      await tester.pumpWidget(feed(folds, signal(cashtags: 3), fold: true));

      expect(find.text('the post'), findsNothing);
      expect(find.text('Possible signal group: Telegram invite and 3 cashtags'), findsOneWidget);
    });

    testWidgets('any other post is left as it is', (tester) async {
      final folds = StocksSignalFolds();
      addTearDown(folds.destroy);
      await tester.pumpWidget(feed(folds, _chain('n', cashtags: ['AAPL'])));

      expect(find.text('the post'), findsOneWidget);
      expect(find.byType(StocksSignalBanner), findsNothing);
    });

    for (final (name, theme) in [('light', xLookLightTheme(null)), ('black', xLookLightsOutTheme(null))]) {
      testWidgets('reads in $name', (tester) async {
        final folds = StocksSignalFolds();
        addTearDown(folds.destroy);
        await tester.pumpWidget(feed(folds, signal(), theme: theme));

        final banner = find.byType(StocksSignalBanner);
        final tint = tester.widget<Container>(find.descendant(of: banner, matching: find.byType(Container)).first).color!;
        final ink = tester.widget<Text>(find.text('Possible signal group: Telegram invite and 4 cashtags')).style!.color!;
        expect(contrastRatio(ink, tint), greaterThanOrEqualTo(4.5));
        expect(tester.getSize(banner).height, greaterThanOrEqualTo(48));
      });
    }
  });

  group('signal-group posts', () {
    test('a Telegram invite with three or more cashtags is one; either alone is not', () {
      bool signal(TweetChain chain) => isSignalGroupPost(chain.tweets.single);

      expect(signal(_chain('a', cashtags: ['BTC', 'ETH', 'SOL'], links: ['https://t.me/pumps'])), isTrue);
      expect(signal(_chain('b', cashtags: ['BTC', 'ETH', 'SOL'], links: ['https://telegram.me/pumps'])), isTrue);
      expect(signal(_chain('c', cashtags: ['BTC', 'ETH'], links: ['https://t.me/pumps'])), isFalse);
      expect(signal(_chain('d', cashtags: ['BTC', 'ETH', 'SOL'], links: ['https://example.com/t.me'])), isFalse);
      expect(signal(_chain('e', cashtags: ['BTC', 'ETH', 'SOL'])), isFalse);
    });

    test('go to the end of their page, both groups in their order, nothing hidden', () {
      final page = [
        _chain('1', cashtags: ['A', 'B', 'C'], links: ['https://t.me/x']),
        _chain('2', cashtags: ['AAPL']),
        _chain('3', cashtags: ['D', 'E', 'F'], links: ['http://www.t.me/y']),
        _chain('4'),
      ];

      expect([for (final chain in signalGroupPostsLast(page)) chain.id], ['2', '4', '1', '3']);
    });
  });
}
