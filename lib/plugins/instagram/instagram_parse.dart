/// Pure parsers for Instagram web_profile_info / feed/user / topsearch JSON.
library;

import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/utils/json.dart';

final _handlePattern = RegExp(r'^[A-Za-z0-9._]{1,30}$');

String? normaliseInstagramHandle(String? raw) {
  if (raw == null) return null;
  final value = raw.trim();
  if (value.startsWith('@')) return _validHandle(value.substring(1).trim());

  final uri = Uri.tryParse(value.contains('://') ? value : 'https://$value');
  final host = uri?.host.toLowerCase();
  if (host == 'instagram.com' || host?.endsWith('.instagram.com') == true) {
    final segment = uri!.pathSegments.isEmpty
        ? ''
        : uri.pathSegments.first.trim();
    if (segment.isEmpty ||
        const {'p', 'reel', 'reels', 'stories', 'explore'}.contains(segment)) {
      return null;
    }
    return _validHandle(segment);
  }
  if (value.contains('/') || value.contains('://')) return null;
  return _validHandle(value);
}

String? _validHandle(String handle) {
  if (!_handlePattern.hasMatch(handle)) return null;
  return handle.toLowerCase();
}

InstagramProfile? parseInstagramProfileJson(Object? json) {
  final root = Json(json);
  final user = root['data']['user'].exists
      ? root['data']['user']
      : root['user'];
  final username = (user['username'].string ?? '').trim();
  final id =
      (user['id'].string ??
              user['pk'].string ??
              user['pk'].integer?.toString() ??
              '')
          .trim();
  if (username.isEmpty || id.isEmpty) return null;

  return InstagramProfile(
    id: id,
    username: username,
    fullName: user['full_name'].string ?? username,
    biography: user['biography'].string,
    avatarUrl: _firstUrl(
      user['profile_pic_url_hd'],
      user['profile_pic_url'],
      user['hd_profile_pic_url_info']['url'],
    ),
    isPrivate: user['is_private'].boolean ?? false,
    isVerified: user['is_verified'].boolean ?? false,
    followerCount:
        user['edge_followed_by']['count'].integer ??
        user['follower_count'].integer ??
        0,
    followingCount:
        user['edge_follow']['count'].integer ??
        user['following_count'].integer ??
        0,
    mediaCount:
        user['edge_owner_to_timeline_media']['count'].integer ??
        user['media_count'].integer ??
        0,
  );
}

InstagramItemPage parseInstagramProfileMedia(Object? json) {
  final root = Json(json);
  final user = root['data']['user'].exists
      ? root['data']['user']
      : root['user'];
  final timeline = user['edge_owner_to_timeline_media'];
  final author = _authorOf(user);
  final posts = <InstagramPost>[];
  for (final edge in timeline['edges'].list) {
    final post = parseInstagramMediaNode(edge['node'], author: author);
    if (post != null) posts.add(post);
  }
  if (posts.isEmpty) {
    for (final item in user['media']['nodes'].list) {
      final post = parseInstagramMediaNode(item, author: author);
      if (post != null) posts.add(post);
    }
  }
  return InstagramItemPage(
    posts: posts,
    cursor: timeline['page_info']['end_cursor'].string,
    hasMore: timeline['page_info']['has_next_page'].boolean ?? false,
  );
}

InstagramItemPage parseInstagramUserFeed(Object? json) {
  final root = Json(json);
  final posts = <InstagramPost>[];
  for (final item in root['items'].list) {
    final post = parseInstagramMediaNode(item);
    if (post != null) posts.add(post);
  }
  final cursor =
      root['next_max_id'].string ?? root['next_max_id'].integer?.toString();
  return InstagramItemPage(
    posts: posts,
    cursor: cursor,
    hasMore: root['more_available'].boolean ?? (cursor?.isNotEmpty == true),
  );
}

