import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/mastodon/mastodon_discover_search.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/search/recent_searches_store.dart';
import 'support/mastodon_harness.dart';

void main() {
  test('recent searches are bounded, deduplicated and isolated by network', () async {
    final prefs = PrefServiceCache();
    final store = RecentSearchesStore(prefs);
    for (var i = 0; i < 12; i++) {
      await store.remember('mastodon', 'query-$i');
    }
    await store.remember('reddit', 'gardening');
    await store.remember('mastodon', 'QUERY-11');
    expect(store.state['mastodon']!.length, 10);
    expect(store.state['mastodon']!.first, 'QUERY-11');
    expect(store.state['reddit'], ['gardening']);
    await store.remove('mastodon', 'QUERY-11');
    await store.destroy();
    final reopened = RecentSearchesStore(prefs);
    expect(reopened.state['mastodon'], isNot(contains('QUERY-11')));
    expect(reopened.state['reddit'], ['gardening']);
    await reopened.destroy();
  });

  testWidgets('empty Mastodon Discover shows followed people and live topics', (tester) async {
    final h = MastodonHarness();
    await tester.pumpWidget(
      h.app(
        child: const Scaffold(body: MastodonDiscoverSearch(query: '')),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Maya Chen'), findsOneWidget);
    expect(find.text('#photography'), findsOneWidget);
    expect(h.client.searches, 0);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });

  testWidgets('inline search keeps its results after opening a profile', (tester) async {
    final h = MastodonHarness();
    await tester.pumpWidget(
      h.app(
        child: const Scaffold(body: MastodonDiscoverSearch(query: 'design')),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.byType(TextField), findsNothing);
    await tester.tap(find.text('Maya Chen'));
    await tester.pumpAndSettle();
    expect(find.byType(MastodonProfileScreen), findsOneWidget);
    await tester.pageBack();
    await tester.pumpAndSettle();
    expect(find.text('Maya Chen'), findsOneWidget);
    expect(h.client.searches, 1);
    expect(tester.takeException(), isNull);
    await h.close(tester);
  });
}
