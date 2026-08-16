import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/feed_read_position.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/home/feed_strip_tab.dart';
import 'package:xta/home/home_feed_unread.dart';

UserSubscription _user(String id, DateTime createdAt, {bool inFeed = true}) =>
    UserSubscription(
      id: id,
      screenName: id,
      name: id,
      profileImageUrlHttps: null,
      verified: false,
      createdAt: createdAt,
      inFeed: inFeed,
    );

Widget _wrap(Widget child) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: DefaultTabController(length: 1, child: Scaffold(body: child)),
);

void main() {
  final t0 = DateTime.utc(2024, 1, 1);
  final t1 = DateTime.utc(2024, 1, 2);

  test(
    'Following hashes match the live -1 chunk and skip out-of-feed users',
    () {
      final users = [
        _user('1', t0),
        _user('2', t1, inFeed: false),
        _user('3', t1),
      ];
      final members = followingChunkMembers([
        for (final user in users)
          (id: user.id, createdAt: user.createdAt, inFeed: user.inFeed),
      ]);
      expect(members.map((m) => m.id), ['1', '3']);

      final live = SubscriptionGroupFeedChunk(
        users.where((u) => u.inFeed).toList(),
        true,
        false,
      );
      expect(
        feedChunkHashesFor(
          members,
          includeReplies: true,
          includeRetweets: false,
        ),
        [live.hash],
      );
    },
  );

  test('Following last-read prefers following over the legacy -1 key', () {
    expect(
      lastReadForFollowing({feedKeyFollowing: t1, legacyFeedKeyFollowing: t0}),
      t1,
    );
    expect(lastReadForFollowing({legacyFeedKeyFollowing: t0}), t0);
    expect(lastReadForFollowing(const {}), isNull);
  });

  test('Following is unread when its cached chunk is newer than last read', () {
    final members = [FeedChunkMember(id: '1', createdAt: t0)];
    final hash = feedChunkHash(
      ['1'],
      includeReplies: true,
      includeRetweets: true,
    );
    expect(
      followingHasUnread(
        inFeedUsers: members,
        includeReplies: true,
        includeRetweets: true,
        tracksReadPosition: true,
        lastReadAt: t0,
        newestByHash: {hash: t1},
      ),
      isTrue,
    );
    expect(
      followingHasUnread(
        inFeedUsers: members,
        includeReplies: true,
        includeRetweets: true,
        tracksReadPosition: false,
        lastReadAt: t0,
        newestByHash: {hash: t1},
      ),
      isFalse,
    );
  });

  test('For You newest-cached pref round-trips', () async {
    final prefs = PrefServiceCache();
    expect(forYouNewestCachedAt(prefs), isNull);
    await prefs.set(optionForYouNewestCachedAt, t1.toUtc().toIso8601String());
    expect(forYouNewestCachedAt(prefs), t1.toLocal());
  });

  testWidgets('an unread strip tab exposes Unread in semantics', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(
      _wrap(const FeedStripTab(title: 'Following', unread: true)),
    );
    await tester.pumpAndSettle();

    expect(find.bySemanticsLabel(RegExp(r'Unread')), findsOneWidget);
    handle.dispose();
  });
}
