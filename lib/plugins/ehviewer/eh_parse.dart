/// Pure HTML / JSON parsers for E-Hentai — unit-tested without HTTP.
library;

import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/utils/json.dart';

final _galleryHref = RegExp(
  r'https?://(?:e-hentai|exhentai)\.org/g/(\d+)/([0-9a-f]+)/?',
);
final _pageHref = RegExp(
  r'https?://(?:e-hentai|exhentai)\.org/s/([0-9a-zA-Z]+)/(\d+)-(\d+)',
);
final _glink = RegExp(r'class="glink"[^>]*>([^<]+)<');
final _glcat = RegExp(
  r'class="gl1c glcat">.*?<div class="cn[^"]*"[^>]*>([^<]+)</div>',
  dotAll: true,
);
final _thumbSrc = RegExp(
  r'class="glthumb"[^>]*>.*?<img[^>]+(?:src|data-src)="([^"]+)"',
  dotAll: true,
);
final _pagesInRow = RegExp(r'>(\d+)\s*pages<');
final _posted = RegExp(r'id="posted_\d+"[^>]*>([^<]+)<');
final _uploader = RegExp(
  r'class="gl4c glhide".*?<a[^>]*>([^<]+)<',
  dotAll: true,
);
final _nextPage = RegExp(
  r'id="unext"[^>]*href="([^"]+)"|href="([^"]+)"[^>]*>\s*&gt;\s*<',
);
final _previewAnchor = RegExp(
  r'<a[^>]+href="[^"]*?/s/([0-9a-zA-Z]+)/(\d+)-(\d+)[^"]*"[^>]*>'
  r'(.*?)</a>',
  dotAll: true,
);
final _previewThumb = RegExp(r'url\(([^)]+)\)\s*(-?\d+)px');
final _previewSheetLink = RegExp(r'[?&]p=(\d+)');
final _imgSrcAfterId = RegExp(r'<img[^>]+id="img"[^>]+src="([^"]+)"');
final _imgSrcBeforeId = RegExp(r'<img[^>]+src="([^"]+)"[^>]+id="img"');
final _fullimgHref = RegExp(
  r'''href=["']([^"']*fullimg[^"']+)["']''',
  caseSensitive: false,
);
final _i7Href = RegExp(
  r'''id="i7"[^>]*>.*?<a[^>]+href=["']([^"']+)["']''',
  dotAll: true,
);

/// Largest free resample E-Hentai offers (`uconfig` key `xr`).
const ehPreferredImageSize = '2400';

/// Large gallery preview tiles (`uconfig` key `ts`).
const ehPreferredPreviewSize = 'l';

EhGalleryPage parseEhGalleryList(String html) {
  final rows = html.split(RegExp(r'<tr[^>]*>'));
  final galleries = <EhGallery>[];
  final seen = <int>{};

  for (final row in rows) {
    if (!row.contains('glink') || !row.contains('/g/')) continue;
    final href = _galleryHref.firstMatch(row);
    if (href == null) continue;
    final gid = int.tryParse(href.group(1)!);
    final token = href.group(2)!;
    if (gid == null || !seen.add(gid)) continue;

    final title = _decode(_glink.firstMatch(row)?.group(1) ?? '');
    if (title.isEmpty) continue;

    galleries.add(
      EhGallery(
        gid: gid,
        token: token,
        title: title,
        category: EhCategory.tryParse(_glcat.firstMatch(row)?.group(1)),
        thumbUrl: _thumbSrc.firstMatch(row)?.group(1),
        pageCount: int.tryParse(_pagesInRow.firstMatch(row)?.group(1) ?? ''),
        postedAt: _parsePosted(_posted.firstMatch(row)?.group(1)),
        uploader: _decode(_uploader.firstMatch(row)?.group(1) ?? ''),
      ),
    );
  }

  final next = _nextPage.firstMatch(html);
  final nextUrl = _decodeAttr(next?.group(1) ?? next?.group(2));

  return EhGalleryPage(
    galleries: galleries,
    nextUrl: nextUrl,
    hasMore: nextUrl != null && nextUrl.isNotEmpty,
  );
}

