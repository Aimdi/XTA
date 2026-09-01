import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/reddit/reddit_auth.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_listing_cache.dart';
import 'package:xta/plugins/reddit/reddit_post_source.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';

RedditPost _post(
  String subreddit,
  String id, {
  DateTime? at,
  bool stickied = false,
  int score = 0,
}) => RedditPost(
  id: id,
  title: id,
  subreddit: subreddit,
  permalink: '/r/$subreddit/comments/$id/',
  createdAt: at,
  stickied: stickied,
  score: score,
);

RedditListing _listing(List<RedditPost> posts) => RedditListing(posts: posts);

/// Counts what reached Reddit, which is the whole point of the cache.
class _FakeRedditClient extends RedditClient {
  _FakeRedditClient({
    this.failing = const {},
    Map<String, List<RedditPost>>? posts,
  }) : posts = posts ?? const {};

  final Set<String> failing;
  final Map<String, List<RedditPost>> posts;

  /// Every listing request, as `subreddit/sort`.
  final List<String> requests = [];

  final List<String?> tokens = [];
  final List<RedditTimeFilter> timeFilters = [];

  int get calls => requests.length;

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
    requests.add('$subreddit/${sort.name}');
    tokens.add(userToken);
    timeFilters.add(timeFilter);

    if (failing.contains(subreddit)) {
      throw const RedditException(RedditErrorKind.notFound, 'private or gone');
    }

    return _listing(posts[subreddit] ?? const []);
  }
}

class _FakeRedditAuth extends RedditAuth {
  int calls = 0;
  bool rejects = false;

  @override
  Future<String> accessToken({
    required String clientId,
    required String refreshToken,
  }) async {
    calls++;
    if (rejects) {
      throw const RedditException(
        RedditErrorKind.unauthorized,
        'refresh token is done',
      );
    }

    return 'token-$calls';
  }
}

BasePrefService _prefs({
  String? refreshToken,
  String? clientId,
  String? source,
  String? sort,
  String? timeFilter,
  String? nsfwMode,
  String? savedPosts,
}) => PrefServiceCache(
  cache: {
    optionPluginRedditClientId: clientId ?? '',
    optionPluginRedditRefreshToken: refreshToken ?? '',
    optionPluginRedditSource: source ?? redditSourceAuto,
    optionPluginRedditSort: sort ?? redditSortHot,
    optionPluginRedditTimeFilter: timeFilter ?? redditTimeFilterDay,
    optionPluginRedditNsfwMode: nsfwMode ?? redditNsfwModeTap,
    optionPluginRedditSavedPosts: savedPosts ?? '[]',
  },
);

