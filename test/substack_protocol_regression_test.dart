import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';

const _publication = SubstackPublication(
  subdomain: 'fieldnotes',
  baseUrl: 'https://www.fieldnotes.example',
  name: 'Field Notes',
);

Map<String, Object?> _article(String id, [Map<String, Object?> extra = const {}]) => {
  'id': id,
  'slug': 'article-$id',
  'title': 'Article $id',
  'body_html': '<p>Body $id</p>',
  ...extra,
};

http.Response _json(Object? value, http.Request request) => http.Response(
  jsonEncode(value),
  200,
  request: request,
  headers: {'content-type': 'application/json; charset=utf-8'},
);

SubstackClient _client(Future<http.Response> Function(http.Request request) respond) {
  final client = SubstackClient(httpClient: MockClient(respond));
  addTearDown(client.httpClient.close);
  return client;
}

String _rss(String host, {int count = 6}) =>
    '''
<rss version="2.0" xmlns:content="http://purl.org/rss/1.0/modules/content/"
 xmlns:dc="http://purl.org/dc/elements/1.1/"><channel>
<title>Field Notes RSS</title><generator>Substack</generator>
<link>https://$host</link>
${List.generate(count, (index) => '''<item><title>RSS $index</title>
<link>https://$host/p/rss-$index</link><dc:creator>Jordan</dc:creator>
<pubDate>Fri, 25 Sep 2026 10:00:00 GMT</pubDate>
<content:encoded><![CDATA[<p>Body $index</p>]]></content:encoded>
<enclosure url="https://media.example/episode-$index.mp3" type="audio/mpeg" />
</item>''').join()}
</channel></rss>''';

