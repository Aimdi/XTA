import 'package:flutter_test/flutter_test.dart';
import 'package:infinite_scroll_pagination/infinite_scroll_pagination.dart';
import 'package:xta/client/client.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_store.dart';
import 'package:xta/plugins/plugin_activity.dart';
import 'package:xta/ui/conversation_sort.dart';
import 'package:xta/ui/sort_menu_button.dart';
import 'package:xta/utils/paging.dart';

typedef _Post = ({String id, DateTime? at, int likes});

_Post _post(String id, {int? minute, int likes = 0}) => (
  id: id,
  at: minute == null ? null : DateTime.utc(2026, 1, 1, 12, minute),
  likes: likes,
);

List<String> _replyIds(
  List<_Post> replies,
  ReplySort sort,
  List<ReplySort> options,
) => orderReplies(
  replies,
  sort,
  options: options,
  postedAt: (r) => r.at,
  likes: (r) => r.likes,
).map((r) => r.id).toList();

MastodonPost _toot(String id, {String? parent, int? minute, int likes = 0}) =>
    MastodonPost(
      id: id,
      replyToId: parent,
      acct: 'a@example.social',
      authorName: 'A',
      text: id,
      url: 'https://example.social/@a/$id',
      publishedAt: minute == null ? null : DateTime.utc(2026, 1, 1, 12, minute),
      favouritesCount: likes,
    );

/// `/context` order: depth first, siblings oldest first.
final _conversation = MastodonThread(
  status: _toot('focal'),
  descendants: [
    _toot('r1', parent: 'focal', minute: 10, likes: 3),
    _toot('r1a', parent: 'r1', minute: 50),
    _toot('r2', parent: 'focal', minute: 30, likes: 9),
    _toot('r3', parent: 'focal', minute: 20, likes: 3),
    _toot('r3a', parent: 'r3', minute: 21),
    _toot('r3b', parent: 'r3', minute: 25, likes: 5),
  ],
);

List<String> _tootOrder(ReplySort sort) => mastodonReplyRows(
  _conversation,
  {},
  order: sort,
).map((row) => row.post.id).toList();

