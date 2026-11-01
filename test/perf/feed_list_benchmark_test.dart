import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/paginated_tweet_list.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/ui/x_look_theme.dart';

import '../support/feed_ink_harness.dart';

/// Host cost of one feed list build with a long history loaded, as the paging controller triggers it twice per
/// page (loading on, loading off). `flutter test` runs JIT with assertions on: these rank work, they are not phone
/// timings.
///
///   fvm flutter test test/perf/feed_list_benchmark_test.dart --dart-define=RUN_BENCH=true
const _skip = !bool.fromEnvironment('RUN_BENCH');

void main() {
  testWidgets('a list build with 600 posts loaded', skip: _skip, (tester) async {
    final feed = TweetFeedController();
    addTearDown(feed.dispose);
    Future<TweetPageResult> load(String? cursor) async => (
      chains: [
        for (var i = 0; i < 600; i++)
          TweetChain(
            id: 'c$i',
            tweets: [inkXPost(text: 'Post $i about https://example.com/article/$i and more')..idStr = 'p$i'],
            isPinned: false,
          ),
      ],
      nextCursor: null,
    );
    await pumpInk(
      tester,
      Scaffold(
        body: PaginatedTweetList(
          feed: feed,
          loadPage: load,
          username: null,
          firstPageErrorPrefix: 'first',
          newPageErrorPrefix: 'next',
          emptyMessage: 'empty',
        ),
      ),
      theme: xLookLightsOutTheme(null),
    );
    await tester.pumpAndSettle();
    expect(feed.items, hasLength(600));

    final samples = <double>[];
    var tiles = 0;
    debugOnRebuildDirtyWidget = (element, _) {
      if (element.widget is TweetTile) tiles++;
    };
    addTearDown(() => debugOnRebuildDirtyWidget = null);
    for (var i = 0; i < 43; i++) {
      final paging = feed.controller;
      paging.value = paging.value.copyWith(isLoading: !paging.value.isLoading);
      final watch = Stopwatch()..start();
      await tester.pump();
      watch.stop();
      if (i >= 3) samples.add(watch.elapsedMicroseconds / 1000);
    }
    debugOnRebuildDirtyWidget = null;
    // ignore: avoid_print
    print('BENCH tiles rebuilt per list build: ${(tiles / 43).toStringAsFixed(1)}');
    samples.sort();
    double at(double q) => samples[(q * (samples.length - 1)).round()];
    // ignore: avoid_print
    print(
      'BENCH list build, 600 posts: median ${at(0.5).toStringAsFixed(2)} ms, p95 ${at(0.95).toStringAsFixed(2)} ms',
    );
  });
}
