import 'dart:async';
import 'dart:convert';
import 'dart:typed_data';

import 'package:crypto/crypto.dart';
import 'package:http/http.dart' as http;
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_auth.dart';
import 'package:xta/plugins/pixiv/pixiv_image_source.dart';
import 'package:xta/plugins/pixiv/pixiv_lru_cache.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_ugoira.dart';
import 'package:xta/plugins/pixiv/pixiv_user_profile.dart';
import 'package:xta/utils/json.dart';

enum PixivErrorKind {
  notConfigured,
  network,
  unauthorized,
  rateLimited,
  notFound,
  badResponse,
}

class PixivException implements Exception {
  final PixivErrorKind kind;
  final String message;

  PixivException(this.kind, this.message);

  @override
  String toString() => 'PixivException{$kind: $message}';
}

class PixivIllustPage extends PixivPage<PixivIllust> {
  const PixivIllustPage({required List<PixivIllust> illusts, super.nextUrl}) : super(illusts);

  List<PixivIllust> get illusts => items;
}

/// The `Accept-Language` Pixiv localises tags and text for, from an XTA
/// locale such as `ja`, `zh_Hant` or `pt_BR`. Pixiv speaks five languages;
/// every other locale reads English rather than Pixiv's Japanese default.
String pixivAcceptLanguage(String locale) {
  final parts = locale.toLowerCase().split(RegExp('[-_]'));
  return switch (parts.first) {
    'ja' => 'ja',
    'ko' => 'ko',
    'zh' when parts.skip(1).any(const {'hant', 'tw', 'hk', 'mo'}.contains) => 'zh-TW',
    'zh' => 'zh-CN',
    _ => 'en',
  };
}

/// Pixiv's edge drops idle keep-alive sockets; the first request on a dead one
/// fails before any header arrives and succeeds when simply sent again.
bool _isDroppedConnection(Object error) => '$error'.contains('Connection closed before');

typedef _Send = Future<http.Response> Function(Map<String, String> headers);

/// Client for Pixiv's unofficial app API.
///
/// Auth is a pasted refresh token — same shape community clients use after
/// password login was removed. Follow and bookmark (`pixiv_bookmark_api.dart`)
/// are the write-backs; there is no compose.
///
/// Feature screens add their endpoints in their own `pixiv_<feature>_api.dart`
/// over the public transport ([getJson], [getNextJson], [getText], [postForm],
/// [illustPageFrom]) instead of growing this class.
class PixivClient {
  final http.Client httpClient;
  final BasePrefService prefs;
  final DateTime Function() clock;

  /// The active XTA locale; Pixiv translates tags into its language.
  final String Function() locale;

  /// Coalesces concurrent refresh calls — opening Following + Ranking used to
  /// stampede the token endpoint and stack several 15s timeouts.
  Future<PixivAuthUser>? _refreshInFlight;

  /// The last few ugoira frame archives, so replaying or saving one does not fetch it again.
  final _ugoiraArchives = PixivLruCache<String, Future<Uint8List>>(4);

  PixivClient(this.prefs, {http.Client? httpClient, DateTime Function()? clock, String Function()? locale})
    : httpClient = httpClient ?? http.Client(),
      clock = clock ?? DateTime.now,
      locale = locale ?? Intl.getCurrentLocale;

  static const _timeout = Duration(seconds: 15);
  static const _apiHost = 'app-api.pixiv.net';
  static const _apiBase = 'https://$_apiHost';
  static const _userAgent = 'PixivAndroidApp/5.0.234 (Android 11; Pixel 5)';

  /// The salt behind `X-Client-Hash`, as widely documented as the id above.
  ///
  /// Pixiv's token endpoint checks that every request carries the current time
  /// and an MD5 of that time plus this salt — the official app always sends the
  /// pair, and community clients have had to since 2017. Without it the token
  /// endpoint refuses the request no matter how valid the refresh token is,
  /// which reads as "wrong token" to a reader who pasted the right one.
  static const clientHashSalt =
      '28c1fdd170a5204386cb1313c7077b34f83e4aaf4aa829ce78c231e05b0bae2c';

  String get _refreshToken =>
      (prefs.get<String>(optionPluginPixivRefreshToken) ?? '').trim();
  String get _storedAccessToken => (prefs.get<String>(optionPluginPixivAccessToken) ?? '').trim();
  bool get showR18 => prefs.get<bool>(optionPluginPixivShowR18) == true;
  bool get hideAi => prefs.get<bool>(optionPluginPixivHideAi) == true;
  bool get isPremium => prefs.get<bool>(optionPluginPixivIsPremium) == true;

