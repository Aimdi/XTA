/// Pure parsers for TikTok web HTML / unsigned creator/item_list JSON.
library;

import 'dart:convert';
import 'dart:math';

import 'package:html_unescape/html_unescape.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/utils/json.dart';

final _unescape = HtmlUnescape();

final _universalData = RegExp(
  r'<script[^>]+id="__UNIVERSAL_DATA_FOR_REHYDRATION__"[^>]*>(.*?)</script>',
  dotAll: true,
);

final _handlePattern = RegExp(r'^[A-Za-z0-9._]{2,24}$');

/// Device ids yt-dlp found the unsigned web list accepts.
const tiktokDeviceIdMin = 7250000000000000000;
const tiktokDeviceIdMax = 7351147085025500000;

String? normaliseTikTokHandle(String? raw) {
  if (raw == null) return null;
  final value = raw.trim();
  if (value.startsWith('@')) return _validHandle(value.substring(1).trim());

  final uri = Uri.tryParse(value.contains('://') ? value : 'https://$value');
  final host = uri?.host.toLowerCase();
  if (host == 'tiktok.com' || host?.endsWith('.tiktok.com') == true) {
    if (host == 'vm.tiktok.com' || host == 'vt.tiktok.com') return null;
    final segment = uri!.pathSegments.isEmpty
        ? ''
        : uri.pathSegments.first.trim();
    return segment.startsWith('@') ? _validHandle(segment.substring(1)) : null;
  }
  if (value.contains('/') || value.contains('://')) return null;
  return _validHandle(value);
}

String? _validHandle(String handle) {
  if (!_handlePattern.hasMatch(handle)) return null;
  return handle.toLowerCase();
}

String randomTikTokDeviceId([Random? random]) {
  final r = random ?? Random();
  const span = tiktokDeviceIdMax - tiktokDeviceIdMin;
  final mix = (r.nextInt(1 << 30) / (1 << 30)) * span;
  return (tiktokDeviceIdMin + mix.round()).toString();
}

Json? parseTikTokUniversalScope(String html) {
  final match = _universalData.firstMatch(html);
  if (match == null) return null;
  try {
    final json = _unescape.convert(match.group(1)!);
    return Json(jsonDecode(json))['__DEFAULT_SCOPE__'];
  } catch (_) {
    return null;
  }
}

TikTokProfile? parseTikTokProfileHtml(String html) {
  final scope = parseTikTokUniversalScope(html);
  if (scope == null) return null;
  return parseTikTokProfileJson(scope['webapp.user-detail']['userInfo']);
}

TikTokProfile? parseTikTokProfileJson(Json userInfo) {
  final user = userInfo['user'];
  final uniqueId = (user['uniqueId'].string ?? '').trim();
  final secUid = (user['secUid'].string ?? '').trim();
  if (uniqueId.isEmpty || secUid.isEmpty) return null;

  final stats = userInfo['stats'].exists ? userInfo['stats'] : user['stats'];
  return TikTokProfile(
    id: user['id'].string ?? uniqueId,
    secUid: secUid,
    uniqueId: uniqueId,
    nickname: user['nickname'].string ?? uniqueId,
    signature: user['signature'].string,
    avatarUrl: _firstUrl(
      user['avatarLarger'],
      user['avatarMedium'],
      user['avatarThumb'],
    ),
    privateAccount: user['privateAccount'].boolean ?? false,
    verified: user['verified'].boolean ?? false,
    followerCount: stats['followerCount'].integer ?? 0,
    followingCount: stats['followingCount'].integer ?? 0,
    videoCount: stats['videoCount'].integer ?? 0,
    heartCount: stats['heartCount'].integer ?? stats['diggCount'].integer ?? 0,
  );
}

TikTokPost? parseTikTokVideoHtml(String html) {
  final scope = parseTikTokUniversalScope(html);
  if (scope == null) return null;
  final item = scope['webapp.video-detail']['itemInfo']['itemStruct'];
  return parseTikTokPost(item);
}

TikTokItemPage parseTikTokItemList(Object? json) {
  final root = Json(json);
  final status = root['statusCode'].integer;
  final posts = <TikTokPost>[];
  for (final item in root['itemList'].list) {
    final post = parseTikTokPost(item);
    if (post != null) posts.add(post);
  }
  final last = posts.isEmpty ? null : posts.last;
  final apiCursor = root['cursor'].string ?? root['cursor'].integer?.toString();
  final cursor = apiCursor?.trim().isNotEmpty == true
      ? apiCursor
      : last == null
      ? null
      : '${last.createdAt.millisecondsSinceEpoch}';
  return TikTokItemPage(
    posts: posts,
    cursor: cursor,
    hasMore:
        root['hasMorePrevious'].boolean ?? root['hasMore'].boolean ?? false,
    statusCode: status,
  );
}

