import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_store.dart';

class _AccountsStub extends ThreadsAccountsStore {
  _AccountsStub(List<String> handles) {
    update([for (final h in handles) ThreadsAccount(handle: h, name: h, avatarUrl: null)]);
  }

  @override
  Future<void> load() async {}
}

String _profileHtml(String handle, String pk) =>
    '<html><script>["LSD",[],{"token":"tok"}]</script>'
    '<script>{"username":"$handle","pk":"$pk"}</script></html>';

String _graphqlBody(String handle, String text) => jsonEncode({
  'data': {
    'mediaData': {
      'threads': [
        {
          'thread_items': [
            {
              'post': {
                'pk': '$handle-1',
                'code': 'c-$handle',
                'caption': {'text': text},
                'taken_at': 1769000000,
                'user': {'username': handle, 'full_name': handle},
              },
            },
          ],
        },
      ],
    },
  },
});

PrefServiceCache _prefs({String userIds = '{}'}) => PrefServiceCache(
  cache: {
    optionPluginThreadsDirectCookies: '',
    optionPluginThreadsDirectBearer: '',
    optionPluginThreadsDirectDeviceId: 'device-1',
    optionPluginThreadsInstance: '',
    optionPluginThreadsUserIds: userIds,
    optionPluginThreadsDirectCooldownUntil: '',
  },
);

void main() {
  // The symptom: with several followed accounts, the tab showed nothing until
  // the slowest account had answered — every account's latency added up
  // behind the anti-ban pacing, and the reader stared at a spinner for all of
  // it. The first account's posts must be on screen while the rest load.
  test('the first account\'s posts appear before the slow ones answer', () async {
    final slow = Completer<void>();
    final prefs = _prefs(userIds: '{"a":"1","b":"2","c":"3"}');
    final direct = ThreadsDirectClient(
      prefs,
      minGap: Duration.zero,
      httpClient: MockClient((request) async {
        final target = request.url.path.startsWith('/@')
            ? request.url.path.substring(2)
            : RegExp(r'"userID":"(\d+)"').firstMatch(Uri.decodeComponent(request.body))?.group(1);
        final handle = switch (target) {
          '1' || 'a' => 'a',
          '2' || 'b' => 'b',
          _ => 'c',
        };
        if (handle != 'a') {
          await slow.future;
        }
        if (request.method == 'GET') {
          return http.Response(_profileHtml(handle, '${'abc'.indexOf(handle) + 1}'), 200);
        }
        return http.Response(_graphqlBody(handle, 'post by $handle'), 200);
      }),
    );

    final store = ThreadsFeedStore(
      ThreadsClient(httpClient: MockClient((_) async => http.Response('[]', 404))),
      direct,
      prefs,
      _AccountsStub(['a', 'b', 'c']),
    );

    final refreshing = store.refresh();

    // Give account a's two requests time to land while b and c stay stuck —
    // the departure pacing keeps its jitter even with a zero floor, so this
    // window is generous; the loop exits the moment the state paints.
    for (var i = 0; i < 200 && store.state.isEmpty; i++) {
      await Future<void>.delayed(const Duration(milliseconds: 25));
    }

    expect(
      store.state.map((p) => p.text),
      contains('post by a'),
      reason: 'the feed must paint the first answered account, not wait for all',
    );

    slow.complete();
    await refreshing;
    expect(store.state, hasLength(3));
  });

  // Each guest read cost two paced round-trips: the profile HTML (for the LSD
  // token and user id) and then the GraphQL call. Once the id is known and an
  // LSD is in hand, the HTML fetch is pure overhead — at two seconds of
  // deliberate pacing per request, it doubled the time the tab took to load.
  test('a known account with a fresh LSD costs one request, not two', () async {
    final requests = <String>[];
    final prefs = _prefs(userIds: '{"a":"1","b":"2"}');
    final direct = ThreadsDirectClient(
      prefs,
      minGap: Duration.zero,
      httpClient: MockClient((request) async {
        requests.add('${request.method} ${request.url.path}');
        if (request.method == 'GET') {
          final handle = request.url.path.substring(2);
          return http.Response(_profileHtml(handle, handle == 'a' ? '1' : '2'), 200);
        }
        final id =
            RegExp(r'%22userID%22%3A%22(\d+)%22').firstMatch(request.body)?.group(1) ??
            RegExp(r'"userID":"(\d+)"').firstMatch(request.body)?.group(1);
        return http.Response(_graphqlBody(id == '1' ? 'a' : 'b', 'post'), 200);
      }),
    );

    await direct.fetchGuestAccount('a');
    final before = requests.length;
    await direct.fetchGuestAccount('b');

    final forB = requests.skip(before).toList();
    expect(forB, hasLength(1), reason: 'the LSD from a\'s page serves b\'s GraphQL call: $forB');
    expect(forB.single, startsWith('POST'));
  });
}
