import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:html/parser.dart' as html;
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/reading/article_reading_bridge.dart';
import 'package:xta/reading/article_reading_store.dart';

void main() {
  test('contents retain publisher targets and distinguish duplicate heading titles', () {
    const body =
        '<h2 id="intro">Introduction</h2><p>Start here.</p>'
        '<h3><em>Details</em> &amp; links</h3><p>Next.</p><h2>Introduction</h2>';
    final document = SubstackArticleDocument.parse(body);
    expect(document.headings.map((entry) => entry.text), ['Introduction', 'Details & links', 'Introduction']);
    expect(document.headings.map((entry) => entry.headingLevel), [2, 3, 2]);
    expect(document.headings.map((entry) => entry.anchor).toSet(), hasLength(3));
    expect(document.body, contains('id="intro"'));
    expect(document.body, SubstackArticleDocument.parse(body).body);
    for (final entry in document.headings) {
      expect(html.parseFragment(document.body).querySelectorAll('[data-xta-block="${entry.anchor}"]'), hasLength(1));
    }
  });

  test('nested paragraphs are indexed once, while plain text still supports find', () {
    final document = SubstackArticleDocument.parse(
      '<blockquote><p>Nested phrase</p></blockquote><ul>'
      '<li>One item</li><li><p>Second item</p></li></ul>',
    );
    expect(document.passages.map((entry) => entry.text), ['Nested phrase', 'One item', 'Second item']);
    final plain = SubstackArticleDocument.parse('Readable &amp; useful');
    expect(plain.passages.single.text, 'Readable & useful');
    expect(plain.headings, isEmpty);
    expect(plain.body, contains('data-xta-block="xta-passage-0"'));
  });

  test('search only sees sanitized visible writing, with all words in any order', () {
    final document = SubstackArticleDocument.parse('''
      <h2>GARDEN notes</h2><p>Rain waters the garden.</p>
      <p>Another place.</p><script>Garden secret</script>
      <div class="paywall"><p>Garden forbidden</p></div>
      <div hidden><p>Garden hidden</p></div>
      <p style="display: none">Garden invisible</p>
      <p aria-hidden="true">Garden aria</p>
    ''');
    expect(document.search('  garden RAIN ').single.text, 'Rain waters the garden.');
    expect(document.search('garden'), hasLength(2));
    expect(document.search(''), isEmpty);
    expect(document.search('nonexistent'), isEmpty);
    expect(document.body, isNot(contains('<script>')));
  });

  test('publisher-injected navigation markers cannot collide with reader targets', () {
    final document = SubstackArticleDocument.parse(
      '<span data-xta-block="xta-passage-0">Label</span>'
      '<p data-xta-block="malicious" onclick="run()">Actual writing</p>',
    );
    expect(html.parseFragment(document.body).querySelectorAll('[data-xta-block]'), hasLength(1));
    expect(document.body, isNot(contains('onclick')));
    expect(document.passages.single.anchor, 'xta-passage-0');
  });

  test('results excerpt around a match without making huge rows', () {
    final text = '${List.filled(80, 'before').join(' ')} needle ${List.filled(80, 'after').join(' ')}';
    final passage = SubstackArticlePassage(anchor: 'p', text: text);
    final excerpt = passage.excerpt('needle');
    expect(excerpt, contains('needle'));
    expect(excerpt.length, lessThanOrEqualTo(242));
    expect(excerpt, startsWith('…'));
    expect(excerpt, endsWith('…'));
  });

  group('article fragment routing', () {
    const canonical = 'https://example.substack.com/p/reading';
    test('resolves relative, encoded and canonical fragment targets', () {
      expect(substackArticleFragment('#chapter%202', canonical), 'chapter 2');
      expect(substackArticleFragment('/p/reading#notes', canonical), 'notes');
      expect(substackArticleFragment('$canonical/?utm_source=reader#notes', canonical), 'notes');
      expect(substackArticleFragment('#notes', null), 'notes');
    });
    test('never claims other articles or external origins', () {
      expect(substackArticleFragment('https://elsewhere.substack.com/p/reading#notes', canonical), isNull);
      expect(substackArticleFragment('/p/other#notes', canonical), isNull);
      expect(substackArticleFragment('http://example.substack.com/p/reading#notes', canonical), isNull);
      expect(substackArticleFragment('/p/reading', canonical), isNull);
      expect(substackArticleFragment('$canonical#', canonical), isNull);
      expect(substackArticleFragment('#%FF', canonical), isNull);
      expect(substackArticleFragment('https://[invalid/#notes', canonical), isNull);
    });
  });

  test('jump code serializes untrusted targets instead of interpolating executable text', () {
    const anchor = "chapter'); steal(); //\n\"name";
    final script = substackArticleJumpJs(anchor, publisherAnchor: true);
    expect(script, contains('const anchor = ${jsonEncode(anchor)};'));
    expect(script, contains("document.querySelector('.content')"));
    expect(script, contains('node.scrollIntoView'));
    expect(script, contains('window.xtaArticle.jumpTo(node)'));
  });

  test('native article jumps stop initial restoration without reporting a reading scroll', () {
    final script = articleReadingBridge(const ArticleReadingState());
    final jump = script.substring(script.indexOf('jumpTo: function'), script.indexOf('\n  };'));
    expect(jump, contains('jumped = true'));
    expect(jump, contains('observer.disconnect()'));
    expect(jump, contains('userScrolled = false'));
    expect(script, contains('if (!jumped) move(saved)'));
    expect(script, contains('if (!interacted && !jumped) move(saved)'));
  });

  test('literal top-level HTML examples remain text through sanitizing and navigation indexing', () {
    const raw = '&lt;img src=x onerror=run()&gt; &amp; ordinary text';
    final clean = sanitizeSubstackBodyHtml(raw);
    expect(html.parseFragment(clean).querySelector('img'), isNull);
    final document = SubstackArticleDocument.parse(raw);
    expect(document.passages.single.text, '<img src=x onerror=run()> & ordinary text');
    expect(html.parseFragment(document.body).querySelector('img'), isNull);
  });

  test('reader document supports RTL, compact tables and passage focus', () {
    final page = wrapSubstackHtml(
      title: 'مرحبا',
      body: '<h2>عنوان</h2><p>مقال</p>',
      background: '#fff',
      foreground: '#000',
      muted: '#888',
      link: '#05c',
      isDark: false,
      isRtl: true,
    );
    final document = html.parse(page);
    expect(document.documentElement!.attributes['dir'], 'rtl');
    expect(page, contains('border-inline-start'));
    expect(page, contains('padding-inline-start'));
    expect(page, contains('overflow-x: auto'));
    expect(page, contains('[data-xta-selected]'));
  });
}