  static String _pad(int value, [int width = 2]) =>
      '$value'.padLeft(width, '0');

  /// The timestamp exactly as the official app writes it: seconds, no
  /// fraction, and a `+00:00` offset rather than `Z`. The hash is of this
  /// string, so the format is part of the contract.
  String _clientTime() {
    final now = clock().toUtc();

    return '${_pad(now.year, 4)}-${_pad(now.month)}-${_pad(now.day)}'
        'T${_pad(now.hour)}:${_pad(now.minute)}:${_pad(now.second)}+00:00';
  }

  /// Computed per request rather than once: the server compares the time
  /// against its own clock, and a stale pair is as refused as a missing one.
  Map<String, String> get _baseHeaders {
    final time = _clientTime();

    return {
      'User-Agent': _userAgent,
      'App-OS': 'android',
      'App-OS-Version': '11',
      'App-Version': '5.0.234',
      'Accept': 'application/json',
      'Accept-Language': pixivAcceptLanguage(locale()),
      'X-Client-Time': time,
      'X-Client-Hash': md5
          .convert(utf8.encode('$time$clientHashSalt'))
          .toString(),
    };
  }

  Map<String, String> _bearer(String token) => {..._baseHeaders, 'Authorization': 'Bearer $token'};

  Future<http.Response> _send(Future<http.Response> Function() run, {bool retried = false}) async {
    try {
      return await run().timeout(_timeout);
    } catch (e) {
      if (!retried && _isDroppedConnection(e)) {
        return _send(run, retried: true);
      }
      throw PixivException(PixivErrorKind.network, '$e');
    }
  }

  /// Pixiv answers an expired or revoked access token with HTTP 400 and an
  /// `error.message` about "the OAuth process" far more often than with 401.
  bool _isTokenRefused(http.Response response) =>
      response.statusCode == 401 ||
      (response.statusCode == 400 &&
          (Json(_tryDecode(response))['error']['message'].string ?? '').contains('OAuth'));

  /// Pixiv rate-limits with a JSON `error.message` of "Rate Limit" on a 4xx
  /// (usually 400 or 403) far more often than with a 429.
  bool _isRateLimited(http.Response response) =>
      response.statusCode == 429 ||
      (response.statusCode >= 400 &&
          response.statusCode < 500 &&
          (Json(_tryDecode(response))['error']['message'].string ?? '').toLowerCase().contains('rate limit'));

  void _throwForStatus(http.Response response, Uri uri) {
    if (_isRateLimited(response)) {
      throw PixivException(PixivErrorKind.rateLimited, '$uri: ${response.statusCode}');
    }
    if (_isTokenRefused(response) || response.statusCode == 403) {
      throw PixivException(
        PixivErrorKind.unauthorized,
        '$uri: ${response.statusCode}',
      );
    }
    if (response.statusCode == 404) {
      throw PixivException(PixivErrorKind.notFound, '$uri: 404');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw PixivException(
        PixivErrorKind.badResponse,
        '$uri: ${response.statusCode}',
      );
    }
  }

  /// An image server holds no token, so a refusal from it is never the token's fault.
  static void _throwForImageStatus(http.Response response, Uri uri) {
    final status = response.statusCode;
    if (status >= 200 && status < 300) return;
    final kind = switch (status) {
      404 || 410 => PixivErrorKind.notFound,
      429 => PixivErrorKind.rateLimited,
      _ => PixivErrorKind.badResponse,
    };
    throw PixivException(kind, '$uri: $status');
  }

  /// The token endpoint says in its body exactly why it refused — a token that
  /// is wrong (`invalid_grant`) reads differently from a request it does not
  /// trust (`invalid_client`). Losing that to a bare 403 turned every failure
  /// into "check your token", including the ones no token could fix.
  void _throwForAuthStatus(http.Response response) {
    if (response.statusCode >= 200 && response.statusCode < 300) {
      return;
    }

    final body = Json(_tryDecode(response));
    final detail =
        body['errors']['system']['message'].string ??
        body['error_description'].string ??
        body['error'].string ??
        'HTTP ${response.statusCode}';

    final unauthorized =
        response.statusCode == 401 || response.statusCode == 403;
    throw PixivException(
      unauthorized ? PixivErrorKind.unauthorized : PixivErrorKind.badResponse,
      'token endpoint: $detail (HTTP ${response.statusCode})',
    );
  }

