/// Which public API family a configured host speaks.
library;

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
