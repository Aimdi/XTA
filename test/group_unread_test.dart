import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/feed_chunk_hash.dart';
import 'package:xta/group/feed_search_query.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/group_screen.dart';
import 'package:xta/group/group_unread.dart';
import 'package:xta/subscriptions/widgets/group_tile.dart';

UserSubscription _user(String id, DateTime createdAt) => UserSubscription(
  id: id,
  screenName: id,
  name: id,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: createdAt,
  inFeed: true,
);

SubscriptionGroup _group() => SubscriptionGroup(
  id: 'g1',
  name: 'Anime',
  icon: defaultGroupIcon,
  color: null,
  numberOfMembers: 2,
  createdAt: DateTime.utc(2024),
);

Widget _wrap(Widget child) => MaterialApp(
  localizationsDelegates: const [
    L10n.delegate,
    GlobalMaterialLocalizations.delegate,
    GlobalWidgetsLocalizations.delegate,
    GlobalCupertinoLocalizations.delegate,
  ],
  supportedLocales: L10n.delegate.supportedLocales,
  home: Scaffold(body: child),
);

void main() {
  final t0 = DateTime.utc(2024, 1, 1);
  final t1 = DateTime.utc(2024, 1, 2);

  test('feedChunkHash matches SubscriptionGroupFeedChunk.hash', () {
    final users = [_user('1', t0), _user('2', t1)];
    final chunk = SubscriptionGroupFeedChunk(users, true, false);
    expect(
      feedChunkHash(['1', '2'], includeReplies: true, includeRetweets: false),
      chunk.hash,
    );
  });

  test('members pack into hashes by the length of their search', () {
    // 15-character names: twenty fill one 512-character search, ten are left.
    final members = [
      for (var i = 0; i < 30; i++)
        FeedChunkMember(
          id: '$i',
          createdAt: t0.add(Duration(minutes: i)),
          term: userSearchTerm('user_long_n${'$i'.padLeft(4, '0')}'),
        ),
    ];
    final hashes = feedChunkHashesFor(
      members,
      includeReplies: true,
      includeRetweets: true,
    );
    expect(hashes, hasLength(2));
    expect(
      hashes.first,
      feedChunkHash(
        [for (var i = 0; i < 20; i++) '$i'],
        includeReplies: true,
        includeRetweets: true,
      ),
    );
    expect(
      hashes.last,
      feedChunkHash(
        [for (var i = 20; i < 30; i++) '$i'],
        includeReplies: true,
        includeRetweets: true,
      ),
    );
  });

  test('groupHasUnread is false when tracking is off', () {
    expect(
      groupHasUnread(
        tracksReadPosition: false,
        newestCachedAt: t1,
        lastReadAt: null,
      ),
      isFalse,
    );
  });

  test('groupHasUnread is false when nothing is cached', () {
    expect(
      groupHasUnread(
        tracksReadPosition: true,
        newestCachedAt: null,
        lastReadAt: null,
      ),
      isFalse,
    );
  });

  test('groupHasUnread is true when tracking, cached, and never marked', () {
    expect(
      groupHasUnread(
        tracksReadPosition: true,
        newestCachedAt: t1,
        lastReadAt: null,
      ),
      isTrue,
    );
  });

  test('groupHasUnread is true when the cache is newer than last read', () {
    expect(
      groupHasUnread(
        tracksReadPosition: true,
        newestCachedAt: t1,
        lastReadAt: t0,
      ),
      isTrue,
    );
  });

  test('groupHasUnread is false when the cache is not newer', () {
    expect(
      groupHasUnread(
        tracksReadPosition: true,
        newestCachedAt: t0,
        lastReadAt: t0,
      ),
      isFalse,
    );
    expect(
      groupHasUnread(
        tracksReadPosition: true,
        newestCachedAt: t0,
        lastReadAt: t1,
      ),
      isFalse,
    );
  });

  test('popular feeds do not track even when the global switch is on', () {
    expect(
      tracksGroupReadPosition(
        popular: true,
        globalReadingPosition: true,
        catchUp: false,
      ),
      isFalse,
    );
    expect(
      tracksGroupReadPosition(
        popular: false,
        globalReadingPosition: false,
        catchUp: true,
      ),
      isTrue,
    );
  });

  test('a parent group hashes the union of nested members', () {
    final parentOf = {'child': 'parent', 'parent': null};
    final members = [
      FeedMemberRow(
        groupId: 'parent',
        id: 'a',
        createdAt: t0,
        search: false,
        term: 'from:a',
      ),
      FeedMemberRow(
        groupId: 'child',
        id: 'b',
        createdAt: t1,
        search: false,
        term: 'from:b',
      ),
    ];
    final parentHash = feedChunkHash(
      ['a', 'b'],
      includeReplies: true,
      includeRetweets: true,
    );
    final childHash = feedChunkHash(
      ['b'],
      includeReplies: true,
      includeRetweets: true,
    );
    expect(parentHash, isNot(childHash));
    expect(
      unreadGroupIds(
        groupIds: ['parent', 'child'],
        parentOf: parentOf,
        members: members,
        includeRepliesByGroup: const {},
        includeRetweetsByGroup: const {},
        globalIncludeReplies: true,
        globalIncludeRetweets: true,
        globalReadingPosition: true,
        catchUpGroupIds: const {},
        popularGroupIds: const {},
        lastReadByGroup: const {},
        newestByHash: {parentHash: t1, childHash: t1},
      ),
      {'parent', 'child'},
    );
  });

  testWidgets('an unread board tile exposes Unread in semantics', (
    tester,
  ) async {
    final handle = tester.ensureSemantics();
    await tester.pumpWidget(
      _wrap(GroupTile(group: _group(), unread: true, onTap: () {})),
    );
    await tester.pumpAndSettle();

    expect(
      tester.getSemantics(find.byType(GroupTile)).label,
      contains('Unread'),
    );
    handle.dispose();
  });
}
