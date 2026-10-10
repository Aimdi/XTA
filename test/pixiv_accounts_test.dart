import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_account_list.dart';
import 'package:xta/plugins/pixiv/pixiv_accounts.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_more_pane.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_store.dart';
import 'package:xta/settings/export_preferences.dart';
import 'package:xta/utils/crash_reporter.dart';

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
