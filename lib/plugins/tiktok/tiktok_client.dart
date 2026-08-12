/// Guest TikTok client — profile HTML + unsigned creator/item_list.
library;

import 'dart:async';
import 'dart:convert';

import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/plugins/tiktok/tiktok_parse.dart';

enum TikTokErrorKind {
  network,
  notFound,
  privateAccount,
  rateLimited,
  badResponse,
}

class TikTokException implements Exception {
  final TikTokErrorKind kind;
  final String message;

  TikTokException(this.kind, this.message);

  @override
  String toString() => 'TikTokException{$kind: $message}';
}

const tiktokUserAgent =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 '
    '(KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36';

const tiktokWebOrigin = 'https://www.tiktok.com';

final _setCookieSplit = RegExp(r',(?=[^;]+=)');

Map<String, String> tiktokPlaybackHeaders(String cookieHeader) => {
  'User-Agent': tiktokUserAgent,
  'Referer': '$tiktokWebOrigin/',
  'Origin': tiktokWebOrigin,
  if (cookieHeader.isNotEmpty) 'Cookie': cookieHeader,
};

class TikTokClient {
  final http.Client httpClient;
  final BasePrefService prefs;
  final Map<String, String> _cookies = {};

  TikTokClient(this.prefs, {http.Client? httpClient})
    : httpClient = httpClient ?? http.Client() {
    _loadCookies();
  }

  static const _timeout = Duration(seconds: 25);

  String get cookieHeader =>
      _cookies.entries.map((e) => '${e.key}=${e.value}').join('; ');

  Map<String, String> get cookies => Map.unmodifiable(_cookies);

  Map<String, String> get playbackHeaders =>
      tiktokPlaybackHeaders(cookieHeader);

  String get deviceId {
    final stored = (prefs.get<String>(optionPluginTiktokDeviceId) ?? '').trim();
    if (stored.isNotEmpty) return stored;
    final generated = randomTikTokDeviceId();
    prefs.set(optionPluginTiktokDeviceId, generated);
    return generated;
  }

  void _loadCookies() {
    final raw = (prefs.get<String>(optionPluginTiktokCookies) ?? '').trim();
    if (raw.isEmpty) return;
    for (final part in raw.split(';')) {
      final i = part.indexOf('=');
      if (i <= 0) continue;
      _cookies[part.substring(0, i).trim()] = part.substring(i + 1).trim();
    }
  }

  Future<void> _persistCookies() async {
    await prefs.set(optionPluginTiktokCookies, cookieHeader);
  }

  Future<void> clearSession() async {
    _cookies.clear();
    await prefs.set(optionPluginTiktokCookies, '');
    await prefs.set(optionPluginTiktokDeviceId, '');
  }

  Future<TikTokProfile> profile(String handle) async {
    final key = normaliseTikTokHandle(handle);
    if (key == null) {
      throw TikTokException(TikTokErrorKind.notFound, handle);
    }
    final uri = Uri.parse('$tiktokWebOrigin/@$key');
    final response = await _get(uri, acceptHtml: true);
    final profile = parseTikTokProfileHtml(response.body);
    if (profile == null) {
      throw TikTokException(TikTokErrorKind.notFound, '@$key');
    }
    return profile;
  }

  Future<TikTokItemPage> creatorItems({
    required String secUid,
    String? cursor,
    int count = 15,
  }) async {
    var page = await _fetchCreatorItems(secUid, cursor, count);
    _validateCreatorPage(page, secUid);
    if (page.posts.isEmpty && page.hasMore) {
      await _rotateDeviceId();
      page = await _fetchCreatorItems(secUid, cursor, count);
      _validateCreatorPage(page, secUid);
    }
    return page;
  }

  Future<TikTokPost> video(String id, {String? handle}) async {
    final author = (handle == null || handle.isEmpty) ? '_' : handle;
    final uri = Uri.parse('$tiktokWebOrigin/@$author/video/$id');
    final response = await _get(uri, acceptHtml: true);
    final post = parseTikTokVideoHtml(response.body);
    if (post == null) {
      throw TikTokException(TikTokErrorKind.notFound, id);
    }
    return post;
  }

  Map<String, String> _creatorQuery(String secUid, String? cursor, int count) {
    final msToken = _cookies['msToken'];
    return {
      'aid': '1988',
      'app_language': 'en',
      'app_name': 'tiktok_web',
      'browser_language': 'en-US',
      'browser_name': 'Mozilla',
      'browser_online': 'true',
      'browser_platform': 'Win32',
      'browser_version': '5.0 (Windows)',
      'channel': 'tiktok_web',
      'cookie_enabled': 'true',
      'count': '$count',
      'cursor': cursor ?? '0',
      'device_id': deviceId,
      'device_platform': 'web_pc',
      'focus_state': 'true',
      'from_page': 'user',
      'history_len': '2',
      'is_fullscreen': 'false',
      'is_page_visible': 'true',
      'language': 'en',
      'os': 'windows',
      'priority_region': '',
      'referer': '',
      'region': 'US',
      'screen_height': '1080',
      'screen_width': '1920',
      'secUid': secUid,
      'type': '1',
      'tz_name': 'UTC',
      'webcast_language': 'en',
      if (msToken != null && msToken.isNotEmpty) 'msToken': msToken,
    };
  }

  Future<TikTokItemPage> _fetchCreatorItems(
    String secUid,
    String? cursor,
    int count,
  ) async {
    final query = _creatorQuery(secUid, cursor, count);
    final uri = Uri.parse(
      '$tiktokWebOrigin/api/creator/item_list/',
    ).replace(queryParameters: query);
    final response = await _get(uri, referer: '$tiktokWebOrigin/');
    return parseTikTokItemList(_decodeJson(response, uri));
  }

  void _validateCreatorPage(TikTokItemPage page, String secUid) {
    if (page.statusCode == 10201) {
      throw TikTokException(TikTokErrorKind.notFound, secUid);
    }
    if (page.statusCode != null && page.statusCode != 0 && page.posts.isEmpty) {
      throw TikTokException(
        TikTokErrorKind.badResponse,
        'status ${page.statusCode}',
      );
    }
  }

  Future<void> _rotateDeviceId() async {
    await prefs.set(optionPluginTiktokDeviceId, randomTikTokDeviceId());
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
              'User-Agent': tiktokUserAgent,
              'Accept': acceptHtml
                  ? 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
                  : 'application/json, text/plain, */*',
              'Accept-Language': 'en-US,en;q=0.9',
              'Referer': ?referer,
              if (_cookies.isNotEmpty) 'Cookie': cookieHeader,
            },
          )
          .timeout(_timeout);
      _rememberCookies(response);
      _throwIfHttpError(response, uri);
      return response;
    } catch (e) {
      if (e is TikTokException) rethrow;
      throw TikTokException(TikTokErrorKind.network, '$e');
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

  void _throwIfHttpError(http.Response response, Uri uri) {
    if (response.statusCode == 404) {
      throw TikTokException(TikTokErrorKind.notFound, '$uri');
    }
    if (response.statusCode == 429) {
      throw TikTokException(TikTokErrorKind.rateLimited, '$uri');
    }
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw TikTokException(
        TikTokErrorKind.badResponse,
        '$uri: ${response.statusCode}',
      );
    }
  }

  Object? _decodeJson(http.Response response, Uri uri) {
    try {
      return jsonDecode(response.body);
    } catch (e) {
      throw TikTokException(TikTokErrorKind.badResponse, '$uri: invalid JSON');
    }
  }
}
