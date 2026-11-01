import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:provider/single_child_widget.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/ui/x_look_theme.dart';

const kThreadPost = RedditPost(
  id: 'abc123',
  title: 'Which app do you use for long threads?',
  subreddit: 'androidapps',
  permalink: '/r/androidapps/comments/abc123/thread/',
  author: 'op_writer',
  isSelf: true,
  score: 30,
  commentCount: 6,
);

const kThreadComments = [
  RedditComment(
    id: 'c1',
    author: 'first_reader',
    body: 'Top level body',
    score: 412,
    replies: [
      RedditComment(
        id: 'c1a',
        author: 'op_writer',
        isSubmitter: true,
        body: 'Reply body',
        score: 118,
        replies: [
          RedditComment(id: 'c1a1', author: 'third', body: 'Nested body', score: 4),
          RedditComment(id: 'more', body: '', permalink: '/r/androidapps/comments/abc123/thread/c1a/', moreCount: 7),
        ],
      ),
    ],
  ),
  RedditComment(id: 'c2', author: 'second_reader', body: 'Sibling body', score: 9),
];

/// One argument nested [max] deep, with long names and words, ending in a
/// "continue this thread" row.
List<RedditComment> deepRedditThread({int max = 9}) => [_chain(0, max)];

RedditComment _chain(int depth, int max) => RedditComment(
  id: 'deep$depth',
  author: 'a_really_long_reddit_username_that_keeps_going_$depth',
  body: 'Depth $depth says something long enough to wrap over several lines at a large text size.',
  score: 123456,
  createdAt: DateTime(2026, 1, 1),
  authorFlair: depth.isEven ? 'Long flair text on level $depth' : null,
  replies: [
    if (depth < max) _chain(depth + 1, max),
    if (depth == max)
      RedditComment(id: 'stub$depth', body: '', permalink: '/r/x/comments/y/z/$depth/', moreCount: 1234),
  ],
);

/// Serves [comments] for any thread, [filled] for a bare post, [more] for
/// held-back comments.
class FakeThreadClient extends RedditClient {
  final List<RedditComment> comments;
  final RedditPost? filled;
  final List<RedditComment> more;

  /// The order each request for comments asked for.
  final sorts = <String?>[];

  FakeThreadClient(this.comments, {this.filled, this.more = const []});

  @override
  Future<RedditPost?> fetchPost(
    String permalink, {
    required String clientId,
    String? userToken,
    bool preferPublic = false,
  }) async => filled;

  @override
  Future<List<RedditComment>> fetchMoreComments(RedditComment stub) async => more;

  @override
  Future<({List<RedditComment> comments, String? selfText, String? postUrl, List<String> postImages})> fetchComments(
    String permalink, {
    String? sort,
    required String clientId,
    String? userToken,
    bool preferPublic = false,
  }) async {
    sorts.add(sort);
    return (comments: comments, selfText: null, postUrl: null, postImages: const <String>[]);
  }
}

class _Icons extends RedditIcons {
  _Icons(super.client);

  @override
  Future<String?> iconFor(
    String subreddit, {
    String clientId = '',
    String? userToken,
    bool preferPublic = false,
  }) async => null;
}

/// Upvotes in memory: the store's own toggle writes to SQLite.
class MemoryVotes extends RedditVotesStore {
  @override
  Future<void> toggle(String id) async => update(state.contains(id) ? ({...state}..remove(id)) : {...state, id});
}

/// [home] with what a Reddit thread reads: the client, icons, votes, saved
/// posts and preferences, in [theme] and [locale], text at [scale].
Widget redditThreadApp(
  Widget home, {
  List<RedditComment> comments = kThreadComments,
  RedditPost? filled,
  List<RedditComment> more = const [],
  ThemeData? theme,
  Locale locale = const Locale('en'),
  double scale = 1,
  TextDirection direction = TextDirection.ltr,
  RedditVotesStore? votes,
  PrefServiceCache? prefs,
  FakeThreadClient? client,
  List<SingleChildWidget> providers = const [],
}) {
  final service = prefs ?? PrefServiceCache(cache: {optionPluginRedditThreadVisits: '{}'});
  client ??= FakeThreadClient(comments, filled: filled, more: more);
  return PrefService(
    service: service,
    child: MultiProvider(
      providers: [
        Provider<RedditClient>.value(value: client),
        Provider<RedditIcons>.value(value: _Icons(client)),
        Provider<RedditVotesStore>.value(value: votes ?? MemoryVotes()),
        Provider<RedditSavedStore>.value(value: RedditSavedStore(service)),
        Provider<RedditSubredditsStore>.value(value: RedditSubredditsStore(service)),
        ...providers,
      ],
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: theme ?? xLookLightTheme(null),
        locale: locale,
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        builder: (context, child) => MediaQuery(
          data: MediaQuery.of(context).copyWith(textScaler: TextScaler.linear(scale)),
          child: Directionality(textDirection: direction, child: child!),
        ),
        home: home,
      ),
    ),
  );
}

/// Pumps [app] on a phone [size] wide and settles it.
Future<void> pumpRedditThread(WidgetTester tester, Widget app, {Size size = const Size(390, 2400)}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(app);
  await tester.pumpAndSettle();
}
