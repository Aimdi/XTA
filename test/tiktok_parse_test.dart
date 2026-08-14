import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/tiktok/tiktok_parse.dart';

const _profileHtml = '''
<html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
{"__DEFAULT_SCOPE__":{"webapp.user-detail":{"userInfo":{"user":{"id":"1","secUid":"MS4wLjABAAAA","uniqueId":"tiktok","nickname":"TikTok","signature":"hello","avatarThumb":"https://p16.tiktokcdn.com/a.jpg","privateAccount":false,"verified":true},"stats":{"followerCount":10,"followingCount":2,"videoCount":3,"heartCount":99}}}}}
</script></html>
''';

const _videoHtml = '''
<html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
{"__DEFAULT_SCOPE__":{"webapp.video-detail":{"itemInfo":{"itemStruct":{"id":"123","desc":"clip","createTime":1700000000,"author":{"uniqueId":"tiktok","nickname":"TikTok","secUid":"MS4wLjABAAAA","avatarThumb":"https://p16.tiktokcdn.com/a.jpg"},"stats":{"diggCount":1,"commentCount":2,"shareCount":3,"playCount":4},"video":{"playAddr":{"urlList":["https://v16-webapp-prime.us.tiktok.com/play.mp4"]},"cover":"https://p16.tiktokcdn.com/c.jpg","duration":12500,"width":720,"height":1280}}}}}}
</script></html>
''';

