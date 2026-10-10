import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_search_api.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';

Map<String, Object?> _illust(int id, {bool ai = false}) => {
  'id': id,
  'title': 'Work $id',
  'type': 'illust',
  'image_urls': {'medium': 'https://i.pximg.net/c/540x540_70/img-master/$id.jpg'},
  'user': {'id': 7, 'name': 'Painter', 'account': 'painter'},
  'page_count': 1,
  'illust_ai_type': ai ? 2 : 1,
};

void main() {
  late List<http.Request> requests;
  late Object? Function(http.Request request) answer;
  late PixivSearchApi api;

  setUp(() {
    requests = [];
    final prefs = PrefServiceCache(
      cache: {
        optionPluginPixivRefreshToken: 'refresh-me',
        optionPluginPixivAccessToken: 'access-1',
        optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        optionPluginPixivShowR18: false,
        optionPluginPixivHideAi: true,
      },
    );
    api = PixivSearchApi(
      PixivClient(
        prefs,
        httpClient: MockClient((request) async {
          requests.add(request);
          return http.Response(jsonEncode(answer(request)), 200, headers: {'content-type': 'application/json'});
        }),
      ),
    );
  });

  test('illust search sends the filter query and follows next_url', () async {
    answer = (_) => {
      'illusts': [_illust(1), _illust(2, ai: true)],
      'next_url': 'https://app-api.pixiv.net/v1/search/illust?word=miku&offset=30',
    };
    final query = pixivSearchQuery(
      const PixivSearchFilter(target: PixivSearchTarget.exactTags, hideAi: false).withUsersIri(500),
      'miku',
      now: DateTime(2026, 10, 10),
    );

    final page = await api.illusts(query, includeAi: true);
    final sent = requests.single.url;
    expect(sent.path, '/v1/search/illust');
    expect(sent.queryParameters, {
      'word': 'miku 500users入り',
      'search_target': 'exact_match_for_tags',
      'sort': 'date_desc',
      'search_ai_type': '0',
      'merge_plain_keyword_results': 'true',
      'filter': 'for_android',
    });
    expect([for (final illust in page.illusts) illust.id], [1, 2], reason: 'the search said to include AI works');
    expect(page.nextUrl, contains('offset=30'));

    await api.illusts(query, nextUrl: page.nextUrl);
    expect(requests.last.url.queryParameters['offset'], '30');
  });

  test('without an AI choice the feeds\' Hide AI applies', () async {
    answer = (_) => {
      'illusts': [_illust(1), _illust(2, ai: true)],
    };
    final page = await api.illusts({'word': 'miku'});
    expect([for (final illust in page.illusts) illust.id], [1]);
  });

  test('user search sends the word and parses each creator with their previews', () async {
    answer = (_) => {
      'user_previews': [
        {
          'user': {'id': 11, 'name': 'Rin', 'account': 'rin', 'is_followed': true},
          'illusts': [_illust(5), _illust(6)],
        },
        {'user': null},
        {
          'user': {'id': 12, 'name': 'Len', 'account': 'len'},
        },
      ],
      'next_url': null,
    };

    final page = await api.users(' kagamine ');
    final sent = requests.single.url;
    expect(sent.path, '/v1/search/user');
    expect(sent.queryParameters, {'word': 'kagamine', 'filter': 'for_android'});
    expect([for (final preview in page.items) preview.user.id], [11, 12]);
    expect(page.items.first.user.isFollowed, isTrue);
    expect([for (final work in page.items.first.illusts) work.id], [5, 6]);
    expect(page.items.last.illusts, isEmpty);
    expect(page.nextUrl, isNull);
  });

  test('the popular preview pages through its own endpoint', () async {
    answer = (_) => {
      'illusts': [_illust(3)],
      'next_url': 'https://app-api.pixiv.net/v1/search/popular-preview/illust?word=miku&offset=30',
    };

    final page = await api.popularPreview('miku', PixivSearchTarget.partialTags, includeAi: true);
    final sent = requests.single.url;
    expect(sent.path, '/v1/search/popular-preview/illust');
    expect(sent.queryParameters['include_translated_tag_results'], 'true');
    expect(sent.queryParameters['search_target'], 'partial_match_for_tags');
    expect(page.illusts.single.id, 3);

    await api.popularPreview('miku', PixivSearchTarget.partialTags, nextUrl: page.nextUrl);
    expect(requests.last.url.queryParameters['offset'], '30');
  });

  test('a reshaped answer reads as an empty page', () async {
    answer = (_) => {'illusts': 'nope', 'user_previews': 42};
    expect((await api.illusts({'word': 'x'})).illusts, isEmpty);
    expect((await api.users('x')).items, isEmpty);
  });
}
