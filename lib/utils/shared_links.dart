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

/// Every web or Pixiv app link in [text], in order, trailing punctuation trimmed.
Iterable<Uri> _sharedLinks(String text) sync* {
  final urls = RegExp(r'''(?:https?|pixiv)://[^\s<>"\u200b]+''', caseSensitive: false);
  for (final match in urls.allMatches(text)) {
    final candidate = match.group(0)!.replaceFirst(RegExp(r'''[)\]}>.,!?;:'"]+$'''), '');
    final uri = Uri.tryParse(candidate);
    if (uri != null) yield uri;
  }
}

/// Every http(s) link in shared text, trailing punctuation removed, in order.
///
/// Shares often contain the post's caption before the actual link.
List<String> sharedTextUrls(String text) => [
  for (final uri in _sharedLinks(text))
    if (uri.scheme == 'https' || uri.scheme == 'http') uri.toString(),
];

bool _supported(Uri uri, {required bool pixiv}) {
  final web = uri.scheme == 'https' || uri.scheme == 'http';
  return (web || uri.scheme == 'pixiv') &&
      uri.userInfo.isEmpty &&
      !uri.hasPort &&
      ((web && _shareHosts.contains(uri.host.toLowerCase())) || (pixiv && _isPixivShare(uri)));
}

/// The first X link in a share — or Pixiv link, when [pixiv] is on. Shares
/// often carry the post's caption before the actual link.
Uri? extractSharedLink(String text, {bool pixiv = false}) =>
    _sharedLinks(text).where((uri) => _supported(uri, pixiv: pixiv)).firstOrNull;

bool _isWebLink(Uri uri) =>
    (uri.scheme == 'https' || uri.scheme == 'http') && uri.host.isNotEmpty && uri.userInfo.isEmpty;

/// X caps a search query at 500 characters.
const _maxSharedQuery = 500;

/// What a piece of text shared to the app from another one asks to open.
sealed class SharedTarget {
  const SharedTarget();
}

/// An X post, profile or short link — or, with the Pixiv plugin on, a Pixiv
/// link: opened through the app's own link handling.
final class SharedXTarget extends SharedTarget {
  final Uri link;
  const SharedXTarget(this.link);
}

/// Any other web page: a native screen when a plugin reads it, else the browser.
final class SharedWebTarget extends SharedTarget {
  final Uri link;
  const SharedWebTarget(this.link);
}

/// A bare number while the Pixiv plugin is on, which Pixiv reads as a work's ID.
final class SharedPixivIdTarget extends SharedTarget {
  final String id;
  const SharedPixivIdTarget(this.id);
}

/// Text without a link, searched for.
final class SharedSearchTarget extends SharedTarget {
  final String query;
  const SharedSearchTarget(this.query);
}

/// Nothing to open: an empty share.
final class SharedNothing extends SharedTarget {
  const SharedNothing();
}

/// Routes a share. An X link (or Pixiv link, when [pixiv] is on) wins over any
/// other link in the text, since a caption can quote a page the post is about;
/// a bare number is a Pixiv work while [pixiv] is on; a share with no link at
/// all is a search.
SharedTarget sharedTargetOf(String text, {bool pixiv = false}) {
  final appLink = extractSharedLink(text, pixiv: pixiv);
  if (appLink != null) return SharedXTarget(appLink);
  if (sharedPixivId(text) case final id? when pixiv) return SharedPixivIdTarget(id);
  final webLink = _sharedLinks(text).where(_isWebLink).firstOrNull;
  if (webLink != null) return SharedWebTarget(webLink);
  final query = text.trim().replaceAll(RegExp(r'\s+'), ' ');
  if (query.isEmpty) return const SharedNothing();
  return SharedSearchTarget(String.fromCharCodes(query.runes.take(_maxSharedQuery)).trim());
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
