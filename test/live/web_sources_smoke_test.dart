import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/websources/web_source_catalogue.dart';
import 'package:xta/plugins/websources/web_source_client.dart';
import 'package:xta/plugins/websources/web_source_definition.dart';
import 'package:xta/plugins/websources/web_source_items.dart';

/// Every published web source, read live. Opt in with:
///   flutter test test/live/web_sources_smoke_test.dart --dart-define=RUN_LIVE=true
///
/// Reads this checkout's sources.json and sources/*.json (on the default
/// branch, what installs are offered) and, for each source, the first page
/// of every list, its search, and the full text of one post when it names
/// where that is. A list that brings no posts fails it: the site changed,
/// and the source's file needs a new rule.
const _queries = {'devto': 'flutter', 'lemmy-world': 'linux', 'nasa-images': 'mars'};

Future<List<String>> _check(WebSourceClient client, WebSourceDefinition source) async {
  final problems = <String>[];
  final lists = [...source.feeds, if (source.search case final search? when _queries.containsKey(source.id)) search];
  for (final list in lists) {
    final url = webSourceListUrl(list, query: _queries[source.id]);
    try {
      final page = webSourcePage(
        source,
        list,
        await client.page(source, url!, list.type),
        url,
        query: _queries[source.id],
      );
      final first = page.items.firstOrNull;
      // ignore: avoid_print
      print(
        '${source.id}/${list.id}: ${page.items.length} posts, next ${page.next != null}; '
        'first: ${first?.title ?? first?.text} | ${first?.link} | ${first?.date} | '
        '${first?.author} | score ${first?.score} | comments ${first?.comments} | pictures ${first?.pictures.length}',
      );
      if (page.items.isEmpty) problems.add('${source.id}/${list.id}: no posts');
      if (first != null && list == source.feeds.first) problems.addAll(await _checkArticle(client, source, first));
    } on WebSourceFailure catch (failure) {
      problems.add('${source.id}/${list.id}: $failure');
    }
  }
  return problems;
}

Future<List<String>> _checkArticle(WebSourceClient client, WebSourceDefinition source, WebSourceItem item) async {
  final article = source.article;
  if (article == null) return const [];
  final url = webSourceArticleUrl(article, item);
  if (url == null) return ['${source.id}: no article address for ${item.id}'];
  final html = webSourceArticleHtml(article, await client.page(source, url, article.type));
  // ignore: avoid_print
  print('${source.id} article: ${html?.length ?? 0} characters');
  return html == null || html.isEmpty ? ['${source.id}: the article rule found no text'] : const [];
}

void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'every published web source brings posts',
    () async {
      final client = WebSourceClient();
      final listings = parseWebSourceCatalogue(
        File('sources.json').readAsStringSync(),
        Uri.parse(defaultWebSourceCatalogueUrl),
      );
      final problems = <String>[];
      for (final listing in listings) {
        final path = listing.url.path.split('/$githubPublishBranch/').last;
        switch (readWebSource(File(path).readAsStringSync())) {
          case ReadWebSource(:final source):
            problems.addAll(await _check(client, source));
          case RefusedWebSource(:final refusal, :final detail):
            problems.add('${listing.id}: refused, $refusal $detail');
        }
      }
      expect(problems, isEmpty, reason: problems.join('\n'));
    },
    timeout: const Timeout(Duration(minutes: 5)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to read the live sites',
  );
}
