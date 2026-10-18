import 'dart:convert';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:xta/plugins/substack/substack_reader_style.dart';

/// URL schemes that are code rather than a destination.
///
/// `javascript:` is the obvious one; `data:` can carry a whole HTML document,
/// which is the same thing wearing a different hat.
final _executableUrl = RegExp(r'^\s*(javascript|vbscript|data)\s*:', caseSensitive: false);

/// Attributes that hold a URL, and so can smuggle one of the above.
const _urlAttributes = {'href', 'src', 'srcset', 'action', 'formaction', 'poster', 'background', 'data'};

/// Removes the parts of a post that are code rather than writing.
///
/// Dropping `<script>` is not enough on its own: `onerror`, `onload` and their
/// forty-odd siblings are attributes, and they run without a script tag
/// anywhere on the page. A post is written by whoever runs the publication, so
/// this is not hypothetical — and the reader screen renders the result in a
/// real web view.
///
/// The reader injects its own progress and speech helpers after sanitizing;
/// executable article markup must never survive into that document.
void _stripExecutable(Element element) {
  // Keyed by `Object`, because a namespaced attribute is an `AttributeName`
  // rather than a string — so the key has to be carried through to the removal
  // instead of the name it prints as.
  for (final key in element.attributes.keys.toList()) {
    final name = '$key'.toLowerCase().split(':').last;

    if (name.startsWith('on') ||
        name == 'srcdoc' ||
        (_urlAttributes.contains(name) &&
            _executableUrl.hasMatch((element.attributes[key] ?? '').replaceAll(RegExp(r'[\u0000-\u0020]'), '')))) {
      element.attributes.remove(key);
    }
  }
}

/// Strip Substack chrome that hurts reading (expand buttons, SVGs, scripts),
/// and anything in the markup that would run rather than be read.
String sanitizeSubstackBodyHtml(String raw) {
  final fragment = html_parser.parseFragment(raw);
  final removable = fragment.querySelectorAll(
    [
      'script',
      'style',
      'meta',
      'link',
      'base',
      'object',
      'embed',
      'math',
      'button',
      // A reader posts nothing: a subscribe or any other form in a body is
      // dropped whatever markup Substack wraps it in.
      'form',
      'input',
      'textarea',
      'select',
      'svg',
      'noscript',
      '.image-link-expand',
      '.restack-image',
      '.icon-container',
      '[data-component-name="AudioPlayer"]',
      '[data-component-name="SubscribeWidget"]',
      '[data-component-name="SubscribePrompt"]',
      '.subscription-widget-wrap',
      '.paywall',
    ].join(', '),
  );
  for (final node in List<Element>.from(removable)) {
    node.remove();
  }

  for (final node in List<Element>.from(fragment.querySelectorAll('div, span'))) {
    if (node.parentNode == null) continue;
    if (node.text.trim().isEmpty && node.querySelector('img, iframe, video, picture, table') == null) {
      // Substack's scripts draw LaTeX, galleries and polls into empty
      // component blocks; the reader marks where one was rather than losing it.
      _drawnOnTheWeb(node) ? node.replaceWith(_webOnlyBlock()) : node.remove();
    }
  }

  for (final node in fragment.querySelectorAll('*')) {
    _stripExecutable(node);
  }

  return fragment.outerHtml;
}

bool _drawnOnTheWeb(Element node) =>
    node.attributes.containsKey('data-component-name') || node.attributes.containsKey('data-attrs');

Element _webOnlyBlock() => Element.tag('div')
  ..classes.add('xta-web-only')
  ..attributes['data-component-name'] = 'XtaWebOnly';

/// Plain text suitable for device TTS, with paragraph breaks preserved.
String substackHtmlToPlainText(String raw) {
  final body = html_parser.parse(sanitizeSubstackBodyHtml(raw)).body;
  return body == null ? '' : substackPlainTextOf(body);
}

