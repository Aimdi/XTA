import 'package:dart_twitter_api/twitter_api.dart' show User;
import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:visibility_detector/visibility_detector.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/_tweets.dart';
import 'package:xta/profile/posts_filter.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/user.dart';

TweetWithCard _tweet(String id, String text, int hour, String author) => TweetWithCard()
  ..idStr = id
  ..fullText = text
  ..createdAt = DateTime.utc(2026, 10, 11, hour)
  ..user = (User()
    ..idStr = 'u$author'
    ..name = author
    ..screenName = author);

TweetChain _post(String id, String text, int hour) =>
    TweetChain(id: id, tweets: [_tweet(id, text, hour, 'someone')], isPinned: false);

/// A repost by the profile at [hour] of someone else's post.
TweetChain _repost(String id, String text, int hour) {
  final original = _tweet('o$id', text, 1, 'other');
  final wrapper = _tweet(id, 'RT @other: $text', hour, 'someone')
    ..retweetedStatus = original
    ..retweetedStatusWithCard = original;
  return TweetChain(id: id, tweets: [wrapper], isPinned: false);
}

TweetStatus _page(List<TweetChain> chains) => TweetStatus(chains: chains, cursorBottom: null, cursorTop: null);

Future<TweetStatus> Function(String?) _serve(List<TweetChain> chains, List<String?> asked) => (cursor) async {
  asked.add(cursor);
  return _page(cursor == null ? chains : const []);
};

Future<void> _pumpTab(
  WidgetTester tester, {
  required PostsFilter filter,
  required Future<TweetStatus> Function(String?) posts,
  required Future<TweetStatus> Function(String?) reposts,
}) async {
  final likes = LikedTweetModel();
  final saves = SavedTweetModel();
  addTearDown(likes.destroy);
  addTearDown(saves.destroy);
  await tester.pumpWidget(
    MultiProvider(
      providers: [
        Provider<LikedTweetModel>.value(value: likes),
        Provider<SavedTweetModel>.value(value: saves),
      ],
      child: PrefService(
        service: PrefServiceCache(
          cache: {
            optionLocale: 'en',
            optionShareBaseUrl: '',
            optionNonConfirmationBiasMode: false,
            optionTweetsShowSubscribeBadge: false,
            optionUseAbsoluteTimestamp: false,
            alwaysShowFullTweetContents: true,
            optionGestureDoubleTapLike: false,
            optionThemeTrueBlack: false,
            optionThemeTrueBlackTweetCards: false,
            optionZenMode: false,
            optionCalmMode: false,
          },
        ),
        child: MaterialApp(
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
            GlobalCupertinoLocalizations.delegate,
          ],
          supportedLocales: const [Locale('en')],
          home: Scaffold(
            body: ProfileTweets(
              user: UserWithExtra.fromArguments(idStr: '42', screenName: 'someone', name: 'Someone'),
              type: 'profile',
              includeReplies: false,
              pinnedTweets: const [],
              pref: PrefServiceCache(),
              filter: filter,
              fetchTweets: posts,
              fetchReposts: reposts,
            ),
          ),
        ),
      ),
    ),
  );
  for (var i = 0; i < 10; i++) {
    await tester.pump(const Duration(milliseconds: 100));
  }
}

void main() {
  setUp(() => VisibilityDetectorController.instance.updateInterval = Duration.zero);
  tearDown(() => VisibilityDetectorController.instance.updateInterval = const Duration(milliseconds: 500));

  testWidgets('Retweets reads the reposts timeline, not the posts one', (tester) async {
    final postsAsked = <String?>[];
    final repostsAsked = <String?>[];
    await _pumpTab(
      tester,
      filter: PostsFilter.retweets,
      posts: _serve([_post('p1', 'my own post', 9)], postsAsked),
      reposts: _serve([_repost('r1', 'a post I reposted', 8)], repostsAsked),
    );
    expect(find.textContaining('a post I reposted'), findsOneWidget);
    expect(find.textContaining('my own post'), findsNothing);
    expect(postsAsked, isEmpty);
    expect(repostsAsked, [null]);
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('All shows posts and reposts, newest first', (tester) async {
    await _pumpTab(
      tester,
      filter: PostsFilter.all,
      posts: _serve([_post('p10', 'newest post', 10), _post('p5', 'older post', 5)], []),
      reposts: _serve([_repost('r8', 'reposted in between', 8)], []),
    );
    final newest = tester.getTopLeft(find.textContaining('newest post')).dy;
    final between = tester.getTopLeft(find.textContaining('reposted in between')).dy;
    final older = tester.getTopLeft(find.textContaining('older post')).dy;
    expect(newest, lessThan(between));
    expect(between, lessThan(older));
    await tester.pumpWidget(const SizedBox.shrink());
  });

  testWidgets('Posts leaves reposts out', (tester) async {
    final repostsAsked = <String?>[];
    await _pumpTab(
      tester,
      filter: PostsFilter.posts,
      posts: _serve([_post('p1', 'my own post', 9)], []),
      reposts: _serve([_repost('r1', 'a post I reposted', 8)], repostsAsked),
    );
    expect(find.textContaining('my own post'), findsOneWidget);
    expect(find.textContaining('a post I reposted'), findsNothing);
    expect(repostsAsked, isEmpty);
    await tester.pumpWidget(const SizedBox.shrink());
  });
}
