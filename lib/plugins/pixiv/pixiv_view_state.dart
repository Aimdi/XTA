/// Which list Home shows under its source chips.
enum PixivHomeSource { following, recommended, manga, watchlist }

/// Whether the five sections show illustrations or novels.
enum PixivContentMode { illust, novel }

/// Which list Home shows in Novel mode.
enum PixivNovelHomeSource { recommended, following, watchlist }

/// Novel mode's own session choices, kept apart from the illustration ones
/// so switching modes returns to each as it was left.
class PixivNovelView {
  final PixivNovelHomeSource homeSource;

  /// `public` or `private`: whose novels the Following list shows.
  final String followRestrict;
  final String rankingMode;
  final DateTime? rankingDate;
  final String bookmarksRestrict;

  const PixivNovelView({
    this.homeSource = PixivNovelHomeSource.recommended,
    this.followRestrict = 'public',
    this.rankingMode = 'day',
    this.rankingDate,
    this.bookmarksRestrict = 'public',
  });

  PixivNovelView copyWith({
    PixivNovelHomeSource? homeSource,
    String? followRestrict,
    String? rankingMode,
    DateTime? rankingDate,
    bool clearRankingDate = false,
    String? bookmarksRestrict,
  }) => PixivNovelView(
    homeSource: homeSource ?? this.homeSource,
    followRestrict: followRestrict ?? this.followRestrict,
    rankingMode: rankingMode ?? this.rankingMode,
    rankingDate: clearRankingDate ? null : rankingDate ?? this.rankingDate,
    bookmarksRestrict: bookmarksRestrict ?? this.bookmarksRestrict,
  );
}

/// The sections the Pixiv screen can open on, as the start-section setting
/// stores them, in tab order.
const pixivStartSections = ['home', 'ranking', 'favorites', 'search'];

/// The tab index for a stored start section; Home for anything unknown.
int pixivStartSectionIndex(String? stored) => switch (pixivStartSections.indexOf(stored ?? '')) {
  final index when index >= 0 => index,
  _ => 0,
};

/// Session-only choices for the Pixiv reader, independent of feed data.
class PixivViewState {
  final int section;
  final PixivContentMode mode;
  final PixivHomeSource homeSource;

  /// `all`, `public` or `private`: whose works the Following feed shows.
  final String followRestrict;
  final String rankingMode;
  final DateTime? rankingDate;
  final String bookmarksRestrict;

  /// The bookmark tag Favorites is narrowed to; null shows every bookmark.
  final String? bookmarkTag;
  final bool signingIn;
  final PixivNovelView novel;

  const PixivViewState({
    this.section = 0,
    this.mode = PixivContentMode.illust,
    this.homeSource = PixivHomeSource.following,
    this.followRestrict = 'all',
    this.rankingMode = 'day',
    this.rankingDate,
    this.bookmarksRestrict = 'public',
    this.bookmarkTag,
    this.signingIn = false,
    this.novel = const PixivNovelView(),
  });

  bool get novelMode => mode == PixivContentMode.novel;

  PixivViewState copyWith({
    int? section,
    PixivContentMode? mode,
    PixivHomeSource? homeSource,
    String? followRestrict,
    String? rankingMode,
    DateTime? rankingDate,
    bool clearRankingDate = false,
    String? bookmarksRestrict,
    String? bookmarkTag,
    bool clearBookmarkTag = false,
    bool? signingIn,
    PixivNovelView? novel,
  }) => PixivViewState(
    section: section ?? this.section,
    mode: mode ?? this.mode,
    homeSource: homeSource ?? this.homeSource,
    followRestrict: followRestrict ?? this.followRestrict,
    rankingMode: rankingMode ?? this.rankingMode,
    rankingDate: clearRankingDate ? null : rankingDate ?? this.rankingDate,
    bookmarksRestrict: bookmarksRestrict ?? this.bookmarksRestrict,
    bookmarkTag: clearBookmarkTag ? null : bookmarkTag ?? this.bookmarkTag,
    signingIn: signingIn ?? this.signingIn,
    novel: novel ?? this.novel,
  );
}
