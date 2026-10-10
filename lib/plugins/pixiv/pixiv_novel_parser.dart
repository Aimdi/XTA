import 'dart:convert';
import 'dart:math';

import 'package:flutter/foundation.dart';

/// Texts this long are read off the UI thread; shorter ones take less than a frame.
const pixivNovelBackgroundLength = 20000;

/// [parse] of [input], in a background isolate once [input] is long enough to stall a frame.
Future<R> pixivNovelParse<R>(R Function(String input) parse, String input) =>
    input.length < pixivNovelBackgroundLength ? Future.value(parse(input)) : compute(parse, input);

const _novelKey = 'novel:';
const _openBrace = 0x7B;
const _closeBrace = 0x7D;
const _quote = 0x22;
const _backslash = 0x5C;

/// The object a novel's webview page writes after `novel:`, decoded; null when
/// no candidate there is a JSON object.
Map<String, Object?>? pixivNovelJsonFromHtml(String html) {
  for (final source in pixivNovelObjectSources(html)) {
    if (_decoded(source) case final Map<String, Object?> object) return object;
  }
  return null;
}

Object? _decoded(String source) {
  try {
    return jsonDecode(source);
  } on FormatException {
    return null;
  }
}

/// Each `{…}` right after a `novel:` in [html], cut at the brace that balances
/// it. Braces and escaped quotes inside strings do not count, so a novel whose
/// text is full of them is cut where the object really ends.
Iterable<String> pixivNovelObjectSources(String html) sync* {
  for (var at = html.indexOf(_novelKey); at >= 0; at = html.indexOf(_novelKey, at + _novelKey.length)) {
    final start = _skipWhitespace(html, at + _novelKey.length);
    if (start >= html.length || html.codeUnitAt(start) != _openBrace) continue;
    if (_closingBrace(html, start) case final end?) yield html.substring(start, end + 1);
  }
}

int _skipWhitespace(String text, int from) {
  var at = from;
  while (at < text.length && text.codeUnitAt(at) <= 0x20) {
    at++;
  }
  return at;
}

/// Where the object opened at [start] closes, or null when it never does.
int? _closingBrace(String text, int start) {
  var depth = 0;
  var inString = false;
  var escaped = false;
  for (var at = start; at < text.length; at++) {
    final unit = text.codeUnitAt(at);
    if (inString) {
      if (escaped) {
        escaped = false;
      } else if (unit == _backslash) {
        escaped = true;
      } else if (unit == _quote) {
        inString = false;
      }
    } else if (unit == _quote) {
      inString = true;
    } else if (unit == _openBrace) {
      depth++;
    } else if (unit == _closeBrace && --depth == 0) {
      return at;
    }
  }
  return null;
}

/// One piece of a novel's body, top to bottom.
@immutable
sealed class PixivNovelBlock {
  const PixivNovelBlock();
}

/// One line of text; an empty one is a blank line the author left.
final class PixivNovelParagraph extends PixivNovelBlock {
  final List<PixivNovelSpan> spans;

  const PixivNovelParagraph(this.spans);

  bool get isBlank => spans.isEmpty;

  @override
  bool operator ==(Object other) => other is PixivNovelParagraph && listEquals(other.spans, spans);

  @override
  int get hashCode => Object.hashAll(spans);

  @override
  String toString() => 'Paragraph($spans)';
}

/// `[newpage]`: where page [page] starts; the text opens on page 1.
final class PixivNovelPageBreak extends PixivNovelBlock {
  final int page;

  const PixivNovelPageBreak(this.page);

  @override
  bool operator ==(Object other) => other is PixivNovelPageBreak && other.page == page;

  @override
  int get hashCode => page.hashCode;

  @override
  String toString() => 'PageBreak($page)';
}

/// `[chapter:…]`: a heading, which may carry ruby.
final class PixivNovelHeading extends PixivNovelBlock {
  final List<PixivNovelSpan> spans;

  const PixivNovelHeading(this.spans);

  @override
  bool operator ==(Object other) => other is PixivNovelHeading && listEquals(other.spans, spans);

  @override
  int get hashCode => Object.hashAll(spans);

  @override
  String toString() => 'Heading($spans)';
}

/// `[pixivimage:ID]` or `[pixivimage:ID-N]`: page [page] (from 1) of a work.
/// [key] is how the webview page files the picture.
final class PixivNovelIllustBlock extends PixivNovelBlock {
  final int illustId;
  final int page;
  final String key;

  const PixivNovelIllustBlock({required this.illustId, required this.page, required this.key});

  @override
  bool operator ==(Object other) =>
      other is PixivNovelIllustBlock && other.illustId == illustId && other.page == page && other.key == key;

  @override
  int get hashCode => Object.hash(illustId, page, key);

  @override
  String toString() => 'Illust($key)';
}

/// `[uploadedimage:ID]`: a picture uploaded with the novel.
final class PixivNovelUploadBlock extends PixivNovelBlock {
  final String imageId;

