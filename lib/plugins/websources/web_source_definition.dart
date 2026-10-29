/// A web source file, read and checked: a site's lists, and where each
/// post's fields sit in them. See docs/specs/web-sources.md.
///
/// A file that breaks a rule is refused whole, with the reason and the key
/// it broke: a source is either exactly what it says or not installed.
library;

import 'dart:convert';

import 'package:xta/plugins/websources/web_source_paths.dart';
import 'package:xta/utils/json.dart';

const webSourceFormat = 1;
const webSourceMaxFileBytes = 64 * 1024;
const webSourceMaxLists = 12;
const webSourceMaxHosts = 8;
const _maxRuleLength = 300;

enum WebSourceLayout { links, articles, posts, gallery }

enum WebSourcePageType { json, html, rss }

enum WebSourceField {
  id,
  title,
  link,
  text,
  html,
  author,
  avatar,
  date,
  image,
  images,
  score,
  comments,
  discussion,
  tags,
}

/// One list of posts: a feed, or the source's search.
class WebSourceList {
  final String id;
  final String name;

  /// Address template: `{page}`, `{cursor}` and, for search, `{query}`.
  final String url;
  final WebSourcePageType type;

  /// JSON path to the posts, or CSS selector matching each post.
  final String items;
  final Map<WebSourceField, String> fields;
  final int firstPage;

  /// Where the next page is named in this one, if it is.
  final String? next;

  const WebSourceList({
    required this.id,
    required this.name,
    required this.url,
    required this.type,
    required this.items,
    required this.fields,
    this.firstPage = 1,
    this.next,
  });
}

/// Where a post's full text is, for the reader.
class WebSourceArticle {
  /// Address template over the post's fields: `{id}`, `{link}`…
  final String url;
  final WebSourcePageType type;

  /// JSON path or HTML rule to the text's markup.
  final String html;

  const WebSourceArticle({required this.url, required this.type, required this.html});
}

class WebSourceDefinition {
  final String id;
  final int version;
  final String name;
  final String description;
  final Uri homepage;
  final int? color;
  final Uri? icon;
  final List<String> hosts;
  final WebSourceLayout layout;
  final List<WebSourceList> feeds;
  final WebSourceList? search;
  final WebSourceArticle? article;

  /// The file as read, so it can be stored and read again.
  final Map<String, Object?> raw;

  const WebSourceDefinition({
    required this.id,
    required this.version,
    required this.name,
    required this.description,
    required this.homepage,
    required this.hosts,
    required this.layout,
    required this.feeds,
    required this.raw,
    this.color,
    this.icon,
    this.search,
    this.article,
  });

  /// Whether this source may contact [uri].
  bool allows(Uri uri) => webSourceHostAllowed(hosts, uri);

  WebSourceList? list(String listId) => [...feeds, ?search].where((list) => list.id == listId).firstOrNull;
}

/// Why a file was refused. The detail says which key or limit, as written in
/// the file, for whoever wrote it.
enum WebSourceRefusal { unreadable, newerFormat, invalid, insecure, tooLarge }

sealed class WebSourceFile {
  const WebSourceFile();
}

final class ReadWebSource extends WebSourceFile {
  final WebSourceDefinition source;

  const ReadWebSource(this.source);
}

final class RefusedWebSource extends WebSourceFile {
  final WebSourceRefusal refusal;
  final String detail;

  const RefusedWebSource(this.refusal, this.detail);
}

class _Refused implements Exception {
  final WebSourceRefusal refusal;
  final String detail;

  const _Refused(this.refusal, this.detail);
}

/// [body], a source file as downloaded.
WebSourceFile readWebSource(String body) {
  if (utf8.encode(body).length > webSourceMaxFileBytes) {
    return const RefusedWebSource(WebSourceRefusal.tooLarge, '64 KB');
  }
  final Object? decoded;
  try {
    decoded = jsonDecode(body);
  } on FormatException {
    return const RefusedWebSource(WebSourceRefusal.unreadable, '');
  }
  return webSourceFromJson(decoded);
}

/// [decoded], a source file already decoded, such as a stored copy.
WebSourceFile webSourceFromJson(Object? decoded) {
  if (decoded is! Map<String, Object?>) return const RefusedWebSource(WebSourceRefusal.unreadable, '');
  try {
    return ReadWebSource(_definition(Json(decoded), decoded));
  } on _Refused catch (refused) {
    return RefusedWebSource(refused.refusal, refused.detail);
  }
}

