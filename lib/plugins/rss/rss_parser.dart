import 'package:xta/plugins/rss/rss_models.dart';

/// Parses RSS 2.0 or Atom into a channel. Pure and tolerant: a mangled feed
/// yields whatever items could be read, never a throw.
RssChannel parseRss(String xml, {required String feedUrl}) {
  final feedId = rssFeedId(feedUrl);
  if (_looksAtom(xml)) {
    return _parseAtom(xml, feedId: feedId, feedUrl: feedUrl);
  }
  return _parseRss2(xml, feedId: feedId, feedUrl: feedUrl);
}

bool _looksAtom(String xml) {
  return RegExp(
    r'<(?:[a-zA-Z0-9]+:)?feed\b',
    caseSensitive: false,
  ).hasMatch(xml);
}

RssChannel _parseRss2(
  String xml, {
  required String feedId,
  required String feedUrl,
}) {
  final channel = _firstTag(xml, 'channel') ?? xml;
  final title = _textTag(channel, 'title');
  final feedTitle = title?.trim().isNotEmpty == true ? title!.trim() : feedId;
  final items = [
    for (final item in _allTags(channel, 'item'))
      if (_itemFromRss(item, feedId: feedId, feedTitle: feedTitle)
          case final parsed?)
        parsed,
  ];
  return RssChannel(
    title: title?.trim(),
    description: _textTag(channel, 'description')?.trim(),
    link: _textTag(channel, 'link')?.trim(),
    imageUrl: _channelImage(channel),
    items: items,
  );
}

RssChannel _parseAtom(
  String xml, {
  required String feedId,
  required String feedUrl,
}) {
  final feed = _firstTag(xml, 'feed') ?? xml;
  final title = _textTag(feed, 'title');
  final feedTitle = title?.trim().isNotEmpty == true ? title!.trim() : feedId;
  final items = [
    for (final entry in _allTags(feed, 'entry'))
      if (_itemFromAtom(entry, feedId: feedId, feedTitle: feedTitle)
          case final parsed?)
        parsed,
  ];
  return RssChannel(
    title: title?.trim(),
    description: _textTag(feed, 'subtitle')?.trim(),
    link: _atomLink(feed, preferHtml: true),
    imageUrl: _textTag(feed, 'logo')?.trim() ?? _textTag(feed, 'icon')?.trim(),
    items: items,
  );
}

RssItem? _itemFromRss(
  String item, {
  required String feedId,
  required String feedTitle,
}) {
  final title = _textTag(item, 'title')?.trim() ?? '';
  final link = _textTag(item, 'link')?.trim();
  final guid = _textTag(item, 'guid')?.trim();
  final id = _itemId(feedId, guid: guid, link: link, title: title);
  if (id == null) return null;

  final content =
      _textTag(item, 'content:encoded') ?? _textTag(item, 'description');
  return RssItem(
    id: id,
    title: title.isEmpty ? feedTitle : title,
    link: link,
    excerpt: _plainExcerpt(content),
    bodyHtml: content?.trim(),
    publishedAt: parseRssDate(
      _textTag(item, 'pubDate') ??
          _textTag(item, 'dc:date') ??
          _textTag(item, 'date'),
    ),
    author: _textTag(item, 'dc:creator') ?? _textTag(item, 'author'),
    imageUrl: _itemImage(item, content),
    feedId: feedId,
    feedTitle: feedTitle,
    categories: _categories(item),
  );
}

RssItem? _itemFromAtom(
  String entry, {
  required String feedId,
  required String feedTitle,
}) {
  final title = _textTag(entry, 'title')?.trim() ?? '';
  final link = _atomLink(entry, preferHtml: true);
  final guid = _textTag(entry, 'id')?.trim();
  final id = _itemId(feedId, guid: guid, link: link, title: title);
  if (id == null) return null;

  final content = _textTag(entry, 'content') ?? _textTag(entry, 'summary');
  return RssItem(
    id: id,
    title: title.isEmpty ? feedTitle : title,
    link: link,
    excerpt: _plainExcerpt(content),
    bodyHtml: content?.trim(),
    publishedAt: parseRssDate(
      _textTag(entry, 'published') ?? _textTag(entry, 'updated'),
    ),
    author: _atomAuthor(entry),
    imageUrl: _itemImage(entry, content),
    feedId: feedId,
    feedTitle: feedTitle,
    categories: _atomCategories(entry),
  );
}

String? _itemId(
  String feedId, {
  String? guid,
  String? link,
  required String title,
}) {
  final raw = (guid != null && guid.isNotEmpty)
      ? guid
      : (link != null && link.isNotEmpty)
      ? link
      : (title.isNotEmpty ? title : null);
  if (raw == null) return null;
  return '$feedId::$raw';
}

String? _channelImage(String channel) {
  final image = _firstTag(channel, 'image');
  return image == null ? null : _textTag(image, 'url')?.trim();
}

String? _itemImage(String item, String? content) {
  final enclosure = _attr(_firstOpenTag(item, 'enclosure') ?? '', 'url');
  if (enclosure != null && _looksImage(enclosure)) return enclosure;
  final media =
      _attr(_firstOpenTag(item, 'media:content') ?? '', 'url') ??
      _attr(_firstOpenTag(item, 'media:thumbnail') ?? '', 'url');
  if (media != null && media.isNotEmpty) return media;
  return _firstImg(content);
}

