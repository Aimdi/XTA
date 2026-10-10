import 'dart:convert';
import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';

Map<String, Object?> _illust(int id, {bool r18 = false, bool ai = false}) => {
  'id': id,
  'title': 't$id',
  'type': 'illust',
  'image_urls': {'medium': 'https://i.pximg.net/$id.jpg'},
  'user': {'id': 2, 'name': 'A', 'account': 'a', 'profile_image_urls': {}},
  'page_count': 1,
  'x_restrict': r18 ? 1 : 0,
  'sanity_level': 2,
  'illust_ai_type': ai ? 2 : 1,
};

http.Response _json(Object? body, [int status = 200]) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

void main() {
  late PrefServiceCache prefs;
  late List<http.Request> requests;

  setUp(() {
    requests = [];
    prefs = PrefServiceCache(
      cache: {
        optionPluginPixivRefreshToken: 'refresh-me',
        optionPluginPixivAccessToken: 'access-1',
        optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        optionPluginPixivShowR18: false,
        optionPluginPixivHideAi: false,
      },
    );
  });

  PixivDiscoveryApi api(http.Response Function(http.Request request) answer, {String locale = 'en'}) =>
      PixivDiscoveryApi(
        PixivClient(
          prefs,
          locale: () => locale,
          httpClient: MockClient((request) async {
            requests.add(request);
            return answer(request);
          }),
        ),
      );

  Map<String, String> query() => requests.last.url.queryParameters;

  group('rankings', () {
    test('send the mode, the archive day when one is picked, and the Android filter', () async {
      final pixiv = api((_) => _json({'illusts': []}));
      await pixiv.ranking('week_original', date: '2026-08-01');
      expect(requests.last.url.path, '/v1/illust/ranking');
      expect(query(), {'mode': 'week_original', 'date': '2026-08-01', 'filter': 'for_android'});

      await pixiv.ranking('day');
      expect(query().containsKey('date'), isFalse);
    });

    test('AI boards keep their AI works under Hide AI; the others drop them', () async {
      await prefs.set(optionPluginPixivHideAi, true);
      final pixiv = api(
        (_) => _json({
          'illusts': [_illust(1, ai: true), _illust(2)],
        }),
      );
      expect((await pixiv.ranking('day_ai')).illusts.map((work) => work.id), [1, 2]);
      expect((await pixiv.ranking('day')).illusts.map((work) => work.id), [2]);
    });

    test('R-18 works stay out of every board while Show R-18 is off', () async {
      final pixiv = api(
        (_) => _json({
          'illusts': [_illust(1, r18: true), _illust(2)],
        }),
      );
      expect((await pixiv.ranking('day_r18')).illusts.map((work) => work.id), [2]);
    });
  });

  test('recommended manga asks the manga endpoint with ranking labels', () async {
    final pixiv = api(
      (_) => _json({
        'illusts': [_illust(5)],
        'next_url': 'https://app-api.pixiv.net/next',
      }),
    );
    final page = await pixiv.mangaRecommended();
    expect(requests.last.url.path, '/v1/manga/recommended');
    expect(query(), {'include_ranking_label': 'true', 'filter': 'for_android'});
    expect(page.illusts.single.id, 5);
    expect(page.nextUrl, 'https://app-api.pixiv.net/next');
  });

  test('the Following feed asks for the chosen follows', () async {
    final pixiv = api((_) => _json({'illusts': []}));
    for (final restrict in ['all', 'public', 'private']) {
      await pixiv.following(restrict: restrict);
      expect(requests.last.url.path, '/v2/illust/follow');
      expect(query(), {'restrict': restrict});
    }
  });

  test('the walkthrough is the one call sent without a token', () async {
    final pixiv = api(
      (_) => _json({
        'illusts': [_illust(9)],
        'next_url': 'https://app-api.pixiv.net/v1/walkthrough/illusts?offset=30',
      }),
    );
    final first = await pixiv.walkthrough();
    await pixiv.walkthrough(nextUrl: first.nextUrl);
    expect(requests.map((request) => request.url.path), ['/v1/walkthrough/illusts', '/v1/walkthrough/illusts']);
    expect(requests.map((request) => request.headers['Authorization']), [null, null]);
    expect(requests.last.url.queryParameters['offset'], '30');
  });

  test('watchlist changes post the series id as a form', () async {
    final pixiv = api((_) => http.Response('', 200));
    await pixiv.addToWatchlist(266067);
    await pixiv.removeFromWatchlist(266067);
    expect(requests.map((request) => '${request.method} ${request.url.path} ${request.body}'), [
      'POST /v1/watchlist/manga/add series_id=266067',
      'POST /v1/watchlist/manga/delete series_id=266067',
    ]);
    expect(requests.first.headers['Content-Type'], startsWith('application/x-www-form-urlencoded'));
  });

  test('recommended creators keep their previews under the reader\'s filters', () async {
    final pixiv = api(
      (_) => _json({
        'user_previews': [
          {
            'user': {'id': 7, 'name': 'Mika', 'account': 'mika', 'profile_image_urls': {}},
            'illusts': [_illust(1, r18: true), _illust(2)],
            'is_muted': false,
          },
          {'user': null},
        ],
        'next_url': null,
      }),
    );
    final page = await pixiv.recommendedUsers();
    expect(query(), {'filter': 'for_android'});
    expect(page.items.single.user.name, 'Mika');
    expect(page.items.single.illusts.map((work) => work.id), [2]);
  });

  test('Pixivision articles come from every category, newest first', () async {
    final pixiv = api(
      (_) => _json({
        'spotlight_articles': [
          {
            'id': 9876,
            'title': 'Cats in Autumn - for your pleasure',
            'pure_title': 'Cats in Autumn',
            'thumbnail': 'https://i.pximg.net/c/w1200/9876.jpg',
            'article_url': 'https://www.pixivision.net/en/a/9876',
            'publish_date': '2024-10-01T18:00:00+09:00',
          },
          {'id': null, 'title': 'broken'},
          {'id': 5, 'title': ''},
        ],
        'next_url': 'https://app-api.pixiv.net/v1/spotlight/articles?offset=10',
      }),
    );
    final page = await pixiv.spotlightArticles();
    expect(query(), {'filter': 'for_android', 'category': 'all'});
    final article = page.items.single;
    expect(
      (article.id, article.displayTitle, article.thumbnailUrl),
      (9876, 'Cats in Autumn', 'https://i.pximg.net/c/w1200/9876.jpg'),
    );
    expect(article.publishedAt!.toUtc(), DateTime.utc(2024, 10, 1, 9));
    expect(page.nextUrl, contains('offset=10'));
  });

  group('series', () {
    final detail = {
      'id': 266067,
      'title': "X'max Present",
      'caption': 'Two <b>parts</b>',
      'cover_image_urls': {'medium': 'https://i.pximg.net/cover.jpg'},
      'series_work_count': 2,
      'create_date': '2024-12-25T01:27:20+09:00',
      'watchlist_added': true,
      'user': {'id': 4004637, 'name': '幻羽', 'account': '9365710x', 'profile_image_urls': {}},
    };

    test('a page carries the header and the works', () async {
      final pixiv = api(
        (_) => _json({
          'illust_series_detail': detail,
          'illusts': [_illust(11), _illust(12)],
        }),
      );
      final page = await pixiv.illustSeries(266067);
      expect(requests.last.url.path, '/v1/illust/series');
      expect(query(), {'illust_series_id': '266067', 'filter': 'for_android'});
      final series = page.series!;
      expect(
        (series.title, series.caption, series.workCount, series.watchlistAdded),
        ("X'max Present", 'Two parts', 2, true),
      );
      expect(series.user.id, 4004637);
      expect(series.url, 'https://www.pixiv.net/user/4004637/series/266067');
      expect(page.works.items.map((work) => work.id), [11, 12]);
    });

    test('a reshaped header reads as no header, and missing fields as defaults', () {
      expect(pixivIllustSeriesFromJson({'title': 'no id'}), isNull);
      expect(pixivIllustSeriesFromJson('nonsense'), isNull);
      final bare = pixivIllustSeriesFromJson({'id': 3})!;
      expect((bare.title, bare.coverUrl, bare.workCount, bare.watchlistAdded, bare.user.id), ('', null, 0, false, 0));
    });

    test('the context gives the place and the neighbours the reader may see', () async {
      final pixiv = api(
        (_) => _json({
          'illust_series_detail': detail,
          'illust_series_context': {'content_order': 2, 'prev': _illust(11), 'next': _illust(13, r18: true)},
        }),
      );
      final place = (await pixiv.seriesContext(12))!;
      expect(requests.last.url.path, '/v1/illust-series/illust');
      expect(query(), {'illust_id': '12'});
      expect((place.order, place.total, place.previous?.id, place.next), (2, 2, 11, null));
    });

    test('a context without a place is no context', () {
      expect(parsePixivSeriesContext({'illust_series_context': null}), isNull);
      expect(
        parsePixivSeriesContext({
          'illust_series_context': {'content_order': 0},
        }),
        isNull,
      );
      final place = parsePixivSeriesContext({
        'illust_series_context': {'content_order': '4', 'prev': 'x'},
      })!;
      expect((place.order, place.total, place.previous, place.next), (4, 0, null, null));
    });
  });

  group('the manga watchlist', () {
    test('lists each watched series and skips rows that do not parse', () async {
      final pixiv = api(
        (_) => _json({
          'series': [
            {
              'mask_text': null,
              'user': {'id': 4004637, 'name': '幻羽'},
              'latest_content_id': 125547965,
              'id': 266067,
              'title': "X'max Present",
              'last_published_content_datetime': '2024-12-26T01:27:48+09:00',
              'published_content_count': 2,
              'url': 'https://i.pximg.net/c/240x480/img-master/125508098_p0_master1200.jpg',
            },
            {'title': 'no id'},
            'garbage',
            {'id': 77, 'mask_text': 'Restricted', 'latest_content_id': 0},
          ],
          'next_url': null,
        }),
      );
      final page = await pixiv.mangaWatchlist();
      expect(requests.last.url.path, '/v1/watchlist/manga');
      expect(page.items.map((series) => series.id), [266067, 77]);
      final watched = page.items.first;
      expect(
        (watched.title, watched.userName, watched.latestContentId, watched.publishedCount),
        ("X'max Present", '幻羽', 125547965, 2),
      );
      expect(watched.lastPublishedAt!.toUtc(), DateTime.utc(2024, 12, 25, 16, 27, 48));
      expect(watched.coverUrl, contains('125508098'));
      final masked = page.items.last;
      expect(
        (masked.maskText, masked.latestContentId, masked.coverUrl, masked.publishedCount),
        ('Restricted', null, null, 0),
      );
    });

    test('a reshaped page is an empty page', () {
      expect(parsePixivWatchlist({'series': 'nope'}), isEmpty);
      expect(parsePixivWatchlist(null), isEmpty);
    });
  });

  group('Pixivision articles', () {
    final html = File('test/fixtures/Pixivision/article_en.html').readAsStringSync();

    test('are fetched as a desktop browser in the reader\'s language, without the token', () async {
      final pixiv = api((_) => http.Response.bytes(utf8.encode(html), 200), locale: 'zh_Hant');
      final article = await pixiv.pixivisionArticle(9876);
      final request = requests.single;
      expect(request.url.toString(), 'https://www.pixivision.net/zh-tw/a/9876');
      expect(request.headers['User-Agent'], pixivisionUserAgent);
      expect(request.headers['Referer'], 'https://www.pixivision.net/zh-tw/');
      expect(request.headers['Accept-Language'], 'zh-TW');
      expect(request.headers.containsKey('Authorization'), isFalse);
      expect(article.works, hasLength(2));
    });

    test('a missing article and an unreadable page say why', () async {
      final missing = api((_) => http.Response('', 404));
      await expectLater(
        missing.pixivisionArticle(1),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.notFound)),
      );
      final blank = api((_) => http.Response('<html><body></body></html>', 200));
      await expectLater(
        blank.pixivisionArticle(1),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.badResponse)),
      );
    });
  });
}