WebSourceDefinition _definition(Json file, Map<String, Object?> raw) {
  _checkFormat(file['format'].integer);
  final hosts = _hosts(file['hosts']);
  final layout = _enumNamed(WebSourceLayout.values, file['layout'].string, 'layout');
  final feeds = _feeds(file, hosts, layout);
  return WebSourceDefinition(
    id: _matching(file['id'].string, _sourceId, 'id'),
    version: _positive(file['version'].integer, 'version'),
    name: _text(file['name'].string, 'name', max: 40),
    description: file['description'].exists ? _text(file['description'].string, 'description', max: 200) : '',
    homepage: _onHosts(file['homepage'].string, hosts, 'homepage'),
    color: _color(file['color']),
    icon: file['icon'].exists ? _onHosts(file['icon'].string, hosts, 'icon') : null,
    hosts: hosts,
    layout: layout,
    feeds: feeds,
    search: file['search'].exists ? _list(file['search'], file, hosts, layout, 'search', search: true) : null,
    article: file['article'].exists ? _article(file['article'], file) : null,
    raw: raw,
  );
}

final _sourceId = RegExp(r'^[a-z0-9][a-z0-9-]{1,39}$');

/// Whether [id] is a well-formed source id.
bool isWebSourceId(String? id) => id != null && _sourceId.hasMatch(id);
final _listId = RegExp(r'^[a-z0-9][a-z0-9-]{0,39}$');
final _hostName = RegExp(r'^(?:\*\.)?(?:[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?\.)+[a-z]{2,63}$');
final _hexColor = RegExp(r'^#[0-9A-Fa-f]{6}$');

void _checkFormat(int? format) {
  if (format != null && format > webSourceFormat) throw _Refused(WebSourceRefusal.newerFormat, 'format $format');
  if (format != webSourceFormat) throw const _Refused(WebSourceRefusal.invalid, 'format');
}

String _matching(String? value, RegExp pattern, String key) =>
    value != null && pattern.hasMatch(value) ? value : throw _Refused(WebSourceRefusal.invalid, key);

int _positive(int? value, String key) =>
    value != null && value > 0 ? value : throw _Refused(WebSourceRefusal.invalid, key);

String _text(String? value, String key, {required int max}) {
  final text = value?.trim() ?? '';
  return text.isNotEmpty && text.length <= max ? text : throw _Refused(WebSourceRefusal.invalid, key);
}

T _enumNamed<T extends Enum>(List<T> values, String? name, String key) =>
    values.where((value) => value.name == (name ?? values.first.name)).firstOrNull ??
    (throw _Refused(WebSourceRefusal.invalid, key));

List<String> _hosts(Json hosts) {
  final names = [for (final host in hosts.list) host.string?.toLowerCase()];
  if (names.isEmpty) throw const _Refused(WebSourceRefusal.invalid, 'hosts');
  if (names.length > webSourceMaxHosts) throw const _Refused(WebSourceRefusal.tooLarge, 'hosts: $webSourceMaxHosts');
  return [for (final (index, name) in names.indexed) _matching(name, _hostName, 'hosts[$index]')];
}

Uri _onHosts(String? address, List<String> hosts, String key) {
  if (address == null || address.isEmpty) throw _Refused(WebSourceRefusal.invalid, key);
  final uri = Uri.tryParse(address);
  if (uri == null || !webSourceHostAllowed(hosts, uri)) throw _Refused(WebSourceRefusal.insecure, key);
  return uri;
}

int? _color(Json color) {
  if (!color.exists) return null;
  final hex = _matching(color.string, _hexColor, 'color');
  return 0xFF000000 | int.parse(hex.substring(1), radix: 16);
}

/// Whether a source listing [hosts] may contact [uri]: HTTPS on the default
/// port, no credentials, and a listed host (`*.example.com` takes its
/// subdomains and itself).
bool webSourceHostAllowed(List<String> hosts, Uri uri) {
  if (!uri.isScheme('https') || uri.userInfo.isNotEmpty || (uri.hasPort && uri.port != 443)) return false;
  final host = uri.host.toLowerCase();
  return host.isNotEmpty &&
      hosts.any(
        (allowed) => allowed.startsWith('*.')
            ? host == allowed.substring(2) || host.endsWith(allowed.substring(1))
            : host == allowed,
      );
}

List<WebSourceList> _feeds(Json file, List<String> hosts, WebSourceLayout layout) {
  final feeds = file['feeds'].list;
  if (feeds.isEmpty) throw const _Refused(WebSourceRefusal.invalid, 'feeds');
  if (feeds.length > webSourceMaxLists) throw const _Refused(WebSourceRefusal.tooLarge, 'feeds: $webSourceMaxLists');
  final lists = [for (final (index, feed) in feeds.indexed) _list(feed, file, hosts, layout, 'feeds[$index]')];
  final ids = <String>{};
  for (final (index, list) in lists.indexed) {
    if (!ids.add(list.id)) throw _Refused(WebSourceRefusal.invalid, 'feeds[$index].id');
  }
  return lists;
}

