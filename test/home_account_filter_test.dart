import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/tweet_models.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/client/account_fetch_gate.dart';
import 'package:xta/home/home_account_filter.dart';
import 'package:xta/user.dart';

TweetChain _chain(String id, DateTime createdAt) {
  final tweet = TweetWithCard();
  tweet.idStr = id;
  tweet.createdAt = createdAt;
  tweet.user = UserWithExtra.fromArguments(idStr: 'u$id', possiblySensitive: false, screenName: 'u$id');
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
    expect(enabledHomeAccounts(accounts, {'1', '2'}).map((a) => a.id), ['1', '2']);
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

  test('home timeline cursors round-trip as JSON', () {
    expect(decodeHomeTimelineCursors(null), isEmpty);
    expect(decodeHomeTimelineCursors('plain-cursor'), isEmpty);
    final encoded = encodeHomeTimelineCursors({'1': 'c1', '2': 'c2'});
    expect(encoded, isNotNull);
    expect(decodeHomeTimelineCursors(encoded), {'1': 'c1', '2': 'c2'});
    expect(encodeHomeTimelineCursors({}), isNull);
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
}
