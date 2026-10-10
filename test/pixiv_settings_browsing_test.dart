import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_account_api.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_favorites_section.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_browsing.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_content.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_mute.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';

import 'support/pixiv_reader_harness.dart';

class _FakeAccountApi extends PixivAccountApi {
  final bool? showsAi;
  var reads = 0;

  _FakeAccountApi(super.client, this.showsAi);

  @override
  Future<bool> showsAiWorks() async {
    reads++;
    return showsAi ?? (throw PixivException(PixivErrorKind.network, 'offline'));
  }
}

Future<void> _pumpAiSetting(WidgetTester tester, bool? showsAi) => pumpPixiv(
  tester,
  const Scaffold(body: PixivContentSettings()),
  extraProviders: [
    Provider<PixivAccountApi>(create: (context) => _FakeAccountApi(context.read<PixivClient>(), showsAi)),
  ],
);

void main() {
  group("the account's AI setting", () {
    testWidgets('reads Shown and links to pixiv.net to change it', (tester) async {
      await _pumpAiSetting(tester, true);
      expect(find.text('Shown'), findsOneWidget);
      expect(find.text('Change on pixiv.net'), findsOneWidget);
      expect(
        find.byType(Switch),
        findsNWidgets(2),
        reason: 'only the local switches; the account setting is read-only',
      );
      await disposePixiv(tester);
    });

    testWidgets('reads Partially hidden, and says when Pixiv cannot be asked', (tester) async {
      await _pumpAiSetting(tester, false);
      expect(find.text('Partially hidden'), findsOneWidget);
      await disposePixiv(tester);

      await _pumpAiSetting(tester, null);
      expect(find.text('Could not read this setting from Pixiv'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('reads the setting again for the account switched to', (tester) async {
      late _FakeAccountApi api;
      final harness = await pumpPixiv(
        tester,
        const Scaffold(body: PixivContentSettings()),
        extraProviders: [
          Provider<PixivAccountApi>(create: (context) => api = _FakeAccountApi(context.read<PixivClient>(), true)),
        ],
      );
      expect(api.reads, 1);
      await harness.prefs.set(optionPluginPixivUserId, 2);
      await settlePixiv(tester);
      expect(api.reads, 2);
      await disposePixiv(tester);
    });

    test('parses show_ai and refuses an answer without it', () async {
      final prefs = PrefServiceCache();
      final api = PixivAccountApi(_JsonClient(prefs, {'show_ai': false}));
      expect(await api.showsAiWorks(), isFalse);
      await expectLater(
        PixivAccountApi(_JsonClient(prefs, {'other': 1})).showsAiWorks(),
        throwsA(isA<PixivException>()),
      );
    });
  });

  group('mute page', () {
    Future<PixivMuteStore> pumpMute(WidgetTester tester) async {
      await pumpPixiv(tester, const PixivMuteScreen(), size: const Size(390, 1200));
      return Provider.of<PixivMuteStore>(tester.element(find.byType(PixivMuteSettings)), listen: false);
    }

    Finder field() => find.byKey(const ValueKey('pixiv-mute-tag-field'));

    testWidgets('typed tags and patterns are muted; a broken pattern is refused', (tester) async {
      final mute = await pumpMute(tester);
      await tester.enterText(field(), "r'(broken'");
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await settlePixiv(tester);
      expect(find.text('This pattern is not a valid regular expression'), findsOneWidget);
      expect(mute.state.tags, isEmpty);

      await tester.enterText(field(), "r'^ai'");
      await tester.tap(find.byTooltip('Mute tag'));
      await settlePixiv(tester);
      expect(mute.state.tags, {"r'^ai'"});
      expect(find.widgetWithText(InputChip, "r'^ai'"), findsOneWidget);

      await tester.enterText(field(), 'Cat');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await settlePixiv(tester);
      expect(find.widgetWithText(InputChip, '#cat'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('tapping an entry asks before unmuting; a long press copies a tag', (tester) async {
      final copied = <String>[];
      tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
        if (call.method == 'Clipboard.setData') copied.add((call.arguments as Map)['text'] as String);
        return null;
      });
      addTearDown(() => tester.binding.defaultBinaryMessenger.setMockMethodCallHandler(SystemChannels.platform, null));
      final mute = await pumpMute(tester);
      await mute.muteTag('cat');
      await mute.muteAuthor(42, name: 'Mika');
      await settlePixiv(tester);
      expect(find.widgetWithText(InputChip, 'Mika · 42'), findsOneWidget);

      await tester.longPress(find.text('#cat'));
      await settlePixiv(tester);
      expect(copied, ['cat']);

      await tester.tap(find.text('#cat'));
      await settlePixiv(tester);
      expect(find.text('Unmute #cat?'), findsOneWidget);
      await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Unmute')));
      await settlePixiv(tester);
      expect(mute.state.tags, isEmpty);
      expect(mute.state.authorIds, {42});
      await disposePixiv(tester);
    });
  });

  group('start section', () {
    test('maps the stored choice to a tab, Home for anything else', () {
      expect(pixivStartSectionIndex('ranking'), 1);
      expect(pixivStartSectionIndex('favorites'), 2);
      expect(pixivStartSectionIndex('search'), 3);
      expect(pixivStartSectionIndex('more'), 0);
      expect(pixivStartSectionIndex(null), 0);
    });

    testWidgets('the setting stores the picked section', (tester) async {
      final harness = await pumpPixiv(tester, const Scaffold(body: PixivStartSectionSetting()));
      expect(find.text('Home'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('pixiv-start-section')));
      await settlePixiv(tester);
      await tester.tap(find.text('Ranking').last);
      await settlePixiv(tester);
      expect(harness.prefs.get<String>(optionPluginPixivStartSection), 'ranking');
      expect(find.text('Ranking'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('the Pixiv screen opens on it, and tapping the shown section scrolls it to the top', (tester) async {
      final scroll = ScrollController();
      addTearDown(scroll.dispose);
      final feed = PixivFeedStore(PixivClient(PrefServiceCache()));
      addTearDown(feed.destroy);
      await pumpPixiv(
        tester,
        PixivScreen(scrollController: scroll),
        size: const Size(390, 420),
        extraProviders: [Provider<PixivFeedStore>.value(value: feed)],
        client: (prefs) {
          prefs.set(optionPluginPixivStartSection, 'favorites');
          return _ScreenClient(prefs);
        },
      );
      expect(find.byType(PixivFavoritesSection), findsOneWidget);

      await tester.tap(find.byIcon(Icons.menu));
      await settlePixiv(tester);
      final more = find.byType(Scrollable).last;
      await tester.drag(more, const Offset(0, -300));
      await settlePixiv(tester);
      expect(tester.state<ScrollableState>(more).position.pixels, greaterThan(0));

      await tester.tap(find.byIcon(Icons.menu));
      await settlePixiv(tester);
      expect(tester.state<ScrollableState>(more).position.pixels, 0);
      await disposePixiv(tester);
    });
  });

  testWidgets('the browsing section offers history, its pause switch and the copy template', (tester) async {
    await pumpPixiv(tester, const Scaffold(body: SingleChildScrollView(child: PixivBrowsingSettings())));
    expect(find.text('Viewing history'), findsOneWidget);
    expect(find.text('Pause viewing history'), findsOneWidget);
    expect(find.text('Copy info template'), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-open-links')), findsOneWidget, reason: 'tests run as Android');
    await disposePixiv(tester);
  });
}

/// Answers every GET with [json], without any token handling.
class _JsonClient extends PixivClient {
  final Object json;

  _JsonClient(super.prefs, this.json);

  @override
  Future<Object?> getJson(String path, {Map<String, String>? query, bool auth = true}) async => json;
}

/// Answers what the Pixiv screen asks on its own at once, offline.
class _ScreenClient extends FakePixivClient {
  _ScreenClient(super.prefs);

  @override
  Future<void> ensureAccessToken() async {}

  @override
  Future<int> ensureUserId() async => 1;

  @override
  Future<PixivAuthUser> verify() async => const PixivAuthUser(id: 1, name: 'Mika', account: 'mika');

  @override
  Future<PixivIllustPage> bookmarks({required int userId, String restrict = 'public', String? nextUrl}) async =>
      const PixivIllustPage(illusts: []);
}
