import 'package:flutter_triple/flutter_triple.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:xta/offline/offline_article.dart';
import 'package:xta/offline/offline_store.dart';
import 'package:xta/plugins/substack/substack_article_cache.dart';
import 'package:xta/plugins/substack/substack_html.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/utils/background_parse.dart';

/// The plain text of a post body, as the search reads it.
String substackBodyText(String bodyHtml) => substackPlainTextOf(html_parser.parseFragment(bodyHtml));

/// A post's body as this device keeps it: the offline copy first, then the
/// article cache. Nothing is fetched.
Future<String?> substackKeptBody(SubstackPost post, {SubstackArticleCache? cache}) async {
  final offline = await OfflineStore.shared.article(OfflineArticle.substack(post).id, canonicalUrl: post.canonicalUrl);
  if (offline?.bodyHtml case final body? when body.isNotEmpty) return body;
  return (await (cache ?? SubstackArticleCache()).get(post.publication, post.slug))?.bodyHtml;
}

/// The words around the first match of [query] in [text], or null.
String? substackMatchSnippet(String text, String lowerText, String query, {int around = 60}) {
  final at = lowerText.indexOf(query);
  if (at < 0) return null;
  final start = (at - around).clamp(0, text.length);
  final end = (at + query.length + around).clamp(0, text.length);
  return '${start > 0 ? '…' : ''}${text.substring(start, end).trim()}${end < text.length ? '…' : ''}';
}

/// Searches the bodies of the library's posts that are kept on this device
/// (offline or in the article cache). Its state maps a post's key to the
/// passage that matched. Bodies are read and turned into text once.
class SubstackBodySearchStore extends Store<Map<String, String>> {
  /// The kept body of a post, or null when the device has none.
  final Future<String?> Function(SubstackPost post) bodyOf;
  final _texts = <String, ({String text, String lower})>{};
  var _request = 0;

  SubstackBodySearchStore(this.bodyOf) : super(const {});

  Future<void> search(String query, Iterable<SubstackPost> posts) async {
    final request = ++_request;
    final q = query.trim().toLowerCase();
    if (q.length < 2) return update(const {});
    for (final post in posts) {
      final key = substackPostKey(post);
      if (_texts.containsKey(key)) continue;
      final body = await bodyOf(post);
      final text = body == null || body.isEmpty ? '' : await parseInBackground<String>(substackBodyText, body);
      _texts[key] = (text: text, lower: text.toLowerCase());
    }
    if (request != _request) return;
    update({for (final post in posts) substackPostKey(post): ?_passage(post, q)});
  }

  String? _passage(SubstackPost post, String query) => switch (_texts[substackPostKey(post)]) {
    final kept? => substackMatchSnippet(kept.text, kept.lower, query),
    null => null,
  };
}