/// The speech text of an already sanitized [root].
String substackPlainTextOf(Node root) {
  final buffer = StringBuffer();
  void walk(Node node) {
    if (node is Text) {
      final text = node.text.replaceAll(RegExp(r'\s+'), ' ');
      if (text.trim().isNotEmpty) buffer.write(text);
      return;
    }
    if (node is! Element) return;

    final tag = node.localName?.toLowerCase();
    if (tag == 'script' || tag == 'style' || tag == 'svg' || tag == 'button') {
      return;
    }
    // A footnote's number, in the text or at its note, read aloud runs into
    // the sentence: "a claim1".
    if (tag == 'a' && (node.classes.contains('footnote-anchor') || node.classes.contains('footnote-number'))) return;
    if (tag == 'br') {
      buffer.write('\n');
      return;
    }

    final isBlock = const {
      'p',
      'div',
      'section',
      'article',
      'h1',
      'h2',
      'h3',
      'h4',
      'h5',
      'h6',
      'li',
      'blockquote',
      'pre',
      'figure',
      'tr',
    }.contains(tag);

    if (isBlock && buffer.isNotEmpty && !buffer.toString().endsWith('\n')) {
      buffer.write('\n');
    }

    for (final child in node.nodes) {
      walk(child);
    }

    if (isBlock) buffer.write('\n');
  }

  root.nodes.forEach(walk);
  return buffer.toString().replaceAll(RegExp(r'[ \t]+\n'), '\n').replaceAll(RegExp(r'\n{3,}'), '\n\n').trim();
}

String buildSubstackSpeakText({
  required String title,
  String? subtitle,
  String? authorName,
  String? publicationName,
  String? bodyHtml,
  String? bodyPlain,
}) {
  final body = (bodyPlain != null && bodyPlain.trim().isNotEmpty)
      ? bodyPlain.trim()
      : (bodyHtml != null && bodyHtml.trim().isNotEmpty)
      ? substackHtmlToPlainText(bodyHtml)
      : '';
  final parts = <String>[
    title.trim(),
    if (publicationName != null && publicationName.trim().isNotEmpty) publicationName.trim(),
    if (authorName != null && authorName.trim().isNotEmpty) authorName.trim(),
    if (subtitle != null && subtitle.trim().isNotEmpty) subtitle.trim(),
    body,
  ].where((e) => e.isNotEmpty);
  return parts.join('\n\n');
}

/// With text selected, `xtaSpeakFromSelection()` posts the rest of the article
/// from the selected paragraph to [XtaTts].
///
/// The reader screen installs that channel. Body HTML is still stripped of
/// scripts and `on*` handlers; this is the only script on the page.
const substackTtsFromHereJs = r'''
(function(){
  if (window.__xtaTtsFromHere) return;
  window.__xtaTtsFromHere = true;
  function post(text){
    if (!text || !window.XtaTts || !XtaTts.postMessage) return;
    XtaTts.postMessage(text);
  }
  // A block's text without its footnote numbers, as the reader speaks it.
  function textOf(b){
    if (!b) return '';
    var marks = b.querySelectorAll ? b.querySelectorAll('.footnote-anchor,.footnote-number') : [];
    var kept = [];
    for (var i = 0; i < marks.length; i++) { kept.push(marks[i].textContent); marks[i].textContent = ''; }
    var t = (b.innerText || '').trim();
    for (var j = 0; j < marks.length; j++) marks[j].textContent = kept[j];
    return t;
  }
  function blockOf(node){
    var n = node && node.nodeType === 3 ? node.parentElement : node;
    while (n && n !== document.body) {
      var t = n.tagName;
      if (t && /^(P|H1|H2|H3|H4|H5|H6|LI|BLOCKQUOTE|PRE|FIGCAPTION)$/.test(t)) return n;
      if (n.classList && (n.classList.contains('title') || n.classList.contains('subtitle'))) return n;
      n = n.parentElement;
    }
    return node;
  }
  function fromHere(el){
    var root = document.querySelector('article') || document.body;
    var blocks = root.querySelectorAll('h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,.title,.subtitle');
    var started = false;
    var parts = [];
    for (var i = 0; i < blocks.length; i++) {
      var b = blocks[i];
      if (b === el || b.contains(el) || (el && el.contains && el.contains(b))) started = true;
      if (started) {
        var t = textOf(b);
        if (t) parts.push(t);
      }
    }
    if (!parts.length) {
      var all = textOf(root);
      var needle = textOf(el).slice(0, 80);
      var at = needle ? all.indexOf(needle) : -1;
      post(at >= 0 ? all.slice(at) : (needle || all));
      return;
    }
    post(parts.join('\n\n'));
  }
  // Long-press selects text as usual; Listen then reads from the selection.
  window.xtaSpeakFromSelection = function(){
    var s = window.getSelection && window.getSelection();
    if (!s || s.isCollapsed || !s.anchorNode) return false;
    fromHere(blockOf(s.anchorNode));
    return true;
  };
})();
''';

