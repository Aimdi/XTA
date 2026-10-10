import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_emoji.dart';
import 'package:xta/plugins/pixiv/pixiv_comment_models.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_api.dart';
import 'package:xta/plugins/pixiv/pixiv_comments_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/plugin_comment_bubble.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

import 'support/pixiv_comments_fake.dart';
import 'support/pixiv_reader_harness.dart';

const _work = PixivCommentTarget.illust(120);
const _stamp = 'https://s.pximg.net/common/images/stamp/generated-stamps/301_s.jpg';

Finder _image(String url) => find.byWidgetPredicate((widget) => widget is PixivNetworkImage && widget.url == url);

Finder _comment(int id) => find.byKey(ValueKey('pixiv-comment-$id'));

/// A screen with one button that opens [target]'s comments, tapped and left
/// before the comments arrive.
Future<FakePixivCommentsApi> _openComments(
  WidgetTester tester,
  Map<String, List<PixivCommentPage?>> pages, {
  PixivCommentTarget target = _work,
  double textScale = 1,
}) async {
  final api = FakePixivCommentsApi(pages);
  await pumpPixiv(
    tester,
    Scaffold(
      body: Builder(
        builder: (context) =>
            TextButton(onPressed: () => openPixivComments(context, target), child: const Text('comments')),
      ),
    ),
    textScale: textScale,
    extraProviders: [Provider<PixivCommentsApi>.value(value: api)],
  );
  await tester.tap(find.text('comments'));
  return api;
}

Future<FakePixivCommentsApi> _pumpComments(
  WidgetTester tester,
  Map<String, List<PixivCommentPage?>> pages, {
  PixivCommentTarget target = _work,
  double textScale = 1,
}) async {
  final api = await _openComments(tester, pages, target: target, textScale: textScale);
  await settlePixiv(tester);
  return api;
}

/// Pumps the first frames of a screen opening, checking [finder] in none of them.
Future<void> _neverDuringOpening(WidgetTester tester, Finder finder) async {
  for (var frame = 0; frame < 8; frame++) {
    await tester.pump(const Duration(milliseconds: 16));
    expect(finder, findsNothing, reason: 'frame $frame');
  }
}

/// Records what the app puts on the clipboard.
List<String?> _clipboard(WidgetTester tester) {
  final copied = <String?>[];
  final messenger = tester.binding.defaultBinaryMessenger;
  messenger.setMockMethodCallHandler(SystemChannels.platform, (call) async {
    if (call.method == 'Clipboard.setData') copied.add((call.arguments as Map)['text'] as String?);
    return null;
  });
  addTearDown(() => messenger.setMockMethodCallHandler(SystemChannels.platform, null));
  return copied;
}

Map<String, List<PixivCommentPage?>> _firstPage(List<PixivComment> comments, {String? nextUrl}) => {
  FakePixivCommentsApi.commentsKey(_work): [PixivCommentPage(comments, nextUrl: nextUrl)],
};

Future<void> _muteFromMenu(WidgetTester tester, int commentId, String action) async {
  await tester.tap(find.descendant(of: _comment(commentId), matching: find.byTooltip('Comment options')));
  await settlePixiv(tester);
  await tester.tap(find.text(action).last);
  await settlePixiv(tester);
  await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.byType(FilledButton)));
  await settlePixiv(tester);
}

PixivMuteState _mutes(WidgetTester tester) =>
    Provider.of<PixivMuteStore>(tester.element(find.text('comments', skipOffstage: false)), listen: false).state;

