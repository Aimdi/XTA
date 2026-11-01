import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/feed_cache.dart';
import 'package:xta/plugins/stocks/stocks_followed_posts.dart';
import 'package:xta/plugins/stocks/stocks_followed_section.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';

/// How much longer a ticker's first page waits for X's searches of followed
/// accounts. What comes later is kept for the next load.
const kFollowedSearchWait = Duration(seconds: 3);

/// The X accounts the reader follows; none where the subscriptions are not
/// provided.
List<UserSubscription> followedXAccounts(BuildContext context) {
  try {
    return context.read<SubscriptionsModel>().state.whereType<UserSubscription>().toList();
  } on ProviderNotFoundException {
    return const [];
  }
}

/// Where the posts of followed accounts about a ticker are looked for: the
/// feed pages cached on the device, then a few X searches, kept [keptFor]
/// per ticker.
class FollowedTickerSource {
  final Future<List<TweetChain>> Function(List<String> likePatterns) _cached;
  final Future<List<TweetChain>> Function(String query) _search;
  final DateTime Function() now;
  final _searched = <String, ({DateTime at, Future<List<TweetChain>> chains})>{};

  FollowedTickerSource({
    Future<List<TweetChain>> Function(List<String> likePatterns)? cached,
    Future<List<TweetChain>> Function(String query)? search,
    DateTime Function()? now,
  }) : _cached = cached ?? cachedFeedChainsMatching,
       _search = search ?? _searchX,
       now = now ?? DateTime.now;

  static final shared = FollowedTickerSource();
  static const keptFor = Duration(minutes: 5);

  static Future<List<TweetChain>> _searchX(String query) async => (await Twitter.searchTweets(query, true)).chains;

  /// The cached feed posts that may name the ticker; none when the cache
  /// cannot be read.
  Future<List<TweetChain>> onDevice(TickerPostTerms terms) async {
    try {
      return await _cached(terms.likePatterns);
    } catch (_) {
      return const [];
    }
  }

  /// What X's searches for [query] by [follows] found, the same answer for
  /// [keptFor]. A search that fails adds nothing.
  Future<List<TweetChain>> fromX(String query, List<UserSubscription> follows, {Set<String> knownPosters = const {}}) {
    final at = now();
    _searched.removeWhere((_, kept) => at.difference(kept.at) >= keptFor);
    final kept = _searched.putIfAbsent(
      query,
      () => (at: at, chains: _searchAll(followedTickerSearches(query, follows, knownPosters: knownPosters))),
    );
    return kept.chains;
  }

  Future<List<TweetChain>> _searchAll(List<String> queries) async {
    final pages = await Future.wait([
      for (final query in queries) _search(query).catchError((Object _) => const <TweetChain>[]),
    ]);
    return [for (final page in pages) ...page];
  }
}

/// A page of [query]'s posts from [page]: on the first, the posts by
/// [follows] that name the ticker go on top ([section] keeps the parts);
/// later pages leave those out.
Future<TweetPageResult> followedFirstPage(
  String? cursor, {
  required String query,
  required List<UserSubscription> follows,
  required FollowedSectionStore section,
  required Future<TweetPageResult> Function(String? cursor) page,
  FollowedTickerSource? source,
}) async {
  if (cursor != null) {
    final next = await page(cursor);
    return (chains: section.next(next.chains), nextCursor: next.nextCursor);
  }
  final terms = TickerPostTerms.of(query);
  final looking = follows.isEmpty || terms.isEmpty
      ? null
      : _lookFor(query, terms, follows, source ?? FollowedTickerSource.shared);
  final first = await page(null);
  return (chains: section.first(await looking?.call() ?? const [], first.chains), nextCursor: first.nextCursor);
}

/// Starts looking at once; the answer is the top part, from the device and
/// what X said within [kFollowedSearchWait] of being asked for it.
Future<List<TweetChain>> Function() _lookFor(
  String query,
  TickerPostTerms terms,
  List<UserSubscription> follows,
  FollowedTickerSource source,
) {
  final ids = {for (final follow in follows) follow.id};
  final onDevice = source.onDevice(terms);
  final fromX = onDevice.then(
    (cached) => source.fromX(query, follows, knownPosters: followedPosters(cached, ids, terms)),
  );
  return () async {
    final found = [...await onDevice, ...await fromX.timeout(kFollowedSearchWait, onTimeout: () => const [])];
    return followedSection(found, ids, terms, now: source.now());
  };
}
