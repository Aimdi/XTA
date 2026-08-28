import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/home/feed_strip_add_sheet.dart';
import 'package:xta/home/feed_strip_store.dart';

Widget _app(Widget child, {required BasePrefService prefs}) {
  return PrefService(
    service: prefs,
    child: Provider(
      create: (_) => FeedStripStore(prefs),
      child: MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        locale: const Locale('en'),
        home: Scaffold(body: child),
      ),
    ),
  );
}

void main() {
  testWidgets('the plus sheet can add, remove and reorder plugin timelines', (
    tester,
  ) async {
    final prefs = PrefServiceCache(
      cache: {
        optionHomeFeedStripPlugins: [pluginIdReddit, pluginIdMastodon],
        optionPluginRedditEnabled: true,
        optionPluginMastodonEnabled: true,
        optionPluginPixivEnabled: true,
      },
    );

    await tester.pumpWidget(
      _app(
        Builder(
          builder: (context) => TextButton(
            onPressed: () => showFeedStripAddSheet(context),
            child: const Text('open'),
          ),
        ),
        prefs: prefs,
      ),
    );
    await tester.pumpAndSettle();
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();

    expect(find.text('Plugin timelines'), findsOneWidget);
    expect(find.text('Reddit'), findsOneWidget);
    expect(find.text('Mastodon'), findsOneWidget);
    expect(find.byIcon(Icons.drag_handle), findsNWidgets(2));
    expect(find.byTooltip('Remove from strip'), findsNWidgets(2));
    expect(find.byTooltip('Reorder'), findsNWidgets(2));
    expect(find.text('Pixiv'), findsOneWidget);
    expect(find.byTooltip('Add timeline'), findsWidgets);

    await tester.tap(find.byTooltip('Add timeline').last);
    await tester.pumpAndSettle();
    expect(prefs.getStringList(optionHomeFeedStripPlugins), [
      pluginIdReddit,
      pluginIdMastodon,
      pluginIdPixiv,
    ]);
    expect(find.text('Plugin timelines'), findsNothing);

    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
    expect(find.byIcon(Icons.drag_handle), findsNWidgets(3));

    await tester.tap(find.byTooltip('Remove from strip').first);
    await tester.pumpAndSettle();
    expect(
      prefs.getStringList(optionHomeFeedStripPlugins),
      isNot(contains(pluginIdReddit)),
    );
  });
}
