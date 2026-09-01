import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:xta/plugins/pixiv/pixiv_auth.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';

void main() {
  group('PixivAuth PKCE', () {
    test('generatePkce produces verifier and S256 challenge', () {
      final pkce = PixivAuth.generatePkce();
      expect(pkce.verifier.length, greaterThanOrEqualTo(32));
      expect(pkce.challenge, isNot(contains('=')));

      final digest = sha256.convert(utf8.encode(pkce.verifier));
      final expected = base64Url.encode(digest.bytes).replaceAll('=', '');
      expect(pkce.challenge, expected);
    });

    test('each generatePkce call is unique', () {
      final a = PixivAuth.generatePkce();
      final b = PixivAuth.generatePkce();
      expect(a.verifier, isNot(equals(b.verifier)));
    });

    test('loginUri includes PKCE parameters', () {
      final uri = PixivAuth.loginUri(codeChallenge: 'abc123');
      expect(uri.host, 'app-api.pixiv.net');
      expect(uri.path, '/web/v1/login');
      expect(uri.queryParameters['code_challenge'], 'abc123');
      expect(uri.queryParameters['code_challenge_method'], 'S256');
      expect(uri.queryParameters['client'], 'pixiv-android');
    });
  });

  group('PixivAuth codeFrom', () {
    test('reads code from https callback', () {
      final uri = Uri.parse(
        '${PixivAuth.redirectUri}?code=auth-code-1&via=login',
      );
      expect(PixivAuth.codeFrom(uri), 'auth-code-1');
    });

    test('reads code from pixiv:// scheme', () {
      final uri = Uri.parse('pixiv://account?code=auth-code-2');
      expect(PixivAuth.codeFrom(uri), 'auth-code-2');
    });

    test('reads code from pixiv://account/login path', () {
      final uri = Uri.parse('pixiv://account/login?code=auth-code-3');
      expect(PixivAuth.codeFrom(uri), 'auth-code-3');
    });

    test('returns null for unrelated URLs', () {
      expect(PixivAuth.codeFrom(Uri.parse('https://www.pixiv.net/')), isNull);
      expect(PixivAuth.codeFrom(Uri.parse('https://example.com/?code=x')), isNull);
    });

    test('returns null when code is missing', () {
      expect(PixivAuth.codeFrom(Uri.parse(PixivAuth.redirectUri)), isNull);
    });
  });

  group('PixivAuth deniedIn', () {
    test('detects error query on https callback', () {
      final uri = Uri.parse('${PixivAuth.redirectUri}?error=access_denied');
      expect(PixivAuth.deniedIn(uri), isTrue);
    });

    test('detects error query on pixiv scheme', () {
      final uri = Uri.parse('pixiv://account/login?error=access_denied');
      expect(PixivAuth.deniedIn(uri), isTrue);
    });

    test('returns false when no error', () {
      expect(PixivAuth.deniedIn(Uri.parse('pixiv://account/login?code=x')), isFalse);
      expect(PixivAuth.deniedIn(Uri.parse('https://www.pixiv.net/')), isFalse);
    });
  });

  group('PixivAuth exchangeCode', () {
    test('posts authorization_code grant and parses tokens', () async {
      final auth = PixivAuth(
        httpClient: MockClient((request) async {
          expect(request.url.toString(), PixivAuth.authTokenUrl);
          expect(request.body, contains('grant_type=authorization_code'));
          expect(request.body, contains('code=the-code'));
          expect(request.body, contains('code_verifier=verifier-xyz'));
          expect(request.body, contains('redirect_uri=${Uri.encodeComponent(PixivAuth.redirectUri)}'));
          return http.Response(
            jsonEncode({
              'access_token': 'access-new',
              'refresh_token': 'refresh-new',
              'expires_in': 3600,
              'user': {'id': '99', 'name': 'Artist', 'account': 'artist'},
            }),
            200,
            headers: {'content-type': 'application/json'},
          );
        }),
      );

      final tokens = await auth.exchangeCode(code: 'the-code', codeVerifier: 'verifier-xyz');
      expect(tokens.accessToken, 'access-new');
      expect(tokens.refreshToken, 'refresh-new');
      expect(tokens.user.name, 'Artist');
      expect(tokens.user.id, 99);
    });

    test('401 becomes unauthorized', () async {
      final auth = PixivAuth(
        httpClient: MockClient((_) async => http.Response('', 401)),
      );
      await expectLater(
        auth.exchangeCode(code: 'bad', codeVerifier: 'v'),
        throwsA(isA<PixivException>().having((e) => e.kind, 'kind', PixivErrorKind.unauthorized)),
      );
    });
  });
}
