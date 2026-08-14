/// Reads one or more X usernames from a paste: `@name`, bare names, or
/// `x.com/name` / `twitter.com/name` links, separated by spaces or newlines.
List<String> parseImportHandles(String raw) {
  final seen = <String>{};
  final handles = <String>[];
  for (final token in raw.split(RegExp(r'[\s,;]+'))) {
    final handle = importHandleFromToken(token);
    if (handle == null) continue;
    if (seen.add(handle.toLowerCase())) {
      handles.add(handle);
    }
  }
  return handles;
}

String? importHandleFromToken(String raw) {
  final token = raw.trim();
  if (token.isEmpty) return null;
  final fromUrl = _handleFromProfileUrl(token);
  if (fromUrl != null) return fromUrl;
  final match = RegExp(r'^@?([A-Za-z0-9_]{1,15})$').firstMatch(token);
  return match?.group(1);
}

String? _handleFromProfileUrl(String raw) {
  final uri = Uri.tryParse(raw.contains('://') ? raw : 'https://$raw');
  if (uri == null) return null;
  final host = uri.host.toLowerCase();
  if (host != 'x.com' &&
      host != 'www.x.com' &&
      host != 'twitter.com' &&
      host != 'www.twitter.com') {
    return null;
  }
  final segs = uri.pathSegments.where((s) => s.isNotEmpty).toList();
  if (segs.isEmpty) return null;
  final first = segs.first.replaceAll('@', '');
  if (!_isHandle(first)) return null;
  return first;
}

const _reservedPaths = {
  'i',
  'home',
  'search',
  'explore',
  'settings',
  'intent',
  'compose',
  'messages',
  'notifications',
  'login',
  'signup',
  'hashtag',
  'share',
  'tos',
  'privacy',
};

bool _isHandle(String value) =>
    RegExp(r'^[A-Za-z0-9_]{1,15}$').hasMatch(value) &&
    !_reservedPaths.contains(value.toLowerCase());
