import 'dart:convert';

import 'package:flutter/widgets.dart';
import 'package:http/http.dart' as http;
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/plugins/pixiv/pixivision_parser.dart';
import 'package:xta/utils/json.dart';

/// pixivision.net serves its full article markup to desktop browsers only.
const pixivisionUserAgent =
    'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0 Safari/537.36';

/// The browse surfaces: rankings, recommended manga and creators, the
/// Following feed by visibility, Pixivision, series, the manga watchlist and
/// the signed-out preview. Built on [PixivClient]'s public transport.
class PixivDiscoveryApi {
  final PixivClient client;

  const PixivDiscoveryApi(this.client);

  /// A Provider override when one is given (tests), else one over the app's client.
  static PixivDiscoveryApi of(BuildContext context) =>
      context.read<PixivDiscoveryApi?>() ?? PixivDiscoveryApi(context.read<PixivClient>());

  Future<Object?> _page(String path, Map<String, String> query, String? nextUrl) =>
      nextUrl == null || nextUrl.isEmpty ? client.getJson(path, query: query) : client.getNextJson(nextUrl);

  String? _nextOf(Object? json) => Json(json)['next_url'].string;

  /// Manga Pixiv recommends, shown under the reader's filters like any feed.
  Future<PixivIllustPage> mangaRecommended({String? nextUrl}) async => client.illustPageFrom(
    await _page('/v1/manga/recommended', const {'include_ranking_label': 'true', 'filter': 'for_android'}, nextUrl),
  );

  /// New works from followed creators: [restrict] is `all`, `public` or `private`.
  Future<PixivIllustPage> following({String restrict = 'all', String? nextUrl}) async =>
      client.illustPageFrom(await _page('/v2/illust/follow', {'restrict': restrict}, nextUrl));

  /// One ranking board (Pixiv has no `/v1/ranking/illust`). [date]
  /// (`YYYY-MM-DD`) opens that day's archived board; null is the latest. AI
  /// boards keep their AI works even with Hide AI on: the reader asked for
  /// that board by name.
  Future<PixivIllustPage> ranking(String mode, {String? date, String? nextUrl}) async {
    final json = await _page('/v1/illust/ranking', {
      'mode': mode,
      if (date != null && date.isNotEmpty) 'date': date,
      'filter': 'for_android',
    }, nextUrl);
    return client.illustPageFrom(json, includeAi: pixivRankingModeIsAi(mode) ? true : null);
  }

  /// Creators Pixiv suggests, each with preview works the reader's filters allow.
  Future<PixivPage<PixivUserPreview>> recommendedUsers({String? nextUrl}) async {
    final json = await _page('/v1/user/recommended', const {'filter': 'for_android'}, nextUrl);
    final previews = parsePixivUserPreviews(json, includeR18: client.showR18, includeAi: !client.hideAi);
    return PixivPage(previews, nextUrl: _nextOf(json));
  }

  Future<PixivPage<PixivSpotlightArticle>> spotlightArticles({String? nextUrl}) async {
    final json = await _page('/v1/spotlight/articles', const {'filter': 'for_android', 'category': 'all'}, nextUrl);
    return PixivPage(parsePixivSpotlightArticles(json), nextUrl: _nextOf(json));
  }

  Future<PixivSeriesPage> illustSeries(int seriesId, {String? nextUrl}) async {
    final json = await _page('/v1/illust/series', {'illust_series_id': '$seriesId', 'filter': 'for_android'}, nextUrl);
    return PixivSeriesPage(
      series: pixivIllustSeriesFromJson(Json(json)['illust_series_detail'].raw),
      works: client.illustPageFrom(json),
    );
  }

  /// Where [illustId] sits in its series; neighbours the reader's filters
  /// would hide are left out.
  Future<PixivSeriesContext?> seriesContext(int illustId) async {
    final json = await client.getJson('/v1/illust-series/illust', query: {'illust_id': '$illustId'});
    return parsePixivSeriesContext(
      json,
      allowed: (illust) => pixivContentAllowed(illust, includeR18: client.showR18, includeAi: !client.hideAi),
    );
  }

  Future<void> addToWatchlist(int seriesId) => client.postForm('/v1/watchlist/manga/add', {'series_id': '$seriesId'});

  Future<void> removeFromWatchlist(int seriesId) =>
      client.postForm('/v1/watchlist/manga/delete', {'series_id': '$seriesId'});

  Future<PixivPage<PixivWatchlistSeries>> mangaWatchlist({String? nextUrl}) async {
    final json = await _page('/v1/watchlist/manga', const {}, nextUrl);
    return PixivPage(parsePixivWatchlist(json), nextUrl: _nextOf(json));
  }

  /// The showcase Pixiv offers before sign-in; the one call that takes no token.
  Future<PixivIllustPage> walkthrough({String? nextUrl}) async {
    final path = nextUrl == null || nextUrl.isEmpty ? '/v1/walkthrough/illusts' : nextUrl;
    return client.illustPageFrom(await client.getJson(path, auth: false));
  }

  /// A Pixivision article in the reader's language, read from its web page.
  Future<PixivisionArticle> pixivisionArticle(int id) async {
    final language = pixivisionLanguage(client.locale());
    final uri = Uri.parse(pixivisionArticleUrl(id, language));
    final article = parsePixivisionArticle(await _webPage(uri, language));
    if (article.isEmpty) {
      throw PixivException(PixivErrorKind.badResponse, '$uri: no article body');
    }
    return article;
  }

  Future<String> _webPage(Uri uri, String language) async {
    final http.Response response;
    try {
      response = await client.httpClient
          .get(
            uri,
            headers: {
              'User-Agent': pixivisionUserAgent,
              'Referer': 'https://www.pixivision.net/$language/',
              'Accept-Language': pixivAcceptLanguage(client.locale()),
              'Accept': 'text/html',
            },
          )
          .timeout(const Duration(seconds: 15));
    } catch (e) {
      throw PixivException(PixivErrorKind.network, '$e');
    }
    if (response.statusCode == 404) throw PixivException(PixivErrorKind.notFound, '$uri: 404');
    if (response.statusCode < 200 || response.statusCode >= 300) {
      throw PixivException(PixivErrorKind.badResponse, '$uri: ${response.statusCode}');
    }
    return utf8.decode(response.bodyBytes, allowMalformed: true);
  }
}
