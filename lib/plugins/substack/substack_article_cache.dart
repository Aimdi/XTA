import 'dart:convert';

import 'package:ffcache/ffcache.dart';
import 'package:xta/plugins/substack/substack_models.dart';

/// Public article bodies kept on device after the first successful open.
///
/// Cache-only — never a source of truth. Cleared by the OS at will; a miss
/// just means the next open hits the network again.
class SubstackArticleCache {
  SubstackArticleCache({FFCache? cache}) : _cache = cache ?? FFCache(name: 'substack_articles');

  final FFCache _cache;
  static const _ttl = Duration(days: 14);

  static String keyFor(SubstackPublication publication, String slug) =>
      '${publication.id}::${slug.trim().toLowerCase()}';

  Future<SubstackPost?> get(SubstackPublication publication, String slug) async {
    try {
      if (!await _cache.has(keyFor(publication, slug))) return null;
      final raw = await _cache.getJSON(keyFor(publication, slug));
      if (raw is! String || raw.isEmpty) return null;
      final map = jsonDecode(raw);
      if (map is! Map) return null;
      return SubstackPost.fromJson(
        Map<String, dynamic>.from(map),
        publicationBaseUrl: publication.baseUrl,
        publicationName: publication.name,
        includeBody: true,
      );
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
      'reaction_count': post.reactionCount,
      'comment_count': post.commentCount,
      'publishedBylines': [
        if (post.authorName != null) {'name': post.authorName},
      ],
    });
    try {
      await _cache.setJSONWithTimeout(keyFor(post.publication, post.slug), payload, _ttl);
    } catch (_) {
      // Cache is optional.
    }
  }
}
