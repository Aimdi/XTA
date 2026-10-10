import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_account.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_mute.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_viewing.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';

import 'support/pixiv_reader_harness.dart';

void main() {
  testWidgets('settings are the account, content, viewing and mute sections in order', (tester) async {
    await pumpPixiv(tester, const PixivSettingsScreen(), size: const Size(390, 2400));

    final sections = [PixivAccountSettings, PixivContentSettings, PixivViewingSettings, PixivMuteSettings];
    final tops = [for (final type in sections) tester.getTopLeft(find.byType(type)).dy];
    expect(tops, orderedEquals([...tops]..sort()));
    expect(find.byKey(const ValueKey('pixiv-hide-ai')), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('muted comments and novels are listed and can be unmuted', (tester) async {
    await pumpPixiv(
      tester,
      const PixivSettingsScreen(),
      size: const Size(390, 2400),
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

  group('account', () {
    Finder tokenField() => find.descendant(of: find.byType(PixivAccountSettings), matching: find.byType(TextField));

    testWidgets('pasting or clearing a refresh token flips Sign in and Sign out at once', (tester) async {
      await pumpPixiv(
        tester,
        const PixivSettingsScreen(),
        size: const Size(390, 1400),
        client: (prefs) {
          prefs.set(optionPluginPixivRefreshToken, '');
          return FakePixivClient(prefs);
        },
      );
      expect(find.text('Sign out'), findsNothing);

      await tester.enterText(tokenField(), 'pasted-refresh-token');
      await tester.pump();
      expect(find.text('Sign out'), findsOneWidget);

      await tester.enterText(tokenField(), '');
      await tester.pump();
      expect(find.text('Sign out'), findsNothing);
      await disposePixiv(tester);
    });

    testWidgets('signing out forgets the follows made in this session', (tester) async {
      final feed = PixivFeedStore(PixivClient(PrefServiceCache()));
      addTearDown(feed.destroy);
      await pumpPixiv(
        tester,
        const PixivSettingsScreen(),
        size: const Size(390, 1400),
        extraProviders: [Provider<PixivFeedStore>.value(value: feed)],
      );
      final follows = Provider.of<PixivFollowStore>(tester.element(find.byType(PixivAccountSettings)), listen: false);
      const painter = PixivUser(id: 9, name: 'Painter', account: 'painter', comment: '');
      await follows.toggle(painter);
      expect(follows.isFollowed(painter), isTrue);

      await tester.tap(find.text('Sign out'));
      await settlePixiv(tester);
      expect(follows.isFollowed(painter), isFalse);
      await disposePixiv(tester);
    });
  });
}
