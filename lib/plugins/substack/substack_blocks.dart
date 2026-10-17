import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;

/// A stretch of text with one style, inside a text block.
class SubstackRun {
  final String text;
  final bool bold;
  final bool italic;
  final bool code;
  final String? href;

  /// The footnote this run points at, for a footnote marker.
  final int? footnote;

  const SubstackRun(this.text, {this.bold = false, this.italic = false, this.code = false, this.href, this.footnote});

  SubstackRun _styled({bool? bold, bool? italic, bool? code, String? href}) => SubstackRun(
    text,
    bold: bold ?? this.bold,
    italic: italic ?? this.italic,
    code: code ?? this.code,
    href: href ?? this.href,
    footnote: footnote,
  );
}

enum SubstackTextKind { paragraph, heading, quote, listItem, code, footnote }

/// One block of a post as the native reader shows it.
sealed class SubstackBlock {
  const SubstackBlock();
}

class SubstackTextBlock extends SubstackBlock {
  final SubstackTextKind kind;
  final List<SubstackRun> runs;

  /// The heading level, or how deep a list item is nested (0 for the top).
  final int level;

  /// A list item's marker ("1.", "•") or a footnote's number.
  final String? marker;

  /// The navigation anchor of this passage, when it has one.
  final String? anchor;

  const SubstackTextBlock(this.kind, this.runs, {this.level = 0, this.marker, this.anchor});

  String get text => runs.map((run) => run.text).join();
}

class SubstackImageBlock extends SubstackBlock {
  final String src;
  final String alt;
  final int? width;
  final int? height;
  final List<SubstackRun> caption;

  const SubstackImageBlock(this.src, {this.alt = '', this.width, this.height, this.caption = const []});
}

class SubstackEmbedBlock extends SubstackBlock {
  final String host;
  final String src;

  const SubstackEmbedBlock(this.host, this.src);
}

/// Where Substack's own scripts would have drawn something (BUG-08).
class SubstackWebOnlyBlock extends SubstackBlock {
  const SubstackWebOnlyBlock();
}

class SubstackDividerBlock extends SubstackBlock {
  const SubstackDividerBlock();
}

class SubstackTableBlock extends SubstackBlock {
  final List<List<String>> rows;

  const SubstackTableBlock(this.rows);
}

/// The blocks of a [SubstackArticleDocument]'s body: sanitized, with passage
/// anchors and held embeds. Parsed only when the native reader asks.
List<SubstackBlock> substackBlocksOfBody(String body) => substackBlocksOf(html_parser.parseFragment(body));

/// The blocks of an already sanitized post body, in reading order.
List<SubstackBlock> substackBlocksOf(Node root) {
  final out = <SubstackBlock>[];
  _BlockWalker(out).children(root, listDepth: 0);
  return out;
}

const _headings = {'h1', 'h2', 'h3', 'h4', 'h5', 'h6'};

class _BlockWalker {
  final List<SubstackBlock> out;
  final _loose = <SubstackRun>[];

  _BlockWalker(this.out);

  void children(Node parent, {required int listDepth}) {
    for (final node in parent.nodes) {
      if (node is Element && _isBlock(node)) {
        _flushLoose();
        block(node, listDepth: listDepth);
      } else {
        _loose.addAll(runsOf(node));
      }
    }
    _flushLoose();
  }

  void block(Element node, {required int listDepth}) {
    final tag = node.localName ?? '';
    final classes = node.classes;
    if (classes.contains('xta-embed')) return _embed(node);
    if (classes.contains('xta-web-only')) return out.add(const SubstackWebOnlyBlock());
    if (classes.contains('footnote')) return _footnote(node);
    if (_headings.contains(tag)) return _text(node, SubstackTextKind.heading, level: int.parse(tag.substring(1)));
    switch (tag) {
      case 'p':
        _text(node, SubstackTextKind.paragraph);
      case 'pre':
        _add(SubstackTextBlock(SubstackTextKind.code, [SubstackRun(node.text, code: true)], anchor: _anchor(node)));
      case 'blockquote':
        _quote(node);
      case 'ul' || 'ol':
        _list(node, ordered: tag == 'ol', depth: listDepth);
      case 'figure' || 'img' || 'picture':
        _image(node);
      case 'hr':
        out.add(const SubstackDividerBlock());
      case 'table':
        _table(node);
      default:
        children(node, listDepth: listDepth);
    }
  }

  void _text(Element node, SubstackTextKind kind, {int level = 0, String? marker}) =>
      _add(SubstackTextBlock(kind, _trimmed(runsOf(node)), level: level, marker: marker, anchor: _anchor(node)));

  void _quote(Element node) {
    final paragraphs = node.children.where((child) => child.localName == 'p').toList();
    if (paragraphs.isEmpty) return _text(node, SubstackTextKind.quote);
    for (final paragraph in paragraphs) {
      _text(paragraph, SubstackTextKind.quote);
    }
  }

