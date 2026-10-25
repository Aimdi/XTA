import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/progressive_feed_store.dart';
import 'package:xta/tweet/progressive_feed_view.dart';
import 'package:xta/ui/floating_notice.dart';

Future<ProgressiveFeedStore> _pump(
  WidgetTester tester,
  Map<String, FeedSourceState> sources, {
  Map<String, List<InterleavedItem>>? pending,
}) async {
  final store = ProgressiveFeedStore();
  store.update(ProgressiveFeedState(sources: sources, pending: pending));
  await tester.pumpWidget(
    MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      locale: const Locale('en'),
      home: Scaffold(
        // A feed is a list as wide as the screen, as in the app.
        body: ProgressiveFeedView(
          store: store,
          builder: (_) => ListView(children: const [Text('posts')]),
        ),
      ),
    ),
  );
  await tester.pump();
  return store;
}

void main() {
  testWidgets('a loading source takes a hairline over the posts, not a row', (tester) async {
    final store = await _pump(tester, {'substack': const FeedSourceState(loading: true)});
    expect(find.byType(ActionChip), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(tester.getSize(find.byType(LinearProgressIndicator)).height, 2);
    expect(tester.getTopLeft(find.byType(LinearProgressIndicator)).dy, 0);
    // The posts start where they would with nothing loading.
    expect(tester.getTopLeft(find.text('posts')).dy, 0);
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });

  testWidgets('a failed or cached source still gets a chip to act on', (tester) async {
    final store = await _pump(tester, {
      'substack': FeedSourceState(error: Exception('offline')),
      'reddit': FeedSourceState(cachedAt: DateTime(2026)),
    });
    expect(find.byType(ActionChip), findsNWidgets(2));
    expect(find.byType(LinearProgressIndicator), findsNothing);
    await tester.pumpAndSettle();
    // The chips float over the posts rather than pushing them down.
    expect(tester.getTopLeft(find.text('posts')).dy, 0);
    expect(tester.getTopLeft(find.byType(ActionChip).first).dy, greaterThan(0));
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });

  testWidgets('new posts float over the feed in a pill, above the chips, without moving the posts', (tester) async {
    final store = await _pump(tester, {'substack': FeedSourceState(error: Exception('offline'))}, pending: {'x': []});
    await tester.pumpAndSettle();
    final pill = find.ancestor(of: find.text('New posts'), matching: find.byType(NoticePill));
    expect(pill, findsOneWidget);
    expect(tester.getTopLeft(find.text('posts')).dy, 0);
    expect(tester.getRect(pill).top, 8);
    expect(tester.getTopLeft(find.byType(ActionChip)).dy, greaterThan(tester.getRect(pill).bottom));
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });

  testWidgets('tapping new posts shows them, and the pill lifts away', (tester) async {
    final store = await _pump(tester, const {}, pending: {'x': []});
    await tester.pumpAndSettle();
    await tester.tap(find.text('New posts'));
    expect(store.state.hasPending, isFalse);
    await tester.pumpAndSettle();
    expect(find.text('New posts'), findsNothing);
    expect(tester.getTopLeft(find.text('posts')).dy, 0);
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });
}
