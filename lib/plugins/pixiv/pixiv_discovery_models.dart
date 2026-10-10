import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

String? _nonEmpty(String? value) {
  final trimmed = value?.trim();
  return trimmed == null || trimmed.isEmpty ? null : trimmed;
}

DateTime? _dateOf(Json value) => DateTime.tryParse(value.string ?? '')?.toLocal();

/// The page a series has on pixiv.net, which is also what its links look like.
String pixivSeriesUrl(int userId, int seriesId) => 'https://www.pixiv.net/user/$userId/series/$seriesId';

/// A Pixivision article as Pixiv's spotlight list announces it.
class PixivSpotlightArticle {
  final int id;
  final String title;

  /// The title without the "for your pleasure" decorations Pixiv adds to [title].
  final String pureTitle;
  final String thumbnailUrl;
  final String articleUrl;
  final DateTime? publishedAt;

  const PixivSpotlightArticle({
    required this.id,
    required this.title,
    required this.thumbnailUrl,
    this.pureTitle = '',
    this.articleUrl = '',
    this.publishedAt,
  });

  String get displayTitle => pureTitle.isNotEmpty ? pureTitle : title;
}

PixivSpotlightArticle? pixivSpotlightArticleFromJson(Json json) {
  final id = json['id'].integer;
  final title = _nonEmpty(json['title'].string) ?? _nonEmpty(json['pure_title'].string);
  if (id == null || id <= 0 || title == null) {
    return null;
  }
  return PixivSpotlightArticle(
    id: id,
    title: title,
    pureTitle: _nonEmpty(json['pure_title'].string) ?? '',
    thumbnailUrl: _nonEmpty(json['thumbnail'].string) ?? '',
    articleUrl: _nonEmpty(json['article_url'].string) ?? '',
    publishedAt: _dateOf(json['publish_date']),
  );
}

/// `spotlight_articles` of `/v1/spotlight/articles`; entries without an id or title are skipped.
List<PixivSpotlightArticle> parsePixivSpotlightArticles(Object? json) => [
  for (final item in Json(json)['spotlight_articles'].list) ?pixivSpotlightArticleFromJson(item),
];

/// An illustration or manga series as its page introduces it.
class PixivIllustSeries {
  final int id;
  final String title;

  /// Plain text; Pixiv sends series captions as HTML like work captions.
  final String caption;
  final String? coverUrl;
  final int workCount;
  final DateTime? createdAt;
  final PixivUser user;
  final bool watchlistAdded;

  const PixivIllustSeries({
    required this.id,
    required this.title,
    required this.user,
    this.caption = '',
    this.coverUrl,
    this.workCount = 0,
    this.createdAt,
    this.watchlistAdded = false,
  });

  String get url => pixivSeriesUrl(user.id, id);

  PixivIllustSeries copyWith({bool? watchlistAdded}) => PixivIllustSeries(
    id: id,
    title: title,
    user: user,
    caption: caption,
    coverUrl: coverUrl,
    workCount: workCount,
    createdAt: createdAt,
    watchlistAdded: watchlistAdded ?? this.watchlistAdded,
  );
}

/// `illust_series_detail`, or null when it names no series.
PixivIllustSeries? pixivIllustSeriesFromJson(Object? json) {
  final detail = Json(json);
  final id = detail['id'].integer;
  if (id == null || id <= 0) {
    return null;
  }
  return PixivIllustSeries(
    id: id,
    title: _nonEmpty(detail['title'].string) ?? '',
    user: PixivUser.fromUserJson(detail['user'].raw),
    caption: pixivCaptionToText(detail['caption'].string),
    coverUrl: _nonEmpty(detail['cover_image_urls']['medium'].string),
    workCount: detail['series_work_count'].integer ?? 0,
    createdAt: _dateOf(detail['create_date']),
    watchlistAdded: detail['watchlist_added'].boolean == true,
  );
}

/// One page of a series: its header (Pixiv repeats it on every page) and works.
class PixivSeriesPage {
  final PixivIllustSeries? series;
  final PixivPage<PixivIllust> works;

  const PixivSeriesPage({required this.works, this.series});
}

/// Where a work sits in its series, and the works either side of it.
class PixivSeriesContext {
  /// One-based position of the work in the series.
  final int order;

  /// Works in the series; zero when Pixiv did not say.
  final int total;
  final PixivIllust? previous;
  final PixivIllust? next;

  const PixivSeriesContext({required this.order, this.total = 0, this.previous, this.next});
}

/// `/v1/illust-series/illust`; null when the work has no place in a series.
/// [allowed] decides whether a neighbour may be offered, as the feeds decide.
PixivSeriesContext? parsePixivSeriesContext(Object? json, {bool Function(PixivIllust illust)? allowed}) {
  final root = Json(json);
  final context = root['illust_series_context'];
  final order = context['content_order'].integer;
  if (order == null || order <= 0) {
    return null;
  }
  PixivIllust? neighbour(String key) => switch (pixivIllustFromJson(context[key].raw)) {
    final illust? when allowed?.call(illust) ?? true => illust,
    _ => null,
  };
  return PixivSeriesContext(
    order: order,
    total: root['illust_series_detail']['series_work_count'].integer ?? 0,
    previous: neighbour('prev'),
    next: neighbour('next'),
  );
}

/// A series on the reader's watchlist, as `/v1/watchlist/manga` lists it. The
/// novel watchlist sends the same shape.
class PixivWatchlistSeries {
  final int id;
  final String title;
  final String? coverUrl;

  /// Pixiv's reason the series cannot be shown, such as a restricted work.
  final String? maskText;
  final int userId;
  final String userName;
  final int? latestContentId;
  final DateTime? lastPublishedAt;
  final int publishedCount;

  const PixivWatchlistSeries({
    required this.id,
    required this.title,
    required this.userId,
    required this.userName,
    this.coverUrl,
    this.maskText,
    this.latestContentId,
    this.lastPublishedAt,
    this.publishedCount = 0,
  });
}

PixivWatchlistSeries? pixivWatchlistSeriesFromJson(Json json) {
  final id = json['id'].integer;
  if (id == null || id <= 0) {
    return null;
  }
  final latest = json['latest_content_id'].integer;
  return PixivWatchlistSeries(
    id: id,
    title: _nonEmpty(json['title'].string) ?? '',
    userId: json['user']['id'].integer ?? 0,
    userName: _nonEmpty(json['user']['name'].string) ?? '',
    coverUrl: _nonEmpty(json['url'].string),
    maskText: _nonEmpty(json['mask_text'].string),
    latestContentId: latest != null && latest > 0 ? latest : null,
    lastPublishedAt: _dateOf(json['last_published_content_datetime']),
    publishedCount: json['published_content_count'].integer ?? 0,
  );
}

/// `series` of a watchlist page; rows that do not parse are skipped.
List<PixivWatchlistSeries> parsePixivWatchlist(Object? json) => [
  for (final item in Json(json)['series'].list) ?pixivWatchlistSeriesFromJson(item),
];
