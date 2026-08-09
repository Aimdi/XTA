import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';

Map<String, Object?> _illustJson(int id) => {
  'id': id,
  'title': 't$id',
  'caption': '',
  'type': 'illust',
  'image_urls': {'square_medium': 'https://i.pximg.net/$id.jpg'},
  'user': {'id': 2, 'name': 'A', 'account': 'a', 'profile_image_urls': {}},
  'page_count': 1,
  'x_restrict': 0,
  'sanity_level': 2,
};

void main() {
  late PrefServiceCache prefs;

  setUp(() async {
    prefs = PrefServiceCache(
      cache: {
        optionPluginPixivRefreshToken: 'refresh-me',
        optionPluginPixivAccessToken: 'access-1',
        optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        optionPluginPixivShowR18: false,
      },
    );
  });

  PixivClient clientAnswering(Map<String, Object?> Function(http.Request request) answer) {
    return PixivClient(
      prefs,
      httpClient: MockClient((request) async {
        return http.Response(jsonEncode(answer(request)), 200, headers: {'content-type': 'application/json'});
      }),
    );
  }

  group('ranking archive', () {
    test('a picked date rides along; none means the latest board', () async {
      Uri? asked;
      final client = clientAnswering((request) {
        asked = request.url;
        return {
          'illusts': [_illustJson(1)],
          'next_url': null,
        };
      });

      await client.ranking(mode: 'day', date: '2026-08-01');
      expect(asked!.queryParameters['date'], '2026-08-01');

      await client.ranking(mode: 'day');
      expect(asked!.queryParameters.containsKey('date'), isFalse);
    });
  });

  group('trending tags', () {
    test('each tag carries its representative illust', () async {
      final client = clientAnswering((request) {
        expect(request.url.path, '/v1/trending-tags/illust');
        return {
          'trend_tags': [
            {'tag': '原神', 'translated_name': 'Genshin', 'illust': _illustJson(9)},
            {'tag': 'noface', 'translated_name': null, 'illust': null},
          ],
        };
      });

      final tags = await client.trendingTags();
      expect(tags, hasLength(2));
      expect(tags.first.name, '原神');
      expect(tags.first.translatedName, 'Genshin');
      expect(tags.first.illust!.id, 9);
      expect(tags.last.illust, isNull);
    });
  });

  group('popular preview', () {
    test('one free page of the most popular results for a word', () async {
      final client = clientAnswering((request) {
        expect(request.url.path, '/v1/search/popular-preview/illust');
        expect(request.url.queryParameters['word'], 'miku');
        return {
          'illusts': [_illustJson(3), _illustJson(4)],
          'next_url': null,
        };
      });

      final page = await client.popularPreview('miku');
      expect(page.illusts.map((e) => e.id), [3, 4]);
    });

    test('an empty word asks nothing', () async {
      final client = clientAnswering((request) => fail('should not be called'));
      final page = await client.popularPreview('  ');
      expect(page.illusts, isEmpty);
    });
  });

  group('tag autocomplete', () {
    test('suggests tags with their translations', () async {
      final client = clientAnswering((request) {
        expect(request.url.path, '/v2/search/autocomplete');
        expect(request.url.queryParameters['word'], 'mik');
        return {
          'tags': [
            {'name': '初音ミク', 'translated_name': 'Hatsune Miku'},
            {'name': 'ミク', 'translated_name': null},
          ],
        };
      });

      final tags = await client.autocomplete('mik');
      expect(tags.map((t) => t.name), ['初音ミク', 'ミク']);
      expect(tags.first.translatedName, 'Hatsune Miku');
    });

    test('an empty word suggests nothing without asking', () async {
      final client = clientAnswering((request) => fail('should not be called'));
      expect(await client.autocomplete(''), isEmpty);
    });
  });
}
