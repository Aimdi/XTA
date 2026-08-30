import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:timeago/timeago.dart' as timeago;
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_likes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_card.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_card.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/plugins/rss/rss_card.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_store.dart';

/// The narrowest phone the app still has to look right on, in logical pixels.
const Size _narrowPhone = Size(320, 900);

/// No network, no cache — the card only needs the avatar to lay out.
class _NoIcons implements RedditIcons {
  @override
  RedditClient get client => throw UnimplementedError();

  @override
  Future<String?> iconFor(
    String subreddit, {
    String clientId = '',
    String? userToken,
    bool preferPublic = false,
  }) async => null;
}

Widget _german(Widget child) {
  return MaterialApp(
    locale: const Locale('de'),
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: SingleChildScrollView(child: child)),
  );
}

Future<void> _pumpNarrow(WidgetTester tester, Widget child) async {
  tester.view.physicalSize = _narrowPhone;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.resetPhysicalSize);
  addTearDown(tester.view.resetDevicePixelRatio);
  await tester.pumpWidget(child);
  await tester.pump();
}

void main() {
  // Without this every relative date silently falls back to English, which is
  // the shorter wording and so the easier layout — the opposite of the test.
  timeago.setLocaleMessages('de', timeago.DeMessages());

  final now = DateTime.now().subtract(const Duration(days: 95));

  testWidgets('a Reddit card in German does not overflow a 320dp phone', (
    tester,
  ) async {
    final votes = RedditVotesStore();
    addTearDown(votes.destroy);

    await _pumpNarrow(
      tester,
      MultiProvider(
        providers: [
          Provider<RedditVotesStore>.value(value: votes),
          Provider<RedditIcons>.value(value: _NoIcons()),
        ],
        child: _german(
          RedditPostCard(
            post: RedditPost(
              id: 'a',
              subreddit: 'einesehrlangencommunitynamehier',
              title: 'Titel',
              author: 'ein_sehr_langer_benutzername_hier',
              permalink: '/r/x/comments/a',
              createdAt: now,
              score: 1234,
              commentCount: 42,
              over18: true,
              spoiler: true,
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('a Mastodon card in German does not overflow a 320dp phone', (
    tester,
  ) async {
    await _pumpNarrow(
      tester,
      PrefService(
        service: PrefServiceCache(),
        child: _german(
          MastodonPostCard(
            post: MastodonPost(
              id: '1',
              acct: 'einsehrlangername@eine.sehr.lange.instanz.example',
              authorName: 'Ein sehr langer Anzeigename',
              text: 'Hallo',
              url: 'https://example.social/@x/1',
              publishedAt: now,
              editedAt: now,
            ),
            pinned: true,
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('a Hacker News card in German does not overflow', (tester) async {
    final prefs = PrefServiceCache();
    final likes = HnLikesStore(prefs);
    final saved = HnSavedStore(prefs);
    addTearDown(() {
      likes.destroy();
      saved.destroy();
    });

    await _pumpNarrow(
      tester,
      MultiProvider(
        providers: [
          Provider<HnLikesStore>.value(value: likes),
          Provider<HnSavedStore>.value(value: saved),
        ],
        child: _german(
          HnStoryCard(
            rank: 100,
            story: HnStory(
              id: 1,
              title:
                  'Eine ungewöhnlich lange Überschrift über Programmiersprachen',
              url: 'https://eine.sehr.lange.subdomain.example.com/artikel',
              author: 'ein_langer_benutzername',
              score: 1234,
              commentCount: 567,
              createdAt: now,
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('an RSS card in German does not overflow', (tester) async {
    final read = RssReadStore(PrefServiceCache());
    addTearDown(read.destroy);

    await _pumpNarrow(
      tester,
      Provider<RssReadStore>.value(
        value: read,
        child: _german(
          RssItemCard(
            item: RssItem(
              id: '1',
              feedId: 'f',
              feedTitle:
                  'Ein außerordentlich langer Feedname der niemals endet',
              title: 'Titel',
              link: 'https://example.com/a',
              publishedAt: now,
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('a Bluesky card in German does not overflow', (tester) async {
    final prefs = PrefServiceCache();
    final accounts = BlueskyAccountsStore();
    final likes = BlueskyLikesStore(prefs);
    addTearDown(() {
      accounts.destroy();
      likes.destroy();
    });

    await _pumpNarrow(
      tester,
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<BlueskyAccountsStore>.value(value: accounts),
            Provider<BlueskyLikesStore>.value(value: likes),
          ],
          child: _german(
            BlueskyPostCard(
              post: BlueskyPost(
                uri: 'at://x/app.bsky.feed.post/1',
                cid: 'c',
                handle: 'ein.sehr.langer.handle.bsky.social',
                did: 'did:plc:x',
                authorName: 'Ein sehr langer Anzeigename hier',
                text: 'Hallo',
                url: 'https://bsky.app/profile/x/post/1',
                publishedAt: now,
              ),
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('a Threads card in German does not overflow', (tester) async {
    final prefs = PrefServiceCache();
    final accounts = ThreadsAccountsStore();
    final likes = ThreadsLikesStore(prefs);
    addTearDown(() {
      accounts.destroy();
      likes.destroy();
    });

    await _pumpNarrow(
      tester,
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<ThreadsAccountsStore>.value(value: accounts),
            Provider<ThreadsLikesStore>.value(value: likes),
          ],
          child: _german(
            ThreadsPostCard(
              post: ThreadsPost(
                id: 'https://threads.net/@x/post/1',
                handle: 'ein.sehr.langer.handle',
                authorName: 'Ein sehr langer Anzeigename hier',
                text: 'Hallo',
                url: 'https://threads.net/@x/post/1',
                publishedAt: now,
                isVerified: true,
              ),
            ),
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });

  testWidgets('a Booru card in German does not overflow', (tester) async {
    await _pumpNarrow(
      tester,
      _german(
        BooruPostCard(
          post: const BooruPost(
            id: '1234567',
            host: 'gelbooru.com',
            engine: 'gelbooru',
            tags: ['ein', 'zwei', 'drei'],
            rating: null,
            score: 4321,
            width: 800,
            height: 600,
            previewUrl: null,
            sampleUrl: null,
            fileUrl: null,
            fileExt: 'jpg',
            source: null,
            createdAt: null,
          ),
        ),
      ),
    );

    expect(tester.takeException(), isNull);
  });
}
