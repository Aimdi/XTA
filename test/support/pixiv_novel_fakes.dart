import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_api.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_store.dart';

/// A novel with every field a card shows; nothing is fetched in tests.
PixivNovel pixivNovel({
  int id = 900,
  String title = 'Autumn Letters',
  int userId = 42,
  String userName = 'Mika',
  List<PixivTag> tags = const [PixivTag(name: '秋', translatedName: 'autumn')],
  PixivSeriesRef? series,
  int textLength = 12345,
  bool bookmarked = false,
  int bookmarks = 10,
  int xRestrict = 0,
  bool ai = false,
}) => PixivNovel(
  id: id,
  title: title,
  user: PixivUser(id: userId, name: userName, account: userName.toLowerCase(), comment: ''),
  coverUrl: 'https://i.pximg.net/c/240x480_80/novel-cover-master/img/2026/09/01/$id.jpg',
  tags: tags,
  series: series,
  textLength: textLength,
  isBookmarked: bookmarked,
  totalBookmarks: bookmarks,
  xRestrict: xRestrict,
  isAi: ai,
);

/// Answers every novel call from fixtures and records what was asked.
class FakePixivNovelApi extends PixivNovelApi {
  final calls = <String>[];
  List<PixivNovel> recommendedNovels;
  List<PixivNovel> followingNovels;
  List<PixivNovel> rankingNovels;
  List<PixivNovel> bookmarkNovels;
  List<PixivWatchlistSeries> watchlistSeries;

  /// Series pages by the `nextUrl` that asks for them; null is the first.
  Map<String?, PixivNovelSeriesPage> seriesPages;

  /// Thrown by every write when set.
  Object? writeError;

  FakePixivNovelApi(
    super.client, {
    this.recommendedNovels = const [],
    this.followingNovels = const [],
    this.rankingNovels = const [],
    this.bookmarkNovels = const [],
    this.watchlistSeries = const [],
    this.seriesPages = const {},
  });

  List<SingleChildWidget> get providers => [
    Provider<PixivNovelApi>.value(value: this),
    Provider<PixivNovelBookmarkStore>(create: (_) => PixivNovelBookmarkStore(), dispose: (_, store) => store.destroy()),
  ];

  @override
  Future<PixivNovelPage> recommended({String? nextUrl}) async {
    calls.add('recommended');
    return PixivPage(recommendedNovels);
  }

  @override
  Future<PixivNovelPage> following({String restrict = 'public', String? nextUrl}) async {
    calls.add('following:$restrict');
    return PixivPage(followingNovels);
  }

  @override
  Future<PixivNovelPage> ranking(String mode, {String? date, String? nextUrl}) async {
    calls.add('rank:$mode:$date');
    return PixivPage(rankingNovels);
  }

  @override
  Future<PixivNovelPage> bookmarks({required int userId, String restrict = 'public', String? nextUrl}) async {
    calls.add('bookmarks:$userId:$restrict');
    return PixivPage(bookmarkNovels);
  }

  @override
  Future<PixivNovelPage> ownBookmarks({String restrict = 'public', String? nextUrl}) async {
    calls.add('own:$restrict');
    return PixivPage(bookmarkNovels);
  }

  Future<void> _write(String call) async {
    calls.add(call);
    if (writeError case final error?) throw error;
  }

  @override
  Future<void> addBookmark(int novelId, {required String restrict}) => _write('bookmark:$novelId:$restrict');

  @override
  Future<void> deleteBookmark(int novelId) => _write('unbookmark:$novelId');

  @override
  Future<PixivPage<PixivWatchlistSeries>> watchlist({String? nextUrl}) async {
    calls.add('watchlist');
    return PixivPage(watchlistSeries);
  }

  @override
  Future<void> addToWatchlist(int seriesId) => _write('watch:$seriesId');

  @override
  Future<void> removeFromWatchlist(int seriesId) => _write('unwatch:$seriesId');

  @override
  Future<PixivNovelSeriesPage> series(int seriesId, {String? nextUrl}) async {
    calls.add('series:$seriesId:$nextUrl');
    final page = seriesPages[nextUrl];
    if (page == null) throw PixivException(PixivErrorKind.notFound, 'series $seriesId');
    return page;
  }
}
