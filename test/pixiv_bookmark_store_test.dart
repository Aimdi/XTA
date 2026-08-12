import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

void main() {
  late PrefServiceCache prefs;

  setUp(() async {
    prefs = PrefServiceCache(
      cache: {
        optionPluginPixivRefreshToken: 'refresh-me',
        optionPluginPixivAccessToken: 'access-1',
        optionPluginPixivAccessExpiresAt: DateTime.now()
            .add(const Duration(hours: 1))
            .toIso8601String(),
        optionPluginPixivShowR18: false,
      },
    );
  });

  test('isBookmarked uses the illust flag until toggled', () {
    final store = PixivBookmarkStore();
    final plain = _illust(id: 1);
    final already = _illust(id: 2, isBookmarked: true, totalBookmarks: 4);

    expect(store.isBookmarked(plain), isFalse);
    expect(store.isBookmarked(already), isTrue);
    expect(store.bookmarkCount(plain), 0);
    expect(store.bookmarkCount(already), 4);
  });

  test('toggle bookmarks then unbookmarks and adjusts the count', () async {
    final paths = <String>[];
    final client = PixivClient(
      prefs,
      httpClient: MockClient((request) async {
        paths.add(request.url.path);
        return http.Response(
          jsonEncode({}),
          200,
          headers: {'content-type': 'application/json'},
        );
      }),
    );
    final store = PixivBookmarkStore();
    final illust = _illust(id: 7, totalBookmarks: 10);

    await store.toggle(client, illust);
    expect(store.isBookmarked(illust), isTrue);
    expect(store.bookmarkCount(illust), 11);
    expect(paths, ['/v2/illust/bookmark/add']);

    await store.toggle(client, illust);
    expect(store.isBookmarked(illust), isFalse);
    expect(store.bookmarkCount(illust), 10);
    expect(paths, ['/v2/illust/bookmark/add', '/v1/illust/bookmark/delete']);
  });

  test('unbookmarking a bookmarked illust decrements the count', () async {
    final client = PixivClient(
      prefs,
      httpClient: MockClient(
        (_) async => http.Response(
          '{}',
          200,
          headers: {'content-type': 'application/json'},
        ),
      ),
    );
    final store = PixivBookmarkStore();
    final illust = _illust(id: 3, isBookmarked: true, totalBookmarks: 5);

    await store.toggle(client, illust);
    expect(store.isBookmarked(illust), isFalse);
    expect(store.bookmarkCount(illust), 4);
  });
}

PixivIllust _illust({
  required int id,
  bool isBookmarked = false,
  int totalBookmarks = 0,
}) {
  return PixivIllust(
    id: id,
    title: '',
    caption: '',
    type: 'illust',
    thumbnailUrl: 'https://i.pximg.net/$id.jpg',
    pageCount: 1,
    userId: 1,
    userName: '',
    userAccount: '',
    isBookmarked: isBookmarked,
    totalBookmarks: totalBookmarks,
  );
}
