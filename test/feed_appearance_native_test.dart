import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_gallery.dart';
import 'package:xta/plugins/reddit/reddit_gallery_loader.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
import 'package:xta/plugins/reddit/reddit_post_media.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/reading/feed_appearance_scope.dart';
import 'package:xta/reading/feed_appearance_store.dart';
import 'support/bluesky_reading_harness.dart';

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

class _GalleryClient extends RedditClient {
  @override
  Future<List<String>> fetchGalleryImages(String permalink) async => [
    'https://i.redd.it/one.png',
    'https://i.redd.it/two.png',
  ];
}

void main() {
  testWidgets('native Bluesky hidden counts preserve explicitly named navigation and local likes', (tester) async {
    final semantics = tester.ensureSemantics();
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(PrefServiceCache());
    const feed = FeedIdentity('bluesky', 'following');
    store.update({feed: const FeedAppearance(counts: false)});
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: FeedAppearanceScope(
            feed: feed,
            store: store,
            publishAction: false,
            child: BlueskyPostCard(post: bluePost('root')),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.text('2'), findsNothing);
    expect(find.text('8'), findsNothing);
    expect(find.byTooltip('Thread'), findsOneWidget);
    expect(find.byTooltip('Quotes'), findsOneWidget);
    final localLike = find.byWidgetPredicate(
      (widget) => widget is Semantics && widget.properties.label == 'Remove like from this device',
    );
    expect(localLike, findsOneWidget);
    expect(tester.getSemantics(localLike).label, contains('Remove like from this device'));
    semantics.dispose();
    await host.close(tester);
    await store.destroy();
  });
  testWidgets('native Bluesky initial disabled media is not built', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(PrefServiceCache());
    const feed = FeedIdentity('bluesky', 'following');
    store.update({feed: const FeedAppearance(media: false)});
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: FeedAppearanceScope(
            feed: feed,
            store: store,
            publishAction: false,
            child: BlueskyPostCard(post: bluePost('root', media: true)),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.byType(PluginPostMedia), findsNothing);
    await host.close(tester);
    await store.destroy();
  });
  testWidgets('scope changes identity without replacing mounted native state', (tester) async {
    final store = FeedAppearanceStore(PrefServiceCache());
    const a = FeedIdentity('bluesky', 'at://one');
    const b = FeedIdentity('bluesky', 'at://two');
    store.update({a: const FeedAppearance(counts: false), b: const FeedAppearance(media: false)});
    Widget app(FeedIdentity feed) => MaterialApp(
      home: FeedAppearanceScope(
        feed: feed,
        store: store,
        publishAction: false,
        child: Builder(
          builder: (context) => Text(
            '${FeedAppearanceScope.feedOf(context)?.nativeId}:${FeedAppearanceScope.of(context).counts}:${FeedAppearanceScope.of(context).media}',
          ),
        ),
      ),
    );
    await tester.pumpWidget(app(a));
    expect(find.text('at://one:false:null'), findsOneWidget);
    final element = tester.element(find.byType(Builder).last);
    await tester.pumpWidget(app(b));
    expect(find.text('at://two:null:false'), findsOneWidget);
    expect(tester.element(find.byType(Builder).last), same(element));
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });
  testWidgets('Reddit inherits visible counts under Zen; hiding keeps named local and conversation actions', (
    tester,
  ) async {
    final host = BlueReadingHarness();
    await host.prefs.set(optionZenMode, true);
    await host.prefs.set(optionCalmMode, true);
    final store = FeedAppearanceStore(host.prefs);
    final votes = RedditVotesStore();
    const feed = FeedIdentity('reddit', 'r/test');
    final post = RedditPost(
      id: 'a',
      title: 'Native post',
      subreddit: 'test',
      permalink: '/r/test/comments/a',
      score: 132,
      commentCount: 45,
    );
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: MultiProvider(
            providers: [
              Provider<RedditVotesStore>.value(value: votes),
              Provider<RedditIcons>.value(value: _NoIcons()),
            ],
            child: FeedAppearanceScope(
              feed: feed,
              store: store,
              publishAction: false,
              child: RedditPostCard(post: post),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    expect(find.text('132'), findsOneWidget);
    expect(find.text('45'), findsOneWidget);
    store.update({feed: const FeedAppearance(counts: false)});
    await tester.pump();
    expect(find.text('132'), findsNothing);
    expect(find.text('45'), findsNothing);
    expect(find.byTooltip('Open post'), findsOneWidget);
    expect(find.byTooltip('Like on this device'), findsOneWidget);
    await host.close(tester);
    await store.destroy();
    await votes.destroy();
  });
  testWidgets('Reddit previews can hide while fetched gallery keeps native element and cache', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(host.prefs);
    final client = _GalleryClient();
    final loader = RedditGalleryLoader(client);
    const feed = FeedIdentity('reddit', 'r/test');
    final gallery = RedditPost(
      id: 'g',
      title: 'Gallery',
      subreddit: 'test',
      permalink: '/r/test/comments/g',
      url: 'https://www.reddit.com/gallery/g',
    );
    final link = RedditPost(
      id: 'l',
      title: 'Link',
      subreddit: 'test',
      permalink: '/r/test/comments/l',
      url: 'https://example.test/article',
      domain: 'example.test',
    );
    await loader.images(gallery.permalink);
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: SingleChildScrollView(
            child: Provider<RedditGalleryLoader>.value(
              value: loader,
              child: FeedAppearanceScope(
                feed: feed,
                store: store,
                publishAction: false,
                child: Column(
                  children: [
                    RedditPostMedia(post: gallery),
                    RedditPostMedia(post: link),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump();
    final element = tester.element(find.byType(RedditGallery));
    expect(find.text('example.test'), findsOneWidget);
    store.update({feed: const FeedAppearance(linkPreviews: false, preset: FeedPreset.gallery)});
    await tester.pump();
    expect(find.text('example.test'), findsNothing);
    expect(find.byType(RedditGallery), findsOneWidget);
    expect(tester.element(find.byType(RedditGallery)), same(element));
    expect(loader.size, 1);
    store.update({feed: const FeedAppearance(media: false)});
    await tester.pump();
    expect(find.byType(RedditGallery), findsNothing);
    expect(find.text('www.reddit.com'), findsOneWidget);
    expect(tester.element(find.byType(RedditGallery, skipOffstage: false)), same(element));
    store.update({feed: const FeedAppearance(linkPreviews: false)});
    await tester.pump();
    expect(find.byType(RedditGallery), findsOneWidget);
    expect(tester.element(find.byType(RedditGallery)), same(element));
    expect(loader.size, 1);
    await host.close(tester);
    await store.destroy();
    client.httpClient.close();
  });
}
