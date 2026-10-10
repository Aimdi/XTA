/// Which list Home shows under its source chips.
enum PixivHomeSource { following, recommended }

/// The sections the Pixiv screen can open on, as the start-section setting
/// stores them, in tab order.
const pixivStartSections = ['home', 'ranking', 'favorites', 'search'];

/// The tab index for a stored start section; Home for anything unknown.
int pixivStartSectionIndex(String? stored) => switch (pixivStartSections.indexOf(stored ?? '')) {
  final index when index >= 0 => index,
  _ => 0,
};

/// Session-only choices for the illustration reader, independent of feed data.
class PixivViewState {
  final int section;
  final PixivHomeSource homeSource;

  /// `all`, `public` or `private`: whose works the Following feed shows.
  final String followRestrict;
  final String rankingMode;
  final DateTime? rankingDate;
  final String bookmarksRestrict;

  /// The bookmark tag Favorites is narrowed to; null shows every bookmark.
  final String? bookmarkTag;
  final bool signingIn;

  const PixivViewState({
    this.section = 0,
    this.homeSource = PixivHomeSource.following,
    this.followRestrict = 'all',
    this.rankingMode = 'day',
    this.rankingDate,
    this.bookmarksRestrict = 'public',
    this.bookmarkTag,
    this.signingIn = false,
  });

  PixivViewState copyWith({
    int? section,
    PixivHomeSource? homeSource,
    String? followRestrict,
    String? rankingMode,
    DateTime? rankingDate,
    bool clearRankingDate = false,
    String? bookmarksRestrict,
    String? bookmarkTag,
    bool clearBookmarkTag = false,
    bool? signingIn,
  }) => PixivViewState(
    section: section ?? this.section,
    homeSource: homeSource ?? this.homeSource,
    followRestrict: followRestrict ?? this.followRestrict,
    rankingMode: rankingMode ?? this.rankingMode,
    rankingDate: clearRankingDate ? null : rankingDate ?? this.rankingDate,
    bookmarksRestrict: bookmarksRestrict ?? this.bookmarksRestrict,
    bookmarkTag: clearBookmarkTag ? null : bookmarkTag ?? this.bookmarkTag,
    signingIn: signingIn ?? this.signingIn,
  );
}
