import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_store.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

Map<String, Object?> _postView(String id, {String text = 'Hi'}) => {
  'uri': 'at://did:plc:abc/app.bsky.feed.post/$id',
  'cid': 'cid-$id',
  'author': {
    'did': 'did:plc:abc',
    'handle': 'alice.bsky.social',
    'displayName': 'Alice',
  },
  'record': {'text': text, 'createdAt': '2026-08-01T10:00:00.000Z'},
};

void main() {
  group('parseBlueskyFeedRef', () {
    test('reads bsky.app feed URLs and AT-URIs', () {
      final web = parseBlueskyFeedRef(
        'https://bsky.app/profile/bsky.app/feed/whats-hot',
      );
      expect(web?.actor, 'bsky.app');
      expect(web?.rkey, 'whats-hot');

      final at = parseBlueskyFeedRef(kBlueskyDiscoverFeedUri);
      expect(at?.atUri, kBlueskyDiscoverFeedUri);
      expect(parseBlueskyFeedRef('https://bsky.app/profile/bsky.app'), isNull);
    });
  });

  group('parseBlueskyFeedGenerators', () {
    test('reads generatorView rows and a singular view', () {
      final page = parseBlueskyFeedGeneratorsPage({
        'cursor': 'next',
        'feeds': [
          {
            'uri': kBlueskyDiscoverFeedUri,
            'displayName': 'Discover',
            'description': "What's hot",
            'creator': {'handle': 'bsky.app'},
          },
          {'uri': '', 'displayName': 'drop me'},
        ],
      });
      expect(page.cursor, 'next');
      expect(page.feeds, hasLength(1));
      expect(page.feeds.single.displayName, 'Discover');
      expect(page.feeds.single.creatorHandle, 'bsky.app');

      final single = parseBlueskyFeedGenerators({
        'view': {
          'uri': 'at://did:plc:x/app.bsky.feed.generator/for-you',
          'displayName': 'For You',
        },
      });
      expect(single.single.displayName, 'For You');
    });

    test('tolerates a reshaped payload', () {
      expect(parseBlueskyFeedGenerators(null), isEmpty);
      expect(parseBlueskyFeedGenerators({'feeds': 'nope'}), isEmpty);
    });
  });

  group('list feed parse', () {
    test('getListFeed uses the same feed item shape as getAuthorFeed', () {
      final posts = parseBlueskyFeed({
        'feed': [
          {'post': _postView('r1', text: 'From a list')},
        ],
      });
      expect(posts.single.text, 'From a list');
    });

    test('listView keeps description and creator', () {
      final list = BlueskyListInfo.fromJson({
        'uri': 'at://did:plc:a/app.bsky.graph.list/1',
        'name': 'Cool',
        'description': 'Friends',
        'listItemCount': 4,
        'creator': {'handle': 'alice.bsky.social'},
      });
      expect(list.description, 'Friends');
      expect(list.creatorHandle, 'alice.bsky.social');
    });
  });

  group('BlueskyAlgoStore', () {
    test('opening the same cached feed does not refetch', () async {
      var feedCalls = 0;
      var popularCalls = 0;
      final client = BlueskyClient(
        httpClient: MockClient((request) async {
          final path = request.url.path;
          if (path.endsWith('getPopularFeedGenerators')) {
            popularCalls++;
            return http.Response(
              jsonEncode({
                'feeds': [
                  {'uri': kBlueskyDiscoverFeedUri, 'displayName': 'Discover'},
                ],
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          if (path.endsWith('getFeed')) {
            feedCalls++;
            return http.Response(
              jsonEncode({
                'feed': [
                  {'post': _postView('a', text: 'Algo')},
                ],
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          return http.Response('unexpected $path', 500);
        }),
      );
      final store = BlueskyAlgoStore(client, PrefServiceCache());

      await store.ensureLoaded(discoverName: 'Discover');
      await store.ensureLoaded(discoverName: 'Discover');
      await store.open(kBlueskyDiscoverFeedUri);

      expect(popularCalls, 1);
      expect(feedCalls, 1);
      expect(store.feedFetches, 1);
      expect(store.state.posts.single.text, 'Algo');

      await store.open(kBlueskyDiscoverFeedUri, force: true);
      expect(feedCalls, 2);
    });

    test('pin persists and unpin removes the URI', () async {
      final prefs = PrefServiceCache();
      final store = BlueskyAlgoStore(
        BlueskyClient(
          httpClient: MockClient((_) async => http.Response('nope', 500)),
        ),
        prefs,
      );
      const feed = BlueskyFeedGenerator(
        uri: kBlueskyDiscoverFeedUri,
        displayName: 'Discover',
      );
      await store.pin(feed);
      await store.pin(feed);
      expect(store.state.pinned, hasLength(1));
      expect(
        prefs.get<String>(optionPluginBlueskyPinnedFeeds),
        contains('whats-hot'),
      );
      await store.unpin(kBlueskyDiscoverFeedUri);
      expect(store.state.pinned, isEmpty);
    });
  });

  group('BlueskyListsStore', () {
    test('opening the same cached list does not refetch', () async {
      var listFeedCalls = 0;
      final client = BlueskyClient(
        httpClient: MockClient((request) async {
          final path = request.url.path;
          if (path.endsWith('getListFeed')) {
            listFeedCalls++;
            return http.Response(
              jsonEncode({
                'feed': [
                  {'post': _postView('l1', text: 'List post')},
                ],
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          return http.Response('unexpected $path', 500);
        }),
      );
      final store = BlueskyListsStore(client, PrefServiceCache());
      const uri = 'at://did:plc:a/app.bsky.graph.list/1';

      await store.open(uri, name: 'Cool');
      await store.open(uri, name: 'Cool');
      await store.ensureLoaded();

      expect(listFeedCalls, 1);
      expect(store.feedFetches, 1);
      expect(store.state.posts.single.text, 'List post');
    });
  });

  group('BlueskyClient feed endpoints', () {
    test('getFeed and getListFeed hit the AppView paths', () async {
      final paths = <String>[];
      final client = BlueskyClient(
        httpClient: MockClient((request) async {
          paths.add(request.url.path);
          return http.Response(
            jsonEncode({
              'feed': [
                {'post': _postView('x', text: 'Via ${request.url.path}')},
              ],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final algo = await client.getFeed(kBlueskyDiscoverFeedUri);
      final list = await client.getListFeed(
        'at://did:plc:a/app.bsky.graph.list/1',
      );
      expect(algo.posts, hasLength(1));
      expect(list.posts, hasLength(1));
      expect(paths, [
        '/xrpc/app.bsky.feed.getFeed',
        '/xrpc/app.bsky.feed.getListFeed',
      ]);
    });
  });
}
