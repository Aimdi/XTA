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
}
