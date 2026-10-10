import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_social_api.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';

Map<String, Object?> _illust(int id, {bool r18 = false}) => {
  'id': id,
  'title': 'Work $id',
  'type': 'illust',
  'image_urls': {'medium': 'https://i.pximg.net/c/540x540_70/img-master/$id.jpg'},
  'page_count': 1,
  'x_restrict': r18 ? 1 : 0,
  'user': {'id': 9, 'name': 'Mika', 'account': 'mika'},
};

Map<String, Object?> _preview(int id) => {
  'user': {'id': id, 'name': 'Painter $id', 'account': 'painter$id', 'is_followed': true},
  'illusts': [_illust(id * 10), _illust(id * 10 + 1, r18: true)],
  'novels': [],
  'is_muted': false,
};

typedef _Api = ({PixivSocialApi api, List<http.Request> requests});

_Api _apiAnswering(Object? Function(http.Request request) answer, {int ownId = 7, bool showR18 = false}) {
  final requests = <http.Request>[];
  final prefs = PrefServiceCache(
    cache: {
      optionPluginPixivUserId: ownId,
      optionPluginPixivAccessToken: 'valid-token',
      optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
      optionPluginPixivShowR18: showR18,
    },
  );
  final client = PixivClient(
    prefs,
    httpClient: MockClient((request) async {
      requests.add(request);
      final body = answer(request);
      return http.Response(body == null ? '' : jsonEncode(body), 200);
    }),
  );
  return (api: PixivSocialApi(client), requests: requests);
}

