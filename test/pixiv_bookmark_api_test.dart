import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';

http.Response _json(Object body) =>
    http.Response(jsonEncode(body), 200, headers: {'content-type': 'application/json; charset=utf-8'});

/// A signed-in client whose every request lands in [requests] and is answered by [answer].
(PixivClient, List<http.Request>) _client([Object Function(http.Request request)? answer]) {
  final requests = <http.Request>[];
  final prefs = PrefServiceCache(
    cache: {
      optionPluginPixivRefreshToken: 'refresh',
      optionPluginPixivAccessToken: 'access',
      optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
      optionPluginPixivUserId: 77,
    },
  );
  final client = PixivClient(
    prefs,
    httpClient: MockClient((request) async {
      requests.add(request);
      return _json(answer?.call(request) ?? const {});
    }),
  );
  return (client, requests);
}

void main() {
  group('bookmark detail', () {
    test('reads visibility and which tags the bookmark is filed under', () async {
      final (client, requests) = _client(
        (_) => {
          'bookmark_detail': {
            'is_bookmarked': true,
            'restrict': 'private',
            'tags': [
              {'name': 'オリジナル', 'is_registered': true},
              {'name': '風景', 'is_registered': false},
              {'name': '  ', 'is_registered': true},
            ],
          },
        },
      );
      final detail = await PixivBookmarkApi(client).detail(5);

      expect(requests.single.url.path, '/v2/illust/bookmark/detail');
      expect(requests.single.url.queryParameters['illust_id'], '5');
      expect((detail.isBookmarked, detail.restrict), (true, 'private'));
      expect(detail.tags, [(name: 'オリジナル', checked: true), (name: '風景', checked: false)]);
    });

    test('a missing or reshaped detail reads as a public work not bookmarked', () {
      for (final json in <Object?>[
        null,
        {},
        {'bookmark_detail': 'gone'},
        {
          'bookmark_detail': {'is_bookmarked': 'yes', 'restrict': 'secret', 'tags': 'none'},
        },
      ]) {
        final detail = PixivBookmarkDetail.fromJson(json);
        expect((detail.isBookmarked, detail.restrict), (false, 'public'), reason: '$json');
        expect(detail.tags, isEmpty, reason: '$json');
      }
    });
  });

  group('bookmark writes', () {
    test('add sends the visibility and every tag space-joined in one tags[] field', () async {
      final (client, requests) = _client();
      await PixivBookmarkApi(client).add(9, restrict: 'private', tags: ['猫', 'blue sky', '猫']);

      final request = requests.single;
      expect((request.method, request.url.path), ('POST', '/v2/illust/bookmark/add'));
      expect(request.bodyFields, {'illust_id': '9', 'restrict': 'private', 'tags[]': '猫 blue sky'});
    });

    test('add without tags sends no tags[] field', () async {
      final (client, requests) = _client();
      await PixivBookmarkApi(client).add(9, restrict: 'public');
      expect(requests.single.bodyFields, {'illust_id': '9', 'restrict': 'public'});
    });

    test('delete posts the work id', () async {
      final (client, requests) = _client();
      await PixivBookmarkApi(client).delete(9);
      expect(requests.single.url.path, '/v1/illust/bookmark/delete');
      expect(requests.single.bodyFields, {'illust_id': '9'});
    });
  });

  group('bookmark tags', () {
    test('the first page asks for the reader\'s tags of one visibility, later pages follow next_url', () async {
      const next = 'https://app-api.pixiv.net/v1/user/bookmark-tags/illust?user_id=77&restrict=private&offset=30';
      final (client, requests) = _client(
        (request) => request.url.queryParameters.containsKey('offset')
            ? {
                'bookmark_tags': [
                  {'name': 'Later', 'count': 1},
                ],
                'next_url': null,
              }
            : {
                'bookmark_tags': [
                  {'name': 'Favs', 'count': 12},
                  {'name': 'NoCount'},
                  {'name': ''},
                  'junk',
                ],
                'next_url': next,
              },
      );
      final api = PixivBookmarkApi(client);
      final first = await api.tags(restrict: 'private');
      final second = await api.tags(restrict: 'private', nextUrl: first.nextUrl);

      expect(requests.first.url.path, '/v1/user/bookmark-tags/illust');
      expect(requests.first.url.queryParameters, {'user_id': '77', 'restrict': 'private'});
      expect(first.items, [(name: 'Favs', count: 12), (name: 'NoCount', count: 0)]);
      expect(first.nextUrl, next);
      expect(second.items, [(name: 'Later', count: 1)]);
      expect(second.nextUrl, isNull);
    });

    test('a reshaped tag list is an empty last page', () {
      final page = parsePixivBookmarkTags({'bookmark_tags': 'none', 'next_url': 3});
      expect(page.items, isEmpty);
      expect(page.nextUrl, isNull);
    });
  });

  group('own bookmarks', () {
    test('Favorites narrows to a tag, 未分類 for untagged ones, and sends none for all', () async {
      final (client, requests) = _client((_) => {'illusts': []});
      await client.bookmarks(userId: 77, restrict: 'private', tag: pixivUnclassifiedTag);
      await client.bookmarks(userId: 77, restrict: 'public');

      expect(requests.first.url.queryParameters, containsPair('tag', '未分類'));
      expect(requests.first.url.queryParameters, containsPair('restrict', 'private'));
      expect(requests.last.url.queryParameters.containsKey('tag'), isFalse);
    });
  });

  group('tag matching', () {
    final tags = [for (var i = 0; i < 12; i++) (name: i.isEven ? 'Cat$i' : 'dog$i', count: i)];

    test('suggests at most eight matches, ignoring case', () {
      final cats = [for (var i = 0; i < 20; i++) (name: 'CAT$i', count: i)];
      expect(pixivBookmarkTagMatches(cats, 'cat'), hasLength(8));
      expect(pixivBookmarkTagMatches(tags, 'cAt').map((tag) => tag.name), [
        'Cat0',
        'Cat2',
        'Cat4',
        'Cat6',
        'Cat8',
        'Cat10',
      ]);
      expect(pixivBookmarkTagMatches(tags, 'G1').map((tag) => tag.name), ['dog1', 'dog11']);
    });

    test('an empty query suggests nothing', () {
      expect(pixivBookmarkTagMatches(tags, '   '), isEmpty);
    });

    test('typed text becomes tags split on whitespace', () {
      expect(pixivTagsFromInput('  blue   sky\tsea '), ['blue', 'sky', 'sea']);
      expect(pixivTagsFromInput('   '), isEmpty);
    });
  });
}
