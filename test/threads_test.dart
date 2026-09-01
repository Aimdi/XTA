import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/threads/threads_api.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/utils/json.dart';

Json _feed(List<Map<String, Object?>> items, {String title = 'zuck - Threads'}) => Json({
      'version': 'https://jsonfeed.org/version/1.1',
      'title': title,
      'icon': 'https://example.org/avatar.jpg',
      'items': items,
    });

void main() {
  group('unwrapThreadsOutboundUrl', () {
    test('unwraps l.threads.com redirects and leaves other URLs alone', () {
      expect(
        unwrapThreadsOutboundUrl('https://l.threads.com/?u=https%3A%2F%2Fexample.org%2Fstory'),
        'https://example.org/story',
      );
      expect(unwrapThreadsOutboundUrl('https://example.org/direct'), 'https://example.org/direct');
    });
  });

  group('normaliseThreadsHandle', () {
    test('takes a bare handle, an @handle and a profile link alike', () {
      expect(normaliseThreadsHandle('zuck'), 'zuck');
      expect(normaliseThreadsHandle('@zuck'), 'zuck');
      expect(normaliseThreadsHandle('  @Zuck '), 'zuck');
      expect(normaliseThreadsHandle('https://www.threads.com/@zuck'), 'zuck');
      expect(normaliseThreadsHandle('https://www.threads.net/@zuck/post/abc'), 'zuck');
    });

    test('keeps the dots and underscores handles are allowed', () {
      expect(normaliseThreadsHandle('@some.one_else'), 'some.one_else');
    });

    test('refuses what is not a handle at all', () {
      expect(normaliseThreadsHandle(''), isNull);
      expect(normaliseThreadsHandle('   '), isNull);
      expect(normaliseThreadsHandle('@'), isNull);
      expect(normaliseThreadsHandle('two words'), isNull);
      expect(normaliseThreadsHandle('https://example.org/nothing'), isNull);
    });
  });

  group('feedUri', () {
    test('asks the instance for JSON, whatever trailing slashes it was given', () {
      expect(ThreadsClient.feedUri('https://rsshub.example.org', 'zuck').toString(),
          'https://rsshub.example.org/threads/zuck?format=json');
      expect(ThreadsClient.feedUri('https://rsshub.example.org///', 'zuck').toString(),
          'https://rsshub.example.org/threads/zuck?format=json');
    });
  });

  group('parseThreadsFeed', () {
    test('reads text, link and date off an item', () {
      final posts = parseThreadsFeed(
        _feed([
          {
            'id': 'https://www.threads.com/@zuck/post/1',
            'url': 'https://www.threads.com/@zuck/post/1',
            'content_html': '<p>Hello there</p>',
            'date_published': '2026-08-01T09:00:00.000Z',
          }
        ]),
        'zuck',
      );

      expect(posts, hasLength(1));
      expect(posts.first.text, 'Hello there');
      expect(posts.first.handle, 'zuck');
      expect(posts.first.url, 'https://www.threads.com/@zuck/post/1');
      expect(posts.first.publishedAt, isNotNull);
    });

    test('takes the author from the feed title, without the site suffix', () {
      final posts = parseThreadsFeed(
        _feed([
          {'id': '1', 'content_text': 'x'}
        ], title: 'Mark Zuckerberg - Threads'),
        'zuck',
      );

      expect(posts.first.authorName, 'Mark Zuckerberg');
    });

    test('falls back to the handle when the feed titles itself uselessly', () {
      final posts = parseThreadsFeed(
        _feed([
          {'id': '1', 'content_text': 'x'}
        ], title: ''),
        'zuck',
      );

      expect(posts.first.authorName, 'zuck');
    });

    test('collects the pictures the html embeds and the attachments declare', () {
      final posts = parseThreadsFeed(
        _feed([
          {
            'id': '1',
            'content_html': '<p>look</p><img src="https://example.org/a.jpg">',
            'attachments': [
              {'url': 'https://example.org/b.jpg', 'mime_type': 'image/jpeg'},
              {'url': 'https://example.org/c.mp4', 'mime_type': 'video/mp4'},
              {'url': 'https://example.org/a.jpg', 'mime_type': 'image/jpeg'},
            ],
          }
        ]),
        'zuck',
      );

      expect(posts.first.images, ['https://example.org/a.jpg', 'https://example.org/b.jpg'],
          reason: 'in order, no repeats, and a video is not a picture');
    });

    test('keeps a picture-only post but drops an empty one', () {
      final posts = parseThreadsFeed(
        _feed([
          {
            'id': '1',
            'attachments': [
              {'url': 'https://example.org/a.jpg', 'mime_type': 'image/jpeg'}
            ],
          },
          {'id': '2', 'content_html': '<p>   </p>'},
        ]),
        'zuck',
      );

      expect(posts, hasLength(1));
      expect(posts.first.id, '1');
    });

    test('a reshaped feed yields nothing rather than throwing', () {
      expect(parseThreadsFeed(const Json(null), 'zuck'), isEmpty);
      expect(parseThreadsFeed(const Json({'items': 'not a list'}), 'zuck'), isEmpty);
    });
  });

  group('ThreadsProfile.fromJson', () {
    const live = {
      'pk': '314216',
      'id': '314216',
      'username': 'zuck',
      'full_name': 'Mark Zuckerberg',
      'is_verified': true,
      'is_private': false,
      'profile_pic_url': 'https://example.org/zuck.jpg',
      'biography': 'Building things',
      'follower_count': 4200000,
      'following_count': 812,
      'media_count': 1330,
      'external_url': 'https://example.org',
    };

    test('reads every documented field', () {
      final profile = ThreadsProfile.fromJson(live);

      expect(profile.pk, '314216');
      expect(profile.username, 'zuck');
      expect(profile.fullName, 'Mark Zuckerberg');
      expect(profile.isVerified, isTrue);
      expect(profile.isPrivate, isFalse);
      expect(profile.followerCount, 4200000);
      expect(profile.followingCount, 812);
      expect(profile.mediaCount, 1330);
      expect(profile.externalUrl, 'https://example.org');
    });

    test('external_url is nullable, and blank counts as absent', () {
      expect(ThreadsProfile.fromJson({...live, 'external_url': null}).externalUrl, isNull);
      expect(ThreadsProfile.fromJson({...live, 'external_url': '  '}).externalUrl, isNull);
    });

    test('a field that stopped being sent leaves a blank, not a crash', () {
      final profile = ThreadsProfile.fromJson({'username': 'zuck'});

      expect(profile.username, 'zuck');
      expect(profile.fullName, '');
      expect(profile.isVerified, isFalse);
      expect(profile.followerCount, 0);
      // With no display name the handle stands in for it.
      expect(profile.displayName, 'zuck');
    });

    test('a reshaped response yields an empty profile rather than throwing', () {
      expect(ThreadsProfile.fromJson(null).username, '');
      expect(ThreadsProfile.fromJson('not an object').username, '');
    });

    test('a profile becomes a followable account, carrying its face', () {
      final account = ThreadsProfile.fromJson(live).toAccount();

      expect(account.handle, 'zuck');
      expect(account.name, 'Mark Zuckerberg');
      expect(account.avatarUrl, 'https://example.org/zuck.jpg');
    });
  });

  group('ThreadsApi', () {
    test('builds endpoints under the configured base, trailing slashes and all', () {
      expect(ThreadsApi.endpoint('https://xy.example.org', '/health').toString(),
          'https://xy.example.org/health');
      expect(ThreadsApi.endpoint('https://xy.example.org//', '/profile/zuck').toString(),
          'https://xy.example.org/profile/zuck');
    });
  });
}
