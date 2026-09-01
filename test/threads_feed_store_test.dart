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
  _AccountsStub(List<ThreadsAccount> accounts) {
    update(accounts);
  }

  @override
  Future<void> load() async {}
}

void main() {
  test('refresh loads Accounts even when a Bearer is pasted', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginThreadsDirectCookies: '',
        optionPluginThreadsDirectBearer: 'IGT:2:secret',
        optionPluginThreadsDirectDeviceId: 'device-1',
        optionPluginThreadsInstance: '',
        optionPluginThreadsUserIds: '{}',
        optionPluginThreadsDirectCooldownUntil: '',
      },
    );

    var followingCalled = false;
    final direct = ThreadsDirectClient(
      prefs,
      minGap: Duration.zero,
      httpClient: MockClient((request) async {
        if (request.url.host == 'i.instagram.com') {
          followingCalled = true;
          return http.Response('{"threads":[]}', 200);
        }
        if (request.method == 'GET' && request.url.path == '/@instagram') {
          return http.Response(
            r'<html><script>["LSD",[],{"token":"tok"}]</script>'
            r'<script>{"username":"instagram","pk":"63404918397"}</script></html>',
            200,
          );
        }
        if (request.method == 'POST' && request.url.path == '/api/graphql') {
          return http.Response(
            '''{"data":{"mediaData":{"threads":[{"thread_items":[{"post":{"pk":"1","code":"c","caption":{"text":"account post"},"user":{"username":"instagram","full_name":"IG"}}}]}]}}}''',
            200,
          );
        }
        return http.Response('unexpected ${request.url}', 500);
      }),
    );

    final store = ThreadsFeedStore(
      ThreadsClient(
        httpClient: MockClient((_) async => http.Response('[]', 404)),
      ),
      direct,
      prefs,
      _AccountsStub([
        const ThreadsAccount(handle: 'instagram', name: 'instagram'),
      ]),
    );

    await store.refresh(force: true);
    expect(store.state.map((p) => p.text), ['account post']);
    expect(followingCalled, isFalse);
  });

  test('refresh falls back to guest when RSSHub returns nothing', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginThreadsDirectCookies: '',
        optionPluginThreadsDirectBearer: '',
        optionPluginThreadsDirectDeviceId: 'device-1',
        optionPluginThreadsInstance: 'https://rsshub.example.org',
        optionPluginThreadsUserIds: '{}',
        optionPluginThreadsDirectCooldownUntil: '',
      },
    );

    var guestGraphql = false;
    final direct = ThreadsDirectClient(
      prefs,
      minGap: Duration.zero,
      httpClient: MockClient((request) async {
        if (request.method == 'GET' && request.url.path == '/@instagram') {
          return http.Response(
            r'<html><script>["LSD",[],{"token":"tok"}]</script>'
            r'<script>{"props":{"user_id":"63404918397"}}</script></html>',
            200,
          );
        }
        if (request.method == 'POST' && request.url.path == '/api/graphql') {
          guestGraphql = true;
          return http.Response(
            '''{"data":{"mediaData":{"threads":[{"thread_items":[{"post":{"pk":"9","code":"g","caption":{"text":"from guest"},"user":{"username":"instagram","full_name":"IG"}}}]}]}}}''',
            200,
          );
        }
        return http.Response('unexpected ${request.url}', 500);
      }),
    );

    final store = ThreadsFeedStore(
      ThreadsClient(
        httpClient: MockClient((_) async => http.Response('{"items":[]}', 200)),
      ),
      direct,
      prefs,
      _AccountsStub([
        const ThreadsAccount(handle: 'instagram', name: 'instagram'),
      ]),
    );

    await store.refresh(force: true);
    expect(guestGraphql, isTrue);
    expect(store.state.map((p) => p.text), ['from guest']);
  });

  test('soft refresh failure keeps prior posts on screen', () async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginThreadsDirectCookies: '',
        optionPluginThreadsDirectBearer: '',
        optionPluginThreadsDirectDeviceId: 'device-1',
        optionPluginThreadsInstance: '',
        optionPluginThreadsUserIds: '{}',
        optionPluginThreadsDirectCooldownUntil: '',
      },
    );

    var fail = false;
    final direct = ThreadsDirectClient(
      prefs,
      minGap: Duration.zero,
      httpClient: MockClient((request) async {
        if (fail) {
          return http.Response('down', 500);
        }
        if (request.method == 'GET' && request.url.path == '/@instagram') {
          return http.Response(
            r'<html><script>["LSD",[],{"token":"tok"}]</script>'
            r'<script>{"props":{"user_id":"63404918397"}}</script></html>',
            200,
          );
        }
        if (request.method == 'POST' && request.url.path == '/api/graphql') {
          return http.Response(
            '''{"data":{"mediaData":{"threads":[{"thread_items":[{"post":{"pk":"1","code":"c","caption":{"text":"kept"},"user":{"username":"instagram","full_name":"IG"}}}]}]}}}''',
            200,
          );
        }
        return http.Response('unexpected ${request.url}', 500);
      }),
    );

    final store = ThreadsFeedStore(
      ThreadsClient(
        httpClient: MockClient((_) async => http.Response('[]', 404)),
      ),
      direct,
      prefs,
      _AccountsStub([
        const ThreadsAccount(handle: 'instagram', name: 'instagram'),
      ]),
    );

    await store.refresh(force: true);
    expect(store.state.single.text, 'kept');

    fail = true;
    await store.refresh(force: true);
    expect(store.state.single.text, 'kept');
    expect(store.error, isNull);
  });

  test(
    'refresh with cookies still uses guest unless session APIs are opted in',
    () async {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginThreadsDirectCookies:
              'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g',
          optionPluginThreadsDirectBearer: '',
          optionPluginThreadsDirectDeviceId: 'device-1',
          optionPluginThreadsInstance: '',
          optionPluginThreadsUserIds: '{"instagram":"63404918397"}',
          optionPluginThreadsDirectCooldownUntil: '',
          optionPluginThreadsGuestLsd: 'tok',
          optionPluginThreadsGuestLsdAt: DateTime.now().toIso8601String(),
        },
      );

      var textFeed = false;
      final direct = ThreadsDirectClient(
        prefs,
        minGap: Duration.zero,
        httpClient: MockClient((request) async {
          if (request.url.path.contains('/text_feed/')) {
            textFeed = true;
            return http.Response('{"threads":[]}', 200);
          }
          if (request.method == 'POST' && request.url.path == '/api/graphql') {
            return http.Response(
              '''{"data":{"mediaData":{"threads":[{"thread_items":[{"post":{"pk":"1","code":"c","caption":{"text":"guest default"},"user":{"username":"instagram","full_name":"IG"}}}]}]}}}''',
              200,
            );
          }
          return http.Response('unexpected ${request.url}', 500);
        }),
      );

      final store = ThreadsFeedStore(
        ThreadsClient(
          httpClient: MockClient((_) async => http.Response('[]', 404)),
        ),
        direct,
        prefs,
        _AccountsStub([
          const ThreadsAccount(handle: 'instagram', name: 'instagram'),
        ]),
      );

      await store.refresh(force: true);
      expect(textFeed, isFalse);
      expect(store.state.map((p) => p.text), ['guest default']);
    },
  );
}
