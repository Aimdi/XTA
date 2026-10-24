import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/ui/reading_column.dart';

void main() {
  group('readingColumnPadding', () {
    const base = EdgeInsets.only(top: 4, bottom: 8);

    test('a phone or a narrow deck column keeps its padding', () {
      expect(readingColumnPadding(390, base), base);
      expect(readingColumnPadding(kReadingColumnMaxWidth, base), base);
    });

    test('a wide window centres a column of the maximum width', () {
      final padding = readingColumnPadding(1892, base);
      expect(padding.left, padding.right);
      expect(1892 - padding.horizontal, kReadingColumnMaxWidth);
      expect(padding.top, 4);
      expect(padding.bottom, 8);
    });

    test('existing side padding is kept on top of the margins', () {
      final padding = readingColumnPadding(1000, const EdgeInsets.symmetric(horizontal: 16));
      expect(padding.left, 16 + (1000 - kReadingColumnMaxWidth) / 2);
    });

    test('an unbounded width leaves the padding alone', () {
      expect(readingColumnPadding(double.infinity, base), base);
    });
  });

  testWidgets('a feed list on a wide window keeps posts in the column but scrolls across it', (tester) async {
    tester.view.physicalSize = const Size(1920, 1080);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.reset);

    await tester.pumpWidget(
      MaterialApp(
        home: Scaffold(
          body: FeedListView(
            itemCount: 30,
            itemBuilder: (context, index) => SizedBox(height: 80, child: Text('post $index')),
          ),
        ),
      ),
    );

    final post = tester.getRect(find.ancestor(of: find.text('post 0'), matching: find.byType(SizedBox)).first);
    expect(post.width, kReadingColumnMaxWidth);
    expect(post.center.dx, closeTo(960, 0.5));
    expect(tester.getSize(find.byType(ListView)).width, 1920);
  });
}
