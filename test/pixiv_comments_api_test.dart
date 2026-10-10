import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_api.dart';

typedef _Api = ({PixivCommentsApi api, List<http.Request> requests});

_Api _apiAnswering(Object? Function(http.Request request) answer) {
  final requests = <http.Request>[];
  final prefs = PrefServiceCache(
    cache: {
      optionPluginPixivAccessToken: 'valid-token',
      optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
    },
  );
  final client = PixivClient(
    prefs,
    httpClient: MockClient((request) async {
      requests.add(request);
      return http.Response(jsonEncode(answer(request)), 200);
    }),
  );
  return (api: PixivCommentsApi(client), requests: requests);
}

const _page = {
  'total_comments': 2,
  'comments': [
    {
      'id': 1,
      'comment': 'First (heart)',
      'user': {'id': 42, 'name': 'Mika', 'account': 'mika'},
      'has_replies': true,
    },
    {
      'id': 2,
      'comment': '',
      'stamp': {'stamp_id': 301, 'stamp_url': 'https://s.pximg.net/stamp/301.jpg'},
    },
  ],
  'next_url': 'https://app-api.pixiv.net/v3/illust/comments?illust_id=120&offset=30',
};

String _call(http.Request request) => '${request.method} ${request.url.path}?${request.url.query}';

void main() {
  test('an artwork target reads /v3/illust/comments with the token', () async {
    final fixture = _apiAnswering((_) => _page);
    final page = await fixture.api.comments(const PixivCommentTarget.illust(120));

    expect(fixture.requests.map(_call), ['GET /v3/illust/comments?illust_id=120']);
    expect(fixture.requests.single.headers['Authorization'], 'Bearer valid-token');
    expect(page.items.map((comment) => comment.id), [1, 2]);
    expect(page.items.last.stampUrl, 'https://s.pximg.net/stamp/301.jpg');
    expect((page.total, page.nextUrl), (2, _page['next_url']));
  });

  test('a novel target reads /v3/novel/comments', () async {
    final fixture = _apiAnswering((_) => {'comments': []});
    await fixture.api.comments(const PixivCommentTarget.novel(55));
    expect(fixture.requests.map(_call), ['GET /v3/novel/comments?novel_id=55']);
  });

  test('replies come from each kind\'s own replies endpoint', () async {
    final fixture = _apiAnswering((_) => {'comments': []});
    await fixture.api.replies(const PixivCommentTarget.illust(120), 1);
    await fixture.api.replies(const PixivCommentTarget.novel(55), 8);
    expect(fixture.requests.map(_call), [
      'GET /v2/illust/comment/replies?comment_id=1',
      'GET /v2/novel/comment/replies?comment_id=8',
    ]);
  });

  test('a later page follows next_url as Pixiv wrote it', () async {
    final fixture = _apiAnswering((_) => {'comments': []});
    await fixture.api.comments(
      const PixivCommentTarget.illust(120),
      nextUrl: 'https://app-api.pixiv.net/v3/illust/comments?illust_id=120&offset=30',
    );
    await fixture.api.replies(
      const PixivCommentTarget.illust(120),
      1,
      nextUrl: 'https://app-api.pixiv.net/v2/illust/comment/replies?comment_id=1&offset=30',
    );
    expect(fixture.requests.map(_call), [
      'GET /v3/illust/comments?illust_id=120&offset=30',
      'GET /v2/illust/comment/replies?comment_id=1&offset=30',
    ]);
  });

  test('only reads: no request posts anything', () async {
    final fixture = _apiAnswering((_) => _page);
    await fixture.api.comments(const PixivCommentTarget.illust(120));
    await fixture.api.replies(const PixivCommentTarget.illust(120), 1);
    expect(fixture.requests.map((request) => request.method).toSet(), {'GET'});
  });
}
