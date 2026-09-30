import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_search_query.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_mixed_sources.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_mixed_sources.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_mixed_sources.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_mixed_sources.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/rss/rss_mixed_sources.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_mixed_sources.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_source.dart';

class _Mastodon extends MastodonClient {
  final calls = <(String, String?)>[];

  @override
  Future<T> firstInstanceThat<T>(List<String> instances, Future<T> Function(String instance) read) =>
      read('first.example');

  @override
  Future<List<MastodonPost>> getTagTimeline(String instance, String tag, {int limit = 30, String? maxId}) async {
    calls.add((instance, maxId));
    final count = maxId == null ? limit : 2;
    final start = maxId == null ? 0 : 100;
    return [
      for (var i = start; i < start + count; i++)
        MastodonPost(
          id: '$i',
          acct: 'a@first.example',
          authorName: 'A',
          text: '#$tag',
          url: 'https://first.example/$i',
        ),
    ];
  }
}

class _Bluesky extends BlueskyClient {
  final cursors = <String?>[];

  @override
  Future<BlueskyFeedPage> getFeed(String feed, {int limit = 30, String? cursor}) async {
    cursors.add(cursor);
    return BlueskyFeedPage(
      posts: [
        BlueskyPost(
          uri: 'at://p/${cursors.length}',
          cid: 'c',
          handle: 'h',
          did: 'did:plc:h',
          authorName: 'H',
          text: 'post',
          url: 'u',
        ),
      ],
      cursor: 'same',
    );
  }
}

class _HackerNews extends HackerNewsClient {
  @override
  Future<HnStoryPage> feed(HnFeed feed, {int page = 0}) async => HnStoryPage(
    page: page,
    hasMore: page == 0,
    stories: [HnStory(id: page, title: '${feed.name} $page')],
  );
}

class _Reddit extends RedditClient {
  final afters = <String?>[];

  @override
  Future<RedditListing> fetchSubreddit(
    String subreddit, {
    required String clientId,
    RedditSort sort = RedditSort.hot,
    RedditTimeFilter timeFilter = RedditTimeFilter.day,
    int limit = kRedditListingPageSize,
    String? after,
    String? userToken,
    bool preferPublic = false,
  }) async {
    afters.add(after);
    return RedditListing(
      posts: [
        const RedditPost(id: 'pinned', title: 'Rules', subreddit: 'dart', permalink: '/r/dart/1', stickied: true),
        const RedditPost(id: 'nsfw', title: 'Adults', subreddit: 'dart', permalink: '/r/dart/2', over18: true),
        RedditPost(id: 'post-${afters.length}', title: 'News', subreddit: 'dart', permalink: '/r/dart/3'),
      ],
      after: after == null ? 't3_next' : null,
    );
  }
}

class _Substack extends SubstackClient {
  final offsets = <int>[];

  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async {
    offsets.add(offset);
    return [
      for (var i = 0; i < (offset == 0 ? limit : 3); i++)
        SubstackPost(
          id: '${offset + i}',
          slug: 's${offset + i}',
          title: 'Post',
          publicationBaseUrl: publication.baseUrl,
          publicationName: publication.name,
        ),
    ];
  }
}

class _Rss extends RssClient {
  @override
  Future<List<RssItem>> fetchItems(RssFeed feed) async {
    if (feed.id == 'broken') throw StateError('offline');
    return [RssItem(id: '${feed.id}-1', feedId: feed.id, feedTitle: feed.name, title: 'From ${feed.name}')];
  }
}

class _Publications extends SubstackPublicationsStore {
  _Publications(super.prefs) {
    update(const [SubstackPublication(subdomain: 'studio', baseUrl: 'https://studio.substack.com', name: 'Studio')]);
  }
}

class _Feeds extends RssFeedsStore {
  final List<RssFeed> follows;
  _Feeds(super.prefs, this.follows);

  @override
  Future<List<RssFeed>> saved() async => follows;
}

/// Builds [kind]'s reader for [source] with the given root providers, the way a mix does.
Future<MixedSourceReader?> _reader(
  WidgetTester tester,
  MixedSourceKind kind,
  MixedFeedSource source,
  List<Provider> providers, {
  BasePrefService? prefs,
}) async {
  MixedSourceReader? reader;
  await tester.pumpWidget(
    PrefService(
      service: prefs ?? PrefServiceCache(),
      child: MultiProvider(
        providers: providers,
        child: Builder(
          builder: (context) {
            reader = kind.reader(context, source);
            return const SizedBox();
          },
        ),
      ),
    ),
  );
  return reader;
}

