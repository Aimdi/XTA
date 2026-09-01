import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_html.dart';

http.Response _json(Object body, int status) => http.Response(
  jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);

Map<String, dynamic> _tokenBody({int expiresIn = 3600}) => {
  'access_token': 'tok_123',
  'token_type': 'bearer',
  'expires_in': expiresIn,
  'scope': '*',
};

Map<String, dynamic> _listingBody({
  String? after,
  List<Map<String, dynamic>>? children,
}) => {
  'kind': 'Listing',
  'data': {
    'after': after,
    'children':
        children ??
        [
          {
            'kind': 't3',
            'data': {
              'id': 'abc123',
              'title': 'Dart 4 is out',
              'subreddit': 'dartlang',
              'author': 'someone',
              'score': 412,
              'num_comments': 37,
              'created_utc': 1769000000,
              'permalink': '/r/dartlang/comments/abc123/dart_4_is_out/',
              'url': 'https://dart.dev/blog',
              'is_self': false,
              'over_18': false,
              'stickied': false,
              'thumbnail': 'https://b.thumbs.redditmedia.com/x.jpg',
            },
          },
        ],
  },
};

/// A listing shaped like old.reddit's, which is what the anonymous path now
/// reads. Only the `data-*` attributes the parser uses are here.
const _listingHtml = '''
<!doctype html><html><body><div class="content" role="main"><div id="siteTable">
  <div class=" thing id-t3_abc123 link " data-fullname="t3_abc123" data-subreddit="dartlang"
       data-author="someone" data-score="412" data-comments-count="37"
       data-timestamp="1769000000000" data-permalink="/r/dartlang/comments/abc123/dart_4_is_out/"
       data-url="https://dart.dev/blog" data-domain="dart.dev" data-nsfw="false">
    <a class="title" href="https://dart.dev/blog">Dart 4 is out</a>
  </div>
</div></div></body></html>
''';

/// Reddit's age gate, which wants a cookie rather than an account.
const _over18Gate = '''
<!doctype html><html><body><div class="content">
  <form action="/over18?dest=%2Fr%2Fdartlang" method="post">
    <input type="hidden" name="over18" value="yes">
  </form>
</div></body></html>
''';

/// A community with nothing in it, and the last page of one that ran out: the
/// listing table is rendered, with no posts inside it.
const _emptyListing = '''
<!doctype html><html><body><div class="content" role="main"><div id="siteTable">
  <p class="title"><em>there doesn't seem to be anything here.</em></p>
</div></div></body></html>
''';

const _privatePage = '''
<!doctype html><html><body><div class="content">
  <h3>This is a private community</h3>
  <p>you must be invited to visit this community</p>
</div></body></html>
''';

const _bannedPage = '''
<!doctype html><html><body><div class="content">
  <h3>This subreddit was banned due to a violation of Reddit's content policy.</h3>
</div></body></html>
''';

const _quarantinedPage = '''
<!doctype html><html><body><div class="content">
  <h3>Are you sure you want to view this community?</h3>
  <form action="/api/quarantine_optin" method="post"><button>Continue</button></form>
</div></body></html>
''';

const _loginPage = '''
<!doctype html><html><body><div class="content">
  <form id="login_login-main" action="//old.reddit.com/post/login" method="post"></form>
</div></body></html>
''';

/// Markup this parser cannot read — a page shape that changed, which is the one
/// case where another route is still worth asking.
const _unreadablePage = '<html><body>nothing familiar</body></html>';

