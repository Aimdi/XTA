/// Guest TikTok client — profile HTML, unsigned creator/item_list, guest search.
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
  List<TikTokSearchUser>? _discoverCache;
  DateTime? _discoverCachedAt;

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
    if (page.statusCode == 10201) {
      throw TikTokException(TikTokErrorKind.notFound, secUid);
    }
    if (page.posts.isEmpty && page.hasMore) {
      await _rotateDeviceId();
      page = await _fetchCreatorItems(secUid, cursor, count);
    }
    _validateCreatorPage(page, secUid);
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

  Future<List<String>> suggestQueries(String keyword) async {
    final q = keyword.trim();
    if (q.isEmpty) return const [];
    final uri = Uri.parse('$tiktokWebOrigin/api/search/general/preview/')
        .replace(
          queryParameters: _webQuery(fromPage: 'search', extra: {'keyword': q}),
        );
    final response = await _get(uri, referer: '$tiktokWebOrigin/search?q=$q');
    return parseTikTokSuggestList(_decodeJson(response, uri));
  }

  Future<List<String>> trendingQueries() async {
    final uri = Uri.parse(
      '$tiktokWebOrigin/api/search/suggest/guide/',
    ).replace(queryParameters: _webQuery(fromPage: 'search'));
    final response = await _get(uri, referer: '$tiktokWebOrigin/search');
    return parseTikTokSuggestList(_decodeJson(response, uri));
  }

  Future<List<TikTokSearchUser>> discoverUsers() async {
    final cached = _discoverCache;
    final at = _discoverCachedAt;
    if (cached != null &&
        at != null &&
        DateTime.now().difference(at) < const Duration(minutes: 10)) {
      return cached;
    }
    final uri = Uri.parse('$tiktokWebOrigin/node/share/discover');
    final response = await _get(uri, referer: '$tiktokWebOrigin/');
    final users = parseTikTokDiscoverUsers(_decodeJson(response, uri));
    _discoverCache = users;
    _discoverCachedAt = DateTime.now();
    return users;
  }

  Future<TikTokSearchPage> search(String raw) async {
    final query = raw.trim();
    if (query.isEmpty) return const TikTokSearchPage();
    final suggestions = await _tryStrings(() => suggestQueries(query));
    final users = await _peopleFor(query, suggestions);
    final posts = await _videosFor(users);
    return TikTokSearchPage(
      users: users,
      posts: posts,
      suggestions: suggestions,
    );
  }

  Map<String, String> _webQuery({
    required String fromPage,
    Map<String, String> extra = const {},
  }) {
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
      'device_id': deviceId,
      'device_platform': 'web_pc',
      'focus_state': 'true',
      'from_page': fromPage,
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
      'tz_name': 'UTC',
      'webcast_language': 'en',
      if (msToken != null && msToken.isNotEmpty) 'msToken': msToken,
      ...extra,
    };
  }

  Map<String, String> _creatorQuery(String secUid, String? cursor, int count) {
    return _webQuery(
      fromPage: 'user',
      extra: {
        'count': '$count',
        'cursor': cursor ?? '0',
        'secUid': secUid,
        'type': '1',
      },
    );
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

  Future<List<TikTokSearchUser>> _peopleFor(
    String query,
    List<String> suggestions,
  ) async {
    final discover = await _tryUsers(discoverUsers);
    final handles = tiktokSearchHandleCandidates(
      query,
      suggestions: suggestions,
    );
    final fetched = await _profilesFor(handles);
    return _mergeSearchUsers(query, fetched, discover);
  }

  Future<List<TikTokSearchUser>> _profilesFor(List<String> handles) async {
    final found = await Future.wait(handles.take(6).map(_tryProfile));
    return [
      for (final profile in found)
        if (profile != null) TikTokSearchUser.fromProfile(profile),
    ];
  }

  Future<TikTokProfile?> _tryProfile(String handle) async {
    try {
      return await profile(handle);
    } on TikTokException {
      return null;
    }
  }

  List<TikTokSearchUser> _mergeSearchUsers(
    String query,
    List<TikTokSearchUser> fetched,
    List<TikTokSearchUser> discover,
  ) {
    final out = <TikTokSearchUser>[];
    final seen = <String>{};
    void add(TikTokSearchUser user) {
      if (seen.add(user.uniqueId.toLowerCase())) out.add(user);
    }

    for (final user in fetched) {
      add(user);
    }
    for (final user in discover) {
      if (tiktokUserMatchesQuery(user, query)) add(user);
    }
    return out;
  }

  Future<List<TikTokPost>> _videosFor(List<TikTokSearchUser> users) async {
    final posts = <TikTokPost>[];
    for (final user
        in users.where((u) => (u.secUid ?? '').isNotEmpty).take(2)) {
      try {
        posts.addAll(
          (await creatorItems(secUid: user.secUid!, count: 8)).posts,
        );
      } on TikTokException {
        continue;
      }
    }
    posts.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    final seen = <String>{};
    return [
      for (final post in posts)
        if (seen.add(post.id)) post,
    ].take(16).toList();
  }

  Future<List<String>> _tryStrings(Future<List<String>> Function() load) async {
    try {
      return await load();
    } on TikTokException {
      return const [];
    }
  }

  Future<List<TikTokSearchUser>> _tryUsers(
    Future<List<TikTokSearchUser>> Function() load,
  ) async {
    try {
      return await load();
    } on TikTokException {
      return const [];
    }
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