EhGalleryDetail? parseEhGalleryDetail(
  String html, {
  required int gid,
  required String token,
}) {
  final title = _decode(
    RegExp(r'<h1 id="gn">([^<]*)</h1>').firstMatch(html)?.group(1) ?? '',
  );
  if (title.isEmpty) return null;

  final titleJpn = _decode(
    RegExp(r'<h1 id="gj">([^<]*)</h1>').firstMatch(html)?.group(1) ?? '',
  );
  final category = EhCategory.tryParse(
    RegExp(
      r'id="gdc"[^>]*>\s*<div[^>]*>([^<]+)</div>',
    ).firstMatch(html)?.group(1),
  );
  final uploader = _decode(
    RegExp(r'id="gdn"><a[^>]*>([^<]+)</a>').firstMatch(html)?.group(1) ?? '',
  );
  final posted = _parsePosted(
    RegExp(r'Posted:</td><td[^>]*>([^<]+)').firstMatch(html)?.group(1),
  );
  final pageCount = int.tryParse(
    RegExp(r'Length:</td><td[^>]*>(\d+)\s*pages').firstMatch(html)?.group(1) ??
        '',
  );
  final rating = double.tryParse(
    RegExp(
          r'id="rating_label"[^>]*>Average:\s*([0-9.]+)',
        ).firstMatch(html)?.group(1) ??
        RegExp(r'Average:\s*([0-9.]+)').firstMatch(html)?.group(1) ??
        '',
  );
  final thumb =
      RegExp(
        r'id="gd1"[^>]*>.*?url\(([^)]+)\)',
        dotAll: true,
      ).firstMatch(html)?.group(1) ??
      RegExp(
        r'id="gd1"[^>]*>.*?<img[^>]+src="([^"]+)"',
        dotAll: true,
      ).firstMatch(html)?.group(1);

  final tags = [
    for (final m in RegExp(r'id="td_([^"]+)"').allMatches(html))
      _decode(m.group(1)!.replaceAll('+', ' ')),
  ];

  final previews = parseEhPreviewSheet(html);
  final sheetMeta = parseEhPreviewSheetMeta(html);

  return EhGalleryDetail(
    gid: gid,
    token: token,
    title: title,
    titleJpn: titleJpn.isEmpty ? null : titleJpn,
    category: category,
    thumbUrl: thumb,
    uploader: uploader.isEmpty ? null : uploader,
    postedAt: posted,
    pageCount: pageCount,
    rating: rating,
    tags: tags,
    previews: previews,
    comments: parseEhComments(html),
    previewSheetIndex: sheetMeta.index,
    previewSheetCount: sheetMeta.count,
  );
}

/// Preview tiles from one gallery HTML sheet (`?p=N`).
List<EhPreview> parseEhPreviewSheet(String html) {
  final previews = <EhPreview>[];
  final seenPages = <int>{};
  for (final m in _previewAnchor.allMatches(html)) {
    final page = int.tryParse(m.group(3) ?? '');
    if (page == null || !seenPages.add(page)) continue;
    final thumb = _previewThumb.firstMatch(m.group(4) ?? '');
    previews.add(
      EhPreview(
        pageToken: m.group(1)!,
        page: page,
        thumbUrl: _decodeAttr(thumb?.group(1)),
        thumbOffsetX: double.tryParse(thumb?.group(2) ?? ''),
      ),
    );
  }
  previews.sort((a, b) => a.page.compareTo(b.page));
  return previews;
}

({int index, int count}) parseEhPreviewSheetMeta(String html) {
  var maxP = 0;
  for (final m in _previewSheetLink.allMatches(html)) {
    final p = int.tryParse(m.group(1) ?? '') ?? 0;
    if (p > maxP) maxP = p;
  }
  final label =
      int.tryParse(
        RegExp(
              r'class="ptds"[^>]*>\s*<a[^>]*>(\d+)<',
            ).firstMatch(html)?.group(1) ??
            '1',
      ) ??
      1;
  final index = (label - 1).clamp(0, maxP);
  return (index: index, count: maxP + 1);
}

/// Parses `/s/{pageToken}/{gid}-{page}` from a next/prev (or absolute) URL.
({String pageToken, int gid, int page})? parseEhPageLink(String? url) {
  if (url == null || url.isEmpty) return null;
  final m = _pageHref.firstMatch(url);
  if (m == null) return null;
  final gid = int.tryParse(m.group(2)!);
  final page = int.tryParse(m.group(3)!);
  if (gid == null || page == null) return null;
  return (pageToken: m.group(1)!, gid: gid, page: page);
}

