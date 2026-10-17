import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:html/parser.dart' as html_parser;
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';

/// [publications] as OPML 2.0: one RSS outline each, with its site and feed.
String substackOpml(List<SubstackPublication> publications, {required String title}) {
  final escape = const HtmlEscape(HtmlEscapeMode.attribute).convert;
  final outlines = [
    for (final publication in publications)
      '    <outline type="rss" text="${escape(publication.name)}" title="${escape(publication.name)}" '
          'xmlUrl="${escape(_origin(publication.baseUrl))}/feed" htmlUrl="${escape(_origin(publication.baseUrl))}"/>',
  ];
  return '<?xml version="1.0" encoding="UTF-8"?>\n'
      '<opml version="2.0">\n'
      '  <head><title>${const HtmlEscape(HtmlEscapeMode.element).convert(title)}</title></head>\n'
      '  <body>\n'
      '${outlines.join('\n')}\n'
      '  </body>\n'
      '</opml>\n';
}

String _origin(String baseUrl) => Uri.tryParse(baseUrl)?.origin ?? baseUrl;

/// The sites an OPML file lists, once each, in its order: an outline's
/// `htmlUrl`, else its `xmlUrl` without the feed path.
List<Uri> substackOpmlSites(String opml) {
  final document = html_parser.parse(opml);
  final sites = <String, Uri>{};
  for (final outline in document.querySelectorAll('outline')) {
    final raw = outline.attributes['htmlurl'] ?? outline.attributes['xmlurl'];
    final uri = Uri.tryParse(raw?.trim() ?? '');
    if (uri == null || (uri.scheme != 'https' && uri.scheme != 'http') || uri.host.isEmpty) continue;
    sites.putIfAbsent(uri.host.toLowerCase().replaceFirst(RegExp(r'^www\.'), ''), () => Uri.parse(uri.origin));
  }
  return sites.values.toList(growable: false);
}

/// How an OPML import went.
class SubstackOpmlImport {
  final int added;
  final int alreadyFollowed;
  final int failed;
  final int total;

  const SubstackOpmlImport({this.added = 0, this.alreadyFollowed = 0, this.failed = 0, required this.total});

  int get done => added + alreadyFollowed + failed;

  SubstackOpmlImport _with({int added = 0, int alreadyFollowed = 0, int failed = 0}) => SubstackOpmlImport(
    added: this.added + added,
    alreadyFollowed: this.alreadyFollowed + alreadyFollowed,
    failed: this.failed + failed,
    total: total,
  );
}

/// Follows the sites of an OPML file one at a time, through the client's
/// limiter. A followed one is left as it is; a rate limit ends the import and
/// counts what is left as failed.
class SubstackOpmlImportStore extends Store<SubstackOpmlImport?> {
  final SubstackClient client;
  final SubstackPublicationsStore publications;

  var _closed = false;

  SubstackOpmlImportStore(this.client, this.publications) : super(null);

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }

  Future<SubstackOpmlImport> run(List<Uri> sites) async {
    var progress = SubstackOpmlImport(total: sites.length);
    if (!_closed) update(progress);
    for (final (index, site) in sites.indexed) {
      // Leaving the settings ends the import; what was added stays followed.
      if (_closed) return progress;
      try {
        final publication = await client.resolvePublication(site.toString());
        if (publications.followOf(publication) != null) {
          progress = progress._with(alreadyFollowed: 1);
        } else {
          await publications.add(publication);
          progress = progress._with(added: 1);
        }
      } on SubstackRateLimitedException {
        progress = progress._with(failed: sites.length - index);
        break;
      } catch (_) {
        progress = progress._with(failed: 1);
      }
      if (!_closed) update(progress);
    }
    if (!_closed) update(progress);
    return progress;
  }
}
