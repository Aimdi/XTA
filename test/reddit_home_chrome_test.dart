import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_home_source.dart';
import 'package:xta/plugins/reddit/reddit_screen.dart';

Widget _app(Widget child) {
  return MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    home: Scaffold(body: child),
  );
}

void main() {
  testWidgets('Reddit chrome is icon tabs, not a titled AppBar', (
    tester,
  ) async {
    var mode = RedditFeedMode.following;
    await tester.pumpWidget(
      _app(
        RedditHomeChrome(
          source: RedditHomeSource(mode: mode),
          onMode: (next) => mode = next,
          actions: [
            IconButton(
              tooltip: 'Saved',
              icon: const Icon(Icons.bookmark_border),
              onPressed: () {},
            ),
          ],
        ),
      ),
    );

    expect(find.byType(PluginHomeChrome), findsOneWidget);
    expect(find.text('Reddit'), findsNothing);
    expect(find.byIcon(Icons.home_outlined), findsOneWidget);
    expect(find.byIcon(Icons.whatshot_outlined), findsOneWidget);
    expect(find.byIcon(Icons.public_outlined), findsOneWidget);
    expect(find.byTooltip('Saved'), findsOneWidget);

    await tester.tap(find.byIcon(Icons.whatshot_outlined));
    expect(mode, RedditFeedMode.popular);
  });

  testWidgets('embedded Reddit chrome skips a second SafeArea', (tester) async {
    await tester.pumpWidget(
      _app(
        PluginEmbedded(
          child: RedditHomeChrome(
            source: const RedditHomeSource(mode: RedditFeedMode.all),
            onMode: (_) {},
          ),
        ),
      ),
    );

    expect(find.byType(SafeArea), findsNothing);
    expect(find.byIcon(Icons.public_outlined), findsOneWidget);
  });

  testWidgets('a followed community deselects Home/Popular/All', (
    tester,
  ) async {
    const source = RedditHomeSource(
      mode: RedditFeedMode.following,
      subreddit: 'foo',
    );
    expect(redditHomeRailSelected(source, RedditFeedMode.following), isFalse);
    expect(redditHomeRailSelected(source, RedditFeedMode.popular), isFalse);
    expect(redditHomeRailSelected(source, RedditFeedMode.all), isFalse);

    await tester.pumpWidget(
      _app(RedditHomeChrome(source: source, onMode: (_) {})),
    );

    expect(find.byType(PluginHomeChrome), findsOneWidget);
  });
}
