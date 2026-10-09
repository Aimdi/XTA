import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_interleaved.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/feed_post_kinds.dart';
import 'package:xta/plugins/mastodon/mastodon_interleaved.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/threads/threads_interleaved.dart';
import 'package:xta/plugins/threads/threads_models.dart';

const FeedPostKinds _noReplies = (replies: false, reposts: true);
const FeedPostKinds _noReposts = (replies: true, reposts: false);

BlueskyPost _bsky(String id, {int day = 1, bool isReply = false, String? repostedBy}) => BlueskyPost(
  uri: 'at://did:plc:a/app.bsky.feed.post/$id',
  cid: 'cid-$id',
  handle: 'alice.bsky.social',
  did: 'did:plc:a',
  authorName: 'Alice',
  text: id,
  url: 'https://bsky.app/profile/alice.bsky.social/post/$id',
  publishedAt: DateTime.utc(2026, 8, day),
  isReply: isReply,
  repostedByHandle: repostedBy,
);

/// A `getAuthorFeed` item as the AppView sends it.
Map<String, Object?> _feedItem(String id, {Map<String, Object?>? reply, Map<String, Object?>? reason}) => {
  'post': {
    'uri': 'at://did:plc:a/app.bsky.feed.post/$id',
    'cid': 'cid-$id',
    'author': {'did': 'did:plc:a', 'handle': 'alice.bsky.social'},
    'record': {'text': id, 'createdAt': '2026-08-01T10:00:00Z', 'reply': ?reply},
  },
  'reason': ?reason,
};

class _Client extends BlueskyClient {
  _Client() : super(baseUrl: kBlueskyDefaultAppView);

  final filters = <String?>[];

  @override
  Future<BlueskyFeedPage> getAuthorFeed(String actor, {int limit = 20, String? cursor, String? filter}) async {
    filters.add(filter);
    return BlueskyFeedPage(posts: [_bsky(filter ?? 'all')]);
  }
}

MastodonPost _toot(String id, {bool isReply = false, bool boosted = false}) => MastodonPost(
  id: id,
  acct: 'alice@mastodon.social',
  authorName: 'Alice',
  text: id,
  url: 'https://mastodon.social/@alice/$id',
  publishedAt: DateTime.utc(2026, 8, 1),
  replyToId: isReply ? '1' : null,
  boosted: boosted,
);

ThreadsPost _thread(String id, {bool isReply = false, String? repostedBy}) => ThreadsPost(
  id: id,
  handle: 'alice',
  authorName: 'Alice',
  text: id,
  publishedAt: DateTime.utc(2026, 8, 1),
  isReply: isReply,
  repostedByHandle: repostedBy,
);

