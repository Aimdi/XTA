import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';

Map<String, Object?> _novel(int id, {int xRestrict = 0, int aiType = 1}) => {
  'id': id,
  'title': 'Novel $id',
  'user': {'id': 2, 'name': 'A', 'account': 'a', 'profile_image_urls': {}},
  'x_restrict': xRestrict,
  'novel_ai_type': aiType,
};

Map<String, Object?> _list({String? next}) => {
  'novels': [_novel(1), _novel(2, xRestrict: 1), _novel(3, aiType: 2)],
  'next_url': next,
};

http.Response _json(Object body) =>
    http.Response(jsonEncode(body), 200, headers: {'content-type': 'application/json; charset=utf-8'});

/// A signed-in reader's API whose every request lands in [requests].
(PixivNovelApi, List<http.Request>) _api({
  bool showR18 = false,
  bool hideAi = false,
  Object Function(http.Request request)? answer,
}) {
  final requests = <http.Request>[];
  final prefs = PrefServiceCache(
    cache: {
      optionPluginPixivRefreshToken: 'refresh',
      optionPluginPixivAccessToken: 'access',
      optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
      optionPluginPixivUserId: 77,
      optionPluginPixivShowR18: showR18,
      optionPluginPixivHideAi: hideAi,
    },
  );
  final client = PixivClient(
    prefs,
    httpClient: MockClient((request) async {
      requests.add(request);
      return _json(answer?.call(request) ?? _list());
    }),
  );
  return (PixivNovelApi(client), requests);
}

List<int> _ids(PixivNovelPage page) => [for (final novel in page.items) novel.id];

