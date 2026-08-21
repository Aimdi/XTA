import 'dart:convert';

/// A public RSS or Atom feed the reader follows.
class RssFeed {
  final String id;
  final String feedUrl;
  final String name;
  final String? siteUrl;
  final String? iconUrl;
  final String? description;

  const RssFeed({
    required this.id,
    required this.feedUrl,
    required this.name,
    this.siteUrl,
    this.iconUrl,
    this.description,
  });

  Map<String, dynamic> toJson() => {
    'id': id,
    'feedUrl': feedUrl,
    'name': name,
    'siteUrl': siteUrl,
    'iconUrl': iconUrl,
    'description': description,
  };

  factory RssFeed.fromJson(Map<String, dynamic> json) {
    final feedUrl =
        json['feedUrl'] as String? ?? json['feed_url'] as String? ?? '';
    return RssFeed(
      id: json['id'] as String? ?? rssFeedId(feedUrl),
      feedUrl: feedUrl,
      name: json['name'] as String? ?? '',
      siteUrl: json['siteUrl'] as String? ?? json['site_url'] as String?,
      iconUrl: json['iconUrl'] as String? ?? json['icon_url'] as String?,
      description: json['description'] as String?,
    );
  }

  static List<RssFeed> listFromPrefs(String? raw) {
    if (raw == null || raw.isEmpty) return const [];
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) return const [];
      return [
        for (final item in decoded)
          if (item is Map) RssFeed.fromJson(Map<String, dynamic>.from(item)),
      ].where((feed) => feed.id.isNotEmpty && feed.feedUrl.isNotEmpty).toList();
    } catch (_) {
      return const [];
    }
  }

  static String listToPrefs(List<RssFeed> feeds) =>
      jsonEncode([for (final feed in feeds) feed.toJson()]);
}

/// One item from an RSS or Atom feed.
class RssItem {
  final String id;
  final String title;
  final String? link;
  final String? excerpt;
  final String? bodyHtml;
  final DateTime? publishedAt;
  final String? author;
  final String? imageUrl;
  final String feedId;
  final String feedTitle;
  final List<String> categories;

  const RssItem({
    required this.id,
    required this.title,
    required this.feedId,
    required this.feedTitle,
    this.link,
    this.excerpt,
    this.bodyHtml,
    this.publishedAt,
    this.author,
    this.imageUrl,
    this.categories = const [],
  });

  bool get hasReadableBody {
    final html = bodyHtml?.trim() ?? '';
    return html.isNotEmpty && html != excerpt?.trim();
  }
}

enum RssFeedFilter { all, unread }

/// Channel-level bits plus items. A mangled feed still yields whatever could
/// be read — never a throw.
class RssChannel {
  final String? title;
  final String? description;
  final String? link;
  final String? imageUrl;
  final List<RssItem> items;

  const RssChannel({
    this.title,
    this.description,
    this.link,
    this.imageUrl,
    this.items = const [],
  });
}

/// Stable id for a feed URL: scheme + host + path, no trailing slash.
String rssFeedId(String url) {
  final uri = Uri.tryParse(url.trim());
  if (uri == null || uri.host.isEmpty) {
    return url.trim();
  }
  final path = uri.path.replaceAll(RegExp(r'/+$'), '');
  return Uri(
    scheme: uri.scheme.isEmpty ? 'https' : uri.scheme.toLowerCase(),
    host: uri.host.toLowerCase(),
    port: uri.hasPort ? uri.port : null,
    path: path,
  ).toString();
}

/// True when [raw] already names a feed document, not a site home page.
bool looksLikeRssUrl(String raw) {
  final uri = Uri.tryParse(raw.trim());
  if (uri == null || uri.host.isEmpty || !uri.hasScheme) {
    return false;
  }
  final path = uri.path.toLowerCase();
  return RegExp(
    r'(?:^|/)(?:feed|rss|atom)(?:[./]|$)|\.(?:xml|rss|atom)$',
  ).hasMatch(path);
}

List<String> readIdsFromPrefs(String? raw) {
  if (raw == null || raw.isEmpty) return const [];
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! List) return const [];
    return decoded.whereType<String>().where((e) => e.isNotEmpty).toList();
  } catch (_) {
    return const [];
  }
}

String readIdsToPrefs(List<String> ids) => jsonEncode(ids);

/// `{feedId: ["news", "blogs"]}` — tags stay in preferences.
Map<String, List<String>> rssTagsFromPrefs(String? raw) {
  if (raw == null || raw.isEmpty) return const {};
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! Map) return const {};
    return {
      for (final entry in decoded.entries)
        if (entry.key is String && entry.value is List)
          entry.key as String: [
            for (final tag in entry.value as List)
              if (tag is String && tag.trim().isNotEmpty) tag.trim(),
          ],
    };
  } catch (_) {
    return const {};
  }
}

String rssTagsToPrefs(Map<String, List<String>> tags) => jsonEncode(tags);

/// Newest first, one row per item id. A later copy of the same id wins so a
/// refresh can update a title without growing the list.
List<RssItem> mergeRssItems(
  Iterable<RssItem> existing,
  Iterable<RssItem> incoming,
) {
  final byId = {for (final item in existing) item.id: item};
  for (final item in incoming) {
    byId[item.id] = item;
  }
  final merged = byId.values.toList()
    ..sort((a, b) => _dateOf(b).compareTo(_dateOf(a)));
  return merged;
}

DateTime _dateOf(RssItem item) =>
    item.publishedAt ?? DateTime.fromMillisecondsSinceEpoch(0);

bool itemMatchesRssFilter(
  RssItem item,
  RssFeedFilter filter,
  Set<String> readIds, {
  String? tag,
  Map<String, List<String>> tagsByFeed = const {},
}) {
  if (filter == RssFeedFilter.unread && readIds.contains(item.id)) {
    return false;
  }
  if (tag == null || tag.isEmpty) {
    return true;
  }
  return (tagsByFeed[item.feedId] ?? const []).contains(tag);
}
