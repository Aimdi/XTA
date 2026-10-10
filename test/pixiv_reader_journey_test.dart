import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_plugin.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

import 'support/pixiv_discovery_fakes.dart';

class _Pixiv extends PixivClient {
  _Pixiv(super.prefs);
  final calls = <String>[];
  @override
  Future<void> ensureAccessToken() async {}
  @override
  Future<int> ensureUserId() async => 1;
  @override
  Future<PixivIllustPage> following({String? nextUrl}) async {
    calls.add('following');
    return const PixivIllustPage(illusts: []);
  }

  @override
  Future<PixivIllustPage> recommended({String? nextUrl}) async {
    calls.add('recommended');
    return const PixivIllustPage(illusts: []);
  }

  @override
  Future<PixivIllustPage> bookmarks({required int userId, String restrict = 'public', String? nextUrl}) async {
    calls.add('favorites:$restrict');
    return const PixivIllustPage(illusts: []);
  }
}

void main() {
  testWidgets('Pixiv descriptor keeps all modes and lazy source/favorites controls reachable', (tester) async {
    final prefs = PrefServiceCache(defaults: {optionPluginPixivRefreshToken: 'fixture-only'});
    final client = _Pixiv(prefs);
    final discovery = FakePixivDiscoveryApi(client);
    final mute = PixivMuteStore(prefs);
    final feed = PixivFeedStore(client, filter: mute.filter);
    final scroll = ScrollController();
    await tester.pumpWidget(
      PrefService(
        service: prefs,
        child: MultiProvider(
          providers: [
            Provider<PixivClient>.value(value: client),
            Provider<PixivMuteStore>.value(value: mute),
            Provider<PixivFeedStore>.value(value: feed),
            Provider<PixivDiscoveryApi>.value(value: discovery),
          ],
          child: MaterialApp(
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            home: PixivPlugin().homeScreen(scrollController: scroll),
          ),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(client.calls, ['following']);
    await tester.tap(find.text('Recommended'));
    await tester.pumpAndSettle();
    expect(client.calls, ['following', 'recommended']);
    expect(discovery.calls, containsAll(['spotlight', 'users']));
    await tester.tap(find.descendant(of: find.byType(PluginHomeChrome), matching: find.byTooltip('Ranking')));
    await tester.pumpAndSettle();
    expect(discovery.calls.last, 'rank:day:null');
    expect(find.byType(ChoiceChip), findsNWidgets(8));
    await tester.tap(find.byKey(const ValueKey('pixiv-ranking-mode-week')));
    await tester.pumpAndSettle();
    expect(discovery.calls.last, 'rank:week:null');
    final date = find.byIcon(Icons.calendar_today);
    expect(tester.getSize(find.ancestor(of: date, matching: find.byType(IconButton))).height, greaterThanOrEqualTo(48));
    await tester.tap(date);
    await tester.pumpAndSettle();
    expect(find.byType(DatePickerDialog), findsOneWidget);
    await tester.tap(find.text('Cancel'));
    await tester.pumpAndSettle();
    final favorites = find.descendant(of: find.byType(PluginHomeChrome), matching: find.byTooltip('Favorites'));
    await tester.ensureVisible(favorites);
    await tester.tap(favorites);
    await tester.pumpAndSettle();
    expect(client.calls.last, 'favorites:public');
    await tester.tap(find.text('Private'));
    await tester.pumpAndSettle();
    expect(client.calls.last, 'favorites:private');
    expect(tester.takeException(), isNull);
    await tester.pumpWidget(const SizedBox());
    scroll.dispose();
    feed.destroy();
    mute.destroy();
  });
}