void main() {
  group('X request variables', () {
    test('each reply order maps to its TweetDetail rankingMode', () {
      expect(xRankingMode(ReplySort.relevant), 'Relevance');
      expect(xRankingMode(ReplySort.recent), 'Recency');
      expect(xRankingMode(ReplySort.mostLiked), 'Likes');
      // Not an X order: falls back to X's default rather than inventing one.
      expect(xRankingMode(ReplySort.oldest), 'Relevance');
    });

    test('TweetDetail variables carry the ranking mode and cursor', () {
      final first = Twitter.tweetDetailVariables(
        '42',
        rankingMode: xRankingMode(ReplySort.recent),
      );
      expect(first['focalTweetId'], '42');
      expect(first['rankingMode'], 'Recency');
      expect(first.containsKey('cursor'), isFalse);

      final next = Twitter.tweetDetailVariables(
        '42',
        cursor: 'c1',
        rankingMode: xRankingMode(ReplySort.mostLiked),
      );
      expect(next['rankingMode'], 'Likes');
      expect(next['cursor'], 'c1');
    });

    test('TweetDetail keeps relevance when no order is given', () {
      expect(Twitter.tweetDetailVariables('42')['rankingMode'], 'Relevance');
    });

    test('each quote order maps to its SearchTimeline product', () {
      expect(xQuotesProduct(QuoteSort.top), 'Top');
      expect(xQuotesProduct(QuoteSort.recent), 'Latest');
      // Device-side orders from other networks fall back to X's default.
      expect(xQuotesProduct(QuoteSort.mostLiked), 'Latest');
    });

    test('an order the network lacks falls back to its default', () {
      expect(effectiveSort(ReplySort.oldest, xReplySorts), ReplySort.relevant);
      expect(
        effectiveSort(ReplySort.relevant, mastodonReplySorts),
        ReplySort.oldest,
      );
      expect(
        effectiveSort(ReplySort.mostLiked, blueskyReplySorts),
        ReplySort.mostLiked,
      );
      expect(effectiveSort(QuoteSort.oldest, xQuoteSorts), QuoteSort.recent);
      expect(effectiveSort(QuoteSort.top, pluginQuoteSorts), QuoteSort.recent);
    });
  });

  group('orderReplies', () {
    final replies = [
      _post('a', minute: 10, likes: 3),
      _post('b', minute: 30, likes: 9),
      _post('c', minute: 20, likes: 3),
      _post('undated'),
    ];

    test('the network default keeps the order it sent', () {
      expect(_replyIds(replies, ReplySort.relevant, blueskyReplySorts), [
        'a',
        'b',
        'c',
        'undated',
      ]);
      expect(_replyIds(replies, ReplySort.oldest, mastodonReplySorts), [
        'a',
        'b',
        'c',
        'undated',
      ]);
    });

    test('recent and oldest sort by date, undated last either way', () {
      expect(_replyIds(replies, ReplySort.recent, blueskyReplySorts), [
        'b',
        'c',
        'a',
        'undated',
      ]);
      expect(_replyIds(replies, ReplySort.oldest, blueskyReplySorts), [
        'a',
        'c',
        'b',
        'undated',
      ]);
    });

    test('most liked orders by likes and keeps ties in network order', () {
      expect(_replyIds(replies, ReplySort.mostLiked, mastodonReplySorts), [
        'b',
        'a',
        'c',
        'undated',
      ]);
    });
  });

  group('Mastodon reply order', () {
    test('oldest is the /context order', () {
      expect(_tootOrder(ReplySort.oldest), [
        'r1',
        'r1a',
        'r2',
        'r3',
        'r3a',
        'r3b',
      ]);
      // Relevance is not a Mastodon order: the server's order stands.
      expect(_tootOrder(ReplySort.relevant), _tootOrder(ReplySort.oldest));
    });

    test('recent orders siblings at every level and keeps sub-threads', () {
      expect(_tootOrder(ReplySort.recent), [
        'r2',
        'r3',
        'r3b',
        'r3a',
        'r1',
        'r1a',
      ]);
      final depths = {
        for (final row in mastodonReplyRows(
          _conversation,
          {},
          order: ReplySort.recent,
        ))
          row.post.id: row.depth,
      };
      expect(depths, {'r2': 0, 'r3': 0, 'r3b': 1, 'r3a': 1, 'r1': 0, 'r1a': 1});
    });

    test('most liked keeps ties in server order', () {
      expect(_tootOrder(ReplySort.mostLiked), [
        'r2',
        'r1',
        'r1a',
        'r3',
        'r3b',
        'r3a',
      ]);
    });

    test('the thread store starts from the session choice it can honour', () {
      final client = MastodonClient();
      addTearDown(client.httpClient.close);
      final fromRelevant = MastodonThreadStore(
        client,
        const [],
        _toot('focal'),
        order: ReplySort.relevant,
      );
      final fromLiked = MastodonThreadStore(
        client,
        const [],
        _toot('focal'),
        order: ReplySort.mostLiked,
      );
      addTearDown(fromRelevant.destroy);
      addTearDown(fromLiked.destroy);
      expect(fromRelevant.state.order, ReplySort.oldest);
      expect(fromLiked.state.order, ReplySort.mostLiked);
      fromRelevant.selectOrder(ReplySort.recent);
      expect(fromRelevant.state.order, ReplySort.recent);
    });
  });

  group('quotes on device', () {
    final quotes = [
      _post('a', minute: 10, likes: 1),
      _post('b', minute: 30, likes: 2),
      _post('c', minute: 20, likes: 7),
    ];
    List<String> ids(QuoteSort sort) => orderQuotes(
      quotes,
      sort,
      postedAt: (q) => q.at,
      likes: (q) => q.likes,
    ).map((q) => q.id).toList();

    test('recent, oldest and most liked sort what is loaded', () {
      expect(ids(QuoteSort.recent), ['b', 'c', 'a']);
      expect(ids(QuoteSort.oldest), ['a', 'c', 'b']);
      expect(ids(QuoteSort.mostLiked), ['c', 'b', 'a']);
      // X's server-side Top is not a device order: recent stands in.
      expect(ids(QuoteSort.top), ids(QuoteSort.recent));
    });

    test('the activity sort offers every device order', () {
      final store = ConversationSortStore()..selectQuotes(QuoteSort.mostLiked);
      addTearDown(store.destroy);
      final sorted = pluginQuoteSort<_Post>(
        postedAt: (q) => q.at,
        likes: (q) => q.likes,
      ).apply(store, quotes);
      expect(sorted.items.map((q) => q.id), ['c', 'b', 'a']);
      final control = sorted.control as SortMenuButton<QuoteSort>;
      expect(control.value, QuoteSort.mostLiked);
      expect(control.options, pluginQuoteSorts);
    });

    test('a single quote needs no control', () {
      final store = ConversationSortStore();
      addTearDown(store.destroy);
      final sorted = pluginQuoteSort<_Post>(
        postedAt: (q) => q.at,
        likes: (q) => q.likes,
      ).apply(store, quotes.take(1).toList());
      expect(sorted.control, isNull);
    });
  });

  group('reposters', () {
    final people = [
      (name: 'a', followers: 5),
      (name: 'b', followers: null),
      (name: 'c', followers: 50),
    ];
    int? followers(({String name, int? followers}) p) => p.followers;

    test('recent keeps the order the network returned', () {
      expect(
        sortReposters(
          people,
          ReposterSort.recent,
          followers: followers,
        ).map((p) => p.name),
        ['a', 'b', 'c'],
      );
    });

    test('most followers sorts on device, unknown counts last', () {
      expect(
        sortReposters(
          people,
          ReposterSort.mostFollowers,
          followers: followers,
        ).map((p) => p.name),
        ['c', 'a', 'b'],
      );
    });

    test('follower order is offered only when counts are present', () {
      expect(reposterSortsFor(people, followers: followers), [
        ReposterSort.recent,
        ReposterSort.mostFollowers,
      ]);
      expect(
        reposterSortsFor([(name: 'x', followers: null)], followers: followers),
        [ReposterSort.recent],
      );
    });

    test('the activity sort hides its control without follower counts', () {
      final store = ConversationSortStore()
        ..selectReposters(ReposterSort.mostFollowers);
      addTearDown(store.destroy);
      final sort = pluginReposterSort(followers: followers);
      final unknown = sort.apply(store, [
        (name: 'x', followers: null),
        (name: 'y', followers: null),
      ]);
      expect(unknown.control, isNull);
      expect(unknown.items.map((p) => p.name), ['x', 'y']);
      final known = sort.apply(store, people);
      expect(known.control, isNotNull);
      expect(known.items.map((p) => p.name), ['c', 'a', 'b']);
    });

    test('Retweeters users carry the follower count when X sends one', () {
      final users = TimelineParser.parseUsersTimeline([
        {
          'type': 'TimelineAddEntries',
          'entries': [
            {
              'content': {
                'itemContent': {
                  'user_results': {
                    'result': {
                      'rest_id': '1',
                      'core': {'screen_name': 'alice', 'name': 'Alice'},
                      'legacy': {'followers_count': 1234},
                    },
                  },
                },
              },
            },
            {
              'content': {
                'itemContent': {
                  'user_results': {
                    'result': {
                      'rest_id': '2',
                      'core': {'screen_name': 'bob', 'name': 'Bob'},
                    },
                  },
                },
              },
            },
          ],
        },
      ]);
      expect(users.users!.map((u) => u.followersCount), [1234, null]);
    });

    test('reordering paged items keeps the page sizes', () {
      final state = PagingState<int, int>(
        pages: const [
          [1, 3],
          [2],
        ],
        keys: const [0, 1],
        hasNextPage: true,
      );
      final sorted = reorderPagingItems(
        state,
        (items) => [...items]..sort((a, b) => b.compareTo(a)),
      );
      expect(sorted.pages, [
        [3, 2],
        [1],
      ]);
      expect(sorted.keys, [0, 1]);
      expect(sorted.hasNextPage, isTrue);
    });
  });

  test('the store remembers each choice independently', () {
    final store = ConversationSortStore();
    addTearDown(store.destroy);
    store.selectReplies(ReplySort.mostLiked);
    store.selectQuotes(QuoteSort.top);
    expect(store.state.replies, ReplySort.mostLiked);
    expect(store.state.quotes, QuoteSort.top);
    expect(store.state.reposters, ReposterSort.recent);
  });
}
