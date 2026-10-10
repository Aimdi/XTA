import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_download_naming.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_downloads.dart';

import 'support/pixiv_reader_harness.dart';

Widget _section() => const Scaffold(
  body: SingleChildScrollView(padding: EdgeInsets.all(16), child: PixivDownloadSettings()),
);

Finder get _field => find.byKey(const ValueKey('pixiv-file-name-field'));

String _fieldText(WidgetTester tester) => tester.widget<TextField>(_field).controller!.text;

Future<void> _openEditor(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-file-name')));
  await settlePixiv(tester);
}

void main() {
  testWidgets('the editor inserts details at the cursor and previews the name', (tester) async {
    await pumpPixiv(tester, _section(), size: const Size(390, 1000));
    expect(find.text(pixivFileNameTemplateDefault), findsOneWidget);
    await _openEditor(tester);

    expect(_fieldText(tester), '{illust_id}_p{part}');
    expect(find.text('Preview: 104812345_p0.png'), findsOneWidget);

    tester.testTextInput.updateEditingValue(
      const TextEditingValue(text: '{illust_id}_p{part}', selection: TextSelection.collapsed(offset: 0)),
    );
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('pixiv-file-name-insert-userName')));
    await tester.pump();

    expect(_fieldText(tester), '{user_name}{illust_id}_p{part}');
    expect(find.text('Preview: Artist name104812345_p0.png'), findsOneWidget);
    expect(tester.widget<ActionChip>(find.byKey(const ValueKey('pixiv-file-name-insert-title'))).tooltip, '{title}');
    await disposePixiv(tester);
  });

  testWidgets('a template without {part} cannot be saved, and Reset brings the default back', (tester) async {
    final harness = await pumpPixiv(tester, _section(), size: const Size(390, 1000));
    await _openEditor(tester);

    await tester.enterText(_field, '{title}');
    await tester.pump();
    expect(find.text('Include {part} so each page of a work gets its own name'), findsOneWidget);
    final save = find.byKey(const ValueKey('pixiv-file-name-save'));
    expect(tester.widget<FilledButton>(save).onPressed, isNull);

    await tester.tap(find.text('Reset'));
    await tester.pump();
    expect(_fieldText(tester), pixivFileNameTemplateDefault);
    expect(tester.widget<FilledButton>(save).onPressed, isNotNull);
    expect(harness.prefs.get<String>(optionPluginPixivFileNameTemplate), isNot('{title}'));
    await disposePixiv(tester);
  });

  testWidgets('saving stores the template and the setting shows it; separators cannot be typed', (tester) async {
    final harness = await pumpPixiv(tester, _section(), size: const Size(390, 1000));
    await _openEditor(tester);

    await tester.enterText(_field, '{user_name}/{part}');
    await tester.pump();
    expect(_fieldText(tester), '{user_name}{part}');
    await tester.tap(find.byKey(const ValueKey('pixiv-file-name-save')));
    await settlePixiv(tester);

    expect(harness.prefs.get<String>(optionPluginPixivFileNameTemplate), '{user_name}{part}');
    expect(find.text('{user_name}{part}'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('the folder switches store their settings', (tester) async {
    final harness = await pumpPixiv(tester, _section(), size: const Size(390, 1000));

    await tester.tap(find.byKey(const ValueKey('pixiv-folder-per-artist')));
    await tester.pump();
    await tester.tap(find.byKey(const ValueKey('pixiv-folder-r18')));
    await tester.pump();

    expect(harness.prefs.get<bool>(optionPluginPixivFolderPerArtist), isTrue);
    expect(harness.prefs.get<bool>(optionPluginPixivFolderR18), isTrue);
    expect(find.text('Folder per artist'), findsOneWidget);
    expect(find.text('Separate folder for R-18'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('the saved pages count can be forgotten after confirming', (tester) async {
    final harness = await pumpPixiv(
      tester,
      _section(),
      size: const Size(390, 1000),
      client: (prefs) {
        prefs.set(optionPluginPixivDownloadIndex, '["1_p0","1_p1"]');
        return FakePixivClient(prefs);
      },
    );
    expect(find.textContaining('2 pages remembered'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('pixiv-download-index-forget')));
    await settlePixiv(tester);
    await tester.tap(find.widgetWithText(FilledButton, 'Forget'));
    await settlePixiv(tester);

    expect(find.textContaining('No pages remembered yet'), findsOneWidget);
    expect(harness.downloads.state, isEmpty);
    expect(harness.prefs.get<String>(optionPluginPixivDownloadIndex), '[]');
    final forget = find.byKey(const ValueKey('pixiv-download-index-forget'));
    expect(tester.widget<TextButton>(forget).onPressed, isNull);
    await disposePixiv(tester);
  });

  testWidgets('the section reads at twice the text size on a narrow screen', (tester) async {
    await pumpPixiv(tester, _section(), size: const Size(320, 1400), textScale: 2);
    expect(tester.takeException(), isNull);
    await _openEditor(tester);
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('pixiv-file-name-insert-part')), findsOneWidget);
    await disposePixiv(tester);
  });
}
