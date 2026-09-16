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
    fragment.querySelectorAll('script, style, noscript, iframe, object, embed, meta, link, base, svg, math'),
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
    final name = '$key'.toLowerCase().split(':').last;
    if (name.startsWith('on') || name == 'srcdoc' ||
        (_urlAttributes.contains(name) &&
            _executableUrl.hasMatch((element.attributes[key] ?? '').replaceAll(RegExp(r'[\u0000-\u0020]'), '')))) {
      element.attributes.remove(key);
    }
  }
}

String rssReaderDocument({
  required String title,
  required String bodyHtml,
  required bool dark,
  String? background,
  String? foreground,
  String? link,
  double fontSizePx = 18,
  double lineHeight = 1.7,
}) {
  final fg = foreground ?? (dark ? '#f2f2f2' : '#1a1a1a');
  final bg = background ?? (dark ? '#121212' : '#fafafa');
  final muted = dark ? '#b0b0b0' : '#5c5c5c';
  return '''
<!DOCTYPE html>
<html>
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<style>
  * { box-sizing: border-box; }
  html { font-size: ${fontSizePx}px; background: $bg; }
  body { margin: 0 auto; max-width: 42rem; padding: 20px 18px 48px; font: ${fontSizePx}px/$lineHeight Georgia, "Iowan Old Style", serif; color: $fg; background: $bg; overflow-wrap: anywhere; }
  h1, h2, h3 { font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", sans-serif; }
  pre { overflow-x: auto; }
  table { display: block; max-width: 100%; overflow-x: auto; }
  blockquote { margin-inline: 0; padding-inline-start: 1em; border-inline-start: 3px solid ${link ?? fg}; }
  h1 { font-size: 1.45rem; line-height: 1.25; margin: 0 0 12px; }
  img, video { max-width: 100%; height: auto; }
  a { color: ${link ?? fg}; }
  p { margin: 0 0 1em; }
  .muted { color: $muted; font-size: 0.92rem; }
</style>
</head>
<body>
<article>
<h1>${_escape(title)}</h1>
<div class="content">$bodyHtml</div>
</article>
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
