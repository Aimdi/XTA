import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

/// A novel's page on pixiv.net, which is also what its links look like.
String pixivNovelUrl(int id) => 'https://www.pixiv.net/novel/show.php?id=$id';

/// A novel series' page on pixiv.net.
String pixivNovelSeriesUrl(int id) => 'https://www.pixiv.net/novel/series/$id';

String? _nonEmpty(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

/// One novel as Pixiv's lists, rankings, bookmarks and series send it.
class PixivNovel {
  final int id;
  final String title;

  /// Plain text; [captionHtml] keeps Pixiv's links and line breaks.
  final String caption;
  final String captionHtml;
  final String? coverUrl;
  final PixivUser user;
  final List<PixivTag> tags;
  final PixivSeriesRef? series;

  /// Characters of text, as Pixiv counts them.
  final int textLength;
  final int pageCount;
  final DateTime? createdAt;
  final int totalBookmarks;
  final int totalViews;
  final int totalComments;
  final bool isBookmarked;

  /// Pixiv's age rating: 0 for everyone, 1 for R-18, 2 for R-18G.
  final int xRestrict;

  /// The author marked the novel as AI-generated.
  final bool isAi;
  final bool isOriginal;

  const PixivNovel({
    required this.id,
    required this.title,
    required this.user,
    this.caption = '',
    this.captionHtml = '',
    this.coverUrl,
    this.tags = const [],
    this.series,
    this.textLength = 0,
    this.pageCount = 1,
    this.createdAt,
    this.totalBookmarks = 0,
    this.totalViews = 0,
    this.totalComments = 0,
    this.isBookmarked = false,
    this.xRestrict = 0,
    this.isAi = false,
    this.isOriginal = false,
  });

  bool get isR18 => xRestrict > 0;
  bool get isR18G => xRestrict >= 2;
  String get url => pixivNovelUrl(id);
}

/// One novel object, or null when it names no novel or Pixiv withholds it
/// (deleted, or restricted to the author's My pixiv).
PixivNovel? pixivNovelFromJson(Object? json) {
  final data = Json(json);
  final id = data['id'].integer;
  if (id == null || id <= 0 || data['visible'].boolean == false) {
    return null;
  }
  final caption = data['caption'].string?.trim() ?? '';
  return PixivNovel(
    id: id,
    title: data['title'].string?.trim() ?? '',
    caption: pixivCaptionToText(caption),
    captionHtml: caption,
    coverUrl: _coverOf(data['image_urls']),
    user: PixivUser.fromUserJson(data['user'].raw),
    tags: pixivTagsFromJson(data['tags']),
    series: pixivSeriesRefFromJson(data['series']),
    textLength: data['text_length'].integer ?? 0,
    pageCount: data['page_count'].integer ?? 1,
    createdAt: DateTime.tryParse(data['create_date'].string ?? '')?.toLocal(),
    totalBookmarks: data['total_bookmarks'].integer ?? 0,
    totalViews: data['total_view'].integer ?? 0,
    totalComments: data['total_comments'].integer ?? 0,
    isBookmarked: data['is_bookmarked'].boolean == true,
    xRestrict: data['x_restrict'].integer ?? 0,
    isAi: data['novel_ai_type'].integer == 2,
    isOriginal: data['is_original'].boolean == true,
  );
}

/// The aspect-preserving `medium` cover first; Pixiv's stand-in for a
/// missing cover is a real image, so it is kept.
String? _coverOf(Json urls) =>
    _nonEmpty(urls['medium'].string) ?? _nonEmpty(urls['square_medium'].string) ?? _nonEmpty(urls['large'].string);

/// Whether the reader's Show R-18 and Hide AI choices let [novel] through.
bool pixivNovelAllowed(PixivNovel novel, {required bool includeR18, required bool includeAi}) =>
    (includeR18 || !novel.isR18) && (includeAi || !novel.isAi);

/// `novels` of a list payload: unusable entries skipped, and R-18 and AI
/// novels left out unless asked for.
List<PixivNovel> parsePixivNovelList(Object? json, {bool includeR18 = false, bool includeAi = true}) => [
  for (final item in Json(json)['novels'].list)
    if (pixivNovelFromJson(item.raw) case final novel?)
      if (pixivNovelAllowed(novel, includeR18: includeR18, includeAi: includeAi)) novel,
];

/// A novel series as its page introduces it.
class PixivNovelSeries {
  final int id;
  final String title;
  final String caption;
  final String captionHtml;
  final PixivUser user;
  final bool isConcluded;

  /// Chapters published so far.
  final int contentCount;
  final int totalCharacterCount;
  final bool isOriginal;
  final bool watchlistAdded;

  const PixivNovelSeries({
    required this.id,
    required this.title,
    required this.user,
    this.caption = '',
    this.captionHtml = '',
    this.isConcluded = false,
    this.contentCount = 0,
    this.totalCharacterCount = 0,
    this.isOriginal = false,
    this.watchlistAdded = false,
  });

  String get url => pixivNovelSeriesUrl(id);

  PixivNovelSeries copyWith({bool? watchlistAdded}) => PixivNovelSeries(
    id: id,
    title: title,
    user: user,
    caption: caption,
    captionHtml: captionHtml,
    isConcluded: isConcluded,
    contentCount: contentCount,
    totalCharacterCount: totalCharacterCount,
    isOriginal: isOriginal,
    watchlistAdded: watchlistAdded ?? this.watchlistAdded,
  );
}

/// `novel_series_detail`, or null when it names no series.
PixivNovelSeries? pixivNovelSeriesFromJson(Object? json) {
  final detail = Json(json);
  final id = detail['id'].integer;
  if (id == null || id <= 0) {
    return null;
  }
  final caption = detail['caption'].string?.trim() ?? '';
  return PixivNovelSeries(
    id: id,
    title: _nonEmpty(detail['title'].string) ?? '',
    user: PixivUser.fromUserJson(detail['user'].raw),
    caption: pixivCaptionToText(caption),
    captionHtml: caption,
    isConcluded: detail['is_concluded'].boolean == true,
    contentCount: detail['content_count'].integer ?? 0,
    totalCharacterCount: detail['total_character_count'].integer ?? 0,
    isOriginal: detail['is_original'].boolean == true,
    watchlistAdded: detail['watchlist_added'].boolean == true,
  );
}

/// A chapter of a series and its place in it, counted over every chapter
/// Pixiv listed, so a chapter the reader's filters hide keeps its number.
class PixivNovelChapter {
  final int order;
  final PixivNovel novel;

  const PixivNovelChapter({required this.order, required this.novel});
}

/// One page of `/v2/novel/series`: the series (Pixiv repeats it on every
/// page), its first and newest chapters, and this page's chapters.
class PixivNovelSeriesPage {
  final PixivNovelSeries? series;
  final PixivNovel? first;
  final PixivNovel? latest;

  /// Chapters numbered from one within this page; the store adds the
  /// chapters of the pages before it.
  final List<PixivNovelChapter> chapters;

  /// How many chapters Pixiv listed on this page, shown or not.
  final int listed;
  final String? nextUrl;

  const PixivNovelSeriesPage({
    this.series,
    this.first,
    this.latest,
    this.chapters = const [],
    this.listed = 0,
    this.nextUrl,
  });
}

/// A series page; chapters the reader's filters ([allowed]) hide are left out
/// but counted, and a first or newest chapter they hide is not offered.
PixivNovelSeriesPage parsePixivNovelSeriesPage(Object? json, {bool Function(PixivNovel novel)? allowed}) {
  final root = Json(json);
  final listed = root['novels'].list;
  PixivNovel? shown(Json item) => switch (pixivNovelFromJson(item.raw)) {
    final novel? when allowed?.call(novel) ?? true => novel,
    _ => null,
  };
  return PixivNovelSeriesPage(
    series: pixivNovelSeriesFromJson(root['novel_series_detail'].raw),
    first: shown(root['novel_series_first_novel']),
    latest: shown(root['novel_series_latest_novel']),
    chapters: [
      for (final (index, item) in listed.indexed)
        if (shown(item) case final novel?) PixivNovelChapter(order: index + 1, novel: novel),
    ],
    listed: listed.length,
    nextUrl: _nonEmpty(root['next_url'].string),
  );
}
