import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/home/home_timeline_controls.dart';
import 'package:xta/plugins/reddit/reddit_interleaved.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
import 'package:xta/tweet/focal_post.dart';
import 'package:xta/tweet/thread_rail.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'package:xta/tweet/tweet_header.dart';
import 'package:xta/tweet/tweet_ink_layout.dart';
import 'package:xta/tweet/tweet_post_menu.dart';
import 'package:xta/ui/provenance_accent.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/feed_ink_harness.dart';

/// docs/specs/feed-ink.md
void main() {
  final theme = xLookLightsOutTheme(null);

  // The theme's own typeface, so a line holds what it holds on a phone: the
  // test font draws every letter a whole em wide.
  setUpAll(() async {
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
  });

  Widget list(List<Widget> children) => Scaffold(body: ListView(children: children));

  Rect avatarOf(WidgetTester tester) =>
      tester.getRect(find.descendant(of: find.byType(TweetAvatarTarget), matching: find.byType(SizedBox)).last);

  group('a post in a timeline', () {
    testWidgets('takes two columns: the avatar at 16 dp, everything else at 68 dp', (tester) async {
      await pumpInk(tester, list([TweetTile(clickable: true, tweet: inkXPost())]), theme: theme);

      final target = tester.getRect(find.byType(TweetAvatarTarget));
      expect(target.size, const Size.square(48));
      expect(avatarOf(tester), Rect.fromLTWH(16, target.top + 4, 40, 40));
      expect(tester.getTopLeft(find.text('Clash Report')).dx, 68);
      expect(tester.getTopLeft(find.byType(RichText).at(_textIndex(tester))).dx, 68);
      final footer = tester.getRect(find.byType(TweetFooterBar));
      expect(footer.left, greaterThanOrEqualTo(kInkBodyIndent));
      expect(
        footer.top,
        greaterThan(tester.getRect(find.textContaining('Russia has extended', findRichText: true)).top),
      );
      expect(find.byType(TweetHeader), findsNothing);
    });

    testWidgets('names the author, handle and time on one line', (tester) async {
      await pumpInk(
        tester,
        list([TweetTile(clickable: true, tweet: inkXPost())]),
        theme: theme,
        locale: const Locale('en'),
      );

      final name = tester.getRect(find.text('Clash Report'));
      final handle = tester.getRect(find.text('@clashreport'));
      final text = tester.getRect(find.textContaining('Russia has extended', findRichText: true));
      expect(handle.center.dy, closeTo(name.center.dy, 1));
      expect(handle.left, greaterThan(name.right));
      expect(text.top - name.bottom, lessThan(8), reason: 'the text starts right under the author line');
    });

    testWidgets('gives way with the handle first, then the name, and keeps the time', (tester) async {
      final long = inkXPost()..user!.name = 'A very long display name that cannot fit';
      await pumpInk(
        tester,
        list([TweetTile(clickable: true, tweet: long)]),
        theme: theme,
        locale: const Locale('en'),
        size: const Size(320, 700),
      );

      expect(tester.takeException(), isNull);
      expect(find.text('@clashreport'), findsNothing);
      final time = find.descendant(of: find.byType(TweetAuthorLine), matching: find.textContaining('m'));
      expect(time, findsWidgets);
    });

    testWidgets('with large text puts the handle and time on a second line', (tester) async {
      await pumpInk(
        tester,
        list([TweetTile(clickable: true, tweet: inkXPost())]),
        theme: theme,
        locale: const Locale('en'),
        scale: 1.3,
      );

      expect(tester.takeException(), isNull);
      expect(
        tester.getRect(find.text('@clashreport')).top,
        greaterThanOrEqualTo(tester.getRect(find.text('Clash Report')).bottom),
      );
    });

    testWidgets('keeps translate and the ⋯ menu at 48 dp, on the author line', (tester) async {
      await pumpInk(tester, list([TweetTile(clickable: true, tweet: inkXPost())]), theme: theme);

      final menu = tester.getRect(find.byType(TweetPostMenuButton));
      expect(menu.size, const Size.square(48));
      expect(menu.right, 390);
      expect(menu.top, kInkColumnTop);
      expect(tester.getSize(find.byType(TweetTranslateButton)), const Size.square(48));
      await tester.tap(find.byType(TweetPostMenuButton));
      await tester.pumpAndSettle();
      expect(find.byType(BottomSheet), findsOneWidget);
    });

    testWidgets('the opened post keeps its own layout', (tester) async {
      await pumpInk(
        tester,
        list([
          FocalPostScope(
            id: '42',
            child: TweetTile(clickable: true, tweet: inkXPost(), tweetOpened: true),
          ),
        ]),
        theme: theme,
      );

      expect(find.byType(TweetHeader), findsOneWidget);
      expect(find.byType(TweetInkLayout), findsNothing);
    });

    testWidgets('a quoted post keeps its compact header inside the post', (tester) async {
      final quoting = inkXPost()
        ..isQuoteStatus = true
        ..quotedStatusWithCard = (inkXPost(text: 'The quoted post')..idStr = '43');
      await pumpInk(tester, list([TweetTile(clickable: true, tweet: quoting)]), theme: theme);

      expect(find.byType(TweetInkLayout), findsOneWidget);
      expect(find.byType(TweetHeader), findsOneWidget);
    });
  });

  testWidgets('a self-thread runs its rail through the avatars, in the same columns', (tester) async {
    await pumpInk(
      tester,
      list([
        TweetTile(clickable: true, tweet: inkXPost(), threadConnectBottom: true),
        TweetTile(clickable: true, tweet: inkXPost(text: 'And the second post')..idStr = '44', threadConnectTop: true),
      ]),
      theme: theme,
    );

    expect(find.byType(TweetInkLayout), findsNWidgets(2));
    expect(find.byType(ThreadRailLines), findsNWidgets(2));
    final avatars = find.descendant(of: find.byType(TweetAvatarTarget), matching: find.byType(SizedBox));
    final first = tester.getRect(avatars.at(1));
    expect(first.left, 16);
    expect(first.width, 40);
    expect(kPostRailGeometry.lineX + kThreadRailLineWidth / 2, first.center.dx);
    expect(tester.getTopLeft(find.textContaining('And the second post', findRichText: true)).dx, 68);
  });

  group('a Reddit post', () {
    testWidgets('takes the same two columns with a rounded-square picture', (tester) async {
      await pumpInk(tester, list([RedditPostCard(post: inkRedditPost(picture: false))]), theme: theme);

      final target = tester.getRect(find.byType(TweetAvatarTarget));
      expect(target.left, kInkAvatarTargetStart);
      final subreddit = tester.getRect(find.text('r/EconomyCharts'));
      final author = tester.getRect(find.text('u/Illustrious_Gap1681'));
      expect(subreddit.left, 68);
      expect(author.center.dy, closeTo(subreddit.center.dy, 1));
      final title = find.textContaining('This is a crazy graph');
      expect(tester.getTopLeft(title).dx, 68);
      final style = tester.widget<Text>(title).style!;
      expect(style.fontWeight, FontWeight.w400);
      expect(style.fontSize, 15);
      final clip = tester.widget<ClipRRect>(
        find.descendant(of: find.byType(TweetAvatarTarget), matching: find.byType(ClipRRect)).first,
      );
      expect(clip.borderRadius, BorderRadius.circular(10));
    });

    testWidgets('spreads upvote, comments, save and open across the column', (tester) async {
      await pumpInk(tester, list([RedditPostCard(post: inkRedditPost(picture: false))]), theme: theme);

      final up = tester.getCenter(find.text('45'));
      final comments = tester.getCenter(find.text('13'));
      final save = tester.getCenter(find.byIcon(Icons.bookmark_border));
      final open = tester.getCenter(find.byIcon(Icons.open_in_new));
      expect(up.dx, lessThan(comments.dx));
      expect(comments.dx, lessThan(save.dx));
      expect(save.dx, lessThan(open.dx));
      expect(open.dx, closeTo(390 - 24, 1));
      expect(up.dx, greaterThan(68));
    });
  });

  group('where a post came from', () {
    testWidgets('a Reddit post in a mixed timeline carries the badge, not the strip', (tester) async {
      final semantics = tester.ensureSemantics();
      await pumpInk(
        tester,
        Builder(
          builder: (context) => list([
            redditInterleavedItems([inkRedditPost(picture: false)]).single.build(context),
          ]),
        ),
        theme: theme,
      );

      final badge = find.byKey(const ValueKey('provenance-badge-$pluginIdReddit'));
      expect(badge, findsOneWidget);
      final avatar = tester.getRect(
        find.descendant(of: find.byType(TweetAvatarTarget), matching: find.byType(ProvenanceAvatarBadge)),
      );
      expect(tester.getRect(badge).bottomRight, avatar.bottomRight + const Offset(4, 4));
      expect(find.bySemanticsLabel(RegExp(r'^r/EconomyCharts, Reddit$')), findsOneWidget);
      expect(_strips(tester), isEmpty);
      semantics.dispose();
    });

    testWidgets('the badge sits on the bottom left in a right-to-left language', (tester) async {
      await pumpInk(
        tester,
        Scaffold(
          body: Center(
            child: ProvenanceAvatarBadge(
              pluginId: pluginIdReddit,
              child: Container(width: 40, height: 40, color: Colors.grey),
            ),
          ),
        ),
        theme: theme,
        direction: TextDirection.rtl,
      );

      final avatar = tester.getRect(find.byType(Container).first);
      final badge = tester.getRect(find.byKey(const ValueKey('provenance-badge-$pluginIdReddit')));
      expect(badge.bottomLeft, avatar.bottomLeft + const Offset(-4, 4));
    });

    testWidgets('a post without an avatar keeps the strip', (tester) async {
      final item = provenanceInterleavedItem(
        date: DateTime(2026),
        pluginId: pluginIdSubstack,
        build: (_) => const SizedBox(height: 40),
      );
      await pumpInk(tester, Builder(builder: (context) => list([item.build(context)])), theme: theme);

      expect(_strips(tester), hasLength(1));
    });

    testWidgets('outside a mixed timeline a Reddit post has no badge', (tester) async {
      await pumpInk(
        tester,
        list([RedditPostCard(post: inkRedditPost(picture: false), showSourceBadge: false)]),
        theme: theme,
      );

      expect(find.byKey(const ValueKey('provenance-badge-$pluginIdReddit')), findsNothing);
    });
  });

  group('the Following bar', () {
    testWidgets('holds the filter icon with its count, Feeds, search and the person with its dot', (tester) async {
      final semantics = tester.ensureSemantics();
      await pumpInk(tester, inkFeedScreen(), theme: theme, locale: const Locale('en'));

      final order = tester.getRect(find.byKey(const ValueKey('home-order-menu')));
      final feeds = tester.getRect(find.byTooltip('Feeds'));
      final search = tester.getRect(find.byTooltip('Search'));
      final accounts = tester.getRect(find.byTooltip('Home feed accounts'));
      // Each control's 48 dp target is checked against Android's guideline in
      // home_following_bar_test.dart.
      expect(order.size, const Size.square(48));
      expect(order.left, lessThan(feeds.left));
      expect(feeds.left, lessThan(search.left));
      expect(search.left, lessThan(accounts.left));
      expect(find.byIcon(Icons.filter_list), findsOneWidget);
      final count = tester.widget<Badge>(
        find.descendant(of: find.byKey(const ValueKey('home-order-menu')), matching: find.byType(Badge)),
      );
      expect(count.isLabelVisible, isTrue);
      expect(count.backgroundColor, XLookTokens.lightsOut.accent);
      expect(find.byIcon(Icons.person_outline), findsOneWidget);
      final dot = tester.widget<Badge>(
        find.descendant(of: find.byTooltip('Home feed accounts'), matching: find.byType(Badge)),
      );
      expect(dot.isLabelVisible, isTrue);
      expect(dot.label, isNull);
      final node = tester.getSemantics(find.byIcon(Icons.person_outline));
      expect(node.value, '7', reason: 'a screen reader hears how many are turned off');
      expect(node.tooltip, 'Home feed accounts');
      expect(node.flagsCollection.isButton, isTrue);
      semantics.dispose();
    });

    testWidgets('has no dot when nothing is turned off', (tester) async {
      await pumpInk(tester, inkFeedScreen(turnedOff: 0, filters: 0), theme: theme, locale: const Locale('en'));

      for (final control in [find.byKey(const ValueKey('home-order-menu')), find.byTooltip('Home feed accounts')]) {
        final badge = tester.widget<Badge>(find.descendant(of: control, matching: find.byType(Badge)));
        expect(badge.isLabelVisible, isFalse, reason: '$control');
      }
    });

    testWidgets('the filter icon opens order and filters', (tester) async {
      await pumpInk(tester, inkFeedScreen(), theme: theme, locale: const Locale('en'));

      await tester.tap(find.byKey(const ValueKey('home-order-menu')));
      await tester.pumpAndSettle();
      for (final item in ['Recent', 'Popular', 'Custom', 'Filters']) {
        expect(find.text(item), findsOneWidget, reason: item);
      }
    });

    testWidgets('the selected tab has its 4 dp marker under its name', (tester) async {
      await pumpInk(tester, inkFeedScreen(), theme: theme);

      final name = tester.getRect(find.text('Tweets'));
      final marker = find.descendant(
        of: find.byKey(const ValueKey('home-posts-tab')),
        matching: find.byType(AnimatedContainer),
      );
      final rect = tester.getRect(marker);
      expect(rect.height, kHomeTabMarkerHeight);
      expect(rect.left, name.left);
      expect(rect.right, name.right);
      expect(tester.widget<Text>(find.text('Tweets')).style!.fontSize, kHomeTabNameSize);
    });
  });
}

/// The leading strips mixed timelines draw down a card from another network.
Iterable<Border> _strips(WidgetTester tester) => tester
    .widgetList<DecoratedBox>(find.byType(DecoratedBox))
    .where((box) => box.decoration is BoxDecoration && (box.decoration as BoxDecoration).border is Border)
    .map((box) => (box.decoration as BoxDecoration).border! as Border)
    .where((border) => border.left.width == 2 && border.top.width == 0);

/// The index of the post's text among the RichTexts on screen.
int _textIndex(WidgetTester tester) => tester
    .widgetList<RichText>(find.byType(RichText))
    .toList()
    .indexWhere((text) => text.text.toPlainText().startsWith('Russia has extended'));
