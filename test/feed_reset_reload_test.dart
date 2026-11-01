import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/profile/media_grid/media_grid.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/utils/paging.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/feed_ink_harness.dart';

/// A list that draws its own placeholders instead of the package's list has to ask for the first page itself,
/// again after every reset (a new search, sort or set of members, a pull to refresh), and not after a read
/// that was only cancelled, as a hidden feed's is.
void main() {
  final theme = xLookLightsOutTheme(null);

  Widget list(TweetFeedController feed, TweetPageLoader load) => Scaffold(
    body: PaginatedTweetList(
      feed: feed,
      loadPage: load,
      username: null,
      firstPageErrorPrefix: 'first',
      newPageErrorPrefix: 'next',
      emptyMessage: 'empty',
    ),
  );

  testWidgets('a feed whose paging controller is reset loads its first page again', (tester) async {
    var loads = 0;
    final feed = TweetFeedController();
    addTearDown(feed.dispose);
    Future<TweetPageResult> load(String? cursor) async {
      loads++;
      return (
        chains: [
          TweetChain(id: 'c$loads', tweets: [inkXPost()..idStr = 'p$loads'], isPinned: false),
        ],
        nextCursor: null,
      );
    }

    await pumpInk(tester, list(feed, load), theme: theme);
    await tester.pumpAndSettle();
    expect(loads, 1);

    feed.controller.refresh();
    await tester.pumpAndSettle();
    expect(loads, 2, reason: 'a reset controller must start its first page again');
    expect(feed.items?.single.id, 'c2');
  });

  testWidgets('a first page that was only cancelled is not started again', (tester) async {
    var loads = 0;
    final pending = Completer<TweetPageResult>();
    final feed = TweetFeedController();
    addTearDown(feed.dispose);
    Future<TweetPageResult> load(String? cursor) {
      loads++;
      return pending.future;
    }

    await pumpInk(tester, list(feed, load), theme: theme);
    await tester.pump();
    expect(loads, 1);

    feed.controller.cancel();
    await tester.pump();
    await tester.pump();
    expect(loads, 1, reason: 'a hidden feed cancels its read and starts it itself once it is seen');
  });

  testWidgets('a media grid pulled to refresh loads its first page again', (tester) async {
    var loads = 0;
    final paging = CursorPagingController<String, MediaGridItem>((cursor) async {
      loads++;
      return (items: const <MediaGridItem>[], nextCursor: null);
    });
    addTearDown(paging.dispose);

    await pumpInk(
      tester,
      Scaffold(
        body: MediaGrid(
          controller: paging.pagingController,
          firstPageErrorPrefix: 'first',
          newPageErrorPrefix: 'next',
          emptyMessage: 'empty',
        ),
      ),
      theme: theme,
    );
    await tester.pumpAndSettle();
    expect(loads, 1);

    // Not awaited: the indicator only finishes as frames are pumped.
    unawaited(tester.state<RefreshIndicatorState>(find.byType(RefreshIndicator)).show());
    await tester.pumpAndSettle();
    expect(loads, 2, reason: 'pull to refresh used to leave the placeholders up for good');
    expect(find.text('empty'), findsOneWidget);
  });
}