void main() {
  group('feedTakesPost', () {
    test('everything passes when the group takes everything', () {
      expect(feedTakesPost(allFeedPostKinds, isReply: true, isRepost: false), isTrue);
      expect(feedTakesPost(allFeedPostKinds, isReply: false, isRepost: true), isTrue);
    });

    test('replies off drops replies and keeps reposts, even of replies', () {
      expect(feedTakesPost(_noReplies, isReply: true, isRepost: false), isFalse);
      expect(feedTakesPost(_noReplies, isReply: false, isRepost: false), isTrue);
      expect(feedTakesPost(_noReplies, isReply: true, isRepost: true), isTrue);
    });

    test('reposts off drops reposts and keeps replies', () {
      expect(feedTakesPost(_noReposts, isReply: false, isRepost: true), isFalse);
      expect(feedTakesPost(_noReposts, isReply: true, isRepost: false), isTrue);
    });
  });

  test('each choice caches apart, and the existing names still read', () {
    expect(pluginFeedCacheSource('bluesky', allFeedPostKinds), 'bluesky');
    expect(pluginFeedCacheSource('bluesky', _noReplies), 'bluesky:no-replies');
    expect(pluginFeedCacheSource('bluesky', _noReposts), 'bluesky:no-reposts');
    expect(pluginFeedCacheSource('bluesky', (replies: false, reposts: false)), 'bluesky:no-replies:no-reposts');
  });

  group('Bluesky members of a group', () {
    final posts = [
      _bsky('post', day: 5),
      _bsky('reply', day: 4, isReply: true),
      _bsky('repost', day: 3, repostedBy: 'bob.bsky.social'),
    ];

    List<DateTime> datesOf(FeedPostKinds kinds) =>
        blueskyInterleavedItems(posts, kinds: kinds).map((e) => e.date).toList();

    test('replies off drops reply posts', () {
      expect(datesOf(_noReplies), [DateTime.utc(2026, 8, 5), DateTime.utc(2026, 8, 3)]);
    });

    test('replies on shows them', () {
      expect(datesOf(allFeedPostKinds), hasLength(3));
      expect(datesOf(_noReposts), contains(DateTime.utc(2026, 8, 4)));
    });

    test('reposts off drops reposts', () {
      expect(datesOf(_noReposts), [DateTime.utc(2026, 8, 5), DateTime.utc(2026, 8, 4)]);
    });

    test('hidden replies do not use up the page', () {
      final replyHeavy = [
        for (var day = 10; day > 1; day--) _bsky('r$day', day: day, isReply: true),
        _bsky('post', day: 1),
      ];
      final items = blueskyInterleavedItems(replyHeavy, limit: 2, kinds: _noReplies);
      expect(items.map((e) => e.date), [DateTime.utc(2026, 8, 1)]);
    });

    test('a self-thread continuation is a reply, as on X', () {
      final self = {
        'root': {'uri': 'at://did:plc:a/app.bsky.feed.post/root'},
        'parent': {'uri': 'at://did:plc:a/app.bsky.feed.post/root'},
      };
      final parsed = parseBlueskyFeed({
        'feed': [_feedItem('continued', reply: self)],
      });
      expect(parsed.single.isReply, isTrue);
      expect(blueskyFeedTakes(_noReplies, parsed.single), isFalse);
    });

    test('a repost reason is read as a repost', () {
      final parsed = parseBlueskyFeed({
        'feed': [
          _feedItem(
            'shared',
            reason: {
              r'$type': 'app.bsky.feed.defs#reasonRepost',
              'by': {'handle': 'bob.bsky.social'},
            },
          ),
        ],
      });
      expect(parsed.single.isRepost, isTrue);
      expect(blueskyFeedTakes(_noReposts, parsed.single), isFalse);
    });

    test('the AppView is asked for posts without replies, apart from the tab', () async {
      final client = _Client();
      final store = BlueskyFeedStore(client, BlueskyAccountsStore());

      final hidden = await store.postsFor(['alice.bsky.social'], withReplies: false);
      final shown = await store.postsFor(['alice.bsky.social']);

      expect(client.filters, [blueskyAuthorFeedNoReplies, null]);
      expect(hidden.single.text, blueskyAuthorFeedNoReplies);
      expect(shown.single.text, 'all');
    });
  });

  group('Mastodon members of a group', () {
    test('a reply to yourself is a reply, though it mentions nobody', () {
      final post = mastodonPostFromStatus({
        'id': '2',
        'content': '<p>continued</p>',
        'url': 'https://mastodon.social/@alice/2',
        'created_at': '2026-08-01T10:00:00Z',
        'in_reply_to_id': '1',
        'in_reply_to_account_id': '42',
        'mentions': <Object?>[],
        'account': {'id': '42', 'acct': 'alice', 'username': 'alice'},
      });
      expect(post?.isReply, isTrue);
      expect(post?.replyToAcct, isNull);
    });

    test('replies off and reposts off drop replies and boosts', () {
      final posts = [_toot('post'), _toot('reply', isReply: true), _toot('boost', boosted: true)];
      expect(mastodonInterleavedItems(posts, kinds: _noReplies), hasLength(2));
      expect(mastodonInterleavedItems(posts, kinds: _noReposts), hasLength(2));
      expect(mastodonInterleavedItems(posts), hasLength(3));
    });
  });

  group('Threads members of a group', () {
    test('replies off and reposts off drop replies and reposts', () {
      final posts = [_thread('post'), _thread('reply', isReply: true), _thread('repost', repostedBy: 'bob')];
      expect(threadsInterleavedItems(posts, kinds: _noReplies), hasLength(2));
      expect(threadsInterleavedItems(posts, kinds: _noReposts), hasLength(2));
      expect(threadsInterleavedItems(posts), hasLength(3));
    });
  });
}
