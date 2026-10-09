import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/progressive_feed_store.dart';
import 'package:xta/tweet/progressive_feed_view.dart';

Future<ProgressiveFeedStore> _pump(WidgetTester tester, Map<String, FeedSourceState> sources) async {
  final store = ProgressiveFeedStore();
  store.update(ProgressiveFeedState(sources: sources));
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
        body: ProgressiveFeedView(store: store, builder: (_) => const Text('posts')),
      ),
    ),
  );
  await tester.pump();
  return store;
}

void main() {
  testWidgets('a loading source takes a hairline, not a chip row', (tester) async {
    final store = await _pump(tester, {'substack': const FeedSourceState(loading: true)});
    expect(find.byType(ActionChip), findsNothing);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(tester.getSize(find.byType(LinearProgressIndicator)).height, 2);
    expect(tester.getTopLeft(find.text('posts')).dy, lessThanOrEqualTo(2));
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
    await tester.pumpWidget(const SizedBox());
    await store.destroy();
  });
}