TikTokPost? parseTikTokPost(Json item) {
  final id = (item['id'].string ?? item['id'].integer?.toString() ?? '').trim();
  if (id.isEmpty) return null;

  final authorJson = item['author'].exists
      ? item['author']
      : item['authorInfo'];
  final uniqueId =
      (authorJson['uniqueId'].string ?? authorJson['author'].string ?? '')
          .trim();
  if (uniqueId.isEmpty) return null;

  final video = item['video'];
  final created = _createdAt(item['createTime']);
  final stats = item['stats'];
  final sources = parseTikTokVideoSources(video);
  final duration = video['duration'].integer ?? 0;
  final coverUrl =
      _firstUrl(video['originCover'], video['cover'], video['dynamicCover']) ??
      _firstUrl(item['imagePost']['cover']['imageURL']['urlList'][0]) ??
      _firstUrl(item['imagePost']['images'][0]['imageURL']['urlList'][0]);

  return TikTokPost(
    id: id,
    desc: item['desc'].string ?? '',
    createdAt: created,
    author: TikTokAuthor(
      uniqueId: uniqueId,
      nickname: authorJson['nickname'].string ?? uniqueId,
      secUid: authorJson['secUid'].string ?? authorJson['authorSecId'].string,
      avatarUrl: _firstUrl(
        authorJson['avatarLarger'],
        authorJson['avatarMedium'],
        authorJson['avatarThumb'],
      ),
      verified: authorJson['verified'].boolean ?? false,
    ),
    coverUrl: coverUrl,
    durationSeconds: duration > 1000 ? (duration / 1000).round() : duration,
    width: video['width'].integer ?? 0,
    height: video['height'].integer ?? 0,
    diggCount: stats['diggCount'].integer ?? 0,
    commentCount: stats['commentCount'].integer ?? 0,
    shareCount: stats['shareCount'].integer ?? 0,
    playCount: stats['playCount'].integer ?? 0,
    sources: sources,
  );
}

List<TikTokVideoSource> parseTikTokVideoSources(Json video) {
  final out = <TikTokVideoSource>[];
  final seen = <String>{};

  void add(String? url, String? label) {
    final cleaned = _playableUrl(url);
    if (cleaned == null || !seen.add(cleaned)) return;
    out.add(TikTokVideoSource(cleaned, label: label));
  }

  for (final info in video['bitrateInfo'].list) {
    final addr = info['PlayAddr'].exists ? info['PlayAddr'] : info['playAddr'];
    final label = _qualityLabel(
      addr['UrlKey'].string ?? info['GearName'].string,
    );
    for (final url in addr['UrlList'].list) {
      add(url.string, label);
    }
  }
  add(_stringOrSrc(video['playAddr']), null);
  add(_stringOrSrc(video['downloadAddr']), 'download');
  return out;
}

DateTime _createdAt(Json value) {
  final seconds = value.integer;
  if (seconds == null || seconds <= 0) {
    return DateTime.fromMillisecondsSinceEpoch(0);
  }
  if (seconds > 100000000000) {
    return DateTime.fromMillisecondsSinceEpoch(seconds);
  }
  return DateTime.fromMillisecondsSinceEpoch(seconds * 1000);
}

String? _stringOrSrc(Json value) {
  final direct = value.string;
  if (direct != null) return direct;
  for (final item in value.list) {
    final url = item.string ?? item['src'].string ?? item['url'].string;
    if (url != null) return url;
  }
  return value['src'].string ??
      value['url'].string ??
      value['UrlList'][0].string ??
      value['urlList'][0].string;
}

String? _firstUrl(Json a, [Json? b, Json? c]) {
  for (final node in [a, ?b, ?c]) {
    final url = _stringOrSrc(node);
    if (url != null && url.startsWith('http')) return url;
  }
  return null;
}

String? _playableUrl(String? url) {
  if (url == null || !url.startsWith('http')) return null;
  final uri = Uri.tryParse(url);
  if (uri == null) return null;
  final host = uri.host.toLowerCase();
  if (host == 'www.tiktok.com' || host == 'tiktok.com') return null;
  return url;
}

String? _qualityLabel(String? key) {
  if (key == null || key.isEmpty) return null;
  final match = RegExp(r'(\d+p)', caseSensitive: false).firstMatch(key);
  return match?.group(1);
}

