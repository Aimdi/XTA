import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';

void main() {
  group('parseThreadsCookieHeader', () {
    test('splits a Cookie header into a map', () {
      final cookies = parseThreadsCookieHeader(
        'sessionid=abc%3A1; csrftoken=tok; ds_user_id=9; mid=m; ig_did=uuid; other=x',
      );
      expect(cookies['sessionid'], 'abc%3A1');
      expect(cookies['csrftoken'], 'tok');
      expect(cookies['ds_user_id'], '9');
      expect(threadsCookiesComplete(cookies), isTrue);
    });

    test('rejects incomplete cookies', () {
      expect(threadsCookiesComplete({'sessionid': 'x'}), isFalse);
    });
  });

  group('normaliseThreadsBearer', () {
    test('accepts IGT:2 with or without Bearer prefix', () {
      expect(normaliseThreadsBearer('IGT:2:abc'), 'IGT:2:abc');
      expect(normaliseThreadsBearer('Bearer IGT:2:abc'), 'IGT:2:abc');
      expect(normaliseThreadsBearer('not-a-token'), isNull);
    });
  });

  group('parseThreadsApiFeed', () {
    test('reads caption, user and permalink from thread_items', () {
      final posts = parseThreadsApiFeed({
        'threads': [
          {
            'thread_items': [
              {
                'post': {
                  'pk': '111',
                  'code': 'AbC',
                  'taken_at': 1720000000,
                  'caption': {'text': 'Hello Threads'},
                  'user': {
                    'username': 'zuck',
                    'full_name': 'Mark',
                    'profile_pic_url': 'https://example.org/a.jpg',
                  },
                  'image_versions2': {
                    'candidates': [
                      {'url': 'https://example.org/p.jpg'},
                    ],
                  },
                },
              },
            ],
          },
        ],
      });

      expect(posts, hasLength(1));
      expect(posts.first.text, 'Hello Threads');
      expect(posts.first.handle, 'zuck');
      expect(posts.first.authorName, 'Mark');
      expect(posts.first.url, 'https://www.threads.com/@zuck/post/AbC');
      expect(posts.first.images, ['https://example.org/p.jpg']);
      expect(posts.first.publishedAt, isNotNull);
      expect(posts.first.likeCount, isNull);
    });

    test('reads likes, replies, reposts and a link preview', () {
      final posts = parseThreadsApiFeed({
        'threads': [
          {
            'thread_items': [
              {
                'post': {
                  'pk': '222',
                  'code': 'LiNk',
                  'like_count': 1348,
                  'caption': {'text': 'Worth a read'},
                  'user': {
                    'username': 'zuck',
                    'full_name': 'Mark',
                    'is_verified': true,
                  },
                  'text_post_app_info': {
                    'direct_reply_count': 12,
                    'repost_count': 4,
                    'link_preview_attachment': {
                      'url':
                          'https://l.threads.com/?u=https%3A%2F%2Fexample.org%2Fstory',
                      'title': 'A story',
                      'description': 'About something',
                      'image_url': 'https://example.org/og.jpg',
                      'display_url': 'example.org',
                    },
                  },
                },
              },
            ],
          },
        ],
      });

      expect(posts, hasLength(1));
      expect(posts.first.likeCount, 1348);
      expect(posts.first.replyCount, 12);
      expect(posts.first.repostCount, 4);
      expect(posts.first.isVerified, isTrue);
      expect(posts.first.linkCard?.title, 'A story');
      expect(posts.first.linkCard?.url, 'https://example.org/story');
    });

    test('unwraps share_info.reposted_post and keeps the reposter', () {
      final posts = parseThreadsApiFeed({
        'threads': [
          {
            'thread_items': [
              {
                'post': {
                  'pk': '99',
                  'code': 'outerCode',
                  'caption': {'text': ''},
                  'taken_at': 1700000000,
                  'like_count': 1,
                  'user': {
                    'username': 'zuck',
                    'full_name': 'Mark Zuckerberg',
                    'profile_pic_url': 'https://cdn.example/zuck.jpg',
                  },
                  'text_post_app_info': {
                    'direct_reply_count': 0,
                    'repost_count': 0,
                    'share_info': {
                      'reposted_post': {
                        'pk': '55',
                        'code': 'innerCode',
                        'caption': {'text': 'original from meta'},
                        'taken_at': 1699990000,
                        'like_count': 42,
                        'user': {
                          'username': 'meta',
                          'full_name': 'Meta',
                          'profile_pic_url': 'https://cdn.example/meta.jpg',
                        },
                        'text_post_app_info': {
                          'direct_reply_count': 3,
                          'repost_count': 7,
                          'quote_count': 1,
                        },
                        'image_versions2': {
                          'candidates': [
                            {
                              'url': 'https://cdn.example/inner.jpg',
                              'width': 640,
                            },
                          ],
                        },
                      },
                    },
                  },
                },
              },
            ],
          },
        ],
      });

      expect(posts, hasLength(1));
      final post = posts.first;
      expect(post.isRepost, isTrue);
      expect(post.id, '99');
      expect(post.handle, 'meta');
      expect(post.authorName, 'Meta');
      expect(post.text, 'original from meta');
      expect(post.repostedByHandle, 'zuck');
      expect(post.repostedByName, 'Mark Zuckerberg');
      expect(post.likeCount, 42);
      expect(post.replyCount, 3);
      expect(post.repostCount, 7);
      expect(post.images.single, 'https://cdn.example/inner.jpg');
      expect(post.url, 'https://www.threads.com/@meta/post/innerCode');
      // Timing reflects when the profile reposted, not the original.
      expect(
        post.publishedAt,
        DateTime.fromMillisecondsSinceEpoch(
          1700000000 * 1000,
          isUtc: true,
        ).toLocal(),
      );
    });
  });

  group('parseThreadsSsrHtml', () {
    test('pulls posts out of data-sjs blobs', () {
      final blob = jsonEncode({
        'require': [
          [
            'RelayPrefetchedStreamCache',
            'next',
            [
              null,
              {
                'thread_items': [
                  {
                    'post': {
                      'pk': '9',
                      'code': 'Xx',
                      'caption': {'text': 'from ssr'},
                      'user': {'username': 'zuck', 'full_name': 'Z'},
                    },
                  },
                ],
              },
            ],
          ],
        ],
      });
      final html =
          '<html><script type="application/json" data-sjs>$blob</script></html>';
      final posts = parseThreadsSsrHtml(html, 'zuck');
      expect(posts, hasLength(1));
      expect(posts.first.text, 'from ssr');
    });

    test('profile parse keeps only the root of each thread_items list', () {
      final blob = jsonEncode({
        'thread_items': [
          {
            'post': {
              'pk': '1',
              'code': 'a',
              'caption': {'text': 'root'},
              'user': {'username': 'zuck', 'full_name': 'Z'},
            },
          },
          {
            'post': {
              'pk': '2',
              'code': 'b',
              'caption': {'text': 'reply'},
              'user': {'username': 'other', 'full_name': 'O'},
            },
          },
        ],
      });
      final html =
          '<html><script type="application/json" data-sjs>$blob</script></html>';
      final posts = parseThreadsSsrHtml(html, 'zuck');
      expect(posts.map((p) => p.text), ['root']);
    });

    test('keeps reposts when matching the reposter handle', () {
      final blob = jsonEncode({
        'require': [
          [
            'RelayPrefetchedStreamCache',
            'next',
            [
              null,
              {
                'thread_items': [
                  {
                    'post': {
                      'pk': '99',
                      'code': 'outer',
                      'caption': {'text': ''},
                      'taken_at': 1700000000,
                      'user': {'username': 'zuck', 'full_name': 'Mark'},
                      'text_post_app_info': {
                        'share_info': {
                          'reposted_post': {
                            'pk': '55',
                            'code': 'inner',
                            'caption': {'text': 'from someone else'},
                            'user': {'username': 'meta', 'full_name': 'Meta'},
                          },
                        },
                      },
                    },
                  },
                ],
              },
            ],
          ],
        ],
      });
      final html =
          '<html><script type="application/json" data-sjs>$blob</script></html>';
      final posts = parseThreadsSsrHtml(html, 'zuck');
      expect(posts, hasLength(1));
      expect(posts.first.isRepost, isTrue);
      expect(posts.first.handle, 'meta');
      expect(posts.first.repostedByHandle, 'zuck');
      expect(posts.first.text, 'from someone else');
    });
  });

  group('parseThreadsSsrThread', () {
    test('keeps every reply in the thread_items chain', () {
      final blob = jsonEncode({
        'thread_items': [
          {
            'post': {
              'pk': '1',
              'code': 'a',
              'caption': {'text': 'root'},
              'user': {'username': 'zuck', 'full_name': 'Z'},
            },
          },
          {
            'post': {
              'pk': '2',
              'code': 'b',
              'caption': {'text': 'reply'},
              'user': {'username': 'other', 'full_name': 'O'},
            },
          },
        ],
      });
      final html =
          '<html><script type="application/json" data-sjs>$blob</script></html>';
      final posts = parseThreadsSsrThread(html);
      expect(posts.map((p) => p.text), ['root', 'reply']);
    });
  });

  group('guest GraphQL helpers', () {
    test('extractThreadsLsd reads the LSD token blob', () {
      expect(extractThreadsLsd('nope'), isNull);
      expect(
        extractThreadsLsd(r'prefix["LSD",[],{"token":"AbC_12-x"}]suffix'),
        'AbC_12-x',
      );
    });

    test(
      'extractThreadsUserIdFromHtml prefers props.user_id on logged-out pages',
      () {
        final html =
            r'{"props":{"initial_thread_count":4,"is_self_profile":false,"user_id":"63055343223"}}';
        expect(extractThreadsUserIdFromHtml(html, 'zuck'), '63055343223');
      },
    );

    test('extractThreadsUserIdFromHtml falls back to pk near username', () {
      final html = r'{"username":"zuck","full_name":"Z","pk":"63055343223"}';
      expect(extractThreadsUserIdFromHtml(html, 'zuck'), '63055343223');
      expect(extractThreadsUserIdFromHtml(html, 'instagram'), isNull);
    });

    test(
      'extractThreadsUserIdFromHtml falls back to modal userID and ignores 0',
      () {
        final html =
            r'{"userID":"0"}{"userID":"63404918397"}{"userID":"63404918397"}{"userID":"1"}';
        expect(extractThreadsUserIdFromHtml(html, 'anyone'), '63404918397');
      },
    );

    test('threadsProfileFromPosts builds a card when OG scrape is empty', () {
      final profile = threadsProfileFromPosts('zuck', [
        const ThreadsPost(
          id: '1',
          handle: 'zuck',
          authorName: 'Mark',
          text: 'hi',
          avatarUrl: 'https://example.org/a.jpg',
        ),
      ]);
      expect(profile, isNotNull);
      expect(profile!.username, 'zuck');
      expect(profile.fullName, 'Mark');
      expect(profile.profilePicUrl, 'https://example.org/a.jpg');
      expect(threadsProfileFromPosts('nobody', const []), isNull);
    });

    test(
      'threadsProfileFromGuestHtml reads OG title, description and image',
      () {
        const html = '''
<html><head>
<meta property="og:title" content="Mark Zuckerberg (&#064;zuck) &#x2022; Threads, Say more" />
<meta property="og:description" content="5.7M Followers &#x2022; 153 Threads &#x2022; Mostly MMA takes." />
<meta property="og:image" content="https://example.org/a.jpg?x=1&amp;y=2" />
<script>{"props":{"user_id":"63055343223"}}</script>
</head></html>
''';
        final profile = threadsProfileFromGuestHtml(html, 'zuck');
        expect(profile, isNotNull);
        expect(profile!.username, 'zuck');
        expect(profile.fullName, 'Mark Zuckerberg');
        expect(profile.followerCount, 5700000);
        expect(profile.mediaCount, 153);
        expect(profile.biography, 'Mostly MMA takes.');
        expect(profile.profilePicUrl, 'https://example.org/a.jpg?x=1&y=2');
        expect(profile.pk, '63055343223');
      },
    );

    test('parseThreadsCompactCount reads K/M suffixes', () {
      expect(parseThreadsCompactCount('5.7M'), 5700000);
      expect(parseThreadsCompactCount('1.4K'), 1400);
      expect(parseThreadsCompactCount('380'), 380);
      expect(parseThreadsCompactCount('nope'), isNull);
    });

    test('parseThreadsGraphqlFeed reads mediaData.threads', () {
      final posts = parseThreadsGraphqlFeed({
        'data': {
          'mediaData': {
            'threads': [
              {
                'thread_items': [
                  {
                    'post': {
                      'pk': '42',
                      'code': 'Cd',
                      'caption': {'text': 'from gql'},
                      'user': {'username': 'instagram', 'full_name': 'IG'},
                    },
                  },
                ],
              },
            ],
          },
        },
      });
      expect(posts, hasLength(1));
      expect(posts.first.text, 'from gql');
      expect(posts.first.handle, 'instagram');
    });
  });

  group('ThreadsDirectClient', () {
    late PrefServiceCache prefs;

    setUp(() {
      prefs = PrefServiceCache(
        cache: {
          optionPluginThreadsDirectCookies: '',
          optionPluginThreadsDirectBearer: '',
          optionPluginThreadsDirectDeviceId: 'device-1',
        },
      );
    });

    test('fetchFollowingTimeline sends Bearer to Instagram API', () async {
      await prefs.set(optionPluginThreadsDirectBearer, 'IGT:2:secret');
      final client = ThreadsDirectClient(
        prefs,
        minGap: Duration.zero,
        httpClient: MockClient((request) async {
          expect(request.url.host, 'i.instagram.com');
          expect(request.url.path, '/api/v1/feed/text_post_app_timeline/');
          expect(request.url.queryParameters['feed_type'], 'for_you');
          expect(request.url.queryParameters['reason'], 'cold_start_fetch');
          expect(request.url.queryParameters['client_session_id'], 'device-1');
          expect(request.headers['Authorization'], 'Bearer IGT:2:secret');
          return http.Response(
            jsonEncode({
              'threads': [
                {
                  'thread_items': [
                    {
                      'post': {
                        'pk': '1',
                        'code': 'c',
                        'caption': {'text': 'hi'},
                        'user': {'username': 'a', 'full_name': 'A'},
                      },
                    },
                  ],
                },
              ],
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final posts = await client.fetchFollowingTimeline();
      expect(posts.first.text, 'hi');
    });

    test('maps login_required to sessionSuspended', () async {
      await prefs.set(
        optionPluginThreadsDirectCookies,
        'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g',
      );
      final client = ThreadsDirectClient(
        prefs,
        minGap: Duration.zero,
        httpClient: MockClient(
          (_) async => http.Response(
            '{"message":"login_required","logout_reason":8}',
            403,
          ),
        ),
      );

      expect(
        () => client.currentUser(),
        throwsA(
          isA<ThreadsException>().having(
            (e) => e.kind,
            'kind',
            ThreadsErrorKind.sessionSuspended,
          ),
        ),
      );
    });

    test(
      'fetchGuestAccount uses GraphQL after reading LSD and user id from HTML',
      () async {
        var sawGraphql = false;
        final client = ThreadsDirectClient(
          prefs,
          minGap: Duration.zero,
          httpClient: MockClient((request) async {
            if (request.method == 'GET' && request.url.path == '/@instagram') {
              return http.Response(
                r'<html><script>["LSD",[],{"token":"tok123"}]</script>'
                r'<script>{"props":{"is_self_profile":false,"user_id":"63404918397"}}</script></html>',
                200,
                headers: {'content-type': 'text/html'},
              );
            }
            if (request.method == 'POST' &&
                request.url.path == '/api/graphql') {
              sawGraphql = true;
              expect(request.headers['X-FB-LSD'], 'tok123');
              expect(request.headers['X-IG-App-ID'], '238260118697367');
              expect(
                request.body,
                contains('doc_id=$threadsGuestProfileThreadsDocId'),
              );
              expect(request.body, contains('63404918397'));
              return http.Response(
                jsonEncode({
                  'data': {
                    'mediaData': {
                      'threads': [
                        {
                          'thread_items': [
                            {
                              'post': {
                                'pk': '99',
                                'code': 'Gg',
                                'caption': {'text': 'guest gql post'},
                                'user': {
                                  'username': 'instagram',
                                  'full_name': 'Instagram',
                                },
                              },
                            },
                          ],
                        },
                      ],
                    },
                  },
                }),
                200,
                headers: {'content-type': 'application/json'},
              );
            }
            return http.Response(
              'unexpected ${request.method} ${request.url}',
              500,
            );
          }),
        );

        final posts = await client.fetchGuestAccount('instagram');
        expect(sawGraphql, isTrue);
        expect(posts, hasLength(1));
        expect(posts.first.text, 'guest gql post');
        expect(
          prefs.get<String>(optionPluginThreadsUserIds),
          contains('63404918397'),
        );
      },
    );

    test('profile + posts for one handle share a single HTML GET', () async {
      var htmlHits = 0;
      final client = ThreadsDirectClient(
        prefs,
        minGap: Duration.zero,
        httpClient: MockClient((request) async {
          if (request.method == 'GET' && request.url.path == '/@instagram') {
            htmlHits++;
            await Future<void>.delayed(const Duration(milliseconds: 40));
            return http.Response(
              r'<html><script>["LSD",[],{"token":"tok"}]</script>'
              r'<meta property="og:title" content="Instagram (@instagram)"/>'
              r'<script>{"props":{"user_id":"63404918397"}}</script></html>',
              200,
              headers: {'content-type': 'text/html'},
            );
          }
          if (request.method == 'POST' && request.url.path == '/api/graphql') {
            return http.Response(
              jsonEncode({
                'data': {
                  'mediaData': {
                    'threads': [
                      {
                        'thread_items': [
                          {
                            'post': {
                              'pk': '1',
                              'code': 'c',
                              'caption': {'text': 'hi'},
                              'user': {
                                'username': 'instagram',
                                'full_name': 'IG',
                              },
                            },
                          },
                        ],
                      },
                    ],
                  },
                },
              }),
              200,
              headers: {'content-type': 'application/json'},
            );
          }
          return http.Response('unexpected ${request.url}', 500);
        }),
      );

      await Future.wait([
        client.fetchGuestProfile('instagram'),
        client.fetchGuestAccount('instagram'),
      ]);

      expect(
        htmlHits,
        1,
        reason: 'dedupe cuts Meta traffic; it must not double it',
      );
    });

    test(
      'fetchUserThreads falls back to guest GraphQL when cookies are refused',
      () async {
        await prefs.set(
          optionPluginThreadsDirectCookies,
          'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g',
        );
        await prefs.set(optionPluginThreadsUseSessionApis, true);
        await prefs.set(
          optionPluginThreadsUserIds,
          '{"instagram":"63404918397"}',
        );
        final client = ThreadsDirectClient(
          prefs,
          minGap: Duration.zero,
          httpClient: MockClient((request) async {
            if (request.url.path.contains('/text_feed/')) {
              return http.Response(
                '{"message":"login_required","logout_reason":8}',
                403,
              );
            }
            if (request.method == 'GET' && request.url.path == '/@instagram') {
              return http.Response(
                r'<html><script>["LSD",[],{"token":"tok"}]</script>'
                r'<script>{"props":{"user_id":"63404918397"}}</script></html>',
                200,
              );
            }
            if (request.method == 'POST' &&
                request.url.path == '/api/graphql') {
              return http.Response(
                jsonEncode({
                  'data': {
                    'mediaData': {
                      'threads': [
                        {
                          'thread_items': [
                            {
                              'post': {
                                'pk': '5',
                                'code': 'X',
                                'caption': {
                                  'text': 'via guest after cookie fail',
                                },
                                'user': {
                                  'username': 'instagram',
                                  'full_name': 'IG',
                                },
                              },
                            },
                          ],
                        },
                      ],
                    },
                  },
                }),
                200,
              );
            }
            return http.Response('unexpected ${request.url}', 500);
          }),
        );

        final posts = await client.fetchUserThreads('instagram');
        expect(posts.first.text, 'via guest after cookie fail');
      },
    );

    test(
      'guest GraphQL still works while a cookie session is cooling down',
      () async {
        await prefs.set(
          optionPluginThreadsDirectCooldownUntil,
          DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        );
        final client = ThreadsDirectClient(
          prefs,
          minGap: Duration.zero,
          httpClient: MockClient((request) async {
            if (request.method == 'GET') {
              return http.Response(
                r'<html><script>["LSD",[],{"token":"tok"}]</script>'
                r'<script>{"username":"zuck","pk":"63055343223"}</script></html>',
                200,
              );
            }
            return http.Response(
              jsonEncode({
                'data': {
                  'mediaData': {
                    'threads': [
                      {
                        'thread_items': [
                          {
                            'post': {
                              'pk': '3',
                              'code': 'z',
                              'caption': {'text': 'during cooldown'},
                              'user': {'username': 'zuck', 'full_name': 'Z'},
                            },
                          },
                        ],
                      },
                    ],
                  },
                },
              }),
              200,
            );
          }),
        );

        final posts = await client.fetchGuestAccount('zuck');
        expect(posts.first.text, 'during cooldown');
      },
    );

    test(
      'fetchGuestAccount falls back to SSR when GraphQL returns empty',
      () async {
        final blob = jsonEncode({
          'thread_items': [
            {
              'post': {
                'pk': '7',
                'code': 'Ss',
                'caption': {'text': 'ssr fallback'},
                'user': {'username': 'zuck', 'full_name': 'Z'},
              },
            },
          ],
        });
        final client = ThreadsDirectClient(
          prefs,
          minGap: Duration.zero,
          httpClient: MockClient((request) async {
            if (request.method == 'GET') {
              return http.Response(
                '<html><script type="application/json" data-sjs>$blob</script>'
                r'<script>["LSD",[],{"token":"t"}]</script>'
                r'<script>{"username":"zuck","pk":"63055343223"}</script></html>',
                200,
              );
            }
            return http.Response(
              jsonEncode({
                'data': {
                  'mediaData': {'threads': []},
                },
              }),
              200,
            );
          }),
        );

        final posts = await client.fetchGuestAccount('zuck');
        expect(posts.first.text, 'ssr fallback');
      },
    );

    test(
      'fetchGuestPostThread scrapes root and replies from a post URL',
      () async {
        final blob = jsonEncode({
          'thread_items': [
            {
              'post': {
                'pk': '1',
                'code': 'Aa',
                'caption': {'text': 'root'},
                'user': {'username': 'zuck', 'full_name': 'Z'},
              },
            },
            {
              'post': {
                'pk': '2',
                'code': 'Bb',
                'caption': {'text': 'reply'},
                'user': {'username': 'other', 'full_name': 'O'},
              },
            },
          ],
        });
        final client = ThreadsDirectClient(
          prefs,
          minGap: Duration.zero,
          httpClient: MockClient((request) async {
            expect(request.url.path, '/@zuck/post/Aa');
            return http.Response(
              '<html><script type="application/json" data-sjs>$blob</script></html>',
              200,
            );
          }),
        );

        final posts = await client.fetchGuestPostThread(
          'https://www.threads.com/@zuck/post/Aa',
        );
        expect(posts.map((p) => p.text), ['root', 'reply']);
      },
    );

    test(
      'pasted cookies do not hit cookie REST unless the reader opts in',
      () async {
        await prefs.set(
          optionPluginThreadsDirectCookies,
          'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g',
        );
        await prefs.set(
          optionPluginThreadsUserIds,
          '{"instagram":"63404918397"}',
        );
        await prefs.set(optionPluginThreadsGuestLsd, 'tok');
        await prefs.set(
          optionPluginThreadsGuestLsdAt,
          DateTime.now().toIso8601String(),
        );
        var textFeed = false;
        final client = ThreadsDirectClient(
          prefs,
          minGap: Duration.zero,
          httpClient: MockClient((request) async {
            if (request.url.path.contains('/text_feed/')) {
              textFeed = true;
              return http.Response('{"threads":[]}', 200);
            }
            if (request.method == 'POST' &&
                request.url.path == '/api/graphql') {
              return http.Response(
                jsonEncode({
                  'data': {
                    'mediaData': {
                      'threads': [
                        {
                          'thread_items': [
                            {
                              'post': {
                                'pk': '1',
                                'code': 'c',
                                'caption': {'text': 'guest'},
                                'user': {
                                  'username': 'instagram',
                                  'full_name': 'IG',
                                },
                              },
                            },
                          ],
                        },
                      ],
                    },
                  },
                }),
                200,
              );
            }
            return http.Response('unexpected ${request.url}', 500);
          }),
        );

        final posts = await client.fetchUserThreads('instagram');
        expect(textFeed, isFalse);
        expect(posts.first.text, 'guest');
      },
    );

    test('cookie REST uses a browser UA, not the official app UA', () async {
      await prefs.set(
        optionPluginThreadsDirectCookies,
        'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g',
      );
      await prefs.set(optionPluginThreadsUseSessionApis, true);
      await prefs.set(optionPluginThreadsUserIds, '{"instagram":"42"}');
      String? ua;
      final client = ThreadsDirectClient(
        prefs,
        minGap: Duration.zero,
        httpClient: MockClient((request) async {
          if (request.url.path.contains('/text_feed/')) {
            ua = request.headers['User-Agent'];
            return http.Response(
              jsonEncode({
                'threads': [
                  {
                    'thread_items': [
                      {
                        'post': {
                          'pk': '1',
                          'code': 'c',
                          'caption': {'text': 'ok'},
                          'user': {'username': 'instagram', 'full_name': 'IG'},
                        },
                      },
                    ],
                  },
                ],
              }),
              200,
            );
          }
          return http.Response('unexpected ${request.url}', 500);
        }),
      );

      await client.fetchUserThreads('instagram');
      expect(ua, contains('Safari'));
      expect(ua, isNot(contains('Barcelona')));
    });
  });
}