void main() {
  test('userProfile asks for the detail and reads the whole profile', () async {
    final fixture = _apiAnswering(
      (_) => {
        'user': {'id': 9, 'name': 'Mika', 'account': 'mika', 'comment': 'Hello'},
        'profile': {'total_illusts': 2, 'total_manga': 5, 'background_image_url': 'https://i.pximg.net/bg.jpg'},
      },
    );
    final profile = await fixture.api.userProfile(9);
    final request = fixture.requests.single;
    expect(request.url.path, '/v1/user/detail');
    expect(request.url.queryParameters, {'user_id': '9', 'filter': 'for_android'});
    expect(profile.user.name, 'Mika');
    expect(profile.defaultWorkType, PixivWorkType.manga);
    expect(profile.backgroundUrl, 'https://i.pximg.net/bg.jpg');
  });

  test('userProfile refuses an answer without a user', () async {
    final fixture = _apiAnswering((_) => {'user': null});
    await expectLater(
      fixture.api.userProfile(9),
      throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.badResponse)),
    );
  });

  group('followDetail', () {
    test('reads whether and how the reader follows', () async {
      final fixture = _apiAnswering(
        (_) => {
          'follow_detail': {'is_followed': true, 'restrict': 'private'},
        },
      );
      final detail = await fixture.api.followDetail(9);
      expect(fixture.requests.single.url.path, '/v1/user/follow/detail');
      expect(fixture.requests.single.url.queryParameters, {'user_id': '9'});
      expect(detail.isFollowed, isTrue);
      expect(detail.isPrivate, isTrue);
    });

    test('a reshaped answer reads as not followed', () async {
      final fixture = _apiAnswering((_) => {'follow_detail': 'nope'});
      final detail = await fixture.api.followDetail(9);
      expect(detail.isFollowed, isFalse);
      expect(detail.restrict, '');
    });
  });

  group('userWorks', () {
    test('asks for manga by type and leaves out R-18 for someone else', () async {
      final fixture = _apiAnswering(
        (_) => {
          'illusts': [_illust(1), _illust(2, r18: true)],
          'next_url': 'https://app-api.pixiv.net/v1/user/illusts?user_id=9&type=manga&offset=30',
        },
      );
      final page = await fixture.api.userWorks(9, PixivWorkType.manga);
      final request = fixture.requests.single;
      expect(request.url.path, '/v1/user/illusts');
      expect(request.url.queryParameters, {'user_id': '9', 'type': 'manga', 'filter': 'for_android'});
      expect(page.items.map((illust) => illust.id), [1]);

      await fixture.api.userWorks(9, PixivWorkType.manga, nextUrl: page.nextUrl);
      expect(fixture.requests.last.url.queryParameters['offset'], '30');
    });

    test('keeps every one of the reader\'s own works', () async {
      final fixture = _apiAnswering(
        (_) => {
          'illusts': [_illust(1), _illust(2, r18: true)],
        },
        ownId: 9,
      );
      final page = await fixture.api.userWorks(9, PixivWorkType.illust);
      expect(fixture.requests.single.url.queryParameters['type'], 'illust');
      expect(page.items.map((illust) => illust.id), [1, 2]);
    });

    test('is the same request the More by strip and group posts make through the client', () async {
      final fixture = _apiAnswering(
        (_) => {
          'illusts': [_illust(1), _illust(2, r18: true)],
        },
        ownId: 9,
      );
      await fixture.api.userWorks(9, PixivWorkType.illust);
      final strip = await fixture.api.client.userIllusts(9);
      expect(fixture.requests.map((request) => request.url), hasLength(2));
      expect(fixture.requests.last.url, fixture.requests.first.url);
      expect(strip.items.map((illust) => illust.id), [1], reason: 'only a profile keeps the reader\'s own R-18 works');
    });
  });

  test('userBookmarks sends the user, restrict and tag', () async {
    final fixture = _apiAnswering(
      (_) => {
        'illusts': [_illust(1), _illust(2, r18: true)],
      },
    );
    final page = await fixture.api.userBookmarks(9, tag: '未分類');
    expect(fixture.requests.single.url.path, '/v1/user/bookmarks/illust');
    expect(fixture.requests.single.url.queryParameters, {
      'user_id': '9',
      'restrict': 'public',
      'tag': '未分類',
      'filter': 'for_android',
    });
    expect(page.items.map((illust) => illust.id), [1]);

    await fixture.api.userBookmarks(9);
    expect(fixture.requests.last.url.queryParameters.containsKey('tag'), isFalse);
  });

  group('user lists', () {
    test('following sends restrict and parses preview cards with their works', () async {
      final fixture = _apiAnswering(
        (_) => {
          'user_previews': [
            _preview(21),
            {
              'user': {'id': 0},
            },
          ],
          'next_url': 'https://app-api.pixiv.net/v1/user/following?user_id=7&restrict=private&offset=30',
        },
      );
      final page = await fixture.api.userFollowing(7, restrict: 'private');
      final request = fixture.requests.single;
      expect(request.url.path, '/v1/user/following');
      expect(request.url.queryParameters, {'user_id': '7', 'restrict': 'private', 'filter': 'for_android'});
      expect(page.items.map((preview) => preview.user.id), [21]);
      expect(page.items.single.user.isFollowed, isTrue);
      expect(page.items.single.illusts.map((illust) => illust.id), [210]);
      expect(page.nextUrl, contains('offset=30'));
    });

    test('followers are always public', () async {
      final fixture = _apiAnswering(
        (_) => {
          'user_previews': [_preview(31)],
        },
        showR18: true,
      );
      final page = await fixture.api.userList(PixivUserListKind.followers, 9, restrict: 'private');
      final request = fixture.requests.single;
      expect(request.url.path, '/v1/user/follower');
      expect(request.url.queryParameters, {'user_id': '9', 'restrict': 'public', 'filter': 'for_android'});
      expect(page.items.single.illusts.map((illust) => illust.id), [310, 311]);
    });

    test('a list without previews is an empty last page', () async {
      final fixture = _apiAnswering((_) => {'user_previews': 'gone'});
      final page = await fixture.api.userList(PixivUserListKind.following, 9);
      expect(page.items, isEmpty);
      expect(page.nextUrl, isNull);
    });
  });

  group('follow writes', () {
    test('a private follow posts restrict=private, and unfollow posts the delete', () async {
      final fixture = _apiAnswering((_) => null);
      final follows = PixivFollowStore(fixture.api.client);
      const user = PixivUser(id: 9, name: 'Mika', account: 'mika', comment: '');

      expect(await follows.follow(user, restrict: 'private'), isTrue);
      final add = fixture.requests.single;
      expect(add.method, 'POST');
      expect(add.url.path, '/v1/user/follow/add');
      expect(add.bodyFields, {'user_id': '9', 'restrict': 'private'});
      expect(follows.isFollowed(user), isTrue);

      expect(await follows.unfollow(user), isFalse);
      expect(fixture.requests.last.url.path, '/v1/user/follow/delete');
      expect(fixture.requests.last.bodyFields, {'user_id': '9'});
      expect(follows.isFollowed(user), isFalse);
      follows.destroy();
    });

    test('a failed follow leaves the follow as it was', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginPixivAccessToken: 'valid-token',
          optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        },
      );
      final client = PixivClient(prefs, httpClient: MockClient((_) async => http.Response('', 500)));
      final follows = PixivFollowStore(client);
      const user = PixivUser(id: 9, name: 'Mika', account: 'mika', comment: '');
      await expectLater(follows.follow(user, restrict: 'private'), throwsA(isA<PixivException>()));
      expect(follows.isFollowed(user), isFalse);
      expect(follows.isBusy(9), isFalse);
      follows.destroy();
    });
  });
}
