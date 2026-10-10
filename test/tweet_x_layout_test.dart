import 'package:dart_twitter_api/twitter_api.dart' show QuotedStatusPermalink, User;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/engagement_count.dart';
import 'package:xta/tweet/focal_post.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_action_style.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/ui/x_look_theme.dart';

TweetWithCard _post({String id = '1', String lang = 'ru', int? views = 77123, TweetWithCard? quoted}) => TweetWithCard()
  ..idStr = id
  ..fullText = 'Привет, мир'
  ..lang = lang
  ..createdAt = DateTime.utc(2026, 10, 9, 14, 12)
  ..user = (User()
    ..idStr = '9$id'
    ..name = 'Author $id'
    ..screenName = 'author$id')
  ..replyCount = 38
  ..retweetCount = 500
  ..quoteCount = 6
  ..favoriteCount = 9123
  ..viewCount = views
  ..isQuoteStatus = quoted != null
  ..quotedStatusWithCard = quoted;

Future<void> _pumpTile(WidgetTester tester, TweetWithCard tweet, {String? focalId, double width = 411}) async {
  tester.view.devicePixelRatio = 1;
  tester.view.physicalSize = Size(width, 900);
  addTearDown(tester.view.resetDevicePixelRatio);
  addTearDown(tester.view.resetPhysicalSize);
  final likes = LikedTweetModel();
  final saves = SavedTweetModel();
  addTearDown(likes.destroy);
  addTearDown(saves.destroy);

  Widget tile = TweetTile(clickable: true, tweet: tweet, tweetOpened: focalId != null);
  if (focalId != null) {
    tile = FocalPostScope(id: focalId, child: tile);
  }

  await tester.pumpWidget(
    MultiProvider(
      providers: [
        Provider<LikedTweetModel>.value(value: likes),
        Provider<SavedTweetModel>.value(value: saves),
      ],
      child: PrefService(
        service: PrefServiceCache(
          cache: {
            optionLocale: 'en',
            optionShareBaseUrl: '',
            optionNonConfirmationBiasMode: false,
            optionTweetsShowSubscribeBadge: false,
            optionUseAbsoluteTimestamp: false,
            alwaysShowFullTweetContents: true,
            optionGestureDoubleTapLike: false,
            optionThemeTrueBlack: false,
            optionThemeTrueBlackTweetCards: false,
            optionZenMode: false,
            optionCalmMode: false,
          },
        ),
        child: MaterialApp(
          theme: xLookLightsOutTheme(null),
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en')],
          home: Scaffold(body: SingleChildScrollView(child: tile)),
        ),
      ),
    ),
  );
  await tester.pumpAndSettle();
}

Rect _glyph(WidgetTester tester, IconData icon) => tester.getRect(find.byIcon(icon).first);

Finder _button(IconData icon) => find.ancestor(of: find.byIcon(icon), matching: find.byType(IconButton));

