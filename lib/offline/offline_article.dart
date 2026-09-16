import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/substack/substack_models.dart';

/// A retained body, separate from the expiring article cache and Saved tables.
class OfflineArticle {
  final String id;
  final String title;
  final String source;
  final String bodyHtml;
  final String? url;
  final Map<String, dynamic> payload;

  const OfflineArticle({
    required this.id,
    required this.title,
    required this.source,
    required this.bodyHtml,
    required this.payload,
    this.url,
  });

  factory OfflineArticle.rss(RssItem item) => OfflineArticle(
    id: 'rss:${item.feedId}:${item.id}',
    title: item.title,
    source: item.feedTitle,
    bodyHtml: item.bodyHtml ?? '',
    url: item.link,
    payload: {
      'kind': 'rss',
      'id': item.id,
      'title': item.title,
      'feedId': item.feedId,
      'feedTitle': item.feedTitle,
      'link': item.link,
      'excerpt': item.excerpt,
      'publishedAt': item.publishedAt?.toIso8601String(),
      'author': item.author,
      'imageUrl': item.imageUrl,
      'categories': item.categories,
    },
  );

  factory OfflineArticle.substack(SubstackPost post) => OfflineArticle(
    id: 'substack:${post.publication.id}:${post.id}',
    title: post.title,
    source: post.publicationName,
    bodyHtml: post.bodyHtml ?? '',
    url: post.canonicalUrl ?? '${post.publicationBaseUrl}/p/${post.slug}',
    payload: {'kind': 'substack', ...post.toJson()},
  );

  RssItem? get rssItem => payload['kind'] != 'rss'
      ? null
      : RssItem(
          id: payload['id'] as String? ?? '',
          title: title,
          feedId: payload['feedId'] as String? ?? '',
          feedTitle: source,
          link: url,
          bodyHtml: bodyHtml,
          excerpt: payload['excerpt'] as String?,
          author: payload['author'] as String?,
          imageUrl: payload['imageUrl'] as String?,
          publishedAt: DateTime.tryParse(payload['publishedAt'] as String? ?? ''),
          categories: (payload['categories'] as List?)?.whereType<String>().toList() ?? const [],
        );

  SubstackPost? get substackPost {
    if (payload['kind'] != 'substack') return null;
    return SubstackPost.fromJson(
      {
        ...payload,
        'body_html': bodyHtml,
        if (payload['hasVideoUpload'] == true) 'videoUpload': true,
        'publishedBylines': [
          if (payload['authorName'] != null) {'name': payload['authorName']},
        ],
      },
      publicationBaseUrl: payload['publicationBaseUrl'] as String? ?? '',
      publicationName: source,
    );
  }

  Map<String, dynamic> toJson() => {
    'id': id,
    'title': title,
    'source': source,
    'bodyHtml': bodyHtml,
    'url': url,
    'payload': payload,
  };

  factory OfflineArticle.fromJson(Map<String, dynamic> json) => OfflineArticle(
    id: json['id'] as String,
    title: json['title'] as String,
    source: json['source'] as String,
    bodyHtml: json['bodyHtml'] as String,
    url: json['url'] as String?,
    payload: Map<String, dynamic>.from(json['payload'] as Map),
  );
}