  Object? _tryDecode(http.Response response) {
    try {
      return jsonDecode(utf8.decode(response.bodyBytes));
    } catch (_) {
      return null;
    }
  }

  Object? _decode(http.Response response, Uri uri) {
    try {
      return jsonDecode(utf8.decode(response.bodyBytes));
    } catch (e) {
      throw PixivException(PixivErrorKind.badResponse, '$uri: $e');
    }
  }

  /// Exchanges the refresh token; stores access token + expiry; returns the user.
  Future<PixivAuthUser> refreshAccessToken() async {
    final inFlight = _refreshInFlight;
    if (inFlight != null) {
      return inFlight;
    }

    final started = _refreshAccessTokenBody();
    _refreshInFlight = started;
    try {
      return await started;
    } finally {
      if (identical(_refreshInFlight, started)) {
        _refreshInFlight = null;
      }
    }
  }

  Future<PixivAuthUser> _refreshAccessTokenBody() async {
    if (_refreshToken.isEmpty) {
      throw PixivException(PixivErrorKind.notConfigured, 'no refresh token');
    }

    final sent = _refreshToken;
    final response = await _send(
      () => httpClient.post(
        Uri.parse(PixivAuth.authTokenUrl),
        headers: {
          ..._baseHeaders,
          'Content-Type': 'application/x-www-form-urlencoded',
        },
        body: {
          'client_id': PixivAuth.clientId,
          'client_secret': PixivAuth.clientSecret,
          'grant_type': 'refresh_token',
          'include_policy': 'true',
          'refresh_token': sent,
        },
      ),
    );

    _throwForAuthStatus(response);
    final json = Json(_decode(response, Uri.parse(PixivAuth.authTokenUrl)));
    // The reader switched accounts while this was in flight: the answer
    // belongs to the old one, so the caller gets the account now in use.
    if (sent != _refreshToken) {
      return _refreshAccessTokenBody();
    }
    return _storeRefreshed(json);
  }

  Future<PixivAuthUser> _storeRefreshed(Json json) async {
    final access = json['access_token'].string;
    final refresh = json['refresh_token'].string;
    final expiresIn = json['expires_in'].integer ?? 3600;

    if (access == null || access.isEmpty) {
      throw PixivException(
        PixivErrorKind.badResponse,
        'token response missing access_token',
      );
    }

    await prefs.set(optionPluginPixivAccessToken, access);
    if (refresh != null && refresh.isNotEmpty) {
      await prefs.set(optionPluginPixivRefreshToken, refresh);
    }
    await prefs.set(
      optionPluginPixivAccessExpiresAt,
      clock().add(Duration(seconds: expiresIn - 60)).toIso8601String(),
    );

    final authUser = PixivAuthUser.fromJson(json['user'].raw);
    await _rememberUser(authUser, known: json['user'].exists);
    return authUser;
  }

  Future<void> _rememberUser(PixivAuthUser user, {bool known = true}) async {
    if (user.id != 0) {
      await prefs.set(optionPluginPixivUserId, user.id);
    }
    if (known) {
      await prefs.set(optionPluginPixivIsPremium, user.isPremium);
    }
  }

  Future<String> _accessToken() async {
    final existing = _storedAccessToken;
    final expiresRaw =
        prefs.get<String>(optionPluginPixivAccessExpiresAt) ?? '';
    final expires = DateTime.tryParse(expiresRaw);
    if (existing.isNotEmpty && expires != null && expires.isAfter(clock())) {
      return existing;
    }
    await refreshAccessToken();
    return _storedAccessToken;
  }

  /// A token for the replay after [refused] was turned away: one another
  /// request already swapped in is reused instead of refreshing again.
  Future<String> _reauthorize(String refused) async {
    final current = _storedAccessToken;
    if (current.isNotEmpty && current != refused) {
      return current;
    }
    await refreshAccessToken();
    return _storedAccessToken;
  }

  /// Warms a usable access token without forcing a refresh when one is still valid.
  Future<void> ensureAccessToken() async {
    await _accessToken();
  }

