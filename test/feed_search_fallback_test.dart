import 'package:flutter_test/flutter_test.dart';
import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/feed_search_fallback.dart';
import 'package:xta/group/future_pool.dart';

TweetChain _chain(String id, {TweetWithCard? tweet}) {
  return TweetChain(
    id: id,
    tweets: [tweet ?? (TweetWithCard()..idStr = id)],
    isPinned: false,
  );
}

TweetChain _retweet(String id) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..retweetedStatusWithCard = (TweetWithCard()..idStr = 'orig-$id');
  return _chain(id, tweet: tweet);
}

TweetStatus _status(List<TweetChain> chains) =>
    TweetStatus(chains: chains, cursorBottom: 'bottom', cursorTop: 'top');

UserSubscription _user(String id) => UserSubscription(
  id: id,
  screenName: id,
  name: id,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime.utc(2026),
  inFeed: true,
);

SearchSubscription _search(String query) =>
    SearchSubscription(id: query, createdAt: DateTime.utc(2026));

void main() {
  group('shouldFallbackToUserTimelines', () {
    test('SearchTimeline 429 and a refused endpoint use UserTweets', () {
      expect(shouldFallbackToUserTimelines(RateLimitedException()), isTrue);
      expect(
        shouldFallbackToUserTimelines(EndpointRefusedException('SearchTimeline')),
        isTrue,
      );
    });

    test('other failures keep cached chains instead of fanning out profiles', () {
      expect(shouldFallbackToUserTimelines(Exception('parse')), isFalse);
      expect(shouldFallbackToUserTimelines(NoAccountAvailableException()), isFalse);
      expect(shouldFallbackToUserTimelines(NoWorkingAccountException()), isFalse);
    });
  });

  group('searchCursorFromStored', () {
    test('null and empty stored cursors become a fresh search', () {
      expect(searchCursorFromStored(null), isNull);
      expect(searchCursorFromStored(''), isNull);
      expect(searchCursorFromStored('cursor-top'), 'cursor-top');
    });
  });

  group('userSubscriptionsForFallback', () {
    test('SearchSubscription members have no rest_id for UserTweets', () {
      final users = [_user('1'), _search('ai art'), _user('2')];
      expect(userSubscriptionsForFallback(users).map((u) => u.id), ['1', '2']);
    });
  });

  group('dropRetweetsIfNeeded', () {
    test('keeps retweets when the group asked for them', () {
      final chains = [_chain('a'), _retweet('b')];
      expect(dropRetweetsIfNeeded(chains, true), chains);
    });

    test('drops retweet chains to match -filter:retweets', () {
      final kept = dropRetweetsIfNeeded([_chain('a'), _retweet('b'), _chain('c')], false);
      expect(kept.map((c) => c.id), ['a', 'c']);
    });
  });

  group('shouldSkipGapFill', () {
    test('does not page SearchTimeline after a fallback or a failed search', () {
      expect(shouldSkipGapFill(usedFallback: true, searchFailed: true), isTrue);
      expect(shouldSkipGapFill(usedFallback: false, searchFailed: true), isTrue);
      expect(shouldSkipGapFill(usedFallback: false, searchFailed: false), isFalse);
    });
  });

  group('feedErrorToRethrow', () {
    test('is silent when every chunk survived', () {
      expect(feedErrorToRethrow(const []), isNull);
    });

    test('prefers the rate-limit widget over a refused endpoint', () {
      final error = feedErrorToRethrow([
        EndpointRefusedException('SearchTimeline'),
        RateLimitedException(),
        Exception('other'),
      ]);
      expect(error, isA<RateLimitedException>());
    });

    test('falls back to endpoint-refused, then the first error', () {
      expect(
        feedErrorToRethrow([Exception('x'), EndpointRefusedException('SearchTimeline')]),
        isA<EndpointRefusedException>(),
      );
      expect(feedErrorToRethrow([Exception('x')]).toString(), 'Exception: x');
    });
  });

  group('fetchChunkWithFallback', () {
    test('returns the search page when SearchTimeline is healthy', () async {
      var fallbackCalls = 0;
      final result = await fetchChunkWithFallback(
        search: () async => _status([_chain('s')]),
        userTimelines: () async {
          fallbackCalls++;
          return [_chain('f')];
        },
      );

      expect(result.search?.chains.single.id, 's');
      expect(result.usedFallback, isFalse);
      expect(result.error, isNull);
      expect(fallbackCalls, 0);
    });

    test('loads UserTweets when search is rate-limited', () async {
      final result = await fetchChunkWithFallback(
        search: () async => throw RateLimitedException(),
        userTimelines: () async => [_chain('from-profile')],
      );

      expect(result.search, isNull);
      expect(result.usedFallback, isTrue);
      expect(result.fallbackChains.single.id, 'from-profile');
      expect(result.error, isNull);
      expect(result.hasFreshPosts, isTrue);
    });

    test('loads UserTweets when SearchTimeline is refused for every account', () async {
      final result = await fetchChunkWithFallback(
        search: () async => throw EndpointRefusedException('SearchTimeline'),
        userTimelines: () async => [_chain('from-profile')],
      );

      expect(result.usedFallback, isTrue);
      expect(result.fallbackChains, isNotEmpty);
      expect(result.error, isNull);
    });

    test('keeps the search error when fallback finds nothing', () async {
      final result = await fetchChunkWithFallback(
        search: () async => throw RateLimitedException(),
        userTimelines: () async => const [],
      );

      expect(result.usedFallback, isTrue);
      expect(result.fallbackChains, isEmpty);
      expect(result.error, isA<RateLimitedException>());
    });

    test('keeps the search error when fallback itself throws', () async {
      final result = await fetchChunkWithFallback(
        search: () async => throw RateLimitedException(),
        userTimelines: () async => throw Exception('profiles down'),
      );

      expect(result.usedFallback, isTrue);
      expect(result.error, isA<RateLimitedException>());
    });

    test('does not fan out UserTweets for unrelated failures', () async {
      var fallbackCalls = 0;
      final result = await fetchChunkWithFallback(
        search: () async => throw Exception('json'),
        userTimelines: () async {
          fallbackCalls++;
          return [_chain('nope')];
        },
      );

      expect(result.search, isNull);
      expect(result.usedFallback, isFalse);
      expect(result.fallbackChains, isEmpty);
      expect(result.error, isA<Exception>());
      expect(fallbackCalls, 0);
    });
  });

  group('fetchUserTimelines', () {
    test('skips search subscriptions and surviving users still contribute', () async {
      final chains = await fetchUserTimelines(
        users: [_user('a'), _search('query'), _user('b')],
        getTweets: (user) async {
          if (user.id == 'b') {
            throw Exception('one profile 429');
          }
          return [_chain(user.id)];
        },
      );

      expect(chains.map((c) => c.id), ['a']);
    });

    test('caps in-flight profile fetches', () async {
      var inFlight = 0;
      var peak = 0;
      await fetchUserTimelines(
        users: List.generate(8, (i) => _user('$i')),
        concurrency: 2,
        getTweets: (_) async {
          inFlight++;
          if (inFlight > peak) {
            peak = inFlight;
          }
          await Future<void>.delayed(const Duration(milliseconds: 20));
          inFlight--;
          return const [];
        },
      );

      expect(peak, lessThanOrEqualTo(2));
      expect(peak, greaterThan(0));
    });

    test('an empty chunk is a no-op', () async {
      final chains = await fetchUserTimelines(
        users: [_search('only-search')],
        getTweets: (_) async => [_chain('nope')],
      );
      expect(chains, isEmpty);
    });
  });

  group('chunk fan-out survival', () {
    test('one rate-limited chunk still lets the others return posts', () async {
      final results = await mapWithConcurrency([1, 2, 3], 2, (n) {
        return fetchChunkWithFallback(
          search: () async {
            if (n == 2) {
              throw RateLimitedException();
            }
            return _status([_chain('$n')]);
          },
          userTimelines: () async => [_chain('fb-$n')],
        );
      });

      expect(results[0].search?.chains.single.id, '1');
      expect(results[1].usedFallback, isTrue);
      expect(results[1].fallbackChains.single.id, 'fb-2');
      expect(results[2].search?.chains.single.id, '3');
      expect(feedErrorToRethrow([
        for (final e in results)
          if (e.error != null) e.error!,
      ]), isNull);
    });
  });
}
