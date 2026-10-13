import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_prompt.dart';
import 'package:xta/plugins/booru/booru_prompt_settings.dart';

Future<void> _pumpSettings(WidgetTester tester, PrefServiceCache prefs) => tester.pumpWidget(
  PrefService(
    service: prefs,
    child: MaterialApp(
      localizationsDelegates: const [
        L10n.delegate,
        GlobalMaterialLocalizations.delegate,
        GlobalWidgetsLocalizations.delegate,
      ],
      home: const Scaffold(body: SingleChildScrollView(child: BooruPromptSettings())),
    ),
  ),
);

void main() {
  test('a kind moves where it is dropped, and a switch keeps or leaves it out', () {
    final kinds = booruPromptKinds(null);

    final moved = booruPromptMoved(kinds, 4, 0);
    expect(moved.first.category, BooruTagCategory.general);
    expect([for (final kind in booruPromptMoved(kinds, 0, 2)) kind.category].take(3), const [
      BooruTagCategory.copyright,
      BooruTagCategory.artist,
      BooruTagCategory.character,
    ]);

    final toggled = booruPromptToggled(kinds, BooruTagCategory.meta, true);
    expect(toggled.last, (category: BooruTagCategory.meta, included: true));
  });

  testWidgets('switching a kind on in settings keeps it in the prompt', (tester) async {
    final prefs = PrefServiceCache();
    await _pumpSettings(tester, prefs);

    await tester.tap(find.byKey(const ValueKey('booru-prompt-meta')));
    await tester.pumpAndSettle();

    expect(booruPromptOrder(prefs), [...booruPromptDefaultOrder, BooruTagCategory.meta]);
    expect(prefs.get<String>(optionPluginBooruPromptKinds), 'character,copyright,artist,species,general,meta');
  });

  testWidgets('dragging a kind by its handle moves it in the prompt', (tester) async {
    final prefs = PrefServiceCache();
    await _pumpSettings(tester, prefs);
    final handle = find.descendant(
      of: find.byKey(const ValueKey('booru-prompt-general')),
      matching: find.byIcon(Icons.drag_handle),
    );

    final gesture = await tester.startGesture(tester.getCenter(handle));
    await tester.pump();
    await gesture.moveBy(const Offset(0, -600));
    await tester.pump();
    await gesture.up();
    await tester.pumpAndSettle();

    expect(booruPromptOrder(prefs).first, BooruTagCategory.general);
  });
}
