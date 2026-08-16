import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';

http.Response _json(Object body, [int status = 200]) => http.Response(
  jsonEncode(body),
  status,
  headers: {'content-type': 'application/json'},
);

Map<String, dynamic> _profile(String acct) => {
  'id': '9',
  'acct': acct,
  'username': acct.split('@').first,
  'display_name': 'Reader',
  'url': 'https://example.social/@${acct.split('@').first}',
};

/// First host never answers; the walk timeout must move on.
class _HangingThenOkClient extends http.BaseClient {
  _HangingThenOkClient(this.asked);

  final List<String> asked;

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    asked.add(request.url.host);
    if (request.url.host == 'slow.place') {
      return Completer<http.StreamedResponse>().future;
    }
    final body = utf8.encode(jsonEncode(_profile('reader@fallback.place')));
    return http.StreamedResponse(
      Stream<List<int>>.value(body),
      200,
      headers: {'content-type': 'application/json'},
    );
  }
}

void main() {
  group('mastodonInstanceCandidates', () {
    test(
      'asks the account\'s own instance first — it is the only complete one',
      () {
        final candidates = mastodonInstanceCandidates('reader@example.social');

        expect(candidates.first, 'https://example.social');
        expect(
          candidates.skip(1),
          kMastodonDefaultInstances,
          reason: 'defaults follow, in their stated order',
        );
      },
    );

    test(
      'the reader\'s instances come between the origin and the defaults',
      () {
        final candidates = mastodonInstanceCandidates(
          'reader@example.social',
          configured: ['https://my.home', 'second.place'],
        );

        expect(candidates.take(3), [
          'https://example.social',
          'https://my.home',
          'https://second.place',
        ]);
      },
    );

    test('a bare local name has no origin, so the reader\'s home leads', () {
      final candidates = mastodonInstanceCandidates(
        'reader',
        configured: ['https://my.home'],
      );

      expect(candidates.first, 'https://my.home');
      expect(candidates, isNot(contains(startsWith('https://reader'))));
    });

    test(
      'never asks the same instance twice, however many ways it was named',
      () {
        final candidates = mastodonInstanceCandidates(
          'reader@mastodon.social',
          configured: ['mastodon.social', 'https://mastodon.social/'],
        );

        expect(
          candidates.where((e) => e == 'https://mastodon.social'),
          hasLength(1),
        );
        expect(candidates.first, 'https://mastodon.social');
      },
    );

    test(
      'with nothing configured there is still a full list — that is the point',
      () {
        expect(mastodonInstanceCandidates('reader'), kMastodonDefaultInstances);
      },
    );

    test('junk in the configured list is dropped rather than asked', () {
      final candidates = mastodonInstanceCandidates(
        'reader',
        configured: ['not a host', ''],
      );

      expect(candidates, kMastodonDefaultInstances);
    });
  });

  group('firstInstanceThat', () {
    test(
      'a miss on one instance moves to the next, and the winner\'s answer is returned',
      () async {
        final asked = <String>[];
        final client = MastodonClient(
          httpClient: MockClient((request) async {
            asked.add(request.url.host);
            if (request.url.host == 'example.social') {
              return http.Response('', 404);
            }
            return _json(_profile('reader@example.social'));
          }),
        );

        final profile = await client.lookupAnywhere([
          'https://example.social',
          'https://fallback.place',
        ], 'reader@example.social');

        expect(asked, ['example.social', 'fallback.place']);
        expect(profile.acct, 'reader@example.social');
      },
    );

    test(
      'when every instance fails, the most telling failure is the one reported',
      () async {
        final client = MastodonClient(
          httpClient: MockClient((request) async {
            // The throttle explains more than the 404 the walk ends on.
            return http.Response('', request.url.host == 'a.place' ? 429 : 404);
          }),
        );

        await expectLater(
          client.lookupAnywhere([
            'https://a.place',
            'https://b.place',
          ], 'reader'),
          throwsA(
            isA<MastodonException>().having(
              (e) => e.kind,
              'kind',
              MastodonErrorKind.rateLimited,
            ),
          ),
        );
      },
    );

    test(
      'a hanging first instance gives up on the walk timeout and asks the next',
      () async {
        final asked = <String>[];
        final client = MastodonClient(
          firstWalkTimeout: const Duration(milliseconds: 40),
          fallbackWalkTimeout: const Duration(milliseconds: 40),
          httpClient: _HangingThenOkClient(asked),
        );

        final profile = await client.lookupAnywhere([
          'https://slow.place',
          'https://fallback.place',
        ], 'reader@fallback.place');

        expect(asked, ['slow.place', 'fallback.place']);
        expect(profile.acct, 'reader@fallback.place');
      },
    );

    test('an empty candidate list is not configured, not a crash', () async {
      final client = MastodonClient(
        httpClient: MockClient((_) async => _json(const [])),
      );

      await expectLater(
        client.lookupAnywhere(const [], 'reader'),
        throwsA(
          isA<MastodonException>().having(
            (e) => e.kind,
            'kind',
            MastodonErrorKind.notConfigured,
          ),
        ),
      );
    });
  });

  group('mastodonConfiguredInstances', () {
    test('home first, then the added list, in order', () {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginMastodonInstance: 'https://my.home',
          optionPluginMastodonInstances: jsonEncode([
            'https://a.place',
            'https://b.place',
          ]),
        },
      );

      expect(mastodonConfiguredInstances(prefs), [
        'https://my.home',
        'https://a.place',
        'https://b.place',
      ]);
    });

    test(
      'nothing configured is an empty list, and corrupt storage reads the same',
      () {
        expect(
          mastodonConfiguredInstances(PrefServiceCache(cache: {})),
          isEmpty,
        );
        expect(
          mastodonConfiguredInstances(
            PrefServiceCache(
              cache: {optionPluginMastodonInstances: 'not json'},
            ),
          ),
          isEmpty,
        );
      },
    );
  });

  group('appendUniqueMastodonPosts', () {
    MastodonPost post(String id) => MastodonPost(
      id: id,
      acct: 'a@b.social',
      authorName: 'A',
      text: id,
      url: 'https://b.social/@a/$id',
    );

    test('keeps order and drops ids already shown', () {
      final merged = appendUniqueMastodonPosts(
        [post('3'), post('2')],
        [post('2'), post('1')],
      );
      expect(merged.map((e) => e.id), ['3', '2', '1']);
    });
  });
}
