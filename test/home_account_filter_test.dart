import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/tweet_models.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/client/account_fetch_gate.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/feed_session_cache.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/user.dart';

TweetChain _chain(String id, DateTime createdAt) {
  final tweet = TweetWithCard();
  tweet.idStr = id;
  tweet.createdAt = createdAt;
  tweet.user = UserWithExtra.fromArguments(
    idStr: 'u$id',
    possiblySensitive: false,
    screenName: 'u$id',
  );
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  test('homeFeedDisabledIdsFromPrefs reads JSON string lists', () {
    expect(homeFeedDisabledIdsFromPrefs(null), isEmpty);
    expect(homeFeedDisabledIdsFromPrefs(''), isEmpty);
    expect(homeFeedDisabledIdsFromPrefs('["a","b"]'), ['a', 'b']);
    expect(homeFeedDisabledIdsFromPrefs('not-json'), isEmpty);
  });

  test('enabledHomeAccounts falls back when every account is disabled', () {
    final accounts = [
      Account(id: '1', authHeader: '{}', screenName: 'one'),
      Account(id: '2', authHeader: '{}', screenName: 'two'),
    ];
    expect(enabledHomeAccounts(accounts, {'1'}).map((a) => a.id), ['2']);
    expect(enabledHomeAccounts(accounts, {'1', '2'}).map((a) => a.id), [
      '1',
      '2',
    ]);
    expect(enabledHomeAccounts(accounts, {}).map((a) => a.id), ['1', '2']);
  });

  test('mergeHomeTimelineChains dedupes and sorts newest first', () {
    final older = _chain('a', DateTime.utc(2024, 1, 1));
    final newer = _chain('b', DateTime.utc(2024, 6, 1));
    final duplicate = _chain('a', DateTime.utc(2024, 1, 1));

    final merged = mergeHomeTimelineChains([
      [older],
      [newer, duplicate],
    ]);

    expect(merged.map((c) => c.id), ['b', 'a']);
  });

  test('dropChainsFromAuthors removes posts by turned-off logins', () {
    final kept = _chain('kept', DateTime.utc(2024, 6, 1));
    final dropped = _chain('gone', DateTime.utc(2024, 6, 2));
    dropped.tweets.first.user!.idStr = 'qug9ik';
    kept.tweets.first.user!.idStr = 'other';

    final out = dropChainsFromAuthors([kept, dropped], {'qug9ik'});
    expect(out.map((c) => c.id), ['kept']);
    expect(dropChainsFromAuthors([kept], const {}), [kept]);
  });

  test('home timeline cursors round-trip as JSON', () {
    expect(decodeHomeTimelineCursors(null), isEmpty);
    expect(decodeHomeTimelineCursors('plain-cursor'), isEmpty);
    final encoded = encodeHomeTimelineCursors({'1': 'c1', '2': 'c2'});
    expect(encoded, isNotNull);
    expect(decodeHomeTimelineCursors(encoded), {'1': 'c1', '2': 'c2'});
    expect(encodeHomeTimelineCursors({}), isNull);
  });

  test('canDisableHomeAccount keeps at least one account on', () {
    final accounts = [
      Account(id: '1', authHeader: '{}', screenName: 'one'),
      Account(id: '2', authHeader: '{}', screenName: 'two'),
    ];
    expect(canDisableHomeAccount('1', accounts, {}), isTrue);
    expect(canDisableHomeAccount('2', accounts, {'1'}), isFalse);
    expect(canDisableHomeAccount('1', [accounts.first], {}), isFalse);
  });

  test('setEnabled refuses to turn off the last account', () async {
    final prefs = PrefServiceCache(
      cache: {optionHomeFeedDisabledAccountIds: '[]'},
    );
    final store = HomeAccountFilterStore(prefs);
    final accounts = [
      Account(id: '1', authHeader: '{}', screenName: 'one'),
      Account(id: '2', authHeader: '{}', screenName: 'two'),
    ];

    await store.setEnabled('1', false, accounts: accounts);
    expect(store.state, {'1'});
    expect(AccountFetchGate.disabledIds, {'1'});

    await store.setEnabled('2', false, accounts: accounts);
    expect(store.state, {'1'});
    expect(AccountFetchGate.disabledIds, {'1'});
    AccountFetchGate.disabledIds = {};
  });

  test('homeFollowingCacheKey matches the home tab cache', () {
    expect(homeFollowingCacheKey('-1'), 'home--1');
  });

  test('enabled accounts are the ones fetch should prefer', () {
    final accounts = [
      Account(id: '1', authHeader: '{}', screenName: 'spare'),
      Account(id: '2', authHeader: '{}', screenName: 'main'),
    ];
    final disabled = {'1'};
    final preferred = enabledHomeAccounts(accounts, disabled);
    expect(preferred.map((a) => a.id), ['2']);

    // Gate mirrors that set for Following chunk rotation.
    AccountFetchGate.disabledIds = disabled;
    expect(AccountFetchGate.disabledIds, {'1'});
    AccountFetchGate.disabledIds = {};
  });

  test(
    'toggling evicts Following cache without a parent FeedRefreshController',
    () {
      final cache = FeedSessionCache();
      final old = cache.getOrCreateController(homeFollowingCacheKey('-1'));
      cache.saveOffset(homeFollowingCacheKey('-1'), 40);

      cache.evict(homeFollowingCacheKey('-1'));
      final next = cache.getOrCreateController(homeFollowingCacheKey('-1'));

      expect(identical(old, next), isFalse);
      expect(cache.readOffset(homeFollowingCacheKey('-1')), isNull);
    },
  );

  _toggleTests();
}

Widget _toggleApp(Widget child) {
  return MaterialApp(
    localizationsDelegates: const [
      L10n.delegate,
      GlobalMaterialLocalizations.delegate,
      GlobalWidgetsLocalizations.delegate,
      GlobalCupertinoLocalizations.delegate,
    ],
    supportedLocales: L10n.delegate.supportedLocales,
    locale: const Locale('en'),
    home: Scaffold(body: child),
  );
}

void _toggleTests() {
  final one = Account(id: '1', authHeader: '{}', screenName: 'only');
  final two = Account(id: '2', authHeader: '{}', screenName: 'spare');

  testWidgets('the last account on cannot be switched off', (tester) async {
    await tester.pumpWidget(
      _toggleApp(
        HomeAccountToggleTile(
          account: one,
          disabled: const {},
          accounts: [one],
          onChanged: (_) async {},
        ),
      ),
    );
    await tester.pump();

    expect(find.text('At least one account has to stay on'), findsOneWidget);
    expect(
      tester.widget<SwitchListTile>(find.byType(SwitchListTile)).onChanged,
      isNull,
    );
  });

  testWidgets('a spare account can be excluded from Following and For you', (
    tester,
  ) async {
    var enabled = true;
    await tester.pumpWidget(
      _toggleApp(
        HomeAccountToggleTile(
          account: two,
          disabled: const {},
          accounts: [one, two],
          onChanged: (value) async => enabled = value,
        ),
      ),
    );
    await tester.pump();

    expect(find.text('Include in Following and For you'), findsOneWidget);
    await tester.tap(find.byType(SwitchListTile));
    await tester.pump();
    expect(enabled, isFalse);
  });
}
