/// Something a Pixiv link or a bare ID names.
sealed class PixivLinkRef {
  const PixivLinkRef();

  const factory PixivLinkRef.artwork(int id) = PixivArtworkLinkRef;
  const factory PixivLinkRef.user(int id) = PixivUserLinkRef;
}

class PixivArtworkLinkRef extends PixivLinkRef {
  final int id;

  const PixivArtworkLinkRef(this.id);
}

class PixivUserLinkRef extends PixivLinkRef {
  final int id;

  const PixivUserLinkRef(this.id);
}

/// A tag page, which XTA opens as a search for the tag.
class PixivTagLinkRef extends PixivLinkRef {
  final String tag;

  const PixivTagLinkRef(this.tag);
}

/// A `pixiv.me/<name>` short link; only a request to pixiv.me says where it goes.
class PixivShortLinkRef extends PixivLinkRef {
  final String name;

  const PixivShortLinkRef(this.name);

  Uri get uri => Uri.https('pixiv.me', '/$name');
}

/// A page that also lives on the web: it knows the address to share or hand
/// the browser, which is where the ones without a screen in XTA open.
sealed class PixivWebPageLinkRef extends PixivLinkRef {
  const PixivWebPageLinkRef();

  String get webUrl;
}

/// An illustration or manga series.
class PixivSeriesLinkRef extends PixivWebPageLinkRef {
  final int id;
  final int userId;

  const PixivSeriesLinkRef(this.id, {required this.userId});

  @override
  String get webUrl => 'https://www.pixiv.net/user/$userId/series/$id';
}

class PixivNovelLinkRef extends PixivWebPageLinkRef {
  final int id;

  const PixivNovelLinkRef(this.id);

  @override
  String get webUrl => 'https://www.pixiv.net/novel/show.php?id=$id';
}

class PixivNovelSeriesLinkRef extends PixivWebPageLinkRef {
  final int id;

  const PixivNovelSeriesLinkRef(this.id);

  @override
  String get webUrl => 'https://www.pixiv.net/novel/series/$id';
}

/// A pixivision article, in the language its link was written for.
class PixivisionLinkRef extends PixivWebPageLinkRef {
  final int id;
  final String language;

  const PixivisionLinkRef(this.id, {this.language = 'en'});

  @override
  String get webUrl => 'https://www.pixivision.net/$language/a/$id';
}

/// What [input] names: a bare number is a work; a link can be any of
/// [PixivLinkRef]'s kinds on pixiv.net, pixiv.me, pixivision.net, an
/// i.pximg.net image file or the `pixiv://` app scheme. Null for anything else.
PixivLinkRef? parsePixivLink(String input) {
  final text = input.trim();
  if (text.isEmpty) {
    return null;
  }
  final numeric = int.tryParse(text);
  if (numeric != null) {
    return PixivLinkRef.artwork(numeric);
  }
  try {
    final uri = _linkUri(text);
    return uri == null ? null : _refOf(uri);
  } on FormatException {
    return null;
  } on ArgumentError {
    return null;
  }
}

PixivLinkRef? _refOf(Uri uri) {
  if (uri.scheme == 'pixiv') {
    return _appSchemeRef(uri);
  }
  final host = uri.host.toLowerCase();
  if (_isImageHost(host)) {
    return _imageFileRef(_segments(uri));
  }
  if (_isPixivisionHost(host)) {
    return _pixivisionRef(_segments(uri));
  }
  if (!_isPixivHost(host)) {
    return null;
  }
  return _queryRef(uri) ?? (host == 'pixiv.me' ? _shortRef(_segments(uri)) : _pathRef(_segments(uri)));
}

/// A link as typed or pasted: a full URL, a host without a scheme, or a path on pixiv.net.
Uri? _linkUri(String text) {
  if (text.contains('://')) {
    return Uri.tryParse(text);
  }
  final first = text.split('/').first.toLowerCase();
  if (_isPixivHost(first) || _isImageHost(first) || _isPixivisionHost(first)) {
    return Uri.tryParse('https://$text');
  }
  return Uri.tryParse('https://www.pixiv.net/$text');
}

List<String> _segments(Uri uri) => uri.pathSegments.where((segment) => segment.isNotEmpty).toList(growable: false);

int? _positiveId(String? text) => switch (int.tryParse(text?.trim() ?? '')) {
  final id? when id > 0 => id,
  _ => null,
};

