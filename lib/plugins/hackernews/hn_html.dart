import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;

/// HN comments arrive as HTML (`<p>`, `<i>`, `<a>`). Flatten to readable text.
String hnHtmlToText(String? raw) {
  final source = raw?.trim() ?? '';
  if (source.isEmpty) {
    return '';
  }
  return _write(html_parser.parseFragment(source)).trim();
}

String _write(Node node) {
  if (node is Text) {
    return node.text;
  }
  final buffer = StringBuffer();
  if (node is Element && (node.localName == 'p' || node.localName == 'br')) {
    buffer.write('\n\n');
  }
  for (final child in node.nodes) {
    buffer.write(_write(child));
  }
  return buffer.toString();
}
