/// Addresses on the stand-in for X that `tool/mock_x` serves.
///
/// The app sends `https://x.com/i/api/…` to `<server>/x.com/i/api/…`, and the
/// stand-in reads the X address back from that path. Plain Dart, so the tool
/// shares it without Flutter.
library;

const _xHosts = {'x.com', 'api.x.com', 'twitter.com', 'api.twitter.com'};

/// Where the stand-in at [server] serves [url], or null when [url] is not X's.
Uri? mockXUrl(Uri server, Uri url) {
  if (!_xHosts.contains(url.host)) return null;
  final base = server.toString().replaceFirst(RegExp(r'/+$'), '');
  return Uri.parse('$base/${url.host}${url.path}').replace(query: url.hasQuery ? url.query : null);
}

/// The X address a request to the stand-in stands for; the inverse of [mockXUrl].
Uri originalXUrl(Uri request) {
  final segments = request.pathSegments;
  if (segments.isEmpty) return request;
  return Uri.https(segments.first, segments.skip(1).join('/')).replace(query: request.hasQuery ? request.query : null);
}
