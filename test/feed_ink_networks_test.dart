import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/tweet/thread_rail.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_header.dart';
import 'package:xta/tweet/tweet_ink_layout.dart';
import 'package:xta/ui/verified_badges.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/bluesky_reading_images.dart';
import 'support/feed_ink_harness.dart';

/// Follows kept in memory: the real stores write to the database.
class _BlueskyFollows extends BlueskyAccountsStore {
  @override
  Future<void> add(BlueskyAccount account) async => update([...state, account]);
}

class _ThreadsFollows extends ThreadsAccountsStore {
  @override
  Future<void> add(ThreadsAccount account) async => update([...state, account]);
}

/// What a tap opened, taken off again before it is built: the profile screens
/// want clients these cards do not.
class _Opened extends NavigatorObserver {
  final _routes = <MaterialPageRoute<dynamic>>[];

  @override
  void didPush(Route<dynamic> route, Route<dynamic>? previousRoute) {
    if (previousRoute != null && route is MaterialPageRoute) _routes.add(route);
  }

  Widget take() {
    final route = _routes.removeLast();
    final page = route.builder(route.navigator!.context);
    route.navigator!.removeRoute(route);
    return page;
  }
}

final _published = DateTime.now().subtract(const Duration(minutes: 15));
const _text = 'Field notes from the studio: the light was right at seven, and the kiln held.';

MastodonPost _toot({bool boosted = false, String? replyTo, bool edited = false}) => MastodonPost(
  id: '1',
  acct: 'maya@studio.example',
  authorName: 'Maya Chen',
  avatarUrl: _render ? 'https://files.studio.example/avatars/maya.jpg' : null,
  text: _text,
  url: 'https://studio.example/@maya/1',
  publishedAt: _published,
  editedAt: edited ? DateTime.now() : null,
  boosted: boosted,
  boostedBy: boosted ? 'Ola Berg' : null,
  boostedByAcct: boosted ? 'ola@fjord.example' : null,
  replyToAcct: replyTo,
);

BlueskyPost _skeet({bool repost = false, String? replyTo}) => BlueskyPost(
  uri: 'at://did:plc:maya/app.bsky.feed.post/1',
  cid: 'c1',
  handle: 'maya.bsky.social',
  did: 'did:plc:maya',
  authorName: 'Maya Chen',
  text: _text,
  url: 'https://bsky.app/profile/maya.bsky.social/post/1',
  publishedAt: _published,
  repostedByName: repost ? 'Ola Berg' : null,
  repostedByHandle: repost ? 'ola.bsky.social' : null,
  repostedByDid: repost ? 'did:plc:ola' : null,
  replyToHandle: replyTo,
);

ThreadsPost _thread({bool repost = false, String? topic}) => ThreadsPost(
  id: '1',
  handle: 'maya',
  authorName: 'Maya Chen',
  text: _text,
  url: 'https://www.threads.net/@maya/post/1',
  publishedAt: _published,
  isVerified: true,
  topicTag: topic,
  repostedByHandle: repost ? 'ola' : null,
  repostedByName: repost ? 'Ola Berg' : null,
);

/// Renders the three networks beside an X post for review:
///   flutter test test/feed_ink_networks_test.dart --dart-define=RENDER_INK=true
const _render = bool.fromEnvironment('RENDER_INK');

