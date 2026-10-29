/// A web source's page, read with its rules into posts.
///
/// Pure: the page comes in as text. A page that has changed shape gives
/// fewer posts, or none, never a throw.
library;

import 'dart:convert';

import 'package:html/parser.dart' as html;
import 'package:xta/plugins/rss/rss_parser.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_paths.dart';

const webSourceMaxItems = 100;

class WebSourceItem {
  final String sourceId;
  final String id;
  final String? title;
  final String? text;
  final String? html;
  final String? link;
  final String? author;
  final String? avatar;
  final String? image;
  final String? discussion;
  final List<String> images;
  final List<String> tags;
  final DateTime? date;
  final int? score;
  final int? comments;

  /// Every single-valued field as read, for an article address.
  final Map<String, String> values;

  const WebSourceItem({
    required this.sourceId,
    required this.id,
    this.title,
    this.text,
    this.html,
    this.link,
    this.author,
    this.avatar,
    this.image,
    this.discussion,
    this.images = const [],
    this.tags = const [],
    this.date,
    this.score,
    this.comments,
    this.values = const {},
  });

  /// Unique across sources.
  String get key => '$sourceId:$id';

  /// Every picture, the main one first.
  List<String> get pictures => {?image, ...images}.toList();
}

typedef WebSourcePage = ({List<WebSourceItem> items, Uri? next});

const _emptyPage = (items: <WebSourceItem>[], next: null);

/// The address of [list]'s page [page] (from its first), or of the page
/// [cursor] names, or of [query]'s results. Null when the template cannot be
/// filled.
Uri? webSourceListUrl(WebSourceList list, {int? page, String cursor = '', String? query}) {
  final filled = fillTemplate(
    list.url,
    (name) => switch (name) {
      'page' => '${page ?? list.firstPage}',
      'cursor' => cursor,
      'query' => query,
      _ => null,
    },
    encode: true,
  );
  return filled == null ? null : Uri.tryParse(filled);
}

/// [body], the page of [list] at [url] (page [page] when it counts pages).
WebSourcePage webSourcePage(
  WebSourceDefinition source,
  WebSourceList list,
  String body,
  Uri url, {
  int? page,
  String? query,
}) => switch (list.type) {
  WebSourcePageType.json => _jsonPage(source, list, body, url, page: page, query: query),
  WebSourcePageType.html => _htmlPage(source, list, body, url, page: page, query: query),
  WebSourcePageType.rss => _rssPage(source, body, url),
};

WebSourcePage _jsonPage(
  WebSourceDefinition source,
  WebSourceList list,
  String body,
  Uri url, {
  int? page,
  String? query,
}) {
  final Object? root;
  try {
    root = jsonDecode(body);
  } on FormatException {
    return _emptyPage;
  }
  final found = jsonValuesAt(root, list.items);
  final elements = found.length == 1 && found.single is List ? found.single as List : found;
  final items = _posts(source, list, [
    for (final element in elements)
      (String rule) => [for (final value in jsonValuesAt(element, rule)) ?jsonText(value)],
  ], url);
  final named = list.next == null ? null : jsonText(jsonValuesAt(root, list.next!).firstOrNull);
  return (items: items, next: _next(list, items, url, named, page: page, query: query, cursor: true));
}

WebSourcePage _htmlPage(
  WebSourceDefinition source,
  WebSourceList list,
  String body,
  Uri url, {
  int? page,
  String? query,
}) {
  final document = html.parse(body);
  final root = document.documentElement;
  if (root == null) return _emptyPage;
  final items = _posts(source, list, [
    for (final element in htmlSelectAll(root, list.items)) (String rule) => htmlValuesAt(element, rule),
  ], url);
  final named = list.next == null ? null : htmlValuesAt(root, list.next!).firstOrNull;
  return (items: items, next: _next(list, items, url, named, page: page, query: query, cursor: false));
}

WebSourcePage _rssPage(WebSourceDefinition source, String body, Uri url) {
  final channel = parseRss(body, feedUrl: url.toString());
  final items = [
    for (final item in channel.items.take(webSourceMaxItems))
      WebSourceItem(
        sourceId: source.id,
        id: item.id,
        title: item.title,
        text: item.excerpt,
        html: item.bodyHtml,
        link: item.link,
        author: item.author,
        image: item.imageUrl,
        date: item.publishedAt,
        tags: item.categories,
        values: {'id': item.id, 'title': item.title, ...?_entry('link', item.link)},
      ),
  ];
  return (items: items, next: null);
}

Map<String, String>? _entry(String key, String? value) => value == null ? null : {key: value};

/// Finds the values a rule reads in one post of a page.
typedef _Read = List<String> Function(String rule);

/// Every [WebSourceItem] of a page whose posts are given as readers.
List<WebSourceItem> _posts(WebSourceDefinition source, WebSourceList list, List<_Read> elements, Uri url) => [
  for (final read in elements.take(webSourceMaxItems)) ?_post(source, list.fields, read, url),
];

