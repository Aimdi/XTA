import 'package:xta/plugins/pixiv/pixiv_novel_parser.dart';
import 'package:xta/utils/json.dart';

String? _nonEmpty(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

/// The chapter before or after this one in its series, as the webview page links it.
class PixivNovelNeighbour {
  final int id;

  /// False when the reader may not open it: R-18 off, My pixiv only, or deleted.
  final bool viewable;
  final String title;

  /// Its place in the series, from 1; 0 when Pixiv did not say.
  final int order;

  const PixivNovelNeighbour({required this.id, this.viewable = false, this.title = '', this.order = 0});
}

PixivNovelNeighbour? _neighbourFromJson(Json json) {
  final id = json['id'].integer;
  if (id == null || id <= 0) return null;
  return PixivNovelNeighbour(
    id: id,
    viewable: json['viewable'].boolean == true,
    title: json['title'].string?.trim() ?? '',
    order: json['contentOrder'].integer ?? 0,
  );
}

/// A picture the text shows: the file drawn, and the full one a save fetches.
class PixivNovelPicture {
  final String url;
  final String? originalUrl;

  const PixivNovelPicture({required this.url, this.originalUrl});

  String get saveUrl => originalUrl ?? url;
}

/// A novel's text and what it refers to, from its webview page: the chapters
/// beside it in its series and the pictures its tags name.
class PixivNovelContent {
  final int id;
  final String title;

  /// The text with Pixiv's markup in it.
  final String text;
  final PixivNovelNeighbour? previous;
  final PixivNovelNeighbour? next;

  /// `[uploadedimage:]` pictures by their id.
  final Map<String, PixivNovelPicture> uploads;

  /// `[pixivimage:]` pictures by `ID` or `ID-N`, as the tag writes them.
  final Map<String, PixivNovelPicture> illusts;

  const PixivNovelContent({
    required this.id,
    required this.text,
    this.title = '',
    this.previous,
    this.next,
    this.uploads = const {},
    this.illusts = const {},
  });

  PixivNovelPicture? upload(PixivNovelUploadBlock block) => uploads[block.imageId];

  /// The page carries a work's picture under the tag's own key; a first page may
  /// be filed under the bare id.
  PixivNovelPicture? illust(PixivNovelIllustBlock block) =>
      illusts[block.key] ?? (block.page == 1 ? illusts['${block.illustId}'] : null);
}

/// The decoded `novel` object of a webview page, or null when it carries no text.
PixivNovelContent? pixivNovelContentFromJson(Object? json) {
  final data = Json(json);
  final text = data['text'].string;
  if (text == null) return null;
  final navigation = data['seriesNavigation'];
  return PixivNovelContent(
    id: data['id'].integer ?? 0,
    title: data['title'].string?.trim() ?? '',
    text: text,
    previous: _neighbourFromJson(navigation['prevNovel']),
    next: _neighbourFromJson(navigation['nextNovel']),
    uploads: _pictures(data['images'], _uploadPicture),
    illusts: _pictures(data['illusts'], _illustPicture),
  );
}

/// A novel's webview page read into its content; null when the page carries none.
PixivNovelContent? pixivNovelContentFromHtml(String html) => pixivNovelContentFromJson(pixivNovelJsonFromHtml(html));

/// A JSON object of pictures by key; entries without a usable address are left out.
Map<String, PixivNovelPicture> _pictures(Json map, PixivNovelPicture? Function(Json entry) read) {
  final raw = map.raw;
  if (raw is! Map) return const {};
  return Map.unmodifiable({for (final MapEntry(:key, :value) in raw.entries) '$key': ?read(Json(value))});
}

String? _firstUrl(List<Json> candidates) => candidates.map((url) => _nonEmpty(url.string)).nonNulls.firstOrNull;

PixivNovelPicture? _uploadPicture(Json image) {
  final urls = image['urls'];
  final shown = _firstUrl([urls['1200x1200'], urls['480mw'], urls['original'], urls['240mw'], urls['128x128']]);
  return shown == null ? null : PixivNovelPicture(url: shown, originalUrl: _nonEmpty(urls['original'].string));
}

PixivNovelPicture? _illustPicture(Json entry) {
  final images = entry['illust']['images'];
  final shown = _firstUrl([images['medium'], images['original'], images['small']]);
  return shown == null ? null : PixivNovelPicture(url: shown, originalUrl: _nonEmpty(images['original'].string));
}