bool _looksImage(String url) {
  final lower = url.toLowerCase();
  return lower.contains('.jpg') ||
      lower.contains('.jpeg') ||
      lower.contains('.png') ||
      lower.contains('.webp') ||
      lower.contains('.gif') ||
      lower.contains('image');
}

List<String> _categories(String xml) {
  return [
    for (final raw in _allTextTags(xml, 'category'))
      if (raw.trim().isNotEmpty) raw.trim(),
  ];
}

List<String> _atomCategories(String xml) {
  return [
    for (final open in _allOpenTags(xml, 'category'))
      if (_attr(open, 'term')?.trim() case final term? when term.isNotEmpty)
        term,
  ];
}

String? _atomAuthor(String entry) {
  final author = _firstTag(entry, 'author');
  return author == null ? null : _textTag(author, 'name') ?? author.trim();
}

String? _atomLink(String xml, {required bool preferHtml}) {
  String? fallback;
  for (final open in _allOpenTags(xml, 'link')) {
    final href = _attr(open, 'href') ?? _attr(open, 'url');
    if (href == null || href.isEmpty) continue;
    final rel = (_attr(open, 'rel') ?? 'alternate').toLowerCase();
    final type = (_attr(open, 'type') ?? '').toLowerCase();
    if (rel != 'alternate' && rel != 'self' && rel.isNotEmpty) continue;
    if (preferHtml && (type.contains('html') || type.isEmpty)) {
      return href;
    }
    fallback ??= href;
  }
  return fallback ?? _textTag(xml, 'link')?.trim();
}

/// RFC 822, RFC 3339, and a few ISO shapes. Null when nothing parses.
DateTime? parseRssDate(String? raw) {
  if (raw == null || raw.trim().isEmpty) return null;
  final value = raw.trim();
  final rfc822 = _parseRfc822(value);
  if (rfc822 != null) return rfc822;
  final iso = DateTime.tryParse(value);
  if (iso != null) return iso.toUtc();
  return _parseLooseDate(value);
}

DateTime? _parseRfc822(String raw) {
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
    r'(?:\w{3},\s+)?(\d{1,2})\s+(\w{3})\s+(\d{4})\s+(\d{2}):(\d{2})(?::(\d{2}))?\s+([A-Z]+|[+-]\d{4})',
    caseSensitive: false,
  ).firstMatch(raw);
  if (m == null) return null;
  final month = months[m.group(2)!.toLowerCase()];
  if (month == null) return null;
  return DateTime.utc(
    int.parse(m.group(3)!),
    month,
    int.parse(m.group(1)!),
    int.parse(m.group(4)!),
    int.parse(m.group(5)!),
    int.parse(m.group(6) ?? '0'),
  );
}

DateTime? _parseLooseDate(String raw) {
  final m = RegExp(r'^(\d{4})[-/](\d{1,2})[-/](\d{1,2})').firstMatch(raw);
  if (m == null) return null;
  return DateTime.utc(
    int.parse(m.group(1)!),
    int.parse(m.group(2)!),
    int.parse(m.group(3)!),
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

/// `<link rel="alternate" type="application/rss+xml" href="…">` in a page.
List<String> discoverFeedUrls(String html, {required Uri page}) {
  final found = <String>{};
  final links = RegExp(r'<link\b[^>]*>', caseSensitive: false).allMatches(html);
  for (final match in links) {
    final tag = match.group(0)!;
    final rel = (_attr(tag, 'rel') ?? '').toLowerCase();
    final type = (_attr(tag, 'type') ?? '').toLowerCase();
    final href = _attr(tag, 'href');
    if (href == null || href.isEmpty) continue;
    final isAlternate = rel.contains('alternate') || rel.contains('feed');
    final isFeedType =
        type.contains('rss') || type.contains('atom') || type.contains('xml');
    if (!isAlternate || !isFeedType) continue;
    final resolved = page.resolve(href).toString();
    if (resolved.isNotEmpty) found.add(resolved);
  }
  return found.toList(growable: false);
}

List<String> commonFeedPaths(Uri site) {
  const paths = [
    '/feed',
    '/rss',
    '/atom.xml',
    '/feed.xml',
    '/index.xml',
    '/rss.xml',
  ];
  return [
    for (final path in paths) site.replace(path: path, query: '').toString(),
  ];
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

List<String> _allTextTags(String xml, String name) {
  return [
    for (final inner in _allTags(xml, name)) _decodeText(inner) ?? '',
  ].where((e) => e.isNotEmpty).toList(growable: false);
}

String? _firstOpenTag(String xml, String name) {
  return RegExp(
    '<$name\\b[^>]*/?>',
    caseSensitive: false,
  ).firstMatch(xml)?.group(0);
}

List<String> _allOpenTags(String xml, String name) {
  return RegExp(
    '<$name\\b[^>]*/?>',
    caseSensitive: false,
  ).allMatches(xml).map((m) => m.group(0)!).toList(growable: false);
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
  return _decodeText(inner);
}

String? _decodeText(String inner) {
  final cdata = RegExp(r'<!\[CDATA\[([\s\S]*?)\]\]>').firstMatch(inner);
  final text = cdata != null
      ? cdata.group(1)!
      : inner.replaceAll(RegExp(r'<[^>]+>'), '');
  return _unescape(text).trim();
}

String _unescape(String text) {
  return text
      .replaceAll('&amp;', '&')
      .replaceAll('&lt;', '<')
      .replaceAll('&gt;', '>')
      .replaceAll('&quot;', '"')
      .replaceAll('&#39;', "'")
      .replaceAll('&apos;', "'")
      .replaceAll('&nbsp;', ' ');
}