void main() {
  group('header', () {
    testWidgets('the ⋯ menu sits right after translate, level with the name', (tester) async {
      await _pumpTile(tester, _post());

      expect(find.byIcon(Icons.more_vert), findsNothing);
      expect(find.byIcon(Icons.more_horiz), findsOneWidget, reason: 'the menu moved out of the footer');

      final translate = _glyph(tester, Icons.translate);
      final menu = _glyph(tester, Icons.more_horiz);
      final name = tester.getRect(find.text('Author 1'));
      expect(translate.width, kTweetActionGlyphSize);
      expect(menu.width, kTweetActionGlyphSize);
      expect(menu.left, greaterThan(translate.right));
      expect(menu.left - translate.right, lessThanOrEqualTo(24), reason: 'no wide hole between the two glyphs');
      expect(menu.top, translate.top);
      expect((menu.top - name.top).abs(), lessThanOrEqualTo(1));
      expect(411 - menu.right, inInclusiveRange(12, 18), reason: 'the ⋯ ends near the text gutter');

      for (final icon in [Icons.translate, Icons.more_horiz]) {
        expect(tester.getSize(_button(icon)), const Size.square(kTweetTouchTarget));
      }
      expect(tester.getRect(_button(Icons.more_horiz)).left, tester.getRect(_button(Icons.translate)).right);
    });

    testWidgets('without translate the ⋯ menu stays at the right end', (tester) async {
      await _pumpTile(tester, _post(lang: 'en'));

      expect(find.byIcon(Icons.translate), findsNothing);
      final menu = _glyph(tester, Icons.more_horiz);
      expect(411 - menu.right, inInclusiveRange(12, 18));
      expect(tester.widget<IconButton>(_button(Icons.more_horiz)).tooltip, 'More info');
    });

    testWidgets('a quoted post carries no menu or footer of its own', (tester) async {
      await _pumpTile(
        tester,
        _post(
          quoted: _post(id: '2', lang: 'en'),
        ),
      );

      expect(tester.takeException(), isNull);
      expect(find.text('Author 2'), findsOneWidget);
      expect(find.byIcon(Icons.more_horiz), findsOneWidget);
      expect(find.byIcon(Icons.share), findsOneWidget);
    });

    testWidgets('a quoted post X withholds can be looked up by its permalink', (tester) async {
      final quoting =
          _post(
              quoted: TweetWithCard.tombstone({
                'text': {'text': 'This Post was deleted by the Post author. Learn more'},
              }),
            )
            ..quotedStatusIdStr = '2095934459606376826'
            ..quotedStatusPermalink = (QuotedStatusPermalink()
              ..expanded = 'https://twitter.com/quax_tests/status/2095934459606376826');
      await _pumpTile(tester, quoting);

      expect(tester.takeException(), isNull);
      expect(find.text('This Post was deleted by the Post author.'), findsOneWidget);
      expect(find.widgetWithText(TextButton, 'Open on web.archive.org'), findsOneWidget);
      expect(find.byType(TweetEmbedSurface), findsOneWidget, reason: 'one frame, not a card inside a card');
    });

    testWidgets('a subscriber-only preview says why it stops short', (tester) async {
      await _pumpTile(tester, _post(lang: 'en')..isSubscriberPreview = true, width: 320);

      expect(tester.takeException(), isNull);
      expect(find.text("Only @author1's paid subscribers on X can read the rest"), findsOneWidget);
      expect(find.byIcon(Icons.lock_outline), findsOneWidget);
    });

    testWidgets('an ordinary post carries no subscriber line', (tester) async {
      await _pumpTile(tester, _post(lang: 'en'));

      expect(find.byIcon(Icons.lock_outline), findsNothing);
    });
  });

  group('footer', () {
    testWidgets('orders reply, repost, like, views, then groups bookmark and share', (tester) async {
      final semantics = tester.ensureSemantics();
      await _pumpTile(tester, _post());

      final order = [
        Icons.chat_bubble_outline,
        Icons.format_quote,
        Icons.favorite_border,
        Icons.bar_chart,
        Icons.bookmark_border,
        Icons.share,
      ].map((icon) => _glyph(tester, icon)).toList();
      for (var i = 1; i < order.length; i++) {
        expect(order[i].left, greaterThan(order[i - 1].right), reason: 'action $i is out of order');
      }
      for (final glyph in order) {
        expect(glyph.width, kTweetActionGlyphSize);
      }

      final grouped = order[5].left - order[4].right;
      final spread = order[2].left - order[1].right;
      expect(grouped, lessThan(spread / 2), reason: 'bookmark and share sit together at the end');
      expect(411 - order[5].right, inInclusiveRange(12, 18));
      for (final label in ['38', '506', '9.1K', '77K']) {
        expect(find.text(label), findsOneWidget);
      }
      expect(tester.getSemantics(find.byType(TweetViewsCount)).label, contains('77K Views'));
      semantics.dispose();
    });

    testWidgets('a post X has not counted views for shows no views item', (tester) async {
      await _pumpTile(tester, _post(views: null));

      expect(find.byIcon(Icons.bar_chart), findsNothing);
      expect(find.byIcon(Icons.share), findsOneWidget);
    });
  });

  group('opened post', () {
    testWidgets('states time, date and views on one line instead of in the footer', (tester) async {
      await _pumpTile(tester, _post(), focalId: '1');

      final line = find.byType(TweetFocalMetaLine);
      expect(line, findsOneWidget);
      final text = tester.widget<RichText>(find.descendant(of: line, matching: find.byType(RichText)));
      expect(text.text.toPlainText(), matches(RegExp(r'^.+ · Oct 9, 2026 · 77K Views$')));
      final bold = <String>[];
      text.text.visitChildren((span) {
        if (span is TextSpan && span.style?.fontWeight == FontWeight.w700) bold.add(span.text ?? '');
        return true;
      });
      expect(bold, ['77K']);

      expect(find.byIcon(Icons.bar_chart), findsNothing, reason: 'the footer drops its views item');
      expect(find.byIcon(Icons.share), findsOneWidget);
    });

    testWidgets('omits the views part when X sent none', (tester) async {
      await _pumpTile(tester, _post(views: 0), focalId: '1');

      final line = find.byType(TweetFocalMetaLine);
      final text = tester.widget<RichText>(find.descendant(of: line, matching: find.byType(RichText)));
      expect(text.text.toPlainText(), isNot(contains('View')));
      expect(text.text.toPlainText(), contains('Oct 9, 2026'));
    });

    testWidgets('only the opened post gets the line', (tester) async {
      await _pumpTile(tester, _post(), focalId: 'another');

      expect(find.byType(TweetFocalMetaLine), findsNothing);
      expect(find.byIcon(Icons.bar_chart), findsOneWidget);
    });
  });

  group('formatEngagementCount', () {
    test('writes counts the way X does', () {
      expect([38, 506, 9123, 9190, 77123, 123456, 1234567].map((n) => formatEngagementCount(n, 'en')), [
        '38',
        '506',
        '9.1K',
        '9.1K',
        '77K',
        '123K',
        '1.2M',
      ]);
    });

    test('keeps the reader\'s separator and short forms', () {
      expect([9123, 77123, 1234567].map((n) => formatEngagementCount(n, 'de')), ['9,1K', '77K', '1,2 Mio.']);
      expect(formatEngagementCount(77123, 'fr'), '77 k');
    });

    test('an abbreviated count takes the many/other plural form', () {
      expect(viewsPluralCount(1), 1);
      expect(viewsPluralCount(77123), 1000);
    });
  });
}
