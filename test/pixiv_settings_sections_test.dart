import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_account.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_mute.dart';

import 'support/pixiv_reader_harness.dart';

void main() {
  testWidgets('settings are the account, content and mute sections in order', (tester) async {
    await pumpPixiv(tester, const PixivSettingsScreen(), size: const Size(390, 1400));

    final sections = [PixivAccountSettings, PixivContentSettings, PixivMuteSettings];
    final tops = [for (final type in sections) tester.getTopLeft(find.byType(type)).dy];
    expect(tops, orderedEquals([...tops]..sort()));
    expect(find.byKey(const ValueKey('pixiv-hide-ai')), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('muted comments and novels are listed and can be unmuted', (tester) async {
    await pumpPixiv(
      tester,
      const PixivSettingsScreen(),
      size: const Size(390, 1400),
      client: (prefs) {
        prefs.set(optionPluginPixivMutedComments, '[5]');
        prefs.set(optionPluginPixivMutedNovels, '[6]');
        return FakePixivClient(prefs);
      },
    );
    expect(find.widgetWithText(InputChip, '5'), findsOneWidget);
    expect(find.widgetWithText(InputChip, '6'), findsOneWidget);

    await tester.tap(find.descendant(of: find.widgetWithText(InputChip, '5'), matching: find.byTooltip('Unmute')));
    await settlePixiv(tester);
    final mute = Provider.of<PixivMuteStore>(tester.element(find.byType(PixivMuteSettings)), listen: false);
    expect(mute.state.novelIds, {6});
    expect(mute.state.commentIds, isEmpty);
    await disposePixiv(tester);
  });
}
