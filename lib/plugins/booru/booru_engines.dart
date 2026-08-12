/// Which public API family a configured host speaks.
library;

import 'dart:convert';

enum BooruEngine {
  danbooru,
  moebooru,
  gelbooruV2,
  e621;

  String get id => switch (this) {
    BooruEngine.danbooru => 'danbooru',
    BooruEngine.moebooru => 'moebooru',
    BooruEngine.gelbooruV2 => 'gelbooru_v2',
    BooruEngine.e621 => 'e621',
  };

  static BooruEngine? tryParse(String? raw) {
    switch ((raw ?? '').trim().toLowerCase()) {
      case 'danbooru':
        return BooruEngine.danbooru;
      case 'moebooru':
        return BooruEngine.moebooru;
      case 'gelbooru':
      case 'gelbooru_v2':
      case 'gelbooru2':
        return BooruEngine.gelbooruV2;
      case 'e621':
      case 'e926':
        return BooruEngine.e621;
      default:
        return null;
    }
  }
}

class BooruPreset {
  final String id;
  final String name;
  final BooruEngine engine;
  final String host;

  const BooruPreset({
    required this.id,
    required this.name,
    required this.engine,
    required this.host,
  });
}

/// Built-in hosts — guest-friendly first. Gelbooru.com often needs an API key;
/// Safebooru stays usable without one. e926 is the safe mirror of e621.
/// Rule34 / Xbooru speak Gelbooru v2; their API lives on the same `index.php`
/// dapi as Safebooru.
const List<BooruPreset> booruPresets = [
  BooruPreset(
    id: 'danbooru',
    name: 'Danbooru',
    engine: BooruEngine.danbooru,
    host: 'https://danbooru.donmai.us',
  ),
  BooruPreset(
    id: 'yandere',
    name: 'Yande.re',
    engine: BooruEngine.moebooru,
    host: 'https://yande.re',
  ),
  BooruPreset(
    id: 'konachan',
    name: 'Konachan',
    engine: BooruEngine.moebooru,
    host: 'https://konachan.com',
  ),
  BooruPreset(
    id: 'safebooru',
    name: 'Safebooru',
    engine: BooruEngine.gelbooruV2,
    host: 'https://safebooru.org',
  ),
  BooruPreset(
    id: 'gelbooru',
    name: 'Gelbooru',
    engine: BooruEngine.gelbooruV2,
    host: 'https://gelbooru.com',
  ),
  BooruPreset(
    id: 'rule34',
    name: 'Rule34',
    engine: BooruEngine.gelbooruV2,
    host: 'https://rule34.xxx',
  ),
  BooruPreset(
    id: 'xbooru',
    name: 'Xbooru',
    engine: BooruEngine.gelbooruV2,
    host: 'https://xbooru.com',
  ),
  BooruPreset(
    id: 'e926',
    name: 'e926',
    engine: BooruEngine.e621,
    host: 'https://e926.net',
  ),
  BooruPreset(
    id: 'e621',
    name: 'e621',
    engine: BooruEngine.e621,
    host: 'https://e621.net',
  ),
];

String normaliseBooruHost(String raw) {
  var host = raw.trim();
  if (host.isEmpty) return '';
  if (!host.contains('://')) {
    host = 'https://$host';
  }
  final uri = Uri.tryParse(host);
  if (uri == null || uri.host.isEmpty) return '';
  final path = uri.path;
  final cleanedPath = path == '/' ? '' : path.replaceAll(RegExp(r'/+$'), '');
  return Uri(
    scheme: uri.scheme.isEmpty ? 'https' : uri.scheme,
    host: uri.host,
    port: uri.hasPort ? uri.port : null,
    path: cleanedPath,
  ).toString();
}

/// Host used for dapi / JSON. Rule34 serves the API on `api.rule34.xxx`.
String booruRequestHost(String host) {
  final normalised = normaliseBooruHost(host);
  final uri = Uri.tryParse(normalised);
  if (uri == null) return normalised;
  final name = uri.host.toLowerCase();
  if (name == 'rule34.xxx' || name == 'www.rule34.xxx') {
    return uri.replace(host: 'api.rule34.xxx').toString();
  }
  return normalised;
}

