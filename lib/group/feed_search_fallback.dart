/// SearchTimeline 429 / endpoint-refused must not blank a group feed.
///
/// Group feeds search `from:user1 OR from:user2`. That GraphQL operation is a
/// separate rate-limit bucket from UserTweets — the endpoint profiles use —
/// so a group can show the hourglass while opening a member still works.
/// Falling back per [UserSubscription] keeps posts on screen. Search
/// subscriptions have no user id, so they cannot use this path.
library;

import 'package:xta/catcher/exceptions.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/future_pool.dart';

/// Max in-flight UserTweets requests while recovering a rate-limited search.
/// Lower than chunk search concurrency: two in-flight chunks of 16 users
/// would otherwise open 32 profile requests at once.
const int feedUserTimelineFallbackConcurrency = 2;

bool shouldFallbackToUserTimelines(Object error) =>
    error is RateLimitedException || error is EndpointRefusedException;

bool shouldSkipGapFill({
  required bool usedFallback,
  required bool searchFailed,
}) => usedFallback || searchFailed;

/// Stored search cursors are SearchTimeline tokens. A missing or empty value
/// must not be cast to [String] — fallback rows store null cursors on purpose
/// so the next load retries a fresh search instead of mixing endpoints.
String? searchCursorFromStored(Object? value) =>
    value is String && value.isNotEmpty ? value : null;

List<UserSubscription> userSubscriptionsForFallback(
  Iterable<Subscription> users,
) => users.whereType<UserSubscription>().toList(growable: false);

/// UserTweets does not honour the search query's `-filter:retweets`.
List<TweetChain> dropRetweetsIfNeeded(
  List<TweetChain> chains,
  bool includeRetweets,
) {
  if (includeRetweets) {
    return chains;
  }
  return [
    for (final chain in chains)
      if (!_isRetweetChain(chain)) chain,
  ];
}

bool _isRetweetChain(TweetChain chain) {
  final tweet = chain.tweets.isEmpty ? null : chain.tweets.first;
  return tweet?.retweetedStatusWithCard != null;
}

/// Error to surface when every chunk failed and nothing is left to show.
/// Prefer the rate-limit widget the reader already knows from SearchTimeline.
Object? feedErrorToRethrow(Iterable<Object> errors) {
  final list = errors.toList(growable: false);
  if (list.isEmpty) {
    return null;
  }
  for (final error in list) {
    if (error is RateLimitedException) {
      return error;
    }
  }
  for (final error in list) {
    if (error is EndpointRefusedException) {
      return error;
    }
  }
  return list.first;
}

/// One chunk's SearchTimeline attempt, with UserTweets to stand in when
/// that search is rate-limited or refused.
class ChunkNetworkResult {
  final TweetStatus? search;
  final List<TweetChain> fallbackChains;
  final bool usedFallback;
  final Object? error;

  const ChunkNetworkResult({
    this.search,
    this.fallbackChains = const [],
    this.usedFallback = false,
    this.error,
  });

  bool get searchFailed => search == null;

  bool get hasFreshPosts =>
      (search?.chains.isNotEmpty ?? false) || fallbackChains.isNotEmpty;
}

/// Tries [search], then [userTimelines] when search is rate-limited or
/// refused. Other failures keep [ChunkNetworkResult.error] so the caller can
/// retain cached chains instead of taking the whole feed down.
Future<ChunkNetworkResult> fetchChunkWithFallback({
  required Future<TweetStatus> Function() search,
  required Future<List<TweetChain>> Function() userTimelines,
}) async {
  try {
    return ChunkNetworkResult(search: await search());
  } catch (error) {
    if (!shouldFallbackToUserTimelines(error)) {
      return ChunkNetworkResult(error: error);
    }
    try {
      final chains = await userTimelines();
      return ChunkNetworkResult(
        fallbackChains: chains,
        usedFallback: true,
        error: chains.isEmpty ? error : null,
      );
    } catch (_) {
      return ChunkNetworkResult(usedFallback: true, error: error);
    }
  }
}

Future<List<TweetChain>> fetchUserTimelines({
  required Iterable<Subscription> users,
  required Future<List<TweetChain>> Function(UserSubscription user) getTweets,
  int concurrency = feedUserTimelineFallbackConcurrency,
}) async {
  final targets = userSubscriptionsForFallback(users);
  if (targets.isEmpty) {
    return const [];
  }
  final pages = await mapWithConcurrency(targets, concurrency, (user) async {
    try {
      return await getTweets(user);
    } catch (_) {
      return const <TweetChain>[];
    }
  });
  return [for (final page in pages) ...page];
}
