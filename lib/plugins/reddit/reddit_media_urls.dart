/// Deciding what a Reddit URL points at.
///
/// A post and a comment ask the same question — is this a picture I can show,
/// or a page I can only link to — so they ask it in one place. Reddit gives no
/// content type in the markup, and following the link to find out would mean a
/// request per post, so the answer comes from the host and the extension.
library;

/// Hosts that serve the picture itself, whatever the path looks like.
const redditImageHosts = {
  'i.redd.it',
  'preview.redd.it',
  'i.imgur.com',
  'i.redditmedia.com',
};

/// Hosts whose links are a video. Reddit's own is a DASH manifest rather than a
/// file, so none of these can be shown inline — but knowing it is a video is
/// what lets a card say so instead of offering a dead thumbnail.
const redditVideoHosts = {
  'v.redd.it',
  'youtube.com',
  'youtu.be',
  'm.youtube.com',
  'streamable.com',
  'gfycat.com',
  'redgifs.com',
};

const _imageExtensions = ['.jpg', '.jpeg', '.png', '.webp', '.gif'];

/// [url] if it is a picture that can be shown inline, otherwise null.
///
/// Imgur's `.gifv` is rewritten to `.gif`: the name looks like an image but the
/// page behind it is a video player, and the `.gif` beside it is the animation
/// itself.
String? redditImageUrl(String? url) {
  final uri = url == null ? null : Uri.tryParse(url);
  if (url == null ||
      uri == null ||
      !uri.hasScheme ||
      !uri.scheme.startsWith('http')) {
    return null;
  }

  final path = uri.path.toLowerCase();
  if (path.endsWith('.gifv')) {
    // Only a plain `…/x.gifv`; with a query on the end the rewrite would have
    // to guess where the extension stopped, and a guess here loads nothing.
    return url.toLowerCase().endsWith('.gifv')
        ? url.substring(0, url.length - 1)
        : null;
  }

  final host = _bareHost(uri.host);
  return redditImageHosts.contains(host) || _imageExtensions.any(path.endsWith)
      ? url
      : null;
}

/// Whether a host serves video rather than anything showable.
bool isRedditVideoHost(String? host) =>
    host != null && redditVideoHosts.contains(_bareHost(host));

/// A picture for [url], rewriting the hosts whose page can be turned into a
/// file without asking them.
///
/// [redditImageUrl] answers "is this already a picture". This answers the
/// larger question a comment actually poses, where half the pictures people
/// post are links to a viewer page that has a predictable file behind it.
/// Anything needing a request or a key to resolve is left alone — a guess that
/// loads nothing is worse than a link.
String? redditEmbeddableImage(String? url) {
  final direct = redditImageUrl(url);
  if (direct != null) {
    return direct;
  }

  final uri = url == null ? null : Uri.tryParse(url);
  if (uri == null || !uri.scheme.startsWith('http')) {
    return null;
  }

  return switch (_bareHost(uri.host)) {
    'giphy.com' || 'media.giphy.com' || 'i.giphy.com' => _giphyFromPath(uri),
    'imgur.com' || 'm.imgur.com' => _imgurFromPath(uri),
    _ => null,
  };
}

/// Giphy's file for a share link.
///
/// `giphy.com/gifs/some-slug-l0HlvtIPzPdt2usKs` — the id is the last piece of
/// the slug, and `media.giphy.com/media/<id>/giphy.gif` serves it with no key.
String? _giphyFromPath(Uri uri) {
  final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();
  final index = segments.indexOf('gifs');
  if (index == -1 || index + 1 >= segments.length) {
    return null;
  }

  final slug = segments[index + 1];
  final id = slug.contains('-') ? slug.split('-').last : slug;

  return _giphyId.hasMatch(id) ? giphyFileFor(id) : null;
}

/// The single image behind an imgur page. An album or a gallery holds several
/// and needs the API to say which, so those are left as links.
String? _imgurFromPath(Uri uri) {
  final segments = uri.pathSegments.where((s) => s.isNotEmpty).toList();
  if (segments.length != 1 ||
      const {'a', 'gallery', 't'}.contains(segments.first)) {
    return null;
  }

  return _imgurId.hasMatch(segments.first)
      ? 'https://i.imgur.com/${segments.first}.jpeg'
      : null;
}

