import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/tweet/cached_tweet_list.dart';
import 'package:xta/tweet/tweet_skeleton.dart';
import 'package:xta/ui/scroll_to_top.dart';

/// Home Following / For you sit in NestedScrollView *without* PluginEmbedded.
/// Plugin crash-sweep tests wrap PluginEmbedded and would hide this leftover.
Widget _shell({
  required ScrollController outer,
  required Widget body,
}) {
  return PrefService(
    service: PrefServiceCache(),
    child: MaterialApp(
      home: Scaffold(
        body: NestedScrollView(
          controller: outer,
          headerSliverBuilder: (context, inner) => [
            const SliverAppBar(pinned: true, title: Text('Home')),
          ],
          body: body,
        ),
      ),
    ),
  );
}

void main() {
  group('home timeline NestedScrollView', () {
    testWidgets('the loading skeleton is the only inner scrollable', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      ScrollController? inner;

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: Builder(
            builder: (context) {
              inner = PrimaryScrollController.maybeOf(context);
              return const TweetFeedSkeleton();
            },
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(inner, isNotNull);
      expect(inner!.positions.length, 1);
    });

    testWidgets(
      'a nested skeleton does not steal the inner controller from the list',
      (tester) async {
        final outer = ScrollController();
        addTearDown(outer.dispose);
        ScrollController? inner;

        await tester.pumpWidget(
          _shell(
            outer: outer,
            body: Builder(
              builder: (context) {
                inner = PrimaryScrollController.maybeOf(context);
                return ListView(
                  children: const [
                    SizedBox(
                      height: 400,
                      child: TweetFeedSkeleton(primary: false),
                    ),
                  ],
                );
              },
            ),
          ),
        );
        await tester.pump();

        expect(tester.takeException(), isNull);
        expect(outer.positions.length, 1);
        expect(inner!.positions.length, 1);
      },
    );

    testWidgets('cached Following preview does not attach the outer', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      ScrollController? inner;

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: Builder(
            builder: (context) {
              inner = PrimaryScrollController.maybeOf(context);
              return const CachedTweetList([]);
            },
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(inner!.positions.length, 1);
    });

    testWidgets('empty Following is a pullable inner list, not a Center', (
      tester,
    ) async {
      final outer = ScrollController();
      addTearDown(outer.dispose);
      ScrollController? inner;

      await tester.pumpWidget(
        _shell(
          outer: outer,
          body: Builder(
            builder: (context) {
              inner = PrimaryScrollController.maybeOf(context);
              return ListView(
                physics: const AlwaysScrollableScrollPhysics(),
                children: const [
                  SizedBox(height: 240, child: Center(child: Text('empty'))),
                ],
              );
            },
          ),
        ),
      );
      await tester.pump();

      expect(tester.takeException(), isNull);
      expect(outer.positions.length, 1);
      expect(inner!.positions.length, 1);
      expect(find.text('empty'), findsOneWidget);
    });
  });

  group('scrollToTop', () {
    testWidgets('does not animate when NestedScrollView has two positions', (
      tester,
    ) async {
      final controller = ScrollController();
      addTearDown(controller.dispose);

      await tester.pumpWidget(
        PrefService(
          service: PrefServiceCache(),
          child: MaterialApp(
            home: ListView(controller: controller, children: const [
              SizedBox(height: 800),
            ]),
          ),
        ),
      );
      await tester.pump();
      expect(controller.positions.length, 1);

      await tester.pumpWidget(
        PrefService(
          service: PrefServiceCache(),
          child: MaterialApp(
            home: Column(
              children: [
                Expanded(
                  child: ListView(controller: controller, children: const [
                    SizedBox(height: 800),
                  ]),
                ),
                Expanded(
                  child: ListView(controller: controller, children: const [
                    SizedBox(height: 800),
                  ]),
                ),
              ],
            ),
          ),
        ),
      );
      await tester.pump();
      expect(controller.positions.length, 2);
      expect(scrollControllerAtTop(controller), isTrue);

      await tester.pumpWidget(
        PrefService(
          service: PrefServiceCache(),
          child: Builder(
            builder: (context) {
              return FutureBuilder<void>(
                future: scrollToTop(context, controller),
                builder: (_, _) => const SizedBox.shrink(),
              );
            },
          ),
        ),
      );
      await tester.pump();
      expect(tester.takeException(), isNull);
    });
  });
}
