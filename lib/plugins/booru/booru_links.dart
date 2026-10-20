import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_engines.dart';

/// One post on a booru XTA knows, as a link to its page names it.
typedef BooruPostRef = ({String host, BooruEngine engine, String id});

/// The built-in sites and those added in settings.
List<BooruPreset> booruKnownSites(BasePrefService prefs) => [
  ...booruPresets,
  ...parseBooruCustomSites(prefs.get<String>(optionPluginBooruCustomSites)),
];

/// The post whose page [uri] is on one of [sites], in the form each engine
/// links its posts (`BooruPost.hostPageUrl`); null for any other page.
BooruPostRef? booruPostRefOf(Uri uri, Iterable<BooruPreset> sites) =>
    sites.map((site) => _postRef(uri, site)).nonNulls.firstOrNull;

BooruPostRef? _postRef(Uri uri, BooruPreset site) {
  final path = _pathOn(uri, site.host);
  final id = path == null ? null : _postId(site.engine, path, uri.queryParameters);
  return id == null ? null : (host: site.host, engine: site.engine, id: id);
}

/// [uri]'s path below [host]'s own, when [uri] is on [host]'s pages.
List<String>? _pathOn(Uri uri, String host) {
  final base = Uri.tryParse(booruPageHost(host));
  if (base == null || _bare(base.host) != _bare(uri.host)) return null;
  final prefix = _segments(base);
  final path = _segments(uri);
  if (path.take(prefix.length).join('/') != prefix.join('/')) return null;
  return path.skip(prefix.length).toList();
}

String? _postId(BooruEngine engine, List<String> path, Map<String, String> query) {
  final id = switch ((engine, path)) {
    (BooruEngine.danbooru || BooruEngine.e621, ['posts', final id]) => id,
    (BooruEngine.moebooru, ['post', 'show', final id, ...]) => id,
    (BooruEngine.gelbooruV2, ['index.php']) when query['page'] == 'post' && query['s'] == 'view' => query['id'],
    _ => null,
  };
  return id != null && _digits.hasMatch(id) ? id : null;
}

final _digits = RegExp(r'^\d+$');

List<String> _segments(Uri uri) => uri.pathSegments.where((segment) => segment.isNotEmpty).toList();

String _bare(String host) => host.toLowerCase().replaceFirst(RegExp(r'^www\.'), '');
