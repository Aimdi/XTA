import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/utils/read_request_scope.dart';

void main() {
  testWidgets('Threads quiet retries still wait for its session request spacing', (tester) async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginThreadsDirectCookies: 'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g',
        optionPluginThreadsDirectBearer: '',
        optionPluginThreadsDirectDeviceId: 'device-1',
      },
    );
    var sends = 0;
    final client = ThreadsDirectClient(
      prefs,
      httpClient: MockClient((_) async {
        sends++;
        return http.Response('busy', 503);
      }),
    );
    final check = expectLater(client.currentUser(), throwsA(isA<Exception>()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    final beforeFirstGap = sends;
    await tester.pump(const Duration(seconds: 4));
    final afterFirstGap = sends;
    await tester.pump(const Duration(milliseconds: 1200));
    final beforeSecondGap = sends;
    await tester.pump(const Duration(seconds: 4));
    await check;
    expect(beforeFirstGap, 1);
    expect(afterFirstGap, 2);
    expect(beforeSecondGap, 2);
    expect(sends, 3);
  });

  testWidgets('one Hacker News page shares two retries across thirty story requests', (tester) async {
    var items = 0;
    final client = HackerNewsClient(
      httpClient: MockClient((request) async {
        if (request.url.path.contains('beststories')) {
          return http.Response(jsonEncode(List.generate(30, (i) => i + 1)), 200);
        }
        items++;
        return http.Response('busy', 503);
      }),
    );
    final check = expectLater(client.feed(HnFeed.best), throwsA(isA<HnException>()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    await check;
    expect(items, 32);
  });

  testWidgets('cancelling a Threads retry also cancels its pacing wait', (tester) async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginThreadsDirectCookies: 'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g',
        optionPluginThreadsDirectBearer: '',
        optionPluginThreadsDirectDeviceId: 'device-1',
      },
    );
    var sends = 0;
    final client = ThreadsDirectClient(
      prefs,
      httpClient: MockClient((_) async {
        sends++;
        return http.Response('busy', 503);
      }),
    );
    final scope = ReadRequestScope();
    final check = expectLater(
      scope.start(client.currentUser, timeout: const Duration(seconds: 20)),
      throwsA(isA<ReadCancelled>()),
    );
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    scope.cancel();
    await check;
    await tester.pump();
    expect(sends, 1);
  });

  testWidgets('a Threads session cooldown stops a queued retry with its original account error', (tester) async {
    final prefs = PrefServiceCache(
      cache: {
        optionPluginThreadsDirectCookies: 'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g',
        optionPluginThreadsDirectBearer: '',
        optionPluginThreadsDirectDeviceId: 'device-1',
      },
    );
    var sends = 0;
    final client = ThreadsDirectClient(
      prefs,
      httpClient: MockClient((_) async {
        sends++;
        return http.Response('busy', 503);
      }),
    );
    final check = expectLater(
      client.currentUser(),
      throwsA(isA<ThreadsException>().having((error) => error.kind, 'kind', ThreadsErrorKind.sessionSuspended)),
    );
    await tester.pump();
    await prefs.set(
      optionPluginThreadsDirectCooldownUntil,
      DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
    );
    await tester.pump(const Duration(milliseconds: 400));
    await check;
    expect(sends, 1);
  });

  testWidgets('Substack fallback hosts and formats share one retry budget', (tester) async {
    const publication = SubstackPublication(subdomain: 'notes', name: 'Notes', baseUrl: 'https://notes.substack.com');
    final requests = <Uri>[];
    final client = SubstackClient(
      httpClient: MockClient((request) async {
        requests.add(request.url);
        return http.Response('unavailable', requests.length <= 5 ? 503 : 404);
      }),
    );
    final check = expectLater(client.fetchPosts(publication), throwsA(isA<Exception>()));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    await tester.pump(const Duration(milliseconds: 400));
    await tester.pump(const Duration(milliseconds: 1200));
    await check;
    expect(requests.length, requests.toSet().length + 2);
  });

  testWidgets('a standalone merged timeline shares retries across its followed accounts', (tester) async {
    var requests = 0;
    final client = BlueskyClient(
      httpClient: MockClient((_) async {
        requests++;
        return http.Response('busy', 503);
      }),
    );
    final cache = AccountPostCache<String>(dateOf: (_) => null, perAccount: 10);
    final check = expectLater(
      cache.merge(List.generate(6, (index) => 'reader$index.bsky.social'), (key) async {
        await client.getAuthorFeed(key);
        return <String>[];
      }),
      throwsA(isA<BlueskyException>()),
    );
    await tester.pump();
    for (var batch = 0; batch < 3; batch++) {
      await tester.pump(const Duration(milliseconds: 400));
      await tester.pump(const Duration(milliseconds: 1200));
    }
    await check;
    expect(requests, 8);
  });
}
