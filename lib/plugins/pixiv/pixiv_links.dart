sealed class PixivLinkRef {
  final int id;

  const PixivLinkRef(this.id);

  const factory PixivLinkRef.artwork(int id) = PixivArtworkLinkRef;
  const factory PixivLinkRef.user(int id) = PixivUserLinkRef;
}

class PixivArtworkLinkRef extends PixivLinkRef {
  const PixivArtworkLinkRef(super.id);
}

class PixivUserLinkRef extends PixivLinkRef {
  const PixivUserLinkRef(super.id);
}

PixivLinkRef? parsePixivLink(String input) {
  final text = input.trim();
  if (text.isEmpty) {
    return null;
  }

  final numeric = int.tryParse(text);
  if (numeric != null) {
    return PixivLinkRef.artwork(numeric);
  }

  final queryIllust = _canTreatAsPixiv(text) ? _queryIllustId(text) : null;
  if (queryIllust != null) {
    return PixivLinkRef.artwork(queryIllust);
  }

  final path = _pathSegments(text);
  if (path.isEmpty) {
    return null;
  }

  final segments = path.first == 'en' ? path.skip(1).toList() : path;
  if (segments.length < 2) {
    return null;
  }

  final id = int.tryParse(segments[1]);
  if (id == null) {
    return null;
  }

  return switch (segments.first) {
    'artworks' || 'artwork' || 'illust' => PixivLinkRef.artwork(id),
    'users' || 'user' => PixivLinkRef.user(id),
    _ => null,
  };
}

int? _queryIllustId(String text) {
  final match = RegExp(r'(?:^|[?&])illust_id=(\d+)(?:$|[&#])').firstMatch(text);
  return match == null ? null : int.tryParse(match.group(1)!);
}

List<String> _pathSegments(String text) {
  final candidate = text.contains('://')
      ? text
      : _startsWithPixivHost(text)
      ? 'https://$text'
      : 'https://www.pixiv.net/$text';
  final uri = Uri.tryParse(candidate);
  if (uri == null || (uri.hasScheme && !_isPixivHost(uri.host))) {
    return const <String>[];
  }
  return uri.pathSegments
      .where((segment) => segment.isNotEmpty)
      .toList(growable: false);
}

bool _canTreatAsPixiv(String text) {
  final uri = Uri.tryParse(text);
  return uri == null || !uri.hasScheme || _isPixivHost(uri.host);
}

bool _isPixivHost(String host) {
  final normalized = host.toLowerCase();
  return normalized == 'pixiv.net' ||
      normalized.endsWith('.pixiv.net') ||
      normalized == 'pixiv.me' ||
      normalized.endsWith('.pixiv.me');
}

bool _startsWithPixivHost(String text) {
  final first = text.split('/').first;
  return _isPixivHost(first);
}
