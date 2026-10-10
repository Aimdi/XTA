import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_content.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_parser.dart';
import 'package:xta/plugins/pixiv/pixiv_ranking_modes.dart';
import 'package:xta/utils/json.dart';

typedef PixivNovelPage = PixivPage<PixivNovel>;

/// The novel side of Pixiv: feeds, rankings, bookmarks, series, the
/// watchlist, search, a creator's novels and a novel's text. Bookmarks and
/// the watchlist are the only writes. Built on [PixivClient]'s public transport.
class PixivNovelApi {
  final PixivClient client;

  const PixivNovelApi(this.client);

  /// A Provider override when one is given (tests), else one over the app's client.
  static PixivNovelApi of(BuildContext context) =>
      context.read<PixivNovelApi?>() ?? PixivNovelApi(context.read<PixivClient>());

  Future<Object?> _page(String path, Map<String, String> query, String? nextUrl) =>
      client.getPage(path, query: query, nextUrl: nextUrl);

  /// A list payload as the reader's Show R-18 and Hide AI show it. [ownList]
  /// keeps everything the reader saved on purpose.
  PixivNovelPage novelPageFrom(Object? json, {bool? includeAi, bool ownList = false}) => PixivPage(
    parsePixivNovelList(
      json,
      includeR18: ownList || client.showR18,
      includeAi: includeAi ?? (ownList || !client.hideAi),
    ),
    nextUrl: Json(json)['next_url'].string,
  );

  bool allowed(PixivNovel novel) => pixivNovelAllowed(novel, includeR18: client.showR18, includeAi: !client.hideAi);

  Future<PixivNovelPage> recommended({String? nextUrl}) async => novelPageFrom(
    await _page('/v1/novel/recommended', const {
      'include_privacy_policy': 'true',
      'filter': 'for_android',
      'include_ranking_novels': 'true',
    }, nextUrl),
  );

  /// New novels from followed creators: [restrict] is `public` or `private`.
  Future<PixivNovelPage> following({String restrict = 'public', String? nextUrl}) async =>
      novelPageFrom(await _page('/v1/novel/follow', {'restrict': restrict}, nextUrl));

  /// One novel ranking board. AI boards keep their AI novels even with Hide
  /// AI on: the reader asked for that board by name.
  Future<PixivNovelPage> ranking(String mode, {String? date, String? nextUrl}) async {
    final json = await _page('/v1/novel/ranking', {
      'mode': mode,
      if (date != null && date.isNotEmpty) 'date': date,
      'filter': 'for_android',
    }, nextUrl);
    return novelPageFrom(json, includeAi: pixivRankingModeIsAi(mode, pixivNovelRankingModes) ? true : null);
  }

  /// [userId]'s novel bookmarks. The reader's own keep R-18 and AI novels,
  /// saved on purpose; someone else's follow the reader's filters.
  Future<PixivNovelPage> bookmarks({required int userId, String restrict = 'public', String? nextUrl}) async {
    final json = await _page('/v1/user/bookmarks/novel', {'user_id': '$userId', 'restrict': restrict}, nextUrl);
    return novelPageFrom(json, ownList: userId == client.storedUserId);
  }

  /// The reader's own novel bookmarks.
  Future<PixivNovelPage> ownBookmarks({String restrict = 'public', String? nextUrl}) async =>
      bookmarks(userId: await client.ensureUserId(), restrict: restrict, nextUrl: nextUrl);

  /// Bookmarks [novelId] with [restrict], or re-files a bookmark it has.
  Future<void> addBookmark(int novelId, {required String restrict}) async {
    await client.postForm('/v2/novel/bookmark/add', {'novel_id': '$novelId', 'restrict': restrict});
  }

  Future<void> deleteBookmark(int novelId) async {
    await client.postForm('/v1/novel/bookmark/delete', {'novel_id': '$novelId'});
  }

  /// Watched novel series; rows that do not parse are skipped.
  Future<PixivPage<PixivWatchlistSeries>> watchlist({String? nextUrl}) async {
    final json = await _page('/v1/watchlist/novel', const {}, nextUrl);
    return PixivPage(parsePixivWatchlist(json), nextUrl: Json(json)['next_url'].string);
  }

  Future<void> addToWatchlist(int seriesId) => client.postForm('/v1/watchlist/novel/add', {'series_id': '$seriesId'});

  Future<void> removeFromWatchlist(int seriesId) =>
      client.postForm('/v1/watchlist/novel/delete', {'series_id': '$seriesId'});

  /// One novel's card fields, for a novel opened by its id alone; a
  /// [PixivException] when Pixiv no longer has it.
  Future<PixivNovel> detail(int novelId) async {
    final json = await client.getJson('/v2/novel/detail', query: {'novel_id': '$novelId'});
    return pixivNovelFromJson(Json(json)['novel'].raw) ??
        (throw PixivException(PixivErrorKind.notFound, 'novel $novelId'));
  }

  /// One page of `/v1/search/novel` for a query from `pixivSearchQuery` with
  /// the novel kind. [includeAi] follows the search's own AI choice.
  Future<PixivNovelPage> search(Map<String, String> query, {String? nextUrl, bool? includeAi}) async =>
      novelPageFrom(await _page('/v1/search/novel', query, nextUrl), includeAi: includeAi);

  /// The tags trending among novels, each over the picture Pixiv chose for it.
  Future<List<PixivTrendTag>> trendingTags() async => parsePixivTrendTags(
    await client.getJson('/v1/trending-tags/novel', query: const {'filter': 'for_android'}),
    includeR18: client.showR18,
    includeAi: !client.hideAi,
  );

  /// [userId]'s own novels. The reader's own keep R-18 and AI novels.
  Future<PixivNovelPage> userNovels(int userId, {String? nextUrl}) async => novelPageFrom(
    await _page('/v1/user/novels', {'user_id': '$userId', 'filter': 'for_android'}, nextUrl),
    ownList: userId == client.storedUserId,
  );

  /// The novel's text with its series neighbours and pictures, read from the
  /// page Pixiv's app shows it in.
  Future<PixivNovelContent> content(int novelId) async {
    final html = await client.getText('/webview/v2/novel', query: {'id': '$novelId'});
    return await pixivNovelParse<PixivNovelContent?>(pixivNovelContentFromHtml, html) ??
        (throw PixivException(PixivErrorKind.badResponse, 'no text in novel $novelId'));
  }

  /// One page of a series; chapters the reader's filters hide are left out but counted.
  Future<PixivNovelSeriesPage> series(int seriesId, {String? nextUrl}) async =>
      parsePixivNovelSeriesPage(await _page('/v2/novel/series', {'series_id': '$seriesId'}, nextUrl), allowed: allowed);
}
