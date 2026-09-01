import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_html.dart';

/// A post's HTML is written by whoever runs the publication, and the reader
/// screen puts it in a web view. Dropping `<script>` was the whole of the old
/// defence, which left every `on…` attribute untouched — those need no script
/// tag anywhere on the page to run.
void main() {
  group('what a post is not allowed to bring', () {
    test('event handlers do not survive, whatever element they are on', () {
      const raw = '''
<p onclick="steal()">Text</p>
<img src="x.png" onerror="fetch('https://example.invalid')">
<div ONLOAD="run()">Body</div>
<a href="/post" onmouseover="run()">Link</a>
''';

      final clean = sanitizeSubstackBodyHtml(raw).toLowerCase();

      expect(clean, isNot(contains('onclick')));
      expect(clean, isNot(contains('onerror')));
      expect(clean, isNot(contains('onload')));
      expect(clean, isNot(contains('onmouseover')));
    });

    test('a link whose destination is code loses the destination, not the text', () {
      const raw = '<a href="javascript:alert(1)">Read this</a>';

      final clean = sanitizeSubstackBodyHtml(raw);

      expect(clean, isNot(contains('javascript:')));
      expect(clean, contains('Read this'), reason: 'the writing stays; only the trigger goes');
    });

    test('a document smuggled in as a URL is refused', () {
      const raw =
          '<iframe src="data:text/html;base64,PHNjcmlwdD5hbGVydCgxKTwvc2NyaXB0Pg=="></iframe>'
          '<img src="VBScript:msgbox(1)">';

      final clean = sanitizeSubstackBodyHtml(raw).toLowerCase();

      expect(clean, isNot(contains('data:text/html')));
      expect(clean, isNot(contains('vbscript:')));
    });

    test('scripts are still removed outright', () {
      const raw = '<p>Before</p><script>steal()</script><noscript>x</noscript><p>After</p>';

      final clean = sanitizeSubstackBodyHtml(raw);

      expect(clean, isNot(contains('steal()')));
      expect(clean, contains('Before'));
      expect(clean, contains('After'));
    });
  });

  group('what a post is allowed to keep', () {
    test('ordinary writing and its markup come through untouched', () {
      const raw =
          '<p>A <strong>real</strong> paragraph with a '
          '<a href="https://example.com/post">link</a> and an '
          '<img src="https://example.com/pic.png" alt="a picture">.</p>';

      final clean = sanitizeSubstackBodyHtml(raw);

      expect(clean, contains('<strong>real</strong>'));
      expect(clean, contains('https://example.com/post'));
      expect(clean, contains('https://example.com/pic.png'));
      expect(clean, contains('alt="a picture"'));
    });

    test('an embed keeps the host it points at', () {
      const raw = '<iframe src="https://www.youtube.com/embed/abc123"></iframe>';

      expect(sanitizeSubstackBodyHtml(raw), contains('https://www.youtube.com/embed/abc123'));
    });
  });

  test('the spoken version never reads out a handler', () {
    const raw = '<p onclick="steal()">The sentence.</p>';

    expect(substackHtmlToPlainText(raw), 'The sentence.');
  });
}
