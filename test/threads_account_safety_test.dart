import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';

/// Reading Threads with the reader's own session is the one thing in this app
/// that can cost them an account: Meta bans sessions that behave like scripts.
/// These are the habits that keep it looking like one person reading.
const _cookies = 'sessionid=s; csrftoken=c; ds_user_id=1; mid=m; ig_did=g';

http.Response _json(Object body, [int status = 200]) => http.Response(
  jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);

Object _searchResult(String handle) => {
  'users': [
    {'username': handle, 'pk': '42'},
  ],
};

Object _feed() => {
  'threads': [
    {
      'thread_items': [
        {
          'post': {
            'pk': '1',
            'code': 'c',
            'caption': {'text': 'hi'},
            'user': {'username': 'someone', 'full_name': 'Someone'},
          },
        },
      ],
    },
  ],
};

PrefServiceCache _prefs() => PrefServiceCache(
  cache: {
    optionPluginThreadsDirectCookies: _cookies,
    optionPluginThreadsDirectBearer: '',
    optionPluginThreadsDirectDeviceId: 'device-1',
    optionPluginThreadsDirectCooldownUntil: '',
    optionPluginThreadsUserIds: '',
    optionPluginThreadsUseSessionApis: true,
  },
);

void main() {
  group('requests leave one at a time', () {
    test('two reads started together do not reach Meta together', () async {
      final prefs = _prefs();
      var inFlight = 0;
      var maxInFlight = 0;

      final client = ThreadsDirectClient(
        prefs,
        // A real gap, so overlap is a question the test can actually ask.
        minGap: const Duration(milliseconds: 30),
        httpClient: MockClient((request) async {
          inFlight++;
          maxInFlight = inFlight > maxInFlight ? inFlight : maxInFlight;
          await Future<void>.delayed(const Duration(milliseconds: 20));
          inFlight--;
          return _json(
            request.url.path.contains('search')
                ? _searchResult(request.url.queryParameters['q'] ?? 'a')
                : _feed(),
          );
        }),
      );

      // Concurrent callers are exactly what the timeline, the tab and a group
      // feed produce; the old pacing let them all fire at the same instant.
      await Future.wait([
        client.fetchUserThreads('a'),
        client.fetchUserThreads('b'),
      ]);

      expect(maxInFlight, 1, reason: 'a burst is what gets a session flagged');
    });
  });

  group('an account id is asked for once', () {
    test(
      'a second read reuses the stored id instead of searching again',
      () async {
        final prefs = _prefs();
        var searches = 0;

        final client = ThreadsDirectClient(
          prefs,
          minGap: Duration.zero,
          httpClient: MockClient((request) async {
            if (request.url.path.contains('search')) {
              searches++;
              return _json(_searchResult('someone'));
            }
            return _json(_feed());
          }),
        );

        await client.fetchUserThreads('someone');
        await client.fetchUserThreads('someone');

        expect(
          searches,
          1,
          reason: 'repeating an identical search is the signature of a script',
        );
        expect(prefs.get<String>(optionPluginThreadsUserIds), contains('42'));
      },
    );

    test(
      'the id survives a restart, so a fresh client does not search either',
      () async {
        final prefs = _prefs();
        await prefs.set(
          optionPluginThreadsUserIds,
          jsonEncode({'someone': '42'}),
        );
        var searches = 0;

        final client = ThreadsDirectClient(
          prefs,
          minGap: Duration.zero,
          httpClient: MockClient((request) async {
            if (request.url.path.contains('search')) {
              searches++;
              return _json(_searchResult('someone'));
            }
            expect(
              request.url.path,
              contains('/42/'),
              reason: 'the remembered id is the one used',
            );
            return _json(_feed());
          }),
        );

        await client.fetchUserThreads('someone');

        expect(searches, 0);
      },
    );
  });

  group('backing off when Meta says to', () {
    test('a throttle is remembered past a restart', () async {
      final prefs = _prefs();
      final throttled = ThreadsDirectClient(
        prefs,
        minGap: Duration.zero,
        httpClient: MockClient(
          (_) async => http.Response('Please wait a few minutes', 429),
        ),
      );

      await expectLater(
        throttled.currentUser(),
        throwsA(
          isA<ThreadsException>().having(
            (e) => e.kind,
            'kind',
            ThreadsErrorKind.throttled,
          ),
        ),
      );
      expect(
        prefs.get<String>(optionPluginThreadsDirectCooldownUntil),
        isNotEmpty,
      );

      // A new client is what the reader gets by force-quitting and reopening.
      // The old cooldown lived only in memory, so that was a way to carry
      // straight on hammering a session Meta had just asked to stop.
      var reached = false;
      final restarted = ThreadsDirectClient(
        prefs,
        minGap: Duration.zero,
        httpClient: MockClient((_) async {
          reached = true;
          return _json(_feed());
        }),
      );

      await expectLater(
        restarted.currentUser(),
        throwsA(
          isA<ThreadsException>().having(
            (e) => e.kind,
            'kind',
            ThreadsErrorKind.sessionSuspended,
          ),
        ),
      );
      expect(
        reached,
        isFalse,
        reason: 'nothing may reach Meta while the session is parked',
      );
    });

    test('an expired cooldown lets reading resume and clears itself', () async {
      final prefs = _prefs();
      await prefs.set(
        optionPluginThreadsDirectCooldownUntil,
        DateTime.now().subtract(const Duration(minutes: 1)).toIso8601String(),
      );

      final client = ThreadsDirectClient(
        prefs,
        minGap: Duration.zero,
        httpClient: MockClient(
          (_) async => _json({
            'user': {'username': 'me', 'pk': '1'},
          }),
        ),
      );

      await client.currentUser();
      expect(
        prefs.get<String>(optionPluginThreadsDirectCooldownUntil),
        isEmpty,
      );
    });
  });
}