WebSourceList _list(
  Json list,
  Json file,
  List<String> hosts,
  WebSourceLayout layout,
  String key, {
  bool search = false,
}) {
  final type = _enumNamed(WebSourcePageType.values, list['type'].string ?? file['type'].string, '$key.type');
  final items = list['items'].string ?? file['items'].string ?? '';
  if (type != WebSourcePageType.rss && !_validRule(items, type, items: true)) {
    throw _Refused(WebSourceRefusal.invalid, '$key.items');
  }
  final fields = type == WebSourcePageType.rss
      ? const <WebSourceField, String>{}
      : _fields(list, file, type, layout, key);
  final next = list['next'].string;
  if (next != null && !_validRule(next, type)) throw _Refused(WebSourceRefusal.invalid, '$key.next');
  return WebSourceList(
    id: search ? 'search' : _matching(list['id'].string, _listId, '$key.id'),
    name: search ? 'search' : _text(list['name'].string, '$key.name', max: 40),
    url: _listUrl(list['url'].string, hosts, '$key.url', search: search),
    type: type,
    items: items,
    fields: fields,
    firstPage: _firstPage(list['first_page'], '$key.first_page'),
    next: next,
  );
}

int _firstPage(Json value, String key) {
  if (!value.exists) return 1;
  final page = value.integer;
  return page != null && page >= 0 ? page : throw _Refused(WebSourceRefusal.invalid, key);
}

const _listPlaceholders = {'page', 'cursor'};

String _listUrl(String? template, List<String> hosts, String key, {required bool search}) {
  final placeholders = templatePlaceholders(template ?? '');
  final allowed = {..._listPlaceholders, if (search) 'query'};
  if (template == null || placeholders.any((name) => !allowed.contains(name))) {
    throw _Refused(WebSourceRefusal.invalid, key);
  }
  if (search && !placeholders.contains('query')) throw _Refused(WebSourceRefusal.invalid, key);
  final sample = fillTemplate(template, (name) => name == 'cursor' ? 'x' : '1', encode: true);
  _onHosts(sample, hosts, key);
  return template;
}

Map<WebSourceField, String> _fields(Json list, Json file, WebSourcePageType type, WebSourceLayout layout, String key) {
  final merged = <WebSourceField, String>{
    for (final source in [file['fields'], list['fields']])
      for (final field in WebSourceField.values) field: ?source[field.name].string,
  };
  for (final MapEntry(key: field, value: rule) in merged.entries) {
    if (!_validRule(rule, type)) {
      throw _Refused(WebSourceRefusal.invalid, '$key.fields.${field.name}');
    }
  }
  final named = merged.containsKey(WebSourceField.title) || merged.containsKey(WebSourceField.text);
  final found = merged.containsKey(WebSourceField.link) || merged.containsKey(WebSourceField.id);
  final pictured = merged.containsKey(WebSourceField.image) || merged.containsKey(WebSourceField.images);
  if (!named || !found || (layout == WebSourceLayout.gallery && !pictured)) {
    throw _Refused(WebSourceRefusal.invalid, '$key.fields');
  }
  return merged;
}

/// Whether [rule] can be read from a [type] page: a path or selector, or a
/// template whose every placeholder is one, in at most [_maxRuleLength].
bool _validRule(String rule, WebSourcePageType type, {bool items = false}) {
  if (rule.isEmpty || rule.length > _maxRuleLength) return false;
  if (!items && isTemplate(rule)) return templatePlaceholders(rule).every((part) => _validRule(part, type));
  return switch (type) {
    WebSourcePageType.json => isJsonPath(rule),
    WebSourcePageType.html => isCssSelector(htmlRule(rule).selector) && (!items || htmlRule(rule).attribute == null),
    WebSourcePageType.rss => true,
  };
}

final _fieldNames = {for (final field in WebSourceField.values) field.name};

WebSourceArticle _article(Json article, Json file) {
  final type = _enumNamed(WebSourcePageType.values, article['type'].string ?? file['type'].string, 'article.type');
  final url = article['url'].string ?? '';
  final html = article['html'].string ?? '';
  final placeholders = templatePlaceholders(url);
  if (placeholders.isEmpty || placeholders.any((name) => !_fieldNames.contains(name))) {
    throw const _Refused(WebSourceRefusal.invalid, 'article.url');
  }
  if (type == WebSourcePageType.rss || !_validRule(html, type)) {
    throw const _Refused(WebSourceRefusal.invalid, 'article.html');
  }
  return WebSourceArticle(url: url, type: type, html: html);
}
