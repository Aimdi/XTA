import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_story_screen.dart';
import 'package:xta/plugins/plugin_comment_bubble.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_store.dart';
import 'package:xta/plugins/reddit/reddit_subreddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_thread_screen.dart';
import 'package:xta/plugins/reddit/reddit_votes_store.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_look_theme.dart';

const _post = RedditPost(
  id: 'abc123',
  title: 'Which app do you use for long threads?',
  subreddit: 'androidapps',
  permalink: '/r/androidapps/comments/abc123/thread/',
  author: 'op_writer',
  isSelf: true,
);

const _reddit = [
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

final _deepReddit = [_redditChain(0, 9)];

RedditComment _redditChain(int depth, int max) => RedditComment(
  id: 'deep$depth',
  author: 'a_really_long_reddit_username_that_keeps_going_$depth',
  body: 'Depth $depth says something long enough to wrap over several lines at a large text size.',
  score: 123456,
  createdAt: DateTime(2026, 1, 1),
  replies: [
    if (depth < max) _redditChain(depth + 1, max),
    if (depth == max)
      RedditComment(id: 'stub$depth', body: '', permalink: '/r/x/comments/y/z/$depth/', moreCount: 1234),
  ],
);

const _story = HnStory(id: 1, title: 'Show HN: A reader', author: 'maker', score: 10);

const _hn = [
  HnComment(
    id: 1,
    author: 'alice',
    text: 'Top level text',
    children: [
      HnComment(id: 11, author: 'bob', text: 'Reply text'),
      HnComment(id: 12, deleted: true),
    ],
  ),
  HnComment(id: 2, author: 'carol', text: 'Sibling text'),
];

final _deepHn = [_hnChain(0, 9)];

HnComment _hnChain(int depth, int max) => HnComment(
  id: 100 + depth,
  author: 'a_really_long_hacker_news_username_$depth',
  text: 'Depth $depth says something long enough to wrap over several lines at a large text size.',
  createdAt: DateTime(2026, 1, 1),
  children: [if (depth < max) _hnChain(depth + 1, max)],
);

class _Reddit extends RedditClient {
  final List<RedditComment> comments;
  _Reddit(this.comments);

  @override
  Future<({List<RedditComment> comments, String? selfText, String? postUrl, List<String> postImages})> fetchComments(
    String permalink, {
    String? sort,
    required String clientId,
    String? userToken,
    bool preferPublic = false,
  }) async => (comments: comments, selfText: null, postUrl: null, postImages: const <String>[]);
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

class _Hn extends HackerNewsClient {
  final List<HnComment> comments;
  _Hn(this.comments);

  @override
  Future<(HnStory, List<HnComment>)> thread(int id) async => (_story, comments);
}

Widget _app(
  Widget home, {
  List<RedditComment> reddit = _reddit,
  List<HnComment> hn = _hn,
  ThemeData? theme,
  double scale = 1,
  TextDirection direction = TextDirection.ltr,
}) {
  final prefs = PrefServiceCache();
  final client = _Reddit(reddit);
  return PrefService(
    service: prefs,
    child: MultiProvider(
      providers: [
        Provider<RedditClient>.value(value: client),
        Provider<RedditIcons>.value(value: _Icons(client)),
        Provider<RedditVotesStore>.value(value: RedditVotesStore()),
        Provider<RedditSavedStore>.value(value: RedditSavedStore(prefs)),
        Provider<HackerNewsClient>.value(value: _Hn(hn)),
      ],
      child: MaterialApp(
        theme: theme ?? xLookLightTheme(null),
        locale: const Locale('en'),
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

Future<void> _pump(WidgetTester tester, Widget app, {Size size = const Size(390, 2400)}) async {
  tester.view.physicalSize = size;
  tester.view.devicePixelRatio = 1;
  addTearDown(tester.view.reset);
  await tester.pumpWidget(app);
  await tester.pumpAndSettle();
}

Finder _bubbleOf(String text) =>
    find.ancestor(of: find.textContaining(text), matching: find.byType(CommentBubble)).first;

Color? _fillOf(WidgetTester tester, String text) =>
    tester.widget<Material>(find.descendant(of: _bubbleOf(text), matching: find.byType(Material)).first).color;

final _themes = {'light': xLookLightTheme(null), 'dim': xLookDimTheme(null), 'lights out': xLookLightsOutTheme(null)};

void main() {
  group('bubble colours', () {
    for (final MapEntry(key: name, value: theme) in _themes.entries) {
      test('every tint keeps text at 4.5:1 in $name', () {
        for (var depth = 0; depth < commentBubbleHues.length; depth++) {
          for (final outlined in [false, true]) {
            final colors = CommentBubbleColors.of(theme, depth, outlined: outlined);
            for (final foreground in [colors.text, colors.muted, colors.accent]) {
              expect(contrastRatio(foreground, colors.fill), greaterThanOrEqualTo(4.5), reason: 'depth $depth');
            }
            expect(colors.text, theme.colorScheme.onSurface, reason: 'body text stays on onSurface');
          }
        }
      });

      test('each depth in a cycle gets its own tint in $name', () {
        final fills = {
          for (var depth = 0; depth < commentBubbleHues.length; depth++) CommentBubbleColors.of(theme, depth).fill,
        };
        expect(fills, hasLength(commentBubbleHues.length));
        expect(CommentBubbleColors.of(theme, commentBubbleHues.length).fill, CommentBubbleColors.of(theme, 0).fill);
      });
    }

    test('indentation stops at the cap', () {
      expect(commentIndent(1), kCommentIndentPerLevel);
      expect(commentIndent(30), kCommentIndentPerLevel * kCommentMaxIndentDepth);
    });
  });

  group('Reddit thread', () {
    testWidgets('every comment sits in its own tinted bubble', (tester) async {
      await _pump(tester, _app(const RedditThreadScreen(post: _post)));

      for (final body in ['Top level body', 'Reply body', 'Nested body', 'Sibling body']) {
        expect(_bubbleOf(body), findsOneWidget, reason: body);
      }
      expect(find.byType(CommentBubble), findsNWidgets(5));
      expect(_fillOf(tester, 'Top level body'), isNot(_fillOf(tester, 'Reply body')));
      expect(_fillOf(tester, 'Top level body'), _fillOf(tester, 'Sibling body'));
      final material = tester.widget<Material>(
        find.descendant(of: _bubbleOf('Top level body'), matching: find.byType(Material)).first,
      );
      expect(material.clipBehavior, Clip.antiAlias);
      expect(material.shape, isA<RoundedRectangleBorder>());
    });

    testWidgets('OP keeps its accent colour', (tester) async {
      await _pump(tester, _app(const RedditThreadScreen(post: _post)));

      final op = tester.widget<Text>(find.descendant(of: _bubbleOf('Reply body'), matching: find.text('u/op_writer')));
      final other = tester.widget<Text>(find.text('u/first_reader'));
      expect(op.style?.color, isNot(other.style?.color));
    });

    testWidgets('folding hides the body and shows how much it holds', (tester) async {
      await _pump(tester, _app(const RedditThreadScreen(post: _post)));

      await tester.tap(find.text('Top level body'));
      await tester.pumpAndSettle();
      expect(find.text('Top level body'), findsNothing);
      expect(find.text('Reply body'), findsNothing);
      expect(find.text('+4'), findsOneWidget);
      expect(tester.getSize(_bubbleOf('first_reader')).height, greaterThanOrEqualTo(48));

      await tester.tap(find.text('+4'));
      await tester.pumpAndSettle();
      expect(find.text('Top level body'), findsOneWidget);
      expect(find.text('Reply body'), findsOneWidget);
    });

    testWidgets('the more-replies stub is an outlined, tappable pill', (tester) async {
      await _pump(tester, _app(const RedditThreadScreen(post: _post)));

      final stub = find.textContaining('More replies');
      final bubble = tester.widget<CommentBubble>(_bubbleOf('More replies'));
      expect(bubble.outlined, isTrue);
      final ink = find.ancestor(of: stub, matching: find.byType(InkWell)).first;
      expect(tester.widget<InkWell>(ink).onTap, isNotNull);
      expect(tester.getSize(ink).height, greaterThanOrEqualTo(48));

      await tester.tap(stub);
      await tester.pumpAndSettle();
      expect(find.byType(RedditThreadScreen, skipOffstage: false), findsNWidgets(2));
    });
  });

  group('Hacker News thread', () {
    testWidgets('every comment sits in its own tinted bubble', (tester) async {
      await _pump(tester, _app(const HnStoryScreen(story: _story)));

      for (final text in ['Top level text', 'Reply text', 'Sibling text']) {
        expect(_bubbleOf(text), findsOneWidget, reason: text);
      }
      expect(find.byType(CommentBubble), findsNWidgets(4));
      expect(_fillOf(tester, 'Top level text'), isNot(_fillOf(tester, 'Reply text')));
      expect(tester.widget<CommentBubble>(_bubbleOf('[deleted]')).outlined, isTrue);
    });

    testWidgets('folding hides the body and names the hidden replies', (tester) async {
      await _pump(tester, _app(const HnStoryScreen(story: _story)));

      await tester.tap(find.text('Top level text'));
      await tester.pumpAndSettle();
      expect(find.text('Top level text'), findsNothing);
      expect(find.text('Reply text'), findsNothing);
      expect(find.textContaining('2 comments'), findsOneWidget);

      await tester.tap(find.textContaining('alice'));
      await tester.pumpAndSettle();
      expect(find.text('Top level text'), findsOneWidget);
    });
  });

  group('narrow screens and large text', () {
    for (final (name, direction) in [('ltr', TextDirection.ltr), ('rtl', TextDirection.rtl)]) {
      for (final MapEntry(key: theme, value: data) in _themes.entries) {
        testWidgets('Reddit at 320dp, 2x text, $name, $theme', (tester) async {
          final app = _app(
            const RedditThreadScreen(post: _post),
            reddit: _deepReddit,
            theme: data,
            scale: 2,
            direction: direction,
          );
          await _pump(tester, app, size: const Size(320, 12000));
          expect(tester.takeException(), isNull);
          expect(find.textContaining('Depth 9 '), findsOneWidget);
          expect(find.textContaining('More replies'), findsOneWidget);
        });

        testWidgets('Hacker News at 320dp, 2x text, $name, $theme', (tester) async {
          final app = _app(
            const HnStoryScreen(story: _story),
            hn: _deepHn,
            theme: data,
            scale: 2,
            direction: direction,
          );
          await _pump(tester, app, size: const Size(320, 12000));
          expect(tester.takeException(), isNull);
          expect(find.textContaining('Depth 9 '), findsOneWidget);
        });
      }
    }
  });
}
