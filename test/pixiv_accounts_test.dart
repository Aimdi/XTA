import 'dart:async';
import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_account_list.dart';
import 'package:xta/plugins/pixiv/pixiv_accounts.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_more_pane.dart';
import 'package:xta/plugins/pixiv/pixiv_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_account.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/settings/export_preferences.dart';
import 'package:xta/utils/crash_reporter.dart';
import 'package:xta/utils/pref_lists.dart';

import 'support/pixiv_reader_harness.dart';

const _mika = PixivAuthUser(
  id: 1,
  name: 'Mika',
  account: 'mika',
  isPremium: true,
  avatarUrl: 'https://i.pximg.net/a.jpg',
);
const _haru = PixivAuthUser(id: 2, name: 'Haru', account: 'haru');

Future<PrefServiceCache> _signedIn(PixivAuthUser user, String token) async {
  final prefs = PrefServiceCache();
  await prefs.set(optionPluginPixivRefreshToken, token);
  await prefs.set(optionPluginPixivAccessToken, 'access-$token');
  await prefs.set(optionPluginPixivAccessExpiresAt, '2999-01-01T00:00:00.000');
  await prefs.set(optionPluginPixivUserId, user.id);
  return prefs;
}

String _accountsJson(List<(PixivAuthUser, String)> accounts) =>
    jsonEncode([for (final (user, token) in accounts) PixivAccount.of(user, token).toJson()]);

Map<int, String> _storedTokens(BasePrefService prefs) => {
  for (final account in readPixivAccounts(prefs.get<String>(optionPluginPixivAccounts)))
    account.userId: account.refreshToken,
};

/// Pixiv's token endpoint for Mika and Haru; Mika's answer waits for [mikaGate].
http.Client _tokenEndpoint(Completer<void> mikaGate, List<String> sent) => MockClient((request) async {
  final token = Uri.splitQueryString(request.body)['refresh_token']!;
  sent.add(token);
  final user = token == 'mika-token' ? _mika : _haru;
  if (user == _mika) await mikaGate.future;
  return http.Response(
    jsonEncode({
      'access_token': 'access-${user.account}',
      'refresh_token': token,
      'expires_in': 3600,
      'user': {'id': '${user.id}', 'name': user.name, 'account': user.account},
    }),
    200,
  );
});

/// Favorites list each account's own work, so a list left from the last
/// account shows.
class _AccountScreenClient extends FakePixivClient {
  _AccountScreenClient(super.prefs);

  @override
  Future<void> ensureAccessToken() async {}

  @override
  Future<int> ensureUserId() async => storedUserId ?? 0;

  @override
  Future<PixivAuthUser> verify() async => throw PixivException(PixivErrorKind.network, 'offline');

  @override
  Future<PixivIllustPage> bookmarks({
    required int userId,
    String restrict = 'public',
    String? tag,
    String? nextUrl,
  }) async => PixivIllustPage(
    illusts: [pixivWork(id: userId * 100, pages: 1, title: 'Work of $userId')],
  );
}

