/// Session-only choices for the illustration reader, independent of feed data.
class PixivViewState {
  final int section;
  final int homeSource;
  final String rankingMode;
  final DateTime? rankingDate;
  final String bookmarksRestrict;
  final bool signingIn;

  const PixivViewState({
    this.section = 0,
    this.homeSource = 0,
    this.rankingMode = 'day',
    this.rankingDate,
    this.bookmarksRestrict = 'public',
    this.signingIn = false,
  });

  PixivViewState copyWith({
    int? section,
    int? homeSource,
    String? rankingMode,
    DateTime? rankingDate,
    bool clearRankingDate = false,
    String? bookmarksRestrict,
    bool? signingIn,
  }) => PixivViewState(
    section: section ?? this.section,
    homeSource: homeSource ?? this.homeSource,
    rankingMode: rankingMode ?? this.rankingMode,
    rankingDate: clearRankingDate ? null : rankingDate ?? this.rankingDate,
    bookmarksRestrict: bookmarksRestrict ?? this.bookmarksRestrict,
    signingIn: signingIn ?? this.signingIn,
  );
}
