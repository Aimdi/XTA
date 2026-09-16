/// Conservative identity for an article link, not a social post or home page.
String? canonicalArticleLink(String? value) {
  final uri = Uri.tryParse(value?.trim() ?? '');
  if (uri == null || !const ['https', 'http'].contains(uri.scheme) || uri.host.isEmpty || uri.userInfo.isNotEmpty) {
    return null;
  }
  final host = uri.host.toLowerCase();
  const social = {
    'x.com',
    'twitter.com',
    't.co',
    'bsky.app',
    'threads.net',
    'threads.com',
    'instagram.com',
    'facebook.com',
  };
  if (social.any((domain) => host == domain || host.endsWith('.$domain'))) return null;
  if (uri.path.isEmpty ||
      uri.path == '/' ||
      uri.pathSegments.any(const {'login', 'signin', 'oauth', 'auth'}.contains)) {
    return null;
  }
  if (uri.fragment.startsWith('/') || uri.fragment.startsWith('!')) return null;
  final query = <String, dynamic>{};
  for (final key in uri.queryParametersAll.keys.toList()..sort()) {
    if (key.toLowerCase().startsWith('utm_') || const {'fbclid', 'gclid', 'dclid', 'mc_cid', 'mc_eid'}.contains(key))
      continue;
    query[key] = uri.queryParametersAll[key];
  }
  return uri
      .replace(
        host: host,
        query: query.isEmpty ? '' : null,
        queryParameters: query.isEmpty ? null : query,
        fragment: '',
      )
      .toString()
      .replaceFirst(RegExp(r'[?#]+$'), '');
}

/// The first (newest) occurrence anchors the stack. Other posts remain intact.
Map<String, List<T>> repeatedLinks<T>(Iterable<T> items, String? Function(T) linkOf) {
  final groups = <String, List<T>>{};
  for (final item in items) {
    final link = canonicalArticleLink(linkOf(item));
    if (link != null) (groups[link] ??= []).add(item);
  }
  groups.removeWhere((_, group) => group.length < 2);
  return groups;
}
