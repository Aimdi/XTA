/// Guest-first Instagram client — web_profile_info, optional session cookies.
library;

import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/instagram/instagram_discovery.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_parse.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';

enum InstagramErrorKind {
  network,
  notFound,
  privateAccount,
  rateLimited,
  loginRequired,
  badResponse,
}

class InstagramException implements Exception {
  final InstagramErrorKind kind;
  final String message;

  InstagramException(this.kind, this.message);

  @override
  String toString() => 'InstagramException{$kind: $message}';
}

const instagramUserAgent =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';

const instagramWebOrigin = 'https://www.instagram.com';
const instagramApiOrigin = 'https://i.instagram.com';
const instagramWebAppId = '936619743392459';

final _setCookieSplit = RegExp(r',(?=[^;]+=)');

class InstagramClient {
  final http.Client httpClient;
  final BasePrefService prefs;
  final Map<String, String> _cookies = {};

  InstagramClient(this.prefs, {http.Client? httpClient})
    : httpClient = httpClient ?? http.Client() {
    _loadCookies();
  }

  static const _timeout = Duration(seconds: 25);

  String get cookieHeader =>
      _cookies.entries.map((e) => '${e.key}=${e.value}').join('; ');

  bool get hasSession => threadsCookiesComplete(_cookies);

  void _loadCookies() {
    final raw = (prefs.get<String>(optionPluginInstagramCookies) ?? '').trim();
    if (raw.isEmpty) return;
    _cookies.addAll(parseThreadsCookieHeader(raw));
  }

  Future<void> _persistCookies() async {
    await prefs.set(optionPluginInstagramCookies, cookieHeader);
  }

  Future<void> setCookies(String raw) async {
    _cookies
      ..clear()
      ..addAll(parseThreadsCookieHeader(raw));
    await _persistCookies();
  }

  Future<void> importThreadsCookies() async {
    final raw = (prefs.get<String>(optionPluginThreadsDirectCookies) ?? '')
        .trim();
    if (raw.isEmpty) {
      throw InstagramException(InstagramErrorKind.loginRequired, 'threads');
    }
    await setCookies(raw);
  }

  Future<void> clearSession() async {
    _cookies.clear();
    await prefs.set(optionPluginInstagramCookies, '');
  }

  Future<InstagramProfile> profile(String handle) async {
    final key = normaliseInstagramHandle(handle);
    if (key == null) {
      throw InstagramException(InstagramErrorKind.notFound, handle);
    }
    await warmGuest();
    final json = await _webProfile(key);
    final profile = parseInstagramProfileJson(json);
    if (profile == null) {
      throw InstagramException(InstagramErrorKind.notFound, '@$key');
    }
    return profile;
  }

  Future<InstagramItemPage> profileMedia(String handle) async {
    final key = normaliseInstagramHandle(handle);
    if (key == null) {
      throw InstagramException(InstagramErrorKind.notFound, handle);
    }
    await warmGuest();
    return parseInstagramProfileMedia(await _webProfile(key));
  }

  Future<InstagramItemPage> userFeed({
    required String pk,
    String? cursor,
    int count = 12,
  }) async {
    final query = {
      'count': '$count',
      if (cursor != null && cursor.isNotEmpty) 'max_id': cursor,
    };
    final uri = Uri.parse(
      '$instagramApiOrigin/api/v1/feed/user/$pk/',
    ).replace(queryParameters: query);
    final response = await _get(uri, referer: '$instagramWebOrigin/');
    return parseInstagramUserFeed(_decodeJson(response, uri));
  }

  /// Instagram's own Explore mix when a session answers; else public seeds.
  Future<InstagramItemPage> forYou({String? cursor}) async {
    if (hasSession) {
      try {
        final page = await exploreFeed(cursor: cursor);
        if (page.posts.isNotEmpty) return page;
      } on InstagramException catch (e) {
        if (e.kind != InstagramErrorKind.loginRequired &&
            e.kind != InstagramErrorKind.rateLimited &&
            e.kind != InstagramErrorKind.badResponse) {
          rethrow;
        }
      }
    }
    if (cursor != null && cursor.isNotEmpty) {
      return const InstagramItemPage(posts: [], hasMore: false);
    }
    return guestDiscover();
  }

  /// Session Explore grid (`discover/web/explore_grid`, then topical_explore).
  Future<InstagramItemPage> exploreFeed({String? cursor}) async {
    await warmGuest();
    final query = {
      'is_prefetch': 'false',
      'omit_cover_media': 'false',
      'module': 'explore_popular',
      if (cursor != null && cursor.isNotEmpty) 'max_id': cursor,
    };
    try {
      final uri = Uri.parse(
        '$instagramWebOrigin/api/v1/discover/web/explore_grid/',
      ).replace(queryParameters: query);
      final response = await _get(uri, referer: '$instagramWebOrigin/explore/');
      return parseInstagramExplore(_decodeJson(response, uri));
    } on InstagramException catch (e) {
      if (e.kind != InstagramErrorKind.rateLimited &&
          e.kind != InstagramErrorKind.loginRequired &&
          e.kind != InstagramErrorKind.badResponse) {
        rethrow;
      }
    }
    final uri = Uri.parse(
      '$instagramApiOrigin/api/v1/discover/topical_explore/',
    ).replace(queryParameters: query);
    final response = await _get(uri, referer: '$instagramWebOrigin/explore/');
    return parseInstagramExplore(_decodeJson(response, uri));
  }