  /// User id for bookmarks — prefers the stored id, refreshes only when missing.
  Future<int> ensureUserId() async {
    await _accessToken();
    final existing = storedUserId;
    if (existing != null) {
      return existing;
    }
    final user = await refreshAccessToken();
    if (user.id == 0) {
      throw PixivException(PixivErrorKind.badResponse, 'token user has no id');
    }
    return user.id;
  }

  /// Confirms the refresh token still works.
  Future<PixivAuthUser> verify() => refreshAccessToken();

  /// Persists tokens from browser OAuth and returns the signed-in user.
  Future<PixivAuthUser> applyLoginTokens(PixivLoginTokens tokens) async {
    await prefs.set(optionPluginPixivAccessToken, tokens.accessToken);
    await prefs.set(optionPluginPixivRefreshToken, tokens.refreshToken);
    await prefs.set(
      optionPluginPixivAccessExpiresAt,
      DateTime.now()
          .add(Duration(seconds: tokens.expiresIn - 60))
          .toIso8601String(),
    );
    // Even an answer without a user id must not leave the last account's id
    // beside this token.
    await prefs.set(optionPluginPixivUserId, tokens.user.id);
    await _rememberUser(tokens.user);
    return tokens.user;
  }

  /// Makes [account] the one every request uses. Its access token is not
  /// kept, so the next request refreshes one from its refresh token.
  Future<void> switchTo(PixivAccount account) async {
    await prefs.set(optionPluginPixivRefreshToken, account.refreshToken);
    await prefs.set(optionPluginPixivAccessToken, '');
    await prefs.set(optionPluginPixivAccessExpiresAt, '');
    await prefs.set(optionPluginPixivUserId, account.userId);
    await prefs.set(optionPluginPixivIsPremium, account.isPremium);
  }

  /// Puts a pasted refresh [token] in use. Whose it is stays unknown until a
  /// token check names its owner, so no stored account takes it meanwhile.
  Future<void> useRefreshToken(String token) async {
    await prefs.set(optionPluginPixivRefreshToken, token);
    await prefs.set(optionPluginPixivAccessToken, '');
    await prefs.set(optionPluginPixivAccessExpiresAt, '');
    await prefs.set(optionPluginPixivUserId, 0);
    await prefs.set(optionPluginPixivIsPremium, false);
  }

  Future<void> signOut() async {
    await prefs.set(optionPluginPixivRefreshToken, '');
    await prefs.set(optionPluginPixivAccessToken, '');
    await prefs.set(optionPluginPixivAccessExpiresAt, '');
    await prefs.set(optionPluginPixivUserId, 0);
    await prefs.set(optionPluginPixivIsPremium, false);
  }

  int? get storedUserId {
    final id = prefs.get<int>(optionPluginPixivUserId) ?? 0;
    return id == 0 ? null : id;
  }

  Uri _uri(String path, Map<String, String>? query) {
    final uri = Uri.parse(path.startsWith('https://') ? path : '$_apiBase$path');
    return query == null || query.isEmpty ? uri : uri.replace(queryParameters: query);
  }

  /// Sends with the bearer token, refreshing once and replaying when Pixiv
  /// refuses it. The token only ever goes to the app API's own host.
  Future<http.Response> _call(Uri uri, _Send send, {required bool auth}) async {
    if (!auth || uri.host != _apiHost) {
      final response = await _send(() => send(_baseHeaders));
      _throwForStatus(response, uri);
      return response;
    }
    final token = await _accessToken();
    var response = await _send(() => send(_bearer(token)));
    if (_isTokenRefused(response)) {
      final fresh = await _reauthorize(token);
      response = await _send(() => send(_bearer(fresh)));
    }
    _throwForStatus(response, uri);
    return response;
  }

  /// GETs an app-API [path] (or an absolute app-API URL) and decodes its JSON.
  /// [auth] false sends no token, for the few endpoints that take none.
  Future<Object?> getJson(String path, {Map<String, String>? query, bool auth = true}) async {
    final uri = _uri(path, query);
    final response = await _call(uri, (headers) => httpClient.get(uri, headers: headers), auth: auth);
    return _decode(response, uri);
  }

  /// The page a list's `next_url` points at.
  Future<Object?> getNextJson(String nextUrl) => getJson(nextUrl);