InstagramPost? parseInstagramMediaNode(Json node, {InstagramAuthor? author}) {
  final id =
      (node['id'].string ??
              node['pk'].string ??
              node['pk'].integer?.toString() ??
              '')
          .trim();
  final shortcode = (node['shortcode'].string ?? node['code'].string ?? '')
      .trim();
  if (id.isEmpty || shortcode.isEmpty) return null;

  final user = node['user'].exists ? node['user'] : node['owner'];
  final resolved = author ?? _authorOf(user);
  if (resolved.username.isEmpty) return null;

  final caption =
      node['edge_media_to_caption']['edges'][0]['node']['text'].string ??
      node['caption']['text'].string ??
      node['caption'].string ??
      '';

  return InstagramPost(
    id: id,
    shortcode: shortcode,
    caption: caption,
    createdAt: _createdAt(node),
    author: resolved,
    coverUrl: _firstUrl(
      node['display_url'],
      node['image_versions2']['candidates'][0]['url'],
      node['thumbnail_src'],
    ),
    isVideo:
        node['is_video'].boolean ??
        (node['media_type'].integer == 2 ||
            (node['product_type'].string ?? '') == 'clips'),
    likeCount:
        node['edge_liked_by']['count'].integer ??
        node['like_count'].integer ??
        0,
    commentCount:
        node['edge_media_to_comment']['count'].integer ??
        node['comment_count'].integer ??
        0,
    carouselUrls: [
      for (final child in node['carousel_media'].list)
        ?_firstUrl(child['image_versions2']['candidates'][0]['url']),
    ],
  );
}

List<InstagramSearchUser> parseInstagramTopSearch(Object? json) {
  final users = <InstagramSearchUser>[];
  final seen = <String>{};
  for (final row in Json(json)['users'].list) {
    final user = row['user'].exists ? row['user'] : row;
    final username = (user['username'].string ?? '').trim();
    final id =
        (user['pk'].string ??
                user['pk'].integer?.toString() ??
                user['id'].string ??
                '')
            .trim();
    if (username.isEmpty || !seen.add(username.toLowerCase())) continue;
    users.add(
      InstagramSearchUser(
        id: id.isEmpty ? username : id,
        username: username,
        fullName: user['full_name'].string ?? username,
        avatarUrl: _firstUrl(user['profile_pic_url']),
        isPrivate: user['is_private'].boolean ?? false,
        isVerified: user['is_verified'].boolean ?? false,
      ),
    );
  }
  return users;
}

/// Explore / topical_explore sectional payload — media can sit a few maps deep.
InstagramItemPage parseInstagramExplore(Object? json) {
  final posts = <InstagramPost>[];
  final seen = <String>{};

  void walk(Object? raw, [int depth = 0]) {
    if (raw == null || depth > 14) return;
    if (raw is List) {
      for (final item in raw) {
        walk(item, depth + 1);
      }
      return;
    }
    if (raw is! Map) return;

    final node = Json(raw);
    final hasCode =
        (node['shortcode'].string ?? node['code'].string ?? '').isNotEmpty;
    final hasId =
        (node['id'].string ??
                node['pk'].string ??
                node['pk'].integer?.toString() ??
                '')
            .isNotEmpty;
    if (hasCode && hasId) {
      final post = parseInstagramMediaNode(
        node['media'].exists ? node['media'] : node,
      );
      if (post != null && seen.add(post.id)) {
        posts.add(post);
      }
    }
    for (final value in raw.values) {
      walk(value, depth + 1);
    }
  }

  walk(json);
  final root = Json(json);
  final cursor =
      root['next_max_id'].string ??
      root['max_id'].string ??
      root['next_max_id'].integer?.toString();
  return InstagramItemPage(
    posts: posts,
    cursor: cursor,
    hasMore: root['more_available'].boolean ?? (cursor?.isNotEmpty == true),
  );
}

bool instagramLoginRequired(Object? json) {
  final root = Json(json);
  final message = (root['message'].string ?? '').toLowerCase();
  return message.contains('login_required') ||
      (root['status'].string ?? '') == 'fail' && message.contains('login');
}

InstagramAuthor _authorOf(Json user) {
  final username = (user['username'].string ?? '').trim();
  return InstagramAuthor(
    username: username,
    fullName: user['full_name'].string ?? username,
    avatarUrl: _firstUrl(user['profile_pic_url_hd'], user['profile_pic_url']),
    isVerified: user['is_verified'].boolean ?? false,
  );
}

DateTime _createdAt(Json node) {
  final seconds =
      node['taken_at_timestamp'].integer ?? node['taken_at'].integer;
  if (seconds == null || seconds <= 0) {
    return DateTime.fromMillisecondsSinceEpoch(0);
  }
  if (seconds > 100000000000) {
    return DateTime.fromMillisecondsSinceEpoch(seconds);
  }
  return DateTime.fromMillisecondsSinceEpoch(seconds * 1000);
}

String? _firstUrl(Json a, [Json? b, Json? c]) {
  for (final node in [a, ?b, ?c]) {
    final url = node.string;
    if (url != null && url.startsWith('http')) return url;
  }
  return null;
}
