import 'package:intl/intl.dart';
import 'package:xml/xml.dart';
import 'package:xta/plugins/rss/rss_models.dart';

const rssOpmlMaxBytes = 2 * 1024 * 1024;
const rssOpmlMaxOutlines = 10000;
const rssOpmlMaxDepth = 32;
const rssOpmlMaxTags = 8;
const _maxTagLength = 64;

enum RssOpmlProblem { tooLarge, malformed, notOpml, declaresEntities, tooDeep }

class RssOpmlException implements Exception {
  final RssOpmlProblem problem;
  const RssOpmlException(this.problem);
  @override
  String toString() => 'RssOpmlException(${problem.name})';
}

/// A feed listed in an OPML file; [tags] come from the folders it sits in and its categories.
class RssOpmlFeed {
  final String url;
  final String title;
  final String? siteUrl;
  final List<String> tags;
  const RssOpmlFeed({required this.url, required this.title, this.siteUrl, this.tags = const []});

  RssOpmlFeed withTags(Iterable<String> more) =>
      RssOpmlFeed(url: url, title: title, siteUrl: siteUrl, tags: _tagsOf([...tags, ...more]));
}

/// The feeds an OPML file lists, once each, with counts of what was left out.
class RssOpmlDocument {
  final List<RssOpmlFeed> feeds;
  final int duplicates;
  final int skipped;
  const RssOpmlDocument({this.feeds = const [], this.duplicates = 0, this.skipped = 0});
}

final _badEscape = RegExp(r'%(?![0-9A-Fa-f]{2})');
final _whitespace = RegExp(r'\s');
final _trailingSlashes = RegExp(r'/+$');
final _tagSeparators = RegExp('[,/]');

({String origin, String path, String query})? _addressParts(String raw) {
  final text = raw.trim();
  if (text.isEmpty || text.length > 4096 || _badEscape.hasMatch(text) || _whitespace.hasMatch(text)) return null;
  final uri = Uri.tryParse(text);
  if (uri == null) return null;
  final scheme = uri.scheme.toLowerCase();
  if ((scheme != 'http' && scheme != 'https') || uri.host.isEmpty) return null;
  final host = uri.host.contains(':') ? '[${uri.host.toLowerCase()}]' : uri.host.toLowerCase();
  final userInfo = uri.userInfo.isEmpty ? '' : '${uri.userInfo}@';
  return (
    origin: '$scheme://$userInfo${uri.hasPort ? '$host:${uri.port}' : host}',
    path: uri.path.isEmpty ? '/' : uri.path,
    query: uri.hasQuery ? '?${uri.query}' : '',
  );
}

/// A feed address as it is stored: http(s) only, scheme and host lower-cased, fragment dropped. The path keeps its
/// case and the query its parameters and order, since feeds such as a channel's differ only there. Null when unusable.
String? normalizeRssFeedUrl(String raw) => switch (_addressParts(raw)) {
  (:final origin, :final path, :final query) => '$origin$path$query',
  null => null,
};

/// What two addresses of the same feed share: the stored form without trailing slashes on the path.
String rssFeedAddressKey(String url) => switch (_addressParts(url)) {
  (:final origin, :final path, :final query) => '$origin${path.replaceAll(_trailingSlashes, '')}$query',
  null => url.trim(),
};

/// The ids followed feeds hold, each with the address key of its feed.
Map<String, String> rssFeedIdsInUse(Iterable<RssFeed> feeds) => {
  for (final feed in feeds) feed.id: rssFeedAddressKey(feed.feedUrl),
};

/// An id for [feed] that no feed in [taken] holds. Feeds keep the usual id; one whose usual id is held by a different
/// address (the same path with another query) gets its full address, so both stay followed.
RssFeed allocateRssFeedIdentity(RssFeed feed, Map<String, String> taken) {
  final wanted = rssFeedId(feed.feedUrl);
  final holder = taken[wanted];
  final id = holder == null || holder == rssFeedAddressKey(feed.feedUrl)
      ? wanted
      : normalizeRssFeedUrl(feed.feedUrl) ?? feed.feedUrl.trim();
  if (id == feed.id) return feed;
  return RssFeed(
    id: id,
    feedUrl: feed.feedUrl,
    name: feed.name,
    siteUrl: feed.siteUrl,
    iconUrl: feed.iconUrl,
    description: feed.description,
  );
}

/// Tags as the tag editor keeps them: split at commas and slashes, trimmed, without repeats.
List<String> _tagsOf(Iterable<String> raw) {
  final tags = <String>{};
  for (final tag in raw.expand((part) => part.split(_tagSeparators))) {
    final trimmed = tag.trim();
    if (trimmed.isEmpty) continue;
    tags.add(trimmed.length > _maxTagLength ? trimmed.substring(0, _maxTagLength).trim() : trimmed);
    if (tags.length == rssOpmlMaxTags) break;
  }
  return List.unmodifiable(tags);
}