const _addresses = {WebSourceField.link, WebSourceField.avatar, WebSourceField.image, WebSourceField.discussion};

WebSourceItem? _post(WebSourceDefinition source, Map<WebSourceField, String> fields, _Read read, Uri url) {
  final values = _singleValues(fields, read, url);
  final id = values[WebSourceField.id] ?? values[WebSourceField.link];
  final title = _plain(values[WebSourceField.title]);
  final text = _plain(values[WebSourceField.text]);
  if (id == null || (title == null && text == null)) return null;
  return WebSourceItem(
    sourceId: source.id,
    id: id,
    title: title,
    text: text,
    html: values[WebSourceField.html],
    link: values[WebSourceField.link],
    author: values[WebSourceField.author],
    avatar: values[WebSourceField.avatar],
    image: values[WebSourceField.image],
    discussion: values[WebSourceField.discussion],
    images: [for (final image in _allValues(fields[WebSourceField.images], read)) ?_address(image, url)],
    tags: _allValues(fields[WebSourceField.tags], read),
    date: webSourceDate(values[WebSourceField.date]),
    score: _count(values[WebSourceField.score]),
    comments: _count(values[WebSourceField.comments]),
    values: {for (final MapEntry(:key, :value) in values.entries) key.name: value},
  );
}

/// The first value of every field, addresses made absolute.
Map<WebSourceField, String> _singleValues(Map<WebSourceField, String> fields, _Read read, Uri url) => {
  for (final MapEntry(key: field, value: rule) in fields.entries)
    if (_single(rule, read) case final value? when value.isNotEmpty)
      field: ?(_addresses.contains(field) ? _address(value, url) : value),
};

String? _single(String rule, _Read read) =>
    isTemplate(rule) ? fillTemplate(rule, (part) => read(part).firstOrNull) : read(rule).firstOrNull;

List<String> _allValues(String? rule, _Read read) => switch (rule) {
  null => const [],
  _ when isTemplate(rule) => [?_single(rule, read)],
  _ => read(rule),
};

/// [value] as an absolute web address, resolved against the page [url].
String? _address(String value, Uri url) {
  final uri = Uri.tryParse(value.trim());
  if (uri == null) return null;
  final resolved = url.resolveUri(uri);
  return resolved.isScheme('https') || resolved.isScheme('http') ? resolved.toString() : null;
}

/// Text that may arrive with markup in it, as plain text.
String? _plain(String? value) {
  if (value == null) return null;
  final text = value.contains('<') || value.contains('&') ? html.parseFragment(value).text ?? value : value;
  final trimmed = text.replaceAll(RegExp(r'\s+'), ' ').trim();
  return trimmed.isEmpty ? null : trimmed;
}

int? _count(String? value) => value == null ? null : int.tryParse(value) ?? double.tryParse(value)?.round();

final _digits = RegExp(r'^\d+$');

/// A date as sites write them: ISO 8601, an RSS date, or Unix seconds or
/// milliseconds.
DateTime? webSourceDate(String? value) {
  final text = value?.trim();
  if (text == null || text.isEmpty) return null;
  if (_digits.hasMatch(text)) {
    final number = int.parse(text);
    return text.length >= 13
        ? DateTime.fromMillisecondsSinceEpoch(number, isUtc: true)
        : DateTime.fromMillisecondsSinceEpoch(number * 1000, isUtc: true);
  }
  return DateTime.tryParse(text) ?? parseRssDate(text);
}

Uri? _next(
  WebSourceList list,
  List<WebSourceItem> items,
  Uri url,
  String? named, {
  int? page,
  String? query,
  required bool cursor,
}) {
  if (list.next != null) {
    if (named == null || named.isEmpty) return null;
    final address = named.startsWith('http') || named.startsWith('/') || !cursor;
    final next = address
        ? Uri.tryParse(_address(named, url) ?? '')
        : webSourceListUrl(list, cursor: named, query: query);
    return next == url ? null : next;
  }
  if (items.isEmpty || !templatePlaceholders(list.url).contains('page')) return null;
  return webSourceListUrl(list, page: (page ?? list.firstPage) + 1, query: query);
}

/// Where [article] keeps [item]'s full text, or null when its address cannot
/// be built from the item.
Uri? webSourceArticleUrl(WebSourceArticle article, WebSourceItem item) {
  final filled = fillTemplate(article.url, (name) => item.values[name], encode: true);
  return filled == null ? null : Uri.tryParse(filled);
}

/// The markup [article] reads from [body], an article page.
String? webSourceArticleHtml(WebSourceArticle article, String body) {
  switch (article.type) {
    case WebSourcePageType.json:
      try {
        return jsonText(jsonValuesAt(jsonDecode(body), article.html).firstOrNull);
      } on FormatException {
        return null;
      }
    case WebSourcePageType.html:
      final rule = htmlRule(article.html).attribute == null ? '${article.html}@html' : article.html;
      final root = html.parse(body).documentElement;
      return root == null ? null : htmlValuesAt(root, rule).firstOrNull;
    case WebSourcePageType.rss:
      return null;
  }
}