/// The file for a Giphy id, which is what Reddit's own `giphy|id|size` token
/// carries instead of a URL.
String giphyFileFor(String id) => 'https://media.giphy.com/media/$id/giphy.gif';

final _giphyId = RegExp(r'^[A-Za-z0-9]{6,}$');
final _imgurId = RegExp(r'^[A-Za-z0-9]{5,10}$');

/// Reddit's own media markdown, which the old site does not render: a comment
/// posted with the GIF picker arrives as the literal text
/// `![gif](giphy|l0HlvtIPzPdt2usKs|downsized)`.
///
/// It is the only trace of the picture on the page, so it is read rather than
/// shown to the reader as the raw token it is.
final redditMediaToken = RegExp(
  r'!\[[a-z]*\]\((giphy|emote)\|([A-Za-z0-9_]+)(?:\|[^)]*)?\)',
);

/// The picture a media token names, or null for the kinds that name no file.
String? redditTokenImage(RegExpMatch match) {
  final kind = match.group(1);
  final id = match.group(2);
  if (id == null) {
    return null;
  }

  // `emote|free_emotes_pack|…` is a sticker set Reddit resolves internally;
  // there is no public file behind the name.
  return kind == 'giphy' ? giphyFileFor(id) : null;
}

String _bareHost(String host) {
  final lower = host.toLowerCase();
  return lower.startsWith('www.') ? lower.substring(4) : lower;
}

/// Collapses Reddit image URLs that are the same asset under different hosts
/// or widths into one URL each.
///
/// Identity is the filename (last path segment), not the full URL — so
/// `preview.redd.it/x.jpg?width=320` and `i.redd.it/x.jpg` are one image.
/// When collapsing, prefer `i.redd.it` over `preview.redd.it`, else the
/// largest `width` query param, else the first seen. Order of first
/// occurrence of each unique image is preserved; different filenames stay
/// separate (real galleries).
List<String> collapseRedditImageUrls(Iterable<String> urls) {
  final result = <String>[];
  final indexByKey = <String, int>{};

  for (final url in urls) {
    final key = _redditImageKey(url);
    if (key == null) {
      result.add(url);
      continue;
    }

    final existing = indexByKey[key];
    if (existing == null) {
      indexByKey[key] = result.length;
      result.add(url);
      continue;
    }

    if (_preferRedditImage(url, result[existing])) {
      result[existing] = url;
    }
  }

  return result;
}

/// Filename identity for a Reddit image URL, or null when the URL has no path.
String? _redditImageKey(String url) {
  final uri = Uri.tryParse(url);
  if (uri == null) {
    return null;
  }

  final segments = uri.pathSegments.where((s) => s.isNotEmpty);
  return segments.isEmpty ? null : segments.last.toLowerCase();
}

/// Whether [candidate] is a better representation of the same image than
/// [current]: `i.redd.it` wins over `preview.redd.it`, else larger `width`.
bool _preferRedditImage(String candidate, String current) {
  final candidateUri = Uri.tryParse(candidate);
  final currentUri = Uri.tryParse(current);
  if (candidateUri == null || currentUri == null) {
    return false;
  }

  final candidateIsDirect = _bareHost(candidateUri.host) == 'i.redd.it';
  final currentIsDirect = _bareHost(currentUri.host) == 'i.redd.it';
  if (candidateIsDirect != currentIsDirect) {
    return candidateIsDirect;
  }

  final candidateWidth =
      int.tryParse(candidateUri.queryParameters['width'] ?? '') ?? -1;
  final currentWidth =
      int.tryParse(currentUri.queryParameters['width'] ?? '') ?? -1;
  return candidateWidth > currentWidth;
}

/// Words meme titles use when the joke is the picture, not the caption.
const _redditMediaPlaceholderWord =
    r'(?:image|img|pic|picture|photo|gif|video|media|foto|bild)';

/// Brackets people wrap that word in: `<image>`, `>image>`, `[gif]`.
final _redditMediaWrappers = RegExp(r'''[\[\]\(\)\{\}<>＜＞｛｝【】「」『』"'`]+''');