/// Turns each held embed into a button that puts the original iframe back.
const substackEmbedJs = r'''
(function(){
  document.querySelectorAll('.xta-embed').forEach(function(box){
    var button = document.createElement('button');
    button.type = 'button';
    button.className = 'xta-embed-load';
    var host = box.getAttribute('data-host');
    button.textContent = [window.xtaEmbedLabel, host].filter(Boolean).join(' \u00b7 ');
    button.addEventListener('click', function(){
      var held = box.querySelector('template');
      if (held) box.replaceWith(held.content.cloneNode(true));
    });
    box.appendChild(button);
  });
})();
''';

/// Says where a block drawn only by Substack's own scripts was, with a link
/// to the post on the web.
const substackWebOnlyJs = r'''
(function(){
  var note = window.xtaWebOnly || {};
  document.querySelectorAll('.xta-web-only').forEach(function(box){
    var p = document.createElement('p');
    p.textContent = note.label || '';
    if (note.url && note.link) {
      var a = document.createElement('a');
      a.href = note.url;
      a.textContent = note.link;
      p.appendChild(document.createTextNode(' '));
      p.appendChild(a);
    }
    box.appendChild(p);
  });
})();
''';

/// [value] as a JavaScript string literal that cannot close its script tag.
String _scriptString(String value) => jsonEncode(value).replaceAll('<', r'\u003c');