  const PixivNovelUploadBlock(this.imageId);

  @override
  bool operator ==(Object other) => other is PixivNovelUploadBlock && other.imageId == imageId;

  @override
  int get hashCode => imageId.hashCode;

  @override
  String toString() => 'Upload($imageId)';
}

/// A run inside a line.
@immutable
sealed class PixivNovelSpan {
  const PixivNovelSpan();
}

final class PixivNovelPlain extends PixivNovelSpan {
  final String text;

  const PixivNovelPlain(this.text);

  @override
  bool operator ==(Object other) => other is PixivNovelPlain && other.text == text;

  @override
  int get hashCode => text.hashCode;

  @override
  String toString() => 'Plain($text)';
}

/// `[[rb:base > ruby]]`: [ruby] is read over [base].
final class PixivNovelRuby extends PixivNovelSpan {
  final String base;
  final String ruby;

  const PixivNovelRuby(this.base, this.ruby);

  @override
  bool operator ==(Object other) => other is PixivNovelRuby && other.base == base && other.ruby == ruby;

  @override
  int get hashCode => Object.hash(base, ruby);

  @override
  String toString() => 'Ruby($base, $ruby)';
}

/// `[[jumpuri:label > url]]`, an http or https [url].
final class PixivNovelLink extends PixivNovelSpan {
  final String label;
  final String url;

  const PixivNovelLink({required this.label, required this.url});

  @override
  bool operator ==(Object other) => other is PixivNovelLink && other.label == label && other.url == url;

  @override
  int get hashCode => Object.hash(label, url);

  @override
  String toString() => 'Link($label, $url)';
}

/// `[jump:N]`: a link to page [page] of the same novel.
final class PixivNovelPageJump extends PixivNovelSpan {
  final int page;

  const PixivNovelPageJump(this.page);

  /// The markup as written, for a page the novel does not have.
  String get source => '[jump:$page]';

  @override
  bool operator ==(Object other) => other is PixivNovelPageJump && other.page == page;

  @override
  int get hashCode => page.hashCode;

  @override
  String toString() => 'Jump($page)';
}

/// A novel's text as blocks. Every line is a paragraph (blank lines kept), a
/// page break, a heading or a picture, and a tag sharing a line with text
/// splits it. A tag this does not know, or one written wrong, stays as text:
/// nothing in a text can make this throw.
List<PixivNovelBlock> parsePixivNovelMarkup(String text) {
  final lines = text.replaceAll('\r\n', '\n').replaceAll('\r', '\n').split('\n');
  final blocks = [for (final line in lines) ..._lineBlocks(line)];
  var page = 1;
  return List.unmodifiable([
    for (final block in blocks) block is PixivNovelPageBreak ? PixivNovelPageBreak(++page) : block,
  ]);
}

List<PixivNovelBlock> _lineBlocks(String line) {
  final pieces = _pieces(line).toList(growable: false);
  if (pieces.every((piece) => piece is PixivNovelSpan)) {
    return [PixivNovelParagraph(List.unmodifiable(pieces.cast<PixivNovelSpan>()))];
  }
  final blocks = <PixivNovelBlock>[];
  var spans = <PixivNovelSpan>[];
  for (final piece in [...pieces, null]) {
    if (piece is PixivNovelSpan) {
      spans.add(piece);
      continue;
    }
    if (!_isBlankRun(spans)) blocks.add(PixivNovelParagraph(List.unmodifiable(spans)));
    spans = [];
    if (piece is PixivNovelBlock) blocks.add(piece);
  }
  return blocks;
}

/// Space left beside a block tag is not a line of its own.
bool _isBlankRun(List<PixivNovelSpan> spans) =>
    spans.every((span) => span is PixivNovelPlain && span.text.trim().isEmpty);

/// The line's spans and blocks in order; text between tags is one plain span.
Iterable<Object> _pieces(String line) sync* {
  var plainFrom = 0;
  var at = line.indexOf('[');
  while (at >= 0) {
    final tag = _tagAt(line, at);
    if (tag == null) {
      at = line.indexOf('[', at + 1);
      continue;
    }
    if (at > plainFrom) yield PixivNovelPlain(line.substring(plainFrom, at));
    yield tag.piece;
    plainFrom = tag.end;
    at = line.indexOf('[', plainFrom);
  }
  if (plainFrom < line.length) yield PixivNovelPlain(line.substring(plainFrom));
}

typedef _Tag = ({Object piece, int end});

_Tag? _tagAt(String line, int at) {
  if (line.startsWith('[[', at)) return _pairedTag(line, at);
  if (line.startsWith('[chapter:', at)) return _chapterTag(line, at);
  final close = line.indexOf(']', at);
  if (close < 0) return null;
  final piece = _simpleTag(line.substring(at + 1, close));
  return piece == null ? null : (piece: piece, end: close + 1);
}

