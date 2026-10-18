import 'dart:convert';

import 'package:html/dom.dart';
import 'package:html/parser.dart' as html;
import 'package:xta/plugins/substack/substack_html.dart';

class SubstackArticlePassage {
  final String anchor;
  final String text;
  final int headingLevel;

  const SubstackArticlePassage({required this.anchor, required this.text, this.headingLevel = 0});

  String excerpt(String query) {
    final term = _terms(query).firstOrNull ?? '';
    final at = text.toLowerCase().indexOf(term);
    final start = at > 72 ? text.lastIndexOf(' ', at - 48) + 1 : 0;
    if (text.length - start <= 240) return '${start > 0 ? '…' : ''}${text.substring(start)}';
    final end = text.lastIndexOf(' ', start + 240);
    return '${start > 0 ? '…' : ''}${text.substring(start, end > start ? end : start + 240)}…';
  }
}

/// A post body parsed once: the sanitized page body, its passages for
/// navigation and search, and its speech text.
class SubstackArticleDocument {
  final String body;
  final List<SubstackArticlePassage> passages;
  final List<SubstackArticlePassage> headings;
  final String plainText;

  const SubstackArticleDocument({
    required this.body,
    required this.passages,
    required this.headings,
    this.plainText = '',
  });

  factory SubstackArticleDocument.parse(String raw) {
    final fragment = html.parseFragment(sanitizeSubstackBodyHtml(raw));
    for (final node in fragment.querySelectorAll('[data-xta-block]')) {
      node.attributes.remove('data-xta-block');
    }
    final plainText = substackPlainTextOf(fragment);
    _holdEmbeds(fragment);
    const selector = 'h1,h2,h3,h4,h5,h6,p,li,blockquote,pre,figcaption,td,th';
    var blocks = fragment.querySelectorAll(selector).where((node) => node.querySelector(selector) == null).toList();
    if (blocks.isEmpty && fragment.text?.trim().isNotEmpty == true) {
      final paragraph = Element.tag('p')..nodes.addAll(fragment.nodes.toList());
      fragment.nodes.add(paragraph);
      blocks = [paragraph];
    }
    final passages = <SubstackArticlePassage>[];
    for (final node in blocks) {
      final text = node.text.replaceAll(RegExp(r'\s+'), ' ').trim();
      if (text.isEmpty || _hidden(node)) continue;
      final anchor = 'xta-passage-${passages.length}';
      node.attributes['data-xta-block'] = anchor;
      final tag = node.localName ?? '';
      final level = RegExp(r'^h[1-6]$').hasMatch(tag) ? int.parse(tag.substring(1)) : 0;
      passages.add(SubstackArticlePassage(anchor: anchor, text: text, headingLevel: level));
    }
    return SubstackArticleDocument(
      body: fragment.outerHtml,
      passages: List.unmodifiable(passages),
      headings: List.unmodifiable(passages.where((passage) => passage.headingLevel > 0)),
      plainText: plainText,
    );
  }

  List<SubstackArticlePassage> search(String query) {
    final terms = _terms(query);
    if (terms.isEmpty) return const [];
    return passages
        .where((passage) {
          final text = passage.text.toLowerCase();
          return terms.every(text.contains);
        })
        .toList(growable: false);
  }
}

/// The last [capacity] parsed articles, oldest first. An entry is reused
/// only for the very same body, so an edited post is parsed again.
class SubstackParsedArticles {
  SubstackParsedArticles({this.capacity = 16});

  final int capacity;
  final _entries = <String, ({String body, SubstackArticleDocument document})>{};

  SubstackArticleDocument? get(String key, String body) {
    final entry = _entries.remove(key);
    if (entry == null || entry.body != body) return null;
    _entries[key] = entry;
    return entry.document;
  }

  void put(String key, String body, SubstackArticleDocument document) {
    _entries.remove(key);
    _entries[key] = (body: body, document: document);
    while (_entries.length > capacity) {
      _entries.remove(_entries.keys.first);
    }
  }
}

/// Parses shared by every reader in this run of the app.
final substackParsedArticles = SubstackParsedArticles();

/// Puts each iframe in an inert template behind a placeholder naming its
/// host, so nothing from another site loads until the reader asks for it.
void _holdEmbeds(DocumentFragment fragment) {
  for (final frame in List<Element>.from(fragment.querySelectorAll('iframe'))) {
    final host = Uri.tryParse(frame.attributes['src'] ?? '')?.host ?? '';
    final held = Element.tag('div')
      ..classes.add('xta-embed')
      ..attributes['data-host'] = host.startsWith('www.') ? host.substring(4) : host;
    frame.replaceWith(held);
    held.append(Element.tag('template')..append(frame));
  }
}

bool _hidden(Element element) {
  Element? node = element;
  while (node != null) {
    if (node.attributes.containsKey('hidden') || node.attributes['aria-hidden'] == 'true') return true;
    final style = node.attributes['style'] ?? '';
    if (RegExp(r'(?:^|;)\s*(display\s*:\s*none|visibility\s*:\s*hidden)\b', caseSensitive: false).hasMatch(style)) {
      return true;
    }
    node = node.parent;
  }
  return false;
}

List<String> _terms(String query) =>
    query.trim().toLowerCase().split(RegExp(r'\s+')).where((s) => s.isNotEmpty).toList();

/// Resolves a fragment only when it belongs to this exact article.
String? substackArticleFragment(String url, String? canonical) {
  final base = Uri.tryParse(canonical ?? '');
  final parsed = Uri.tryParse(url);
  if (parsed == null) return null;
  final target = base?.resolveUri(parsed) ?? parsed;
  if (!target.hasFragment || target.fragment.isEmpty) return null;
  String fragment;
  try {
    fragment = Uri.decodeComponent(target.fragment);
  } on FormatException {
    return null;
  }
  if (url.startsWith('#')) return fragment;
  if (base == null || !base.hasAuthority) return null;
  if (target.scheme != base.scheme || target.host != base.host || target.port != base.port) return null;
  if (target.path.replaceFirst(RegExp(r'/$'), '') != base.path.replaceFirst(RegExp(r'/$'), '')) return null;
  return fragment;
}

/// Serialized arguments never become executable publisher text.
String substackArticleJumpJs(String anchor, {bool publisherAnchor = false}) =>
    '''
(function() {
  const root = document.querySelector('.content');
  if (!root) return;
  const anchor = ${jsonEncode(anchor)};
  const publisher = $publisherAnchor;
  const nodes = root.querySelectorAll(publisher ? '[id],a[name]' : '[data-xta-block]');
  const node = Array.from(nodes).find(function(n) {
    return publisher ? n.id === anchor || n.getAttribute('name') === anchor : n.getAttribute('data-xta-block') === anchor;
  });
  if (!node) return;
  const old = root.querySelector('[data-xta-selected]');
  if (old) old.removeAttribute('data-xta-selected');
  node.setAttribute('data-xta-selected', 'true');
  if (window.xtaArticle && window.xtaArticle.jumpTo) window.xtaArticle.jumpTo(node);
  else node.scrollIntoView({block: 'start', behavior: 'auto'});
  node.setAttribute('tabindex', '-1');
  node.focus({preventScroll: true});
})();
''';
