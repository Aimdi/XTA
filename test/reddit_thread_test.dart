import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/plugin_comment_bubble.dart';
import 'package:xta/plugins/reddit/reddit_avatar.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_comments.dart';
import 'package:xta/plugins/reddit/reddit_thread_chrome.dart';
import 'package:xta/plugins/reddit/reddit_thread_comment.dart';
import 'package:xta/plugins/reddit/reddit_thread_navigation.dart';
import 'package:xta/plugins/reddit/reddit_thread_post.dart';
import 'package:xta/plugins/reddit/reddit_thread_screen.dart';
import 'package:xta/plugins/reddit/reddit_thread_visits.dart';
import 'package:xta/ui/contrast.dart';
import 'package:xta/ui/x_look_theme.dart';

import 'support/reddit_thread_harness.dart';

Finder _rowOf(String text) =>
    find.ancestor(of: find.textContaining(text), matching: find.byType(RedditCommentRow)).first;

Finder _linesOf(String text) =>
    find.ancestor(of: find.textContaining(text), matching: find.byType(RedditThreadLines)).first;

Future<void> _open(WidgetTester tester, {RedditPost post = kThreadPost, Size size = const Size(390, 2400)}) =>
    pumpRedditThread(tester, redditThreadApp(RedditThreadScreen(post: post)), size: size);

final _themes = {'light': xLookLightTheme(null), 'dim': xLookDimTheme(null), 'lights out': xLookLightsOutTheme(null)};