void main() {
  group('normaliseSubreddit', () {
    test('accepts the shapes people paste', () {
      for (final input in [
        'dartlang',
        'r/dartlang',
        '/r/dartlang',
        '/r/dartlang/',
        'R/dartlang',
      ]) {
        expect(normaliseSubreddit(input), 'dartlang', reason: input);
      }
    });

    test('pulls the name out of a URL', () {
      expect(
        normaliseSubreddit('https://www.reddit.com/r/dartlang/'),
        'dartlang',
      );
      expect(
        normaliseSubreddit('https://old.reddit.com/r/dartlang/comments/abc/x/'),
        'dartlang',
      );
    });

    test('rejects what is not a subreddit', () {
      for (final input in [
        '',
        '   ',
        'a',
        'has spaces',
        'https://reddit.com/u/someone',
        'r/',
        'way_too_long_subreddit_name_here',
      ]) {
        expect(normaliseSubreddit(input), isNull, reason: input);
      }
    });
  });

  group('authorisation', () {
    test(
      'uses the installed_client grant with the client id as basic auth',
      () async {
        final requests = <http.Request>[];
        final client = RedditClient(
          httpClient: MockClient((request) async {
            requests.add(request);
            return request.url.path.contains('access_token')
                ? _json(_tokenBody(), 200)
                : _json(_listingBody(), 200);
          }),
        );

        await client.fetchSubreddit('dartlang', clientId: 'my_client_id');

        final token = requests.first;
        expect(
          token.url,
          Uri.parse('https://www.reddit.com/api/v1/access_token'),
        );
        expect(
          token.body,
          contains(
            'grant_type=https://oauth.reddit.com/grants/installed_client',
          ),
        );
        expect(token.body, contains('device_id=${RedditClient.deviceId}'));
        expect(
          token.headers['Authorization'],
          'Basic ${base64Encode(utf8.encode('my_client_id:'))}',
        );
        expect(token.headers['User-Agent'], RedditClient.userAgent);
      },
    );

    test(
      'reuses the token for a second request instead of re-authorising',
      () async {
        var tokenCalls = 0;
        final client = RedditClient(
          httpClient: MockClient((request) async {
            if (request.url.path.contains('access_token')) {
              tokenCalls++;
              return _json(_tokenBody(), 200);
            }
            return _json(_listingBody(), 200);
          }),
        );

        await client.fetchSubreddit('dartlang', clientId: 'id');
        await client.fetchSubreddit('flutterdev', clientId: 'id');

        expect(tokenCalls, 1);
        expect(client.hasToken, isTrue);
      },
    );

    test('a token that is about to expire is not reused', () async {
      var tokenCalls = 0;
      final client = RedditClient(
        clock: () => DateTime.utc(2026, 7, 25, 12),
        httpClient: MockClient((request) async {
          if (request.url.path.contains('access_token')) {
            tokenCalls++;
            // Shorter than the safety margin, so it counts as already expired.
            return _json(_tokenBody(expiresIn: 30), 200);
          }
          return _json(_listingBody(), 200);
        }),
      );

      await client.fetchSubreddit('dartlang', clientId: 'id');
      await client.fetchSubreddit('dartlang', clientId: 'id');

      expect(tokenCalls, 2);
      expect(client.hasToken, isFalse);
    });

    // Without a client id the reader used to fail every request. It now reads
    // the public web, which takes no credentials, so switching the plugin on is
    // enough to see posts.
    test(
      'no client id scrapes old.reddit first, with no token and no auth header',
      () async {
        final requested = <http.Request>[];
        final client = RedditClient(
          httpClient: MockClient((request) async {
            requested.add(request);
            return http.Response(
              _listingHtml,
              200,
              headers: {'content-type': 'text/html'},
            );
          }),
        );

        final listing = await client.fetchSubreddit('dartlang', clientId: '  ');

        expect(
          requested,
          hasLength(1),
          reason: 'no token request, and the HTML answered',
        );
        expect(requested.single.url.host, 'old.reddit.com');
        expect(requested.single.url.path, '/r/dartlang/hot');
        expect(requested.single.headers.containsKey('Authorization'), isFalse);
        // The website, not the API: it has to look like a browser to be served.
        expect(
          requested.single.headers['User-Agent'],
          RedditClient.publicUserAgent,
        );
        expect(listing.posts.single.id, 'abc123');
      },
    );

    test(
      'unreadable HTML falls back to the JSON endpoints rather than giving up',
      () async {
        // Reddit deprecated unauthenticated .json, so HTML leads — but if that
        // page changes shape, the old route is still worth asking.
        final urls = <Uri>[];
        final client = RedditClient(
          httpClient: MockClient((request) async {
            urls.add(request.url);
            if (request.url.path.endsWith('.json')) {
              return _json(_listingBody(), 200);
            }
            return http.Response(
              '<html><body>nothing familiar</body></html>',
              200,
            );
          }),
        );

        final listing = await client.fetchSubreddit('dartlang', clientId: '');

        expect(urls.first.host, 'old.reddit.com');
        expect(urls.any((u) => u.path == '/r/dartlang/hot.json'), isTrue);
        expect(listing.posts, isNotEmpty);
      },
    );

    test('the over-18 gate is answered with a cookie, not a login', () async {
      final cookies = <String?>[];
      var served = 0;
      final client = RedditClient(
        httpClient: MockClient((request) async {
          cookies.add(request.headers['Cookie']);
          served++;
          if (served == 1) {
            return http.Response(_over18Gate, 200);
          }
          return http.Response(_listingHtml, 200);
        }),
      );

      final listing = await client.fetchSubreddit('dartlang', clientId: '');

      expect(cookies.first, isNull);
      expect(cookies[1], contains('over18=1'));
      expect(listing.posts, isNotEmpty);
    });

    test(
      'an anonymous reader that Reddit refuses is told what happened',
      () async {
        // This used to report "add a client id". Reddit now rejects nearly every
        // new app registration, so that named a remedy the reader cannot obtain
        // for a refusal that usually passes on its own.
        const expected = {
          403: RedditErrorKind.blocked,
          429: RedditErrorKind.rateLimited,
        };

        for (final entry in expected.entries) {
          final client = RedditClient(
            httpClient: MockClient(
              (_) async => _json({'error': entry.key}, entry.key),
            ),
          );

          await expectLater(
            client.fetchSubreddit('dartlang', clientId: ''),
            throwsA(
              isA<RedditException>().having((e) => e.kind, 'kind', entry.value),
            ),
            reason: 'HTTP ${entry.key}',
          );
        }
      },
    );

    // www refusing an anonymous reader does not mean old. will: the two are
    // served and throttled separately.
    test('a refusal from www is retried against old.reddit.com', () async {
      final urls = <Uri>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          urls.add(request.url);
          if (!request.url.path.endsWith('.json')) {
            return http.Response('', 403); // the scrape is refused
          }
          if (request.url.host == 'www.reddit.com') {
            return _json({'error': 403}, 403);
          }
          return _json(_listingBody(), 200);
        }),
      );

      final listing = await client.fetchSubreddit('dartlang', clientId: '');

      expect(urls.map((u) => u.host), [
        'old.reddit.com',
        'www.reddit.com',
        'old.reddit.com',
      ]);
      expect(listing.posts, isNotEmpty);
    });

    test('a page that scrapes is not asked for twice', () async {
      final urls = <Uri>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          urls.add(request.url);
          return http.Response(_listingHtml, 200);
        }),
      );

      await client.fetchSubreddit('dartlang', clientId: '');

      expect(
        urls,
        hasLength(1),
        reason: 'the JSON fallback is only paid on failure',
      );
    });

    test(
      'both public hosts refusing is reported as a block, not as missing setup',
      () async {
        // It used to say "add a client id". Reddit now turns away nearly every
        // new app registration, so that sent readers somewhere they could not
        // get to for a refusal that usually passes on its own.
        final client = RedditClient(
          httpClient: MockClient((_) async => _json({'error': 403}, 403)),
        );

        await expectLater(
          client.fetchSubreddit('dartlang', clientId: ''),
          throwsA(
            isA<RedditException>()
                .having((e) => e.kind, 'kind', RedditErrorKind.blocked)
                .having(
                  (e) => e.detail,
                  'detail',
                  contains('both public hosts'),
                ),
          ),
        );
      },
    );

    test('both public hosts throttling is reported as rate limiting', () async {
      final client = RedditClient(
        httpClient: MockClient((_) async => _json({'error': 429}, 429)),
      );

      await expectLater(
        client.fetchSubreddit('dartlang', clientId: ''),
        throwsA(
          isA<RedditException>().having(
            (e) => e.kind,
            'kind',
            RedditErrorKind.rateLimited,
          ),
        ),
      );
    });

    test('the public hosts are asked as a browser, not as an app', () async {
      // The website sits behind an edge that turns away anything announcing
      // itself as a bot, which the API-format agent does.
      final agents = <String?>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          agents.add(request.headers['User-Agent']);
          return _json({
            'kind': 'Listing',
            'data': {'after': null, 'children': const []},
          }, 200);
        }),
      );

      await client.fetchSubreddit('dartlang', clientId: '');

      expect(agents, everyElement(RedditClient.publicUserAgent));
      expect(agents.first, startsWith('Mozilla/'));
      expect(agents.first, isNot(RedditClient.userAgent));
    });

    test('the API keeps the agent Reddit asks its clients for', () async {
      final agents = <String?>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          agents.add(request.headers['User-Agent']);
          if (request.url.path.contains('access_token')) {
            return _json({'access_token': 'tok', 'expires_in': 3600}, 200);
          }
          return _json({
            'kind': 'Listing',
            'data': {'after': null, 'children': const []},
          }, 200);
        }),
      );

      await client.fetchSubreddit('dartlang', clientId: 'my_id');

      expect(agents, everyElement(RedditClient.userAgent));
    });

    test('a 404 is not retried: the subreddit is simply not there', () async {
      final hosts = <String>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          hosts.add(request.url.host);
          return _json({'error': 404}, 404);
        }),
      );

      await expectLater(
        client.fetchSubreddit('dartlang', clientId: ''),
        throwsA(
          isA<RedditException>().having(
            (e) => e.kind,
            'kind',
            RedditErrorKind.notFound,
          ),
        ),
      );
      // A missing subreddit is missing on every host, so the first route to say
      // so is the last one asked.
      expect(hosts, ['old.reddit.com']);
    });

    test('a client id still uses the authenticated host', () async {
      final requested = <http.Request>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          requested.add(request);
          return _json(
            request.url.path.contains('access_token')
                ? _tokenBody()
                : _listingBody(),
            200,
          );
        }),
      );

      await client.fetchSubreddit('dartlang', clientId: 'id');

      expect(requested.last.url.host, 'oauth.reddit.com');
      expect(requested.last.headers['Authorization'], startsWith('Bearer '));
    });

    test('a rejected client id is reported as unauthorised', () async {
      final client = RedditClient(
        httpClient: MockClient((_) async => _json({'error': 401}, 401)),
      );

      await expectLater(
        client.verify(clientId: 'wrong'),
        throwsA(
          isA<RedditException>().having(
            (e) => e.kind,
            'kind',
            RedditErrorKind.unauthorized,
          ),
        ),
      );
    });
  });

  // The anonymous route is served HTML for refusals too, so a 200 says nothing
  // about whether the reader got a listing. These are the pages Reddit sends
  // instead of one.
  group('what Reddit served instead of a listing', () {
    test('each refusal is recognised for what it is', () {
      const pages = {
        _listingHtml: RedditPageKind.listing,
        _emptyListing: RedditPageKind.listing,
        _over18Gate: RedditPageKind.over18Gate,
        _privatePage: RedditPageKind.private,
        _bannedPage: RedditPageKind.banned,
        _quarantinedPage: RedditPageKind.quarantined,
        _loginPage: RedditPageKind.loginWall,
        _unreadablePage: RedditPageKind.unreadable,
      };

      for (final entry in pages.entries) {
        expect(readListingPage(entry.key).kind, entry.value, reason: entry.key);
      }
    });

    test('a community with nothing in it is empty, not broken', () async {
      final hosts = <String>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          hosts.add(request.url.host);
          return http.Response(_emptyListing, 200);
        }),
      );

      final listing = await client.fetchSubreddit('dartlang', clientId: '');

      expect(listing.posts, isEmpty);
      expect(listing.after, isNull);
      // Emptiness is an answer. Asking the other hosts for a different one is
      // three requests spent to be told the same thing.
      expect(hosts, ['old.reddit.com']);
    });

    test(
      'a page whose shape changed is worth asking another route about',
      () async {
        final urls = <Uri>[];
        final client = RedditClient(
          httpClient: MockClient((request) async {
            urls.add(request.url);
            return request.url.path.endsWith('.json')
                ? _json(_listingBody(), 200)
                : http.Response(_unreadablePage, 200);
          }),
        );

        final listing = await client.fetchSubreddit('dartlang', clientId: '');

        expect(listing.posts, isNotEmpty);
        expect(
          urls.length,
          greaterThan(1),
          reason: 'unreadable is the one page kind that is not a verdict',
        );
      },
    );

    test('a refusal is reported as itself and ends the search', () async {
      const refusals = {
        _privatePage: (RedditErrorKind.notFound, 'private'),
        _bannedPage: (RedditErrorKind.notFound, 'banned'),
        _quarantinedPage: (RedditErrorKind.blocked, 'quarantined'),
        _loginPage: (RedditErrorKind.blocked, 'login page'),
      };

      for (final entry in refusals.entries) {
        final (kind, detail) = entry.value;
        final hosts = <String>[];
        final client = RedditClient(
          httpClient: MockClient((request) async {
            hosts.add(request.url.host);
            return http.Response(entry.key, 200);
          }),
        );

        await expectLater(
          client.fetchSubreddit('dartlang', clientId: ''),
          throwsA(
            isA<RedditException>()
                .having((e) => e.kind, 'kind', kind)
                .having((e) => e.detail, 'detail', contains(detail)),
          ),
          reason: detail,
        );
        // No second host is going to disagree about a banned community.
        expect(hosts, ['old.reddit.com'], reason: detail);
      }
    });

    test(
      'a private community is refused with a 403 and still names the reason',
      () async {
        // Reddit serves the private interstitial under a 403, so the status alone
        // would have reported a block and lost which community it was about.
        final client = RedditClient(
          httpClient: MockClient((_) async => http.Response(_privatePage, 403)),
        );

        await expectLater(
          client.fetchSubreddit('dartlang', clientId: ''),
          throwsA(
            isA<RedditException>()
                .having((e) => e.kind, 'kind', RedditErrorKind.notFound)
                .having((e) => e.detail, 'detail', contains('private')),
          ),
        );
      },
    );
  });

  group('fetchSubreddit', () {
    test(
      'asks for the sort, limit and raw_json, and reads the listing',
      () async {
        Uri? listingUrl;
        final client = RedditClient(
          httpClient: MockClient((request) async {
            if (request.url.path.contains('access_token'))
              return _json(_tokenBody(), 200);
            listingUrl = request.url;
            return _json(_listingBody(after: 't3_abc123'), 200);
          }),
        );

        final listing = await client.fetchSubreddit(
          'r/dartlang',
          clientId: 'id',
          sort: RedditSort.newest,
          limit: 10,
        );

        expect(listingUrl!.host, 'oauth.reddit.com');
        expect(listingUrl!.path, '/r/dartlang/new');
        expect(listingUrl!.queryParameters['limit'], '10');
        expect(listingUrl!.queryParameters['raw_json'], '1');

        expect(listing.after, 't3_abc123');
        final post = listing.posts.single;
        expect(post.id, 'abc123');
        expect(post.title, 'Dart 4 is out');
        expect(post.subreddit, 'dartlang');
        expect(post.score, 412);
        expect(post.commentCount, 37);
        expect(post.createdAt, isNotNull);
        expect(post.thumbnailUrl, 'https://b.thumbs.redditmedia.com/x.jpg');
      },
    );

    test('default page size asks Reddit for fifty posts', () async {
      Uri? listingUrl;
      final client = RedditClient(
        httpClient: MockClient((request) async {
          if (request.url.path.contains('access_token')) {
            return _json(_tokenBody(), 200);
          }
          listingUrl = request.url;
          return _json(_listingBody(), 200);
        }),
      );

      await client.fetchSubreddit('dartlang', clientId: 'id');

      expect(kRedditListingPageSize, 50);
      expect(listingUrl!.queryParameters['limit'], '50');
    });

    test(
      'adds t= for authenticated top and controversial listings only',
      () async {
        final urls = <Uri>[];
        final client = RedditClient(
          httpClient: MockClient((request) async {
            if (request.url.path.contains('access_token'))
              return _json(_tokenBody(), 200);
            urls.add(request.url);
            return _json(_listingBody(), 200);
          }),
        );

        await client.fetchSubreddit(
          'dartlang',
          clientId: 'id',
          sort: RedditSort.top,
          timeFilter: RedditTimeFilter.week,
        );
        await client.fetchSubreddit(
          'dartlang',
          clientId: 'id',
          sort: RedditSort.hot,
          timeFilter: RedditTimeFilter.year,
        );
        await client.fetchSubreddit(
          'dartlang',
          clientId: 'id',
          sort: RedditSort.controversial,
          timeFilter: RedditTimeFilter.all,
        );

        expect(urls[0].queryParameters['t'], 'week');
        expect(urls[1].queryParameters.containsKey('t'), isFalse);
        expect(urls[2].queryParameters['t'], 'all');
      },
    );

    test('adds t= to public HTML and JSON listing routes', () async {
      final urls = <Uri>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          urls.add(request.url);
          return request.url.path.endsWith('.json')
              ? _json(_listingBody(), 200)
              : http.Response(_unreadablePage, 200);
        }),
      );

      await client.fetchSubreddit(
        'dartlang',
        clientId: '',
        sort: RedditSort.top,
        timeFilter: RedditTimeFilter.month,
      );

      expect(urls.first.path, '/r/dartlang/top');
      expect(urls.first.queryParameters['t'], 'month');
      expect(urls.first.queryParameters.containsKey('raw_json'), isFalse);
      expect(
        urls.any(
          (url) =>
              url.path == '/r/dartlang/top.json' &&
              url.queryParameters['t'] == 'month',
        ),
        isTrue,
      );
    });

    test('passes the cursor on for the next page', () async {
      Uri? listingUrl;
      final client = RedditClient(
        httpClient: MockClient((request) async {
          if (request.url.path.contains('access_token'))
            return _json(_tokenBody(), 200);
          listingUrl = request.url;
          return _json(_listingBody(), 200);
        }),
      );

      await client.fetchSubreddit(
        'dartlang',
        clientId: 'id',
        after: 't3_abc123',
      );

      expect(listingUrl!.queryParameters['after'], 't3_abc123');
    });

    test('a null after means the end of the listing', () async {
      final client = RedditClient(
        httpClient: MockClient((request) async {
          if (request.url.path.contains('access_token'))
            return _json(_tokenBody(), 200);
          return _json(_listingBody(after: null), 200);
        }),
      );

      expect(
        (await client.fetchSubreddit('dartlang', clientId: 'id')).after,
        isNull,
      );
    });

    test(
      'skips children that are not posts, and posts without a title',
      () async {
        final client = RedditClient(
          httpClient: MockClient((request) async {
            if (request.url.path.contains('access_token'))
              return _json(_tokenBody(), 200);
            return _json(
              _listingBody(
                children: [
                  {
                    'kind': 't1',
                    'data': {'id': 'comment'},
                  },
                  {
                    'kind': 't3',
                    'data': {'id': 'no_title'},
                  },
                  {
                    'kind': 't3',
                    'data': {
                      'id': 'ok',
                      'title': 'Fine',
                      'subreddit': 'x',
                      'permalink': '/x',
                    },
                  },
                ],
              ),
              200,
            );
          }),
        );

        final listing = await client.fetchSubreddit('dartlang', clientId: 'id');

        expect(listing.posts.map((p) => p.id), ['ok']);
      },
    );

    test('a self post carries its text and no thumbnail', () async {
      final client = RedditClient(
        httpClient: MockClient((request) async {
          if (request.url.path.contains('access_token'))
            return _json(_tokenBody(), 200);
          return _json(
            _listingBody(
              children: [
                {
                  'kind': 't3',
                  'data': {
                    'id': 's1',
                    'title': 'Ask anything',
                    'subreddit': 'dartlang',
                    'permalink': '/r/dartlang/comments/s1/x/',
                    'is_self': true,
                    'selftext': '  Some body text  ',
                    'thumbnail': 'self',
                    'over_18': true,
                  },
                },
              ],
            ),
            200,
          );
        }),
      );

      final post = (await client.fetchSubreddit(
        'dartlang',
        clientId: 'id',
      )).posts.single;

      expect(post.isSelf, isTrue);
      expect(post.selfText, 'Some body text');
      expect(
        post.thumbnailUrl,
        isNull,
        reason: '"self" is a sentinel, not an image',
      );
      expect(post.over18, isTrue);
    });

    test('a spoiler post carries the flag', () async {
      final client = RedditClient(
        httpClient: MockClient((request) async {
          if (request.url.path.contains('access_token'))
            return _json(_tokenBody(), 200);
          return _json(
            _listingBody(
              children: [
                {
                  'kind': 't3',
                  'data': {
                    'id': 'spoiled',
                    'title': 'Ending explained',
                    'subreddit': 'movies',
                    'permalink': '/r/movies/comments/spoiled/x/',
                    'spoiler': true,
                  },
                },
              ],
            ),
            200,
          );
        }),
      );

      final post = (await client.fetchSubreddit(
        'movies',
        clientId: 'id',
      )).posts.single;

      expect(post.spoiler, isTrue);
    });

    test('an unknown subreddit name never leaves the device', () async {
      var called = false;
      final client = RedditClient(
        httpClient: MockClient((_) async {
          called = true;
          return _json(_tokenBody(), 200);
        }),
      );

      await expectLater(
        client.fetchSubreddit('not a subreddit', clientId: 'id'),
        throwsA(
          isA<RedditException>().having(
            (e) => e.kind,
            'kind',
            RedditErrorKind.notFound,
          ),
        ),
      );
      expect(called, isFalse);
    });

    test('each documented status maps to its own kind', () async {
      final cases = {
        403: RedditErrorKind.blocked,
        404: RedditErrorKind.notFound,
        429: RedditErrorKind.rateLimited,
        500: RedditErrorKind.badResponse,
      };

      for (final entry in cases.entries) {
        final client = RedditClient(
          httpClient: MockClient((request) async {
            if (request.url.path.contains('access_token'))
              return _json(_tokenBody(), 200);
            return _json({'error': entry.key}, entry.key);
          }),
        );

        await expectLater(
          client.fetchSubreddit('dartlang', clientId: 'id'),
          throwsA(
            isA<RedditException>().having((e) => e.kind, 'kind', entry.value),
          ),
          reason: 'HTTP ${entry.key}',
        );
      }
    });

    test(
      'a 401 on a listing drops the cached token so the next try re-authorises',
      () async {
        var tokenCalls = 0;
        var listingCalls = 0;
        final client = RedditClient(
          httpClient: MockClient((request) async {
            if (request.url.path.contains('access_token')) {
              tokenCalls++;
              return _json(_tokenBody(), 200);
            }
            listingCalls++;
            return listingCalls == 1
                ? _json({'error': 401}, 401)
                : _json(_listingBody(), 200);
          }),
        );

        await expectLater(
          client.fetchSubreddit('dartlang', clientId: 'id'),
          throwsA(isA<RedditException>()),
        );
        expect(client.hasToken, isFalse);

        await client.fetchSubreddit('dartlang', clientId: 'id');
        expect(tokenCalls, 2);
      },
    );

    test('HTML instead of JSON is a bad response, not a crash', () async {
      final client = RedditClient(
        httpClient: MockClient((request) async {
          if (request.url.path.contains('access_token'))
            return _json(_tokenBody(), 200);
          return http.Response(
            '<html>blocked</html>',
            200,
            headers: {'content-type': 'text/html'},
          );
        }),
      );

      await expectLater(
        client.fetchSubreddit('dartlang', clientId: 'id'),
        throwsA(
          isA<RedditException>().having(
            (e) => e.kind,
            'kind',
            RedditErrorKind.badResponse,
          ),
        ),
      );
    });

    test('an unreachable host is a network failure', () async {
      final client = RedditClient(
        httpClient: MockClient(
          (_) async => throw http.ClientException('no route'),
        ),
      );

      await expectLater(
        client.fetchSubreddit('dartlang', clientId: 'id'),
        throwsA(
          isA<RedditException>().having(
            (e) => e.kind,
            'kind',
            RedditErrorKind.network,
          ),
        ),
      );
    });
  });

  group('what a post has to show', () {
    RedditPost post({String? url, String? domain}) => RedditPost(
      id: 'a',
      title: 't',
      subreddit: 'x',
      permalink: '/r/x/comments/a/',
      url: url,
      domain: domain,
    );

    test('a direct picture is shown at full width', () {
      expect(
        post(url: 'https://i.redd.it/abc.jpg', domain: 'i.redd.it').imageUrl,
        'https://i.redd.it/abc.jpg',
      );
      expect(
        post(url: 'https://example.com/photo.PNG').imageUrl,
        'https://example.com/photo.PNG',
      );
      expect(
        post(
          url: 'https://preview.redd.it/x?width=640',
          domain: 'preview.redd.it',
        ).imageUrl,
        isNotNull,
        reason: 'the host serves the picture whatever the path looks like',
      );
    });

    test('a page is not a picture, however tempting the thumbnail is', () {
      expect(
        post(
          url: 'https://www.reddit.com/gallery/abc',
          domain: 'reddit.com',
        ).imageUrl,
        isNull,
      );
      expect(
        post(url: 'https://v.redd.it/abc', domain: 'v.redd.it').imageUrl,
        isNull,
      );
      expect(
        post(
          url: 'https://news.example.com/story',
          domain: 'news.example.com',
        ).imageUrl,
        isNull,
      );
      expect(post().imageUrl, isNull);
      expect(post(url: 'not a url at all').imageUrl, isNull);
    });

    test(
      'placeholder titles stay hidden when the picture is already shown',
      () {
        final image = RedditPost(
          id: 'a',
          title: '<image>',
          subreddit: 'x',
          permalink: '/r/x/comments/a/',
          url: 'https://i.redd.it/abc.jpg',
          domain: 'i.redd.it',
        );
        expect(image.showsTitle, isFalse);

        final realTitle = RedditPost(
          id: 'b',
          title: 'Look at this',
          subreddit: 'x',
          permalink: '/r/x/comments/b/',
          url: 'https://i.redd.it/abc.jpg',
          domain: 'i.redd.it',
        );
        expect(realTitle.showsTitle, isTrue);

        final textOnly = RedditPost(
          id: 'c',
          title: '<image>',
          subreddit: 'x',
          permalink: '/r/x/comments/c/',
          isSelf: true,
        );
        expect(
          textOnly.showsTitle,
          isTrue,
          reason: 'no media — keep the title',
        );

        final gtImage = RedditPost(
          id: 'd',
          title: '>image>',
          subreddit: 'x',
          permalink: '/r/x/comments/d/',
          url: 'https://i.redd.it/abc.jpg',
          domain: 'i.redd.it',
        );
        expect(gtImage.showsTitle, isFalse);

        final placeholderBody = RedditPost(
          id: 'e',
          title: 'A real title',
          subreddit: 'x',
          permalink: '/r/x/comments/e/',
          url: 'https://i.redd.it/abc.jpg',
          domain: 'i.redd.it',
          isSelf: true,
          selfText: '>image>',
        );
        expect(placeholderBody.showsTitle, isTrue);
        expect(placeholderBody.showsSelfText, isFalse);
      },
    );

    test(
      'a title that is the picture URL stays hidden next to the picture',
      () {
        final url =
            'https://preview.redd.it/aabro673w5mh1.jpeg?width=2400&format=pjpg';
        final onlyUrl = RedditPost(
          id: 'u',
          title: url,
          subreddit: 'x',
          permalink: '/r/x/comments/u/',
          url: url,
          domain: 'preview.redd.it',
        );
        expect(onlyUrl.showsTitle, isFalse);
        expect(onlyUrl.displayTitle, isEmpty);

        final caption = RedditPost(
          id: 'v',
          title: 'look: $url',
          subreddit: 'x',
          permalink: '/r/x/comments/v/',
          url: url,
          domain: 'preview.redd.it',
        );
        expect(caption.showsTitle, isTrue);
        expect(caption.displayTitle, 'look');
      },
    );

    test(
      'a video is recognised so the card offers a play badge, not a dead image',
      () {
        expect(
          post(url: 'https://v.redd.it/abc', domain: 'v.redd.it').isVideo,
          isTrue,
        );
        expect(
          post(
            url: 'https://www.youtube.com/watch?v=1',
            domain: 'youtube.com',
          ).isVideo,
          isTrue,
        );
        expect(
          post(url: 'https://example.com/story', domain: 'example.com').isVideo,
          isFalse,
        );
        expect(post().isVideo, isFalse);
      },
    );

    test(
      'HTML listings without secure_media still resolve a playable DASH URL',
      () {
        final scraped = post(
          url: 'https://v.redd.it/pigday1',
          domain: 'v.redd.it',
        );
        expect(
          scraped.resolvedVideoDashUrl,
          'https://v.redd.it/pigday1/DASHPlaylist.mpd',
        );
        expect(
          post(
            url: 'https://youtube.com/watch?v=1',
            domain: 'youtube.com',
          ).resolvedVideoDashUrl,
          isNull,
          reason: 'only native Reddit video gets a reconstructed DASH URL',
        );
      },
    );

    test('JSON videoDashUrl wins over reconstructing from the link', () {
      final withDash = RedditPost(
        id: 'a',
        title: 't',
        subreddit: 'x',
        permalink: '/r/x/comments/a/',
        url: 'https://v.redd.it/abc',
        domain: 'v.redd.it',
        videoDashUrl: 'https://v.redd.it/abc/DASHPlaylist.mpd',
      );
      expect(withDash.resolvedVideoDashUrl, withDash.videoDashUrl);
    });

    test('link cards use previewImage, else the listing thumbnail', () {
      final withPreview = RedditPost(
        id: 'a',
        title: 't',
        subreddit: 'x',
        permalink: '/r/x/comments/a/',
        url: 'https://t-online.de/story',
        domain: 't-online.de',
        previewImage: 'https://preview.redd.it/article.jpg?width=1080',
        thumbnail: 'https://b.thumbs.redditmedia.com/small.jpg',
      );
      expect(withPreview.cardPreviewImage, withPreview.previewImage);

      final htmlOnly = RedditPost(
        id: 'b',
        title: 't',
        subreddit: 'x',
        permalink: '/r/x/comments/b/',
        url: 'https://t-online.de/story',
        domain: 't-online.de',
        thumbnail: 'https://b.thumbs.redditmedia.com/article.jpg',
      );
      expect(
        htmlOnly.cardPreviewImage,
        'https://b.thumbs.redditmedia.com/article.jpg',
        reason:
            'old.reddit scrape has no previewImage — thumbnail still fills the banner',
      );
    });

    test('NSFW hide mode removes over-18 posts', () {
      final safe = post();
      final adult = RedditPost(
        id: 'b',
        title: 'b',
        subreddit: 'x',
        permalink: '/r/x/comments/b/',
        over18: true,
      );

      expect(filterRedditPosts([safe, adult], nsfwMode: RedditNsfwMode.hide), [
        safe,
      ]);
      expect(filterRedditPosts([safe, adult], nsfwMode: RedditNsfwMode.tap), [
        safe,
        adult,
      ]);
    });

    test('saved snapshots keep enough post data to reopen', () {
      final saved = RedditPost.listFromPrefs(
        jsonEncode([
          RedditPost(
            id: 's1',
            title: 'Saved',
            subreddit: 'dartlang',
            permalink: '/r/dartlang/comments/s1/saved/',
            url: 'https://i.redd.it/s1.jpg',
            over18: true,
            spoiler: true,
            galleryImages: const ['https://i.redd.it/one.jpg'],
          ).toJson(),
        ]),
      );

      expect(saved.single.id, 's1');
      expect(saved.single.url, 'https://i.redd.it/s1.jpg');
      expect(saved.single.over18, isTrue);
      expect(saved.single.spoiler, isTrue);
      expect(saved.single.galleryImages, ['https://i.redd.it/one.jpg']);
    });
  });

  group('a gallery post', () {
    Map<String, dynamic> child({Map<String, dynamic> extra = const {}}) => {
      'kind': 't3',
      'data': {
        'id': 'g1',
        'title': 'A gallery',
        'subreddit': 'pics',
        'permalink': '/r/pics/comments/g1/a_gallery/',
        'url': 'https://www.reddit.com/gallery/g1',
        ...extra,
      },
    };

    test('carries its pictures in the author\'s order, unescaped', () {
      final post = RedditPost.fromChild(
        child(
          extra: {
            'gallery_data': {
              'items': [
                {'media_id': 'two'},
                {'media_id': 'one'},
              ],
            },
            'media_metadata': {
              'one': {
                's': {
                  'u': 'https://preview.redd.it/one.jpg?width=640&amp;s=abc',
                },
              },
              'two': {
                's': {
                  'u': 'https://preview.redd.it/two.jpg?width=640&amp;s=def',
                },
              },
            },
          },
        ),
      )!;

      expect(post.galleryImages, [
        'https://preview.redd.it/two.jpg?width=640&s=def',
        'https://preview.redd.it/one.jpg?width=640&s=abc',
      ]);
      expect(
        post.imageUrl,
        'https://preview.redd.it/two.jpg?width=640&s=def',
        reason: 'a gallery whose url is a page still has a picture to show',
      );
    });

    test('a GIF in a gallery serves its gif, not a missing u', () {
      final post = RedditPost.fromChild(
        child(
          extra: {
            'gallery_data': {
              'items': [
                {'media_id': 'anim'},
              ],
            },
            'media_metadata': {
              'anim': {
                's': {'gif': 'https://i.redd.it/anim.gif'},
              },
            },
          },
        ),
      )!;

      expect(post.galleryImages, ['https://i.redd.it/anim.gif']);
    });

    test('files with no order are still worth showing', () {
      // A crosspost sometimes arrives with media_metadata and no gallery_data.
      final post = RedditPost.fromChild(
        child(
          extra: {
            'media_metadata': {
              'only': {
                's': {'u': 'https://preview.redd.it/only.jpg'},
              },
            },
          },
        ),
      )!;

      expect(post.galleryImages, ['https://preview.redd.it/only.jpg']);
    });

    test('a post that is not a gallery has no gallery', () {
      expect(RedditPost.fromChild(child())!.galleryImages, isEmpty);
    });

    test(
      'metadata that no longer fits reads as nothing rather than throwing',
      () {
        final post = RedditPost.fromChild(
          child(
            extra: {
              'gallery_data': 'nonsense',
              'media_metadata': {
                'one': {'s': 'also nonsense'},
                'two': 42,
              },
            },
          ),
        )!;

        expect(post.galleryImages, isEmpty);
      },
    );
  });

  group('a post\'s preview', () {
    test('is read and unescaped, for the card of a video or an article', () {
      final post = RedditPost.fromChild({
        'kind': 't3',
        'data': {
          'id': 'v1',
          'title': 'A clip',
          'subreddit': 'videos',
          'permalink': '/r/videos/comments/v1/a_clip/',
          'url': 'https://v.redd.it/v1',
          'preview': {
            'images': [
              {
                'source': {
                  'url':
                      'https://preview.redd.it/poster.jpg?width=1080&amp;s=xyz',
                },
              },
            ],
          },
        },
      })!;

      expect(
        post.previewImage,
        'https://preview.redd.it/poster.jpg?width=1080&s=xyz',
      );
      expect(
        post.imageUrl,
        isNull,
        reason: 'a poster frame is the card\'s banner, not the post\'s picture',
      );
    });

    test('a post without one has none', () {
      final post = RedditPost.fromChild({
        'kind': 't3',
        'data': {
          'id': 'p',
          'title': 't',
          'subreddit': 'x',
          'permalink': '/r/x/comments/p/',
        },
      })!;

      expect(post.previewImage, isNull);
    });
  });

  group('fetchComments', () {
    const permalink = '/r/dartlang/comments/abc123/dart_4_is_out/';
    const threadHtml = '''
<!doctype html><html><body>
  <div id="siteTable">
    <div class="thing" data-fullname="t3_abc123" data-url="https://dart.dev/blog"></div>
  </div>
  <div class="commentarea"><div class="sitetable nestedlisting">
    <div class="thing id-t1_c1 comment" data-fullname="t1_c1" data-author="someone">
      <div class="entry unvoted">
        <p class="tagline"><span class="score unvoted">3 points</span>
          <time datetime="2026-07-01T10:00:00+00:00"></time></p>
        <form class="usertext"><div class="usertext-body"><div class="md"><p>Hello</p></div></div></form>
      </div>
      <div class="child"></div>
    </div>
  </div></div>
</body></html>
''';

    test('a signed-in reader hits the authenticated host', () async {
      final hosts = <String>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          hosts.add(request.url.host);
          expect(request.headers['Authorization'], 'Bearer user_tok');
          return _json([
            {
              'kind': 'Listing',
              'data': {
                'children': [
                  {
                    'kind': 't3',
                    'data': {
                      'id': 'abc123',
                      'title': 'Dart 4 is out',
                      'subreddit': 'dartlang',
                      'permalink': permalink,
                      'selftext': 'Body',
                      'url': 'https://dart.dev/blog',
                      'is_self': true,
                    },
                  },
                ],
              },
            },
            {
              'kind': 'Listing',
              'data': {
                'children': [
                  {
                    'kind': 't1',
                    'data': {
                      'id': 'c1',
                      'author': 'someone',
                      'body': 'Hello',
                      'score': 3,
                      'created_utc': 1769000000,
                      'permalink': '${permalink}c1/',
                      'replies': '',
                    },
                  },
                ],
              },
            },
          ], 200);
        }),
      );

      final result = await client.fetchComments(
        permalink,
        clientId: 'app',
        userToken: 'user_tok',
      );
      expect(hosts, ['oauth.reddit.com']);
      expect(result.comments.single.id, 'c1');
      expect(result.selfText, 'Body');
    });

    test('a refused OAuth read falls back to the old site', () async {
      final hosts = <String>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          hosts.add(request.url.host);
          if (request.url.host == 'oauth.reddit.com') {
            return _json({'error': 403}, 403);
          }
          return http.Response(
            threadHtml,
            200,
            headers: {'content-type': 'text/html'},
          );
        }),
      );

      final result = await client.fetchComments(
        permalink,
        clientId: 'app',
        userToken: 'user_tok',
      );
      expect(hosts, ['oauth.reddit.com', 'old.reddit.com']);
      expect(result.comments.single.body, 'Hello');
    });

    test('an empty scrape falls back to public JSON comments', () async {
      const emptyThread = '''
<!doctype html><html><body>
  <div id="siteTable"></div>
  <div class="commentarea"></div>
</body></html>
''';
      final hosts = <String>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          hosts.add(request.url.host);
          if (request.url.path.endsWith('.json')) {
            return _json([
              {
                'kind': 'Listing',
                'data': {
                  'children': [
                    {
                      'kind': 't3',
                      'data': {
                        'id': 'abc123',
                        'title': 'Dart 4 is out',
                        'subreddit': 'dartlang',
                        'permalink': permalink,
                        'selftext': 'Body',
                        'url': 'https://dart.dev/blog',
                        'is_self': true,
                      },
                    },
                  ],
                },
              },
              {
                'kind': 'Listing',
                'data': {
                  'children': [
                    {
                      'kind': 't1',
                      'data': {
                        'id': 'c1',
                        'author': 'someone',
                        'body': 'From JSON',
                        'score': 3,
                        'created_utc': 1769000000,
                        'permalink': '${permalink}c1/',
                        'replies': '',
                      },
                    },
                  ],
                },
              },
            ], 200);
          }
          return http.Response(
            emptyThread,
            200,
            headers: {'content-type': 'text/html'},
          );
        }),
      );

      final result = await client.fetchComments(permalink, clientId: '');
      expect(hosts.first, 'old.reddit.com');
      expect(hosts, contains('www.reddit.com'));
      expect(result.comments.single.body, 'From JSON');
    });

    test('preferPublic scrapes even when a token is sitting there', () async {
      final hosts = <String>[];
      final client = RedditClient(
        httpClient: MockClient((request) async {
          hosts.add(request.url.host);
          expect(request.headers.containsKey('Authorization'), isFalse);
          return http.Response(
            threadHtml,
            200,
            headers: {'content-type': 'text/html'},
          );
        }),
      );

      final result = await client.fetchComments(
        permalink,
        clientId: 'app',
        userToken: 'user_tok',
        preferPublic: true,
      );
      expect(hosts, ['old.reddit.com']);
      expect(result.comments, hasLength(1));
    });
  });
}
