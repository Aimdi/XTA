/// Which posts about one ticker are by accounts the reader follows, and the
/// searches that find more of them.
library;

import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/feed_search_query.dart';
import 'package:xta/tweet/cashtag_quotes.dart';

/// The most posts the top part shows, and how old they may be.
const kFollowedPostsShown = 10;
const kFollowedPostsWithin = Duration(days: 7);

/// The most X searches one ticker spends on followed accounts.
const kFollowedSearchesPerTicker = 3;

final _evmAddress = RegExp(r'^0x[0-9a-fA-F]{40}$');

/// What names a ticker in a post: its cashtags and, for a token, its contract
/// address, as the ticker's posts search spells them.
class TickerPostTerms {
  final Set<String> cashtags;
  final List<String> phrases;

  const TickerPostTerms({required this.cashtags, required this.phrases});

  /// The terms of [query]: `$NVDA`, `"0x…"`, `("0x…" OR $PEPE)`.
  factory TickerPostTerms.of(String query) => TickerPostTerms(
    cashtags: {for (final match in RegExp(r'\$([^\s()"]+)').allMatches(query)) match[1]!.toUpperCase()},
    phrases: [for (final match in RegExp(r'"([^"]+)"').allMatches(query)) match[1]!],
  );

  bool get isEmpty => cashtags.isEmpty && phrases.isEmpty;

  /// What a cached feed page must contain to hold such a post (SQL `LIKE`):
  /// a cheap first sieve, [names] decides.
  List<String> get likePatterns => [for (final tag in cashtags) '%\$$tag%', for (final phrase in phrases) '%$phrase%'];

  /// Whether [tweet] itself names the ticker: by its cashtags, not their
  /// letters, or by an address in its text or links.
  bool names(TweetWithCard tweet) =>
      tweetCashtags(tweet).any(cashtags.contains) || phrases.any((phrase) => _mentions(tweet, phrase));

  static bool _mentions(TweetWithCard tweet, String phrase) {
    final urls = tweet.noteEntities?.urls ?? tweet.entities?.urls ?? const [];
    final text = [tweet.noteText ?? tweet.fullText ?? tweet.text ?? '', for (final url in urls) ?url.expandedUrl];
    // An EVM address is the same in either case; any other is not.
    return _evmAddress.hasMatch(phrase)
        ? text.any((part) => part.toLowerCase().contains(phrase.toLowerCase()))
        : text.any((part) => part.contains(phrase));
  }
}

/// [tweet] counts when a followed account wrote it (a repost is someone
/// else's post) and it names the ticker.
bool isFollowedPost(TweetWithCard tweet, Set<String> followed, TickerPostTerms terms) =>
    tweet.retweetedStatusWithCard == null && followed.contains(tweet.user?.idStr) && terms.names(tweet);

/// The followed accounts that wrote such a post in [chains].
Set<String> followedPosters(Iterable<TweetChain> chains, Set<String> followed, TickerPostTerms terms) => {
  for (final chain in chains)
    for (final tweet in chain.tweets)
      if (isFollowedPost(tweet, followed, terms)) ?tweet.user?.idStr,
};

/// When [chain]'s newest counting post was written; null when none counts.
DateTime? _postedAt(TweetChain chain, Set<String> followed, TickerPostTerms terms) => chain.tweets
    .where((tweet) => isFollowedPost(tweet, followed, terms))
    .map((tweet) => tweet.createdAt)
    .nonNulls
    .fold<DateTime?>(null, (newest, at) => newest == null || at.isAfter(newest) ? at : newest);

/// The top part from everything [found]: chains with a counting post from
/// the last [kFollowedPostsWithin], each post once, newest first, at most
/// [kFollowedPostsShown].
List<TweetChain> followedSection(
  Iterable<TweetChain> found,
  Set<String> followed,
  TickerPostTerms terms, {
  required DateTime now,
}) {
  final cutoff = now.subtract(kFollowedPostsWithin);
  final dated = [
    for (final chain in found)
      if (_postedAt(chain, followed, terms) case final at? when at.isAfter(cutoff)) (chain: chain, at: at),
  ]..sort((a, b) => b.at.compareTo(a.at));
  return dated.fold<List<TweetChain>>(
    const [],
    (section, entry) => section.length < kFollowedPostsShown && withoutSection([entry.chain], section).isNotEmpty
        ? [...section, entry.chain]
        : section,
  );
}

/// Every post id in [chains].
Set<String> _postIds(Iterable<TweetChain> chains) => {
  for (final chain in chains)
    for (final tweet in chain.tweets) ?tweet.idStr,
};

/// [chains] without those that repeat a post of [section].
List<TweetChain> withoutSection(Iterable<TweetChain> chains, Iterable<TweetChain> section) {
  final ids = {for (final chain in section) chain.id};
  final posts = _postIds(section);
  return [
    for (final chain in chains)
      if (!ids.contains(chain.id) && !chain.tweets.any((tweet) => posts.contains(tweet.idStr))) chain,
  ];
}

/// The X searches for [query]'s posts by [follows], packed as the Following
/// feed packs its members, at most [kFollowedSearchesPerTicker]; accounts in
/// [knownPosters] go first.
List<String> followedTickerSearches(
  String query,
  Iterable<UserSubscription> follows, {
  Set<String> knownPosters = const {},
}) {
  if (query.isEmpty) return const [];
  final ordered = [
    ...follows.where((follow) => knownPosters.contains(follow.id)),
    ...follows.where((follow) => !knownPosters.contains(follow.id)),
  ];
  String term(UserSubscription follow) => userSearchTerm(follow.screenName);
  final chunks = packFeedChunks(ordered, term, includeReplies: true, includeRetweets: false, within: query);
  return [
    for (final chunk in chunks.take(kFollowedSearchesPerTicker))
      buildFeedSearchQuery(chunk.map(term), includeReplies: true, includeRetweets: false, within: query),
  ];
}