/// `pixiv://illusts/<id>`, `pixiv://users/<id>` and `pixiv://novels/<id>`.
PixivLinkRef? _appSchemeRef(Uri uri) {
  final segments = _segments(uri);
  final id = segments.isEmpty ? null : _positiveId(segments.last);
  if (id == null) {
    return null;
  }
  return switch (uri.host.toLowerCase()) {
    'illusts' || 'illust' => PixivLinkRef.artwork(id),
    'users' || 'user' => PixivLinkRef.user(id),
    'novels' || 'novel' => PixivNovelLinkRef(id),
    _ => null,
  };
}

const _imageFolders = {'img-original', 'img-master', 'custom-thumb', 'img-zip-ugoira'};

/// An image file such as `.../img-original/img/2026/07/01/00/00/00/123_p0.png`
/// carries its work's ID before the first underscore.
PixivLinkRef? _imageFileRef(List<String> segments) {
  if (segments.isEmpty || !segments.any(_imageFolders.contains)) {
    return null;
  }
  final id = _positiveId(RegExp(r'^(\d+)_').firstMatch(segments.last)?.group(1));
  return id == null ? null : PixivLinkRef.artwork(id);
}

/// `pixivision.net/<language>/a/<id>`.
PixivLinkRef? _pixivisionRef(List<String> segments) {
  final at = segments.indexOf('a');
  final id = at < 0 || at + 1 >= segments.length ? null : _positiveId(segments[at + 1]);
  if (id == null) {
    return null;
  }
  return at > 0 ? PixivisionLinkRef(id, language: segments[at - 1]) : PixivisionLinkRef(id);
}

/// The old PHP pages: `member_illust.php?illust_id=`, `member.php?id=` and
/// `novel/show.php?id=`.
PixivLinkRef? _queryRef(Uri uri) {
  final illust = _positiveId(uri.queryParameters['illust_id']);
  if (illust != null) {
    return PixivLinkRef.artwork(illust);
  }
  final id = _positiveId(uri.queryParameters['id']);
  if (id == null) {
    return null;
  }
  return switch (_segments(uri).lastOrNull) {
    'member.php' || 'member_illust.php' => PixivLinkRef.user(id),
    'show.php' when uri.path.contains('novel') => PixivNovelLinkRef(id),
    _ => null,
  };
}

PixivLinkRef? _shortRef(List<String> segments) {
  if (segments.length != 1 || segments.single.endsWith('.php')) {
    return null;
  }
  return PixivShortLinkRef(segments.single);
}

PixivLinkRef? _pathRef(List<String> path) {
  final segments = path.isNotEmpty && path.first == 'en' ? path.sublist(1) : path;
  if (segments.length < 2) {
    return null;
  }
  return _namedPathRef(segments) ?? _idPathRef(segments.first, _positiveId(segments[1]));
}

/// Paths whose second segment is not the ID: tags, series and novel series.
PixivLinkRef? _namedPathRef(List<String> segments) {
  final head = segments.first;
  if (head == 'tags') {
    final tag = segments[1].trim();
    return tag.isEmpty ? null : PixivTagLinkRef(tag);
  }
  if (head == 'novel' && segments[1] == 'series' && segments.length > 2) {
    final id = _positiveId(segments[2]);
    return id == null ? null : PixivNovelSeriesLinkRef(id);
  }
  if ((head == 'user' || head == 'users') && segments.length > 3 && segments[2] == 'series') {
    final (userId, id) = (_positiveId(segments[1]), _positiveId(segments[3]));
    return userId == null || id == null ? null : PixivSeriesLinkRef(id, userId: userId);
  }
  return null;
}

PixivLinkRef? _idPathRef(String head, int? id) {
  if (id == null) {
    return null;
  }
  return switch (head) {
    'artworks' || 'artwork' || 'illust' || 'i' => PixivLinkRef.artwork(id),
    'users' || 'user' || 'u' => PixivLinkRef.user(id),
    'n' => PixivNovelLinkRef(id),
    _ => null,
  };
}

/// pixiv.net and pixiv.me, with any subdomain.
bool isPixivWebHost(String host) => _isPixivHost(host.toLowerCase());

/// The image CDN, whose file names start with the work's ID.
bool isPixivImageHost(String host) => _isImageHost(host.toLowerCase());

/// pixivision.net, Pixiv's magazine, with any subdomain.
bool isPixivisionHost(String host) => _isPixivisionHost(host.toLowerCase());

bool _isPixivHost(String host) =>
    host == 'pixiv.net' || host.endsWith('.pixiv.net') || host == 'pixiv.me' || host.endsWith('.pixiv.me');

bool _isImageHost(String host) => host == 'pximg.net' || host.endsWith('.pximg.net');

bool _isPixivisionHost(String host) => host == 'pixivision.net' || host.endsWith('.pixivision.net');