void main() {
  testWidgets('a Mastodon hashtag pages on the instance that served the first page', (tester) async {
    final client = _Mastodon();
    final reader = await _reader(
      tester,
      const MastodonTagMixedSource(),
      const MixedFeedSource(kind: 'mastodon.tag', value: 'dart', label: '#dart'),
      [Provider<MastodonClient>.value(value: client)],
    );
    final first = await reader!.read(null);
    expect(first.entries, hasLength(30));
    expect(first.entries.first.identity, startsWith('mastodon:'));
    final second = await reader.read(first.next);
    expect(client.calls, [('first.example', null), ('first.example', '29')]);
    expect(second.entries, hasLength(2));
    expect(second.next, isNull);
  });

  testWidgets('a typed hashtag is cleaned up, and nonsense is refused', (tester) async {
    late BuildContext context;
    await tester.pumpWidget(
      Builder(
        builder: (built) {
          context = built;
          return const SizedBox();
        },
      ),
    );
    const kind = MastodonTagMixedSource();
    expect((await kind.fromText(context, '##Dart'))?.value, 'dart');
    expect(await kind.fromText(context, 'two words'), isNull);
  });

  testWidgets('a Bluesky feed stops when its cursor comes back unchanged', (tester) async {
    final client = _Bluesky();
    final reader = await _reader(
      tester,
      const BlueskyFeedMixedSource(),
      const MixedFeedSource(kind: 'bluesky.feed', value: 'at://feed', label: 'Feed'),
      [Provider<BlueskyClient>.value(value: client)],
    );
    final first = await reader!.read(null);
    expect(first.next, 'same');
    final second = await reader.read(first.next);
    expect(second.next, isNull);
    expect(client.cursors, [null, 'same']);
  });

  testWidgets('Reddit leaves out stickied and, when asked, adult posts, and pages with after', (tester) async {
    final client = _Reddit();
    final reader = await _reader(
      tester,
      const RedditSubredditMixedSource(),
      const MixedFeedSource(kind: 'reddit.subreddit', value: 'dart', label: 'r/dart'),
      [Provider<RedditClient>.value(value: client), Provider<RedditAuth>.value(value: RedditAuth())],
      prefs: PrefServiceCache(defaults: {optionPluginRedditNsfwMode: RedditNsfwMode.hide.name}),
    );
    final first = await reader!.read(null);
    expect(first.entries.map((entry) => entry.identity), ['reddit:post-1']);
    final second = await reader.read(first.next);
    expect(client.afters, [null, 't3_next']);
    expect(second.next, isNull);
  });

  testWidgets('Hacker News and Substack page by number and by offset', (tester) async {
    final hn = await _reader(
      tester,
      const HnFeedMixedSource(),
      const MixedFeedSource(kind: 'hackernews.feed', value: 'best', label: 'Best'),
      [Provider<HackerNewsClient>.value(value: _HackerNews())],
    );
    final page = await hn!.read(null);
    expect((page.entries.single.identity, page.next), ('hn:0', 1));
    expect((await hn.read(1)).next, isNull);

    final substack = _Substack();
    final prefs = PrefServiceCache();
    final reader = await _reader(
      tester,
      const SubstackPublicationMixedSource(),
      const MixedFeedSource(kind: 'substack.publication', value: 'studio', label: 'Studio'),
      [
        Provider<SubstackClient>.value(value: substack),
        Provider<SubstackPublicationsStore>.value(value: _Publications(prefs)),
      ],
      prefs: prefs,
    );
    final first = await reader!.read(null);
    final second = await reader.read(first.next);
    expect(substack.offsets, [0, 12]);
    expect(second.next, isNull);
  });

  testWidgets('all followed RSS feeds read together; one feed failing leaves the rest', (tester) async {
    final prefs = PrefServiceCache();
    final reader = await _reader(
      tester,
      const RssAllMixedSource(),
      const MixedFeedSource(kind: 'rss.all', label: 'All'),
      [
        Provider<RssClient>.value(value: _Rss()),
        Provider<RssFeedsStore>.value(
          value: _Feeds(prefs, const [
            RssFeed(id: 'ok', feedUrl: 'https://ok.example/rss', name: 'Ok'),
            RssFeed(id: 'broken', feedUrl: 'https://broken.example/rss', name: 'Broken'),
          ]),
        ),
      ],
      prefs: prefs,
    );
    final page = await reader!.read(null);
    expect(page.entries.map((entry) => entry.identity), ['rss:ok:ok-1']);
    expect(page.next, isNull);
  });

  test('group search queries join members by OR and carry the group settings', () {
    final created = DateTime.utc(2026);
    final members = <Subscription>[
      UserSubscription(
        id: '1',
        screenName: 'alice',
        name: 'Alice',
        profileImageUrlHttps: null,
        verified: false,
        createdAt: created,
        inFeed: true,
      ),
      SearchSubscription(id: 'flutter news', createdAt: created),
    ];
    expect(
      groupSearchQuery(members, includeReplies: false, includeRetweets: true),
      'from:alice OR "flutter news" -filter:replies  include:nativeretweets ',
    );
    expect(groupSearchQuery(members, includeReplies: true, includeRetweets: false), endsWith(' -filter:retweets '));
  });
}
