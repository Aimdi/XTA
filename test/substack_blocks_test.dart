import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/substack/substack_article_navigation.dart';
import 'package:xta/plugins/substack/substack_blocks.dart';

import 'fixtures/substack_synthetic.dart';

List<SubstackBlock> _blocks(String html) => substackBlocksOfBody(SubstackArticleDocument.parse(html).body);

void main() {
  test('paragraphs keep their styles, links and footnote markers', () {
    final block =
        _blocks(
              '<p>Plain <strong>bold <em>both</em></strong> <code>x()</code> '
              '<a href="https://example.com">link</a><a class="footnote-anchor" href="#footnote-1">1</a>.</p>',
            ).single
            as SubstackTextBlock;

    expect(block.kind, SubstackTextKind.paragraph);
    expect(block.text, 'Plain bold both x() link1.');
    expect(block.anchor, 'xta-passage-0');
    final both = block.runs.firstWhere((run) => run.text == 'both');
    expect((both.bold, both.italic), (true, true));
    expect(block.runs.firstWhere((run) => run.text == 'x()').code, isTrue);
    expect(block.runs.firstWhere((run) => run.text == 'link').href, 'https://example.com');
    expect(block.runs.firstWhere((run) => run.text == '1').footnote, 1);
  });

  test('headings, quotes, nested lists, code, dividers and tables come out in order', () {
    final blocks = _blocks(
      '<h2>Title</h2><blockquote><p>One</p><p>Two</p></blockquote>'
      '<ol><li>First<ul><li>Inner</li></ul></li><li>Second</li></ol><pre>a\n  b</pre><hr>'
      '<table><tr><th>H</th><td> cell </td></tr></table>',
    );

    String describe(SubstackBlock block) => switch (block) {
      SubstackTextBlock(:final kind, :final level, :final marker) =>
        '${kind.name}:$level:${marker ?? ''}:${block.text}',
      SubstackDividerBlock() => 'divider',
      SubstackTableBlock(:final rows) => 'table:$rows',
      _ => '$block',
    };
    expect(blocks.map(describe), [
      'heading:2::Title',
      'quote:0::One',
      'quote:0::Two',
      'listItem:0:1.:First',
      'listItem:1:•:Inner',
      'listItem:0:2.:Second',
      'code:0::a\n  b',
      'divider',
      'table:[[H, cell]]',
    ]);
  });

  test('images, held embeds, web-only gaps and footnotes are their own blocks', () {
    final blocks = _blocks(
      '${syntheticImage(0, 'https://cdn.example/a.jpg')}'
      '<iframe src="https://www.youtube.com/embed/x"></iframe>'
      '<div class="latex-rendered" data-component-name="LatexBlockToDOM"></div>'
      '<div class="footnote"><a class="footnote-number" href="#a">3</a><div class="footnote-content"><p>Note.</p></div></div>',
    );

    final image = blocks[0] as SubstackImageBlock;
    expect((image.src, image.width, image.height), ('https://cdn.example/a.jpg', 1456, 816));
    expect(image.caption.map((run) => run.text).join(), startsWith('Figure 1.'));
    final embed = blocks[1] as SubstackEmbedBlock;
    expect((embed.host, embed.src), ('youtube.com', 'https://www.youtube.com/embed/x'));
    expect(blocks[2], isA<SubstackWebOnlyBlock>());
    final note = blocks[3] as SubstackTextBlock;
    expect((note.kind, note.marker, note.text), (SubstackTextKind.footnote, '3', 'Note.'));
  });

  test('the synthetic long post loses no text and no image', () {
    final body = syntheticSubstackBody();
    final document = SubstackArticleDocument.parse(body);
    final blocks = substackBlocksOfBody(document.body);
    final text = blocks.whereType<SubstackTextBlock>().toList();

    expect(blocks.whereType<SubstackImageBlock>(), hasLength(24));
    expect(text.where((block) => block.kind == SubstackTextKind.footnote), hasLength(12));
    final words = RegExp(r'[a-z]+');
    int count(String s) => words.allMatches(s).length;
    final captions = blocks.whereType<SubstackImageBlock>().map((image) => image.caption.map((run) => run.text).join());
    expect(
      text.fold<int>(0, (sum, block) => sum + count(block.text)) + captions.fold<int>(0, (sum, c) => sum + count(c)),
      count(document.plainText),
    );
    expect(text.where((block) => block.anchor != null).length, greaterThan(200));
  });
}
