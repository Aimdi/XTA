import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_compose.dart';
import 'package:xta/saved/local_post_tile.dart';
import 'package:xta/ui/x_look_theme.dart';
import 'package:xta/utils/local_json_store.dart';

class _DraftStorage implements JsonStore {
  final values = <String, Object?>{};
  @override
  Future<Object?> read(String key) async => values[key];
  @override
  Future<void> write(String key, Object? value) async {
    values[key] = value;
  }

  @override
  Future<void> remove(String key) async {
    values.remove(key);
  }

  @override
  Future<Map<String, Object?>> readPrefix(String prefix) async => {
    for (final entry in values.entries)
      if (entry.key.startsWith(prefix)) entry.key: entry.value,
  };
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-Regular.ttf'))).load();
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  for (final variant in ['timeline', 'timeline-dark', 'timeline-rtl', 'compose', 'compose-dark']) {
    testWidgets('post-style notes $variant', (tester) async {
      tester.view.devicePixelRatio = 1;
      tester.view.physicalSize = const Size(390, 844);
      addTearDown(tester.view.resetDevicePixelRatio);
      addTearDown(tester.view.resetPhysicalSize);
      final prefs = PrefServiceCache(defaults: {optionUseAbsoluteTimestamp: true, optionDisableAnimations: true});
      final post = LocalPost(
        id: 'sample',
        body: 'A thought worth keeping.\n\nThe best ideas often start with a small observation.',
        createdAt: DateTime(2026, 9, 15, 10),
        updatedAt: DateTime(2026, 9, 15, 10),
      );
      var replies = 0;
      var edits = 0;
      final composing = variant.startsWith('compose');
      await tester.pumpWidget(
        PrefService(
          service: prefs,
          child: RepaintBoundary(
            key: const ValueKey('note-render'),
            child: MaterialApp(
              debugShowCheckedModeBanner: false,
              theme: variant.endsWith('dark') ? xLookLightsOutTheme(null) : xLookLightTheme(null),
              localizationsDelegates: const [
                L10n.delegate,
                GlobalMaterialLocalizations.delegate,
                GlobalWidgetsLocalizations.delegate,
                GlobalCupertinoLocalizations.delegate,
              ],
              supportedLocales: const [Locale('en')],
              builder: (context, child) => MediaQuery(
                data: MediaQuery.of(
                  context,
                ).copyWith(disableAnimations: true, textScaler: TextScaler.linear(variant.endsWith('rtl') ? 1.8 : 1)),
                child: Directionality(
                  textDirection: variant.endsWith('rtl') ? TextDirection.rtl : TextDirection.ltr,
                  child: child!,
                ),
              ),
              home: composing
                  ? LocalPostComposeSheet(existing: post, draftStorage: _DraftStorage())
                  : Scaffold(
                      appBar: AppBar(title: const Text('Notes')),
                      body: ListView(
                        children: [
                          LocalPostTile(
                            post: post,
                            compact: true,
                            onEdit: () => edits++,
                            onDelete: () {},
                            onReply: () => replies++,
                          ),
                        ],
                      ),
                    ),
            ),
          ),
        ),
      );
      await tester.pumpAndSettle();
      expect(tester.takeException(), isNull);
      await expectLater(
        find.byKey(const ValueKey('note-render')),
        matchesGoldenFile('../review-artifacts/renders/note-$variant.png'),
      );
      if (!composing) {
        await tester.tap(find.byTooltip('Reply'));
        await tester.tap(find.byTooltip('Edit note'));
        expect(replies, 1);
        expect(edits, 1);
      }
      await tester.pumpWidget(const SizedBox());
      await tester.pumpAndSettle();
    });
  }
}