Object? _simpleTag(String body) {
  if (body == 'newpage') return const PixivNovelPageBreak(0);
  final colon = body.indexOf(':');
  if (colon < 0) return null;
  final value = body.substring(colon + 1).trim();
  return switch (body.substring(0, colon)) {
    'jump' => _pageJump(value),
    'pixivimage' => _illustBlock(value),
    'uploadedimage' => value.isEmpty ? null : PixivNovelUploadBlock(value),
    _ => null,
  };
}

PixivNovelPageJump? _pageJump(String value) => switch (int.tryParse(value)) {
  final page? when page > 0 => PixivNovelPageJump(page),
  _ => null,
};

PixivNovelIllustBlock? _illustBlock(String value) {
  final dash = value.indexOf('-');
  final id = int.tryParse(dash < 0 ? value : value.substring(0, dash));
  final page = dash < 0 ? 1 : int.tryParse(value.substring(dash + 1));
  if (id == null || id <= 0 || page == null || page <= 0) return null;
  return PixivNovelIllustBlock(illustId: id, page: page, key: value);
}

_Tag? _pairedTag(String line, int at) {
  final close = line.indexOf(']]', at + 2);
  if (close < 0) return null;
  final body = line.substring(at + 2, close);
  final piece = body.startsWith('rb:')
      ? _ruby(body.substring(3))
      : body.startsWith('jumpuri:')
      ? _link(body.substring(8))
      : null;
  return piece == null ? null : (piece: piece, end: close + 2);
}

PixivNovelRuby? _ruby(String body) => switch (_splitArrow(body, last: false)) {
  (final base, final ruby) when base.isNotEmpty => PixivNovelRuby(base, ruby),
  _ => null,
};

/// The URL is what follows the last arrow, so a label may hold one of its own.
PixivNovelLink? _link(String body) {
  final (label, url) = _splitArrow(body, last: true) ?? ('', '');
  final uri = Uri.tryParse(url);
  if (uri == null || !(uri.isScheme('https') || uri.isScheme('http')) || uri.host.isEmpty) return null;
  return PixivNovelLink(label: label.isEmpty ? url : label, url: url);
}

/// `left > right`, with the half- or full-width arrow Pixiv's editor writes.
(String, String)? _splitArrow(String body, {required bool last}) {
  final half = last ? body.lastIndexOf('>') : body.indexOf('>');
  final full = last ? body.lastIndexOf('＞') : body.indexOf('＞');
  final at = last ? max(half, full) : _firstFound(half, full);
  if (at < 0) return null;
  final right = body.substring(at + 1).trim();
  return right.isEmpty ? null : (body.substring(0, at).trim(), right);
}

int _firstFound(int a, int b) => a < 0 ? b : (b < 0 ? a : min(a, b));

_Tag? _chapterTag(String line, int at) {
  final start = at + '[chapter:'.length;
  final close = _chapterEnd(line, start);
  if (close == null) return null;
  final spans = [
    for (final piece in _pieces(line.substring(start, close)))
      if (piece is PixivNovelSpan) piece,
  ];
  return (piece: PixivNovelHeading(List.unmodifiable(spans)), end: close + 1);
}

/// The `]` closing a chapter title, past any `[[rb:…]]` inside it.
int? _chapterEnd(String line, int from) {
  var at = from;
  while (at < line.length) {
    final ruby = line.startsWith('[[', at) ? line.indexOf(']]', at + 2) : -1;
    if (ruby >= 0) {
      at = ruby + 2;
    } else if (line.codeUnitAt(at) == 0x5D) {
      return at;
    } else {
      at++;
    }
  }
  return null;
}

/// Where each page starts in [blocks]: page 1 at the top, the others at their breaks.
Map<int, int> pixivNovelPageStarts(List<PixivNovelBlock> blocks) => {
  1: 0,
  for (final (index, block) in blocks.indexed)
    if (block is PixivNovelPageBreak) block.page: index,
};

/// The text without Pixiv's markup: ruby as `base(ruby)`, links as their
/// labels with the address beside them, a page break as a blank line, and
/// pictures and page jumps left out.
String pixivNovelPlainText(List<PixivNovelBlock> blocks) => [for (final block in blocks) ?_plainLine(block)].join('\n');

String? _plainLine(PixivNovelBlock block) => switch (block) {
  PixivNovelParagraph(:final spans) || PixivNovelHeading(:final spans) => spans.map(_plainSpan).join(),
  PixivNovelPageBreak() => '',
  PixivNovelIllustBlock() || PixivNovelUploadBlock() => null,
};

String _plainSpan(PixivNovelSpan span) => switch (span) {
  PixivNovelPlain(:final text) => text,
  PixivNovelRuby(:final base, :final ruby) => '$base($ruby)',
  PixivNovelLink(:final label, :final url) => label == url ? url : '$label ($url)',
  PixivNovelPageJump() => '',
};
