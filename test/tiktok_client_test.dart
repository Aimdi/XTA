import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';

String _profileHtml(String handle, {String nickname = 'Name'}) =>
    '''
<html><script id="__UNIVERSAL_DATA_FOR_REHYDRATION__">
{"__DEFAULT_SCOPE__":{"webapp.user-detail":{"userInfo":{"user":{"id":"1","secUid":"MS4wLjABAAAA_$handle","uniqueId":"$handle","nickname":"$nickname","avatarThumb":"https://p16.tiktokcdn.com/a.jpg","privateAccount":false,"verified":true},"stats":{"followerCount":10}}}}}
</script></html>
''';

void main() {
  late PrefServiceCache prefs;

  setUp(() {
    prefs = PrefServiceCache(
      cache: {optionPluginTiktokDeviceId: '7300000000000000000'},
    );
  });

  test('search resolves a display name via profile HTML and discover', () async {
    final requested = <Uri>[];
    final client = TikTokClient(
      prefs,
      httpClient: MockClient((request) async {
        requested.add(request.url);
        final path = request.url.path;
        if (path == '/api/search/general/preview/') {
          return http.Response(
            jsonEncode({
              'sug_list': [
                {'content': 'charli d amelio'},
              ],
            }),
            200,
          );
        }
        if (path == '/node/share/discover') {
          return http.Response(
            jsonEncode({
              'statusCode': 0,
              'body': [
                {
                  'exploreList': [
                    {
                      'cardItem': {
                        'type': 2,
                        'title': 'charli d’amelio',
                        'link': '/@charlidamelio',
                        'extraInfo': {'verified': true, 'fans': 150000000},
                      },
                    },
                    {
                      'cardItem': {
                        'type': 2,
                        'title': 'The Rock',
                        'link': '/@therock',
                        'extraInfo': {'verified': true, 'fans': 1},
                      },
                    },
                  ],
                },
              ],
            }),
            200,
          );
        }
        if (path.startsWith('/@')) {
          final handle = path.substring(2);
          if (handle == 'charlidamelio' || handle == 'charli') {
            return http.Response(
              _profileHtml(handle, nickname: 'charli damelio'),
              200,
              headers: {'content-type': 'text/html; charset=utf-8'},
            );
          }
          return http.Response('missing', 404);
        }
        if (path == '/api/creator/item_list/') {
          return http.Response(
            jsonEncode({
              'statusCode': 0,
              'hasMore': false,
              'itemList': [
                {
                  'id': '99',
                  'desc': 'hi',
                  'createTime': 1700000000,
                  'author': {'uniqueId': 'charlidamelio', 'nickname': 'charli'},
                  'video': {
                    'playAddr': 'https://v16.tiktokcdn.com/a.mp4',
                    'cover': 'https://p16.tiktokcdn.com/c.jpg',
                  },
                },
              ],
            }),
            200,
          );
        }
        return http.Response('no', 404);
      }),
    );

    final page = await client.search("Charli D'Amelio");

    expect(
      requested.map((u) => u.path),
      contains('/api/search/general/preview/'),
    );
    expect(requested.map((u) => u.path), contains('/node/share/discover'));
    expect(requested.map((u) => u.path), contains('/@charlidamelio'));
    expect(
      page.users.map((u) => u.uniqueId),
      contains('charlidamelio'),
      reason:
          'requested ${requested.map((u) => u.path).toList()} users=${page.users}',
    );
    expect(page.users.map((u) => u.uniqueId), isNot(contains('therock')));
    expect(page.posts, isNotEmpty);
    expect(page.posts.single.id, '99');
    expect(page.suggestions, contains('charli d amelio'));
  });

  test('suggest and trending parse unsigned preview endpoints', () async {
    final client = TikTokClient(
      prefs,
      httpClient: MockClient((request) async {
        if (request.url.path.contains('preview')) {
          return http.Response(
            jsonEncode({
              'sug_list': [
                {'content': 'cats meowing'},
              ],
            }),
            200,
          );
        }
        return http.Response(
          jsonEncode({
            'data': [
              {'word': 'NFL Preseason'},
            ],
          }),
          200,
        );
      }),
    );

    expect(await client.suggestQueries('cats'), ['cats meowing']);
    expect(await client.trendingQueries(), ['NFL Preseason']);
  });
}
