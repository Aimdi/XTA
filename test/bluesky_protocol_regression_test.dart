import 'dart:convert';

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_facets.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

const _did = 'did:plc:abc';
String _uri(String key) => 'at://$_did/app.bsky.feed.post/$key';

Map<String, Object?> _view(String key, {String text = 'Post'}) => {
  'uri': _uri(key),
  'cid': 'cid-$key',
  'author': {'did': _did, 'handle': 'alice.test', 'displayName': 'Alice'},
  'record': <String, Object?>{'text': text, 'createdAt': '2026-09-20T10:00:00Z'},
};

http.Response _json(Object value) =>
    http.Response(jsonEncode(value), 200, headers: {'content-type': 'application/json'});

void main() {
  group('Bluesky record and snapshot fidelity', () {
    test('leading whitespace and Unicode retain the exact linked facet span', () {
      const prefix = '  👋 Grüße ';
      const linked = '@alice.test';
      const suffix = '\n  ';
      const body = '$prefix$linked$suffix';
      final view = _view('facet', text: body);
      (view['record'] as Map<String, Object?>)['facets'] = [
        {
          'index': {'byteStart': utf8.encode(prefix).length, 'byteEnd': utf8.encode('$prefix$linked').length},
          'features': [
            {'\$type': 'app.bsky.richtext.facet#mention', 'did': _did},
          ],
        },
      ];
      final post = blueskyPostFromView(view)!;
      final recognizers = <GestureRecognizer>[];
      addTearDown(() {
        for (final recognizer in recognizers) {
          recognizer.dispose();
        }
      });
      final spans = blueskyRichTextSpans(
        text: post.text,
        facets: post.facets,
        style: const TextStyle(),
        linkStyle: const TextStyle(),
        onFacetTap: (_) {},
        recognizers: recognizers,
      );
      expect(post.text, body);
      expect(spans.whereType<TextSpan>().map((span) => span.text).join(), body);
      expect(spans.whereType<TextSpan>().singleWhere((span) => span.recognizer != null).text, linked);
    });

    test('repost activity date remains separate from publication through offline snapshots', () {
      final post = blueskyPostFromFeedItem({
        'post': _view('repost'),
        'reason': {
          '\$type': 'app.bsky.feed.defs#reasonRepost',
          'by': {'did': 'did:plc:def', 'handle': 'bob.test'},
          'indexedAt': '2026-09-25T12:00:00Z',
        },
      })!;
      final restored = BlueskyPost.listFromPrefs(BlueskyPost.listToPrefs([post])).single;
      expect(restored.publishedAt?.toUtc(), DateTime.utc(2026, 9, 20, 10));
      expect(restored.repostedAt?.toUtc(), DateTime.utc(2026, 9, 25, 12));
      expect(restored.timelineDate, restored.repostedAt);
      expect(restored.repostedByDid, 'did:plc:def');
    });

    test('malformed optional cached fields cannot discard other saved posts', () {
      final raw = blueskyPostFromView(_view('damaged'))!.toJson()
        ..addAll({
          'avatarUrl': false,
          'replyCount': -20,
          'likeCount': 'not-a-number',
          'images': ['https://example.org/one.jpg', 12, null],
          'facets': [
            {'kind': {}, 'byteStart': 'bad', 'byteEnd': 8, 'value': []},
          ],
          'quotedPost': {'uri': _uri('quote'), 'authorName': 24, 'text': false},
          'linkCard': {'url': 'https://example.org/story', 'title': 3, 'imageUrl': false},
        });
      final restored = BlueskyPost.listFromPrefs(jsonEncode([raw, blueskyPostFromView(_view('intact'))!.toJson()]));
      expect(restored.map((post) => post.uri), [_uri('damaged'), _uri('intact')]);
      expect(restored.first.avatarUrl, isNull);
      expect(restored.first.replyCount, 0);
      expect(restored.first.likeCount, 0);
      expect(restored.first.images, ['https://example.org/one.jpg']);
      expect(restored.first.facets, isEmpty);
      expect(restored.first.quotedPost?.text, '');
      expect(restored.first.linkCard?.title, isNull);
    });

    test('cached quote nesting is bounded without losing the outer post', () {
      Map<String, Object?> raw = {'uri': _uri('last'), 'text': 'last'};
      for (var depth = 0; depth < 30; depth++) {
        raw = {'uri': _uri('q$depth'), 'text': 'q$depth', 'quotedPost': raw};
      }
      var post = BlueskyPost.fromSnapshot(raw);
      expect(post.text, 'q29');
      var quotes = 0;
      while (post.quotedPost != null) {
        quotes++;
        post = post.quotedPost!;
      }
      expect(quotes, 4);
    });

    test('profile pin reads its strong-reference URI safely', () {
      final profile = BlueskyProfile.fromJson({
        'did': _did,
        'handle': 'alice.test',
        'pinnedPost': {'uri': _uri('pin'), 'cid': 'pin-cid'},
      });
      expect(profile.pinnedPostUri, _uri('pin'));
      expect(BlueskyProfile.fromJson({'pinnedPost': true}).pinnedPostUri, isNull);
    });

    test('post links fall back to the stable DID when the handle is missing', () {
      final raw = _view('fallback')..['author'] = {'did': _did, 'handle': ''};
      expect(blueskyPostFromView(raw)?.url, 'https://bsky.app/profile/$_did/post/fallback');
      expect(blueskyWebUrl(handle: '', atUri: _uri('fallback')), 'https://bsky.app/profile/$_did/post/fallback');
      expect(blueskyWebUrl(handle: 'alice.test', atUri: 'at://$_did/app.bsky.graph.list/list'), isNull);
    });

    test('pinned generator metadata survives a flat preference round trip', () {
      const feed = BlueskyFeedGenerator(
        uri: 'at://did:plc:abc/app.bsky.feed.generator/art',
        displayName: 'Art',
        description: 'Artists',
        avatarUrl: 'https://example.org/art.jpg',
        creatorHandle: 'alice.test',
      );
      final restored = blueskyGeneratorsFromPrefs(blueskyGeneratorsToPrefs([feed])).single;
      expect(restored.toJson(), feed.toJson());
      final legacy = BlueskyFeedGenerator.fromSnapshot({
        'uri': feed.uri,
        'displayName': 'Art',
        'avatar': feed.avatarUrl,
        'creator': {'handle': feed.creatorHandle},
      });
      expect(legacy.avatarUrl, feed.avatarUrl);
      expect(legacy.creatorHandle, feed.creatorHandle);
    });

    test('pinned list counts and metadata survive a flat preference round trip', () {
      const list = BlueskyListInfo(
        uri: 'at://did:plc:abc/app.bsky.graph.list/art',
        name: 'Artists',
        description: 'Art',
        itemCount: 17,
        avatarUrl: 'https://example.org/art.jpg',
        creatorHandle: 'alice.test',
      );
      final restored = blueskyListsFromPrefs(blueskyListsToPrefs([list])).single;
      expect(restored.toJson(), list.toJson());
      final legacy = BlueskyListInfo.fromSnapshot({
        'uri': list.uri,
        'name': list.name,
        'listItemCount': 17,
        'avatar': list.avatarUrl,
        'creator': {'handle': list.creatorHandle},
      });
      expect(legacy.itemCount, 17);
      expect(legacy.avatarUrl, list.avatarUrl);
      expect(legacy.creatorHandle, list.creatorHandle);
    });
  });

  group('Public reader HTTP contracts', () {
    test('getPosts sends repeated URI parameters in deduplicated batches of at most 25', () async {
      final batches = <List<String>>[];
      final client = BlueskyClient(
        httpClient: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/xrpc/app.bsky.feed.getPosts');
          expect(request.headers.containsKey('authorization'), isFalse);
          final batch = request.url.queryParametersAll['uris']!;
          batches.add(batch);
          return _json({
            'posts': [for (final uri in batch) _view(uri.split('/').last)],
          });
        }),
      );
      final uris = List.generate(27, (i) => _uri('post$i'));
      final posts = await client.getPosts([...uris, uris.first, '', 'https://example.org/not-an-at-uri', uris.last]);
      expect(batches.map((batch) => batch.length), [25, 2]);
      expect(batches.expand((batch) => batch), uris);
      expect(posts.map((post) => post.uri), uris);
      await client.getPosts(const []);
      expect(batches, hasLength(2));
    });

    test('post search carries encoded author, tags, order and cursor parameters', () async {
      final requests = <Uri>[];
      final client = BlueskyClient(
        httpClient: MockClient((request) async {
          requests.add(request.url);
          return _json({
            'posts': [_view('result')],
            'cursor': 'next-page',
          });
        }),
      );
      final page = await client.searchPosts(
        'art & design',
        limit: 500,
        cursor: 'cursor+/=',
        sort: 'top',
        author: ' alice.test ',
        tags: ['#art', '  design  ', '#art', '', '#'],
      );
      final query = requests.single.queryParametersAll;
      expect(requests.single.path, '/xrpc/app.bsky.feed.searchPosts');
      expect(query['q'], ['art & design']);
      expect(query['limit'], ['100']);
      expect(query['cursor'], ['cursor+/=']);
      expect(query['sort'], ['top']);
      expect(query['author'], ['alice.test']);
      expect(query['tag'], ['art', 'design']);
      expect(page.cursor, 'next-page');
      await client.searchPosts('fallback', limit: -3, sort: 'unsupported', author: ' ');
      expect(requests.last.queryParameters['sort'], 'latest');
      expect(requests.last.queryParameters['limit'], '1');
      expect(requests.last.queryParameters.containsKey('author'), isFalse);
    });

    test('actor search carries cursor and reads the next public page', () async {
      final client = BlueskyClient(
        httpClient: MockClient((request) async {
          expect(request.method, 'GET');
          expect(request.url.path, '/xrpc/app.bsky.actor.searchActors');
          expect(request.url.queryParameters, {'q': 'blue sky', 'limit': '25', 'cursor': 'second/page='});
          return _json({
            'actors': [
              {'did': _did, 'handle': 'alice.test'},
            ],
            'cursor': 'third',
          });
        }),
      );
      final page = await client.searchActorsPage('blue sky', limit: 25, cursor: 'second/page=');
      expect(page.actors.single.did, _did);
      expect(page.cursor, 'third');
    });
  });
}