String formatTikTokDuration(int seconds) {
  if (seconds <= 0) return '';
  final m = seconds ~/ 60;
  final s = seconds % 60;
  return '$m:${s.toString().padLeft(2, '0')}';
}

final _searchApos = RegExp(r"['\u2019]");
final _searchJunk = RegExp(r'[^a-z0-9._\s]+');
final _searchSpaces = RegExp(r'\s+');

/// Handles worth fetching as public profile HTML for a typed query.
List<String> tiktokSearchHandleCandidates(
  String query, {
  Iterable<String> suggestions = const [],
}) {
  final out = <String>[];
  void add(String? raw) {
    final handle = normaliseTikTokHandle(raw);
    if (handle != null && !out.contains(handle)) out.add(handle);
  }

  add(query);
  final words = tiktokSearchWords(query);
  for (final glued in tiktokGluedHandles(words)) {
    add(glued);
  }
  if (words.isNotEmpty) add(words.first);
  for (final suggestion in suggestions.take(6)) {
    add(suggestion);
    for (final glued in tiktokGluedHandles(tiktokSearchWords(suggestion))) {
      add(glued);
    }
    if (out.length >= 8) break;
  }
  return out.take(8).toList();
}

List<String> tiktokSearchWords(String raw) {
  final cleaned = raw
      .toLowerCase()
      .replaceAll(_searchApos, '')
      .replaceAll(_searchJunk, ' ')
      .trim();
  if (cleaned.isEmpty) return const [];
  return [
    for (final word in cleaned.split(_searchSpaces))
      if (word.isNotEmpty) word,
  ];
}

List<String> tiktokGluedHandles(List<String> words) {
  if (words.length < 2 || words.length > 4) return const [];
  return [words.join(), words.join('.'), words.join('_')];
}

bool tiktokUserMatchesQuery(TikTokSearchUser user, String query) {
  final q = tiktokFoldQuery(query);
  if (q.isEmpty) return false;
  final glued = q.replaceAll(' ', '');
  return tiktokFoldQuery(user.uniqueId).contains(q) ||
      tiktokFoldQuery(user.nickname).contains(q) ||
      user.uniqueId.toLowerCase().contains(glued);
}

String tiktokFoldQuery(String raw) {
  return raw
      .toLowerCase()
      .replaceFirst(RegExp(r'^@'), '')
      .replaceAll(_searchApos, '')
      .replaceAll(_searchJunk, ' ')
      .replaceAll(_searchSpaces, ' ')
      .trim();
}

List<TikTokSearchUser> parseTikTokDiscoverUsers(Object? json) {
  final users = <TikTokSearchUser>[];
  final seen = <String>{};
  for (final section in Json(json)['body'].list) {
    for (final card in section['exploreList'].list) {
      final user = parseTikTokDiscoverUser(card['cardItem']);
      if (user == null || !seen.add(user.uniqueId.toLowerCase())) continue;
      users.add(user);
    }
  }
  return users;
}

TikTokSearchUser? parseTikTokDiscoverUser(Json item) {
  if ((item['type'].integer ?? 0) != 2) return null;
  final uniqueId = _handleFromDiscoverLink(
    item['link'].string ?? item['subTitle'].string,
  );
  if (uniqueId == null) return null;
  final extra = item['extraInfo'];
  return TikTokSearchUser(
    uniqueId: uniqueId,
    nickname: item['title'].string ?? uniqueId,
    avatarUrl: _firstUrl(item['cover']),
    signature: item['description'].string,
    verified: extra['verified'].boolean ?? false,
    followerCount: extra['fans'].integer ?? extra['followerCount'].integer ?? 0,
  );
}

List<String> parseTikTokSuggestList(Object? json) {
  final out = <String>[];
  final seen = <String>{};
  void add(String? raw) {
    final word = (raw ?? '').trim();
    if (word.isEmpty || !seen.add(word.toLowerCase())) return;
    out.add(word);
  }

  final root = Json(json);
  for (final item in root['sug_list'].list) {
    add(item['content'].string ?? item['word_record']['words_content'].string);
  }
  for (final item in root['data'].list) {
    add(item['word'].string);
  }
  return out;
}

String? _handleFromDiscoverLink(String? raw) {
  if (raw == null || raw.isEmpty) return null;
  if (raw.startsWith('/')) {
    return normaliseTikTokHandle('https://www.tiktok.com$raw');
  }
  return normaliseTikTokHandle(raw);
}
