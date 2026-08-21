import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;

final _executableUrl = RegExp(
  r'^\s*(javascript|vbscript|data)\s*:',
  caseSensitive: false,
);

const _urlAttributes = {
  'href',
  'src',
  'srcset',
  'action',
  'formaction',
  'poster',
  'background',
  'data',
};

/// Drops scripts and URL attributes that would run rather than be read.
String sanitizeRssBodyHtml(String raw) {
  final fragment = html_parser.parseFragment(raw);
  for (final node in List<Element>.from(
    fragment.querySelectorAll('script, style, noscript, iframe, object, embed'),
  )) {
    node.remove();
  }
  for (final element in fragment.querySelectorAll('*')) {
    _stripExecutable(element);
  }
  return fragment.outerHtml;
}

void _stripExecutable(Element element) {
  for (final key in element.attributes.keys.toList()) {
    final name = '$key'.toLowerCase();
    if (name.startsWith('on') ||
        (_urlAttributes.contains(name) &&
            _executableUrl.hasMatch(element.attributes[key] ?? ''))) {
      element.attributes.remove(key);
    }
  }
}

String rssReaderDocument({
  required String title,
  required String bodyHtml,
  required bool dark,
}) {
  final fg = dark ? '#f2f2f2' : '#1a1a1a';
  final bg = dark ? '#121212' : '#fafafa';
  final muted = dark ? '#b0b0b0' : '#5c5c5c';
  return '''
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  body { margin: 0; padding: 20px 18px 48px; font: 18px/1.55 -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; color: $fg; background: $bg; }
  h1 { font-size: 1.45rem; line-height: 1.25; margin: 0 0 12px; }
  img, video { max-width: 100%; height: auto; }
  a { color: inherit; }
  p { margin: 0 0 1em; }
  .muted { color: $muted; font-size: 0.92rem; }
</style>
</head>
<body>
<h1>${_escape(title)}</h1>
$bodyHtml
</body>
</html>
''';
}

String _escape(String text) {
  return text
      .replaceAll('&', '&amp;')
      .replaceAll('<', '&lt;')
      .replaceAll('>', '&gt;');
}
