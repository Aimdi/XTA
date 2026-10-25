import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/unrelated_posts.dart';
import 'package:xta/user.dart';

UserSubscription _member(String id, String screenName) => UserSubscription(
  id: id,
  screenName: screenName,
  name: screenName,
  profileImageUrlHttps: null,
  verified: false,
  createdAt: DateTime.utc(2026),
  inFeed: true,
);

TweetChain _post(String id, {String? authorId, String? authorName}) => TweetChain(
  id: id,
  tweets: [
    TweetWithCard()
      ..idStr = id
      ..user = authorId == null
          ? null
          : (UserWithExtra()
              ..idStr = authorId
              ..screenName = authorName),
  ],
  isPinned: false,
);

void main() {
  final members = [_member('1', 'nasa'), _member('2', 'esa')];

  test('posts by the chunk members are not unrelated', () {
    expect(chunkHasUnrelatedPosts([_post('a', authorId: '1'), _post('b', authorId: '2')], members), isFalse);
  });

  test('a post by someone outside the chunk is', () {
    expect(chunkHasUnrelatedPosts([_post('a', authorId: '1'), _post('b', authorId: '99')], members), isTrue);
  });

  test('a member that changed its name, or its capitals, is still itself', () {
    expect(chunkHasUnrelatedPosts([_post('a', authorId: '1', authorName: 'NASA_official')], members), isFalse);
  });

  test('a chunk with a saved search never warns: its posts come from anyone', () {
    final withSearch = [...members, SearchSubscription(id: 'list:123', createdAt: DateTime.utc(2026))];
    expect(chunkHasUnrelatedPosts([_post('a', authorId: '99')], withSearch), isFalse);
  });

  test('a post without an author, such as a tombstone, is ignored', () {
    expect(chunkHasUnrelatedPosts([_post('a')], members), isFalse);
  });

  testWidgets('the warning is a pill whose button explains it', (tester) async {
    var explained = 0;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        home: Scaffold(
          body: Center(child: UnrelatedPostsPill(onExplain: () => explained++)),
        ),
      ),
    );
    await tester.pumpAndSettle();
    expect(find.text('Feed issue detected'), findsOneWidget);
    expect(find.byType(AlertDialog), findsNothing);
    await tester.tap(find.byTooltip('More info'));
    expect(explained, 1);
  });
}
