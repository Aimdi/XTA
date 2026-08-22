import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/feed_source_reload.dart';
import 'package:xta/group/group_members.dart';
import 'package:xta/plugins/bluesky/bluesky_plugin.dart';
import 'package:xta/plugins/mastodon/mastodon_plugin.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/plugins/reddit/reddit_plugin.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/plugins/subscription_source.dart';
import 'package:xta/plugins/threads/threads_plugin.dart';

final _at = DateTime.utc(2026, 8, 21);

UserSubscription _x(String screenName) => UserSubscription(
  id: screenName,
  screenName: screenName,
  name: screenName,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: _at,
  inFeed: true,
);

RedditSubscription _reddit(String name) => RedditSubscription(
  id: name.toLowerCase(),
  name: name,
  createdAt: _at,
  inFeed: true,
);

SubstackSubscription _substack(String id) => SubstackSubscription(
  id: id,
  baseUrl: 'https://$id.substack.com',
  name: id,
  logoUrl: null,
  createdAt: _at,
  inFeed: true,
);

BlueskySubscription _bluesky(String handle) => BlueskySubscription(
  id: handle,
  name: handle,
  avatarUrl: null,
  createdAt: _at,
  inFeed: true,
);

ThreadsSubscription _threads(String handle) => ThreadsSubscription(
  id: handle,
  name: handle,
  avatarUrl: null,
  createdAt: _at,
  inFeed: true,
);

MastodonSubscription _mastodon(String acct) => MastodonSubscription(
  id: acct,
  name: acct,
  avatarUrl: null,
  createdAt: _at,
  inFeed: true,
);

S _source<S extends SubscriptionSource>() =>
    subscriptionSources.whereType<S>().single;

List<String> _ids(GroupMemberSplit split, SubscriptionSource source) =>
    pluginMemberIds(split.pluginMembers, source);

void main() {
  group('splitGroupMembers', () {
    test(
      'a mixed group keeps X members for search and plugin members for their own fetch',
      () {
        final split = splitGroupMembers([
          _x('alice'),
          _reddit('flutter'),
          _substack('platformer'),
          SearchSubscription(id: 'dartlang', createdAt: _at),
        ]);

        expect(split.xMembers.map((e) => e.id), ['alice', 'dartlang']);
        expect(_ids(split, _source<RedditPlugin>()), ['flutter']);
        expect(_ids(split, _source<SubstackPlugin>()), ['platformer']);
        expect(
          split.pluginMembers.keys.whereType<RedditPlugin>(),
          hasLength(1),
        );
        expect(
          split.pluginMembers.keys.whereType<SubstackPlugin>(),
          hasLength(1),
        );
      },
    );

    test('Reddit and Substack members are never searched on X', () {
      final split = splitGroupMembers([
        _reddit('flutter'),
        _substack('platformer'),
      ]);

      expect(split.xMembers, isEmpty);
      expect(split.pluginMembers, isNotEmpty);
    });

    test('other add-to-group plugins split the same way', () {
      final split = splitGroupMembers([
        _x('bob'),
        _bluesky('alice.bsky.social'),
        _threads('zuck'),
        _mastodon('alice@example.social'),
      ]);

      expect(split.xMembers.map((e) => e.id), ['bob']);
      expect(_ids(split, _source<BlueskyPlugin>()), ['alice.bsky.social']);
      expect(_ids(split, _source<ThreadsPlugin>()), ['zuck']);
      expect(_ids(split, _source<MastodonPlugin>()), ['alice@example.social']);
    });

    test('an X-only group has no plugin slots', () {
      final split = splitGroupMembers([_x('alice')]);

      expect(split.xMembers, hasLength(1));
      expect(split.pluginMembers, isEmpty);
    });
  });

  group('what a group feed asks each plugin for', () {
    // The ids handed to interleavedPosts. A regular group uses its own
    // members — not every followed account, and not the home-feed toggle.
    test(
      'are the group members, so adding a subreddit or publication is enough',
      () {
        final split = splitGroupMembers([
          _x('alice'),
          _reddit('Flutter'),
          _substack('platformer'),
        ]);

        expect(
          sourceIdsFor(
            memberIds: _ids(split, _source<RedditPlugin>()),
            isCombinedFeed: false,
            inHomeFeed: true,
            homeFeedIds: const ['dartlang'],
          ),
          ['flutter'],
        );
        expect(
          sourceIdsFor(
            memberIds: _ids(split, _source<SubstackPlugin>()),
            isCombinedFeed: false,
            inHomeFeed: false,
            homeFeedIds: const ['other'],
          ),
          ['platformer'],
        );
      },
    );

    test('X members still have ids of their own for the search query', () {
      final split = splitGroupMembers([_x('alice'), _reddit('flutter')]);

      expect(split.xMembers.map((e) => e.screenName), ['alice']);
      expect(split.xMembers, isNot(contains(_reddit('flutter'))));
    });
  });
}
