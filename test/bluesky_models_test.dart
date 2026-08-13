import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';

void main() {
  group('normaliseBlueskyAppView', () {
    test('accepts bare hosts and trims trailing slashes', () {
      expect(
        normaliseBlueskyAppView('public.api.bsky.app'),
        'https://public.api.bsky.app',
      );
      expect(
        normaliseBlueskyAppView('https://public.api.bsky.app/'),
        'https://public.api.bsky.app',
      );
      expect(
        normaliseBlueskyAppView('  https://example.org  '),
        'https://example.org',
      );
    });

    test('refuses empty or non-http values', () {
      expect(normaliseBlueskyAppView(''), isNull);
      expect(normaliseBlueskyAppView('ftp://example.org'), isNull);
      expect(normaliseBlueskyAppView('not a url'), isNull);
    });

    test('blueskyAppViewFromPrefs falls back to the working default', () {
      expect(blueskyAppViewFromPrefs(null), kBlueskyDefaultAppView);
      expect(blueskyAppViewFromPrefs(''), kBlueskyDefaultAppView);
      expect(blueskyAppViewFromPrefs('ftp://nope'), kBlueskyDefaultAppView);
      expect(
        blueskyAppViewFromPrefs('https://my.appview.example'),
        'https://my.appview.example',
      );
    });
  });

  group('normaliseBlueskyHandle', () {
    test('accepts bare handles, @handles and profile URLs', () {
      expect(normaliseBlueskyHandle('alice.bsky.social'), 'alice.bsky.social');
      expect(normaliseBlueskyHandle('@Alice.bsky.social'), 'alice.bsky.social');
      expect(
        normaliseBlueskyHandle('  @alice.bsky.social '),
        'alice.bsky.social',
      );
      expect(
        normaliseBlueskyHandle('https://bsky.app/profile/alice.bsky.social'),
        'alice.bsky.social',
      );
      expect(
        normaliseBlueskyHandle(
          'https://www.bsky.app/profile/alice.bsky.social/post/abc',
        ),
        'alice.bsky.social',
      );
    });

    test('keeps dotted custom handles', () {
      expect(normaliseBlueskyHandle('jay.bsky.team'), 'jay.bsky.team');
    });

    test('accepts did:plc identifiers', () {
      expect(
        normaliseBlueskyHandle('did:plc:z72i7hdynmk6r22z27h6tvur'),
        'did:plc:z72i7hdynmk6r22z27h6tvur',
      );
      expect(
        normaliseBlueskyHandle('DID:PLC:z72i7hdynmk6r22z27h6tvur'),
        'did:plc:z72i7hdynmk6r22z27h6tvur',
      );
    });

    test('refuses what is not a handle', () {
      expect(normaliseBlueskyHandle(''), isNull);
      expect(normaliseBlueskyHandle('   '), isNull);
      expect(normaliseBlueskyHandle('@'), isNull);
      expect(normaliseBlueskyHandle('nodot'), isNull);
      expect(normaliseBlueskyHandle('two words.bsky.social'), isNull);
      expect(normaliseBlueskyHandle('https://example.org/nothing'), isNull);
      expect(normaliseBlueskyHandle('did:web:example.com'), isNull);
    });
  });

  group('blueskyWebUrl / blueskyRkeyOf', () {
    test('builds a bsky.app post URL from handle and at:// URI', () {
      expect(
        blueskyWebUrl(
          handle: 'bsky.app',
          atUri:
              'at://did:plc:z72i7hdynmk6r22z27h6tvur/app.bsky.feed.post/3mqafridzgk2e',
        ),
        'https://bsky.app/profile/bsky.app/post/3mqafridzgk2e',
      );
    });

    test('returns null when the URI has no rkey', () {
      expect(blueskyRkeyOf('not-an-at-uri'), isNull);
      expect(
        blueskyWebUrl(
          handle: 'bsky.app',
          atUri: 'at://did:plc:x/app.bsky.feed.post',
        ),
        isNull,
      );
    });
  });

  group('parseBlueskyFeed', () {
    test('reads text, author, images and date off a feed item', () {
      final posts = parseBlueskyFeed({
        'feed': [
          {
            'post': {
              'uri': 'at://did:plc:abc/app.bsky.feed.post/rkey1',
              'cid': 'bafyreiabc',
              'author': {
                'did': 'did:plc:abc',
                'handle': 'alice.bsky.social',
                'displayName': 'Alice',
                'avatar': 'https://example.org/a.jpg',
              },
              'record': {
                '\$type': 'app.bsky.feed.post',
                'text': 'Hello from Bluesky',
                'createdAt': '2026-08-01T09:00:00.000Z',
              },
              'embed': {
                '\$type': 'app.bsky.embed.images#view',
                'images': [
                  {
                    'thumb': 'https://example.org/thumb.jpg',
                    'fullsize': 'https://example.org/full.jpg',
                  },
                ],
              },
            },
          },
        ],
      });

      expect(posts, hasLength(1));
      final post = posts.first;
      expect(post.text, 'Hello from Bluesky');
      expect(post.handle, 'alice.bsky.social');
      expect(post.did, 'did:plc:abc');
      expect(post.authorName, 'Alice');
      expect(post.images, ['https://example.org/full.jpg']);
      expect(post.imageAspects, [null]);
      expect(post.imageIsVideo, [false]);
      expect(post.url, 'https://bsky.app/profile/alice.bsky.social/post/rkey1');
      expect(post.publishedAt, isNotNull);
    });

    test('drops empty items and tolerates a reshaped feed', () {
      expect(parseBlueskyFeed(null), isEmpty);
      expect(parseBlueskyFeed({'feed': 'nope'}), isEmpty);
      expect(
        parseBlueskyFeed({
          'feed': [
            {
              'post': {
                'uri': 'at://did:plc:abc/app.bsky.feed.post/empty',
                'author': {'handle': 'alice.bsky.social'},
                'record': {'text': '   '},
              },
            },
          ],
        }),
        isEmpty,
      );
    });

    test('keeps engagement counts and a repost reason', () {
      final posts = parseBlueskyFeed({
        'feed': [
          {
            'post': {
              'uri': 'at://did:plc:abc/app.bsky.feed.post/r1',
              'author': {'handle': 'alice.bsky.social', 'displayName': 'Alice'},
              'record': {'text': 'Hi', 'createdAt': '2026-08-01T09:00:00.000Z'},
              'replyCount': 2,
              'repostCount': 3,
              'likeCount': 4,
              'quoteCount': 1,
            },
            'reason': {
              '\$type': 'app.bsky.feed.defs#reasonRepost',
              'by': {'handle': 'bob.bsky.social', 'displayName': 'Bob'},
            },
          },
        ],
      });

      expect(posts.single.replyCount, 2);
      expect(posts.single.likeCount, 4);
      expect(posts.single.isRepost, isTrue);
      expect(posts.single.repostedByName, 'Bob');
      expect(posts.single.repostedByHandle, 'bob.bsky.social');
    });

    test('reads a quote embed and an external link card', () {
      final posts = parseBlueskyFeed({
        'feed': [
          {
            'post': {
              'uri': 'at://did:plc:abc/app.bsky.feed.post/q1',
              'author': {'handle': 'alice.bsky.social', 'displayName': 'Alice'},
              'record': {
                'text': 'Look',
                'createdAt': '2026-08-01T09:00:00.000Z',
              },
              'embed': {
                '\$type': 'app.bsky.embed.recordWithMedia#view',
                'record': {
                  'record': {
                    '\$type': 'app.bsky.embed.record#viewRecord',
                    'uri': 'at://did:plc:xyz/app.bsky.feed.post/orig',
                    'cid': 'bafy',
                    'author': {
                      'handle': 'carol.bsky.social',
                      'displayName': 'Carol',
                    },
                    'value': {
                      'text': 'Quoted body',
                      'createdAt': '2026-08-01T08:00:00.000Z',
                    },
                  },
                },
                'media': {
                  '\$type': 'app.bsky.embed.external#view',
                  'external': {
                    'uri': 'https://example.org/story',
                    'title': 'A story',
                    'description': 'About things',
                    'thumb': 'https://example.org/t.jpg',
                  },
                },
              },
            },
          },
        ],
      });

      expect(posts, hasLength(1));
      expect(posts.single.quotedPost?.text, 'Quoted body');
      expect(posts.single.quotedPost?.handle, 'carol.bsky.social');
      expect(posts.single.linkCard?.url, 'https://example.org/story');
      expect(posts.single.linkCard?.title, 'A story');
    });

    test('prefers fullsize, keeps aspect, and reads a video thumb', () {
      final posts = parseBlueskyFeed({
        'feed': [
          {
            'post': {
              'uri': 'at://did:plc:abc/app.bsky.feed.post/vid',
              'author': {'handle': 'alice.bsky.social'},
              'record': {
                'text': 'Watch',
                'createdAt': '2026-08-01T09:00:00.000Z',
              },
              'embed': {
                '\$type': 'app.bsky.embed.video#view',
                'thumbnail': 'https://example.org/video-thumb.jpg',
                'aspectRatio': {'width': 1920, 'height': 1080},
              },
            },
          },
          {
            'post': {
              'uri': 'at://did:plc:abc/app.bsky.feed.post/img',
              'author': {'handle': 'alice.bsky.social'},
              'record': {
                'text': 'Pic',
                'createdAt': '2026-08-01T09:00:00.000Z',
              },
              'embed': {
                '\$type': 'app.bsky.embed.images#view',
                'images': [
                  {
                    'thumb': 'https://example.org/t.jpg',
                    'fullsize': 'https://example.org/f.jpg',
                    'aspectRatio': {'width': 2200, 'height': 1312},
                  },
                ],
              },
            },
          },
        ],
      });

      expect(posts, hasLength(2));
      expect(posts.first.images, ['https://example.org/video-thumb.jpg']);
      expect(posts.first.imageIsVideo.single, isTrue);
      expect(posts.first.imageAspects.single, closeTo(1920 / 1080, 0.001));
      expect(posts.last.images, ['https://example.org/f.jpg']);
      expect(posts.last.imageAspects.single, closeTo(2200 / 1312, 0.001));
    });

    test('reads reply parent handle from a feed item', () {
      final posts = parseBlueskyFeed({
        'feed': [
          {
            'post': {
              'uri': 'at://did:plc:abc/app.bsky.feed.post/r',
              'author': {'handle': 'alice.bsky.social'},
              'record': {
                'text': 'yes',
                'createdAt': '2026-08-01T09:00:00.000Z',
                'reply': {
                  'parent': {'uri': 'at://did:plc:xyz/app.bsky.feed.post/p'},
                },
              },
            },
            'reply': {
              'parent': {
                'author': {'handle': 'bob.bsky.social', 'displayName': 'Bob'},
              },
            },
          },
        ],
      });

      expect(posts.single.isReply, isTrue);
      expect(posts.single.replyToHandle, 'bob.bsky.social');
    });

    test('quoted viewRecord keeps embeds media', () {
      final posts = parseBlueskyFeed({
        'feed': [
          {
            'post': {
              'uri': 'at://did:plc:abc/app.bsky.feed.post/q',
              'author': {'handle': 'alice.bsky.social'},
              'record': {
                'text': 'Quote',
                'createdAt': '2026-08-01T09:00:00.000Z',
              },
              'embed': {
                '\$type': 'app.bsky.embed.record#view',
                'record': {
                  '\$type': 'app.bsky.embed.record#viewRecord',
                  'uri': 'at://did:plc:xyz/app.bsky.feed.post/orig',
                  'author': {'handle': 'carol.bsky.social'},
                  'value': {
                    'text': 'Quoted',
                    'createdAt': '2026-08-01T08:00:00.000Z',
                  },
                  'embeds': [
                    {
                      '\$type': 'app.bsky.embed.images#view',
                      'images': [
                        {
                          'fullsize': 'https://example.org/quoted.jpg',
                          'thumb': 'https://example.org/quoted-t.jpg',
                        },
                      ],
                    },
                  ],
                },
              },
            },
          },
        ],
      });

      expect(posts.single.quotedPost?.images, [
        'https://example.org/quoted.jpg',
      ]);
    });
  });

  group('parseBlueskyThread', () {
    test('flattens parents and nested replies', () {
      final thread = parseBlueskyThread({
        'thread': {
          '\$type': 'app.bsky.feed.defs#threadViewPost',
          'post': {
            'uri': 'at://did:plc:a/app.bsky.feed.post/focal',
            'author': {'handle': 'alice.bsky.social'},
            'record': {
              'text': 'focal',
              'createdAt': '2026-08-01T10:00:00.000Z',
            },
          },
          'parent': {
            '\$type': 'app.bsky.feed.defs#threadViewPost',
            'post': {
              'uri': 'at://did:plc:a/app.bsky.feed.post/root',
              'author': {'handle': 'alice.bsky.social'},
              'record': {
                'text': 'root',
                'createdAt': '2026-08-01T09:00:00.000Z',
              },
            },
          },
          'replies': [
            {
              '\$type': 'app.bsky.feed.defs#threadViewPost',
              'post': {
                'uri': 'at://did:plc:b/app.bsky.feed.post/r1',
                'author': {'handle': 'bob.bsky.social'},
                'record': {
                  'text': 'reply',
                  'createdAt': '2026-08-01T11:00:00.000Z',
                },
              },
              'replies': [
                {
                  '\$type': 'app.bsky.feed.defs#threadViewPost',
                  'post': {
                    'uri': 'at://did:plc:c/app.bsky.feed.post/r2',
                    'author': {'handle': 'carol.bsky.social'},
                    'record': {
                      'text': 'nested',
                      'createdAt': '2026-08-01T12:00:00.000Z',
                    },
                  },
                },
              ],
            },
          ],
        },
      });

      expect(thread, isNotNull);
      expect(thread!.post.text, 'focal');
      expect(thread.ancestors.map((p) => p.text), ['root']);
      expect(thread.replies.map((p) => p.text), ['reply', 'nested']);
    });
  });

  group('BlueskyProfile.fromJson', () {
    test('reads documented fields defensively', () {
      final profile = BlueskyProfile.fromJson({
        'did': 'did:plc:abc',
        'handle': 'alice.bsky.social',
        'displayName': 'Alice',
        'avatar': 'https://example.org/a.jpg',
        'description': 'Hi',
        'followersCount': 12,
        'followsCount': 3,
        'postsCount': 40,
      });

      expect(profile.did, 'did:plc:abc');
      expect(profile.handle, 'alice.bsky.social');
      expect(profile.displayName, 'Alice');
      expect(profile.followersCount, 12);
      expect(profile.toAccount().actor, 'did:plc:abc');
    });
  });

  group('parseBlueskyListRef', () {
    test('reads bsky.app list URLs and AT-URIs', () {
      final web = parseBlueskyListRef(
        'https://bsky.app/profile/alice.bsky.social/lists/3abc',
      );
      expect(web?.actor, 'alice.bsky.social');
      expect(web?.rkey, '3abc');

      final at = parseBlueskyListRef(
        'at://did:plc:abc/app.bsky.graph.list/3abc',
      );
      expect(at?.atUri, 'at://did:plc:abc/app.bsky.graph.list/3abc');
      expect(
        parseBlueskyListRef('https://bsky.app/profile/alice.bsky.social'),
        isNull,
      );
    });
  });

  group('graph page parsers', () {
    test('parseBlueskyFollowsPage reads follows and cursor', () {
      final page = parseBlueskyFollowsPage({
        'cursor': 'next',
        'follows': [
          {
            'did': 'did:plc:1',
            'handle': 'one.bsky.social',
            'displayName': 'One',
          },
        ],
      });
      expect(page.cursor, 'next');
      expect(page.follows.single.handle, 'one.bsky.social');
    });

    test('parseBlueskyListMembersPage reads subjects', () {
      final page = parseBlueskyListMembersPage({
        'list': {
          'uri': 'at://did:plc:a/app.bsky.graph.list/1',
          'name': 'Cool',
          'listItemCount': 2,
        },
        'items': [
          {
            'subject': {'did': 'did:plc:1', 'handle': 'one.bsky.social'},
          },
        ],
      });
      expect(page.list?.name, 'Cool');
      expect(page.members.single.handle, 'one.bsky.social');
    });

    test('parseBlueskyFollowersPage reads followers and cursor', () {
      final page = parseBlueskyFollowersPage({
        'cursor': 'c2',
        'followers': [
          {'did': 'did:plc:2', 'handle': 'two.bsky.social'},
        ],
      });
      expect(page.cursor, 'c2');
      expect(page.followers.single.handle, 'two.bsky.social');
    });
  });
}