  void _list(Element node, {required bool ordered, required int depth}) {
    var index = 0;
    for (final item in node.children.where((child) => child.localName == 'li')) {
      index++;
      final nested = item.children.where((child) => child.localName == 'ul' || child.localName == 'ol').toList();
      final own = [
        for (final child in item.nodes)
          if (child is! Element || (child.localName != 'ul' && child.localName != 'ol')) ...runsOf(child),
      ];
      _add(
        SubstackTextBlock(
          SubstackTextKind.listItem,
          _trimmed(own),
          level: depth,
          marker: ordered ? '$index.' : '•',
          anchor: _anchor(item),
        ),
      );
      for (final list in nested) {
        _list(list, ordered: list.localName == 'ol', depth: depth + 1);
      }
    }
  }

  void _footnote(Element node) {
    final number = node.querySelector('.footnote-number')?.text.trim();
    final content = node.querySelector('.footnote-content') ?? node;
    for (final paragraph in content.querySelectorAll('p').ifEmpty([content])) {
      _text(paragraph, SubstackTextKind.footnote, marker: number);
    }
  }

  void _image(Element node) {
    final img = node.localName == 'img' ? node : node.querySelector('img');
    final src = img?.attributes['src'] ?? '';
    if (img == null || src.isEmpty) return;
    final caption = node.querySelector('figcaption');
    out.add(
      SubstackImageBlock(
        src,
        alt: img.attributes['alt'] ?? '',
        width: int.tryParse(img.attributes['width'] ?? ''),
        height: int.tryParse(img.attributes['height'] ?? ''),
        caption: caption == null ? const [] : _trimmed(runsOf(caption)),
      ),
    );
  }

  void _embed(Element node) {
    final frame = node.querySelector('template iframe') ?? node.querySelector('iframe');
    final src = frame?.attributes['src'] ?? '';
    if (src.isNotEmpty) out.add(SubstackEmbedBlock(node.attributes['data-host'] ?? '', src));
  }

  void _table(Element node) {
    final rows = [
      for (final row in node.querySelectorAll('tr'))
        [for (final cell in row.children) cell.text.replaceAll(RegExp(r'\s+'), ' ').trim()],
    ].where((row) => row.any((cell) => cell.isNotEmpty)).toList();
    if (rows.isNotEmpty) out.add(SubstackTableBlock(rows));
  }

  void _add(SubstackTextBlock block) {
    if (block.text.trim().isNotEmpty) out.add(block);
  }

  void _flushLoose() {
    if (_loose.isEmpty) return;
    _add(SubstackTextBlock(SubstackTextKind.paragraph, _trimmed(List.of(_loose))));
    _loose.clear();
  }
}

String? _anchor(Element node) => node.attributes['data-xta-block'];

bool _isBlock(Element node) {
  const blocks = {
    'p',
    'div',
    'section',
    'article',
    'blockquote',
    'ul',
    'ol',
    'pre',
    'figure',
    'picture',
    'img',
    'hr',
    'table',
    'h1',
    'h2',
    'h3',
    'h4',
    'h5',
    'h6',
    'header',
    'footer',
    'aside',
    'main',
  };
  return blocks.contains(node.localName);
}

/// The styled runs of an element's inline content.
List<SubstackRun> runsOf(Node node) {
  if (node is Text) return [SubstackRun(node.text.replaceAll(RegExp(r'\s+'), ' '))];
  if (node is! Element) return const [];
  final tag = node.localName ?? '';
  if (tag == 'br') return const [SubstackRun('\n')];
  if (node.classes.contains('footnote-anchor')) {
    final number = int.tryParse(node.text.trim());
    return [SubstackRun(node.text.trim(), footnote: number, href: node.attributes['href'])];
  }
  final inner = [for (final child in node.nodes) ...runsOf(child)];
  return switch (tag) {
    'strong' || 'b' => [for (final run in inner) run._styled(bold: true)],
    'em' || 'i' => [for (final run in inner) run._styled(italic: true)],
    'code' => [for (final run in inner) run._styled(code: true)],
    'a' => [for (final run in inner) run._styled(href: node.attributes['href'])],
    _ => inner,
  };
}

/// [runs] without the whitespace a block starts or ends with.
List<SubstackRun> _trimmed(List<SubstackRun> runs) {
  final kept = runs.where((run) => run.text.isNotEmpty).toList();
  if (kept.isEmpty) return const [];
  SubstackRun trim(SubstackRun run, String text) =>
      SubstackRun(text, bold: run.bold, italic: run.italic, code: run.code, href: run.href, footnote: run.footnote);
  kept[0] = trim(kept.first, kept.first.text.trimLeft());
  kept[kept.length - 1] = trim(kept.last, kept.last.text.trimRight());
  return kept.where((run) => run.text.isNotEmpty).toList(growable: false);
}

extension<T> on List<T> {
  List<T> ifEmpty(List<T> fallback) => isEmpty ? fallback : this;
}
