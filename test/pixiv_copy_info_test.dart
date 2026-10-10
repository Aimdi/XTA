import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_copy_info.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

import 'support/pixiv_reader_harness.dart';

void main() {
  final work = pixivWork(
    id: 120,
    title: 'Sommerfest',
    tags: const [
      PixivTag(name: 'cat'),
      PixivTag(name: '夏'),
    ],
  );

  group('pixivCopyInfo', () {
    test('fills every placeholder', () {
      expect(pixivCopyInfo('{title}|{illust_id}|{user_id}|{user_name}|{tags}', work), 'Sommerfest|120|42|Mika|#cat #夏');
      expect(pixivCopyInfo(pixivCopyArtworkUrl, work), 'https://www.pixiv.net/artworks/120');
      expect(pixivCopyInfo(pixivCopyUserUrl, work), 'https://www.pixiv.net/users/42');
    });

    test('leaves unknown braces and a title that looks like a placeholder as written', () {
      final tricky = pixivWork(title: '{user_name}');
      expect(pixivCopyInfo('{title} by {user_name} {page}', tricky), '{user_name} by Mika {page}');
    });

    test('the default template names title, artist and work id', () async {
      final l10n = await L10n.load(const Locale('en'));
      expect(pixivCopyInfo(pixivDefaultCopyTemplate(l10n), work), 'Title: Sommerfest\nArtist: Mika\nWork ID: 120');
    });
  });

  testWidgets('the editor inserts placeholders, saves as it goes and resets to the default', (tester) async {
    final harness = await pumpPixiv(tester, const PixivCopyTemplateScreen());
    final field = find.byKey(const ValueKey('pixiv-copy-template'));
    expect(tester.widget<TextField>(field).controller!.text, startsWith('Title: {title}'));

    await tester.enterText(field, 'ID ');
    await tester.tap(find.text('{illust_id}'));
    await tester.pump();
    expect(harness.prefs.get<String>(optionPluginPixivCopyTemplate), 'ID {illust_id}');

    await tester.tap(find.text('Work link'));
    await tester.pump();
    expect(harness.prefs.get<String>(optionPluginPixivCopyTemplate), 'ID {illust_id}$pixivCopyArtworkUrl');

    await tester.tap(find.byTooltip('Reset to default'));
    await tester.pump();
    expect(harness.prefs.get<String>(optionPluginPixivCopyTemplate), '');
    expect(tester.widget<TextField>(field).controller!.text, startsWith('Title: {title}'));
    await disposePixiv(tester);
  });

  testWidgets('Copy info puts the filled template on the clipboard', (tester) async {
    final copied = <String>[];
    tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
      if (call.method == 'Clipboard.setData') copied.add((call.arguments as Map)['text'] as String);
      return null;
    });
    addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
    final harness = await pumpPixiv(
      tester,
      Scaffold(
        body: Builder(
          builder: (context) => TextButton(onPressed: () => copyPixivInfo(context, work), child: const Text('copy')),
        ),
      ),
    );
    await harness.prefs.set(optionPluginPixivCopyTemplate, '{title} {illust_id}');
    await tester.tap(find.text('copy'));
    await tester.pump();
    expect(copied, ['Sommerfest 120']);
    expect(find.text('Info copied'), findsOneWidget);
    await disposePixiv(tester);
  });
}
