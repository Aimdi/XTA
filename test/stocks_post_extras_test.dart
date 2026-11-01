import 'package:dart_twitter_api/twitter_api.dart' show Entities;
import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/stocks/stocks_signal_posts.dart';
import 'package:xta/tweet/cashtag_quotes.dart';
import 'package:xta/tweet/ticker/ticker_quote.dart';
import 'package:xta/tweet/ticker/ticker_quote_cache.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/rich_text.dart';

const _evm = '0x23a2b8f3c1d4e5f60718293a4b5c6d7e8f90fe51';
const _mint = 'EKpQGSJtjMFqKZ9KQanSqYXRcF8fBopzLHYxdM65zcjm';

Widget _app(Widget child, {Locale? locale}) => MaterialApp(
  theme: xLookLightTheme(null),
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
    testWidgets('is cut in the middle, in the link colour, and copies whole on tap', (tester) async {
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

      expect(find.textContaining('0x23a2…fe51', findRichText: true), findsOneWidget);
      expect(find.textContaining(_evm, findRichText: true), findsNothing, reason: 'the 42 characters took two lines');
      expect(find.textContaining('EKpQ…zcjm', findRichText: true), findsOneWidget);
      final span = _spanShowing(parts, '0x23a2…fe51')!;
      expect(span.style?.color, Theme.of(tester.element(find.byType(Scaffold))).colorScheme.secondary);

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

  testWidgets('the quote chips under a post share a row at 360dp', (tester) async {
    tester.view.physicalSize = const Size(360, 640);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);
    TickerQuote quote(String symbol, double price, double before) =>
        TickerQuote(symbol: symbol, currency: 'USD', price: price, previousClose: before, points: const []);
    final cache = _Quotes()
      ..remember('AAPL', quote('AAPL', 200, 190))
      ..remember('NVDA', quote('NVDA', 120, 125))
      ..remember('TSLA', quote('TSLA', 250, 250));
    addTearDown(cache.destroy);

    await tester.pumpWidget(
      _app(
        Provider<TickerQuoteCache>.value(
          value: cache,
          child: const Align(
            alignment: Alignment.topCenter,
            child: CashtagQuotesBar(symbols: ['AAPL', 'NVDA', 'TSLA']),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();

    final chips = find.byType(ActionChip);
    expect(chips, findsNWidgets(3));
    final boxes = [for (final chip in chips.evaluate()) tester.getRect(find.byWidget(chip.widget))];
    expect(boxes[1].top, boxes[0].top, reason: 'chips share a line rather than stacking');
    expect(boxes.last.bottom - boxes.first.top, lessThanOrEqualTo(2 * 48), reason: 'three chips, two lines at most');
    for (final box in boxes) {
      expect(box.height, greaterThanOrEqualTo(48), reason: 'a touch target');
    }
    expect(find.textContaining(r'$AAPL 200.00 +5.26%', findRichText: true), findsOneWidget);
    expect(tester.takeException(), isNull);
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
