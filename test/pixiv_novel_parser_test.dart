import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_parser.dart';

/// A webview page in the shape Pixiv sends: the novel object inside a script, among other keys.
String _page(Map<String, Object?> novel, {String before = '', String after = ''}) =>
    '<html><head><script>$before Object.defineProperty(window, "pixiv", {value: {'
    'novel: ${jsonEncode(novel)}, isOwnWork: false}}); $after</script></head><body></body></html>';

PixivNovelParagraph _line(List<PixivNovelSpan> spans) => PixivNovelParagraph(spans);

PixivNovelParagraph _plain(String text) => PixivNovelParagraph([PixivNovelPlain(text)]);

void main() {
  group('the novel object in a webview page', () {
    test('is cut at its own closing brace when the text holds braces, quotes and escapes', () {
      final novel = {
        'id': '12',
        'text': 'He said "}{" and left. \\ A {brace} and a \\"quote\\"',
        'seriesNavigation': {
          'nextNovel': {'id': 13, 'viewable': true},
        },
      };
      final html = _page(novel, after: 'var other = {"a": 1};');

      expect(pixivNovelObjectSources(html).first, jsonEncode(novel));
      expect(pixivNovelJsonFromHtml(html), novel);
    });

    test('skips a "novel:" that opens no object and takes the next one', () {
      final html = _page({'id': '5', 'text': 'x'}, before: 'var label = "novel: chapter";');

      expect(pixivNovelJsonFromHtml(html)?['id'], '5');
    });

    test('is null for a page without one, an unclosed object or broken JSON', () {
      expect(pixivNovelJsonFromHtml('<html>no novel here</html>'), isNull);
      expect(pixivNovelJsonFromHtml('novel: {"id": 1, "text": "never closed'), isNull);
      expect(pixivNovelJsonFromHtml('novel: {id: 1}'), isNull);
      expect(pixivNovelJsonFromHtml(''), isNull);
    });
  });

  group('markup', () {
    test('every line is a paragraph and blank lines are kept', () {
      expect(parsePixivNovelMarkup('First\r\n\r\nSecond'), [
        _plain('First'),
        const PixivNovelParagraph([]),
        _plain('Second'),
      ]);
    });

    test('[newpage] breaks are numbered from page 2', () {
      final blocks = parsePixivNovelMarkup('One\n[newpage]\nTwo\n[newpage]\nThree');

      expect(blocks, [
        _plain('One'),
        const PixivNovelPageBreak(2),
        _plain('Two'),
        const PixivNovelPageBreak(3),
        _plain('Three'),
      ]);
      expect(pixivNovelPageStarts(blocks), {1: 0, 2: 1, 3: 3});
    });

    test('[chapter:] is a heading, ruby inside it included', () {
      expect(parsePixivNovelMarkup('[chapter:第一章 [[rb:旅立 > たびだ]]ち]'), [
        const PixivNovelHeading([PixivNovelPlain('第一章 '), PixivNovelRuby('旅立', 'たびだ'), PixivNovelPlain('ち')]),
      ]);
    });

    test('[[rb:]] takes either arrow and keeps the text around it', () {
      expect(parsePixivNovelMarkup('彼は[[rb:漢字 > かんじ]]と[[rb:未来＞あした]]を書いた'), [
        _line(const [
          PixivNovelPlain('彼は'),
          PixivNovelRuby('漢字', 'かんじ'),
          PixivNovelPlain('と'),
          PixivNovelRuby('未来', 'あした'),
          PixivNovelPlain('を書いた'),
        ]),
      ]);
    });

    test('[[jumpuri:]] links Pixiv and outside addresses alike, the label kept', () {
      expect(
        parsePixivNovelMarkup(
          'See [[jumpuri:my page > https://www.pixiv.net/users/42]] or '
          '[[jumpuri:elsewhere＞https://example.com/a?b=1]]',
        ),
        [
          _line(const [
            PixivNovelPlain('See '),
            PixivNovelLink(label: 'my page', url: 'https://www.pixiv.net/users/42'),
            PixivNovelPlain(' or '),
            PixivNovelLink(label: 'elsewhere', url: 'https://example.com/a?b=1'),
          ]),
        ],
      );
    });

    test('a jumpuri without a label shows its address; one that is no web address stays text', () {
      expect(parsePixivNovelMarkup('[[jumpuri: > https://example.com]]'), [
        _line(const [PixivNovelLink(label: 'https://example.com', url: 'https://example.com')]),
      ]);
      expect(parsePixivNovelMarkup('[[jumpuri:bad > javascript:alert(1)]]'), [
        _plain('[[jumpuri:bad > javascript:alert(1)]]'),
      ]);
    });

    test('[jump:N] is a page jump', () {
      expect(parsePixivNovelMarkup('Back to [jump:1].'), [
        _line(const [PixivNovelPlain('Back to '), PixivNovelPageJump(1), PixivNovelPlain('.')]),
      ]);
      expect(const PixivNovelPageJump(4).source, '[jump:4]');
    });

    test('[pixivimage:] and [uploadedimage:] are pictures of their own, splitting a line', () {
      expect(parsePixivNovelMarkup('Look [pixivimage:12345-3] here\n[pixivimage:777]\n[uploadedimage:9001]'), [
        _plain('Look '),
        const PixivNovelIllustBlock(illustId: 12345, page: 3, key: '12345-3'),
        _plain(' here'),
        const PixivNovelIllustBlock(illustId: 777, page: 1, key: '777'),
        const PixivNovelUploadBlock('9001'),
      ]);
    });

    test('space beside a block tag is not a line of its own', () {
      expect(parsePixivNovelMarkup('  [newpage]  '), [const PixivNovelPageBreak(2)]);
    });

    test('unknown tags and malformed markup stay as text without throwing', () {
      const texts = [
        '[unknown:thing]',
        '[chapter:never closed',
        '[[rb:no arrow]]',
        '[[rb: > only ruby]]',
        '[[rb:base > ]]',
        '[jump:zero]',
        '[jump:0]',
        '[pixivimage:abc]',
        '[pixivimage:12-x]',
        '[uploadedimage:]',
        '[[jumpuri:half',
        '[[[[]]]]][[',
        ']]][[[',
      ];
      for (final text in texts) {
        expect(parsePixivNovelMarkup(text), [_plain(text)], reason: text);
      }
    });

    test('an empty text is one blank line', () {
      expect(parsePixivNovelMarkup(''), [const PixivNovelParagraph([])]);
    });
  });

  group('plain text', () {
    test('drops the markup: ruby in brackets, links by label, pictures left out', () {
      final blocks = parsePixivNovelMarkup(
        '[chapter:Start]\n[[rb:漢字>かんじ]] and [[jumpuri:a site>https://example.com]] [jump:2]\n'
        '[pixivimage:5]\n[newpage]\nEnd',
      );

      expect(pixivNovelPlainText(blocks), 'Start\n漢字(かんじ) and a site (https://example.com) \n\nEnd');
    });
  });

  test('a long text is parsed off the UI thread to the same blocks', () async {
    final text = List.filled(4000, 'Line [[rb:漢>かん]]\n[newpage]').join('\n');
    expect(text.length, greaterThan(pixivNovelBackgroundLength));

    expect(await pixivNovelParse(parsePixivNovelMarkup, text), parsePixivNovelMarkup(text));
  });
}
