import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_models.dart';

void main() {
  test('resolveSubstackBase accepts handle and URL', () {
    expect(
      resolveSubstackBase('astralcodexten')?.host,
      'astralcodexten.substack.com',
    );
    expect(
      resolveSubstackBase('https://astralcodexten.substack.com/p/x')?.origin,
      'https://astralcodexten.substack.com',
    );
    expect(
      resolveSubstackBase('www.astralcodexten.com')?.host,
      'www.astralcodexten.com',
    );
    expect(resolveSubstackBase(''), isNull);
  });

  test('resolveSubstackPostRef parses /p/slug URLs', () {
    final ref = resolveSubstackPostRef(
      'https://astralcodexten.substack.com/p/hello-world?utm=1',
    );
    expect(ref?.base.host, 'astralcodexten.substack.com');
    expect(ref?.slug, 'hello-world');
    expect(resolveSubstackPostRef('astralcodexten'), isNull);
  });

  test('publicationFromPostJson reads nested publication metadata', () {
    final pub = publicationFromPostJson({
      'publishedBylines': [
        {
          'publicationUsers': [
            {
              'publication': {
                'name': 'Astral Codex Ten',
                'subdomain': 'astralcodexten',
                'custom_domain': 'www.astralcodexten.com',
                'hero_text': 'commentary',
                'logo_url': 'https://example.com/logo.png',
              },
            },
          ],
        },
      ],
    }, fallbackBase: Uri.parse('https://astralcodexten.substack.com'));

    expect(pub.name, 'Astral Codex Ten');
    expect(pub.subdomain, 'astralcodexten');
    expect(pub.baseUrl, 'https://www.astralcodexten.com');
    expect(pub.description, 'commentary');
  });

  test('isPaywalled recognizes live only_paid audience', () {
    const paid = SubstackPost(
      id: '1',
      title: 'Paid',
      slug: 'paid',
      audience: 'only_paid',
      publicationBaseUrl: 'https://example.substack.com',
      publicationName: 'Example',
    );
    const free = SubstackPost(
      id: '2',
      title: 'Free',
      slug: 'free',
      audience: 'everyone',
      publicationBaseUrl: 'https://example.substack.com',
      publicationName: 'Example',
    );
    expect(paid.isPaywalled, isTrue);
    expect(free.isPaywalled, isFalse);
  });

  test('SubstackClient.fetchPosts parses public JSON without body', () async {
    final client = SubstackClient(
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/posts');
        return http.Response(
          '''
          [{
            "id": 1,
            "title": "Hello",
            "slug": "hello",
            "subtitle": "world",
            "description": "longer excerpt",
            "post_date": "2026-07-01T00:00:00.000Z",
            "canonical_url": "https://example.substack.com/p/hello",
            "audience": "everyone",
            "body_html": "<p>secret</p>",
            "publishedBylines": [{"name": "Author"}]
          }]
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final posts = await client.fetchPosts(
      const SubstackPublication(
        subdomain: 'example',
        baseUrl: 'https://example.substack.com',
        name: 'Example',
      ),
    );

    expect(posts, hasLength(1));
    expect(posts.first.title, 'Hello');
    expect(posts.first.authorName, 'Author');
    expect(posts.first.isPaywalled, isFalse);
    expect(posts.first.bodyHtml, isNull);
    expect(posts.first.excerpt, 'world');
  });

  test('SubstackClient.fetchPost loads full body and paywall flag', () async {
    final client = SubstackClient(
      httpClient: MockClient((request) async {
        expect(request.url.path, '/api/v1/posts/hello');
        return http.Response(
          '''
          {
            "id": 1,
            "title": "Hello",
            "slug": "hello",
            "audience": "only_paid",
            "body_html": "",
            "canonical_url": "https://example.substack.com/p/hello"
          }
          ''',
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final post = await client.fetchPost(
      const SubstackPublication(
        subdomain: 'example',
        baseUrl: 'https://example.substack.com',
        name: 'Example',
      ),
      'hello',
    );

    expect(post.isPaywalled, isTrue);
  });

  test('wrapSubstackHtml is theme-aware and sanitizes chrome', () {
    final html = wrapSubstackHtml(
      title: 'Hello <World>',
      body: '<p>Body</p><button>x</button><svg></svg>',
      background: '#000000',
      foreground: '#FFFFFF',
      muted: '#AAAAAA',
      link: '#1D9BF0',
      isDark: true,
      subtitle: 'Sub',
      publicationName: 'Pub',
    );
    expect(html, contains('color-scheme: dark'));
    expect(html, contains('#000000'));
    expect(html, contains('Hello &lt;World&gt;'));
    expect(html, contains('<p>Body</p>'));
    expect(html, isNot(contains('<button')));
    expect(html, contains('font-family: Georgia'));
  });

  test('sanitize and plain text keep readable content', () {
    const raw = '''
      <p>Hello <strong>world</strong>.</p>
      <button>Follow</button>
      <p>Second paragraph</p>
    ''';
    final clean = sanitizeSubstackBodyHtml(raw);
    expect(clean, contains('Hello'));
    expect(clean, isNot(contains('<button')));
    final text = substackHtmlToPlainText(raw);
    expect(text, contains('Hello world.'));
    expect(text, contains('Second paragraph'));
    expect(buildSubstackSpeakText(title: 'T', bodyHtml: raw), contains('T'));
  });

  test(
    'SubstackClient.fetchSimilarPublications prefers recs then search',
    () async {
      final recHtml =
          '<html><script>window._preloads = JSON.parse(${jsonEncode(jsonEncode({
            'recommendations': [
              {
                'description': 'Casey',
                'recommendedPublication': {'name': 'Big Technology', 'subdomain': 'bigtechnology'},
              },
            ],
          }))});</script></html>';

      final client = SubstackClient(
        httpClient: MockClient((request) async {
          if (request.url.path == '/recommendations') {
            return http.Response(
              recHtml,
              200,
              headers: {'content-type': 'text/html'},
            );
          }
          expect(request.url.path, '/api/v1/publication/search');
          expect(request.url.queryParameters['query'], 'Platformer');
          return http.Response(
            jsonEncode({
              'results': [
                {'name': 'Platformer', 'subdomain': 'platformer'},
                {'name': 'User Mag', 'subdomain': 'usermag'},
              ],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final similar = await client.fetchSimilarPublications(
        const SubstackPublication(
          subdomain: 'platformer',
          baseUrl: 'https://platformer.substack.com',
          name: 'Platformer',
        ),
      );

      expect(similar.map((e) => e.publication.id), [
        'bigtechnology',
        'usermag',
      ]);
      expect(similar.first.blurb, 'Casey');
    },
  );

  test('SubstackPublication prefs round-trip', () {
    const pubs = [
      SubstackPublication(
        subdomain: 'a',
        baseUrl: 'https://a.substack.com',
        name: 'A',
      ),
    ];
    final raw = SubstackPublication.listToPrefs(pubs);
    final back = SubstackPublication.listFromPrefs(raw);
    expect(back.single.name, 'A');
    expect(back.single.id, 'a');
  });
}