  /// A list's first page from [path], or the page [nextUrl] points at when there is one.
  Future<Object?> getPage(String path, {Map<String, String>? query, String? nextUrl}) =>
      nextUrl == null || nextUrl.isEmpty ? getJson(path, query: query) : getNextJson(nextUrl);

  /// GETs a page Pixiv answers in HTML, such as a novel's webview text.
  Future<String> getText(String path, {Map<String, String>? query, bool auth = true}) async {
    final uri = _uri(path, query);
    final response = await _call(
      uri,
      (headers) => httpClient.get(uri, headers: {...headers, 'Accept': 'text/html'}),
      auth: auth,
    );
    return utf8.decode(response.bodyBytes, allowMalformed: true);
  }

  /// POSTs a form to an app-API [path]; an empty answer decodes to null.
  Future<Object?> postForm(String path, Map<String, String> body) async {
    final uri = _uri(path, null);
    final response = await _call(
      uri,
      (headers) =>
          httpClient.post(uri, headers: {...headers, 'Content-Type': 'application/x-www-form-urlencoded'}, body: body),
      auth: true,
    );
    return response.bodyBytes.isEmpty ? null : _decode(response, uri);
  }

  /// A list payload as the reader's filters show it. [ownList] keeps
  /// everything the reader saved on purpose, R-18 and AI works included.
  PixivIllustPage illustPageFrom(Object? json, {bool? includeR18, bool? includeAi, bool ownList = false}) =>
      PixivIllustPage(
        illusts: parsePixivIllustList(
          json,
          includeR18: includeR18 ?? (ownList || showR18),
          includeAi: includeAi ?? (ownList || !hideAi),
        ),
        nextUrl: Json(json)['next_url'].string,
      );

  Future<Object?> _firstOrNext(String path, Map<String, String> query, String? nextUrl) =>
      nextUrl == null ? getJson(path, query: query) : getNextJson(nextUrl);

  /// Follow [userId] publicly so their works appear in the Following tab.
  Future<void> followUser(int userId, {String restrict = 'public'}) async {
    await postForm('/v1/user/follow/add', {
      'user_id': '$userId',
      'restrict': restrict,
    });
  }

  Future<void> unfollowUser(int userId) async {
    await postForm('/v1/user/follow/delete', {'user_id': '$userId'});
  }

  Future<PixivIllustPage> following({String? nextUrl}) async {
    final json = await _firstOrNext('/v2/illust/follow', {'restrict': 'all'}, nextUrl);
    return illustPageFrom(json);
  }

  /// Personalized For You feed — Flare's Discover "status" surface.
  ///
  /// `include_ranking_illusts` mixes today's ranking in when the personalised
  /// set is thin, matching DimensionDev/Flare's `recommendedIllusts` call.
  Future<PixivIllustPage> recommended({String? nextUrl}) async {
    final json = await _firstOrNext('/v1/illust/recommended', {
      'include_ranking_illusts': 'true',
      'include_privacy_policy': 'true',
      'filter': 'for_android',
    }, nextUrl);
    return illustPageFrom(json);
  }

  /// Creators Pixiv suggests — Flare's Discover "users" strip.
  Future<({List<PixivUser> users, String? nextUrl})> recommendedUsers({
    String? nextUrl,
  }) async {
    final json = await _firstOrNext('/v1/user/recommended', {'filter': 'for_android'}, nextUrl);
    final root = Json(json);
    return (users: parsePixivUserList(json), nextUrl: root['next_url'].string);
  }

  /// Creators Pixiv relates to [seedUserId], each with a few preview works —
  /// the "similar users" strip under a profile. Previews follow the reader's
  /// Show R-18 and Hide AI choices unless told otherwise.
  ///
  /// `seed_user_id` stays the last parameter: Pixiv warns clients to put it at the end.
  Future<List<PixivUserPreview>> relatedUsers(int seedUserId, {bool? includeR18, bool? includeAi}) async {
    final json = await getJson('/v1/user/related', query: {
      'filter': 'for_android',
      'seed_user_id': '$seedUserId',
    });
    return parsePixivUserPreviews(json, includeR18: includeR18 ?? showR18, includeAi: includeAi ?? !hideAi);
  }

  /// What Pixiv is drawing right now — each tag ships a representative illust.
  Future<List<PixivTrendTag>> trendingTags() async => parsePixivTrendTags(
    await getJson('/v1/trending-tags/illust', query: {'filter': 'for_android'}),
    includeR18: showR18,
    includeAi: !hideAi,
  );

