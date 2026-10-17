import 'dart:convert';

import 'package:ffcache/ffcache.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/utils/json.dart';

/// One article in the cache's own index, which FFCache cannot list.
typedef SubstackCacheEntry = ({String key, int bytes, int savedAt});

/// The newest entries of [index], oldest first, that fit both limits. The
/// newest entry stays whatever its size: it is the one just read.
List<SubstackCacheEntry> substackCacheKeep(List<SubstackCacheEntry> index, {required int articles, required int bytes}) {
  var total = 0;
  var start = index.length;
  while (start > 0 && index.length - start < articles) {
    total += index[start - 1].bytes;
    if (total > bytes && start < index.length) break;
    start--;
  }
  return index.sublist(start);
}

/// Whether [cached] still matches [listed], the copy the reader was opened
/// with: a different date or title means the post was edited or rescheduled.
/// A side that does not know a field, like a deep link, does not count.
bool substackCachedCopyIsCurrent(SubstackPost cached, SubstackPost listed) {
  bool same(String? a, String? b) {
    final left = a?.trim() ?? '';
    final right = b?.trim() ?? '';
    return left.isEmpty || right.isEmpty || left == right;
  }

  return same(cached.postDate, listed.postDate) && same(cached.title, listed.title);
}

/// Public article bodies kept on device after the first successful open.
///
/// Cache-only — never a source of truth. Cleared by the OS at will; a miss
/// just means the next open hits the network again. Bounded by count and size:
/// opening an article writes it again, so the least recently read go first.
class SubstackArticleCache {
  SubstackArticleCache({FFCache? cache, this.maxArticles = 200, this.maxBytes = 16 << 20})
    : _cache = cache ?? FFCache(name: 'substack_articles');

  final FFCache _cache;
  final int maxArticles;
  final int maxBytes;
  static const _ttl = Duration(days: 14);
  static const _indexKey = 'index';

  /// Writes from every reader in turn: they share one index.
  static Future<void> _writes = Future.value();

  static String keyFor(SubstackPublication publication, String slug) {
    final base = Uri.tryParse(publication.baseUrl);
    final origin = base != null && base.hasAuthority ? base.origin : publication.baseUrl;
    return '$origin::${slug.trim()}';
  }

  Future<SubstackPost?> get(SubstackPublication publication, String slug) async {
    try {
      if (!await _cache.has(keyFor(publication, slug))) return null;
      final raw = await _cache.getJSON(keyFor(publication, slug));
      if (raw is! String || raw.isEmpty) return null;
      final map = jsonDecode(raw);
      if (map is! Map) return null;
      final post = SubstackPost.fromJson(
        Map<String, dynamic>.from(map),
        publicationBaseUrl: publication.baseUrl,
        publicationName: publication.name,
        includeBody: true,
      );
      return post.slug == slug.trim() ? post : null;
    } catch (_) {
      return null;
    }
  }

  Future<void> put(SubstackPost post) async {
    final html = post.bodyHtml?.trim();
    if (html == null || html.isEmpty) return;
    final payload = jsonEncode({
      'id': post.id,
      'title': post.title,
      'subtitle': post.subtitle,
      'description': post.description,
      'slug': post.slug,
      'post_date': post.postDate,
      'canonical_url': post.canonicalUrl,
      'cover_image': post.coverImage,
      'body_html': post.bodyHtml,
      'audience': post.audience,
      'type': post.type,
      'podcast_url': post.audioUrl,
      if (post.hasVideoUpload) 'videoUpload': true,
      'reaction_count': post.reactionCount,
      'comment_count': post.commentCount,
      'wordcount': post.wordCount,
      'publishedBylines': [
        if (post.authorName != null) {'name': post.authorName},
      ],
    });
    final write = _writes.then((_) => _store(keyFor(post.publication, post.slug), payload));
    _writes = write;
    await write;
  }

  Future<void> _store(String key, String payload) async {
    try {
      final now = DateTime.now().millisecondsSinceEpoch;
      final index = [
        ...(await _index(now)).where((entry) => entry.key != key),
        (key: key, bytes: payload.length, savedAt: now),
      ];
      final kept = substackCacheKeep(index, articles: maxArticles, bytes: maxBytes);
      for (final dropped in index.take(index.length - kept.length)) {
        await _cache.remove(dropped.key);
      }
      await _cache.setJSONWithTimeout(key, payload, _ttl);
      await _cache.setJSONWithTimeout(_indexKey, jsonEncode([for (final e in kept) [e.key, e.bytes, e.savedAt]]), _ttl);
    } catch (_) {
      // Cache is optional.
    }
  }

  Future<List<SubstackCacheEntry>> _index(int now) async {
    if (!await _cache.has(_indexKey)) return const [];
    final raw = await _cache.getJSON(_indexKey);
    final decoded = raw is String ? jsonDecode(raw) : null;
    return [
      for (final row in Json(decoded).list)
        if ((row[0].string, row[1].integer, row[2].integer) case (final key?, final bytes?, final savedAt?))
          if (now - savedAt < _ttl.inMilliseconds) (key: key, bytes: bytes, savedAt: savedAt),
    ];
  }
}