void main() {
  test('normaliseTikTokHandle strips @ and rejects junk', () {
    expect(normaliseTikTokHandle('@TikTok'), 'tiktok');
    expect(normaliseTikTokHandle(' some.user_1 '), 'some.user_1');
    expect(normaliseTikTokHandle('ab'), 'ab');
    expect(normaliseTikTokHandle('a'), isNull);
    expect(normaliseTikTokHandle('https://www.tiktok.com/@x'), isNull);
    expect(normaliseTikTokHandle('https://www.tiktok.com/@tiktok'), 'tiktok');
    expect(
      normaliseTikTokHandle('https://www.tiktok.com/@tiktok/video/123'),
      'tiktok',
    );
    expect(normaliseTikTokHandle('https://vm.tiktok.com/ZMxxx'), isNull);
    expect(normaliseTikTokHandle('https://vt.tiktok.com/ZMxxx'), isNull);
  });

  test('parseTikTokProfileHtml reads user-detail rehydration', () {
    final profile = parseTikTokProfileHtml(_profileHtml);
    expect(profile, isNotNull);
    expect(profile!.uniqueId, 'tiktok');
    expect(profile.secUid, 'MS4wLjABAAAA');
    expect(profile.nickname, 'TikTok');
    expect(profile.followerCount, 10);
    expect(profile.verified, isTrue);
    expect(profile.privateAccount, isFalse);
    expect(profile.avatarUrl, contains('tiktokcdn'));
  });

  test('HTML entities in rehydration JSON are unescaped', () {
    final bodyStart = _profileHtml.indexOf('\n') + 1;
    final bodyEnd = _profileHtml.lastIndexOf('\n');
    final escapedHtml =
        _profileHtml.substring(0, bodyStart) +
        _profileHtml.substring(bodyStart, bodyEnd).replaceAll('"', '&quot;') +
        _profileHtml.substring(bodyEnd);
    final profile = parseTikTokProfileHtml(escapedHtml);
    expect(profile, isNotNull);
    expect(profile!.uniqueId, 'tiktok');
  });

  test('private account is parsed without throwing', () {
    final profile = parseTikTokProfileHtml(
      _profileHtml.replaceFirst(
        '"privateAccount":false',
        '"privateAccount":true',
      ),
    );
    expect(profile, isNotNull);
    expect(profile!.privateAccount, isTrue);
  });

  test('parseTikTokVideoHtml reads object playAddr', () {
    final post = parseTikTokVideoHtml(_videoHtml);
    expect(post, isNotNull);
    expect(post!.id, '123');
    expect(post.desc, 'clip');
    expect(post.author.uniqueId, 'tiktok');
    expect(post.playUrl, startsWith('https://v16-webapp-prime'));
    // Millisecond duration 12500 is converted to seconds and rounded to 13.
    expect(post.durationSeconds, 13);
    expect(post.aspectRatio, closeTo(720 / 1280, 0.001));
  });

  test('parseTikTokItemList reads unsigned creator list', () {
    final page = parseTikTokItemList({
      'statusCode': 0,
      'hasMorePrevious': true,
      'cursor': 987654,
      'itemList': [
        {
          'id': '9',
          'desc': 'hi',
          'createTime': 1700000000,
          'author': {'uniqueId': 'bob', 'nickname': 'Bob'},
          'stats': {'diggCount': 5},
          'video': {
            'playAddr': 'https://v16.tiktokcdn.com/a.mp4',
            'bitrateInfo': [
              {
                'PlayAddr': {
                  'UrlKey': 'id_h264_720p_1',
                  'UrlList': ['https://v16.tiktokcdn.com/720.mp4'],
                },
              },
            ],
            'cover': 'https://p16.tiktokcdn.com/c.jpg',
            'width': 1080,
            'height': 1920,
            'duration': 8,
          },
        },
      ],
    });
    expect(page.statusCode, 0);
    expect(page.hasMore, isTrue);
    expect(page.posts, hasLength(1));
    expect(page.posts.single.author.uniqueId, 'bob');
    expect(page.posts.single.sources.first.label, '720p');
    expect(page.cursor, '987654');
  });

  test('empty itemList preserves hasMore', () {
    final page = parseTikTokItemList({
      'statusCode': 0,
      'hasMore': true,
      'itemList': [],
    });
    expect(page.posts, isEmpty);
    expect(page.hasMore, isTrue);
    expect(page.cursor, isNull);
  });

  test('imagePost cover is used when video cover is missing', () {
    final page = parseTikTokItemList({
      'statusCode': 0,
      'itemList': [
        {
          'id': 'photo-1',
          'author': {'uniqueId': 'photo', 'nickname': 'Photo'},
          'imagePost': {
            'cover': {
              'imageURL': {
                'urlList': ['https://p16.tiktokcdn.com/cover.jpg'],
              },
            },
            'images': [],
          },
          'video': {},
        },
      ],
    });
    expect(page.posts.single.coverUrl, 'https://p16.tiktokcdn.com/cover.jpg');
    expect(page.posts.single.isPhoto, isTrue);
  });

  test('drops www.tiktok.com play URLs and missing fields', () {
    final page = parseTikTokItemList({
      'statusCode': 0,
      'itemList': [
        {
          'id': '1',
          'author': {'uniqueId': 'a', 'nickname': 'A'},
          'video': {'playAddr': 'https://www.tiktok.com/aweme/v1/play/'},
        },
        {'id': '2'},
      ],
    });
    expect(page.posts, hasLength(1));
    expect(page.posts.single.playUrl, isNull);
  });

  test('status 10201 is preserved for the client', () {
    final page = parseTikTokItemList({'statusCode': 10201, 'itemList': []});
    expect(page.statusCode, 10201);
    expect(page.posts, isEmpty);
  });

  test('missing rehydration is null, not an exception', () {
    expect(parseTikTokProfileHtml('<html></html>'), isNull);
    expect(parseTikTokVideoHtml('<html></html>'), isNull);
  });

  test('formatTikTokDuration formats minutes and hides zero', () {
    expect(formatTikTokDuration(65), '1:05');
    expect(formatTikTokDuration(0), '');
  });

  test('handle candidates glue a display name into likely usernames', () {
    expect(
      tiktokSearchHandleCandidates("Charli D'Amelio"),
      containsAll([
        'charlidamelio',
        'charli.damelio',
        'charli_damelio',
        'charli',
      ]),
    );
    expect(tiktokSearchHandleCandidates('@NBA'), ['nba']);
    expect(
      tiktokSearchHandleCandidates(
        'charli',
        suggestions: const ['charli damelio'],
      ),
      contains('charlidamelio'),
    );
  });

  test('discover cards of type 2 become search users', () {
    final users = parseTikTokDiscoverUsers({
      'statusCode': 0,
      'body': [
        {
          'exploreList': [
            {
              'cardItem': {
                'type': 2,
                'title': 'The Rock',
                'subTitle': '@therock',
                'link': '/@therock',
                'cover': 'https://p16.tiktokcdn.com/a.jpg',
                'extraInfo': {'verified': true, 'fans': 13900000},
              },
            },
            {
              'cardItem': {'type': 3, 'title': '#cats', 'link': '/tag/cats'},
            },
          ],
        },
      ],
    });
    expect(users, hasLength(1));
    expect(users.single.uniqueId, 'therock');
    expect(users.single.nickname, 'The Rock');
    expect(users.single.verified, isTrue);
    expect(users.single.followerCount, 13900000);
    expect(tiktokUserMatchesQuery(users.single, 'rock'), isTrue);
    expect(tiktokUserMatchesQuery(users.single, 'cats'), isFalse);
  });

  test('suggest preview and guide lists are parsed together', () {
    expect(
      parseTikTokSuggestList({
        'sug_list': [
          {'content': 'cats meowing'},
          {
            'word_record': {'words_content': 'cats'},
          },
        ],
        'data': [
          {'word': 'NFL Preseason'},
        ],
      }),
      ['cats meowing', 'cats', 'NFL Preseason'],
    );
    expect(parseTikTokSuggestList({'status_code': 0}), isEmpty);
  });
}
