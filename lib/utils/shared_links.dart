import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;
import 'package:xta/plugins/pixiv/pixiv_links.dart' show isPixivImageHost, isPixivWebHost, isPixivisionHost;

const sharedTextChannel = EventChannel('com.aimdi.xta/shared_text');
const _shareHosts = {
  'x.com',
  'www.x.com',
  'mobile.x.com',
  'twitter.com',
  'www.twitter.com',
  'mobile.twitter.com',
  't.co',
  'fxtwitter.com',
  'www.fxtwitter.com',
  'vxtwitter.com',
  'www.vxtwitter.com',
  'fixupx.com',
  'www.fixupx.com',
};

/// Pixiv's pages, its image files (whose names carry the work's ID) and its
/// app links; taken only while the Pixiv plugin is on, as nothing else in XTA
/// reads them.
bool _isPixivShare(Uri uri) {
  if (uri.scheme == 'pixiv') return true;
  final host = uri.host.toLowerCase();
  return isPixivWebHost(host) || isPixivisionHost(host) || isPixivImageHost(host);
}

bool _supported(Uri uri, {required bool pixiv}) {
  final web = uri.scheme == 'https' || uri.scheme == 'http';
  return (web || uri.scheme == 'pixiv') &&
      uri.userInfo.isEmpty &&
      !uri.hasPort &&
      ((web && _shareHosts.contains(uri.host.toLowerCase())) || (pixiv && _isPixivShare(uri)));
}

/// The first X link in a share — or Pixiv link, when [pixiv] is on. Shares
/// often carry the post's caption before the actual link.
Uri? extractSharedLink(String text, {bool pixiv = false}) {
  final urls = RegExp(r'''(?:https?|pixiv)://[^\s<>"\u200b]+''', caseSensitive: false);
  for (final match in urls.allMatches(text)) {
    final candidate = match.group(0)!.replaceFirst(RegExp(r'''[)\]}>.,!?;:'"]+$'''), '');
    final uri = Uri.tryParse(candidate);
    if (uri != null && _supported(uri, pixiv: pixiv)) return uri;
  }
  return null;
}

/// Whether X's link parser should read [link], which no plugin opened. A Pixiv
/// or pixivision page, image file or app link should not: X would take
/// `pixiv.net/en/` for the profile `@en`, so it goes to the browser instead.
bool readsAsXLink(Uri link) => !_isPixivShare(link);

/// A share that is only a number, which Pixiv reads as a work's ID.
String? sharedPixivId(String text) {
  final trimmed = text.trim();
  return RegExp(r'^\d{1,12}$').hasMatch(trimmed) ? trimmed : null;
}

/// Where [uri] redirects, from one request that does not follow it; null when
/// the answer is not a redirect.
Future<Uri?> redirectLocation(http.Client transport, Uri uri) async {
  final request = http.Request('GET', uri)..followRedirects = false;
  final response = await transport.send(request).timeout(const Duration(seconds: 8));
  await response.stream.listen((_) {}).cancel();
  final location = response.headers['location'];
  if (response.statusCode < 300 || response.statusCode >= 400 || location == null) return null;
  return uri.resolve(location);
}

/// [extractSharedLink], with X short links resolved, without following
/// redirects to arbitrary hosts. Pixiv short links are resolved where they open.
Future<Uri?> resolveSharedLink(String text, {bool pixiv = false, http.Client? client}) async {
  var uri = extractSharedLink(text, pixiv: pixiv);
  if (uri == null || uri.host != 't.co') return uri;
  final transport = client ?? http.Client();
  try {
    for (var hop = 0; hop < 3 && uri != null && uri.host == 't.co'; hop++) {
      uri = await redirectLocation(transport, uri);
      if (uri == null || !_supported(uri, pixiv: false)) return null;
    }
    return uri?.host == 't.co' ? null : uri;
  } finally {
    if (client == null) transport.close();
  }
}
