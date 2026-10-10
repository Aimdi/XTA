import 'package:dart_twitter_api/twitter_api.dart' hide Size;
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/links/link_preview_card.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/tweet/_card.dart';
import 'package:xta/ui/x_look_theme.dart';

const _article = 'https://www.washingtonpost.com/politics/2026/10/08/budget-vote/';
const _longTitle =
    'Senate leaders strike a late-night deal on the budget after weeks of '
    'talks, sending the bill back to the House with amendments that could '
    'still unravel before the weekend deadline';

Widget _fakeImage(BuildContext context, String url, int? cacheWidth) =>
    const ColoredBox(key: ValueKey('preview-image'), color: Colors.teal);

Future<void> _pump(
  WidgetTester tester,
  Widget child, {
  double width = 400,
  double textScale = 1,
  bool dark = false,
  Map<String, Object> prefs = const {},
}) async {
  tester.view.physicalSize = Size(width * 3, 2400);
  tester.view.devicePixelRatio = 3;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(
    PrefService(
      service: PrefServiceCache(cache: prefs),
      child: MaterialApp(
        theme: dark ? xLookLightsOutTheme(null) : xLookLightTheme(null),
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        home: Builder(
          builder: (context) => MediaQuery(
            data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
            child: Scaffold(
              body: SingleChildScrollView(padding: const EdgeInsets.all(16), child: child),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.pump();
}

void main() {
  group('linkPreviewLayoutFor', () {
    test('an article is compact, with or without a picture', () {
      expect(linkPreviewLayoutFor(_article, hasImage: true), LinkPreviewLayout.compact);
      expect(linkPreviewLayoutFor(_article, hasImage: false), LinkPreviewLayout.compact);
    });

    test('a video page or a photo keeps the large picture', () {
      for (final url in [
        'https://www.youtube.com/watch?v=abc',
        'https://youtu.be/abc',
        'https://m.youtube.com/watch?v=abc',
        'https://vimeo.com/123',
      ]) {
        expect(linkPreviewLayoutFor(url, hasImage: true), LinkPreviewLayout.large, reason: url);
      }
      expect(linkPreviewLayoutFor(_article, hasImage: true, kind: 'video'), LinkPreviewLayout.large);
      expect(linkPreviewLayoutFor(_article, hasImage: true, kind: 'photo'), LinkPreviewLayout.large);
    });

    test('nothing to show large means compact', () {
      expect(linkPreviewLayoutFor('https://youtu.be/abc', hasImage: false), LinkPreviewLayout.compact);
    });
  });

  test('linkDomain drops www and case', () {
    expect(linkDomain('https://WWW.WashingtonPost.com/a'), 'washingtonpost.com');
    expect(linkDomain('https://news.ycombinator.com/item'), 'news.ycombinator.com');
    expect(linkDomain('nytimes.com'), 'nytimes.com');
  });

  testWidgets('with an image: the tile shows it beside domain and title', (tester) async {
    var taps = 0;
    await _pump(
      tester,
      LinkPreviewCard(
        url: _article,
        title: 'Budget vote',
        imageUrl: 'https://img.example/budget.jpg',
        imageBuilder: _fakeImage,
        onTap: () => taps++,
      ),
    );

    expect(find.byKey(const ValueKey('preview-image')), findsOneWidget);
    expect(find.text('washingtonpost.com'), findsOneWidget);
    expect(find.text('Budget vote'), findsOneWidget);
    expect(find.byIcon(Icons.link), findsNothing);
    final tile = tester.getSize(find.byKey(const ValueKey('preview-image')));
    expect(tile, const Size(kLinkPreviewTileSize, kLinkPreviewTileSize));
    final card = tester.getSize(find.byType(LinkPreviewCard));
    expect(card.height, greaterThanOrEqualTo(48));
    expect(card.height, lessThan(100), reason: 'compact, not a hero image');

    await tester.tap(find.byType(LinkPreviewCard));
    expect(taps, 1);
  });

  testWidgets('without an image: a link glyph holds the tile', (tester) async {
    await _pump(tester, LinkPreviewCard(url: _article, title: 'Budget vote', onTap: () {}));

    expect(find.byIcon(Icons.link), findsOneWidget);
    expect(find.text('washingtonpost.com'), findsOneWidget);
  });

  testWidgets('a missing title falls back to the address', (tester) async {
    await _pump(tester, LinkPreviewCard(url: _article, onTap: () {}));

    expect(find.text('www.washingtonpost.com/politics/2026/10/08/budget-vote/'), findsOneWidget);
  });

  testWidgets('screen readers hear one link: domain and title', (tester) async {
    final handle = tester.ensureSemantics();
    await _pump(tester, LinkPreviewCard(url: _article, title: 'Budget vote', onTap: () {}));

    final node = tester.getSemantics(find.byType(LinkPreviewCard));
    expect(node.label, 'washingtonpost.com, Budget vote');
    expect(node.flagsCollection.isLink, isTrue);
    handle.dispose();
  });

  testWidgets('a long title fits a 320dp phone at double text size in true black', (tester) async {
    await _pump(
      tester,
      LinkPreviewCard(
        url: _article,
        title: _longTitle,
        imageUrl: 'https://img.example/budget.jpg',
        imageBuilder: _fakeImage,
        onTap: () {},
      ),
      width: 320,
      textScale: 2,
      dark: true,
    );

    expect(tester.takeException(), isNull);
    final title = tester.widget<Text>(find.text(_longTitle));
    expect(title.maxLines, 3);
    expect(title.overflow, TextOverflow.ellipsis);
    expect(Theme.of(tester.element(find.byType(LinkPreviewCard))).brightness, Brightness.dark);
  });

  testWidgets('the large layout keeps the picture across the card', (tester) async {
    await _pump(
      tester,
      LinkPreviewCard(
        url: 'https://www.youtube.com/watch?v=abc',
        title: 'A video',
        description: 'Watch it',
        imageUrl: 'https://img.example/v.jpg',
        imageBuilder: _fakeImage,
        layout: LinkPreviewLayout.large,
        onTap: () {},
      ),
    );

    final image = tester.getSize(find.byKey(const ValueKey('preview-image')));
    expect(image.width, greaterThan(kLinkPreviewTileSize * 2));
    expect(find.text('Watch it'), findsOneWidget);
  });

  group('X cards', () {
    TweetWithCard tweet() => TweetWithCard()
      ..idStr = '1'
      ..entities = Entities.fromJson({'urls': []});

    Map<String, dynamic> card(String name, String url) => {
      'name': name,
      'url': url,
      'binding_values': {
        'title': {'string_value': 'Budget vote'},
        'description': {'string_value': 'What changed'},
        'vanity_url': {'string_value': 'washingtonpost.com'},
      },
    };

    for (final name in ['summary', 'summary_large_image']) {
      testWidgets('an article in a $name card is the compact row', (tester) async {
        await _pump(
          tester,
          TweetCard(tweet: tweet(), card: card(name, _article)),
          prefs: {optionImageQuality: 'disabled'},
        );

        expect(find.byType(LinkPreviewCard), findsOneWidget);
        expect(find.text('washingtonpost.com'), findsOneWidget);
        expect(find.text('Budget vote'), findsOneWidget);
      });
    }
  });

  group('plugin cards', () {
    const launcher = MethodChannel('plugins.flutter.io/url_launcher');
    const resolver = MethodChannel('browser_resolver');
    final launched = <String>[];

    setUp(() {
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(launcher, (call) async {
        launched.add((call.arguments as Map)['url'] as String);
        return true;
      });
      messenger.setMockMethodCallHandler(resolver, (_) async => null);
    });

    tearDown(() {
      final messenger = TestDefaultBinaryMessengerBinding.instance.defaultBinaryMessenger;
      messenger.setMockMethodCallHandler(launcher, null);
      messenger.setMockMethodCallHandler(resolver, null);
      launched.clear();
    });

    const post = MastodonPost(
      id: 'p1',
      acct: 'maya@studio.example',
      authorName: 'Maya Chen',
      text: 'Worth a read',
      url: 'https://studio.example/@maya/1',
      repliesCount: 3,
      linkCard: MastodonLinkCard(url: _article, title: 'Budget vote', description: 'What changed'),
    );

    testWidgets('a Mastodon article link is the shared compact row', (tester) async {
      await _pump(tester, const MastodonPostCard(post: post));

      expect(find.byType(LinkPreviewCard), findsOneWidget);
      expect(find.text('washingtonpost.com'), findsOneWidget);
      expect(find.text('What changed'), findsNothing, reason: 'compact row');
    });

    testWidgets('with a browser chosen in settings, the link leaves the app', (tester) async {
      await _pump(tester, const MastodonPostCard(post: post), prefs: {optionOpenLinksInEmbeddedBrowser: false});

      await tester.tap(find.byType(LinkPreviewCard));
      await tester.pumpAndSettle();

      expect(launched, [_article]);
    });
  });
}
