import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_scrolled_past.dart';

SubstackPost _post(int i) => SubstackPost(
  id: '$i',
  title: 'Post $i',
  slug: 'post-$i',
  canonicalUrl: 'https://fieldnotes.substack.com/p/post-$i',
  publicationBaseUrl: 'https://fieldnotes.substack.com',
  publicationName: 'Field Notes',
);

void main() {
  testWidgets('only cards fully above the top are reported, once a second, and only scrolling down', (tester) async {
    final reports = <List<String>>[];
    final tracker = SubstackScrolledPast(onPast: (posts) => reports.add([for (final post in posts) post.id]));
    addTearDown(tracker.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: NotificationListener<ScrollNotification>(
          onNotification: tracker.onScroll,
          child: ListView.builder(
            itemCount: 40,
            itemBuilder: (context, i) => SubstackScrolledPastTag(
              key: ValueKey(i),
              tracker: tracker,
              post: _post(i),
              child: SizedBox(height: 100, child: Text('Post $i')),
            ),
          ),
        ),
      ),
    );

    await tester.drag(find.byType(ListView), const Offset(0, -250));
    await tester.pump();
    await tester.pump(const Duration(milliseconds: 500));
    expect(reports, isEmpty, reason: 'checked at most once a second');
    await tester.pump(const Duration(milliseconds: 600));
    expect(reports.single, ['0', '1'], reason: 'the third card still shows half');

    await tester.drag(find.byType(ListView), const Offset(0, 200));
    await tester.pump(const Duration(seconds: 2));
    expect(reports, hasLength(1), reason: 'scrolling back up reports nothing');
  });

  testWidgets('a fast fling past many cards still reports each one', (tester) async {
    final reported = <String>{};
    final tracker = SubstackScrolledPast(onPast: (posts) => reported.addAll([for (final post in posts) post.id]));
    addTearDown(tracker.dispose);
    await tester.pumpWidget(
      MaterialApp(
        home: NotificationListener<ScrollNotification>(
          onNotification: tracker.onScroll,
          child: ListView.builder(
            itemCount: 400,
            itemBuilder: (context, i) => SubstackScrolledPastTag(
              key: ValueKey(i),
              tracker: tracker,
              post: _post(i),
              child: SizedBox(height: 100, child: Text('Post $i')),
            ),
          ),
        ),
      ),
    );

    await tester.fling(find.byType(ListView), const Offset(0, -600), 8000);
    // Frames as a phone draws them; a test's 100 ms steps would outrun the list's cache.
    for (var i = 0; i < 300; i++) {
      await tester.pump(const Duration(milliseconds: 16));
    }
    await tester.pump(const Duration(seconds: 2));

    final position = tester.state<ScrollableState>(find.byType(Scrollable)).position.pixels;
    final above = (position / 100).floor();
    expect(above, greaterThan(20), reason: 'the fling went far');
    expect(reported, {for (var i = 0; i < above; i++) '$i'});
  });
}
