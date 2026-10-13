import 'dart:convert';

import 'package:ffcache/ffcache.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/substack/substack_article_cache.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_links.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_rss.dart';
import 'package:xta/plugins/substack/substack_store.dart';

/// Pins the current behaviour behind each bug in docs/substack/audit.md. A fix
/// flips its probe on purpose, and the flipped probe becomes the regression test.
const _base = 'https://fieldnotes.substack.com';
const _fieldNotes = SubstackPublication(subdomain: 'fieldnotes', baseUrl: _base, name: 'Field Notes');

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

String _rss(String host, {String slug = 'hello'}) =>
    '''
<rss version="2.0"><channel><title>Feed of $host</title><generator>Substack</generator>
<item><title>Hello</title><link>https://$host/p/$slug</link>
<pubDate>Fri, 25 Sep 2026 10:00:00 GMT</pubDate><description>Hi</description></item>
</channel></rss>''';

Map<String, Object?> _replyChain(int deepest) {
  var node = <String, Object?>{'id': deepest, 'body': 'reply $deepest', 'children': <Object?>[]};
  for (var id = deepest - 1; id >= 0; id--) {
    node = {
      'id': id,
      'body': 'reply $id',
      'children': [node],
    };
  }
  return node;
}

class _Publications extends SubstackPublicationsStore {
  _Publications(List<SubstackPublication> pubs) : super(PrefServiceCache(cache: {})) {
    update(pubs);
  }
}

class _CountingClient extends SubstackClient {
  final calls = <String>[];

  @override
  Future<List<SubstackPost>> fetchPosts(SubstackPublication publication, {int limit = 12, int offset = 0}) async {
    calls.add(publication.baseUrl);
    return [
      for (var i = 0; i < limit; i++)
        SubstackPost(
          id: '${publication.id}-${offset + i}',
          title: 'Post $i',
          slug: 'post-$i',
          publicationBaseUrl: publication.baseUrl,
          publicationName: publication.name,
        ),
    ];
  }
}

class _MemoryCache implements FFCache {
  final entries = <String, dynamic>{};
  @override
  Future<bool> has(String key) async => entries.containsKey(key);
  @override
  Future<dynamic> getJSON(String key) async => entries[key];
  @override
  Future<void> setJSONWithTimeout(String key, dynamic value, Duration timeout) async => entries[key] = value;
  @override
  dynamic noSuchMethod(Invocation invocation) => super.noSuchMethod(invocation);
}

/// Shapes of Substack markup reconstructed from its editor's node names, not
/// recorded from a live response: the probes show what the sanitizer does with
/// such markup and stay unverified until a recorded body replaces them.
const _subscribeWidget =
    '<div class="subscription-widget-wrap-editor" data-component-name="SubscribeWidgetToDOM">'
    '<div class="subscription-widget show-subscribe"><div class="preamble"><p class="cta-caption">Thanks for reading!</p>'
    '</div><form class="subscription-widget-subscribe"><input type="email" class="email-input" name="email" '
    'placeholder="Type your email…"><input type="submit" class="button primary" value="Subscribe">'
    '</form></div></div>';

const _clientRenderedBlocks = {
  'latex':
      '<div class="latex-rendered" data-attrs="{&quot;persistentExpression&quot;:&quot;E=mc^2&quot;}" '
      'data-component-name="LatexBlockToDOM"></div>',
  'gallery':
      '<div class="image-gallery-embed" data-attrs="{&quot;gallery&quot;:{&quot;images&quot;:[{&quot;src&quot;:'
      '&quot;https://substack-post-media.s3.amazonaws.com/public/images/a.jpeg&quot;}]}}"></div>',
  'poll': '<div class="poll-embed" data-attrs="{&quot;id&quot;:123}" data-component-name="PollToDOM"></div>',
  'audio':
      '<div class="native-audio-embed" data-component-name="AudioPlaceholder" '
      'data-attrs="{&quot;mediaUploadId&quot;:&quot;abc&quot;,&quot;duration&quot;:93.5}"></div>',
};