String? _outlineTitle(XmlElement outline) {
  final title = (outline.getAttribute('title') ?? outline.getAttribute('text') ?? '').trim();
  return title.isEmpty ? null : title;
}

XmlElement _body(String source) {
  if (source.length > rssOpmlMaxBytes) throw const RssOpmlException(RssOpmlProblem.tooLarge);
  final XmlDocument document;
  try {
    document = XmlDocument.parse(source.startsWith('﻿') ? source.substring(1) : source);
  } on XmlException {
    throw const RssOpmlException(RssOpmlProblem.malformed);
  }
  if (document.doctypeElement?.internalSubset != null) {
    throw const RssOpmlException(RssOpmlProblem.declaresEntities);
  }
  final root = document.rootElement;
  final body = root.name.local.toLowerCase() == 'opml' ? root.getElement('body') : null;
  return body ?? (throw const RssOpmlException(RssOpmlProblem.notOpml));
}

/// Reads an OPML subscription list, following nested folders. Entity declarations are refused outright; only the
/// predefined and numeric entities are decoded, and nothing outside the file is ever fetched.
RssOpmlDocument parseRssOpml(String source) {
  final body = _body(source);
  final feeds = <RssOpmlFeed>[];
  final indexByKey = <String, int>{};
  var duplicates = 0;
  var skipped = 0;
  var outlines = 0;

  void visit(XmlElement parent, int depth, String? folder) {
    if (depth > rssOpmlMaxDepth) throw const RssOpmlException(RssOpmlProblem.tooDeep);
    for (final outline in parent.childElements.where((element) => element.name.local == 'outline')) {
      if (++outlines > rssOpmlMaxOutlines) throw const RssOpmlException(RssOpmlProblem.tooLarge);
      final raw = outline.getAttribute('xmlUrl') ?? outline.getAttribute('xmlurl');
      if (raw == null) {
        visit(outline, depth + 1, _outlineTitle(outline) ?? folder);
        continue;
      }
      final url = normalizeRssFeedUrl(raw);
      final key = url == null ? null : rssFeedAddressKey(url);
      final tags = [?folder, ?outline.getAttribute('category')];
      if (url == null || key == null) {
        skipped++;
      } else if (indexByKey[key] case final index?) {
        duplicates++;
        feeds[index] = feeds[index].withTags(tags);
      } else {
        indexByKey[key] = feeds.length;
        final site = outline.getAttribute('htmlUrl') ?? outline.getAttribute('htmlurl');
        feeds.add(
          RssOpmlFeed(
            url: url,
            title: _outlineTitle(outline) ?? Uri.parse(url).host,
            siteUrl: site == null ? null : normalizeRssFeedUrl(site),
            tags: _tagsOf(tags),
          ),
        );
      }
    }
  }

  visit(body, 1, null);
  return RssOpmlDocument(feeds: feeds, duplicates: duplicates, skipped: skipped);
}

void _outline(XmlBuilder builder, RssFeed feed, List<String> tags) => builder.element(
  'outline',
  attributes: {
    'type': 'rss',
    'text': feed.name,
    'title': feed.name,
    'xmlUrl': feed.feedUrl,
    'htmlUrl': ?feed.siteUrl,
    if (tags.isNotEmpty) 'category': tags.map((tag) => '/$tag').join(','),
  },
);

/// Every followed feed as an OPML 2.0 file. A tagged feed sits in the folder of its first tag and lists all its tags
/// as categories, so importing the file again restores them. The XML writer escapes titles and addresses.
String exportRssOpml(
  Iterable<RssFeed> feeds, {
  Map<String, List<String>> tags = const {},
  String title = 'XTA',
  DateTime? now,
}) {
  final created = DateFormat("EEE, dd MMM yyyy HH:mm:ss 'GMT'", 'en_US').format((now ?? DateTime.now()).toUtc());
  final folders = <String, List<RssFeed>>{};
  final loose = <RssFeed>[];
  for (final feed in feeds) {
    if (tags[feed.id]?.firstOrNull case final first?) {
      (folders[first] ??= []).add(feed);
    } else {
      loose.add(feed);
    }
  }
  final builder = XmlBuilder()..processing('xml', 'version="1.0" encoding="UTF-8"');
  builder.element(
    'opml',
    attributes: {'version': '2.0'},
    nest: () {
      builder.element(
        'head',
        nest: () {
          builder.element('title', nest: title);
          builder.element('dateCreated', nest: created);
        },
      );
      builder.element(
        'body',
        nest: () {
          for (final MapEntry(key: folder, value: members) in folders.entries) {
            builder.element(
              'outline',
              attributes: {'text': folder, 'title': folder},
              nest: () {
                for (final feed in members) {
                  _outline(builder, feed, tags[feed.id] ?? const []);
                }
              },
            );
          }
          for (final feed in loose) {
            _outline(builder, feed, const []);
          }
        },
      );
    },
  );
  return builder.buildDocument().toXmlString(pretty: true, indent: '  ');
}
