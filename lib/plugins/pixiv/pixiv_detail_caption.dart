import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:html/dom.dart' as dom;
import 'package:html/parser.dart' as html_parser;
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/utils/urls.dart';

/// A run of caption text with the formatting it had.
class PixivCaptionPart {
  final String text;
  final bool bold;

  /// Where the run links to, already made absolute; null for plain text.
  final String? href;

  const PixivCaptionPart(this.text, {this.bold = false, this.href});
}

/// A Pixiv caption's HTML as runs of text: line breaks, bold and links are
/// kept, every other tag is read for its text alone.
List<PixivCaptionPart> pixivCaptionParts(String html) {
  if (html.trim().isEmpty) return const [];
  return List.unmodifiable(_partsOf(html_parser.parseFragment(html).nodes, bold: false, href: null));
}

List<PixivCaptionPart> _partsOf(Iterable<dom.Node> nodes, {required bool bold, required String? href}) => [
  for (final node in nodes) ..._partsOfNode(node, bold: bold, href: href),
];

List<PixivCaptionPart> _partsOfNode(dom.Node node, {required bool bold, required String? href}) {
  if (node is dom.Text) {
    return node.data.isEmpty ? const [] : [PixivCaptionPart(node.data, bold: bold, href: href)];
  }
  if (node is! dom.Element) return const [];
  return switch (node.localName) {
    'br' => const [PixivCaptionPart('\n')],
    'strong' || 'b' => _partsOf(node.nodes, bold: true, href: href),
    'a' => _partsOf(node.nodes, bold: bold, href: pixivCaptionHref(node.attributes['href']) ?? href),
    'p' || 'div' => [..._partsOf(node.nodes, bold: bold, href: href), const PixivCaptionPart('\n')],
    _ => _partsOf(node.nodes, bold: bold, href: href),
  };
}

/// A caption link made absolute. Pixiv writes its own pages as paths or
/// `pixiv://` links, and wraps outside ones in `/jump.php?<url>`.
String? pixivCaptionHref(String? raw) {
  final text = raw?.trim() ?? '';
  final uri = text.isEmpty ? null : Uri.tryParse(text);
  if (uri == null) return null;
  if (uri.scheme == 'pixiv') return text;
  final absolute = uri.hasScheme ? uri : Uri.parse('https://www.pixiv.net/').resolveUri(uri);
  if (absolute.scheme != 'https' && absolute.scheme != 'http') return null;
  if (absolute.path != '/jump.php' || absolute.query.isEmpty) return absolute.toString();
  try {
    return pixivCaptionHref(absolute.queryParameters['url'] ?? Uri.decodeComponent(absolute.query));
  } on ArgumentError {
    return null;
  }
}

/// The creator's caption under a work.
class PixivDetailCaption extends StatelessWidget {
  final PixivIllust illust;

  const PixivDetailCaption({super.key, required this.illust});

  @override
  Widget build(BuildContext context) => PixivHtmlText(html: illust.captionHtml, plainText: illust.caption);
}

/// Pixiv's HTML text — a work's caption, a profile's bio, a novel's caption —
/// selectable, with Pixiv links opening in XTA and others where [openLink]
/// sends them. [plainText] stands in when there is no HTML.
class PixivHtmlText extends StatefulWidget {
  final String html;
  final String plainText;

  const PixivHtmlText({super.key, required this.html, this.plainText = ''});

  @override
  State<PixivHtmlText> createState() => _PixivHtmlTextState();
}

class _PixivHtmlTextState extends State<PixivHtmlText> {
  final _recognizers = <TapGestureRecognizer>[];

  @override
  void dispose() {
    _clearRecognizers();
    super.dispose();
  }

  void _clearRecognizers() {
    for (final recognizer in _recognizers) {
      recognizer.dispose();
    }
    _recognizers.clear();
  }

  List<PixivCaptionPart> get _parts =>
      widget.html.isEmpty ? [PixivCaptionPart(widget.plainText)] : pixivCaptionParts(widget.html);

  /// A Pixiv link that did not open in XTA goes straight to the browser:
  /// [openLink] would hand it back to the Pixiv router and fetch it again.
  Future<void> _open(String href) async {
    final ref = parsePixivLink(href);
    if (ref == null) return openLink(context, href);
    if (!await openPixivLinkRef(context, ref) && mounted) await openUri(context, href);
  }

  @override
  Widget build(BuildContext context) {
    _clearRecognizers();
    final theme = Theme.of(context);
    final style = theme.textTheme.bodyMedium!.copyWith(height: 1.35);
    final link = style.copyWith(color: theme.colorScheme.primary, decoration: TextDecoration.underline);
    return SelectionArea(
      child: Text.rich(TextSpan(style: style, children: [for (final part in _parts) _span(part, link)])),
    );
  }

  InlineSpan _span(PixivCaptionPart part, TextStyle link) {
    final weight = part.bold ? const TextStyle(fontWeight: FontWeight.w700) : null;
    final href = part.href;
    if (href == null) return TextSpan(text: part.text, style: weight);
    final recognizer = TapGestureRecognizer()..onTap = () => _open(href);
    _recognizers.add(recognizer);
    return TextSpan(text: part.text, style: link.merge(weight), recognizer: recognizer);
  }
}
