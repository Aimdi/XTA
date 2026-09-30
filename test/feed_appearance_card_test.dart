import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/plugin_post_media.dart';
import 'package:xta/plugins/rss/rss_card.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_note_card.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/reading/feed_appearance_scope.dart';
import 'package:xta/reading/feed_appearance_store.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/_ExpandableTweetText.dart';
import 'package:xta/tweet/tweet_footer.dart';
import 'support/bluesky_reading_harness.dart';

void main() {
  testWidgets('native Mastodon appearance preserves revealed warning and hides only preview/counts', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(host.prefs);
    const feed = FeedIdentity('mastodon', 'local');
    const post = MastodonPost(
      id: 'p',
      acct: 'author@example.test',
      authorName: 'Author',
      text: 'Revealed native body',
      url: 'https://example.test/@author/p',
      spoilerText: 'Native warning',
      repliesCount: 13,
      reblogsCount: 27,
      linkCard: MastodonLinkCard(url: 'https://example.test/story', title: 'Native preview'),
    );
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: SingleChildScrollView(
            child: FeedAppearanceScope(
              feed: feed,
              store: store,
              publishAction: false,
              child: const MastodonPostCard(post: post),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Revealed native body'), findsNothing);
    await tester.tap(find.text('Show'));
    await tester.pumpAndSettle();
    expect(find.text('Native preview'), findsOneWidget);
    expect(find.text('13'), findsOneWidget);
    final card = tester.element(find.byType(MastodonPostCard));
    store.update({feed: const FeedAppearance(counts: false, linkPreviews: false)});
    await tester.pumpAndSettle();
    expect(find.text('Revealed native body'), findsOneWidget);
    expect(find.text('Native warning'), findsOneWidget);
    expect(find.text('Native preview'), findsNothing);
    expect(find.text('13'), findsNothing);
    expect(tester.element(find.byType(MastodonPostCard)), same(card));
    await host.close(tester);
    await store.destroy();
  });

  testWidgets('native Threads counters hide but conversation and device like remain named', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(host.prefs);
    final likes = ThreadsLikesStore(host.prefs);
    final accounts = ThreadsAccountsStore();
    const feed = FeedIdentity('threads', 'home');
    store.update({feed: const FeedAppearance(counts: false, media: false)});
    const post = ThreadsPost(
      id: 'p',
      handle: 'author',
      authorName: 'Author',
      text: 'Native caption',
      replyCount: 13,
      repostCount: 27,
      likeCount: 39,
      images: ['https://example.test/image.png'],
    );
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      host.app(
        MultiProvider(
          providers: [
            Provider.value(value: likes),
            Provider.value(value: accounts),
          ],
          child: Scaffold(
            body: FeedAppearanceScope(
              feed: feed,
              store: store,
              publishAction: false,
              child: const ThreadsPostCard(post: post),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('13'), findsNothing);
    expect(find.text('27'), findsNothing);
    expect(find.text('39'), findsNothing);
    expect(find.byTooltip('Thread'), findsNWidgets(2));
    final localLike = find.byWidgetPredicate(
      (widget) => widget is Semantics && widget.properties.label == 'Like on this device',
    );
    expect(localLike, findsOneWidget);
    expect(tester.getSemantics(localLike).label, contains('Like on this device'));
    expect(find.byType(PluginPostMedia), findsNothing);
    semantics.dispose();
    await host.close(tester);
    await store.destroy();
    await likes.destroy();
    await accounts.destroy();
  });

  testWidgets('native RSS preset text/media are distinct and independent from link previews', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(host.prefs);
    final read = RssReadStore(host.prefs);
    const feed = FeedIdentity('rss', 'one');
    const item = RssItem(
      id: 'a',
      feedId: 'one',
      feedTitle: 'Publication',
      title: 'An article headline',
      excerpt: 'A long excerpt',
      imageUrl: 'https://example.test/image.png',
    );
    store.update({feed: const FeedAppearance(preset: FeedPreset.compact, linkPreviews: false)});
    await tester.pumpWidget(
      host.app(
        Provider<RssReadStore>.value(
          value: read,
          child: Scaffold(
            body: SingleChildScrollView(
              child: FeedAppearanceScope(
                feed: feed,
                store: store,
                publishAction: false,
                child: const RssItemCard(item: item),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pump(const Duration(milliseconds: 200));
    expect(find.byType(ExtendedImage), findsOneWidget);
    expect(tester.widget<Text>(find.text(item.title)).maxLines, 3);
    final card = tester.element(find.byType(RssItemCard));
    store.update({feed: const FeedAppearance(preset: FeedPreset.reading, media: false, linkPreviews: false)});
    await tester.pump(const Duration(milliseconds: 200));
    expect(tester.widget<Text>(find.text(item.title)).maxLines, isNull);
    expect(find.byType(ExtendedImage), findsNothing);
    expect(tester.element(find.byType(RssItemCard)), same(card));
    await host.close(tester);
    await store.destroy();
    await read.destroy();
  });

  testWidgets('native Substack articles and Notes consume independent media and counter overrides', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(host.prefs);
    final read = SubstackReadStore(host.prefs);
    final likes = SubstackLikesStore(host.prefs);
    final saved = SubstackSavedStore(host.prefs);
    const feed = FeedIdentity('substack', 'home');
    store.update({feed: const FeedAppearance(counts: false, media: false)});
    const post = SubstackPost(
      id: 'a',
      title: 'Native article',
      slug: 'article',
      publicationBaseUrl: 'https://example.substack.com',
      publicationName: 'Publication',
      coverImage: 'https://example.test/image.png',
      reactionCount: 37,
      commentCount: 23,
    );
    const note = SubstackNote(
      id: 'n',
      body: 'A complete native Note',
      authorName: 'Author',
      reactionCount: 56,
      imageUrl: 'https://example.test/note.png',
    );
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      host.app(
        MultiProvider(
          providers: [
            Provider.value(value: read),
            Provider.value(value: likes),
            Provider.value(value: saved),
          ],
          child: Scaffold(
            body: SingleChildScrollView(
              child: FeedAppearanceScope(
                feed: feed,
                store: store,
                publishAction: false,
                child: const Column(
                  children: [
                    SubstackPostCard(post: post),
                    SubstackNoteCard(note: note),
                  ],
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('37'), findsNothing);
    expect(find.text('23'), findsNothing);
    expect(find.text('56'), findsNothing);
    expect(find.byType(ExtendedImage), findsNothing);
    expect(find.byType(PluginPostMedia), findsNothing);
    final localLike = find.byWidgetPredicate(
      (widget) => widget is Semantics && widget.properties.label == 'Like on this device',
    );
    expect(localLike, findsOneWidget);
    expect(tester.getSemantics(localLike).label, contains('Like on this device'));
    expect(find.byTooltip('Comments'), findsOneWidget);
    semantics.dispose();
    await host.close(tester);
    await store.destroy();
    await read.destroy();
    await likes.destroy();
    await saved.destroy();
  });

  testWidgets('native X footer respects scoped counts and keeps read-only conversation/local actions', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(host.prefs);
    final likes = LikedTweetModel();
    final saved = SavedTweetModel();
    const feed = FeedIdentity('x', 'following');
    store.update({feed: const FeedAppearance(counts: false)});
    final post = TweetWithCard()
      ..idStr = '123'
      ..user = (User()..screenName = 'reader')
      ..replyCount = 13
      ..favoriteCount = 37;
    var opens = 0;
    final semantics = tester.ensureSemantics();
    await tester.pumpWidget(
      host.app(
        MultiProvider(
          providers: [
            Provider<LikedTweetModel>.value(value: likes),
            Provider<SavedTweetModel>.value(value: saved),
          ],
          child: Scaffold(
            body: FeedAppearanceScope(
              feed: feed,
              store: store,
              publishAction: false,
              child: TweetFooterBar(
                tweet: post,
                tweetText: 'Native post',
                shareBaseUrl: 'https://x.com/',
                locale: const Locale('en'),
                numberFormat: NumberFormat.compact(locale: 'en'),
                onOpenTweet: () => opens++,
                onCaptureImage: () async => null,
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('13'), findsNothing);
    expect(find.text('37'), findsNothing);
    expect(find.bySemanticsLabel('Open post'), findsOneWidget);
    final localLike = find.byWidgetPredicate(
      (widget) => widget is Semantics && widget.properties.label == 'Like on this device',
    );
    expect(localLike, findsOneWidget);
    expect(tester.getSemantics(localLike).label, contains('Like on this device'));
    await tester.tap(find.bySemanticsLabel('Open post'));
    await tester.pumpAndSettle();
    expect(opens, 1);
    semantics.dispose();
    await host.close(tester);
    await store.destroy();
    await likes.destroy();
    await saved.destroy();
  });

  testWidgets('native X text cap and plugin media layout follow three distinct presets', (tester) async {
    final host = BlueReadingHarness();
    final store = FeedAppearanceStore(host.prefs);
    const feed = FeedIdentity('x', 'following');
    final spans = [TextSpan(text: List.filled(70, 'A longer native paragraph.').join(' '))];
    await tester.pumpWidget(
      host.app(
        Scaffold(
          body: SingleChildScrollView(
            child: FeedAppearanceScope(
              feed: feed,
              store: store,
              publishAction: false,
              child: Column(
                children: [
                  ExpandableTweetText(textSpans: spans, maxLines: 8),
                  PluginPostMedia(
                    items: const [PluginMediaItem(url: 'https://example.test/image.png', aspectRatio: 2)],
                    imageBuilder: (_, _, _) => const ColoredBox(color: Colors.blue),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    final original = tester.element(find.byType(ExpandableTweetText));
    final heights = <double>[];
    for (final preset in FeedPreset.values) {
      store.update({feed: FeedAppearance(preset: preset)});
      await tester.pumpAndSettle();
      heights.add(tester.getSize(find.byType(PluginPostMedia)).height);
      if (preset == FeedPreset.reading) {
        expect(find.text('Show more'), findsNothing);
      } else {
        expect(find.text('Show more'), findsOneWidget);
      }
      expect(tester.element(find.byType(ExpandableTweetText)), same(original));
    }
    expect(heights[0], lessThan(heights[2]));
    expect(heights[1], greaterThan(heights[2]));
    await host.close(tester);
    await store.destroy();
  });
}