void main() {
  group('header and post', () {
    testWidgets('the bar names the subreddit and its menu opens the post sheet', (tester) async {
      await _open(tester);
      expect(find.descendant(of: find.byType(AppBar), matching: find.text('r/androidapps')), findsOneWidget);
      await tester.tap(find.descendant(of: find.byType(AppBar), matching: find.byTooltip('More')));
      await tester.pumpAndSettle();
      expect(find.text('Open in browser'), findsOneWidget);
      expect(find.text('Share link'), findsOneWidget);
    });

    testWidgets('the author row has a round avatar, then the title at 21', (tester) async {
      await _open(tester);
      final post = find.byType(RedditThreadPost);
      expect(find.descendant(of: post, matching: find.text('u/op_writer')), findsOneWidget);
      final avatar = tester.widget<RedditAvatar>(find.descendant(of: post, matching: find.byType(RedditAvatar)));
      expect((avatar.size, avatar.round), (22, true));
      final title = tester.widget<Text>(find.text('Which app do you use for long threads?'));
      expect(title.style?.fontSize, 21);
    });

    testWidgets('a link with a preview is a card with the picture over the domain', (tester) async {
      const post = RedditPost(
        id: 'link1',
        title: 'EU Commission backs Poland government',
        subreddit: 'europe',
        permalink: '/r/europe/comments/link1/eu/',
        url: 'https://www.yahoo.com/news/article',
        domain: 'yahoo.com',
        previewImage: 'https://preview.redd.it/x.jpg',
      );
      await _open(tester, post: post);
      final card = find.byType(RedditThreadLinkCard);
      expect(find.descendant(of: card, matching: find.text('yahoo.com')), findsOneWidget);
      expect(find.descendant(of: card, matching: find.byIcon(Icons.open_in_new)), findsOneWidget);
      final picture = find.descendant(of: card, matching: find.byType(SizedBox)).evaluate().map((e) => e.widget);
      expect(picture.whereType<SizedBox>().any((box) => box.height == RedditThreadLinkCard.pictureHeight), isTrue);
    });

    testWidgets('a link without a preview is one row: initial, domain, Open in browser', (tester) async {
      const post = RedditPost(
        id: 'link2',
        title: 'A link with only a thumbnail',
        subreddit: 'europe',
        permalink: '/r/europe/comments/link2/x/',
        url: 'https://news.example.org/a',
        domain: 'news.example.org',
        thumbnail: 'https://b.thumbs.redditmedia.com/t.jpg',
      );
      await _open(tester, post: post);
      final card = find.byType(RedditThreadLinkCard);
      expect(find.descendant(of: card, matching: find.text('n')), findsOneWidget);
      expect(find.descendant(of: card, matching: find.text('news.example.org')), findsOneWidget);
      expect(find.descendant(of: card, matching: find.text('Open in browser')), findsOneWidget);
      expect(tester.getSize(card).height, greaterThanOrEqualTo(56));
    });
  });

  group('actions under the post', () {
    testWidgets('the vote pill upvotes on this device and says so', (tester) async {
      final semantics = tester.ensureSemantics();
      await _open(tester);
      final pill = find.descendant(of: find.byType(RedditThreadPostActions), matching: find.byType(RedditUpvoteButton));
      expect(find.descendant(of: pill, matching: find.text('30')), findsOneWidget);
      expect(tester.getSemantics(pill), isSemantics(label: 'Upvote\n30', isButton: true, isToggled: false));

      await tester.tap(pill);
      await tester.pumpAndSettle();
      expect(find.descendant(of: pill, matching: find.text('31')), findsOneWidget);
      expect(tester.getSemantics(pill), isSemantics(isToggled: true));
      final score = tester.widget<Text>(find.descendant(of: pill, matching: find.text('31')));
      expect(score.style?.color, isNot(tester.widget<Text>(find.text('6')).style?.color));
      semantics.dispose();
    });

    testWidgets('the comments pill counts the comments and scrolls to them', (tester) async {
      const post = RedditPost(
        id: 'long',
        title: 'A long post',
        subreddit: 'androidapps',
        permalink: '/r/androidapps/comments/long/x/',
        isSelf: true,
        selfText: 'A paragraph that goes on.\n\nAnother paragraph.\n\nAnd one more.\n\nStill going.\n\nNearly there.',
        commentCount: 6,
      );
      await _open(tester, post: post, size: const Size(390, 520));
      expect(find.bySemanticsLabel('6 comments'), findsOneWidget);
      await tester.tap(find.bySemanticsLabel('6 comments'));
      await tester.pumpAndSettle();
      final appBar = tester.getRect(find.byType(AppBar));
      expect(tester.getTopLeft(find.byType(RedditCommentsHeader)).dy, moreOrLessEquals(appBar.bottom, epsilon: 1));
    });

    testWidgets('share and save sit at the end', (tester) async {
      await _open(tester);
      final actions = find.byType(RedditThreadPostActions);
      expect(find.descendant(of: actions, matching: find.byTooltip('Share link')), findsOneWidget);
      expect(find.descendant(of: actions, matching: find.byType(RedditThreadSaveButton)), findsOneWidget);
    });
  });

  group('comments header', () {
    testWidgets('counts the comments, sorts from a menu and folds all', (tester) async {
      final client = FakeThreadClient(kThreadComments);
      await pumpRedditThread(tester, redditThreadApp(const RedditThreadScreen(post: kThreadPost), client: client));
      final header = find.byType(RedditCommentsHeader);
      expect(find.descendant(of: header, matching: find.text('6 comments')), findsOneWidget);

      await tester.tap(find.descendant(of: header, matching: find.text('Best')));
      await tester.pumpAndSettle();
      for (final label in ['Best', 'Top', 'New', 'Controversial', 'Old', 'Q&A']) {
        expect(find.widgetWithText(CheckedPopupMenuItem<String>, label), findsOneWidget, reason: label);
      }
      await tester.tap(find.widgetWithText(CheckedPopupMenuItem<String>, 'New'));
      await tester.pumpAndSettle();
      expect(client.sorts.last, 'new');
      expect(find.descendant(of: header, matching: find.text('New')), findsOneWidget);

      await tester.tap(find.text('Collapse all'));
      await tester.pumpAndSettle();
      expect(find.text('Top level body'), findsNothing);
      expect(find.text('Sibling body'), findsOneWidget, reason: 'nothing to fold under it');
      await tester.tap(find.text('Expand all'));
      await tester.pumpAndSettle();
      expect(find.text('Top level body'), findsOneWidget);
    });
  });

  group('comment rows', () {
    testWidgets('replies hang off thread lines, a column further in, not in bubbles', (tester) async {
      await _open(tester);
      expect(find.byType(CommentBubble), findsNothing);
      expect(tester.widget<RedditThreadLines>(_linesOf('Top level body')).depth, 0);
      expect(tester.widget<RedditThreadLines>(_linesOf('Reply body')).depth, 1);
      expect(tester.widget<RedditThreadLines>(_linesOf('Nested body')).depth, 2);
      final top = tester.getTopLeft(find.text('Top level body')).dx;
      expect(tester.getTopLeft(find.text('Reply body')).dx - top, kRedditThreadColumn);
      expect(tester.getTopLeft(find.text('Nested body')).dx - top, 2 * kRedditThreadColumn);
    });

    testWidgets('each level has its colour, at 3:1 against the page', (tester) async {
      await _open(tester);
      final context = tester.element(find.text('Reply body'));
      final page = Theme.of(context).scaffoldBackgroundColor;
      final colours = {for (var level = 0; level < 4; level++) redditThreadLineColour(context, level)};
      expect(colours, hasLength(4));
      for (final colour in colours) {
        expect(contrastRatio(colour, page), greaterThanOrEqualTo(3));
      }
      expect(redditThreadLineColour(context, 4), redditThreadLineColour(context, 0));
      expect(redditThreadLines(30), kRedditThreadMaxLines);
    });

    testWidgets('a hairline separates top-level threads, not the first', (tester) async {
      await _open(tester);
      expect(tester.widget<RedditCommentRow>(_rowOf('Top level body')).divided, isFalse);
      expect(tester.widget<RedditCommentRow>(_rowOf('Reply body')).divided, isFalse);
      expect(tester.widget<RedditCommentRow>(_rowOf('Sibling body')).divided, isTrue);
      expect(find.descendant(of: _rowOf('Sibling body'), matching: find.byType(Divider)), findsOneWidget);
    });

    testWidgets('the header folds the comment and counts the replies it holds', (tester) async {
      final semantics = tester.ensureSemantics();
      await _open(tester);
      final header = find.text('u/first_reader');
      expect(tester.getSemantics(header), isSemantics(hasExpandedState: true, isExpanded: true, isButton: true));

      await tester.tap(header);
      await tester.pumpAndSettle();
      expect(find.text('Top level body'), findsNothing);
      expect(find.text('Reply body'), findsNothing);
      expect(find.text('+2 replies'), findsOneWidget);
      expect(tester.getSemantics(header), isSemantics(isExpanded: false));

      await tester.tap(header);
      await tester.pumpAndSettle();
      expect(find.text('Top level body'), findsOneWidget);
      expect(find.text('Reply body'), findsOneWidget);
      semantics.dispose();
    });

    testWidgets('the post author reads in the accent with OP and a round avatar', (tester) async {
      await _open(tester);
      final row = _rowOf('Reply body');
      expect(find.descendant(of: row, matching: find.text('OP')), findsOneWidget);
      final op = tester.widget<Text>(find.descendant(of: row, matching: find.text('u/op_writer')));
      final other = tester.widget<Text>(find.text('u/first_reader'));
      expect(op.style?.color, isNot(other.style?.color));
      expect(tester.widget<RedditAvatar>(find.descendant(of: row, matching: find.byType(RedditAvatar))).round, isTrue);
      expect(find.descendant(of: _rowOf('Top level body'), matching: find.text('OP')), findsNothing);
    });

    testWidgets('flair shows beside the name', (tester) async {
      await pumpRedditThread(
        tester,
        redditThreadApp(
          const RedditThreadScreen(post: kThreadPost),
          comments: const [RedditComment(id: 'f', author: 'flaired', body: 'With flair', authorFlair: 'Mod team')],
        ),
      );
      expect(find.descendant(of: _rowOf('With flair'), matching: find.text('Mod team')), findsOneWidget);
    });

    testWidgets('a comment upvotes on this device from its action row', (tester) async {
      await _open(tester);
      final upvote = find.descendant(of: _rowOf('Top level body'), matching: find.byType(RedditUpvoteButton)).first;
      expect(find.descendant(of: upvote, matching: find.text('412')), findsOneWidget);
      await tester.tap(upvote);
      await tester.pumpAndSettle();
      expect(find.descendant(of: upvote, matching: find.text('413')), findsOneWidget);
      final sibling = find.descendant(of: _rowOf('Sibling body'), matching: find.byType(RedditUpvoteButton));
      expect(
        find.descendant(of: sibling, matching: find.text('9')),
        findsOneWidget,
        reason: 'one comment at a time',
      );
    });

    testWidgets('removed comments say what happened, without an upvote or menu', (tester) async {
      await pumpRedditThread(
        tester,
        redditThreadApp(
          const RedditThreadScreen(post: kThreadPost),
          comments: const [
            RedditComment(id: 'r1', author: '[deleted]', body: '[removed]'),
            RedditComment(id: 'r2', author: '[deleted]', body: '[deleted]'),
            RedditComment(id: 'r3', body: '[ removed by Reddit ]'),
            RedditComment(id: 'r4', author: 'kept', body: '[unavailable]'),
          ],
        ),
      );
      for (final reason in [
        'Comment removed by moderators',
        'Comment deleted',
        'Comment removed by Reddit',
        'Comment unavailable',
      ]) {
        final text = tester.widget<Text>(find.text(reason));
        expect(text.style?.fontStyle, FontStyle.italic, reason: reason);
      }
      expect(find.byType(RedditUpvoteButton), findsNWidgets(1), reason: 'only the post');
      expect(find.byIcon(Icons.more_horiz), findsNothing);
    });

    testWidgets('a comment written since the last visit says it is new', (tester) async {
      final visit = DateTime.now().subtract(const Duration(hours: 1));
      final prefs = PrefServiceCache(
        cache: {
          optionPluginRedditThreadVisits:
              '{"${redditThreadKey(kThreadPost.permalink)}":${visit.millisecondsSinceEpoch}}',
        },
      );
      await pumpRedditThread(
        tester,
        redditThreadApp(
          const RedditThreadScreen(post: kThreadPost),
          prefs: prefs,
          comments: [
            RedditComment(
              id: 'n1',
              author: 'late',
              body: 'Written since',
              createdAt: DateTime.now().subtract(const Duration(minutes: 21)),
            ),
            RedditComment(
              id: 'o1',
              author: 'early',
              body: 'Written before',
              createdAt: DateTime.now().subtract(const Duration(hours: 3)),
            ),
          ],
        ),
      );
      expect(find.descendant(of: _rowOf('Written since'), matching: find.textContaining(', new')), findsOneWidget);
      expect(find.descendant(of: _rowOf('Written before'), matching: find.textContaining(', new')), findsNothing);
      final recorded = redditLastThreadVisit(prefs, kThreadPost.permalink)!;
      expect(recorded.isAfter(visit), isTrue, reason: 'this visit is noted for the next one');
    });

    testWidgets('the menu reaches the author and copies the words', (tester) async {
      await _open(tester);
      await tester.tap(find.descendant(of: _rowOf('Top level body'), matching: find.byIcon(Icons.more_horiz)));
      await tester.pumpAndSettle();
      expect(find.text('u/first_reader'), findsNWidgets(2));
      expect(find.text('Copy'), findsOneWidget);
    });
  });

  group('held-back comments', () {
    testWidgets('deeper ones continue on their own page', (tester) async {
      await _open(tester);
      final row = find.ancestor(of: find.text('Continue thread'), matching: find.byType(RedditHeldBackRow));
      expect(tester.widget<RedditHeldBackRow>(row).depth, 2);
      final button = find.ancestor(of: find.text('Continue thread'), matching: find.byType(TextButton));
      expect(tester.getSize(button).height, greaterThanOrEqualTo(48));
      await tester.tap(find.text('Continue thread'));
      await tester.pumpAndSettle();
      expect(find.byType(RedditThreadScreen, skipOffstage: false), findsNWidgets(2));
    });

    testWidgets('the next top-level ones load into the thread', (tester) async {
      final app = redditThreadApp(
        const RedditThreadScreen(post: kThreadPost),
        comments: const [
          RedditComment(id: 'c1', author: 'first_reader', body: 'Top level body', score: 3),
          RedditComment(
            id: 'more-root',
            body: '',
            permalink: '/r/androidapps/comments/abc123/thread/',
            moreCount: -1,
            nextPage: (path: '/svc/shreddit/more-comments/androidapps/t3_abc123?top-level=1', cursor: 'c'),
          ),
        ],
        more: const [RedditComment(id: 'c11', author: 'eleventh', body: 'The eleventh comment', score: 1)],
      );
      await pumpRedditThread(tester, app);
      await tester.tap(find.text('Load more comments'));
      await tester.pumpAndSettle();
      expect(find.text('The eleventh comment'), findsOneWidget);
      expect(find.text('Load more comments'), findsNothing, reason: 'Reddit held nothing more back');
      expect(find.byType(RedditThreadScreen), findsOneWidget, reason: 'it stays in this thread');
    });
  });

  group('jump to the next comment', () {
    List<RedditComment> longThread() => [
      for (var i = 0; i < 5; i++)
        RedditComment(
          id: 't$i',
          author: 'writer$i',
          body: List.filled(18, 'A long line of argument number $i.').join('\n'),
          replies: [RedditComment(id: 'r$i', author: 'reply$i', body: 'A reply to $i')],
        ),
    ];

    testWidgets('lands each top-level comment at the top in turn, then the end', (tester) async {
      await pumpRedditThread(
        tester,
        redditThreadApp(const RedditThreadScreen(post: kThreadPost), comments: longThread()),
        size: const Size(390, 844),
      );
      final top = tester.getRect(find.byType(AppBar)).bottom;
      for (var i = 0; i < 5; i++) {
        await tester.tap(find.byType(RedditJumpButton));
        await tester.pumpAndSettle();
        expect(tester.getTopLeft(_rowOf('u/writer$i')).dy, moreOrLessEquals(top, epsilon: 1), reason: 'writer$i');
      }
      await tester.tap(find.byType(RedditJumpButton));
      await tester.pumpAndSettle();
      expect(find.text('A reply to 4'), findsOneWidget);
    });

    testWidgets('shows only once there are two top-level comments', (tester) async {
      await pumpRedditThread(
        tester,
        redditThreadApp(
          const RedditThreadScreen(post: kThreadPost),
          comments: const [RedditComment(id: 'only', author: 'solo', body: 'Alone')],
        ),
      );
      expect(find.byType(RedditJumpButton), findsNothing);
    });

    test('the next target is the nearest one below the top', () {
      expect(redditNextTargetOffset([100, 400, 900], 0), 100);
      expect(redditNextTargetOffset([100, 400, 900], 100), 400);
      expect(redditNextTargetOffset([100, 400, 900], 100.5), 400, reason: 'the one at the top is passed over');
      expect(redditNextTargetOffset([100, 400], 400), isNull);
    });
  });

  group('every size', () {
    testWidgets('every target is 48 dp', (tester) async {
      final semantics = tester.ensureSemantics();
      await _open(tester);
      await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
      semantics.dispose();
    });

    for (final (name, direction) in [('ltr', TextDirection.ltr), ('rtl', TextDirection.rtl)]) {
      for (final MapEntry(key: theme, value: data) in _themes.entries) {
        testWidgets('320dp, 2x text, $name, $theme', (tester) async {
          final app = redditThreadApp(
            const RedditThreadScreen(post: kThreadPost),
            comments: deepRedditThread(),
            theme: data,
            scale: 2,
            direction: direction,
          );
          await pumpRedditThread(tester, app, size: const Size(320, 12000));
          expect(tester.takeException(), isNull);
          expect(find.textContaining('Depth 9 '), findsOneWidget);
          expect(find.text('Continue thread'), findsOneWidget);
        });
      }
    }
  });

  group('last visits', () {
    test('keep the most recent threads', () {
      final visits = {for (var i = 0; i < kRedditThreadVisitsKept + 5; i++) '/r/x/comments/$i': i};
      final kept = redditKeepRecentVisits(visits);
      expect(kept, hasLength(kRedditThreadVisitsKept));
      expect(kept.containsKey('/r/x/comments/0'), isFalse);
      expect(kept.containsKey('/r/x/comments/${kRedditThreadVisitsKept + 4}'), isTrue);
    });

    test('one thread however its permalink ends', () async {
      final prefs = PrefServiceCache(cache: {optionPluginRedditThreadVisits: '{}'});
      final at = DateTime.fromMillisecondsSinceEpoch(1760000000000);
      await redditRecordThreadVisit(prefs, '/r/x/comments/a/', at);
      expect(redditLastThreadVisit(prefs, '/r/x/comments/a'), at);
      expect(redditLastThreadVisit(prefs, '/r/x/comments/b/'), isNull);
    });

    test('nothing is new on a first visit', () {
      final comment = RedditComment(id: 'x', body: 'Hi', createdAt: DateTime(2026));
      expect(redditCommentIsNew(comment, null), isFalse);
      expect(redditCommentIsNew(comment, DateTime(2025)), isTrue);
      expect(redditCommentIsNew(comment, DateTime(2027)), isFalse);
    });
  });
}
