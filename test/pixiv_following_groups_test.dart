import 'dart:async';
import 'dart:convert';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_following_screen.dart';

void main() {
  test('following picker loads public pages then private follows without duplicate artists', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginPixivUserId: 7,
        optionPluginPixivAccessToken: 'valid-token',
        optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
      },
    );
    final requests = <Uri>[];
    final client = PixivClient(
      prefs,
      httpClient: MockClient((request) async {
        requests.add(request.url);
        final index = requests.length;
        expect(request.method, 'GET');
        expect(request.url.host, 'app-api.pixiv.net');
        expect(request.url.path, '/v1/user/following');
        return http.Response(
          jsonEncode({
            'user_previews': [
              {
                'user': {
                  'id': index == 3 ? 1 : index,
                  'name': 'Artist $index',
                  'account': 'artist',
                  'is_followed': true,
                },
              },
              {
                'user': {'id': 0},
              },
            ],
            if (index == 1)
              'next_url': 'https://app-api.pixiv.net/v1/user/following?user_id=7&restrict=public&offset=30',
          }),
          200,
        );
      }),
    );
    final store = PixivFollowingStore(client);
    await store.refresh();
    expect(store.state.map((user) => user.id), [1]);
    await store.loadMore();
    expect(store.state.map((user) => user.id), [1, 2]);
    await store.loadMore();
    expect(store.state.map((user) => user.id), [1, 2]);
    expect(requests[1].queryParameters['offset'], '30');
    expect(requests[2].queryParameters['restrict'], 'private');
    expect(store.hasMore, isFalse);
    store.destroy();
  });

  test('refresh discards an older following page and preserves its own cursor', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginPixivUserId: 7,
        optionPluginPixivAccessToken: 'valid-token',
        optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
      },
    );
    final oldPage = Completer<http.Response>();
    final oldStarted = Completer<void>();
    final requests = <Uri>[];
    http.Response page(int id, {String? next}) => http.Response(
      jsonEncode({
        'user_previews': [
          {
            'user': {'id': id, 'name': 'Artist', 'account': 'artist'},
          },
        ],
        if (next != null) 'next_url': next,
      }),
      200,
    );
    final client = PixivClient(
      prefs,
      httpClient: MockClient((request) async {
        requests.add(request.url);
        if (requests.length == 1) return page(1, next: 'https://app-api.pixiv.net/v1/user/following?offset=30');
        if (requests.length == 2) {
          oldStarted.complete();
          return oldPage.future;
        }
        if (requests.length == 3) return page(30, next: 'https://app-api.pixiv.net/v1/user/following?offset=60');
        return page(40);
      }),
    );
    final store = PixivFollowingStore(client);
    await store.refresh();
    final pending = store.loadMore();
    await oldStarted.future;
    await store.refresh();
    oldPage.complete(page(2));
    await pending;
    expect(store.state.map((user) => user.id), [30]);
    await store.loadMore();
    expect(requests.last.queryParameters['offset'], '60');
    expect(store.state.map((user) => user.id), [30, 40]);
    await store.destroy();
  });
}
