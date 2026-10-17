import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_body_search.dart';
import 'package:xta/plugins/substack/substack_models.dart';

SubstackPost _post(String slug) => SubstackPost(
  id: slug,
  title: 'Title $slug',
  slug: slug,
  canonicalUrl: 'https://fieldnotes.substack.com/p/$slug',
  publicationBaseUrl: 'https://fieldnotes.substack.com',
  publicationName: 'Field Notes',
);

void main() {
  test('a passage is cut around the first match, marked where it was cut', () {
    const text = 'One two three. The Quiet Harbour was full of boats that morning. Four five six.';
    final snippet = substackMatchSnippet(text, text.toLowerCase(), 'quiet harbour', around: 10);
    expect(snippet, '…hree. The Quiet Harbour was full…');
    expect(substackMatchSnippet(text, text.toLowerCase(), 'absent'), isNull);
  });

  test('kept bodies are searched once read, and only posts with a kept body match', () async {
    final reads = <String>[];
    final store = SubstackBodySearchStore((post) async {
      reads.add(post.slug);
      return switch (post.slug) {
        'kept' => '<p>Notes on the <em>quiet harbour</em> at dawn.</p><script>harbour()</script>',
        _ => null,
      };
    });
    addTearDown(store.destroy);
    final posts = [_post('kept'), _post('remote')];

    await store.search('Harbour', posts);
    expect(store.state.keys, ['fieldnotes.substack.com/p/kept']);
    expect(store.state.values.single, contains('quiet harbour'));

    await store.search('dawn', posts);
    expect(reads, ['kept', 'remote'], reason: 'each body is read once');
    expect(store.state, hasLength(1));

    await store.search('h', posts);
    expect(store.state, isEmpty, reason: 'one letter is too short to search bodies');
  });

  test('a post kept after a search is found once the search is cleared and typed again', () async {
    String? body;
    final store = SubstackBodySearchStore((_) async => body);
    addTearDown(store.destroy);
    final posts = [_post('later')];

    await store.search('harbour', posts);
    expect(store.state, isEmpty);
    body = '<p>The harbour at dawn.</p>';
    await store.search('', posts);
    await store.search('harbour', posts);
    expect(store.state, hasLength(1));
  });

  test('a search still reading bodies when the store goes away does not touch it', () async {
    final gate = Completer<String?>();
    final store = SubstackBodySearchStore((_) => gate.future);
    final searching = store.search('harbour', [_post('slow')]);
    await store.destroy();
    gate.complete('<p>harbour</p>');
    await searching;
  });
}
