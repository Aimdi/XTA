import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_html.dart';

import 'fixtures/substack_synthetic.dart';

String _page(String body, {bool sanitized = false}) => wrapSubstackHtml(
  title: 'A post',
  body: body,
  bodySanitized: sanitized,
  background: '#000000',
  foreground: '#ffffff',
  muted: '#888888',
  link: '#1d9bf0',
  isDark: true,
);

void main() {
  final bodies = {
    'synthetic long post': syntheticSubstackBody(),
    'bare text': 'Just words, <em>no</em> blocks at all.',
    'line breaks and lists': '<p>One<br>two</p><ul><li>a<ul><li>b</li></ul></li></ul><pre>x\n  y</pre>',
    'tables': '<table><tr><th>H</th><td>cell</td></tr></table><p>after</p>',
    'stripped parts': '<p onclick="x()">Kept</p><script>gone()</script><style>p{}</style><button>no</button>',
    'hidden and footnotes': '<p hidden>secret</p><p>A claim<a class="footnote-anchor" href="#f1">1</a>.</p>',
  };

  for (final MapEntry(key: name, value: body) in bodies.entries) {
    test('one parse gives the same page and speech text: $name', () {
      final document = SubstackArticleDocument.parse(body);

      expect(document.plainText, substackHtmlToPlainText(body));
      expect(_page(document.body, sanitized: true), _page(document.body));
    });
  }

  test('an embed is held behind a button until the reader asks for it', () {
    const frame = '<iframe src="https://www.youtube.com/embed/abc" allowfullscreen></iframe>';
    final document = SubstackArticleDocument.parse('<p>Before</p><div class="youtube-wrap">$frame</div><p>After</p>');

    expect(RegExp(r'<iframe').allMatches(document.body), hasLength(1));
    expect(document.body, contains('<div class="xta-embed" data-host="youtube.com"><template><iframe'));
    expect(document.body, contains('src="https://www.youtube.com/embed/abc"'));
    expect(document.plainText, 'Before\n\nAfter');

    final page = wrapSubstackHtml(
      title: 'A post',
      body: document.body,
      bodySanitized: true,
      background: '#000000',
      foreground: '#ffffff',
      muted: '#888888',
      link: '#1d9bf0',
      isDark: true,
      embedLabel: 'Load </script> "content"',
    );
    expect(page, contains(r'window.xtaEmbedLabel = "Load \u003c/script> \"content\"";'));
    expect(page, contains("box.replaceWith(held.content.cloneNode(true))"));
  });

  test('a parsed article is reused only for the same post, date and body', () {
    final cache = SubstackParsedArticles(capacity: 2);
    final one = SubstackArticleDocument.parse('<p>One</p>');
    final two = SubstackArticleDocument.parse('<p>Two</p>');
    final three = SubstackArticleDocument.parse('<p>Three</p>');

    cache.put('a\n2026-09-01', '<p>One</p>', one);
    expect(cache.get('a\n2026-09-01', '<p>One</p>'), same(one));
    expect(cache.get('a\n2026-09-01', '<p>One, edited</p>'), isNull);
    expect(cache.get('a\n2026-09-02', '<p>One</p>'), isNull);

    cache.put('a\n2026-09-01', '<p>One</p>', one);
    cache.put('b', '<p>Two</p>', two);
    cache.get('a\n2026-09-01', '<p>One</p>');
    cache.put('c', '<p>Three</p>', three);
    expect(cache.get('b', '<p>Two</p>'), isNull, reason: 'least recently used goes first');
    expect(cache.get('a\n2026-09-01', '<p>One</p>'), same(one));
    expect(cache.get('c', '<p>Three</p>'), same(three));
  });
}