void main() {
  test('publication search preserves query, offset, limit and followed source base', () async {
    final requests = <Uri>[];
    final client = _client((request) async {
      requests.add(request.url);
      return _json([
        _article('one', {'canonical_url': 'https://fieldnotes.substack.com/p/article-one'}),
      ], request);
    });
    const query = 'climate & café / "notes"';
    final posts = await client.searchPosts(_publication, query, limit: 7, offset: 21);
    expect(requests.single.host, 'www.fieldnotes.example');
    expect(requests.single.path, '/api/v1/archive');
    expect(requests.single.queryParameters, {'sort': 'new', 'search': query, 'limit': '7', 'offset': '21'});
    expect(posts.single.publicationBaseUrl, _publication.baseUrl);
    expect(posts.single.publicationName, _publication.name);
    expect(posts.single.canonicalUrl, 'https://fieldnotes.substack.com/p/article-one');
    expect(posts.single.bodyHtml, isNull);
  });

  test('search bounds its page size and normalizes negative offsets', () async {
    final requests = <Uri>[];
    final client = _client((request) async {
      requests.add(request.url);
      return _json([], request);
    });
    await client.searchPosts(_publication, 'one', limit: 10000, offset: -5);
    await client.searchPosts(_publication, 'two', limit: 0, offset: 30);
    expect(requests[0].queryParameters['limit'], '100');
    expect(requests[0].queryParameters['offset'], '0');
    expect(requests[1].queryParameters['limit'], '1');
    expect(requests[1].queryParameters['offset'], '30');
  });

  test('a valid empty search result stops fallback while all-host failures remain errors', () async {
    final hosts = <String>[];
    final empty = _client((request) async {
      hosts.add(request.url.host);
      return _json([], request);
    });
    expect(await empty.searchPosts(_publication, 'none'), isEmpty);
    expect(hosts, [_publication.baseUrl.replaceFirst('https://', '')]);
    final failed = _client((request) async => http.Response('offline', 503, request: request));
    await expectLater(failed.searchPosts(_publication, 'none'), throwsA(isA<SubstackClientException>()));
  });

  test('malformed 200 search shapes retry hosts instead of reporting no matches', () async {
    final visited = <String>[];
    final fallback = _client((request) async {
      visited.add(request.url.host);
      return _json(
        request.url.host == 'fieldnotes.example' ? [_article('fallback')] : {'error': 'unavailable'},
        request,
      );
    });
    expect((await fallback.searchPosts(_publication, 'topic')).single.id, 'fallback');
    expect(visited, ['www.fieldnotes.example', 'fieldnotes.example']);
    final malformed = _client((request) async => _json({'posts': []}, request));
    await expectLater(malformed.searchPosts(_publication, 'topic'), throwsA(isA<SubstackClientException>()));
    final brokenJson = _client((request) async => http.Response('{broken', 200, request: request));
    await expectLater(brokenJson.searchPosts(_publication, 'topic'), throwsA(isA<FormatException>()));
  });

  test('pagination preserves a valid empty page and rejects inaccessible or malformed archives', () async {
    final empty = _client((request) async => _json([], request));
    expect(await empty.fetchPosts(_publication, offset: 12), isEmpty);
    final failed = _client((request) async => http.Response('no service', 503, request: request));
    await expectLater(failed.fetchPosts(_publication, offset: 12), throwsA(isA<SubstackClientException>()));
    final malformed = _client((request) async => _json({'error': true}, request));
    await expectLater(malformed.fetchPosts(_publication, offset: 12), throwsA(isA<SubstackClientException>()));
  });

  test('RSS fallback pages locally without repeating requests and retains post metadata', () async {
    final requests = <Uri>[];
    final client = _client((request) async {
      requests.add(request.url);
      return request.url.host == 'www.fieldnotes.example' && request.url.path == '/feed'
          ? http.Response(_rss(request.url.host), 200, request: request)
          : http.Response('missing', 404, request: request);
    });
    final first = await client.fetchPosts(_publication, limit: 2);
    expect(first.map((post) => post.slug), ['rss-0', 'rss-1']);
    final requestCount = requests.length;
    final second = await client.fetchPosts(_publication, limit: 2, offset: 2);
    expect(second.map((post) => post.slug), ['rss-2', 'rss-3']);
    expect(requests, hasLength(requestCount));
    expect(second.first.publicationBaseUrl, _publication.baseUrl);
    expect(second.first.publicationName, 'Field Notes RSS');
    expect(second.first.authorName, 'Jordan');
    expect(second.first.publishedAt, isNotNull);
    expect(second.first.audioUrl, 'https://media.example/episode-2.mp3');
    expect(second.first.type, 'podcast');
    expect(second.first.bodyHtml, isNull, reason: 'Cached archive pages retain metadata, not complete article bodies.');
    expect(await client.fetchPosts(_publication, limit: 2, offset: 6), isEmpty);
  });

  test('RSS paging cache is isolated by source even when publication IDs match', () async {
    final client = _client(
      (request) async => request.url.host == 'www.fieldnotes.example' && request.url.path == '/feed'
          ? http.Response(_rss(request.url.host), 200, request: request)
          : http.Response('missing', 404, request: request),
    );
    await client.fetchPosts(_publication, limit: 2);
    const changed = SubstackPublication(subdomain: 'fieldnotes', baseUrl: 'https://changed.example', name: 'Changed');
    await expectLater(client.fetchPosts(changed, offset: 2), throwsA(isA<SubstackClientException>()));
  });

  test('a successful JSON refresh replaces the previous RSS paging cache', () async {
    var jsonAvailable = false;
    final requests = <Uri>[];
    final client = _client((request) async {
      requests.add(request.url);
      if (jsonAvailable && request.url.path == '/api/v1/posts') {
        return _json([_article('json-${request.url.queryParameters['offset']}')], request);
      }
      return request.url.host == 'www.fieldnotes.example' && request.url.path == '/feed'
          ? http.Response(_rss(request.url.host), 200, request: request)
          : http.Response('missing', 404, request: request);
    });
    await client.fetchPosts(_publication, limit: 2);
    jsonAvailable = true;
    expect((await client.fetchPosts(_publication, limit: 2)).single.id, 'json-0');
    final before = requests.length;
    expect((await client.fetchPosts(_publication, limit: 2, offset: 2)).single.id, 'json-2');
    expect(requests.length, greaterThan(before));
  });

  test('a valid empty JSON refresh cannot leave stale RSS posts available for pagination', () async {
    var emptied = false;
    final client = _client((request) async {
      if (emptied && request.url.path.startsWith('/api/v1/')) return _json([], request);
      return !emptied && request.url.host == 'www.fieldnotes.example' && request.url.path == '/feed'
          ? http.Response(_rss(request.url.host), 200, request: request)
          : http.Response('missing', 404, request: request);
    });
    expect(await client.fetchPosts(_publication, limit: 2), hasLength(2));
    emptied = true;
    expect(await client.fetchPosts(_publication, limit: 2), isEmpty);
    expect(await client.fetchPosts(_publication, limit: 2, offset: 2), isEmpty);
  });

  test('a failed refresh preserves previously downloaded RSS pages for offline reading', () async {
    var offline = false;
    final client = _client(
      (request) async => !offline && request.url.host == 'www.fieldnotes.example' && request.url.path == '/feed'
          ? http.Response(_rss(request.url.host), 200, request: request)
          : http.Response('unavailable', 503, request: request),
    );
    await client.fetchPosts(_publication, limit: 2);
    offline = true;
    await expectLater(client.fetchPosts(_publication, limit: 2), throwsA(isA<SubstackClientException>()));
    expect((await client.fetchPosts(_publication, limit: 2, offset: 2)).map((post) => post.slug), ['rss-2', 'rss-3']);
  });

  test('bad optional article scalars do not remove valid search siblings', () async {
    final client = _client(
      (request) async => _json([
        false,
        null,
        _article('one', {
          'subtitle': 7,
          'description': false,
          'post_date': [],
          'canonical_url': 4,
          'cover_image': {},
          'body_html': true,
          'audience': [],
          'type': 13,
          'publishedBylines': [
            {'name': false},
          ],
          'podcast_url': 10,
          'reaction_count': 'many',
          'comment_count': -4,
        }),
        _article('two'),
        {'id': [], 'title': false, 'slug': 9},
      ], request),
    );
    final posts = await client.searchPosts(_publication, 'topic');
    expect(posts.map((post) => post.id), ['one', 'two']);
    expect(posts.first.subtitle, isNull);
    expect(posts.first.authorName, isNull);
    expect(posts.first.reactionCount, isNull);
    expect(posts.first.commentCount, 0);
  });

  test('bad optional byline publication metadata cannot discard an entire archive page', () async {
    final client = _client(
      (request) async => request.url.path == '/api/v1/posts'
          ? _json([
              _article('one', {
                'publishedBylines': [
                  {
                    'name': 3,
                    'publicationUsers': [
                      {
                        'publication': {'name': 42, 'subdomain': false, 'hero_text': [], 'logo_url': {}},
                      },
                    ],
                  },
                ],
              }),
              _article('two'),
            ], request)
          : http.Response('missing', 404, request: request),
    );
    final posts = await client.fetchPosts(_publication);
    expect(posts.map((post) => post.id), ['one', 'two']);
    expect(posts.every((post) => post.publicationBaseUrl == _publication.baseUrl), isTrue);
  });

  test('snapshot lists retain valid rows around wrongly typed optional fields', () {
    final good = SubstackPost.fromJson(
      _article('one'),
      publicationBaseUrl: _publication.baseUrl,
      publicationName: _publication.name,
    ).toJson();
    final posts = SubstackPost.listFromPrefs(
      jsonEncode([
        null,
        {...good, 'subtitle': 4, 'authorName': [], 'reaction_count': {}, 'comment_count': -2},
        {...good, 'id': 'two', 'slug': 'article-two'},
      ]),
    );
    expect(posts.map((post) => post.id), ['one', 'two']);
    expect(posts.first.subtitle, isNull);
    expect(posts.first.commentCount, 0);
    final publications = SubstackPublication.listFromPrefs(
      jsonEncode([
        {'subdomain': 3, 'baseUrl': false},
        {..._publication.toJson(), 'description': {}, 'logoUrl': 3},
        null,
      ]),
    );
    expect(publications.single.id, _publication.id);
    expect(publications.single.description, isNull);
  });

  test('Notes tolerate missing author, malformed counts and unsupported bodies without losing siblings', () async {
    final client = _client(
      (request) async => _json({
        'items': [
          {
            'comment': {
              'id': 1,
              'body': 'Readable without author',
              'name': false,
              'handle': [],
              'date': 4,
              'reaction_count': 'many',
              'attachments': [
                false,
                {'type': 'image', 'imageUrl': 4},
              ],
            },
            'publication': {'subdomain': false, 'custom_domain': {}, 'name': 4},
          },
          {
            'comment': {
              'id': 2,
              'body': ['unsupported'],
            },
          },
          {
            'comment': {'id': 3, 'body': 'A second valid note', 'handle': 'jordan', 'reaction_count': -2},
          },
          {
            'comment': {'id': {}, 'body': 'Invalid identity'},
          },
          null,
        ],
        'nextCursor': 4,
      }, request),
    );
    final page = await client.fetchReaderNotes();
    expect(page.notes.map((note) => note.id), ['1', '3']);
    expect(page.notes.first.authorName, isNull);
    expect(page.notes.first.authorHandle, isNull);
    expect(page.notes.first.url, isNull);
    expect(page.notes.first.reactionCount, isNull);
    expect(page.notes.first.publication, isNull);
    expect(page.notes.last.reactionCount, 0);
    expect(page.notes.last.url, 'https://substack.com/@jordan/note/c-3');
    expect(page.nextCursor, isNull);
    expect(SubstackNote.fromReaderItem({'comment': null, 'entity_key': false}).id, isEmpty);
  });

  test('non-finite counters remain safe and negative counters never display as negative', () {
    final post = SubstackPost.fromJson(
      _article('one', {
        'reaction_count': double.infinity,
        'reactions': {'heart': 3, 'bad': double.nan, 'negative': -8},
        'comment_count': double.nan,
      }),
      publicationBaseUrl: _publication.baseUrl,
      publicationName: _publication.name,
    );
    expect(post.reactionCount, 3);
    expect(post.commentCount, isNull);
    final note = SubstackNote.fromReaderItem({
      'comment': {'id': 1, 'body': 'Text', 'reaction_count': double.infinity},
    });
    expect(note.reactionCount, isNull);
  });

  test('comments retain parent IDs and malformed/deleted parents preserve readable descendants', () {
    final parsed = flattenSubstackComments({
      'comments': [
        {
          'id': 1,
          'body': 'Root',
          'children': [
            {
              'id': 2,
              'body': false,
              'children': [
                {'id': 3, 'body': 'Under root', 'name': []},
              ],
            },
          ],
        },
        {'id': 4, 'body': 'Flat reply', 'parent_id': 1},
        {
          'id': 5,
          'body': 'Other flat reply',
          'parent': {'id': '4'},
        },
        {'id': [], 'body': 'Bad identity'},
      ],
    });
    expect(parsed.map((comment) => comment.id), ['1', '3', '4', '5']);
    expect(parsed.map((comment) => comment.parentId), [null, '1', '1', '4']);
    expect(parsed[1].depth, 1);
    expect(parsed[1].author, isNull);
  });

  test('comment cycles, repeated identities and very deep deleted chains terminate safely', () {
    final cyclic = <String, Object?>{'id': 'root', 'body': 'Root'};
    cyclic['children'] = [
      cyclic,
      {'id': 'child', 'body': 'Child'},
    ];
    expect(flattenSubstackComments([cyclic, cyclic]).map((comment) => comment.id), ['root', 'child']);
    final duplicate = flattenSubstackComments([
      {'id': 'one', 'body': 'First'},
      {
        'id': 'one',
        'body': 'Duplicate',
        'children': [
          {'id': 'two', 'body': 'Still readable'},
        ],
      },
    ]);
    expect(duplicate.map((comment) => comment.id), ['one', 'two']);
    Map<String, Object?> visible = {'id': 10, 'body': 'Deep'};
    for (var index = 9; index >= 0; index--) {
      visible = {
        'id': index,
        'body': 'Level',
        'children': [visible],
      };
    }
    expect(flattenSubstackComments([visible], maxDepth: 2).map((comment) => comment.depth), [0, 1, 2]);
    Map<String, Object?> deleted = {'id': 'leaf', 'body': 'Leaf'};
    for (var index = 0; index < 21000; index++) {
      deleted = {
        'children': [deleted],
      };
    }
    expect(flattenSubstackComments([deleted]), isEmpty);
  });

  test('comments distinguish a readable empty discussion from exhausted transport failures', () async {
    final empty = _client((request) async => _json({'comments': []}, request));
    expect(await empty.fetchComments(_publication, 'one'), isEmpty);
    final failed = _client((request) async => http.Response('offline', 503, request: request));
    await expectLater(failed.fetchComments(_publication, 'one'), throwsA(isA<SubstackClientException>()));
  });
}
