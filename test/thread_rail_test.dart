import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/thread_rail.dart';
import 'package:xta/tweet/threaded_conversation.dart';

/// A thread tile lives in a list, so its height is whatever its content is.
Widget _inAList(Widget child) => MaterialApp(
  home: Scaffold(body: ListView(children: [child])),
);

void main() {
  group('threadNestedIndent', () {
    test('the opened tweet and its direct replies sit flush left', () {
      expect(threadNestedIndent(0), 0);
      expect(threadNestedIndent(1), 0);
    });

    test('only a reply-to-a-reply steps in', () {
      expect(threadNestedIndent(2), kThreadLevelWidth);
      expect(threadNestedIndent(3), 2 * kThreadLevelWidth);
    });
  });

  group('ThreadRailBody', () {
    testWidgets('a tile with a rail below it is as tall as its content', (tester) async {
      await tester.pumpWidget(
        _inAList(
          ThreadRailBody(
            connectTop: false,
            connectBottom: true,
            indentBody: true,
            avatar: const SizedBox(width: 48, height: 48),
            header: const SizedBox(height: 20),
            bodyChildren: const [SizedBox(height: 100)],
            onTapProfile: () {},
          ),
        ),
      );

      final height = tester.getSize(find.byType(ThreadRailBody)).height;

      expect(height.isFinite, isTrue, reason: 'an unbounded tile pushes the rest of the timeline off screen');
      expect(height, lessThan(400));
    });

    testWidgets('the rail is drawn over the whole tile, not just its top', (tester) async {
      await tester.pumpWidget(
        _inAList(
          ThreadRailBody(
            connectTop: true,
            connectBottom: true,
            indentBody: true,
            avatar: const SizedBox(width: 48, height: 48),
            header: const SizedBox(height: 20),
            bodyChildren: const [SizedBox(height: 200)],
            onTapProfile: () {},
          ),
        ),
      );

      final body = tester.getSize(find.byType(ThreadRailBody));
      final lines = tester.getSize(find.byType(ThreadRailLines));

      expect(lines.height, body.height);
    });
  });

  group('ThreadIndent', () {
    testWidgets('a nested reply with a rail is as tall as its content', (tester) async {
      await tester.pumpWidget(
        _inAList(const ThreadIndent(depth: 1, connectTop: true, connectBottom: true, child: SizedBox(height: 120))),
      );

      final height = tester.getSize(find.byType(ThreadIndent)).height;

      expect(height.isFinite, isTrue);
      expect(height, 120);
    });

    testWidgets('a direct reply lines up with the opened tweet', (tester) async {
      await tester.pumpWidget(
        _inAList(
          const Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              ThreadIndent(depth: 0, child: SizedBox(key: Key('root'), width: 20, height: 20)),
              ThreadIndent(depth: 1, child: SizedBox(key: Key('reply'), width: 20, height: 20)),
              ThreadIndent(depth: 2, child: SizedBox(key: Key('nested'), width: 20, height: 20)),
            ],
          ),
        ),
      );

      final root = tester.getTopLeft(find.byKey(const Key('root')));
      final reply = tester.getTopLeft(find.byKey(const Key('reply')));
      final nested = tester.getTopLeft(find.byKey(const Key('nested')));

      expect(reply.dx, root.dx, reason: 'direct replies must not leave a blank strip on the left');
      expect(nested.dx, root.dx + kThreadLevelWidth);
    });

    testWidgets('nested replies indent from the reading edge in RTL', (
      tester,
    ) async {
      await tester.pumpWidget(
        MaterialApp(
          home: Scaffold(
            body: Directionality(
              textDirection: TextDirection.rtl,
              child: const Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  ThreadIndent(
                    depth: 0,
                    child: SizedBox(
                      key: Key('rtl-root'),
                      width: 20,
                      height: 20,
                    ),
                  ),
                  ThreadIndent(
                    depth: 2,
                    child: SizedBox(
                      key: Key('rtl-nested'),
                      width: 20,
                      height: 20,
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      );

      final root = tester.getTopRight(find.byKey(const Key('rtl-root')));
      final nested = tester.getTopRight(find.byKey(const Key('rtl-nested')));
      expect(nested.dx, root.dx - kThreadLevelWidth);
    });
  });
}
