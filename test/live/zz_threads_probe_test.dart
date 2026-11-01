import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:xta/plugins/plugin_repairs.dart';
import 'package:xta/plugins/threads/threads_parse.dart';

// Temporary probe: what a guest can reach on Threads past a profile's first page. Removed once read.
void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);
  final ua = PluginRepairs.threadsWebUserAgent.value;
  // ignore: avoid_print
  void out(String line) => print('PROBE $line');
  String clip(String text, [int max = 400]) => text.length <= max ? text : '${text.substring(0, max)}…';

  Future<http.Response> page(String url) => http.get(
    Uri.parse(url),
    headers: {
      'User-Agent': ua,
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
      'Accept-Language': 'en-US,en;q=0.9',
    },
  );

  Future<http.Response> graphql(String lsd, String docId, String name, Map<String, Object?> variables) => http.post(
    Uri.parse('https://www.threads.com/api/graphql'),
    headers: {
      'User-Agent': ua,
      'Accept': '*/*',
      'Accept-Language': 'en-US,en;q=0.9',
      'X-IG-App-ID': PluginRepairs.threadsAppId.value,
      'X-ASBD-ID': PluginRepairs.threadsAsbdId.value,
      'X-FB-LSD': lsd,
      'X-FB-Friendly-Name': name,
      'Origin': 'https://www.threads.com',
      'Referer': 'https://www.threads.com/@zuck',
    },
    body: {'lsd': lsd, 'doc_id': docId, 'variables': jsonEncode(variables)},
  );

  /// Key paths that look like paging, and the shape of the answer to depth 4.
  void walk(Object? node, String path, int depth, List<String> hits) {
    if (node is Map) {
      for (final entry in node.entries) {
        final key = '${entry.key}';
        final next = '$path.$key';
        if (RegExp('cursor|page_info|has_next|end_cursor|next_max_id|paging', caseSensitive: false).hasMatch(key)) {
          hits.add('$next = ${clip(jsonEncode(entry.value), 160)}');
        }
        if (depth < 4 && entry.value is! Map && entry.value is! List) continue;
        if (depth < 4) out('shape $next ${entry.value is List ? '[${(entry.value as List).length}]' : '{}'}');
        walk(entry.value, next, depth + 1, hits);
      }
    } else if (node is List && node.isNotEmpty) {
      walk(node.first, '$path[0]', depth + 1, hits);
    }
  }

  test(
    'probe threads guest',
    () async {
      final profile = await page('https://www.threads.com/@zuck');
      final html = profile.body;
      final lsd = extractThreadsLsd(html);
      final userId = extractThreadsUserIdFromHtml(html, 'zuck');
      out('profile ${profile.statusCode} ${html.length} lsd ${lsd != null} user $userId');

      // Relay operations the web app names in its scripts.
      final ops = <String, String>{};
      final opPattern = RegExp(
        r'__d\("([A-Za-z0-9_]+?Query)_[A-Za-z]*[Rr]elayOperation",\[\],\(function\([a-z,]*\)\{e\.exports="(\d+)"',
      );
      void collect(String body) {
        for (final match in opPattern.allMatches(body)) {
          ops[match.group(1)!] = match.group(2)!;
        }
      }

      collect(html);
      final scripts = RegExp(
        r'src="(https://static\.cdninstagram\.com/rsrc\.php/[^"]+\.js[^"]*)"',
      ).allMatches(html).map((match) => match.group(1)!.replaceAll('&amp;', '&')).toSet().toList();
      out('scripts ${scripts.length}');
      for (final src in scripts.take(80)) {
        try {
          collect((await http.get(Uri.parse(src), headers: {'User-Agent': ua})).body);
        } catch (error) {
          out('script failed $error');
        }
      }
      final wanted = RegExp(
        'Profile|Search|Tag|Topic|Feed|Repl|Repost|Quote|Liker|Refetch|Pagination',
        caseSensitive: false,
      );
      for (final entry in ops.entries.where((entry) => wanted.hasMatch(entry.key))) {
        out('op ${entry.key} ${entry.value}');
      }
      out('ops total ${ops.length}');

      if (lsd == null || userId == null) return;
      final first = await graphql(
        lsd,
        PluginRepairs.threadsGuestDocId.value,
        PluginRepairs.threadsGuestQueryName.value,
        {'userID': userId},
      );
      out(
        'first page ${first.statusCode} ${first.body.length} posts ${parseThreadsGraphqlFeed(jsonDecode(first.body)).length}',
      );
      final hits = <String>[];
      walk(jsonDecode(first.body), '', 0, hits);
      for (final hit in hits) {
        out('paging $hit');
      }

      // A next page, if the scripts name a refetch query for the profile's threads tab.
      final cursor = RegExp(r'"end_cursor":"([^"]+)"').firstMatch(first.body)?.group(1);
      for (final entry in ops.entries.where((entry) => entry.key.contains('ProfileThreads'))) {
        for (final variables in [
          {'userID': userId, 'after': cursor, 'first': 10},
          {'userID': userId, 'after': cursor, 'before': null, 'first': 10, 'last': null},
        ]) {
          final next = await graphql(lsd, entry.value, entry.key, variables);
          final body = next.body;
          var posts = 0;
          try {
            posts = parseThreadsGraphqlFeed(jsonDecode(body)).length;
          } catch (_) {}
          out(
            'next ${entry.key} ${jsonEncode(variables.keys.toList())} -> ${next.statusCode} posts $posts ${clip(body, 300)}',
          );
        }
      }

      // Search and tag pages as a guest.
      for (final url in [
        'https://www.threads.com/search?q=flutter&serp_type=default',
        'https://www.threads.com/search?q=flutter&serp_type=default&filter=recent',
        'https://www.threads.com/search?q=books&serp_type=tags',
      ]) {
        final response = await page(url);
        final body = response.body;
        collect(body);
        out(
          'search $url -> ${response.statusCode} ${body.length} thread_items ${'"thread_items"'.allMatches(body).length} '
          'codes ${RegExp(r'"code":"[A-Za-z0-9_-]{6,}"').allMatches(body).length} '
          'chains ${threadsSsrChains(body).length} login ${body.contains('Log in')}',
        );
      }
      for (final entry in ops.entries.where((entry) => entry.key.contains('Search'))) {
        final response = await graphql(lsd, entry.value, entry.key, {
          'query': 'flutter',
          'first': 10,
          'search_surface': 'default',
          'recent': 0,
        });
        out('search op ${entry.key} -> ${response.statusCode} ${clip(response.body, 300)}');
      }
    },
    timeout: const Timeout(Duration(minutes: 6)),
    skip: enabled ? false : 'live',
  );
}
