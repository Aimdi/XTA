import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/media_grid/media_grid.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/tweet/sensitive_media_gate.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/paging.dart';

class ProfileMediaGrid extends StatefulWidget {
  final UserWithExtra user;
  final BasePrefService pref;
  final MediaFilter filter;
  final ValueChanged<bool>? onBroadcastsFound;

  const ProfileMediaGrid({
    super.key,
    required this.user,
    required this.pref,
    this.filter = MediaFilter.all,
    this.onBroadcastsFound,
  });

  @override
  State<ProfileMediaGrid> createState() => _ProfileMediaGridState();
}

class _ProfileMediaGridState extends State<ProfileMediaGrid>
    with AutomaticKeepAliveClientMixin<ProfileMediaGrid> {
  late CursorPagingController<String, MediaGridItem> _paging;

  @override
  bool get wantKeepAlive => true;

  static const int pageSize = 20;

  /// Successive media pages overlap at their boundaries, so an entry already
  /// shown must not come round again.
  final Set<String> _seen = {};

  @override
  void initState() {
    super.initState();
    _paging = CursorPagingController<String, MediaGridItem>(_fetchPage);
  }

  @override
  void didUpdateWidget(covariant ProfileMediaGrid oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.filter != oldWidget.filter) {
      _paging.dispose();
      _seen.clear();
      _paging = CursorPagingController<String, MediaGridItem>(_fetchPage);
    }
  }

  @override
  void dispose() {
    _paging.dispose();
    super.dispose();
  }

  // Deliberately inert. The parser's sparse-page counter exists to stop
  // regex-filtered feeds paging forever, and it does that by nulling the
  // cursor after a handful of thin pages — but thin pages are the media tab's
  // normal condition (twenty text posts map to no media), and the lookahead
  // above this owns when to stop. Feeding the real counter here ended long
  // text-heavy profiles' grids early.
  void incrementLoadTweetsCounter() {}

  int getLoadTweetsCounter() {
    return 0;
  }

  Future<CursorPage<String, MediaGridItem>> _fetchPage(String? cursor) async {
    if (cursor == null) {
      _seen.clear();
    }

    return mediaPageWithLookahead(cursor, _chainsAfter, _unseenItems);
  }

  Future<ChainPage> _chainsAfter(String? cursor) async {
    var result = await Twitter.getTweets(
      widget.user.idStr!,
      'media',
      const [],
      cursor: cursor,
      count: pageSize,
      includeReplies: false,
      getTweetsCounter: getLoadTweetsCounter,
      incrementTweetsCounter: incrementLoadTweetsCounter,
    );

    final page = mediaPageFromStatus(result, cursor);
    return (chains: result.chains, nextCursor: page.nextCursor);
  }

  List<MediaGridItem> _unseenItems(List<TweetChain> chains) {
    final raw = mediaItemsFromChains(chains);
    if (raw.any((item) => item is BroadcastGridItem)) {
      widget.onBroadcastsFound?.call(true);
    }
    return raw
        .where(widget.filter.accepts)
        .where((m) => _seen.add('${m.tweetId}/${m.mediaIndex}'))
        .toList();
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    return SensitiveMediaGate(
      sensitive: widget.user.possiblySensitive ?? false,
      errorMessage: L10n.current.possibly_sensitive_profile,
      wrapInCard: false,
      child: MediaGrid(
        controller: _paging.pagingController,
        firstPageErrorPrefix: L10n.of(context).unable_to_load_the_tweets,
        newPageErrorPrefix: L10n.of(
          context,
        ).unable_to_load_the_next_page_of_tweets,
        emptyMessage: L10n.of(context).could_not_find_any_tweets_by_this_user,
      ),
    );
  }
}
