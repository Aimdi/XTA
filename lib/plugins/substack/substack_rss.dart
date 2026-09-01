import 'package:xta/plugins/substack/substack_models.dart';

/// Channel-level bits from a Substack (or Substack-shaped) RSS `/feed`.
class SubstackRssChannel {
  final String? title;
  final String? description;
  final String? link;
  final String? imageUrl;
  final List<SubstackPost> posts;

  /// Whether the feed is Substack's, not Ghost / Beehiiv / a random blog.
  final bool looksLikeSubstack;

  const SubstackRssChannel({
    this.title,
    this.description,
    this.link,
    this.imageUrl,
    this.posts = const [],
    this.looksLikeSubstack = false,
  });
}

/// Substack stamps `<generator>Substack</generator>` and hosts images on
/// substackcdn; Ghost and other newsletter hosts do not.
bool rssLooksLikeSubstack(String xml) {
  final generator = RegExp(
    r'<generator[^>]*>([^<]+)</generator>',
    caseSensitive: false,
  ).firstMatch(xml)?.group(1)?.toLowerCase();
  if (generator != null && generator.contains('substack')) return true;
  return xml.contains('substackcdn.com') || xml.contains('.substack.com');
}

/// Parses Substack's public RSS into posts + channel metadata.
///
/// Pure and tolerant: a mangled feed yields whatever items could be read, never
/// a throw. CDATA and plain text are both accepted.
SubstackRssChannel parseSubstackRss(
  String xml, {
  required String publicationBaseUrl,
  required String publicationName,
}) {
  final channel = _firstTag(xml, 'channel') ?? xml;
  final title = _textTag(channel, 'title');
  final description = _textTag(channel, 'description');
  final link = _textTag(channel, 'link');
  final imageBlock = _firstTag(channel, 'image');
  final imageUrl = imageBlock == null ? null : _textTag(imageBlock, 'url');

  final posts = <SubstackPost>[];
  for (final item in _allTags(channel, 'item')) {
    final post = _postFromItem(
      item,
      publicationBaseUrl: publicationBaseUrl,
      publicationName: title?.trim().isNotEmpty == true
          ? title!.trim()
          : publicationName,
    );
    if (post != null) posts.add(post);
  }

  return SubstackRssChannel(
    title: title?.trim(),
    description: description?.trim(),
    link: link?.trim(),
    imageUrl: imageUrl?.trim(),
    posts: posts,
    looksLikeSubstack: rssLooksLikeSubstack(xml),
  );
}

SubstackPost? _postFromItem(
  String item, {
  required String publicationBaseUrl,
  required String publicationName,
}) {
  final title = _textTag(item, 'title')?.trim() ?? '';
  final link = _textTag(item, 'link')?.trim();
  final slug = _slugFromLink(link);
  if (title.isEmpty || slug == null) return null;

  final content =
      _textTag(item, 'content:encoded') ?? _textTag(item, 'description');
  final pubDate = _textTag(item, 'pubDate');
  final enclosure = _attr(_firstOpenTag(item, 'enclosure') ?? '', 'url');
  final audio = enclosure != null && _looksAudio(enclosure) ? enclosure : null;

  return SubstackPost(
    id: slug,
    title: title,
    slug: slug,
    subtitle: _plainExcerpt(content),
    postDate: _rssDate(pubDate),
    canonicalUrl: link ?? '$publicationBaseUrl/p/$slug',
    coverImage: _firstImg(content),
    bodyHtml: content,
    audience: 'everyone',
    authorName: _textTag(item, 'dc:creator') ?? _textTag(item, 'author'),
    type: audio != null ? 'podcast' : 'newsletter',
    audioUrl: audio,
    publicationBaseUrl: publicationBaseUrl,
    publicationName: publicationName,
  );
}

String? _slugFromLink(String? link) {
  if (link == null || link.isEmpty) return null;
  final ref = resolveSubstackPostRef(link);
  return ref?.slug;
}

String? _rssDate(String? raw) {
  if (raw == null || raw.trim().isEmpty) return null;
  try {
    // Substack uses RFC 822; Dart's HttpDate is closest without a new dependency.
    return _parseHttpDate(raw.trim())?.toUtc().toIso8601String();
  } catch (_) {
    return null;
  }
}

DateTime? _parseHttpDate(String raw) {
  // Wed, 04 Aug 2026 23:03:08 GMT
  final months = {
    'jan': 1,
    'feb': 2,
    'mar': 3,
    'apr': 4,
    'may': 5,
    'jun': 6,
    'jul': 7,
    'aug': 8,
    'sep': 9,
    'oct': 10,
    'nov': 11,
    'dec': 12,
  };
  final m = RegExp(
    r'^\w{3},\s+(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2}):(\d{2})\s+GMT$',
    caseSensitive: false,
  ).firstMatch(raw);
  if (m == null) return DateTime.tryParse(raw);
  final month = months[m.group(2)!.toLowerCase()];
  if (month == null) return null;
  return DateTime.utc(
    int.parse(m.group(3)!),
    month,
    int.parse(m.group(1)!),
    int.parse(m.group(4)!),
    int.parse(m.group(5)!),
    int.parse(m.group(6)!),
  );
}

String? _plainExcerpt(String? html) {
  if (html == null || html.trim().isEmpty) return null;
  final text = html
      .replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n')
      .replaceAll(RegExp(r'<[^>]+>'), ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
  if (text.isEmpty) return null;
  return text.length <= 240 ? text : '${text.substring(0, 237)}…';
}

String? _firstImg(String? html) {
  if (html == null) return null;
  return RegExp(
    r'''<img[^>]+src=["']([^"']+)["']''',
    caseSensitive: false,
  ).firstMatch(html)?.group(1);
}

bool _looksAudio(String url) {
  final lower = url.toLowerCase();
  return lower.contains('.mp3') ||
      lower.contains('.m4a') ||
      lower.contains('audio') ||
      lower.contains('podcast');
}

String? _firstTag(String xml, String name) {
  final match = RegExp(
    '<$name(?:\\s[^>]*)?>([\\s\\S]*?)</$name>',
    caseSensitive: false,
  ).firstMatch(xml);
  return match?.group(1);
}

List<String> _allTags(String xml, String name) {
  return RegExp(
    '<$name(?:\\s[^>]*)?>([\\s\\S]*?)</$name>',
    caseSensitive: false,
  ).allMatches(xml).map((m) => m.group(1)!).toList(growable: false);
}

String? _firstOpenTag(String xml, String name) {
  return RegExp(
    '<$name\\b[^>]*>',
    caseSensitive: false,
  ).firstMatch(xml)?.group(0);
}

String? _attr(String openTag, String name) {
  return RegExp(
    '$name=["\']([^"\']+)["\']',
    caseSensitive: false,
  ).firstMatch(openTag)?.group(1);
}

String? _textTag(String xml, String name) {
  final inner = _firstTag(xml, name);
  if (inner == null) return null;
  final cdata = RegExp(r'<!\[CDATA\[([\s\S]*?)\]\]>').firstMatch(inner);
  if (cdata != null) return cdata.group(1);
  return inner.replaceAll(RegExp(r'<[^>]+>'), '').trim();
}
