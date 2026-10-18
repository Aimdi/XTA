import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_models.dart';

const _base = 'https://fieldnotes.substack.com';

SubstackPost _post({int? words, String? audio, String? type}) => SubstackPost(
  id: '1',
  title: 'Post',
  slug: 'post',
  publicationBaseUrl: _base,
  publicationName: 'Field Notes',
  wordCount: words,
  audioUrl: audio,
  type: type,
);

void main() {
  test('read time is the words at 230 a minute, rounded up, at least one minute', () {
    expect(substackReadMinutes(_post(words: 1)), 1);
    expect(substackReadMinutes(_post(words: 230)), 1);
    expect(substackReadMinutes(_post(words: 231)), 2);
    expect(substackReadMinutes(_post(words: 2300)), 10);
  });

  test('the body, when at hand, counts instead of the API figure', () {
    final body = List.filled(460, 'word').join(' ');
    expect(substackReadMinutes(_post(words: 23000), plainText: body), 2);
  });

  test('no estimate for an episode, a video, or a post with no count', () {
    expect(substackReadMinutes(_post(words: 900, audio: 'https://cdn.example/a.mp3')), isNull);
    expect(substackReadMinutes(_post(words: 900, type: 'video')), isNull);
    expect(substackReadMinutes(_post()), isNull);
    expect(substackReadMinutes(_post(words: 0)), isNull);
  });

  test('the API word count is read, survives a saved snapshot, and a bad one is ignored', () {
    final post = SubstackPost.fromJson(
      {'id': 7, 'slug': 's', 'title': 'T', 'wordcount': 1200},
      publicationBaseUrl: _base,
      publicationName: 'Field Notes',
    );
    expect(post.wordCount, 1200);
    expect(SubstackPost.fromSnapshot(post.toJson()).wordCount, 1200);
    final odd = SubstackPost.fromJson(
      {'id': 8, 'slug': 's', 'title': 'T', 'wordcount': 'many'},
      publicationBaseUrl: _base,
      publicationName: 'Field Notes',
    );
    expect(odd.wordCount, isNull);
  });

  test('the article page names the read time with the byline', () {
    final page = wrapSubstackHtml(
      title: 'T',
      body: '<p>x</p>',
      background: '#fff',
      foreground: '#000',
      muted: '#666',
      link: '#00f',
      isDark: false,
      publicationName: 'Field Notes',
      authorName: 'Ana',
      readTime: '4 min <read>',
    );
    expect(page, contains('<div class="meta">Field Notes · Ana · 4 min &lt;read&gt;</div>'));
  });
}
