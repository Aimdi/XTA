import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_favorite_tags_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_search_api.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';

/// Answers every search call from fixtures and records what was asked.
class FakePixivSearchApi extends PixivSearchApi {
  final calls = <String>[];
  final queries = <Map<String, String>>[];
  bool premium;
  List<PixivIllust> works;
  List<PixivIllust> preview;
  List<PixivUserPreview> creatorsFound;
  List<PixivTrendTag> trending;
  List<PixivUser> creators;
  Map<String, List<PixivTrendTag>> suggestions;

  /// Thrown by the trending call while set, to fail that landing list alone.
  Object? trendingError;

  /// Handed back as every works page's `next_url` while set.
  String? worksNextUrl;

  FakePixivSearchApi({
    PixivClient? client,
    this.premium = false,
    this.works = const [],
    this.preview = const [],
    this.creatorsFound = const [],
    this.trending = const [],
    this.creators = const [],
    this.suggestions = const {},
  }) : super(client ?? PixivClient(PrefServiceCache()));

  SingleChildWidget get provider => Provider<PixivSearchApi>.value(value: this);

  @override
  bool get isPremium => premium;

  @override
  Future<PixivIllustPage> illusts(Map<String, String> query, {String? nextUrl, bool? includeAi}) async {
    if (nextUrl != null) {
      calls.add('illusts-next');
    } else {
      calls.add('illusts:${query['word']}');
      queries.add(query);
    }
    return PixivIllustPage(illusts: works, nextUrl: worksNextUrl);
  }

  @override
  Future<PixivIllustPage> popularPreview(
    String word,
    PixivSearchTarget target, {
    String? nextUrl,
    bool? includeAi,
  }) async {
    calls.add('preview:$word');
    return PixivIllustPage(illusts: preview);
  }

  @override
  Future<PixivPage<PixivUserPreview>> users(String word, {String? nextUrl}) async {
    calls.add('users:$word');
    return PixivPage(creatorsFound);
  }

  @override
  Future<List<PixivTrendTag>> trendingTags() async {
    calls.add('trending');
    final error = trendingError;
    if (error != null) throw error;
    return trending;
  }

  @override
  Future<List<PixivUser>> recommendedUsers() async {
    calls.add('creators');
    return creators;
  }

  @override
  Future<List<PixivTrendTag>> autocomplete(String word) async {
    calls.add('autocomplete:$word');
    return suggestions[word] ?? const [];
  }
}

/// The app's one favourite-tags list, as main.dart provides it, over the
/// harness's prefs.
SingleChildWidget pixivFavoriteTagsProvider() => Provider<PixivFavoriteTagsStore>(
  create: (context) => PixivFavoriteTagsStore(PrefService.of(context, listen: false)),
  dispose: (_, store) => store.destroy(),
);
