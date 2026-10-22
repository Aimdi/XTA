import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_popular.dart';
import 'package:xta/plugins/ehviewer/eh_client.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/rss/rss_client.dart';
import 'package:xta/plugins/stocks/crypto_client.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/tweet/ticker/ticker_client.dart';

/// Live reads of every source that needs no account. Opt in with:
///   fvm flutter test test/live/sources_smoke_test.dart --dart-define=RUN_LIVE=true
///
/// Each check reads what a reader opening the source first sees, then one level
/// deeper (a thread, a post, a gallery), the way the screens do.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);
  const skip = enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit the live services';
  const timeout = Timeout(Duration(minutes: 2));

  // ignore: avoid_print
  void report(String line) => print(line);

  group('Bluesky', () {
    final client = BlueskyClient();

    test(
      'a profile, its posts and a thread',
      () async {
        final profile = await client.getProfile('bsky.app');
        expect(profile.handle, 'bsky.app');
        final feed = await client.getAuthorFeed('bsky.app', limit: 5);
        expect(feed.posts, isNotEmpty);
        final thread = await client.getPostThread(feed.posts.first.uri);
        expect(thread.post.uri, feed.posts.first.uri);
        report('Bluesky: ${feed.posts.length} posts, thread with ${thread.replies.length} replies');
      },
      timeout: timeout,
      skip: skip,
    );

    test(
      'Discover and the searches',
      () async {
        final discover = await client.getFeed(kBlueskyDiscoverFeedUri, limit: 5);
        expect(discover.posts, isNotEmpty);
        final people = await client.searchActors('bluesky');
        expect(people, isNotEmpty);
        final posts = await client.searchPosts('bluesky');
        expect(posts.posts, isNotEmpty);
        final more = await client.searchPosts('bluesky', cursor: posts.cursor);
        expect(more.posts, isNotEmpty);
        report(
          'Bluesky: discover ${discover.posts.length}, people ${people.length}, '
          'posts ${posts.posts.length} then ${more.posts.length}',
        );
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('Mastodon', () {
    final client = MastodonClient(timeout: const Duration(seconds: 20));
    const instance = 'https://mastodon.social';

    test(
      'a profile, its posts and a thread',
      () async {
        final profile = await client.lookup(instance, 'Gargron');
        expect(profile.username.toLowerCase(), 'gargron');
        final posts = await client.getStatuses(instance, profile.id, limit: 5);
        expect(posts, isNotEmpty);
        final context = await client.getContext(instance, posts.first.id);
        report('Mastodon: ${posts.length} posts, context ${context.ancestors.length}+${context.descendants.length}');
      },
      timeout: timeout,
      skip: skip,
    );

    test(
      'trending, a tag and search',
      () async {
        final trending = await client.getTrendingStatuses(instance, limit: 5);
        expect(trending, isNotEmpty);
        final tag = await client.getTagTimeline(instance, 'photography', limit: 5);
        expect(tag, isNotEmpty);
        final found = await client.search(instance, 'mastodon');
        report('Mastodon: trending ${trending.length}, tag ${tag.length}, search ${found.accounts.length} people');
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('Hacker News', () {
    final client = HackerNewsClient();

    test(
      'front page, a thread, a user and search',
      () async {
        final page = await client.feed(HnFeed.top);
        expect(page.stories, isNotEmpty);
        final (story, comments) = await client.thread(page.stories.first.id);
        expect(story.id, page.stories.first.id);
        final user = await client.user('pg');
        expect(user.karma, greaterThan(0));
        final found = await client.search('flutter');
        expect(found.stories, isNotEmpty);
        report('HN: ${page.stories.length} stories, ${comments.length} comments, search ${found.stories.length}');
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('RSS', () {
    final client = RssClient();

    test(
      'an RSS feed, an Atom feed and discovery from a site',
      () async {
        final rss = await client.fetchChannel('https://www.astralcodexten.com/feed');
        expect(rss.items, isNotEmpty);
        final atom = await client.fetchChannel('https://github.com/flutter/flutter/releases.atom');
        expect(atom.items, isNotEmpty);
        final found = await client.lookup('https://www.theverge.com');
        final items = await client.fetchItems(found);
        expect(items, isNotEmpty);
        report('RSS: ${rss.items.length} RSS items, ${atom.items.length} Atom entries, ${items.length} discovered');
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('Booru', () {
    final client = BooruClient(PrefServiceCache(cache: {}));

    for (final (host, engine) in [
      ('https://danbooru.donmai.us', BooruEngine.danbooru),
      ('https://safebooru.org', BooruEngine.gelbooruV2),
      ('https://e926.net', BooruEngine.e621),
      ('https://yande.re', BooruEngine.moebooru),
    ]) {
      test(
        '$host: a search',
        () async {
          final page = await client.search('cat', site: client.siteAt(host, engine));
          expect(page.allowed, isNotEmpty);
          report('Booru $host: ${page.posts.length} of ${page.allowed.length} shown');
        },
        timeout: timeout,
        skip: skip,
      );
    }

    test(
      'Danbooru: latest and popular',
      () async {
        final latest = await client.latest();
        expect(latest.allowed, isNotEmpty);
        final popular = await client.popular(BooruPopularQuery.today());
        report('Booru Danbooru: latest ${latest.allowed.length}, popular ${popular.allowed.length}');
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('E-Hentai', () {
    final client = EhClient(PrefServiceCache(cache: {}));

    test(
      'the front page and a gallery',
      () async {
        final page = await client.frontPage();
        expect(page.galleries, isNotEmpty);
        final first = page.galleries.first;
        final detail = await client.galleryDetail(gid: first.gid, token: first.token);
        expect(detail.gid, first.gid);
        report('E-Hentai: ${page.galleries.length} galleries, ${detail.previews.length} previews');
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('Pixiv', () {
    test(
      'the walkthrough, the one read without a token',
      () async {
        final api = PixivDiscoveryApi(PixivClient(PrefServiceCache(cache: {})));
        final page = await api.walkthrough();
        expect(page.items, isNotEmpty);
        report('Pixiv: ${page.items.length} walkthrough works');
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('Threads', () {
    final client = ThreadsDirectClient(PrefServiceCache(cache: {}));

    test(
      'a profile, its posts and a thread',
      () async {
        final profile = await client.fetchGuestProfile('zuck');
        expect(profile.username, 'zuck');
        final posts = await client.fetchGuestAccount('zuck');
        expect(posts, isNotEmpty);
        final thread = await client.fetchGuestPostThread(posts.map((post) => post.url).nonNulls.first);
        expect(thread, isNotEmpty);
        report('Threads: ${posts.length} posts, thread of ${thread.length}');
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('Stocks', () {
    test(
      'a quote, a search, trending, news and a coin',
      () async {
        final ticker = TickerClient();
        final quote = await ticker.fetchQuote('AAPL', range: '1d', interval: '5m');
        expect(quote.price, isNotNull);
        final hits = await ticker.searchSymbols('apple');
        expect(hits, isNotEmpty);
        final trending = await ticker.fetchTrending();
        final news = await ticker.fetchNews('AAPL');
        final coins = await CryptoClient().search('PEPE');
        expect(coins, isNotEmpty);
        report(
          'Stocks: AAPL ${quote.price}, ${hits.length} hits, ${trending.length} trending, ${news.length} news, '
          '${coins.length} coins',
        );
      },
      timeout: timeout,
      skip: skip,
    );
  });

  group('Substack', () {
    final client = SubstackClient();

    test(
      'a publication, its posts, a post and its comments',
      () async {
        // Substack's own hosts send datacenter runners a Cloudflare check; a
        // publication on its own domain is served to them.
        final publication = await client.resolvePublication('https://www.astralcodexten.com');
        final posts = await client.fetchPosts(publication, limit: 5);
        expect(posts, isNotEmpty);
        final post = await client.fetchPost(publication, posts.first.slug);
        expect(post.bodyHtml ?? '', isNotEmpty);
        final comments = await client.fetchComments(publication, post.id);
        report('Substack: ${posts.length} posts, body ${post.bodyHtml?.length}, ${comments.length} comments');
      },
      timeout: timeout,
      skip: skip,
    );

    test(
      'search, categories and notes',
      () async {
        try {
          await client.fetchCategories();
        } on SubstackClientException catch (error) {
          if (!'$error'.contains('HTTP 403')) rethrow;
          report('Substack: substack.com sent this runner its browser check; search not read');
          return;
        }
        final found = await client.searchPublications('technology');
        expect(found, isNotEmpty);
        final categories = await client.fetchCategories();
        expect(categories, isNotEmpty);
        final notes = await client.fetchReaderNotes();
        report(
          'Substack: ${found.length} publications, ${categories.length} categories, '
          '${notes.notes.length} notes',
        );
      },
      timeout: timeout,
      skip: skip,
    );
  });
}