final _redditMediaPlaceholderBare = RegExp(
  '^$_redditMediaPlaceholderWord\$',
  caseSensitive: false,
);

/// A wrapped placeholder sitting in a longer body (`>image> nice one`).
final _redditMediaPlaceholderToken = RegExp(
  '(?:&lt;|&gt;|[<>\\[\\]\\(\\)\\{\\}｛｝【】「」『』])+\\s*'
  '$_redditMediaPlaceholderWord'
  '\\s*(?:&lt;|&gt;|[<>\\[\\]\\(\\)\\{\\}｛｝【】「」『』])+',
  caseSensitive: false,
);

String _decodeRedditPlaceholderEntities(String text) =>
    text.replaceAll('&lt;', '<').replaceAll('&gt;', '>');

/// Titles that only announce media the card already shows as a picture.
///
/// Meme subs title image posts `<image>`, `>image>` or `[image]` because the
/// joke is the picture — showing that label above every comic is just noise.
bool isRedditMediaPlaceholderTitle(String title) {
  var text = _decodeRedditPlaceholderEntities(title).trim();
  if (text.isEmpty) {
    return false;
  }
  text = text.replaceAll(_redditMediaWrappers, '').trim();
  text = text.replaceAll(RegExp(r'[.!?,;:]+$'), '');
  return _redditMediaPlaceholderBare.hasMatch(text);
}

/// Drops leftover `>image>` / `<gif>` tokens from a comment or selftext once
/// the picture is already on the card.
String stripRedditMediaPlaceholderTokens(String text) {
  return _decodeRedditPlaceholderEntities(text)
      .replaceAll(_redditMediaPlaceholderToken, ' ')
      .replaceAll(RegExp(r'\s+'), ' ')
      .trim();
}

final _redditHttpUrl = RegExp(r'https?://[^\s\]\)<>]+', caseSensitive: false);

/// Drops preview.redd.it / i.redd.it (and other image) URLs from title or
/// body text once the card already shows that picture.
///
/// Meme titles often end with the full CDN URL; printing it above the photo
/// is the URL a second time.
String stripRedditMediaLinksFromText(String text) {
  var out = text.replaceAllMapped(_redditHttpUrl, (match) {
    final url = match.group(0)!;
    return redditImageUrl(url) != null || redditEmbeddableImage(url) != null
        ? ''
        : url;
  });
  out = stripRedditMediaPlaceholderTokens(out);
  out = out.replaceAll(RegExp(r' {2,}'), ' ').trim();
  out = out.replaceAll(RegExp(r'[\s:–—\-]+$'), '').trim();
  return out;
}

/// Playable DASH manifest for a `v.redd.it/...` link when the listing omitted
/// `secure_media` (old.reddit HTML scrape never carries it).
///
/// Reddit serves every native video at `{id}/DASHPlaylist.mpd`; JSON listings
/// already put that URL on the post, and the HTML path has to reconstruct it.
String? redditVRedditDashUrl(String? url) {
  final uri = url == null ? null : Uri.tryParse(url);
  if (uri == null || _bareHost(uri.host) != 'v.redd.it') {
    return null;
  }

  final segments = uri.pathSegments
      .where((s) => s.isNotEmpty)
      .toList(growable: false);
  if (segments.isEmpty || segments.first.contains('.')) {
    return null;
  }
  final id = segments.first;

  return 'https://v.redd.it/$id/DASHPlaylist.mpd';
}

/// Whether [url] is a Reddit gallery the listing did not carry the pictures for.
///
/// Scraped from old.reddit, a gallery arrives as this link and a 70px
/// thumbnail — its pictures live in `media_metadata`, which that HTML does not
/// contain. Recognising the link is what lets the card go and fetch them.
bool isRedditGalleryUrl(String? url) {
  if (url == null || url.isEmpty) {
    return false;
  }

  final uri = Uri.tryParse(url);
  if (uri == null ||
      !(uri.host == 'reddit.com' || uri.host.endsWith('.reddit.com'))) {
    return false;
  }

  final segments = uri.pathSegments
      .where((s) => s.isNotEmpty)
      .toList(growable: false);
  return segments.length >= 2 && segments.first == 'gallery';
}
