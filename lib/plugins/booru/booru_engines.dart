/// Which public API family a configured host speaks.
library;

enum BooruEngine {
  danbooru,
  moebooru,
  gelbooruV2;

  String get id => switch (this) {
    BooruEngine.danbooru => 'danbooru',
    BooruEngine.moebooru => 'moebooru',
    BooruEngine.gelbooruV2 => 'gelbooru_v2',
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
/// Safebooru stays usable without one.
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
