import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/ui/reading_column.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/feed_ink_harness.dart';

void main() {
  group('readingColumnPadding', () {
    const base = EdgeInsets.only(top: 4, bottom: 8);

    test('a phone or a narrow deck column keeps its padding', () {
      expect(readingColumnPadding(390, base), base);
      expect(readingColumnPadding(kReadingColumnMaxWidth, base), base);
    });

    test('a wide window centres a column of the maximum width', () {
      final padding = readingColumnPadding(1892, base);
      expect(padding.left, padding.right);
      expect(1892 - padding.horizontal, kReadingColumnMaxWidth);
      expect(padding.top, 4);
      expect(padding.bottom, 8);
    });

    test('existing side padding is kept on top of the margins', () {
      final padding = readingColumnPadding(1000, const EdgeInsets.symmetric(horizontal: 16));
      expect(padding.left, 16 + (1000 - kReadingColumnMaxWidth) / 2);
    });

    test('an unbounded width leaves the padding alone', () {
      expect(readingColumnPadding(double.infinity, base), base);
    });
  });

  testWidgets('a feed list on a wide window keeps posts in the column but scrolls across it', (tester) async {
    tester.view.physicalSize = const Size(1920, 1080);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FeedListView(
            itemCount: 30,
            itemBuilder: (context, index) => SizedBox(height: 80, child: Text('post $index')),
          ),
        ),
      ),
    );

    final post = tester.getRect(find.ancestor(of: find.text('post 0'), matching: find.byType(SizedBox)).first);
    expect(post.width, kReadingColumnMaxWidth);
    expect(post.center.dx, closeTo(960, 0.5));
    expect(tester.getSize(find.byType(ListView)).width, 1920);
  });

  // The home header slides away over 180 ms and changes the feed's height on every frame of it. The posts in the
  // feed do not change, so none of them should be built again.
  testWidgets('a height change builds none of the posts in a feed again', (tester) async {
    final feed = TweetFeedController();
    addTearDown(feed.dispose);
    Future<TweetPageResult> load(String? cursor) async => (
      chains: [
        for (var i = 0; i < 8; i++) TweetChain(id: 'c$i', tweets: [inkXPost()..idStr = 'p$i'], isPinned: false),
      ],
      nextCursor: null,
    );
    final header = ValueNotifier<double>(48);
    addTearDown(header.dispose);
    // Built once, as the feed under the home header is: the slide changes its height, not the widget.
    final list = PaginatedTweetList(
      feed: feed,
      loadPage: load,
      username: null,
      firstPageErrorPrefix: 'first',
      newPageErrorPrefix: 'next',
      emptyMessage: 'empty',
    );

    await pumpInk(
      tester,
      Scaffold(
        body: ValueListenableBuilder<double>(
          valueListenable: header,
          builder: (_, height, child) => Column(
            children: [
              SizedBox(height: height),
              Expanded(child: child!),
            ],
          ),
          child: list,
        ),
      ),
      theme: xLookLightsOutTheme(null),
    );
    await tester.pumpAndSettle();
    expect(find.byType(TweetTile), findsWidgets);

    var rebuilt = 0;
    debugOnRebuildDirtyWidget = (element, _) {
      if (element.widget is TweetTile) rebuilt++;
    };
    addTearDown(() => debugOnRebuildDirtyWidget = null);
    for (var frame = 1; frame <= 12; frame++) {
      header.value = 48 - frame * 4;
      await tester.pump(const Duration(milliseconds: 16));
    }
    debugOnRebuildDirtyWidget = null;
    expect(rebuilt, 0);
  });
}
