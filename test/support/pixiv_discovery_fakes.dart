import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixivision_parser.dart';

/// Answers every discovery call from fixtures and records what was asked.
class FakePixivDiscoveryApi extends PixivDiscoveryApi {
  final calls = <String>[];
  List<PixivIllust> manga;
  List<PixivIllust> rankingWorks;
  List<PixivIllust> followingWorks;
  List<PixivIllust> walkthroughWorks;
  List<PixivUserPreview> users;
  List<PixivSpotlightArticle> articles;
  List<PixivWatchlistSeries> watchlist;
  PixivIllustSeries? series;
  List<PixivIllust> seriesWorks;
  PixivSeriesContext? context;
  PixivisionArticle? article;

  FakePixivDiscoveryApi(
    super.client, {
    this.manga = const [],
    this.rankingWorks = const [],
    this.followingWorks = const [],
    this.walkthroughWorks = const [],
    this.users = const [],
    this.articles = const [],
    this.watchlist = const [],
    this.series,
    this.seriesWorks = const [],
    this.context,
    this.article,
  });

  @override
  Future<PixivIllustPage> mangaRecommended({String? nextUrl}) async {
    calls.add('manga');
    return PixivIllustPage(illusts: manga);
  }

  @override
  Future<PixivIllustPage> following({String restrict = 'all', String? nextUrl}) async {
    calls.add('following:$restrict');
    return PixivIllustPage(illusts: followingWorks);
  }

  @override
  Future<PixivIllustPage> ranking(String mode, {String? date, String? nextUrl}) async {
    calls.add('rank:$mode:$date');
    return PixivIllustPage(illusts: rankingWorks);
  }

  @override
  Future<PixivPage<PixivUserPreview>> recommendedUsers({String? nextUrl}) async {
    calls.add('users');
    return PixivPage(users);
  }

  @override
  Future<PixivPage<PixivSpotlightArticle>> spotlightArticles({String? nextUrl}) async {
    calls.add('spotlight');
    return PixivPage(articles);
  }

  @override
  Future<PixivSeriesPage> illustSeries(int seriesId, {String? nextUrl}) async {
    calls.add('series:$seriesId');
    return PixivSeriesPage(series: series, works: PixivIllustPage(illusts: seriesWorks));
  }

  @override
  Future<PixivSeriesContext?> seriesContext(int illustId) async {
    calls.add('context:$illustId');
    return context;
  }

  @override
  Future<void> addToWatchlist(int seriesId) async => calls.add('watch:$seriesId');

  @override
  Future<void> removeFromWatchlist(int seriesId) async => calls.add('unwatch:$seriesId');

  @override
  Future<PixivPage<PixivWatchlistSeries>> mangaWatchlist({String? nextUrl}) async {
    calls.add('watchlist');
    return PixivPage(watchlist);
  }

  @override
  Future<PixivIllustPage> walkthrough({String? nextUrl}) async {
    calls.add('walkthrough');
    return PixivIllustPage(illusts: walkthroughWorks);
  }

  @override
  Future<PixivisionArticle> pixivisionArticle(int id) async {
    calls.add('pixivision:$id');
    final found = article;
    if (found == null) throw PixivException(PixivErrorKind.notFound, 'article $id');
    return found;
  }
}
