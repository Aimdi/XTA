import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/feed_ink_harness.dart';

/// The feed list builds again on every loading flag, group batch and notice. The posts it shows are built again
/// only when they or a setting changed.
void main() {
  late TweetFeedController feed;
  late List<TweetChain> page;

  List<TweetChain> posts() => [
    for (var i = 0; i < 6; i++) TweetChain(id: 'c$i', tweets: [inkXPost()..idStr = 'p$i'], isPinned: false),
  ];

  Future<void> pumpFeed(WidgetTester tester) async {
    feed = TweetFeedController();
    addTearDown(feed.dispose);
    page = posts();
    await pumpInk(
      tester,
      Scaffold(
        body: PaginatedTweetList(
          feed: feed,
          loadPage: (_) async => (chains: page, nextCursor: null),
          username: null,
          firstPageErrorPrefix: 'first',
          newPageErrorPrefix: 'next',
          emptyMessage: 'empty',
        ),
      ),
      theme: xLookLightsOutTheme(null),
    );
    await tester.pumpAndSettle();
  }

  /// How many posts the list builds again while [change] runs and a frame is drawn.
  Future<int> rebuiltBy(WidgetTester tester, void Function() change) async {
    var rebuilt = 0;
    debugOnRebuildDirtyWidget = (element, _) {
      if (element.widget is TweetTile) rebuilt++;
    };
    addTearDown(() => debugOnRebuildDirtyWidget = null);
    change();
    await tester.pump();
    debugOnRebuildDirtyWidget = null;
    return rebuilt;
  }

  void flipLoading() =>
      feed.controller.value = feed.controller.value.copyWith(isLoading: !feed.controller.value.isLoading);

  testWidgets('a list build that changes no post builds none of them again', (tester) async {
    await pumpFeed(tester);
    expect(await rebuiltBy(tester, flipLoading), 0);
  });

  testWidgets('a setting changing builds the posts on screen again', (tester) async {
    await pumpFeed(tester);
    final prefs = PrefService.of(tester.element(find.byType(PaginatedTweetList)), listen: false);

    final rebuilt = await rebuiltBy(tester, () {
      prefs.set(optionUseAbsoluteTimestamp, true);
      flipLoading();
    });
    expect(rebuilt, greaterThan(0), reason: 'a setting must land without a restart');
  });

  testWidgets('a refresh that brings new copies of the posts builds them again', (tester) async {
    await pumpFeed(tester);
    page = posts();

    final rebuilt = await rebuiltBy(tester, () => feed.softRefresh());
    await tester.pumpAndSettle();
    expect(rebuilt + await rebuiltBy(tester, () {}), greaterThan(0));
    expect(feed.items!.first, same(page.first));
  });
}