/// docs/specs/feed-ink-networks.md
void main() {
  final black = xLookLightsOutTheme(null);

  // The theme's own typeface, so a line holds what it holds on a phone.
  setUpAll(() async {
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    if (_render) {
      autoUpdateGoldenFiles = true;
      await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
    }
  });

  late _BlueskyFollows blueskyFollows;
  late _ThreadsFollows threadsFollows;
  late BlueskyLikesStore blueskyLikes;
  late ThreadsLikesStore threadsLikes;

  setUp(() {
    final prefs = PrefServiceCache();
    blueskyFollows = _BlueskyFollows();
    threadsFollows = _ThreadsFollows();
    blueskyLikes = BlueskyLikesStore(prefs);
    threadsLikes = ThreadsLikesStore(prefs);
  });

  tearDown(() async {
    await blueskyFollows.destroy();
    await threadsFollows.destroy();
    await blueskyLikes.destroy();
    await threadsLikes.destroy();
  });

  Future<void> pumpAll(
    WidgetTester tester,
    List<Widget> posts, {
    ThemeData? theme,
    Size size = const Size(390, 844),
    double scale = 1,
    TextDirection? direction,
    List<NavigatorObserver> observers = const [],
  }) => pumpInk(
    tester,
    MultiProvider(
      providers: [
        Provider<BlueskyAccountsStore>.value(value: blueskyFollows),
        Provider<BlueskyLikesStore>.value(value: blueskyLikes),
        Provider<ThreadsAccountsStore>.value(value: threadsFollows),
        Provider<ThreadsLikesStore>.value(value: threadsLikes),
      ],
      child: Scaffold(body: ListView(children: posts)),
    ),
    theme: theme ?? black,
    size: size,
    scale: scale,
    direction: direction,
    observers: observers,
  );

  Future<void> pump(
    WidgetTester tester,
    Widget card, {
    Size size = const Size(390, 844),
    double scale = 1,
    List<NavigatorObserver> observers = const [],
  }) => pumpAll(tester, [card], size: size, scale: scale, observers: observers);

  Rect avatarOf(WidgetTester tester) =>
      tester.getRect(find.descendant(of: find.byType(TweetAvatarTarget), matching: find.byType(ClipOval)));

  Rect textOf(WidgetTester tester) => tester.getRect(find.textContaining('Field notes', findRichText: true).first);

  Finder contextLineIcon(IconData icon) =>
      find.descendant(of: find.byType(TweetContextRow), matching: find.byIcon(icon));

  final cards = <String, Widget Function()>{
    'Mastodon': () => MastodonPostCard(post: _toot(), showSourceBadge: false),
    'Bluesky': () => BlueskyPostCard(post: _skeet(), showSourceBadge: false),
    'Threads': () => ThreadsPostCard(post: _thread(), showSourceBadge: false),
  };

  for (final MapEntry(key: network, value: card) in cards.entries) {
    group('a $network post', () {
      testWidgets('takes two columns: the avatar at 16 dp, everything else at 68 dp', (tester) async {
        await pump(tester, card());

        final target = tester.getRect(find.byType(TweetAvatarTarget));
        expect(target.size, const Size.square(48));
        expect(target.topLeft, const Offset(kInkAvatarTargetStart, kInkAvatarTargetTop));
        expect(avatarOf(tester), Rect.fromLTWH(16, target.top + 4, 40, 40));
        expect(tester.getTopLeft(find.text('Maya Chen')).dx, kInkColumnStart);
        expect(tester.getTopLeft(find.text('Maya Chen')).dy, greaterThanOrEqualTo(kInkColumnTop));
        expect(textOf(tester).left, kInkColumnStart);
        expect(
          tester.getRect(find.byTooltip(L10n.current.open_in_browser)).right,
          390,
          reason: "the actions row ends at the edge, where an X post's share glyph sits",
        );
      });

      testWidgets('names the author, handle and short time on one line', (tester) async {
        await pump(tester, card());

        final line = find.byType(TweetAuthorLine);
        final name = tester.getRect(find.text('Maya Chen'));
        final handle = tester.getRect(find.descendant(of: line, matching: find.textContaining('@maya')));
        final time = tester.getRect(find.descendant(of: line, matching: find.textContaining('15 Min.')));
        expect(handle.center.dy, closeTo(name.center.dy, 1));
        expect(time.center.dy, closeTo(name.center.dy, 1));
        expect(handle.left, greaterThan(name.right));
        expect(time.left, greaterThan(handle.left));
        expect(textOf(tester).top - name.bottom, lessThan(8), reason: 'the text starts right under the author line');
      });

      testWidgets('with large text on a narrow phone takes a second author line and fits', (tester) async {
        await pump(tester, card(), size: const Size(320, 900), scale: 2);

        expect(tester.takeException(), isNull);
        final name = tester.getRect(find.text('Maya Chen'));
        final handle = tester.getRect(find.textContaining('@maya').first);
        expect(handle.top, greaterThanOrEqualTo(name.bottom));
      });

      testWidgets('says the author and, in a mixed timeline, the network on the avatar', (tester) async {
        final semantics = tester.ensureSemantics();
        await pump(tester, switch (network) {
          'Mastodon' => MastodonPostCard(post: _toot()),
          'Bluesky' => BlueskyPostCard(post: _skeet()),
          _ => ThreadsPostCard(post: _thread()),
        });

        expect(tester.getSemantics(find.byType(TweetAvatarTarget)).label, 'Maya Chen, $network');
        semantics.dispose();
      });
    });
  }

  group('a Mastodon post', () {
    testWidgets('dims the server in the handle', (tester) async {
      await pump(tester, MastodonPostCard(post: _toot(), showSourceBadge: false));

      final handle = tester.widget<Text>(find.text('@maya@studio.example'));
      final server = (handle.textSpan! as TextSpan).children!.last as TextSpan;
      expect(server.text, '@studio.example');
      expect(server.style!.color!.a, lessThan(handle.style!.color!.a));
    });

    testWidgets('says "Edited" after the time', (tester) async {
      await pump(tester, MastodonPostCard(post: _toot(edited: true), showSourceBadge: false));

      final time = find.descendant(
        of: find.byType(TweetAuthorLine),
        matching: find.text('15 Min. · ${L10n.current.plugin_mastodon_edited}'),
      );
      expect(time, findsOneWidget);
      expect(tester.getRect(time).center.dy, closeTo(tester.getRect(find.text('Maya Chen')).center.dy, 1));
    });

    testWidgets('a boost and a pinned post have their context lines at 68 dp; the boost opens the booster', (
      tester,
    ) async {
      final opened = _Opened();
      await pump(
        tester,
        MastodonPostCard(post: _toot(boosted: true), pinned: true, showSourceBadge: false),
        observers: [opened],
      );

      expect(tester.getTopLeft(contextLineIcon(Icons.repeat)).dx, kInkColumnStart);
      expect(tester.getTopLeft(contextLineIcon(Icons.push_pin)).dx, kInkColumnStart);
      expect(find.text(L10n.current.plugin_mastodon_pinned), findsOneWidget);
      expect(
        tester.getRect(find.byType(TweetAvatarTarget)).top,
        greaterThan(tester.getRect(contextLineIcon(Icons.push_pin)).bottom),
        reason: 'the lines sit above the post, as on X',
      );

      await tester.tap(find.text(L10n.current.plugin_mastodon_boosted('Ola Berg')));
      final page = opened.take();
      expect(page, isA<MastodonProfileScreen>().having((s) => s.acct, 'acct', 'ola@fjord.example'));
      await tester.pump();
    });

    testWidgets('says who it replies to under the author line, at 68 dp', (tester) async {
      await pump(tester, MastodonPostCard(post: _toot(replyTo: 'ola@fjord.example'), showSourceBadge: false));

      final reply = tester.getRect(find.text(L10n.current.plugin_replying_to('ola@fjord.example')));
      expect(reply.left, kInkColumnStart);
      expect(reply.top, greaterThanOrEqualTo(tester.getRect(find.text('Maya Chen')).bottom));
      expect(reply.bottom, lessThanOrEqualTo(textOf(tester).top));
    });
  });

  group('a Bluesky post', () {
    testWidgets('offers Follow as a 48 dp icon at the end of the author line until followed', (tester) async {
      await pump(tester, BlueskyPostCard(post: _skeet(), showSourceBadge: false));

      final follow = find.byTooltip(L10n.current.plugin_bluesky_follow);
      expect(tester.getRect(follow), const Rect.fromLTWH(390 - 48, kInkColumnTop, 48, 48));
      expect(find.widgetWithText(TextButton, L10n.current.plugin_bluesky_follow), findsNothing);

      await tester.tap(follow);
      await tester.pump();
      expect(blueskyFollows.follows('maya.bsky.social'), isTrue);
      expect(follow, findsNothing);
    });

    testWidgets('a repost has its context line at 68 dp and it opens the reposter', (tester) async {
      final opened = _Opened();
      await pump(tester, BlueskyPostCard(post: _skeet(repost: true), showSourceBadge: false), observers: [opened]);

      expect(tester.getTopLeft(contextLineIcon(Icons.repeat)).dx, kInkColumnStart);
      await tester.tap(find.text(L10n.current.plugin_bluesky_reposted('Ola Berg')));
      expect(opened.take(), isA<BlueskyProfileScreen>().having((s) => s.actor, 'actor', 'did:plc:ola'));
      await tester.pump();
    });

    testWidgets('says who it replies to under the author line', (tester) async {
      await pump(tester, BlueskyPostCard(post: _skeet(replyTo: 'ola.bsky.social'), showSourceBadge: false));

      final reply = tester.getRect(find.text(L10n.current.plugin_replying_to('ola.bsky.social')));
      expect(reply.left, kInkColumnStart);
      expect(reply.top, greaterThanOrEqualTo(tester.getRect(find.text('Maya Chen')).bottom));
      expect(reply.bottom, lessThanOrEqualTo(textOf(tester).top));
    });
  });

  group('a Threads post', () {
    testWidgets('carries the verified check on the author line', (tester) async {
      await pump(tester, ThreadsPostCard(post: _thread(), showSourceBadge: false));

      final check = find.descendant(of: find.byType(TweetAuthorLine), matching: find.byType(VerifiedCheck));
      expect(check, findsOneWidget);
      expect(tester.getRect(check).center.dy, closeTo(tester.getRect(find.text('Maya Chen')).center.dy, 2));
    });

    testWidgets('offers Follow as a 48 dp icon until followed, before the ⋯ at the end', (tester) async {
      await pump(tester, ThreadsPostCard(post: _thread(), showSourceBadge: false));

      final follow = find.byTooltip(L10n.current.plugin_threads_follow);
      expect(tester.getRect(follow), const Rect.fromLTWH(390 - 96, kInkColumnTop, 48, 48));
      expect(tester.getRect(find.byTooltip(L10n.current.more_info)), const Rect.fromLTWH(390 - 48, kInkColumnTop, 48, 48));
      await tester.tap(follow);
      await tester.pump();
      expect(threadsFollows.follows('maya'), isTrue);
      expect(follow, findsNothing);
    });

    testWidgets('a repost has its context line at 68 dp and it opens the reposter', (tester) async {
      final opened = _Opened();
      await pump(tester, ThreadsPostCard(post: _thread(repost: true), showSourceBadge: false), observers: [opened]);

      expect(tester.getTopLeft(contextLineIcon(Icons.repeat)).dx, kInkColumnStart);
      await tester.tap(find.text(L10n.current.plugin_threads_reposted('Ola Berg')));
      expect(opened.take(), isA<ThreadsProfileScreen>().having((s) => s.username, 'username', 'ola'));
      await tester.pump();
    });

    testWidgets('puts its topic under the author line, at 68 dp', (tester) async {
      await pump(tester, ThreadsPostCard(post: _thread(topic: 'Ceramics'), showSourceBadge: false));

      final topic = tester.getRect(find.byType(ThreadsTopicTag));
      expect(topic.left, kInkColumnStart);
      expect(topic.top, greaterThanOrEqualTo(tester.getRect(find.text('Maya Chen')).bottom));
      expect(topic.bottom, lessThanOrEqualTo(textOf(tester).top));
    });

    testWidgets("a conversation's rail is drawn inside the card, through the avatar", (tester) async {
      await pump(
        tester,
        ThreadsPostCard(post: _thread(), showSourceBadge: false, threadConnectTop: true, threadConnectBottom: true),
      );

      final rail = find.descendant(of: find.byType(Card), matching: find.byType(ThreadRailLines));
      expect(rail, findsOneWidget, reason: "the card's colour would cover a rail drawn behind it");
      final line = tester.getRect(find.descendant(of: rail, matching: find.byType(ColoredBox)).first);
      expect(line.center.dx, avatarOf(tester).center.dx);
      expect(line.top, tester.getRect(find.byType(Card)).top);
      expect(find.byType(Divider), findsNothing, reason: 'the rail runs on into the next post unbroken');
    });

    testWidgets('the last post of a conversation, and a post outside one, keep their hairline', (tester) async {
      await pumpAll(tester, [
        ThreadsPostCard(post: _thread(), showSourceBadge: false, threadConnectTop: true),
        ThreadsPostCard(post: _thread(), showSourceBadge: false),
      ]);

      expect(find.byType(Divider), findsNWidgets(2));
      expect(find.byType(ThreadRailLines), findsOneWidget);
    });
  });

  group('renders', skip: !_render, () {
    Future<void> capture(WidgetTester tester, String name) async {
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('ink-render')),
        matchesGoldenFile('../review-artifacts/renders/feed-ink-networks-$name.png'),
      );
    }

    for (final (name, theme, size, scale, direction) in [
      ('black', black, const Size(390, 844), 1.0, null),
      ('light', xLookLightTheme(null), const Size(390, 844), 1.0, null),
      ('large-rtl', black, const Size(320, 1100), 1.3, TextDirection.rtl),
    ]) {
      testWidgets('a mixed timeline, $name', (tester) async {
        await installBlueskyReadingImages(tester);
        await pumpAll(
          tester,
          [
            TweetTile(clickable: true, tweet: inkXPost()),
            MastodonPostCard(post: _toot(boosted: true, edited: true)),
            BlueskyPostCard(post: _skeet(replyTo: 'ola.bsky.social')),
            ThreadsPostCard(post: _thread(topic: 'Ceramics')),
          ],
          theme: theme,
          size: size,
          scale: scale,
          direction: direction,
        );
        await settleBlueskyReadingImages(tester);
        await capture(tester, name);
      });
    }

    testWidgets('a Threads conversation', (tester) async {
      await pumpAll(tester, [
        for (final (index, post) in [_thread(), _thread(topic: 'Ceramics'), _thread()].indexed)
          ThreadsPostCard(
            post: post,
            showSourceBadge: false,
            threadConnectTop: index > 0,
            threadConnectBottom: index < 2,
          ),
      ]);
      await capture(tester, 'threads-conversation');
    });
  });
}
