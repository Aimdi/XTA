import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/plugin_activity.dart';

const _uri = 'at://did:plc:author/app.bsky.feed.post/one';
const _mastodon = MastodonPost(
  id: '10',
  acct: 'alice@example.social',
  authorName: 'Alice',
  text: 'Post',
  url: 'https://example.social/@alice/10',
);

void main() {
  test('Bluesky reads repost actors and quote posts with opaque paging cursors', () async {
    final calls = <http.Request>[];
    final client = BlueskyClient(
      httpClient: MockClient((request) async {
        calls.add(request);
        expect(request.method, 'GET');
        expect(request.url.queryParameters['uri'], _uri);
        if (request.url.path.endsWith('getRepostedBy')) {
          return http.Response(
            jsonEncode({
              'repostedBy': [
                {'did': 'did:plc:b', 'handle': 'bob.test', 'displayName': 'Bob'},
                {},
              ],
              'cursor': 'opaque-next',
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'posts': [
              {
                'uri': 'at://did:plc:b/app.bsky.feed.post/quote',
                'cid': 'cid',
                'author': {'did': 'did:plc:b', 'handle': 'bob.test'},
                'record': {'text': 'Thoughts', 'createdAt': '2026-09-09T12:00:00Z'},
              },
            ],
          }),
          200,
        );
      }),
    );
    final people = await client.getRepostedBy(_uri);
    expect(people.items.single.did, 'did:plc:b');
    expect(people.cursor, 'opaque-next');
    final quotes = await client.getQuotes(_uri, cursor: people.cursor);
    expect(quotes.items.single.text, 'Thoughts');
    expect(calls.last.url.queryParameters['cursor'], 'opaque-next');
    expect(quotes.cursor, isNull);
  });

  test('Bluesky retains stable repost identity when a handle is absent', () {
    final post = blueskyPostFromFeedItem({
      'post': {
        'uri': _uri,
        'author': {'did': 'did:plc:author', 'handle': 'alice.test'},
        'record': {'text': 'Hello'},
      },
      'reason': {
        r'$type': 'app.bsky.feed.defs#reasonRepost',
        'by': {'did': 'did:plc:b', 'displayName': 'Bob'},
      },
    })!;
    expect(post.isRepost, isTrue);
    expect(post.reposterActor, 'did:plc:b');
    expect(BlueskyPost.fromSnapshot(post.toJson()).reposterActor, 'did:plc:b');
  });

  test('Mastodon uses Link cursor rather than the last account ID', () async {
    final calls = <Uri>[];
    final client = MastodonClient(
      httpClient: MockClient((request) async {
        calls.add(request.url);
        if (request.url.path == '/api/v1/statuses/10') {
          return http.Response(
            jsonEncode({
              'id': '10',
              'url': _mastodon.url,
              'content': '<p>Post</p>',
              'account': {'id': '1', 'acct': 'alice'},
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode([
            {'id': '999', 'acct': 'bob@elsewhere.test', 'display_name': 'Bob'},
          ]),
          200,
          headers: {
            'link': '<https://example.social/api/v1/statuses/10/reblogged_by?max_id=boost-edge-123>; rel="next"',
          },
        );
      }),
    );
    final first = await client.getRepostedBy(['https://example.social'], _mastodon);
    expect(first.items.single.acct, 'bob@elsewhere.test');
    await client.getRepostedBy(['https://example.social'], _mastodon, cursor: first.cursor);
    expect(calls.last.queryParameters['max_id'], 'boost-edge-123');
    expect(calls.where((uri) => uri.path == '/api/v1/statuses/10').length, 1);
  });

  test('Mastodon unauthorized quote lists stay errors rather than empty results', () async {
    final client = MastodonClient(
      httpClient: MockClient((request) async {
        if (request.url.path.endsWith('/quotes')) return http.Response('{}', 401);
        return http.Response(
          jsonEncode({
            'id': '10',
            'url': _mastodon.url,
            'content': '<p>Post</p>',
            'account': {'id': '1', 'acct': 'alice'},
          }),
          200,
        );
      }),
    );
    await expectLater(
      client.getQuotes(['https://example.social'], _mastodon),
      throwsA(isA<MastodonException>().having((error) => error.kind, 'kind', MastodonErrorKind.unauthorized)),
    );
  });

  test('Activity pagination rejects another server and endpoint', () {
    final current = Uri.parse('https://example.social/api/v1/statuses/10/reblogged_by');
    expect(
      mastodonActivityNextPage('<https://other.test/api/v1/statuses/10/reblogged_by>; rel="next"', current),
      isNull,
    );
    expect(mastodonActivityNextPage('<https://example.social/api/v1/accounts>; rel="next"', current), isNull);
  });

  test('Activity store keeps prior pages on failure, retries cursor and deduplicates', () async {
    var fail = true;
    final cursors = <String?>[];
    final store = PluginActivityStore<String>((cursor) async {
      cursors.add(cursor);
      if (cursor == null) return const PluginActivityPage(['a'], cursor: 'page2');
      if (fail) throw StateError('offline');
      return const PluginActivityPage(['a', 'b']);
    }, (value) => value);
    await store.load();
    await store.load(more: true);
    expect(store.state.items, ['a']);
    expect(store.state.error, isNotNull);
    fail = false;
    await store.load(more: true);
    expect(cursors, [null, 'page2', 'page2']);
    expect(store.state.items, ['a', 'b']);
    expect(store.state.cursor, isNull);
    await store.destroy();
  });
}
