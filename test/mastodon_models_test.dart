import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';

void main() {
  group('normaliseMastodonInstance', () {
    test('adds https and strips a trailing slash', () {
      expect(
        normaliseMastodonInstance('mastodon.social'),
        'https://mastodon.social',
      );
      expect(
        normaliseMastodonInstance('https://mastodon.social/'),
        'https://mastodon.social',
      );
      expect(
        normaliseMastodonInstance('http://localhost:3000'),
        'http://localhost:3000',
      );
    });

    test('refuses empty or scheme-less garbage', () {
      expect(normaliseMastodonInstance(''), isNull);
      expect(normaliseMastodonInstance('   '), isNull);
      expect(normaliseMastodonInstance('not a host'), isNull);
    });
  });

  group('normaliseMastodonAcct', () {
    test('accepts bare users, @users, user@domain and profile URLs', () {
      expect(normaliseMastodonAcct('Gargron'), 'gargron');
      expect(normaliseMastodonAcct('@Gargron'), 'gargron');
      expect(
        normaliseMastodonAcct('dansup@pixelfed.social'),
        'dansup@pixelfed.social',
      );
      expect(
        normaliseMastodonAcct('https://mastodon.social/@Gargron'),
        'gargron@mastodon.social',
      );
      expect(
        normaliseMastodonAcct('https://mastodon.social/users/Gargron'),
        'gargron@mastodon.social',
      );
    });

    test('refuses what is not an address', () {
      expect(normaliseMastodonAcct(''), isNull);
      expect(normaliseMastodonAcct('@'), isNull);
      expect(normaliseMastodonAcct('two words'), isNull);
      expect(normaliseMastodonAcct('user@nodot'), isNull);
    });
  });

  group('canonicalMastodonAcct', () {
    test('attaches the home domain to a local username', () {
      expect(
        canonicalMastodonAcct('Gargron', homeDomain: 'mastodon.social'),
        'gargron@mastodon.social',
      );
      expect(
        canonicalMastodonAcct(
          'dansup@pixelfed.social',
          homeDomain: 'mastodon.social',
        ),
        'dansup@pixelfed.social',
      );
    });
  });

  group('mastodonStatusIdFromUrl', () {
    test('reads /@user/id and /users/user/statuses/id', () {
      expect(
        mastodonStatusIdFromUrl(
          'https://flipboard.social/@newsguyusa/117055372780611166',
        ),
        '117055372780611166',
      );
      expect(
        mastodonStatusIdFromUrl(
          'https://fosstodon.org/users/mariatta/statuses/117055260382620938',
        ),
        '117055260382620938',
      );
    });

    test('ignores profile URLs and junk', () {
      expect(
        mastodonStatusIdFromUrl('https://flipboard.social/@newsguyusa'),
        isNull,
      );
      expect(mastodonStatusIdFromUrl('not a url'), isNull);
    });
  });

  group('sameMastodonStatusUrl', () {
    test('treats trailing slashes and host case as the same post', () {
      expect(
        sameMastodonStatusUrl(
          'https://Flipboard.social/@newsguyusa/117055372780611166/',
          'https://flipboard.social/@newsguyusa/117055372780611166',
        ),
        isTrue,
      );
      expect(
        sameMastodonStatusUrl(
          'https://flipboard.social/@newsguyusa/1',
          'https://flipboard.social/@newsguyusa/2',
        ),
        isFalse,
      );
    });
  });

  group('parseMastodonStatuses', () {
    test(
      'reads text, author, images, counts, link card and unwraps a boost',
      () {
        final posts = parseMastodonStatuses([
          {
            'id': '1',
            'created_at': '2026-08-01T09:00:00.000Z',
            'content': '<p>Hello <br>there</p>',
            'url': 'https://mastodon.social/@a/1',
            'replies_count': 3,
            'reblogs_count': 12,
            'favourites_count': 40,
            'card': {
              'url': 'https://example.org/article',
              'title': 'An article',
              'description': 'About things',
              'image': 'https://example.org/og.jpg',
              'type': 'link',
              'provider_name': 'Example',
            },
            'account': {
              'id': '10',
              'username': 'alice',
              'acct': 'alice',
              'display_name': 'Alice',
              'avatar': 'https://example.org/a.jpg',
              'note': '',
              'url': 'https://mastodon.social/@alice',
              'followers_count': 1,
              'following_count': 2,
              'statuses_count': 3,
            },
            'media_attachments': [
              {
                'type': 'image',
                'preview_url': 'https://example.org/thumb.jpg',
                'url': 'https://example.org/full.jpg',
              },
              {'type': 'video', 'preview_url': 'https://example.org/v.jpg'},
            ],
            'reblog': null,
          },
          {
            'id': '2',
            'created_at': '2026-08-01T10:00:00.000Z',
            'content': '',
            'url': 'https://mastodon.social/@a/2',
            'account': {
              'id': '10',
              'username': 'alice',
              'acct': 'alice',
              'display_name': 'Alice',
              'note': '',
              'url': 'https://mastodon.social/@alice',
            },
            'media_attachments': [],
            'reblog': {
              'id': '99',
              'created_at': '2026-08-01T08:00:00.000Z',
              'content': '<p>Boosted</p>',
              'url': 'https://other.social/@bob/99',
              'spoiler_text': '',
              'replies_count': 1,
              'reblogs_count': 2,
              'favourites_count': 5,
              'account': {
                'id': '20',
                'username': 'bob',
                'acct': 'bob@other.social',
                'display_name': 'Bob',
                'note': '',
                'url': 'https://other.social/@bob',
              },
              'media_attachments': [],
            },
          },
        ], homeDomain: 'mastodon.social');

        expect(posts, hasLength(2));
        expect(posts.first.text, 'Hello \nthere');
        expect(posts.first.acct, 'alice@mastodon.social');
        expect(posts.first.images, [
          'https://example.org/full.jpg',
          'https://example.org/v.jpg',
        ]);
        expect(posts.first.imageIsVideo, [false, true]);
        expect(posts.first.boosted, isFalse);
        expect(posts.first.repliesCount, 3);
        expect(posts.first.reblogsCount, 12);
        expect(posts.first.favouritesCount, 40);
        expect(posts.first.linkCard?.title, 'An article');
        expect(posts.first.linkCard?.imageUrl, 'https://example.org/og.jpg');
        expect(posts.first.linkCard?.providerName, 'Example');

        expect(posts.last.id, '99');
        expect(posts.last.text, 'Boosted');
        expect(posts.last.acct, 'bob@other.social');
        expect(posts.last.boosted, isTrue);
        expect(posts.last.favouritesCount, 5);
      },
    );

    test('keeps a link-only status that has no text or images', () {
      final posts = parseMastodonStatuses([
        {
          'id': '7',
          'content': '',
          'url': 'https://mastodon.social/@a/7',
          'account': {
            'id': '10',
            'username': 'alice',
            'acct': 'alice',
            'display_name': 'Alice',
            'note': '',
            'url': 'https://mastodon.social/@alice',
          },
          'media_attachments': [],
          'card': {
            'url': 'https://news.example/story',
            'title': 'Story',
            'description': '',
            'image': 'https://news.example/cover.jpg',
            'type': 'link',
          },
        },
      ], homeDomain: 'mastodon.social');

      expect(posts, hasLength(1));
      expect(posts.single.linkCard?.url, 'https://news.example/story');
      expect(posts.single.hasMedia, isFalse);
    });

    test('reads reply target from mentions and image aspect', () {
      final posts = parseMastodonStatuses([
        {
          'id': '8',
          'content': '<p>yes</p>',
          'url': 'https://mastodon.social/@a/8',
          'in_reply_to_id': '7',
          'in_reply_to_account_id': '20',
          'account': {
            'id': '10',
            'username': 'alice',
            'acct': 'alice',
            'display_name': 'Alice',
            'note': '',
            'url': 'https://mastodon.social/@alice',
          },
          'mentions': [
            {'id': '20', 'username': 'bob', 'acct': 'bob@other.social'},
          ],
          'media_attachments': [
            {
              'type': 'image',
              'url': 'https://example.org/wide.jpg',
              'preview_url': 'https://example.org/wide-t.jpg',
              'meta': {
                'original': {'width': 1600, 'height': 900, 'aspect': 1.777},
              },
            },
          ],
        },
      ], homeDomain: 'mastodon.social');

      expect(posts.single.isReply, isTrue);
      expect(posts.single.replyToAcct, 'bob@other.social');
      expect(posts.single.images, ['https://example.org/wide.jpg']);
      expect(posts.single.imageAspects.single, closeTo(1.777, 0.001));
    });

    test('drops empty items and tolerates a reshaped payload', () {
      expect(parseMastodonStatuses(null), isEmpty);
      expect(parseMastodonStatuses('nope'), isEmpty);
      expect(
        parseMastodonStatuses([
          {
            'id': '1',
            'content': '   ',
            'account': {'acct': 'alice', 'username': 'alice'},
            'media_attachments': [],
          },
        ]),
        isEmpty,
      );
    });
  });

  group('MastodonProfile.fromJson', () {
    test('reads documented fields and becomes a followable account', () {
      final profile = MastodonProfile.fromJson({
        'id': '1',
        'username': 'Gargron',
        'acct': 'Gargron',
        'display_name': 'Eugen',
        'avatar': 'https://example.org/a.jpg',
        'note': '<p>Building</p>',
        'url': 'https://mastodon.social/@Gargron',
        'followers_count': 10,
        'following_count': 2,
        'statuses_count': 40,
        'locked': false,
      }, homeDomain: 'mastodon.social');

      expect(profile.acct, 'gargron@mastodon.social');
      expect(profile.displayName, 'Eugen');
      expect(profile.note, 'Building');
      expect(profile.toAccount().acct, 'gargron@mastodon.social');
    });
  });
  group('parseMastodonTrendingTags', () {
    test('sums uses across history days', () {
      final tags = parseMastodonTrendingTags([
        {
          'name': 'flutter',
          'url': 'https://mastodon.social/tags/flutter',
          'history': [
            {'day': '1', 'uses': '3', 'accounts': '2'},
            {'day': '2', 'uses': '5', 'accounts': '4'},
          ],
        },
        {'name': '', 'history': []},
      ]);
      expect(tags, hasLength(1));
      expect(tags.first.name, 'flutter');
      expect(tags.first.uses, 8);
    });
  });
}
