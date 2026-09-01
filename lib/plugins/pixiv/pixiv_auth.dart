import 'dart:convert';
import 'dart:math';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

/// Browser OAuth (PKCE) for Pixiv — same flow community clients like Pixez use.
///
/// Read-only: only authorization_code and refresh_token grants; no write scopes.
class PixivAuth {
  static const clientId = 'MOBrBDS8blbauoSck0ZfDbtuzpyT';
  static const clientSecret = 'lsACyCD94FhDUtGTXi3QzcFE2uU1hqtDaKeqrdwj';
  static const authTokenUrl = 'https://oauth.secure.pixiv.net/auth/token';
  static const loginUrl = 'https://app-api.pixiv.net/web/v1/login';
  static const redirectUri = 'https://app-api.pixiv.net/web/v1/users/auth/pixiv/callback';

  final http.Client httpClient;

  PixivAuth({http.Client? httpClient}) : httpClient = httpClient ?? http.Client();

  /// PKCE pair for a single login attempt.
  static ({String verifier, String challenge}) generatePkce() {
    final random = Random.secure();
    final bytes = List<int>.generate(32, (_) => random.nextInt(256));
    final verifier = base64Url.encode(bytes).replaceAll('=', '');
    final digest = sha256.convert(utf8.encode(verifier));
    final challenge = base64Url.encode(digest.bytes).replaceAll('=', '');
    return (verifier: verifier, challenge: challenge);
  }

  /// Pixiv login page — username, password, and 2FA happen here.
  static Uri loginUri({required String codeChallenge}) => Uri.parse(loginUrl).replace(queryParameters: {
        'code_challenge': codeChallenge,
        'code_challenge_method': 'S256',
        'client': 'pixiv-android',
      });

  /// Authorization code from the redirect, or null when this URL is not the callback.
  static String? codeFrom(Uri uri) {
    if (uri.scheme != 'pixiv' && !uri.toString().startsWith(redirectUri)) {
      return null;
    }
    final code = uri.queryParameters['code'];
    return (code == null || code.isEmpty) ? null : code;
  }

  /// OAuth denial or other error surfaced in the redirect query.
  static bool deniedIn(Uri uri) {
    if (uri.scheme != 'pixiv' && !uri.toString().startsWith(redirectUri)) {
      return false;
    }
    final error = uri.queryParameters['error'];
    return error != null && error.isNotEmpty;
  }

  /// Trades the one-time code for access + refresh tokens.
  Future<PixivLoginTokens> exchangeCode({
    required String code,
    required String codeVerifier,
  }) async {
    late final http.Response response;
    try {
      response = await httpClient
          .post(
            Uri.parse(authTokenUrl),
            headers: {
              'User-Agent': 'PixivAndroidApp/5.0.234 (Android 11; Pixel 5)',
              'App-OS': 'android',
              'App-OS-Version': '11',
              'App-Version': '5.0.234',
              'Content-Type': 'application/x-www-form-urlencoded',
            },
            body: {
              'client_id': clientId,
              'client_secret': clientSecret,
              'grant_type': 'authorization_code',
              'code': code,
              'code_verifier': codeVerifier,
              'redirect_uri': redirectUri,
              'include_policy': 'true',
            },
          )
          .timeout(const Duration(seconds: 15));
    } catch (e) {
      throw PixivException(PixivErrorKind.network, '$e');
    }

    if (response.statusCode == 401 || response.statusCode == 403) {
      throw PixivException(PixivErrorKind.unauthorized, 'Pixiv rejected the login code');
    }
    if (response.statusCode != 200) {
      throw PixivException(PixivErrorKind.badResponse, 'HTTP ${response.statusCode} from token endpoint');
    }

    Object? decoded;
    try {
      decoded = jsonDecode(utf8.decode(response.bodyBytes));
    } catch (e) {
      throw PixivException(PixivErrorKind.badResponse, '$e');
    }

    final json = Json(decoded);
    final access = json['access_token'].string;
    final refresh = json['refresh_token'].string;
    final expiresIn = json['expires_in'].integer ?? 3600;
    if (access == null || access.isEmpty || refresh == null || refresh.isEmpty) {
      throw PixivException(PixivErrorKind.badResponse, 'token response missing tokens');
    }

    final user = json['user'];
    return PixivLoginTokens(
      accessToken: access,
      refreshToken: refresh,
      expiresIn: expiresIn,
      user: PixivAuthUser(
        id: user['id'].integer ?? int.tryParse(user['id'].string ?? '') ?? 0,
        name: user['name'].string?.trim() ?? '',
        account: user['account'].string?.trim() ?? '',
      ),
    );
  }
}

/// Tokens returned by the authorization-code exchange.
class PixivLoginTokens {
  final String accessToken;
  final String refreshToken;
  final int expiresIn;
  final PixivAuthUser user;

  const PixivLoginTokens({
    required this.accessToken,
    required this.refreshToken,
    required this.expiresIn,
    required this.user,
  });
}