EhImagePage? parseEhImagePage(String html, {required int page}) {
  final imageUrl =
      _imgSrcAfterId.firstMatch(html)?.group(1) ??
      _imgSrcBeforeId.firstMatch(html)?.group(1);
  if (imageUrl == null || imageUrl.isEmpty) return null;

  final next = RegExp(
    r'id="next"[^>]*href="([^"]+)"',
  ).firstMatch(html)?.group(1);
  final prev = RegExp(
    r'id="prev"[^>]*href="([^"]+)"',
  ).firstMatch(html)?.group(1);
  final pageCount = int.tryParse(
    RegExp(
          r'>\s*(\d+)\s*</span>\s*</div>\s*<a id="next"',
        ).firstMatch(html)?.group(1) ??
        RegExp(r'(\d+)\s*</span>\s*</div>').firstMatch(html)?.group(1) ??
        '',
  );

  return EhImagePage(
    imageUrl: _decodeAttr(imageUrl) ?? imageUrl,
    originalImageUrl: parseEhOriginalImageUrl(html),
    reloadKey: parseEhReloadKey(html),
    page: page,
    nextPageUrl: _decodeAttr(next),
    prevPageUrl: _decodeAttr(prev),
    pageCount: pageCount,
  );
}

/// `nl('code')` from "Click here if the image fails loading".
String? parseEhReloadKey(String html) {
  return RegExp(r"""nl\(['"]([^'"]+)['"]\)""").firstMatch(html)?.group(1);
}

/// Image-page URI, optionally forcing a different H@H node via `nl`.
Uri ehImagePageUri({
  required String host,
  required String pageToken,
  required int gid,
  required int page,
  String? reloadKey,
}) {
  final uri = Uri.parse('$host/s/$pageToken/$gid-$page');
  if (reloadKey == null || reloadKey.isEmpty) return uri;
  return uri.replace(queryParameters: {'nl': reloadKey});
}

/// Search query + `f_*` params (min rating, language tag).
({String query, Map<String, String> params}) ehBuildSearch({
  required String query,
  required int catMask,
  int minRating = 0,
  String language = '',
}) {
  var q = query.trim();
  final lang = language.trim();
  if (lang.isNotEmpty) q = '$q language:$lang'.trim();
  return (
    query: q,
    params: {
      'f_search': q,
      'f_cats': '$catMask',
      'f_apply': 'Apply Filter',
      if (minRating >= 2) 'f_sr': 'on',
      if (minRating >= 2) 'f_srdd': '$minRating',
    },
  );
}

List<EhComment> parseEhComments(String html) {
  final chunks = html.split(RegExp(r'<div class="c1">'));
  if (chunks.length < 2) return const [];
  return [for (final chunk in chunks.skip(1)) ?_commentOne(chunk)];
}

EhComment? _commentOne(String chunk) {
  final author = _decode(
    RegExp(
          r'class="c3">.*?by:.*?<a[^>]*>([^<]+)</a>',
          dotAll: true,
        ).firstMatch(chunk)?.group(1) ??
        '',
  );
  final posted = _decode(
    RegExp(
          r'class="c3">Posted on\s+([^<]+?)\s+by:',
        ).firstMatch(chunk)?.group(1) ??
        '',
  );
  final rawBody =
      RegExp(
        r'<div class="c6"[^>]*>(.*?)</div>',
        dotAll: true,
      ).firstMatch(chunk)?.group(1) ??
      '';
  final body = _stripHtml(rawBody);
  if (author.isEmpty && body.isEmpty) return null;
  final score = RegExp(
    r'id="comment_score_[^"]*"[^>]*>([^<]+)',
  ).firstMatch(chunk)?.group(1);
  return EhComment(
    author: author,
    posted: posted,
    body: body,
    score: score?.trim(),
    uploader: chunk.contains('Uploader Comment'),
  );
}

String _stripHtml(String raw) =>
    _decode(
          raw
              .replaceAll(RegExp(r'<br\s*/?>', caseSensitive: false), '\n')
              .replaceAll(RegExp(r'<[^>]+>'), ' '),
        )
        .replaceAll(RegExp(r'\s+\n'), '\n')
        .replaceAll(RegExp(r'[ \t]{2,}'), ' ')
        .trim();

