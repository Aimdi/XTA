import 'package:flutter/services.dart';
import 'package:http/http.dart' as http;

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

bool _supported(Uri uri) =>
    (uri.scheme == 'https' || uri.scheme == 'http') &&
    uri.userInfo.isEmpty &&
    !uri.hasPort &&
    _shareHosts.contains(uri.host.toLowerCase());

/// Shares often contain the post's caption before the actual link.
Uri? extractSharedXLink(String text) {
  final urls = RegExp(r'''https?://[^\s<>"\u200b]+''', caseSensitive: false);
  for (final match in urls.allMatches(text)) {
    final candidate = match.group(0)!.replaceFirst(RegExp(r'''[)\]}>.,!?;:'"]+$'''), '');
    final uri = Uri.tryParse(candidate);
    if (uri != null && _supported(uri)) return uri;
  }
  return null;
}

/// Resolve only X short links, without following redirects to arbitrary hosts.
Future<Uri?> resolveSharedXLink(String text, {http.Client? client}) async {
  var uri = extractSharedXLink(text);
  if (uri == null || uri.host != 't.co') return uri;
  final transport = client ?? http.Client();
  try {
    for (var hop = 0; hop < 3 && uri != null && uri.host == 't.co'; hop++) {
      final request = http.Request('GET', uri)..followRedirects = false;
      final response = await transport.send(request).timeout(const Duration(seconds: 8));
      await response.stream.listen((_) {}).cancel();
      final location = response.headers['location'];
      if (response.statusCode < 300 || response.statusCode >= 400 || location == null) return null;
      uri = uri.resolve(location);
      if (!_supported(uri)) return null;
    }
    return uri?.host == 't.co' ? null : uri;
  } finally {
    if (client == null) transport.close();
  }
}