  /// Tag suggestions while typing, with translated names where Pixiv has them.
  Future<List<PixivTrendTag>> autocomplete(String word) async {
    final trimmed = word.trim();
    if (trimmed.isEmpty) {
      return const [];
    }
    final json = await getJson('/v2/search/autocomplete', query: {
      'word': trimmed,
      'merge_plain_keyword_results': 'true',
    });
    return [
      for (final entry in Json(json)['tags'].list)
        if (entry['name'].string case final String name when name.isNotEmpty)
          PixivTrendTag(
            name: name,
            translatedName: entry['translated_name'].string,
          ),
    ];
  }

  /// Bookmarks for [userId] (usually the signed-in account).
  ///
  /// Pixiv likes *are* bookmarks — there is no `/v1/user/like/illust`.
  /// [userId] is required; omitting it 404s.
  ///
  /// Own bookmarks always keep R-18 works: the reader saved them on purpose,
  /// and filtering them out left only Pixiv's "deleted or private" stubs.
  /// [tag] narrows them to one bookmark tag (`未分類` for untagged ones).
  Future<PixivIllustPage> bookmarks({
    required int userId,
    String restrict = 'public',
    String? tag,
    String? nextUrl,
  }) async {
    final json = await _firstOrNext('/v1/user/bookmarks/illust', {
      'user_id': '$userId',
      'restrict': restrict,
      if (tag != null && tag.isNotEmpty) 'tag': tag,
      'filter': 'for_android',
    }, nextUrl);
    return illustPageFrom(json, ownList: true);
  }

  Future<PixivIllust> illustDetail(int illustId) async {
    final json = await getJson('/v1/illust/detail', query: {'illust_id': '$illustId'});
    final illust = pixivIllustFromJson(Json(json)['illust'].raw);
    if (illust == null) {
      throw PixivException(
        PixivErrorKind.badResponse,
        'empty illust $illustId',
      );
    }
    return illust;
  }

  Future<PixivUgoira> ugoiraMetadata(int illustId) async {
    final json = await getJson('/v1/ugoira/metadata', query: {'illust_id': '$illustId'});
    final ugoira = parsePixivUgoira(json);
    if (ugoira == null) {
      throw PixivException(PixivErrorKind.badResponse, 'empty ugoira $illustId');
    }
    return ugoira;
  }

  /// An ugoira's frame archive, from the image server the reader picked. A failed fetch
  /// leaves the cache, so the next attempt goes to the network again.
  Future<Uint8List> ugoiraArchive(String url) {
    final cached = _ugoiraArchives[url];
    if (cached != null) return cached;
    final fetch = _fetchUgoiraArchive(pixivImageUrl(url, pixivImageHostSetting(prefs)));
    _ugoiraArchives[url] = fetch;
    unawaited(fetch.then<void>((_) {}, onError: (Object _) => _ugoiraArchives.removeIfHolds(url, fetch)));
    return fetch;
  }

  /// Pixiv's image CDN only answers with Pixiv's Referer.
  Future<Uint8List> _fetchUgoiraArchive(String url) async {
    final uri = Uri.parse(url);
    final http.Response response;
    try {
      response = await httpClient.get(uri, headers: pixivImageHeaders).timeout(const Duration(seconds: 60));
    } catch (e) {
      throw PixivException(PixivErrorKind.network, '$e');
    }
    _throwForImageStatus(response, uri);
    return response.bodyBytes;
  }

  Future<PixivIllustPage> related(
    int illustId, {
    String? nextUrl,
    bool? includeR18,
  }) async {
    final json = await _firstOrNext('/v2/illust/related', {
      'illust_id': '$illustId',
      'filter': 'for_android',
    }, nextUrl);
    return illustPageFrom(json, includeR18: includeR18 ?? showR18);
  }

  /// One kind of [userId]'s works, the one request every works list makes.
  /// [ownList] keeps everything the reader posted, R-18 and AI works included.
  Future<PixivIllustPage> userIllusts(
    int userId, {
    PixivWorkType type = PixivWorkType.illust,
    bool ownList = false,
    String? nextUrl,
  }) async {
    final json = await _firstOrNext('/v1/user/illusts', {
      'user_id': '$userId',
      'type': type.name,
      'filter': 'for_android',
    }, nextUrl);
    return illustPageFrom(json, ownList: ownList);
  }
}
