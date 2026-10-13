import 'dart:convert';

import 'package:crypto/crypto.dart';
import 'package:ffcache/ffcache.dart';
import 'package:flutter/foundation.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_json.dart';
import 'package:xta/utils/json.dart';

/// The folder of the app's temporary directory that [BooruFeedCache] keeps
/// its pages in; the plugin's storage figure counts it and removing the
/// plugin deletes it.
const booruFeedCacheName = 'booru_feeds';

/// The first page of a Booru feed, kept on the device so that a launch
/// without a network still shows the last one. It is only a fallback: a
/// page that loads replaces it, and Android may empty the folder at will.
///
/// A page of 100 posts took about 6 ms to encode on a desktop CPU, so the
/// JSON is written and read on a background isolate, as the group feed
/// cache does.
class BooruFeedCache {
  BooruFeedCache({FFCache? cache}) : _cache = cache ?? FFCache(name: booruFeedCacheName);

  final FFCache _cache;

  /// How long a kept page can still be shown.
  static const keep = Duration(days: 14);

  /// The page kept under [key], or null when there is none or it cannot be
  /// read.
  Future<BooruPostPage?> read(String key) async {
    try {
      final text = await _cache.getString(_fileName(key));
      return text == null ? null : await compute(_decodePage, text);
    } catch (_) {
      return null;
    }
  }

  /// Keeps [page] under [key]. A failed write only means there is no copy.
  Future<void> write(String key, BooruPostPage page) async {
    try {
      await _cache.setStringWithTimeout(_fileName(key), await compute(_encodePage, page), keep);
    } catch (_) {
      // Nothing kept; the feed still shows what it loaded.
    }
  }

  /// A key names the site, filters and query, so it is hashed into a short
  /// file name.
  static String _fileName(String key) => sha1.convert(utf8.encode(key)).toString();
}

String _encodePage(BooruPostPage page) => jsonEncode({
  'page': page.page,
  'hasMore': page.hasMore,
  'posts': [for (final post in page.posts) booruPostToJson(post)],
});

BooruPostPage? _decodePage(String text) {
  final json = Json(jsonDecode(text));
  final posts = [for (final post in json['posts'].list) ?booruPostFromJson(post)];
  if (posts.isEmpty) return null;
  return BooruPostPage(posts: posts, page: json['page'].integer ?? 1, hasMore: json['hasMore'].boolean ?? true);
}