/// Host used for "open on site" links. `api.rule34.xxx` is not a page host.
String booruPageHost(String host) {
  final normalised = normaliseBooruHost(host);
  final uri = Uri.tryParse(normalised);
  if (uri == null) return normalised;
  if (uri.host.toLowerCase() == 'api.rule34.xxx') {
    return uri.replace(host: 'rule34.xxx').toString();
  }
  return normalised;
}

/// Best-effort engine from a hostname. Unknown Gelbooru-style clones default
/// to v2 dapi — that is what Rule34 forks speak. Paheal / Sankaku are not.
BooruEngine? guessBooruEngine(String host) {
  final h = Uri.tryParse(normaliseBooruHost(host))?.host.toLowerCase() ?? '';
  if (h.isEmpty) return null;
  if (h.contains('paheal') || h.contains('sankaku')) return null;
  if (h.contains('e621') || h.contains('e926')) return BooruEngine.e621;
  if (h.contains('yande.re') ||
      h.contains('konachan') ||
      h.contains('hypnohub')) {
    return BooruEngine.moebooru;
  }
  if (h.contains('donmai.us') ||
      h.contains('danbooru') ||
      h.contains('aibooru')) {
    return BooruEngine.danbooru;
  }
  return BooruEngine.gelbooruV2;
}

String customBooruSiteId(String host) {
  final uri = Uri.tryParse(normaliseBooruHost(host));
  final name = uri?.host ?? '';
  return name.isEmpty ? '' : 'custom:$name';
}

String displayNameForBooruHost(String host) {
  return Uri.tryParse(normaliseBooruHost(host))?.host ?? host;
}

List<BooruPreset> parseBooruCustomSites(String? raw) {
  if (raw == null || raw.trim().isEmpty) return const [];
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! List) return const [];
    return [
      for (final item in decoded)
        if (item is Map) ?_customSiteFromMap(item),
    ];
  } catch (_) {
    return const [];
  }
}

BooruPreset? _customSiteFromMap(Map<dynamic, dynamic> item) {
  final host = normaliseBooruHost('${item['host'] ?? ''}');
  final engine = BooruEngine.tryParse('${item['engine'] ?? ''}');
  if (host.isEmpty || engine == null) return null;
  final name = '${item['name'] ?? ''}'.trim();
  final id = '${item['id'] ?? ''}'.trim();
  return BooruPreset(
    id: id.isEmpty ? customBooruSiteId(host) : id,
    name: name.isEmpty ? displayNameForBooruHost(host) : name,
    engine: engine,
    host: host,
  );
}

String encodeBooruCustomSites(List<BooruPreset> sites) => jsonEncode([
  for (final site in sites)
    {
      'id': site.id,
      'name': site.name,
      'engine': site.engine.id,
      'host': site.host,
    },
]);

List<BooruPreset> upsertBooruCustomSite(
  List<BooruPreset> sites,
  BooruPreset site,
) {
  final id = site.id.isEmpty ? customBooruSiteId(site.host) : site.id;
  final next = BooruPreset(
    id: id,
    name: site.name,
    engine: site.engine,
    host: site.host,
  );
  return [
    for (final existing in sites)
      if (existing.id != next.id && existing.host != next.host) existing,
    next,
  ];
}

String? normaliseBooruTag(String raw) {
  final tag = raw.trim().toLowerCase().replaceAll(RegExp(r'\s+'), '_');
  if (tag.isEmpty) return null;
  return tag;
}

/// Last space-separated token in a search query, for "follow this tag".
String? lastBooruTagToken(String query) {
  final parts = query
      .trim()
      .split(RegExp(r'\s+'))
      .where((t) => t.isNotEmpty)
      .toList(growable: false);
  if (parts.isEmpty) return null;
  final last = parts.last;
  if (last.contains(':')) return null; // metatag, not a followable tag
  return normaliseBooruTag(last);
}
