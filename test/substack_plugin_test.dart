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

  test('resolveSubstackBase reads share links and @profiles', () {
    expect(
      resolveSubstackBase('https://open.substack.com/pub/platformer')?.host,
      'platformer.substack.com',
    );
    expect(
      resolveSubstackBase(
        'https://open.substack.com/pub/platformer/p/the-deal?utm=1',
      )?.host,
      'platformer.substack.com',
    );
    expect(
      resolveSubstackBase('https://substack.com/@platformer')?.host,
      'platformer.substack.com',
    );
    expect(resolveSubstackBase('@platformer')?.host, 'platformer.substack.com');
    expect(resolveSubstackBase('https://substack.com/'), isNull);
    expect(resolveSubstackBase('https://open.substack.com/'), isNull);
    expect(resolveSubstackBase('https://medium.com/@someone'), isNull);
  });

  test('resolveSubstackPostRef parses /p/slug and share URLs', () {
    final ref = resolveSubstackPostRef(
      'https://astralcodexten.substack.com/p/hello-world?utm=1',
    );
    expect(ref?.base.host, 'astralcodexten.substack.com');
    expect(ref?.slug, 'hello-world');
    expect(resolveSubstackPostRef('astralcodexten'), isNull);

    final share = resolveSubstackPostRef(
      'https://open.substack.com/pub/platformer/p/the-deal?utm_source=share',
    );
    expect(share?.base.host, 'platformer.substack.com');
    expect(share?.slug, 'the-deal');
  });

  test('publicationFromPostJson keeps the host that served the posts', () {
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
    expect(pub.baseUrl, 'https://astralcodexten.substack.com');
    expect(pub.description, 'commentary');
  });

  test('publicationFetchBases tries leftover custom domains then Substack', () {
    const pub = SubstackPublication(
      subdomain: 'platformer',
      baseUrl: 'https://www.platformer.news',
      name: 'Platformer',
    );
    expect(publicationFetchBases(pub).map((e) => e.host).toList(), [
      'www.platformer.news',
      'platformer.news',
      'platformer.substack.com',
    ]);
  });

  test('subdomainOf does not turn a custom domain into www', () {
    expect(
      subdomainOf(Uri.parse('https://www.garbageday.email')),
      'garbageday',
    );
    expect(subdomainOf(Uri.parse('https://garbageday.email')), 'garbageday');
    expect(
      subdomainOf(Uri.parse('https://garbageday.substack.com')),
      'garbageday',
    );
    expect(publicationNameLooksGeneric('www'), isTrue);
    expect(publicationNameLooksGeneric('Garbage Day'), isFalse);
  });

  test('substackHostCandidates adds the Substack twin for a custom domain', () {
    expect(
      substackHostCandidates(
        Uri.parse('https://www.garbageday.email'),
      ).map((e) => e.host).toList(),
      ['www.garbageday.email', 'garbageday.email', 'garbageday.substack.com'],
    );
  });

  test('publicationFromHomepageHtml reads the real title and avatar', () {
    const html = '''
      <html><head>
        <title>Home | Garbage Day</title>
        <meta property="og:site_name" content="Garbage Day">
        <meta property="og:image" content="https://example.com/logo.png">
      </head></html>
    ''';
    final pub = publicationFromHomepageHtml(
      html,
      Uri.parse('https://www.garbageday.email'),
    );
    expect(pub?.name, 'Garbage Day');
    expect(pub?.subdomain, 'garbageday');
    expect(pub?.logoUrl, 'https://example.com/logo.png');
  });

  test('publicationFromProfileJson reads primaryPublication', () {
    final pub = publicationFromProfileJson({
      'handle': 'platformer',
      'primaryPublication': {
        'name': 'Platformer',
        'subdomain': 'platformer',
        'custom_domain': 'www.platformer.news',
      },
    });
    expect(pub?.name, 'Platformer');
    expect(pub?.subdomain, 'platformer');
    expect(pub?.baseUrl, 'https://www.platformer.news');
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
    expect(html, contains('XtaTts.postMessage'));
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

  test('fetchPublication refuses a site that is not Substack', () async {
    final client = SubstackClient(
      httpClient: MockClient((request) async {
        return http.Response('Not found', 404);
      }),
    );

    expect(
      () => client.fetchPublication(Uri.parse('https://www.platformer.news')),
      throwsA(isA<SubstackNotPublicationException>()),
    );
  });

  test(
    'fetchPosts falls back to the Substack host after a leftover domain',
    () async {
      final client = SubstackClient(
        httpClient: MockClient((request) async {
          if (request.url.host.endsWith('platformer.news')) {
            return http.Response('Not found', 404);
          }
          expect(request.url.host, 'platformer.substack.com');
          expect(request.url.path, '/api/v1/posts');
          return http.Response(
            jsonEncode([
              {
                'id': 1,
                'title': 'Still here',
                'slug': 'still-here',
                'publishedBylines': [
                  {
                    'publicationUsers': [
                      {
                        'publication': {
                          'name': 'Platformer',
                          'subdomain': 'platformer',
                          'custom_domain': 'www.platformer.news',
                        },
                      },
                    ],
                  },
                ],
              },
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final posts = await client.fetchPosts(
        const SubstackPublication(
          subdomain: 'platformer',
          baseUrl: 'https://www.platformer.news',
          name: 'Platformer',
        ),
      );

      expect(posts, hasLength(1));
      expect(posts.first.slug, 'still-here');
      expect(posts.first.publicationBaseUrl, 'https://www.platformer.news');
      expect(posts.first.publicationName, 'Platformer');
    },
  );

  test(
    'a follow saved as www on a custom domain still loads the Substack twin',
    () async {
      final client = SubstackClient(
        httpClient: MockClient((request) async {
          if (request.url.host.contains('garbageday.email')) {
            return http.Response('Not found', 404);
          }
          expect(request.url.host, 'garbageday.substack.com');
          if (request.url.path == '/api/v1/posts') {
            return http.Response(
              jsonEncode([
                {
                  'id': 9,
                  'title': 'How The MrBeast Undisclosed Ad Thing Works',
                  'slug': 'how-the-mrbeast-undisclosed-ad-thing',
                  'publishedBylines': [
                    {
                      'publicationUsers': [
                        {
                          'publication': {
                            'name': 'Garbage Day',
                            'subdomain': 'garbageday',
                            'hero_text':
                                'A newsletter about having fun online.',
                            'logo_url': 'https://example.com/gd.png',
                          },
                        },
                      ],
                    },
                  ],
                },
              ]),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          return http.Response('Not found', 404);
        }),
      );

      const stub = SubstackPublication(
        subdomain: 'www',
        baseUrl: 'https://www.garbageday.email',
        name: 'www',
      );

      final pub = await client.fetchPublication(Uri.parse(stub.baseUrl));
      expect(pub.name, 'Garbage Day');
      expect(pub.subdomain, 'garbageday');
      expect(pub.baseUrl, 'https://www.garbageday.email');
      expect(pub.logoUrl, 'https://example.com/gd.png');

      final posts = await client.fetchPosts(stub);
      expect(posts, isNotEmpty);
      expect(posts.first.title, contains('MrBeast'));
      expect(posts.first.publicationName, 'Garbage Day');
    },
  );

  test('fetchPosts uses /api/v1/archive when /posts is gone', () async {
    final client = SubstackClient(
      httpClient: MockClient((request) async {
        if (request.url.path == '/api/v1/posts') {
          return http.Response('Not found', 404);
        }
        expect(request.url.path, '/api/v1/archive');
        return http.Response(
          jsonEncode([
            {'id': 2, 'title': 'From archive', 'slug': 'from-archive'},
          ]),
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
    expect(posts.single.title, 'From archive');
  });

  test('resolvePublication follows open.substack.com share links', () async {
    final client = SubstackClient(
      httpClient: MockClient((request) async {
        expect(request.url.host, 'platformer.substack.com');
        expect(request.url.path, '/api/v1/posts');
        return http.Response(
          jsonEncode([
            {
              'id': 1,
              'title': 'Hello',
              'slug': 'hello',
              'publishedBylines': [
                {
                  'publicationUsers': [
                    {
                      'publication': {
                        'name': 'Platformer',
                        'subdomain': 'platformer',
                      },
                    },
                  ],
                },
              ],
            },
          ]),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );

    final pub = await client.resolvePublication(
      'https://open.substack.com/pub/platformer/p/hello',
    );
    expect(pub.subdomain, 'platformer');
    expect(pub.baseUrl, 'https://platformer.substack.com');
    expect(pub.name, 'Platformer');
  });

  test(
    'resolvePublication maps a leftover custom domain to its Substack twin',
    () async {
      final client = SubstackClient(
        httpClient: MockClient((request) async {
          if (request.url.path.contains('/user/platformer/public_profile')) {
            return http.Response(
              jsonEncode({
                'handle': 'platformer',
                'primaryPublication': {
                  'name': 'Platformer',
                  'subdomain': 'platformer',
                  'custom_domain': 'www.platformer.news',
                },
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          if (request.url.host.contains('platformer.news')) {
            return http.Response('Gone', 404);
          }
          expect(request.url.host, 'platformer.substack.com');
          return http.Response(
            jsonEncode([
              {
                'id': 1,
                'title': 'Archive',
                'slug': 'archive',
                'publishedBylines': [
                  {
                    'publicationUsers': [
                      {
                        'publication': {
                          'name': 'Platformer',
                          'subdomain': 'platformer',
                          'custom_domain': 'www.platformer.news',
                        },
                      },
                    ],
                  },
                ],
              },
            ]),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final pub = await client.resolvePublication(
        'https://www.platformer.news',
      );
      expect(pub.subdomain, 'platformer');
      expect(pub.name, 'Platformer');
      expect(pub.baseUrl, 'https://www.platformer.news');
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
