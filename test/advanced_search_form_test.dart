import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/advanced_search.dart';
import 'package:xta/search/advanced_search_model.dart';
import 'package:xta/ui/x_look_theme.dart';

Future<void> _openForm(
  WidgetTester tester, {
  AdvancedSearchState initialState = const AdvancedSearchState(),
  required ValueChanged<AdvancedSearchState?> onResult,
  double textScale = 1,
}) async {
  await tester.pumpWidget(
    MaterialApp(
      theme: xLookLightTheme(null),
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
        GlobalCupertinoLocalizations.delegate,
      ],
      supportedLocales: L10n.delegate.supportedLocales,
      builder: (context, child) => MediaQuery(
        data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(textScale)),
        child: child!,
      ),
      home: Builder(
        builder: (context) => Scaffold(
          body: TextButton(
            onPressed: () async {
              final result = await Navigator.push<AdvancedSearchState>(
                context,
                MaterialPageRoute(builder: (_) => AdvancedSearchScreen(initialState: initialState)),
              );
              onResult(result);
            },
            child: const Text('Open'),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('Open'));
  await tester.pumpAndSettle();
}

Future<void> _reveal(WidgetTester tester, Finder finder) async {
  await tester.scrollUntilVisible(
    finder,
    200,
    scrollable: find.descendant(of: find.byType(ListView), matching: find.byType(Scrollable)).first,
  );
  await tester.pumpAndSettle();
}

void main() {
  testWidgets('content chips and exclusions apply on a narrow large-text form', (tester) async {
    tester.view.physicalSize = const Size(320, 720);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    AdvancedSearchState? result;
    await _openForm(
      tester,
      initialState: AdvancedSearchState.fromQuery('flutter filter:images'),
      textScale: 2,
      onResult: (state) => result = state,
    );
    final photos = find.byKey(const ValueKey(AdvancedSearchContentFilter.photos));
    await _reveal(tester, photos);
    expect(tester.widget<ChoiceChip>(photos).selected, isTrue);
    await tester.tap(photos);
    await tester.pumpAndSettle();
    expect(tester.widget<ChoiceChip>(photos).selected, isTrue);

    final videos = find.byKey(const ValueKey(AdvancedSearchContentFilter.videos));
    await _reveal(tester, videos);
    await tester.tap(videos);
    await tester.pumpAndSettle();
    expect(tester.widget<ChoiceChip>(videos).selected, isTrue);
    expect(tester.widget<ChoiceChip>(photos).selected, isFalse);

    for (final label in ['Hide replies', 'Hide retweets']) {
      await _reveal(tester, find.text(label));
      await tester.tap(find.text(label));
      await tester.pumpAndSettle();
    }
    expect(tester.takeException(), isNull);
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();
    expect(result?.query, 'flutter filter:videos -filter:replies -filter:retweets');
  });

  testWidgets('reset clears initial content and exclusion choices', (tester) async {
    AdvancedSearchState? result;
    await _openForm(
      tester,
      initialState: AdvancedSearchState(
        allWords: 'flutter',
        contentFilter: AdvancedSearchContentFilter.links,
        excludeReplies: true,
        excludeRetweets: true,
        since: DateTime(2026, 1, 1),
      ),
      onResult: (state) => result = state,
    );

    await tester.tap(find.byIcon(Icons.restart_alt));
    await tester.pumpAndSettle();
    expect(tester.widget<TextField>(find.byType(TextField).first).controller!.text, isEmpty);
    await tester.tap(find.byType(FilledButton));
    await tester.pumpAndSettle();

    expect(result, isNotNull);
    expect(result!.activeFilters, isEmpty);
    expect(result!.contentFilter, AdvancedSearchContentFilter.all);
    expect(result!.query, isEmpty);
  });

  testWidgets('dismissing edited content leaves the original search intact', (tester) async {
    const initial = AdvancedSearchState(allWords: 'flutter', onlyMedia: true);
    var dismissed = false;
    AdvancedSearchState? result;
    await _openForm(
      tester,
      initialState: initial,
      onResult: (state) {
        result = state;
        dismissed = true;
      },
    );
    final links = find.byKey(const ValueKey(AdvancedSearchContentFilter.links));
    await _reveal(tester, links);
    await tester.tap(links);
    await tester.pumpAndSettle();
    await tester.pageBack();
    await tester.pumpAndSettle();

    expect(dismissed, isTrue);
    expect(result, isNull);
    expect(initial.query, 'flutter filter:media');
  });
}