void main() {
  group('RedditListingCache', () {
    late DateTime now;

    setUp(() => now = DateTime(2026, 1, 1, 12));

    RedditListingCache cache({
      Duration? ttl,
      Duration? failureTtl,
      int maxEntries = kRedditListingCacheSize,
    }) => RedditListingCache(
      ttl: ttl ?? kRedditListingTtl,
      failureTtl: failureTtl ?? kRedditListingFailureTtl,
      maxEntries: maxEntries,
      clock: () => now,
    );

    const key = (subreddit: 'dartlang', sort: RedditSort.hot, timeFilter: null);

    test('a listing fetched for one surface is handed to the next', () async {
      final subject = cache();
      var fetches = 0;
      Future<RedditListing> fetch() async {
        fetches++;
        return _listing([_post('dartlang', 'a')]);
      }

      final first = await subject.listing(key, fetch: fetch);
      now = now.add(const Duration(seconds: 30));
      final second = await subject.listing(key, fetch: fetch);

      expect(fetches, 1);
      expect(second.posts.single.id, first.posts.single.id);
    });

    test('surfaces mounting at the same moment join one request', () async {
      final subject = cache();
      final completer = Completer<RedditListing>();
      var fetches = 0;
      Future<RedditListing> fetch() {
        fetches++;
        return completer.future;
      }

      final both = Future.wait([
        subject.listing(key, fetch: fetch),
        subject.listing(key, fetch: fetch),
      ]);
      completer.complete(_listing([_post('dartlang', 'a')]));
      final results = await both;

      expect(
        fetches,
        1,
        reason: 'the second caller joined the request in flight',
      );
      expect(results.map((e) => e.posts.single.id), ['a', 'a']);
    });

    test('a sort of its own is a listing of its own', () async {
      final subject = cache();
      var fetches = 0;
      Future<RedditListing> fetch() async {
        fetches++;
        return _listing(const []);
      }

      await subject.listing(key, fetch: fetch);
      await subject.listing((
        subreddit: 'dartlang',
        sort: RedditSort.newest,
        timeFilter: null,
      ), fetch: fetch);

      expect(fetches, 2);
    });

    test('a time filter of its own is a listing of its own', () async {
      final subject = cache();
      var fetches = 0;
      Future<RedditListing> fetch() async {
        fetches++;
        return _listing(const []);
      }

      await subject.listing((
        subreddit: 'dartlang',
        sort: RedditSort.top,
        timeFilter: RedditTimeFilter.week,
      ), fetch: fetch);
      await subject.listing((
        subreddit: 'dartlang',
        sort: RedditSort.top,
        timeFilter: RedditTimeFilter.month,
      ), fetch: fetch);

      expect(fetches, 2);
    });

    test(
      'a forced refresh goes past it, or the reader can never get new posts',
      () async {
        final subject = cache();
        var fetches = 0;
        Future<RedditListing> fetch() async {
          fetches++;
          return _listing([_post('dartlang', 'post$fetches')]);
        }

        await subject.listing(key, fetch: fetch);
        final forced = await subject.listing(
          key,
          fetch: fetch,
          forceRefresh: true,
        );

        expect(fetches, 2);
        expect(forced.posts.single.id, 'post2');
        // And the forced result is what the next reader is handed.
        expect(
          (await subject.listing(key, fetch: fetch)).posts.single.id,
          'post2',
        );
        expect(fetches, 2);
      },
    );

    test('it is fetched again once the entry is old', () async {
      final subject = cache(ttl: const Duration(minutes: 3));
      var fetches = 0;
      Future<RedditListing> fetch() async {
        fetches++;
        return _listing(const []);
      }

      await subject.listing(key, fetch: fetch);
      now = now.add(const Duration(minutes: 3, seconds: 1));
      await subject.listing(key, fetch: fetch);

      expect(fetches, 2);
      expect(
        subject.length,
        1,
        reason: 'the expired entry was replaced, not accumulated',
      );
    });

    test(
      'a subreddit that failed is left alone briefly, then asked again',
      () async {
        final subject = cache(failureTtl: const Duration(seconds: 30));
        var fetches = 0;
        Future<RedditListing> fetch() async {
          fetches++;
          throw const RedditException(RedditErrorKind.notFound, 'gone');
        }

        await expectLater(
          subject.listing(key, fetch: fetch),
          throwsA(isA<RedditException>()),
        );
        await expectLater(
          subject.listing(key, fetch: fetch),
          throwsA(isA<RedditException>()),
        );
        expect(
          fetches,
          1,
          reason: 'the second surface was handed the same failure',
        );

        now = now.add(const Duration(seconds: 31));
        await expectLater(
          subject.listing(key, fetch: fetch),
          throwsA(isA<RedditException>()),
        );
        expect(fetches, 2);
      },
    );

    test('a failure is forgotten sooner than a success', () async {
      final subject = cache(
        ttl: const Duration(minutes: 3),
        failureTtl: const Duration(seconds: 30),
      );
      var fetches = 0;
      Future<RedditListing> fetch() async {
        fetches++;
        return _listing(const []);
      }

      await subject.listing(key, fetch: fetch);
      now = now.add(const Duration(seconds: 31));
      await subject.listing(key, fetch: fetch);

      expect(fetches, 1, reason: 'a good listing outlives the failure window');
    });

    test('it stops growing, dropping the least recently used', () async {
      final subject = cache(maxEntries: 2);
      Future<RedditListing> fetch() async => _listing(const []);

      await subject.listing((
        subreddit: 'a',
        sort: RedditSort.hot,
        timeFilter: null,
      ), fetch: fetch);
      await subject.listing((
        subreddit: 'b',
        sort: RedditSort.hot,
        timeFilter: null,
      ), fetch: fetch);
      // Touching 'a' makes 'b' the eviction candidate.
      await subject.listing((
        subreddit: 'a',
        sort: RedditSort.hot,
        timeFilter: null,
      ), fetch: fetch);
      await subject.listing((
        subreddit: 'c',
        sort: RedditSort.hot,
        timeFilter: null,
      ), fetch: fetch);

      expect(subject.length, 2);

      var fetches = 0;
      Future<RedditListing> counted() async {
        fetches++;
        return _listing(const []);
      }

      await subject.listing((
        subreddit: 'a',
        sort: RedditSort.hot,
        timeFilter: null,
      ), fetch: counted);
      expect(fetches, 0, reason: 'a was used most recently');
      await subject.listing((
        subreddit: 'b',
        sort: RedditSort.hot,
        timeFilter: null,
      ), fetch: counted);
      expect(fetches, 1, reason: 'b was the least recently used');
    });

    test('clear empties it', () async {
      final subject = cache();
      Future<RedditListing> fetch() async => _listing(const []);

      await subject.listing(key, fetch: fetch);
      subject.clear();

      expect(subject.length, 0);
    });
  });

  group('RedditPostSource', () {
    late DateTime now;

    setUp(() => now = DateTime(2026, 1, 1, 12));

    RedditPostSource source(
      _FakeRedditClient client, {
      BasePrefService? prefs,
      RedditAuth? auth,
    }) => RedditPostSource(
      client,
      prefs ?? _prefs(),
      auth: auth ?? _FakeRedditAuth(),
      cache: RedditListingCache(clock: () => now),
      clock: () => now,
    );

    test(
      'Following, For you and the Reddit tab pay for one fetch between them',
      () async {
        // The journey the cache exists for: the same subreddits, three surfaces,
        // one download.
        final client = _FakeRedditClient(
          posts: {
            'dartlang': [_post('dartlang', 'a', at: now)],
            'flutterdev': [_post('flutterdev', 'b', at: now)],
          },
        );
        final subject = source(client);
        const names = ['dartlang', 'flutterdev'];

        // The timeline shows ten of each and the tab fifteen, which used to be
        // two requests per subreddit and is now the same one.
        await subject.posts(names, limit: 10);
        await subject.posts(names, limit: 10);
        await subject.posts(names);

        expect(client.calls, 2, reason: 'two subreddits, not two per surface');
      },
    );

    test(
      'a group that shares a subreddit with the tab does not fetch it again',
      () async {
        final client = _FakeRedditClient(
          posts: {
            'dartlang': [_post('dartlang', 'a', at: now)],
            'androiddev': [_post('androiddev', 'c', at: now)],
          },
        );
        final subject = source(client);

        await subject.posts(['dartlang']);
        await subject.posts(['dartlang', 'androiddev']);

        expect(client.requests, ['dartlang/hot', 'androiddev/hot']);
      },
    );

    test('a forced read reaches Reddit again', () async {
      final client = _FakeRedditClient(
        posts: {
          'dartlang': [_post('dartlang', 'a', at: now)],
        },
      );
      final subject = source(client);

      await subject.posts(['dartlang']);
      await subject.posts(['dartlang'], forceRefresh: true);

      expect(client.calls, 2);
    });

    test('one bad subreddit does not empty the feed', () async {
      final client = _FakeRedditClient(
        failing: {'private'},
        posts: {
          'dartlang': [_post('dartlang', 'a', at: now)],
        },
      );
      final subject = source(client);

      final posts = await subject.posts(['private', 'dartlang']);

      expect(posts.map((e) => e.id), ['a']);
    });

    test('and it is not asked again by the next surface', () async {
      final client = _FakeRedditClient(
        failing: {'private'},
        posts: {
          'dartlang': [_post('dartlang', 'a', at: now)],
        },
      );
      final subject = source(client);

      await subject.posts(['private', 'dartlang']);
      final second = await subject.posts(['private', 'dartlang']);

      expect(
        client.calls,
        2,
        reason: 'one try each, the failure remembered as well as the success',
      );
      expect(second.map((e) => e.id), ['a']);
    });

    test(
      'nothing to show is a real answer, not a reason to keep the old posts',
      () async {
        // A subreddit dropped from a group has to take its posts with it, so an
        // empty result has to come back empty rather than as what was cached.
        final client = _FakeRedditClient(
          posts: {
            'dartlang': [_post('dartlang', 'a', at: now)],
            'quiet': const [],
          },
        );
        final subject = source(client);

        expect((await subject.posts(['dartlang'])).map((e) => e.id), ['a']);
        expect(await subject.posts(['quiet']), isEmpty);
        expect(await subject.posts(const []), isEmpty);
      },
    );

    test(
      'newest first, stickied dropped, trimmed to what the surface shows',
      () async {
        final older = now.subtract(const Duration(hours: 2));
        final client = _FakeRedditClient(
          posts: {
            'dartlang': [
              _post('dartlang', 'pinned', at: now, stickied: true),
              _post('dartlang', 'old', at: older),
              _post('dartlang', 'new', at: now),
              _post('dartlang', 'spare', at: older),
            ],
          },
        );
        final subject = source(client);

        final posts = await subject.posts(['dartlang'], limit: 2);

        expect(
          posts.map((e) => e.id),
          ['new', 'old'],
          reason: 'the pin is gone and the fourth was trimmed',
        );
      },
    );

    test(
      'one access token for every surface rather than one per read',
      () async {
        final auth = _FakeRedditAuth();
        final client = _FakeRedditClient(
          posts: {
            'dartlang': [_post('dartlang', 'a', at: now)],
          },
        );
        final subject = source(
          client,
          prefs: _prefs(clientId: 'cid', refreshToken: 'refresh'),
          auth: auth,
        );

        await subject.posts(['dartlang']);
        await subject.posts(['dartlang'], forceRefresh: true);

        expect(auth.calls, 1);
        expect(client.tokens, ['token-1', 'token-1']);
      },
    );

    test(
      'a refresh token Reddit rejects ends the session rather than the read',
      () async {
        final auth = _FakeRedditAuth()..rejects = true;
        final prefs = _prefs(clientId: 'cid', refreshToken: 'refresh');
        final client = _FakeRedditClient(
          posts: {
            'dartlang': [_post('dartlang', 'a', at: now)],
          },
        );

        final posts = await source(
          client,
          prefs: prefs,
          auth: auth,
        ).posts(['dartlang']);

        expect(posts.map((e) => e.id), ['a']);
        expect(prefs.get<String>(optionPluginRedditRefreshToken), '');
        expect(client.tokens, [
          null,
        ], reason: 'the read fell back to the public route');
      },
    );

    test(
      'signing in throws away what was cached for the old credentials',
      () async {
        final prefs = _prefs();
        final client = _FakeRedditClient(
          posts: {
            'dartlang': [_post('dartlang', 'a', at: now)],
          },
        );
        final subject = source(client, prefs: prefs, auth: _FakeRedditAuth());

        await subject.posts(['dartlang']);
        await prefs.set(optionPluginRedditClientId, 'cid');
        await prefs.set(optionPluginRedditRefreshToken, 'refresh');
        await subject.posts(['dartlang']);

        expect(
          client.calls,
          2,
          reason: 'the cached listing answered a different Reddit',
        );
        expect(client.tokens, [null, 'token-1']);
      },
    );

    test('asking for the account-free route does the same', () async {
      final prefs = _prefs();
      final client = _FakeRedditClient(
        posts: {
          'dartlang': [_post('dartlang', 'a', at: now)],
        },
      );
      final subject = source(client, prefs: prefs, auth: _FakeRedditAuth());

      await subject.posts(['dartlang']);
      await prefs.set(optionPluginRedditSource, redditSourcePublic);
      await subject.posts(['dartlang']);

      expect(client.calls, 2);
    });

    test('the stored sort is what every surface reads', () async {
      final prefs = _prefs(sort: RedditSort.newest.name);
      final client = _FakeRedditClient(
        posts: {
          'dartlang': [_post('dartlang', 'a', at: now)],
        },
      );
      final subject = source(client, prefs: prefs);

      await subject.posts(['dartlang']);
      await subject.posts(['dartlang'], sort: RedditSort.top);

      expect(client.requests, ['dartlang/newest', 'dartlang/top']);
    });

    test('the stored time filter is sent for top listings', () async {
      final prefs = _prefs(
        sort: RedditSort.top.name,
        timeFilter: RedditTimeFilter.week.name,
      );
      final client = _FakeRedditClient(
        posts: {
          'dartlang': [_post('dartlang', 'a', at: now)],
        },
      );

      await source(client, prefs: prefs).posts(['dartlang']);

      expect(client.timeFilters, [RedditTimeFilter.week]);
    });

    test('NSFW hide mode filters the merged feed', () async {
      final prefs = _prefs(nsfwMode: RedditNsfwMode.hide.name);
      final client = _FakeRedditClient(
        posts: {
          'dartlang': [
            _post('dartlang', 'safe', at: now),
            RedditPost(
              id: 'adult',
              title: 'adult',
              subreddit: 'dartlang',
              permalink: '/r/dartlang/comments/adult/',
              createdAt: now,
              over18: true,
            ),
          ],
        },
      );

      final posts = await source(client, prefs: prefs).posts(['dartlang']);

      expect(posts.map((post) => post.id), ['safe']);
    });

    test('a subreddit named twice is read once', () async {
      final client = _FakeRedditClient(
        posts: {
          'dartlang': [_post('dartlang', 'a', at: now)],
        },
      );

      // A group can hold a subreddit the reader also follows, and the combined
      // feed adds both lists together.
      await source(client).posts(['dartlang', 'dartlang']);

      expect(client.calls, 1);
    });
  });

  group('RedditSavedStore', () {
    test('round-trips post snapshots through prefs', () async {
      final prefs = _prefs();
      final post = _post('dartlang', 'a', at: DateTime.utc(2026, 1, 1));
      final store = RedditSavedStore(prefs);

      await store.load();
      await store.toggle(post);

      final restored = RedditSavedStore(prefs);
      await restored.load();

      expect(restored.state.single.id, 'a');
      expect(restored.state.single.title, 'a');
      expect(restored.isSaved(post), isTrue);

      await restored.toggle(post);
      expect(
        RedditPost.listFromPrefs(
          prefs.get<String>(optionPluginRedditSavedPosts),
        ),
        isEmpty,
      );
    });
  });
}