void main() {
  group('stored accounts', () {
    test('read each user once and skip entries without an id or token', () {
      final raw = jsonEncode([
        PixivAccount.of(_mika, 'm1').toJson(),
        PixivAccount.of(_mika, 'm2').toJson(),
        {'userId': 3},
        {'refreshToken': 'x'},
        'junk',
      ]);
      final accounts = readPixivAccounts(raw);
      expect(accounts.map((account) => account.refreshToken), ['m1']);
      expect(accounts.single.isPremium, isTrue);
      expect(accounts.single.avatarUrl, 'https://i.pximg.net/a.jpg');
      expect(readPixivAccounts('{oops'), isEmpty);
      expect(readPixivAccounts(null), isEmpty);
    });

    test('an account is replaced in place, a new one is added last', () {
      final first = [PixivAccount.of(_mika, 'm1'), PixivAccount.of(_haru, 'h1')];
      final updated = pixivAccountsWith(first, PixivAccount.of(_mika, 'm2'));
      expect(updated.map((account) => account.refreshToken), ['m2', 'h1']);
      expect(pixivAccountsWithout(updated, 1).map((account) => account.userId), [2]);
    });

    test('the token response user brings the avatar used in the switcher', () {
      final user = PixivAuthUser.fromJson({
        'id': '7',
        'name': 'Rin',
        'profile_image_urls': {'px_50x50': 'https://i.pximg.net/50.jpg', 'px_170x170': 'https://i.pximg.net/170.jpg'},
      });
      expect([user.id, user.avatarUrl], [7, 'https://i.pximg.net/170.jpg']);
      expect(PixivAuthUser.fromJson({'id': 7}).avatarUrl, isNull);
    });
  });

  group('PixivAccountsStore', () {
    test('adds and switches accounts; switching clears the access token', () async {
      final prefs = await _signedIn(_mika, 'mika-token');
      final client = PixivClient(prefs);
      final store = PixivAccountsStore(client);
      addTearDown(store.destroy);
      await store.remember(_mika);

      await prefs.set(optionPluginPixivRefreshToken, 'haru-token');
      await prefs.set(optionPluginPixivUserId, 2);
      await store.remember(_haru);
      expect(store.state.map((account) => account.userId), [1, 2]);
      expect(store.active?.name, 'Haru');

      await store.switchTo(store.state.first);
      expect(prefs.get<String>(optionPluginPixivRefreshToken), 'mika-token');
      expect(prefs.get<String>(optionPluginPixivAccessToken), '');
      expect(prefs.get<String>(optionPluginPixivAccessExpiresAt), '');
      expect(prefs.get<int>(optionPluginPixivUserId), 1);
      expect(prefs.get<bool>(optionPluginPixivIsPremium), isTrue);
      expect(store.active?.name, 'Mika');
    });

    test('a token Pixiv rotated since storing is kept when switching away', () async {
      final prefs = await _signedIn(_mika, 'old');
      final store = PixivAccountsStore(PixivClient(prefs));
      addTearDown(store.destroy);
      await store.remember(_mika);
      await prefs.set(
        optionPluginPixivAccounts,
        jsonEncode([PixivAccount.of(_mika, 'old').toJson(), PixivAccount.of(_haru, 'haru').toJson()]),
      );
      await prefs.set(optionPluginPixivRefreshToken, 'rotated');
      store.load();

      await store.switchTo(store.state.last);
      expect(readPixivAccounts(prefs.get<String>(optionPluginPixivAccounts)).first.refreshToken, 'rotated');
      expect(prefs.get<String>(optionPluginPixivRefreshToken), 'haru');
    });

    test('signing out forgets only the active account and hands over to another', () async {
      final prefs = await _signedIn(_haru, 'haru');
      await prefs.set(
        optionPluginPixivAccounts,
        jsonEncode([PixivAccount.of(_mika, 'mika').toJson(), PixivAccount.of(_haru, 'haru').toJson()]),
      );
      final store = PixivAccountsStore(PixivClient(prefs))..load();
      addTearDown(store.destroy);

      final next = await store.signOutActive();
      expect(next?.userId, 1);
      expect(store.state.map((account) => account.userId), [1]);
      expect(prefs.get<String>(optionPluginPixivRefreshToken), 'mika');

      expect(await store.signOutActive(), isNull);
      expect(store.state, isEmpty);
      expect(prefs.get<String>(optionPluginPixivRefreshToken), '');
      expect(store.signedIn, isFalse);
    });
  });

  group('a refresh token stays with its own account', () {
    test('a token check answered after a switch reports, and keeps, only the account now in use', () async {
      final prefs = await _signedIn(_mika, 'mika-token');
      await prefs.set(optionPluginPixivAccessExpiresAt, '');
      await prefs.set(optionPluginPixivAccounts, _accountsJson([(_mika, 'mika-token'), (_haru, 'haru-token')]));
      final gate = Completer<void>();
      final sent = <String>[];
      final client = PixivClient(prefs, httpClient: _tokenEndpoint(gate, sent));
      final store = PixivAccountsStore(client)..load();
      addTearDown(store.destroy);

      final checking = pixivVerifiedName(client);
      await pumpEventQueue();
      await store.switchTo(store.state.last);
      gate.complete();

      expect(await checking, 'Haru', reason: "the old account's answer is not reported as the one in use");
      expect(sent, ['mika-token', 'haru-token']);
      expect(_storedTokens(prefs), {1: 'mika-token', 2: 'haru-token'});
      expect(prefs.get<String>(optionPluginPixivRefreshToken), 'haru-token');
      expect(prefs.get<String>(optionPluginPixivAccessToken), 'access-haru');
      expect(prefs.get<int>(optionPluginPixivUserId), 2);
    });

    test('a user is only kept with the token of the account stored as in use', () async {
      final prefs = await _signedIn(_haru, 'haru-token');
      await rememberPixivAccount(prefs, _mika);
      expect(_storedTokens(prefs), isEmpty);
      await rememberPixivAccount(prefs, _haru);
      expect(_storedTokens(prefs), {2: 'haru-token'});
    });

    test('an account never checked since it signed in is kept by id before another takes over', () async {
      final prefs = await _signedIn(const PixivAuthUser(id: 5, name: '', account: ''), 'old-token');
      await keepActivePixivToken(prefs);
      final kept = readPixivAccounts(prefs.get<String>(optionPluginPixivAccounts)).single;
      expect([kept.userId, kept.refreshToken, kept.displayName], [5, 'old-token', '5']);
    });

    testWidgets('a token typed in Settings has no owner, so a switch keeps every stored token', (tester) async {
      final feed = PixivFeedStore(PixivClient(PrefServiceCache()));
      addTearDown(feed.destroy);
      final harness = await pumpPixiv(
        tester,
        const PixivSettingsScreen(),
        size: const Size(390, 1600),
        extraProviders: [Provider<PixivFeedStore>.value(value: feed)],
        client: (prefs) {
          prefs.set(optionPluginPixivRefreshToken, 'mika-token');
          prefs.set(optionPluginPixivUserId, 1);
          prefs.set(optionPluginPixivAccounts, _accountsJson([(_mika, 'mika-token'), (_haru, 'haru-token')]));
          return FakePixivClient(prefs);
        },
      );
      final tokenField = find.descendant(of: find.byType(PixivAccountSettings), matching: find.byType(TextField));
      await tester.enterText(tokenField.last, 'mistyped');
      await tester.testTextInput.receiveAction(TextInputAction.done);
      await tester.pump();
      expect(harness.prefs.get<int>(optionPluginPixivUserId), 0);

      await tester.tap(find.byKey(const ValueKey('pixiv-account-2')));
      await settlePixiv(tester);
      expect(_storedTokens(harness.prefs), {1: 'mika-token', 2: 'haru-token'});
      expect(harness.prefs.get<String>(optionPluginPixivRefreshToken), 'haru-token');
      await disposePixiv(tester);
    });

    testWidgets('a token entered in Settings is a switch: the last account keeps its token and its marks go', (
      tester,
    ) async {
      final feed = PixivFeedStore(PixivClient(PrefServiceCache()));
      addTearDown(feed.destroy);
      final harness = await pumpPixiv(
        tester,
        const PixivSettingsScreen(),
        size: const Size(390, 1600),
        extraProviders: [Provider<PixivFeedStore>.value(value: feed)],
        client: (prefs) {
          prefs.set(optionPluginPixivRefreshToken, 'rotated-mika-token');
          prefs.set(optionPluginPixivUserId, 1);
          prefs.set(optionPluginPixivAccounts, _accountsJson([(_mika, 'mika-token')]));
          return FakePixivClient(prefs);
        },
      );
      final context = tester.element(find.byType(PixivAccountSettings));
      final bookmarks = Provider.of<PixivBookmarkStore>(context, listen: false)..mark(123, true);
      final tokenField = find.descendant(of: find.byType(PixivAccountSettings), matching: find.byType(TextField));
      await tester.enterText(tokenField.last, 'another-accounts-token');
      await tester.pump();
      expect(bookmarks.state, {123: true}, reason: 'typing alone changes nothing');
      expect(harness.prefs.get<String>(optionPluginPixivRefreshToken), 'rotated-mika-token');

      await tester.testTextInput.receiveAction(TextInputAction.done);
      await settlePixiv(tester);
      expect(bookmarks.state, isEmpty);
      expect(_storedTokens(harness.prefs), {1: 'rotated-mika-token'});
      expect(harness.prefs.get<String>(optionPluginPixivRefreshToken), 'another-accounts-token');
      expect(harness.prefs.get<int>(optionPluginPixivUserId), 0);
      await disposePixiv(tester);
    });
  });

  group('the Pixiv screen follows an account change made anywhere', () {
    Future<PixivHarness> pumpScreen(WidgetTester tester, Widget screen, List<Object> stores) => pumpPixiv(
      tester,
      screen,
      extraProviders: [
        Provider<PixivFeedStore>.value(value: stores[0] as PixivFeedStore),
        Provider<PluginSessionStore>.value(value: stores[1] as PluginSessionStore),
      ],
      client: (prefs) {
        prefs.set(optionPluginPixivStartSection, 'favorites');
        prefs.set(optionPluginPixivUserId, 1);
        prefs.set(optionPluginPixivAccounts, _accountsJson([(_mika, 'mika-token'), (_haru, 'haru-token')]));
        return _AccountScreenClient(prefs);
      },
    );

    testWidgets("a switch from the plugin's Settings page reloads Favorites for the new account", (tester) async {
      final scroll = ScrollController();
      final feed = PixivFeedStore(PixivClient(PrefServiceCache()));
      final session = PluginSessionStore();
      addTearDown(scroll.dispose);
      addTearDown(feed.destroy);
      addTearDown(session.destroy);
      final harness = await pumpScreen(tester, PixivScreen(scrollController: scroll), [feed, session]);
      expect(find.text('Work of 1'), findsOneWidget);

      final settings = PixivAccountsStore(harness.client)..load();
      addTearDown(settings.destroy);
      await settings.switchTo(settings.state.last);
      await settlePixiv(tester);
      expect(find.text('Work of 1'), findsNothing, reason: "the last account's favourites are gone");
      expect(find.text('Work of 2'), findsOneWidget);
      await disposePixiv(tester);
    });

    testWidgets('a screen built again after a switch never shows the last account\'s lists', (tester) async {
      final scroll = ScrollController();
      final feed = PixivFeedStore(PixivClient(PrefServiceCache()));
      final session = PluginSessionStore();
      final shown = ValueNotifier(true);
      addTearDown(scroll.dispose);
      addTearDown(feed.destroy);
      addTearDown(session.destroy);
      addTearDown(shown.dispose);
      final screen = ValueListenableBuilder<bool>(
        valueListenable: shown,
        builder: (_, on, _) => on ? PixivScreen(scrollController: scroll) : const SizedBox(),
      );
      final harness = await pumpScreen(tester, screen, [feed, session]);
      expect(find.text('Work of 1'), findsOneWidget);
      shown.value = false;
      await tester.pump();

      await harness.client.switchTo(PixivAccount.of(_haru, 'haru-token'));
      shown.value = true;
      await tester.pump();
      expect(find.text('Work of 1'), findsNothing, reason: 'not even for the first frame');
      await settlePixiv(tester);
      expect(find.text('Work of 2'), findsOneWidget);
      await disposePixiv(tester);
    });
  });

  group('backups', () {
    test('the accounts key is a secret, so exports and crash reports leave it out', () {
      expect(secretPrefKeys, contains(optionPluginPixivAccounts));
      final prefs = {
        optionPluginPixivAccounts: jsonEncode([PixivAccount.of(_mika, 'token').toJson()]),
        optionPluginPixivShowR18: true,
      };
      expect(prefsMapWithoutSecrets(prefs).keys, [optionPluginPixivShowR18]);
      final exported = preferencesForExport(prefs, includeSettings: true, includeSubscriptions: true)!;
      expect(exported.containsKey(optionPluginPixivAccounts), isFalse);
      expect(jsonEncode(exported), isNot(contains('token')));
    });

    test('whose the tokens are and the saved pages stay on the device, both ways', () {
      final prefs = {
        optionPluginPixivUserId: 1,
        optionPluginPixivIsPremium: true,
        optionPluginPixivAccessExpiresAt: '2099-01-01T00:00:00.000',
        optionPluginPixivDownloadIndex: '["123_p0"]',
        optionPluginPixivShowR18: true,
      };
      expect(prefsMapWithoutSecrets(prefs).keys, [optionPluginPixivShowR18]);
      expect(prefsForImport(prefs).keys, [optionPluginPixivShowR18], reason: 'an older backup carried them');
    });

    test("restoring another account's backup leaves the account in use and every stored token alone", () async {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginPixivRefreshToken: 'haru-token',
          optionPluginPixivUserId: 2,
          optionPluginPixivAccounts: _accountsJson([(_mika, 'mika-token'), (_haru, 'haru-token')]),
        },
      );
      final backup = {optionPluginPixivUserId: 1, optionPluginPixivIsPremium: true, optionPluginPixivShowR18: true};
      await prefs.fromMap(prefsForImport(backup));
      expect(prefs.get<int>(optionPluginPixivUserId), 2);

      final store = PixivAccountsStore(PixivClient(prefs));
      addTearDown(store.destroy);
      store.load();
      await store.switchTo(store.state.firstWhere((account) => account.userId == 1));
      expect(_storedTokens(prefs), {1: 'mika-token', 2: 'haru-token'});
      expect(prefs.get<String>(optionPluginPixivRefreshToken), 'mika-token');
    });
  });

  testWidgets('the More hub shows the active account and switches from its sheet', (tester) async {
    final feed = PixivFeedStore(PixivClient(PrefServiceCache()));
    addTearDown(feed.destroy);
    final harness = await pumpPixiv(
      tester,
      Scaffold(body: PixivMorePane(onAuthChanged: () {})),
      extraProviders: [Provider<PixivFeedStore>.value(value: feed)],
      client: (prefs) {
        prefs.set(optionPluginPixivUserId, 1);
        prefs.set(
          optionPluginPixivAccounts,
          jsonEncode([PixivAccount.of(_mika, 'fixture-only').toJson(), PixivAccount.of(_haru, 'haru-token').toJson()]),
        );
        return FakePixivClient(prefs);
      },
    );
    final header = find.byKey(const ValueKey('pixiv-account-header'));
    expect(find.descendant(of: header, matching: find.text('Mika')), findsOneWidget);
    expect(find.descendant(of: header, matching: find.text('Premium')), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-more-history')), findsOneWidget);
    expect(find.byKey(const ValueKey('pixiv-more-manage')), findsOneWidget);

    final follows = Provider.of<PixivFollowStore>(tester.element(header), listen: false);
    const painter = PixivUser(id: 9, name: 'Painter', account: 'painter', comment: '');
    await follows.toggle(painter);

    await tester.tap(find.byTooltip('Switch account'));
    await settlePixiv(tester);
    expect(find.byType(PixivAccountList), findsOneWidget);
    await tester.tap(find.byKey(const ValueKey('pixiv-account-2')));
    await settlePixiv(tester);

    expect(harness.prefs.get<String>(optionPluginPixivRefreshToken), 'haru-token');
    expect(harness.prefs.get<String>(optionPluginPixivAccessToken), '');
    expect(follows.isFollowed(painter), isFalse, reason: 'what was loaded for the last account is dropped');
    expect(find.descendant(of: header, matching: find.text('Haru')), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('cancelling a removal leaves the account switcher open', (tester) async {
    final harness = await pumpPixiv(
      tester,
      Scaffold(body: PixivMorePane(onAuthChanged: () {})),
      client: (prefs) {
        prefs.set(optionPluginPixivUserId, 1);
        prefs.set(optionPluginPixivAccounts, _accountsJson([(_mika, 'fixture-only'), (_haru, 'haru-token')]));
        return FakePixivClient(prefs);
      },
    );
    await tester.tap(find.byTooltip('Switch account'));
    await settlePixiv(tester);
    await tester.tap(find.byTooltip('Remove account'));
    await settlePixiv(tester);
    await tester.tap(find.text('Cancel'));
    await settlePixiv(tester);
    expect(find.byType(PixivAccountList), findsOneWidget);
    expect(_storedTokens(harness.prefs).keys, [1, 2]);

    await tester.tap(find.byTooltip('Remove account'));
    await settlePixiv(tester);
    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Remove account')));
    await settlePixiv(tester);
    expect(find.byType(PixivAccountList), findsNothing, reason: 'a removal closes the sheet');
    expect(_storedTokens(harness.prefs).keys, [1]);
    await disposePixiv(tester);
  });

  testWidgets("embedded in Home, the More list scrolls with Home's controller", (tester) async {
    final primary = ScrollController();
    final own = ScrollController();
    addTearDown(primary.dispose);
    addTearDown(own.dispose);
    await pumpPixiv(
      tester,
      Scaffold(
        body: PluginEmbedded(
          child: PrimaryScrollController(
            controller: primary,
            child: PixivMorePane(onAuthChanged: () {}, scrollController: own),
          ),
        ),
      ),
      size: const Size(390, 300),
    );
    await tester.drag(find.byType(ListView), const Offset(0, -200));
    await settlePixiv(tester);
    expect(primary.offset, greaterThan(0), reason: 'tapping More again scrolls this controller to the top');
    expect(own.hasClients, isFalse);
    await disposePixiv(tester);
  });

  testWidgets('the More hub fits a narrow screen with large text', (tester) async {
    await pumpPixiv(
      tester,
      Scaffold(body: PixivMorePane(onAuthChanged: () {})),
      size: const Size(320, 640),
      textScale: 2,
      client: (prefs) {
        prefs.set(optionPluginPixivUserId, 1);
        prefs.set(optionPluginPixivAccounts, jsonEncode([PixivAccount.of(_mika, 'fixture-only').toJson()]));
        return FakePixivClient(prefs);
      },
    );
    expect(tester.takeException(), isNull);
    expect(find.text('Premium'), findsOneWidget);
    await disposePixiv(tester);
  });
}
