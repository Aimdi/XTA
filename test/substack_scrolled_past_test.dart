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
    await tester.pump(const Duration(milliseconds: 500));
    expect(reports, isEmpty, reason: 'checked at most once a second');
    await tester.pump(const Duration(milliseconds: 600));
    expect(reports.single, ['0', '1'], reason: 'the third card still shows half');

    await tester.drag(find.byType(ListView), const Offset(0, 200));
    await tester.pump(const Duration(seconds: 2));
    expect(reports, hasLength(1), reason: 'scrolling back up reports nothing');
  });
}