void main() {
  testWidgets('shows text with inline emoji, a sticker and whom a reply answers, and offers no composer', (
    tester,
  ) async {
    final api = await _pumpComments(
      tester,
      _firstPage([
        pixivTestComment(1, text: 'Lovely colours (heart)'),
        pixivTestComment(
          2,
          text: '',
          stampUrl: _stamp,
          user: pixivCommenter(7, name: 'Rin'),
        ),
        pixivTestComment(
          3,
          text: 'Agreed',
          replyTo: pixivCommenter(7, name: 'Rin'),
        ),
      ]),
    );

    expect(api.calls, ['illust:120@first']);
    expect(find.text('Comments'), findsOneWidget);
    expect(find.textContaining('Lovely colours'), findsOneWidget);
    expect(_image(pixivEmojiUrl(501)), findsOneWidget);
    expect(_image(_stamp), findsOneWidget);
    final semantics = tester.ensureSemantics();
    await tester.pump();
    expect(find.bySemanticsLabel('Sticker'), findsOneWidget);
    semantics.dispose();
    expect(find.textContaining('To Rin'), findsOneWidget);
    expect(find.byType(CommentBubble), findsNWidgets(3));
    expect(find.descendant(of: _comment(1), matching: find.byType(SelectionArea)), findsOneWidget);
    expect(find.byType(TextField), findsNothing);
    expect(find.byType(EditableText), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('View replies opens the thread with its comment on top', (tester) async {
    final parent = pixivTestComment(1, text: 'Which brush?', hasReplies: true);
    final api = await _pumpComments(tester, {
      ..._firstPage([parent, pixivTestComment(2, text: 'No replies here')]),
      FakePixivCommentsApi.repliesKey(_work, 1): [
        PixivCommentPage([
          pixivTestComment(10, text: 'A round one', replyTo: pixivCommenter(42)),
          pixivTestComment(11, text: 'Thanks'),
        ]),
      ],
    });
    expect(find.text('View replies'), findsOneWidget);

    await tester.tap(find.text('View replies'));
    await settlePixiv(tester);

    expect(api.calls.last, 'illust:120/1@first');
    expect(find.text('Replies'), findsOneWidget);
    Offset bubble(int id) =>
        tester.getTopLeft(find.descendant(of: _comment(id), matching: find.byType(Material)).first);
    expect(bubble(10).dy, greaterThan(bubble(1).dy));
    expect(bubble(10).dx, greaterThan(bubble(1).dx));
    expect(find.text('View replies'), findsNothing);
    expect(find.byType(TextField), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('a thread without replies says so under its comment', (tester) async {
    final parent = pixivTestComment(1, hasReplies: true);
    await _pumpComments(tester, _firstPage([parent]));
    await tester.tap(find.text('View replies'));
    await settlePixiv(tester);
    expect(_comment(1), findsOneWidget);
    expect(find.text('No replies yet'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a comment linking outside Pixiv waits behind Show', (tester) async {
    await _pumpComments(
      tester,
      _firstPage([
        pixivTestComment(1, text: 'Free coins at https://bit.ly/x'),
        pixivTestComment(2, text: 'Source: https://www.pixiv.net/artworks/9'),
      ]),
    );
    expect(find.textContaining('Free coins'), findsNothing);
    expect(find.text('Hidden: links outside Pixiv'), findsOneWidget);
    expect(find.textContaining('Source:'), findsOneWidget);

    await tester.tap(find.text('Show'));
    await settlePixiv(tester);
    expect(find.textContaining('Free coins'), findsOneWidget);
    expect(find.text('Hidden: links outside Pixiv'), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('a comment shown past its link note stays shown in its thread', (tester) async {
    await _pumpComments(tester, {
      ..._firstPage([pixivTestComment(1, text: 'Prints at https://shop.example/x', hasReplies: true)]),
      FakePixivCommentsApi.repliesKey(_work, 1): [
        PixivCommentPage([pixivTestComment(10, text: 'Thanks')]),
      ],
    });
    await tester.tap(find.text('Show'));
    await settlePixiv(tester);
    await tester.tap(find.text('View replies'));
    await settlePixiv(tester);

    expect(find.text('Replies'), findsOneWidget);
    expect(find.textContaining('Prints at'), findsOneWidget);
    expect(find.text('Hidden: links outside Pixiv'), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('copying a comment keeps its emoji as their codes', (tester) async {
    final copied = _clipboard(tester);
    await _pumpComments(tester, _firstPage([pixivTestComment(1, text: 'Lovely (heart) colours (star)')]));
    final text = tester.element(find.descendant(of: _comment(1), matching: find.byType(PixivCommentText)));
    Actions.invoke(text, const SelectAllTextIntent(SelectionChangedCause.keyboard));
    await tester.pump();
    Actions.invoke(text, CopySelectionTextIntent.copy);
    await tester.pump();
    expect(copied, ['Lovely (heart) colours (star)']);
    await disposePixiv(tester);
  });

  testWidgets('muting a comment asks first and hides it', (tester) async {
    await _pumpComments(tester, _firstPage([pixivTestComment(1, text: 'Spam'), pixivTestComment(2, text: 'Kind')]));

    await _muteFromMenu(tester, 1, 'Mute this comment');
    expect(_mutes(tester).commentIds, {1});
    expect(_comment(1), findsNothing);
    expect(_comment(2), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('muting an author hides every comment they wrote', (tester) async {
    final rin = pixivCommenter(7, name: 'Rin');
    await _pumpComments(
      tester,
      _firstPage([pixivTestComment(1, user: rin), pixivTestComment(2), pixivTestComment(3, user: rin)]),
    );

    await _muteFromMenu(tester, 1, 'Mute Rin');
    expect(_mutes(tester).authorIds, {7});
    expect(_comment(1), findsNothing);
    expect(_comment(3), findsNothing);
    expect(_comment(2), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a comment without an author can only be muted itself', (tester) async {
    await _pumpComments(tester, _firstPage([pixivTestComment(1, anonymous: true)]));
    expect(find.textContaining('Unknown user'), findsOneWidget);
    await tester.tap(find.byTooltip('Comment options'));
    await settlePixiv(tester);
    expect(find.text('Mute this comment'), findsOneWidget);
    expect(find.textContaining('Mute Unknown'), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('muting the comment a thread hangs off leaves the thread', (tester) async {
    await _pumpComments(tester, {
      ..._firstPage([pixivTestComment(1, hasReplies: true), pixivTestComment(2)]),
      FakePixivCommentsApi.repliesKey(_work, 1): [
        PixivCommentPage([pixivTestComment(10)]),
      ],
    });
    await tester.tap(find.text('View replies'));
    await settlePixiv(tester);

    await _muteFromMenu(tester, 1, 'Mute this comment');
    expect(find.text('Replies'), findsNothing);
    expect(find.text('Comments'), findsOneWidget);
    expect(_comment(1), findsNothing);
    expect(_comment(2), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('opening shows the skeleton, never a passing "No comments yet" or "No replies yet"', (tester) async {
    await _openComments(tester, {
      ..._firstPage([pixivTestComment(1, text: 'Hello', hasReplies: true)]),
      FakePixivCommentsApi.repliesKey(_work, 1): [
        PixivCommentPage([pixivTestComment(10, text: 'Hi back')]),
      ],
    });
    await _neverDuringOpening(tester, find.text('No comments yet'));
    await settlePixiv(tester);
    expect(find.textContaining('Hello'), findsOneWidget);

    await tester.tap(find.text('View replies'));
    await _neverDuringOpening(tester, find.text('No replies yet'));
    await settlePixiv(tester);
    expect(find.textContaining('Hi back'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a later page that fails keeps the comments and offers Retry instead of a spinner', (tester) async {
    final api = await _pumpComments(tester, {
      FakePixivCommentsApi.commentsKey(_work): [
        PixivCommentPage([pixivTestComment(1)], nextUrl: 'next-1'),
        null,
        PixivCommentPage([pixivTestComment(2)]),
      ],
    });
    expect(api.calls, ['illust:120@first', 'illust:120@next-1']);
    expect(_comment(1), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    expect(find.text('Could not reach Pixiv'), findsOneWidget);

    await tester.drag(find.byType(FeedListView), const Offset(0, -200));
    await settlePixiv(tester);
    expect(api.calls, hasLength(2), reason: 'scrolling does not retry by itself');

    await tester.tap(find.text('Retry'));
    await settlePixiv(tester);
    expect(api.calls.last, 'illust:120@next-1');
    expect(_comment(1), findsOneWidget);
    expect(_comment(2), findsOneWidget);
    expect(find.text('Retry'), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('scrolling toward the end of a long page asks for the next', (tester) async {
    final api = await _pumpComments(tester, {
      FakePixivCommentsApi.commentsKey(_work): [
        PixivCommentPage([for (var id = 1; id <= 40; id++) pixivTestComment(id)], nextUrl: 'next-1'),
        PixivCommentPage([pixivTestComment(41, text: 'The last one')]),
      ],
    });
    expect(api.calls, ['illust:120@first']);

    final position = tester.state<ScrollableState>(find.byType(Scrollable).last).position;
    position.jumpTo(position.maxScrollExtent - 700);
    await settlePixiv(tester);
    expect(api.calls, ['illust:120@first', 'illust:120@next-1']);
    await tester.drag(find.byType(FeedListView), const Offset(0, -2000));
    await settlePixiv(tester);
    expect(find.textContaining('The last one'), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('pulling down reloads the comments', (tester) async {
    final api = await _pumpComments(tester, {
      FakePixivCommentsApi.commentsKey(_work): [
        PixivCommentPage([pixivTestComment(1, text: 'Before')]),
        PixivCommentPage([pixivTestComment(2, text: 'After')]),
      ],
    });
    await tester.fling(find.byType(FeedListView), const Offset(0, 400), 1000);
    await settlePixiv(tester);
    expect(api.calls, ['illust:120@first', 'illust:120@first']);
    expect(_comment(2), findsOneWidget);
    expect(_comment(1), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('a short first page reaches for the next one by itself', (tester) async {
    final api = await _pumpComments(tester, {
      FakePixivCommentsApi.commentsKey(_work): [
        PixivCommentPage([pixivTestComment(1)], nextUrl: 'next-1'),
        PixivCommentPage([pixivTestComment(2)]),
      ],
    });
    expect(api.calls, ['illust:120@first', 'illust:120@next-1']);
    expect(_comment(2), findsOneWidget);
    expect(find.byType(CircularProgressIndicator), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('no comments says so, and a failed load offers Retry', (tester) async {
    await _pumpComments(tester, {});
    expect(find.text('No comments yet'), findsOneWidget);
    await disposePixiv(tester);

    final api = await _pumpComments(tester, {
      FakePixivCommentsApi.commentsKey(_work): [
        null,
        PixivCommentPage([pixivTestComment(1)]),
      ],
    });
    expect(find.byType(FullPageErrorWidget), findsOneWidget);
    await tester.tap(find.text('Retry'));
    await settlePixiv(tester);
    expect(_comment(1), findsOneWidget);
    expect(api.calls, hasLength(2));
    await disposePixiv(tester);
  });

  testWidgets('the novel target reads the novel\'s comments and replies', (tester) async {
    const novel = PixivCommentTarget.novel(55);
    final api = await _pumpComments(tester, {
      FakePixivCommentsApi.commentsKey(novel): [
        PixivCommentPage([pixivTestComment(1, hasReplies: true)]),
      ],
    }, target: novel);
    await tester.tap(find.text('View replies'));
    await settlePixiv(tester);
    expect(api.calls, ['novel:55@first', 'novel:55/1@first']);
    await disposePixiv(tester);
  });

  testWidgets('large text keeps every comment inside the screen', (tester) async {
    await _pumpComments(
      tester,
      _firstPage([
        pixivTestComment(1, text: 'A rather long comment about the light (heart)(star)', hasReplies: true),
        pixivTestComment(2, text: 'https://bit.ly/x'),
        pixivTestComment(
          3,
          text: '',
          stampUrl: _stamp,
          replyTo: pixivCommenter(9, name: 'Someone with a long name'),
        ),
      ]),
      textScale: 2,
    );
    expect(tester.takeException(), isNull);
    expect(find.text('View replies'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('every control is a labelled, finger-sized target', (tester) async {
    final semantics = tester.ensureSemantics();
    await _pumpComments(
      tester,
      _firstPage([
        pixivTestComment(1, text: 'Which brush? (normal)', hasReplies: true),
        pixivTestComment(2, text: 'https://bit.ly/x'),
        pixivTestComment(3, text: '', stampUrl: _stamp),
      ]),
    );
    await expectLater(tester, meetsGuideline(androidTapTargetGuideline));
    await expectLater(tester, meetsGuideline(labeledTapTargetGuideline));
    expect(
      tester.getSemantics(find.descendant(of: _comment(1), matching: find.bySemanticsLabel('Mika'))),
      isSemantics(label: 'Mika', isButton: true, hasTapAction: true),
    );
    semantics.dispose();
    await disposePixiv(tester);
  });

  testWidgets('the artwork detail shows the comment count and opens the comments', (tester) async {
    final api = FakePixivCommentsApi(_firstPage([pixivTestComment(1, text: 'First!')]));
    const work = PixivIllust(
      id: 120,
      title: 'Sommerfest',
      caption: '',
      type: 'illust',
      thumbnailUrl: 'https://i.pximg.net/c/540x540_70/img-master/img/2026/07/01/00/00/00/120_p0_master1200.jpg',
      pageCount: 1,
      width: 1200,
      height: 1200,
      userId: 42,
      userName: 'Mika',
      userAccount: 'mika',
      totalComments: 12,
    );
    await pumpPixiv(
      tester,
      const PixivIllustScreen(illust: work),
      client: (prefs) => FakePixivClient(prefs, detail: work),
      extraProviders: [Provider<PixivCommentsApi>.value(value: api)],
    );

    final link = find.byKey(const ValueKey('pixiv-comments-link'));
    await tester.ensureVisible(link);
    expect(find.text('View comments (12)'), findsOneWidget);
    await tester.tap(link);
    await settlePixiv(tester);
    expect(find.byType(PixivCommentsScreen), findsOneWidget);
    expect(find.textContaining('First!'), findsOneWidget);
    expect(api.calls, ['illust:120@first']);
    await disposePixiv(tester);
  });
}