void main() {
  group('feeds', () {
    test('recommended asks for ranking novels and the Android filter, then follows next_url', () async {
      final (api, requests) = _api(answer: (_) => _list(next: 'https://app-api.pixiv.net/v1/novel/recommended?p=2'));
      final page = await api.recommended();

      final first = requests.single.url;
      expect(first.path, '/v1/novel/recommended');
      expect(first.queryParameters, {
        'include_privacy_policy': 'true',
        'filter': 'for_android',
        'include_ranking_novels': 'true',
      });
      expect(page.nextUrl, 'https://app-api.pixiv.net/v1/novel/recommended?p=2');
      expect(_ids(page), [1, 3], reason: 'R-18 novels stay out while Show R-18 is off');

      await api.recommended(nextUrl: page.nextUrl);
      expect(requests.last.url.toString(), 'https://app-api.pixiv.net/v1/novel/recommended?p=2');
    });

    test('following sends public or private', () async {
      final (api, requests) = _api();
      await api.following();
      await api.following(restrict: 'private');

      expect(requests.map((request) => request.url.path), everyElement('/v1/novel/follow'));
      expect([for (final request in requests) request.url.queryParameters['restrict']], ['public', 'private']);
    });

    test('Show R-18 and Hide AI apply to the feeds', () async {
      final (shown, _) = _api(showR18: true);
      expect(_ids(await shown.following()), [1, 2, 3]);

      final (hidden, _) = _api(hideAi: true);
      expect(_ids(await hidden.following()), [1]);
    });
  });

  group('rankings', () {
    test('send the board, the archive date when one is picked, and the Android filter', () async {
      final (api, requests) = _api();
      await api.ranking('day_male', date: '2026-08-01');
      await api.ranking('week');

      expect(requests.first.url.path, '/v1/novel/ranking');
      expect(requests.first.url.queryParameters, {'mode': 'day_male', 'date': '2026-08-01', 'filter': 'for_android'});
      expect(requests.last.url.queryParameters.containsKey('date'), isFalse);
    });

    test('an AI board keeps its AI novels with Hide AI on; other boards drop them', () async {
      final (api, _) = _api(hideAi: true);
      expect(_ids(await api.ranking('week_ai')), [1, 3]);
      expect(_ids(await api.ranking('day')), [1]);
    });
  });

  group('bookmarks', () {
    test('the reader\'s own keep R-18 and AI novels; someone else\'s follow the filters', () async {
      final (api, requests) = _api(hideAi: true);
      final own = await api.ownBookmarks(restrict: 'private');
      final theirs = await api.bookmarks(userId: 5);

      expect(requests.first.url.path, '/v1/user/bookmarks/novel');
      expect(requests.first.url.queryParameters, {'user_id': '77', 'restrict': 'private'});
      expect(requests.last.url.queryParameters, {'user_id': '5', 'restrict': 'public'});
      expect(_ids(own), [1, 2, 3]);
      expect(_ids(theirs), [1]);
    });

    test('add and delete post the novel id and, for add, the visibility', () async {
      final (api, requests) = _api(answer: (_) => const {});
      await api.addBookmark(31, restrict: 'private');
      await api.deleteBookmark(31);

      expect(requests.map((request) => (request.method, request.url.path)), [
        ('POST', '/v2/novel/bookmark/add'),
        ('POST', '/v1/novel/bookmark/delete'),
      ]);
      expect(requests.first.bodyFields, {'novel_id': '31', 'restrict': 'private'});
      expect(requests.last.bodyFields, {'novel_id': '31'});
      expect(requests.first.headers['Content-Type'], startsWith('application/x-www-form-urlencoded'));
    });
  });

  group('watchlist', () {
    test('lists watched series, skipping rows that do not parse', () async {
      final (api, requests) = _api(
        answer: (_) => {
          'series': [
            {
              'id': 8,
              'title': 'Seasons',
              'url': 'https://i.pximg.net/c/cover/8.jpg',
              'mask_text': null,
              'published_content_count': 4,
              'last_published_content_datetime': '2026-09-30T10:00:00+09:00',
              'latest_content_id': 13,
              'user': {'id': 42, 'name': 'Mika'},
            },
            {'title': 'no id'},
            'junk',
          ],
          'next_url': 'https://app-api.pixiv.net/v1/watchlist/novel?offset=30',
        },
      );
      final page = await api.watchlist();

      expect(requests.single.url.path, '/v1/watchlist/novel');
      expect(
        [for (final series in page.items) (series.id, series.latestContentId, series.publishedCount)],
        [(8, 13, 4)],
      );
      expect(page.nextUrl, endsWith('offset=30'));
    });

    test('add and delete post the series id', () async {
      final (api, requests) = _api(answer: (_) => const {});
      await api.addToWatchlist(8);
      await api.removeFromWatchlist(8);

      expect(
        [for (final request in requests) request.url.path],
        ['/v1/watchlist/novel/add', '/v1/watchlist/novel/delete'],
      );
      expect(
        [for (final request in requests) request.bodyFields],
        [
          {'series_id': '8'},
          {'series_id': '8'},
        ],
      );
    });
  });

  group('series', () {
    test('asks by series id, then by next_url, and hides chapters the filters hide', () async {
      final (api, requests) = _api(
        answer: (_) => {
          'novel_series_detail': {'id': 8, 'title': 'Seasons', 'user': {}},
          'novel_series_first_novel': _novel(1),
          'novel_series_latest_novel': _novel(2, xRestrict: 1),
          'novels': [_novel(1), _novel(2, xRestrict: 1)],
          'next_url': 'https://app-api.pixiv.net/v2/novel/series?series_id=8&last_order=2',
        },
      );
      final page = await api.series(8);
      await api.series(8, nextUrl: page.nextUrl);

      expect(requests.first.url.path, '/v2/novel/series');
      expect(requests.first.url.queryParameters, {'series_id': '8'});
      expect(requests.last.url.queryParameters['last_order'], '2');
      expect([for (final chapter in page.chapters) chapter.novel.id], [1]);
      expect((page.listed, page.first?.id, page.latest?.id), (2, 1, null));
    });
  });

  group('search', () {
    test('sends the word, place to look, order and dates to /v1/search/novel, then follows next_url', () async {
      final (api, requests) = _api(answer: (_) => _list(next: 'https://app-api.pixiv.net/v1/search/novel?offset=30'));
      final filter = const PixivSearchFilter(
        target: PixivSearchTarget.text,
        sort: PixivSearchSort.oldest,
      ).withDates(PixivDatePreset.week).withUsersIri(1000);
      final query = pixivSearchQuery(filter, 'letters', now: DateTime(2026, 10, 10), kind: PixivSearchKind.novels);
      final page = await api.search(query);
      await api.search(query, nextUrl: page.nextUrl);

      expect(requests.first.url.path, '/v1/search/novel');
      expect(requests.first.url.queryParameters, {
        'word': 'letters 1000users入り',
        'search_target': 'text',
        'sort': 'date_asc',
        'start_date': '2026-10-03',
        'end_date': '2026-10-10',
        'merge_plain_keyword_results': 'true',
        'filter': 'for_android',
      });
      expect(requests.last.url.toString(), 'https://app-api.pixiv.net/v1/search/novel?offset=30');
      expect(_ids(page), [1, 3]);
    });

    test('the search\'s own AI choice decides, not the feeds\'', () async {
      final (api, _) = _api(hideAi: true);
      expect(_ids(await api.search(const {'word': 'a'}, includeAi: true)), [1, 3]);
      expect(_ids(await api.search(const {'word': 'a'}, includeAi: false)), [1]);
    });
  });

  group('trending tags', () {
    test('read the novel tags with their pictures, leaving a filtered picture out', () async {
      final (api, requests) = _api(
        answer: (_) => {
          'trend_tags': [
            {
              'tag': '恋愛',
              'translated_name': 'romance',
              'illust': {
                'id': 5,
                'title': 'Cover',
                'image_urls': {'square_medium': 'https://i.pximg.net/c/5.jpg'},
              },
            },
            {
              'tag': '異世界',
              'illust': {
                'id': 6,
                'x_restrict': 1,
                'image_urls': {'square_medium': 'https://i.pximg.net/c/6.jpg'},
              },
            },
            {'tag': ''},
            'junk',
          ],
        },
      );
      final tags = await api.trendingTags();

      expect(requests.single.url.path, '/v1/trending-tags/novel');
      expect(requests.single.url.queryParameters, {'filter': 'for_android'});
      expect(
        [for (final tag in tags) (tag.name, tag.translatedName, tag.illust?.id)],
        [('恋愛', 'romance', 5), ('異世界', null, null)],
      );
    });

    test('a reshaped answer is an empty list', () async {
      final (api, _) = _api(answer: (_) => {'trend_tags': 'none'});
      expect(await api.trendingTags(), isEmpty);
    });
  });

  group('a creator\'s novels', () {
    test('ask by user id with the Android filter; the reader\'s own keep R-18 and AI novels', () async {
      final (api, requests) = _api(hideAi: true);
      final theirs = await api.userNovels(5);
      final own = await api.userNovels(77);

      expect(requests.first.url.path, '/v1/user/novels');
      expect(requests.first.url.queryParameters, {'user_id': '5', 'filter': 'for_android'});
      expect(_ids(theirs), [1]);
      expect(_ids(own), [1, 2, 3]);
    });
  });

  group('detail', () {
    test('reads the novel by id', () async {
      final (api, requests) = _api(answer: (_) => {'novel': _novel(31)});
      final novel = await api.detail(31);

      expect(requests.single.url.path, '/v2/novel/detail');
      expect(requests.single.url.queryParameters, {'novel_id': '31'});
      expect((novel.id, novel.title, novel.user.name), (31, 'Novel 31', 'A'));
    });

    test('a withheld or missing novel is not found rather than a throw from parsing', () async {
      for (final body in [
        {
          'novel': {..._novel(31), 'visible': false},
        },
        {'novel': 'gone'},
        const <String, Object?>{},
      ]) {
        final (api, _) = _api(answer: (_) => body);
        await expectLater(
          api.detail(31),
          throwsA(isA<PixivException>().having((error) => error.kind, 'kind', PixivErrorKind.notFound)),
        );
      }
    });
  });
}