void main() {
  test('BUG-01: replies nested deeper than nine levels are dropped', () {
    final comments = flattenSubstackComments({
      'comments': [_replyChain(11)],
    });

    expect(comments, hasLength(9), reason: 'twelve comments were sent');
    expect(comments.last.depth, 8);
  });

  test('BUG-02: one post read through RSS and through the API has two identities', () {
    final api = SubstackPost.fromJson(
      {'id': 4242, 'slug': 'hello', 'title': 'Hello', 'canonical_url': '$_base/p/hello'},
      publicationBaseUrl: _base,
      publicationName: 'Field Notes',
    );
    final rss = parseSubstackRss(
      _rss('fieldnotes.substack.com'),
      publicationBaseUrl: _base,
      publicationName: 'Field Notes',
    ).posts.single;

    expect(rss.canonicalUrl, api.canonicalUrl);
    expect(rss.id, isNot(api.id));
    expect(substackFeedPostKey(rss), isNot(substackFeedPostKey(api)));
  });

  test('BUG-03: a rate-limited publication costs twelve requests and reads as "not a publication"', () async {
    final requests = <Uri>[];
    final client = _client((request) async {
      requests.add(request.url);
      return http.Response('', 429, request: request);
    });

    await expectLater(client.fetchPosts(_fieldNotes, limit: 8), throwsA(isA<SubstackNotPublicationException>()));
    expect(requests, hasLength(12));
    expect(requests.map((uri) => uri.host).toSet(), {'fieldnotes.substack.com', 'www.fieldnotes.substack.com'});
  });

  test('BUG-04: an unreachable custom domain shows posts of an unrelated {label}.substack.com', () async {
    const followed = SubstackPublication(
      subdomain: 'examplecorp',
      baseUrl: 'https://newsletter.example.com',
      name: 'Example Weekly',
    );
    final client = _client((request) async {
      if (request.url.host == 'newsletter.substack.com' && request.url.path == '/api/v1/posts') {
        return _json([
          {'id': 1, 'slug': 'welcome', 'title': 'Somebody else'},
        ], request);
      }
      return http.Response('', 503, request: request);
    });

    final posts = await client.fetchPosts(followed, limit: 8);

    expect(posts.single.title, 'Somebody else');
    expect(posts.single.publicationName, 'Example Weekly');
    expect(posts.single.publicationBaseUrl, 'https://newsletter.example.com');
  });

  test('BUG-05: a refresh reads only 24 of 30 followed publications', () async {
    final pubs = [
      for (var i = 0; i < 30; i++)
        SubstackPublication(subdomain: 'p$i', baseUrl: 'https://p$i.substack.com', name: 'P$i'),
    ];
    final client = _CountingClient();
    final store = SubstackFeedStore(client, _Publications(pubs));
    addTearDown(store.destroy);

    await store.refresh(force: true);

    expect(client.calls.toSet(), hasLength(24));
    expect(store.pendingCount, 6);
  });

  test('BUG-06: two custom domains resolved from RSS share one subscription id', () async {
    final client = _client((request) async {
      if (request.url.path == '/feed') {
        return http.Response(_rss(request.url.host), 200, request: request);
      }
      return http.Response('', 404, request: request);
    });

    final alpha = await client.fetchPublication(Uri.parse('https://newsletter.alpha.example'));
    final beta = await client.fetchPublication(Uri.parse('https://newsletter.beta.example'));

    expect(alpha.baseUrl, isNot(beta.baseUrl));
    expect(alpha.id, beta.id);
  });

  test('BUG-07 (reconstructed markup): the subscribe widget survives as a live form', () {
    final clean = sanitizeSubstackBodyHtml('<p>Intro</p>$_subscribeWidget');

    expect(clean, contains('<form'));
    expect(clean, contains('type="email"'));
  });

  test('BUG-08 (reconstructed markup): client-rendered blocks vanish without a trace', () {
    for (final markup in _clientRenderedBlocks.values) {
      expect(sanitizeSubstackBodyHtml('<p>Before</p>$markup<p>After</p>'), '<p>Before</p><p>After</p>');
    }
  });

  test('BUG-09: the article cache forgets that a post is a video', () async {
    final cache = SubstackArticleCache(cache: _MemoryCache());
    const video = SubstackPost(
      id: '7',
      title: 'Clip',
      slug: 'clip',
      publicationBaseUrl: _base,
      publicationName: 'Field Notes',
      bodyHtml: '<p>Clip</p>',
      hasVideoUpload: true,
    );

    await cache.put(video);
    final cached = await cache.get(video.publication, 'clip');

    expect(video.isVideo, isTrue);
    expect(cached!.isVideo, isFalse);
  });

  test('BUG-10 (link shape unverified): an app share link is not read in-app', () {
    expect(parseSubstackPostLink('https://substack.com/home/post/p-147371717'), isNull);
  });
}
