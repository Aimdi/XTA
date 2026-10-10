import 'package:pref/pref.dart';
import 'package:xta/constants.dart';

/// Pixiv's own image server, where every artwork URL points.
const pixivImageHost = 'i.pximg.net';

/// The public reverse proxy for [pixivImageHost], for readers who cannot reach Pixiv's CDN.
const pixivMirrorHost = 'i.pixiv.re';

enum PixivImageHostChoice { pixiv, mirror, custom }

PixivImageHostChoice pixivImageHostChoice(String host) => switch (host.trim()) {
  '' || pixivImageHost => PixivImageHostChoice.pixiv,
  pixivMirrorHost => PixivImageHostChoice.mirror,
  _ => PixivImageHostChoice.custom,
};

/// The server a host setting names, with its port and path prefix; null when it is
/// unusable: blank, containing spaces, not https, or carrying a query or credentials.
/// Plain http would carry every work's address and Pixiv's Referer in the clear,
/// and Android refuses it anyway.
Uri? parsePixivImageHost(String input) {
  final text = input.trim();
  if (text.isEmpty || text.contains(RegExp(r'\s'))) return null;
  final uri = Uri.tryParse(text.contains('://') ? text : 'https://$text');
  if (uri == null || uri.host.isEmpty || uri.scheme != 'https') return null;
  if (uri.hasQuery || uri.hasFragment || uri.userInfo.isNotEmpty) return null;
  return uri;
}

/// [url] as served by [host] instead of Pixiv. Only `i.pximg.net` moves: the static
/// `s.pximg.net`, every other URL, and everything when [host] is Pixiv's or unusable stay as they are.
String pixivImageUrl(String url, String host) {
  final server = pixivImageHostChoice(host) == PixivImageHostChoice.pixiv ? null : parsePixivImageHost(host);
  final uri = server == null ? null : Uri.tryParse(url);
  if (server == null || uri == null || uri.host != pixivImageHost) return url;
  final prefix = server.path.endsWith('/') ? server.path.substring(0, server.path.length - 1) : server.path;
  return Uri(
    scheme: server.scheme,
    host: server.host,
    port: server.hasPort ? server.port : null,
    path: '$prefix${uri.path}',
    query: uri.hasQuery ? uri.query : null,
  ).toString();
}

/// The image server the reader picked; Pixiv's own until they pick another.
String pixivImageHostSetting(BasePrefService? prefs) =>
    prefs?.get<String>(optionPluginPixivImageHost) ?? pixivImageHost;
