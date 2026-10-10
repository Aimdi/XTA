import 'package:flutter/widgets.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';
import 'package:xta/utils/json.dart';

/// Pixiv's search endpoints: works, creators, the free popular preview and the
/// landing's trending tags, suggested creators and tag suggestions.
class PixivSearchApi {
  final PixivClient client;

  const PixivSearchApi(this.client);

  /// A test's fake when one is provided, else the app's client.
  static PixivSearchApi of(BuildContext context) =>
      context.read<PixivSearchApi?>() ?? PixivSearchApi(context.read<PixivClient>());

  bool get isPremium => client.isPremium;

  /// One page of `/v1/search/illust` for a [query] from [pixivSearchQuery].
  /// [includeAi] follows the search's own AI choice rather than the feeds'.
  Future<PixivIllustPage> illusts(Map<String, String> query, {String? nextUrl, bool? includeAi}) async =>
      client.illustPageFrom(await _page('/v1/search/illust', query, nextUrl), includeAi: includeAi);

  /// Pixiv's free popular preview, which also matches translated tags.
  Future<PixivIllustPage> popularPreview(
    String word,
    PixivSearchTarget target, {
    String? nextUrl,
    bool? includeAi,
  }) async => client.illustPageFrom(
    await _page('/v1/search/popular-preview/illust', pixivPopularPreviewQuery(target, word), nextUrl),
    includeAi: includeAi,
  );

  /// Creators matching [word], each with the works Pixiv previews beside them.
  Future<PixivPage<PixivUserPreview>> users(String word, {String? nextUrl}) async {
    final json = await _page('/v1/search/user', {'word': word.trim(), 'filter': 'for_android'}, nextUrl);
    return PixivPage(
      parsePixivUserPreviews(json, includeR18: client.showR18, includeAi: !client.hideAi),
      nextUrl: Json(json)['next_url'].string,
    );
  }

  Future<List<PixivTrendTag>> trendingTags() => client.trendingTags();

  Future<List<PixivUser>> recommendedUsers() async => (await client.recommendedUsers()).users;

  Future<List<PixivTrendTag>> autocomplete(String word) => client.autocomplete(word);

  Future<Object?> _page(String path, Map<String, String> query, String? nextUrl) =>
      nextUrl == null || nextUrl.isEmpty ? client.getJson(path, query: query) : client.getNextJson(nextUrl);
}
