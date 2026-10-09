import 'package:flutter_triple/flutter_triple.dart';

/// How the replies under an opened post are ordered.
enum ReplySort { relevant, recent, oldest, mostLiked }

/// How the quotes of a post are ordered.
enum QuoteSort { recent, top, oldest, mostLiked }

/// How the people who reposted a post are ordered.
enum ReposterSort { recent, mostFollowers }

/// X ranks replies on the server through TweetDetail's `rankingMode`.
const xReplySorts = [ReplySort.relevant, ReplySort.recent, ReplySort.mostLiked];

/// Bluesky's AppView returns its own ranking; the rest is sorted on device.
const blueskyReplySorts = [
  ReplySort.relevant,
  ReplySort.recent,
  ReplySort.oldest,
  ReplySort.mostLiked,
];

/// Mastodon has no ranking: `/context` is the conversation, oldest first.
const mastodonReplySorts = [
  ReplySort.oldest,
  ReplySort.recent,
  ReplySort.mostLiked,
];

/// X ranks quotes on the server through SearchTimeline's `product`.
const xQuoteSorts = [QuoteSort.recent, QuoteSort.top];

/// Bluesky and Mastodon quote lists have no ranking; sorted on device.
const pluginQuoteSorts = [
  QuoteSort.recent,
  QuoteSort.oldest,
  QuoteSort.mostLiked,
];

/// The sort choices a reader made this session, shared by every network.
class ConversationSorts {
  final ReplySort replies;
  final QuoteSort quotes;
  final ReposterSort reposters;

  const ConversationSorts({
    this.replies = ReplySort.relevant,
    this.quotes = QuoteSort.recent,
    this.reposters = ReposterSort.recent,
  });

  ConversationSorts copyWith({
    ReplySort? replies,
    QuoteSort? quotes,
    ReposterSort? reposters,
  }) => ConversationSorts(
    replies: replies ?? this.replies,
    quotes: quotes ?? this.quotes,
    reposters: reposters ?? this.reposters,
  );
}

/// Remembers the reply, quote and repost order for the rest of the session.
class ConversationSortStore extends Store<ConversationSorts> {
  ConversationSortStore([super.initialState = const ConversationSorts()]);

  void selectReplies(ReplySort sort) => update(state.copyWith(replies: sort));

  void selectQuotes(QuoteSort sort) => update(state.copyWith(quotes: sort));

  void selectReposters(ReposterSort sort) =>
      update(state.copyWith(reposters: sort));
}

/// [chosen] when this network offers it, else the network's own default.
T effectiveSort<T>(T chosen, List<T> options) =>
    options.contains(chosen) ? chosen : options.first;

/// TweetDetail's `rankingMode` variable for [sort].
String xRankingMode(ReplySort sort) =>
    switch (effectiveSort(sort, xReplySorts)) {
      ReplySort.recent => 'Recency',
      ReplySort.mostLiked => 'Likes',
      ReplySort.relevant || ReplySort.oldest => 'Relevance',
    };

/// SearchTimeline's `product` for a `quoted_tweet_id:` query.
String xQuotesProduct(QuoteSort sort) =>
    switch (effectiveSort(sort, xQuoteSorts)) {
      QuoteSort.top => 'Top',
      QuoteSort.recent || QuoteSort.oldest || QuoteSort.mostLiked => 'Latest',
    };

enum _PostOrder { asSent, newest, oldest, mostLiked }

/// Sibling replies in the order [sort] asks for. The network's own order, the
/// first of [options], keeps the replies as they were sent.
List<T> orderReplies<T>(
  List<T> replies,
  ReplySort sort, {
  required List<ReplySort> options,
  required DateTime? Function(T reply) postedAt,
  required int Function(T reply) likes,
}) {
  final effective = effectiveSort(sort, options);
  final order = effective == options.first
      ? _PostOrder.asSent
      : switch (effective) {
          ReplySort.relevant => _PostOrder.asSent,
          ReplySort.recent => _PostOrder.newest,
          ReplySort.oldest => _PostOrder.oldest,
          ReplySort.mostLiked => _PostOrder.mostLiked,
        };
  return _ordered(replies, order, postedAt: postedAt, likes: likes);
}

/// Loaded quotes in the order [sort] asks for, sorted on device.
List<T> orderQuotes<T>(
  List<T> quotes,
  QuoteSort sort, {
  required DateTime? Function(T quote) postedAt,
  required int Function(T quote) likes,
}) => _ordered(
  quotes,
  switch (effectiveSort(sort, pluginQuoteSorts)) {
    QuoteSort.recent || QuoteSort.top => _PostOrder.newest,
    QuoteSort.oldest => _PostOrder.oldest,
    QuoteSort.mostLiked => _PostOrder.mostLiked,
  },
  postedAt: postedAt,
  likes: likes,
);

List<T> _ordered<T>(
  List<T> posts,
  _PostOrder order, {
  required DateTime? Function(T post) postedAt,
  required int Function(T post) likes,
}) => switch (order) {
  _PostOrder.asSent => posts,
  _PostOrder.newest => _stableSorted(
    posts,
    (a, b) => _compareTimes(postedAt(a), postedAt(b), newestFirst: true),
  ),
  _PostOrder.oldest => _stableSorted(
    posts,
    (a, b) => _compareTimes(postedAt(a), postedAt(b), newestFirst: false),
  ),
  _PostOrder.mostLiked => _stableSorted(
    posts,
    (a, b) => likes(b).compareTo(likes(a)),
  ),
};

/// Undated posts sort last in either direction.
int _compareTimes(DateTime? a, DateTime? b, {required bool newestFirst}) {
  if (a == null || b == null) {
    return (a == null ? 1 : 0) - (b == null ? 1 : 0);
  }
  return newestFirst ? b.compareTo(a) : a.compareTo(b);
}

/// Orders people who reposted. [ReposterSort.recent] is the network's own
/// order; people without a follower count sort last for
/// [ReposterSort.mostFollowers].
List<T> sortReposters<T>(
  List<T> people,
  ReposterSort sort, {
  required int? Function(T person) followers,
}) => switch (sort) {
  ReposterSort.recent => people,
  ReposterSort.mostFollowers => _stableSorted(
    people,
    (a, b) => (followers(b) ?? -1).compareTo(followers(a) ?? -1),
  ),
};

/// The reposter orders the loaded data can back: follower order only when
/// the response actually carried follower counts.
List<ReposterSort> reposterSortsFor<T>(
  List<T> people, {
  required int? Function(T person) followers,
}) => [
  ReposterSort.recent,
  if (people.any((person) => followers(person) != null))
    ReposterSort.mostFollowers,
];

/// [List.sort] is not stable; ties keep their original order here.
List<T> _stableSorted<T>(List<T> items, int Function(T a, T b) compare) {
  final indexed = items.indexed.toList()
    ..sort((a, b) {
      final byValue = compare(a.$2, b.$2);
      return byValue != 0 ? byValue : a.$1.compareTo(b.$1);
    });
  return [for (final (_, item) in indexed) item];
}
