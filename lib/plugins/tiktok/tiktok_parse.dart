/// Pure parsers for TikTok web HTML / unsigned creator/item_list JSON.
library;

import 'dart:convert';
import 'dart:math';

import 'package:xta/plugins/tiktok/tiktok_models.dart';
import 'package:xta/utils/json.dart';

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
  var handle = raw.trim();
  if (handle.startsWith('@')) handle = handle.substring(1).trim();
  handle = handle.split('/').last.trim();
  if (handle.toLowerCase().startsWith('tiktok.com')) return null;
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
    return Json(jsonDecode(match.group(1)!))['__DEFAULT_SCOPE__'];
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
  final cursor = last == null
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
    coverUrl: _firstUrl(
      video['originCover'],
      video['cover'],
      video['dynamicCover'],
    ),
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
