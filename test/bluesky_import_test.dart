import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_import_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';

Widget _app() {
  final client = BlueskyClient();
  final accounts = BlueskyAccountsStore();

  return MultiProvider(
    providers: [
      Provider<BlueskyClient>.value(value: client),
      Provider<BlueskyAccountsStore>.value(value: accounts),
      Provider<BlueskyFeedStore>.value(value: BlueskyFeedStore(client, accounts)),
    ],
    child: const MaterialApp(
      localizationsDelegates: [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      home: BlueskyImportFollowsScreen(),
    ),
  );
}

void main() {
  group('the handle the import accepts', () {
    // The screen rejects exactly what normaliseBlueskyHandle rejects; these
    // pin down which inputs a reader may type, since a rejected one now has to
    // say so rather than doing nothing.
    test('a bare name is not a Bluesky handle', () {
      expect(normaliseBlueskyHandle('elios'), isNull);
    });

    test('a dotted handle is', () {
      expect(normaliseBlueskyHandle('@alice.bsky.social'), 'alice.bsky.social');
    });

    test('so is a DID', () {
      expect(normaliseBlueskyHandle('did:plc:abc234'), 'did:plc:abc234');
    });

    test('so is a pasted profile URL', () {
      expect(normaliseBlueskyHandle('https://bsky.app/profile/alice.bsky.social'), 'alice.bsky.social');
    });

    test('nothing typed is nothing', () {
      expect(normaliseBlueskyHandle('   '), isNull);
    });
  });

  group('BlueskyImportFollowsScreen', () {
    testWidgets('a handle it cannot read is said so, not ignored', (tester) async {
      await tester.pumpWidget(_app());
      await tester.pumpAndSettle();

      await tester.enterText(find.byType(TextField), 'elios');
      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();

      expect(find.text(L10n.current.plugin_bluesky_invalid_handle), findsOneWidget);
    });

    testWidgets('correcting the field clears the complaint', (tester) async {
      await tester.pumpWidget(_app());
      await tester.pumpAndSettle();

      await tester.enterText(find.byType(TextField), 'elios');
      await tester.tap(find.byType(FloatingActionButton));
      await tester.pumpAndSettle();
      await tester.enterText(find.byType(TextField), 'elios.bsky.social');
      await tester.pumpAndSettle();

      expect(find.text(L10n.current.plugin_bluesky_invalid_handle), findsNothing);
    });
  });
}