  /// Guest For You: recent public posts from well-known accounts, interleaved.
  Future<InstagramItemPage> guestDiscover({int perAccount = 4}) async {
    await warmGuest();
    final errors = <Object>[];
    final pages = await Future.wait([
      for (final handle in kInstagramDiscoverHandles)
        _profileMediaOrEmpty(handle, perAccount, errors),
    ]);
    final posts = interleaveInstagramDiscover(pages);
    if (posts.isEmpty && errors.isNotEmpty) {
      throw _preferredDiscoverError(errors);
    }
    return InstagramItemPage(posts: posts, hasMore: false);
  }

  Future<List<InstagramPost>> _profileMediaOrEmpty(
    String handle,
    int cap,
    List<Object> errors,
  ) async {
    try {
      final page = await profileMedia(handle);
      return page.posts.take(cap).toList(growable: false);
    } catch (e) {
      errors.add(e);
      return const [];
    }
  }

  InstagramException _preferredDiscoverError(List<Object> errors) {
    for (final error in errors) {
      if (error is InstagramException &&
          error.kind == InstagramErrorKind.rateLimited) {
        return error;
      }
    }
    final error = errors.first;
    if (error is InstagramException) return error;
    return InstagramException(InstagramErrorKind.network, '$error');
  }

  Future<List<InstagramSearchUser>> searchUsers(String raw) async {
    final query = raw.trim();
    if (query.isEmpty) return const [];
    await warmGuest();
    final uri = Uri.parse(
      '$instagramWebOrigin/api/v1/web/search/topsearch/',
    ).replace(queryParameters: {'query': query, 'context': 'user'});
    final response = await _get(uri, referer: '$instagramWebOrigin/');
    return parseInstagramTopSearch(_decodeJson(response, uri));
  }

  Future<void> warmGuest() async {
    if (_cookies.containsKey('csrftoken') && _cookies.containsKey('mid')) {
      return;
    }
    await _get(Uri.parse('$instagramWebOrigin/'), acceptHtml: true);
  }

  Future<Object?> _webProfile(String username) async {
    final uri = Uri.parse(
      '$instagramApiOrigin/api/v1/users/web_profile_info/',
    ).replace(queryParameters: {'username': username});
    try {
      final response = await _get(
        uri,
        referer: '$instagramWebOrigin/$username/',
      );
      return _decodeJson(response, uri);
    } on InstagramException catch (e) {
      if (e.kind != InstagramErrorKind.rateLimited &&
          e.kind != InstagramErrorKind.loginRequired) {
        rethrow;
      }
      final web = Uri.parse(
        '$instagramWebOrigin/api/v1/users/web_profile_info/',
      ).replace(queryParameters: {'username': username});
      final response = await _get(
        web,
        referer: '$instagramWebOrigin/$username/',
      );
      return _decodeJson(response, web);
    }
  }

  Future<http.Response> _get(
    Uri uri, {
    bool acceptHtml = false,
    String? referer,
  }) async {
    try {
      final response = await httpClient
          .get(
            uri,
            headers: {
              'User-Agent': instagramUserAgent,
              'Accept': acceptHtml
                  ? 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
                  : 'application/json, text/plain, */*',
              'Accept-Language': 'en-US,en;q=0.9',
              'X-IG-App-ID': instagramWebAppId,
              'X-Requested-With': 'XMLHttpRequest',
              'Origin': instagramWebOrigin,
              'Referer': ?referer,
              if (_cookies['csrftoken'] != null)
                'X-CSRFToken': _cookies['csrftoken']!,
              if (_cookies.isNotEmpty) 'Cookie': cookieHeader,
            },
          )
          .timeout(_timeout);
      _rememberCookies(response);
      _throwIfHttpError(response, uri, acceptHtml: acceptHtml);
      return response;
    } catch (e) {
      if (e is InstagramException) rethrow;
      throw InstagramException(InstagramErrorKind.network, '$e');
    }
  }

  void _rememberCookies(http.Response response) {
    final header = response.headers['set-cookie'];
    if (header == null) return;
    for (final piece in header.split(_setCookieSplit)) {
      final pair = piece.split(';').first.trim();
      final equals = pair.indexOf('=');
      if (equals > 0) {
        _cookies[pair.substring(0, equals).trim()] = pair
            .substring(equals + 1)
            .trim();
      }
    }
    unawaited(_persistCookies());
  }

  void _throwIfHttpError(
    http.Response response,
    Uri uri, {
    bool acceptHtml = false,
  }) {
    if (response.statusCode == 404) {
      throw InstagramException(InstagramErrorKind.notFound, '$uri');
    }
    if (response.statusCode == 429) {
      throw InstagramException(InstagramErrorKind.rateLimited, '$uri');
    }
    if (response.statusCode == 401 || response.statusCode == 403) {
      throw InstagramException(InstagramErrorKind.loginRequired, '$uri');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw InstagramException(
        InstagramErrorKind.badResponse,
        '$uri: ${response.statusCode}',
      );
    }
    if (acceptHtml) return;
    if (!response.body.startsWith('{') && !response.body.startsWith('[')) {
      if (response.body.contains('<html')) {
        throw InstagramException(InstagramErrorKind.loginRequired, '$uri');
      }
    }
  }

  Object? _decodeJson(http.Response response, Uri uri) {
    try {
      final decoded = jsonDecode(response.body);
      if (instagramLoginRequired(decoded)) {
        throw InstagramException(InstagramErrorKind.loginRequired, '$uri');
      }
      return decoded;
    } on InstagramException {
      rethrow;
    } catch (e) {
      throw InstagramException(InstagramErrorKind.badResponse, '$uri: $e');
    }
  }
}