/// `fullimg.php` / `#i7` "Download original" href, if the page offers one.
String? parseEhOriginalImageUrl(String html) {
  final fullimg = _fullimgHref.firstMatch(html)?.group(1);
  if (fullimg != null && fullimg.isNotEmpty) return _decodeAttr(fullimg);
  final i7 = _i7Href.firstMatch(html)?.group(1);
  if (i7 == null || i7.isEmpty) return null;
  return _decodeAttr(i7);
}

/// Rewrites the cookie header so `uconfig` asks for 2400px resamples + large thumbs.
String ehRequestCookies(
  String raw, {
  String imageSize = ehPreferredImageSize,
  String previewSize = ehPreferredPreviewSize,
}) {
  final parts = <String, String>{};
  final order = <String>[];
  for (final piece in raw.split(';')) {
    final trimmed = piece.trim();
    if (trimmed.isEmpty) continue;
    final eq = trimmed.indexOf('=');
    if (eq <= 0) continue;
    final key = trimmed.substring(0, eq).trim();
    var value = trimmed.substring(eq + 1).trim();
    if (value.length >= 2 && value.startsWith('"') && value.endsWith('"')) {
      value = value.substring(1, value.length - 1);
    }
    if (!parts.containsKey(key)) order.add(key);
    parts[key] = value;
  }
  parts['uconfig'] = ehUconfigWithQuality(
    parts['uconfig'] ?? '',
    imageSize: imageSize,
    previewSize: previewSize,
  );
  if (!order.contains('uconfig')) order.add('uconfig');
  return [for (final key in order) '$key=${parts[key]}'].join('; ');
}

/// Sets `xr` (resample) and `ts` (preview tile size) on an existing `uconfig`.
String ehUconfigWithQuality(
  String raw, {
  String imageSize = ehPreferredImageSize,
  String previewSize = ehPreferredPreviewSize,
}) {
  final flags = <String, String>{};
  final order = <String>[];
  if (raw.isNotEmpty) {
    for (final piece in raw.split('-')) {
      if (piece.isEmpty) continue;
      final us = piece.indexOf('_');
      if (us <= 0) continue;
      final key = piece.substring(0, us);
      final value = piece.substring(us + 1);
      if (!flags.containsKey(key)) order.add(key);
      flags[key] = value;
    }
  }
  flags['xr'] = imageSize;
  flags['ts'] = previewSize;
  flags['nw'] = '1';
  if (!order.contains('xr')) order.add('xr');
  if (!order.contains('ts')) order.add('ts');
  if (!order.contains('nw')) order.add('nw');
  return [for (final key in order) '${key}_${flags[key]}'].join('-');
}

List<EhGallery> parseEhGdata(Object? raw) {
  final root = Json(raw);
  final list = root['gmetadata'];
  if (list.raw is! List) return const [];

  return [for (final item in list.raw as List) ?_gdataOne(Json(item))];
}

EhGallery? _gdataOne(Json json) {
  if (json['error'].exists) return null;
  final gid = json['gid'].integer;
  final token = json['token'].string;
  final title = json['title'].string;
  if (gid == null || token == null || token.isEmpty || title == null) {
    return null;
  }

  final postedUnix =
      int.tryParse(json['posted'].string ?? '') ?? json['posted'].integer;
  final tags = [for (final t in json['tags'].list) ?t.string];

  return EhGallery(
    gid: gid,
    token: token,
    title: title,
    titleJpn: json['title_jpn'].string,
    category: EhCategory.tryParse(json['category'].string),
    thumbUrl: json['thumb'].string,
    uploader: json['uploader'].string,
    postedAt: postedUnix == null
        ? null
        : DateTime.fromMillisecondsSinceEpoch(postedUnix * 1000, isUtc: true),
    pageCount:
        int.tryParse(json['filecount'].string ?? '') ??
        json['filecount'].integer,
    rating: json['rating'].number,
    tags: tags,
  );
}

DateTime? _parsePosted(String? raw) {
  if (raw == null || raw.trim().isEmpty) return null;
  // "2026-08-11 21:36" — site time, treat as local/unspecified.
  final normalised = raw.trim().replaceFirst(' ', 'T');
  return DateTime.tryParse(normalised);
}

String _decode(String raw) => raw
    .replaceAll('&amp;', '&')
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&quot;', '"')
    .replaceAll('&#039;', "'")
    .replaceAll('&nbsp;', ' ')
    .trim();

String? _decodeAttr(String? raw) {
  if (raw == null || raw.isEmpty) return null;
  return _decode(raw);
}
