import 'dart:async';
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';

const _oauthRefusal = {
  'error': {
    'user_message': '',
    'message':
        'Error occurred at the OAuth process. Please check your Access Token to fix this. Error Message: invalid_grant',
    'reason': '',
  },
};

http.Response _json(Object? body, [int status = 200]) =>
    http.Response(jsonEncode(body), status, headers: {'content-type': 'application/json'});

http.Response _token(String access, {Object? user = const {'id': '7', 'name': 'Reader', 'account': 'reader'}}) =>
    _json({'access_token': access, 'refresh_token': 'refresh-next', 'expires_in': 3600, 'user': ?user});

bool _isTokenCall(http.BaseRequest request) => request.url.host == 'oauth.secure.pixiv.net';

void main() {
  late PrefServiceCache prefs;

  setUp(() {
    prefs = PrefServiceCache(
      cache: {
        optionPluginPixivRefreshToken: 'refresh-me',
        optionPluginPixivAccessToken: 'access-1',
        optionPluginPixivAccessExpiresAt: DateTime.now().add(const Duration(hours: 1)).toIso8601String(),
        optionPluginPixivShowR18: false,
        optionPluginPixivHideAi: false,
        optionPluginPixivIsPremium: false,
      },
    );
  });

  PixivClient client(Future<http.Response> Function(http.Request request) handler, {String locale = 'en'}) =>
      PixivClient(prefs, httpClient: MockClient(handler), locale: () => locale);

  group('Accept-Language', () {
    test('maps XTA locales onto the five languages Pixiv answers in', () {
      final mapped = {
        for (final locale in ['ja', 'ko', 'zh_Hans', 'zh', 'zh_Hant', 'zh-TW', 'en', 'de', 'pt_BR', 'nb_NO', 'be_Latn'])
          locale: pixivAcceptLanguage(locale),
      };
      expect(mapped, {
        'ja': 'ja',
        'ko': 'ko',
        'zh_Hans': 'zh-CN',
        'zh': 'zh-CN',
        'zh_Hant': 'zh-TW',
        'zh-TW': 'zh-TW',
        'en': 'en',
        'de': 'en',
        'pt_BR': 'en',
        'nb_NO': 'en',
        'be_Latn': 'en',
      });
    });

    test('every request carries the active locale, token requests included', () async {
      await prefs.set(optionPluginPixivAccessToken, '');
      final seen = <String, String?>{};
      final pixiv = client(locale: 'zh_Hant', (request) async {
        seen[request.url.host] = request.headers['Accept-Language'];
        return _isTokenCall(request) ? _token('access-2') : _json({'illusts': []});
      });

      await pixiv.following();
      expect(seen, {'oauth.secure.pixiv.net': 'zh-TW', 'app-api.pixiv.net': 'zh-TW'});
    });
  });

  group('refused tokens', () {
    test('a 400 about the OAuth process refreshes once and replays the request', () async {
      final bearers = <String?>[];
      var refreshes = 0;
      final pixiv = client((request) async {
        if (_isTokenCall(request)) {
          refreshes++;
          return _token('access-2');
        }
        bearers.add(request.headers['Authorization']);
        return bearers.length == 1 ? _json(_oauthRefusal, 400) : _json({'illusts': []});
      });

      final page = await pixiv.following();
      expect(page.illusts, isEmpty);
      expect(refreshes, 1);
      expect(bearers, ['Bearer access-1', 'Bearer access-2']);
      expect(prefs.get<String>(optionPluginPixivAccessToken), 'access-2');
    });

    test('requests refused together share one refresh', () async {
      var refreshes = 0;
      final refreshed = Completer<void>();
      final pixiv = client((request) async {
        if (_isTokenCall(request)) {
          refreshes++;
          await refreshed.future;
          return _token('access-2');
        }
        return request.headers['Authorization'] == 'Bearer access-1'
            ? _json(_oauthRefusal, 400)
            : _json({'illusts': []});
      });

      final both = Future.wait([pixiv.following(), pixiv.recommended()]);
      await pumpEventQueue();
      refreshed.complete();
      await both;
      expect(refreshes, 1);
    });

    test('a refusal after the refresh is reported, not retried forever', () async {
      var refreshes = 0;
      var calls = 0;
      final pixiv = client((request) async {
        if (_isTokenCall(request)) {
          refreshes++;
          return _token('access-$refreshes');
        }
        calls++;
        return _json(_oauthRefusal, 400);
      });

      await expectLater(
        pixiv.following(),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.unauthorized)),
      );
      expect((refreshes, calls), (1, 2));
    });

    test('any other 400 is a bad response and leaves the token alone', () async {
      var refreshes = 0;
      final pixiv = client((request) async {
        if (_isTokenCall(request)) refreshes++;
        return _json({
          'error': {'message': 'Invalid illust_id'},
        }, 400);
      });

      await expectLater(
        pixiv.illustDetail(1),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.badResponse)),
      );
      expect(refreshes, 0);
    });
  });

  group('dropped connections', () {
    http.ClientException dropped() => http.ClientException('Connection closed before full header was received');

    test('a connection closed before any header is sent again once', () async {
      var calls = 0;
      final pixiv = client((request) async {
        if (++calls == 1) throw dropped();
        return _json({'illusts': []});
      });

      await pixiv.following();
      expect(calls, 2);
    });

    test('a second drop is a network error', () async {
      var calls = 0;
      final pixiv = client((request) async {
        calls++;
        throw dropped();
      });

      await expectLater(
        pixiv.following(),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.network)),
      );
      expect(calls, 2);
    });

    test('other failures are not retried', () async {
      var calls = 0;
      final pixiv = client((request) async {
        calls++;
        throw http.ClientException('Connection refused');
      });

      await expectLater(pixiv.following(), throwsA(isA<PixivException>()));
      expect(calls, 1);
    });
  });

  group('public transport', () {
    test('getJson without auth sends no token and never refreshes', () async {
      await prefs.set(optionPluginPixivAccessToken, '');
      final requests = <http.Request>[];
      final pixiv = client((request) async {
        requests.add(request);
        return _json({'ok': true});
      });

      expect(await pixiv.getJson('/v1/walkthrough/illusts', auth: false), {'ok': true});
      expect(requests.single.url.host, 'app-api.pixiv.net');
      expect(requests.single.headers.containsKey('Authorization'), isFalse);
      expect(requests.single.headers['X-Client-Hash'], isNotEmpty);
    });

    test('the token never leaves the app API host', () async {
      final requests = <http.Request>[];
      final pixiv = client((request) async {
        requests.add(request);
        return _json({'illusts': []});
      });

      await pixiv.getNextJson('https://app-api.pixiv.net/v1/illust/ranking?offset=30');
      await pixiv.getNextJson('https://elsewhere.example/v1/illust/ranking?offset=30');
      expect(requests.map((r) => r.headers['Authorization']), ['Bearer access-1', null]);
      expect(requests.first.url.queryParameters['offset'], '30');
    });

    test('getText returns an HTML body as text', () async {
      final pixiv = client((request) async {
        expect(request.headers['Accept'], 'text/html');
        expect(request.url.queryParameters['id'], '9');
        return http.Response.bytes(utf8.encode('<html><body>小説</body></html>'), 200);
      });

      expect(await pixiv.getText('/webview/v2/novel', query: {'id': '9'}), '<html><body>小説</body></html>');
    });

    test('postForm sends a form and reads an empty answer as null', () async {
      final pixiv = client((request) async {
        expect(request.method, 'POST');
        expect(request.headers['Content-Type'], startsWith('application/x-www-form-urlencoded'));
        expect(request.bodyFields, {'illust_id': '5', 'restrict': 'public'});
        return http.Response('', 200);
      });

      expect(await pixiv.postForm('/v2/illust/bookmark/add', {'illust_id': '5', 'restrict': 'public'}), isNull);
    });

    test('illustPageFrom follows the reader\'s filters unless it is their own list', () async {
      await prefs.set(optionPluginPixivHideAi, true);
      final pixiv = client((_) async => _json(null));
      Map<String, Object?> work(int id, {int restrict = 0, int ai = 1}) => {
        'id': id,
        'type': 'illust',
        'image_urls': {'medium': 'https://i.pximg.net/$id.jpg'},
        'user': {'id': 1},
        'x_restrict': restrict,
        'illust_ai_type': ai,
      };
      final json = {
        'illusts': [work(1), work(2, restrict: 1), work(3, ai: 2)],
        'next_url': 'https://app-api.pixiv.net/next',
      };

      final feed = pixiv.illustPageFrom(json);
      expect(feed.illusts.map((i) => i.id), [1]);
      expect(feed.nextUrl, 'https://app-api.pixiv.net/next');
      expect(pixiv.illustPageFrom(json, ownList: true).illusts.map((i) => i.id), [1, 2, 3]);
      expect(pixiv.illustPageFrom(json, includeR18: true, includeAi: true).illusts, hasLength(3));
    });
  });

  group('premium', () {
    test('is stored from the token response and cleared on sign-out', () async {
      final pixiv = client(
        (_) async => _token('access-2', user: const {'id': '7', 'name': 'Reader', 'account': 'r', 'is_premium': true}),
      );

      final user = await pixiv.refreshAccessToken();
      expect((user.isPremium, pixiv.isPremium, prefs.get<bool>(optionPluginPixivIsPremium)), (true, true, true));

      await pixiv.signOut();
      expect(pixiv.isPremium, isFalse);
    });

    test('a token response without a user keeps what was known', () async {
      await prefs.set(optionPluginPixivIsPremium, true);
      final pixiv = client((_) async => _token('access-2', user: null));

      await pixiv.refreshAccessToken();
      expect(pixiv.isPremium, isTrue);
    });
  });
}