String wrapSubstackHtml({
  required String title,
  required String body,
  required String background,
  required String foreground,
  required String muted,
  required String link,
  required bool isDark,
  bool isRtl = false,
  String? subtitle,
  String? authorName,
  String? publicationName,
  String? readTime,
  SubstackReaderFont font = SubstackReaderFont.serif,
  SubstackColumnWidth column = SubstackColumnWidth.medium,
  double fontSizePx = 18,
  double lineHeight = 1.7,
  String? footer,
  String? footerLink,
  String? footerLinkLabel,
  bool bodySanitized = false,
  String embedLabel = '',
  String webOnlyLabel = '',
  String openLabel = '',
  String? postUrl,
}) {
  final cleanBody = bodySanitized ? body : sanitizeSubstackBodyHtml(body);
  final meta = [
    if (publicationName != null && publicationName.isNotEmpty) _escape(publicationName),
    if (authorName != null && authorName.isNotEmpty) _escape(authorName),
    if (readTime != null && readTime.isNotEmpty) _escape(readTime),
  ].join(' · ');

  final codeBg = isDark ? '#1E1E1E' : '#F1F3F5';
  final rule = isDark ? 'rgba(255,255,255,0.12)' : 'rgba(15,20,25,0.12)';

  return '''
<!DOCTYPE html>
<html dir="${isRtl ? 'rtl' : 'ltr'}">
<head>
<meta charset="utf-8" />
<meta name="viewport" content="width=device-width, initial-scale=1, maximum-scale=3" />
<meta name="color-scheme" content="${isDark ? 'dark' : 'light'}" />
<style>
  :root { color-scheme: ${isDark ? 'dark' : 'light'}; }
  * { box-sizing: border-box; }
  html, body {
    margin: 0;
    padding: 0;
    background: $background;
    color: $foreground;
  }
  body {
    font-family: ${font.cssStack};
    font-size: ${fontSizePx}px;
    line-height: $lineHeight;
    padding: 20px 18px 48px;
    -webkit-text-size-adjust: 100%;
    text-rendering: optimizeLegibility;
    word-wrap: break-word;
    overflow-wrap: anywhere;
  }
  .reader {
    max-width: ${column.rem}rem;
    margin: 0 auto;
  }
  .title {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    font-size: 1.75rem;
    font-weight: 700;
    line-height: 1.25;
    letter-spacing: -0.02em;
    margin: 0 0 10px;
  }
  .meta {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    color: $muted;
    font-size: 0.92rem;
    margin: 0 0 8px;
  }
  .subtitle {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    color: $muted;
    font-size: 1.08rem;
    line-height: 1.45;
    margin: 0 0 1.4rem;
  }
  .content > *:first-child { margin-top: 0; }
  p { margin: 0 0 1.05em; }
  h1, h2, h3, h4, h5, h6 {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    line-height: 1.3;
    letter-spacing: -0.01em;
    margin: 1.5em 0 0.6em;
  }
  h2 { font-size: 1.35rem; }
  h3 { font-size: 1.2rem; }
  a { color: $link; text-decoration-thickness: from-font; }
  strong, b { font-weight: 700; }
  em, i { font-style: italic; }
  ul, ol { padding-inline-start: 1.35em; margin: 0 0 1.05em; }
  li { margin: 0.35em 0; }
  blockquote {
    margin: 1.2em 0;
    padding-block: 0.15em;
    padding-inline-start: 1em;
    border-inline-start: 3px solid $link;
    color: $muted;
  }
  hr {
    border: 0;
    border-top: 1px solid $rule;
    margin: 1.6em 0;
  }
  figure, .captioned-image-container, .image2-inset {
    margin: 1.35em 0;
    padding: 0;
  }
  img, picture, video, iframe {
    max-width: 100%;
    height: auto;
    border-radius: 8px;
    display: block;
  }
  iframe { width: 100%; min-height: 220px; border: 0; }
  .xta-web-only p { color: $muted; font-style: italic; }
  .xta-embed-load {
    width: 100%; min-height: 48px; padding: 12px 16px; border: 1px solid $rule; border-radius: 12px;
    background: transparent; color: $link; font: inherit; text-align: center;
  }
  figcaption, .image-caption, .caption {
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
    color: $muted;
    font-size: 0.88rem;
    line-height: 1.4;
    margin-top: 0.55em;
  }
  pre, code {
    font-family: ui-monospace, SFMono-Regular, Menlo, Consolas, monospace;
    background: $codeBg;
    border-radius: 8px;
  }
  code { padding: 0.1em 0.35em; font-size: 0.92em; }
  pre {
    padding: 12px 14px;
    overflow-x: auto;
    line-height: 1.45;
  }
  pre code { padding: 0; background: transparent; }
  table {
    display: block;
    overflow-x: auto;
    width: 100%;
    border-collapse: collapse;
    margin: 1.2em 0;
    font-size: 0.95em;
  }
  th, td {
    border: 1px solid $rule;
    padding: 8px 10px;
    text-align: start;
    vertical-align: top;
  }
  .twitter-embed, .youtube-wrap, .youtube-inner {
    margin: 1.2em 0;
  }
  .preview-end {
    margin-top: 2em;
    padding-top: 1em;
    border-top: 1px solid $rule;
    color: $muted;
    font-size: 0.9em;
  }
  .preview-end p { margin: 0.4em 0; }
  [data-xta-block], [id], a[name] { scroll-margin-top: 16px; }
  [data-xta-selected] { outline: 2px solid $link; outline-offset: 5px; }
  :focus-visible { outline: 2px solid $link; outline-offset: 4px; }
</style>
</head>
<body>
  <article class="reader">
    <h1 class="title">${_escape(title)}</h1>
    ${meta.isEmpty ? '' : '<div class="meta">$meta</div>'}
    ${subtitle == null || subtitle.isEmpty ? '' : '<p class="subtitle">${_escape(subtitle)}</p>'}
    <div class="content">$cleanBody</div>
    ${_footerHtml(footer, footerLink, footerLinkLabel)}
  </article>
  <script>$substackTtsFromHereJs</script>
  <script>window.xtaEmbedLabel = ${_scriptString(embedLabel)};$substackEmbedJs</script>
  <script>window.xtaWebOnly = {label: ${_scriptString(webOnlyLabel)}, link: ${_scriptString(openLabel)}, url: ${_scriptString(postUrl ?? '')}};$substackWebOnlyJs</script>
</body>
</html>
''';
}

/// A note under the article saying where the free part stops.
///
/// Part of the page rather than a widget beneath it, so it scrolls with the
/// text and is not mistaken for a control of the app's.
String _footerHtml(String? text, String? link, String? linkLabel) {
  if (text == null || text.isEmpty) {
    return '';
  }

  final action = (link != null && link.isNotEmpty && linkLabel != null && linkLabel.isNotEmpty)
      ? '<p><a href="${_escape(link)}">${_escape(linkLabel)}</a></p>'
      : '';

  return '<div class="preview-end"><p>${_escape(text)}</p>$action</div>';
}

String _escape(String value) =>
    value.replaceAll('&', '&amp;').replaceAll('<', '&lt;').replaceAll('>', '&gt;').replaceAll('"', '&quot;');
