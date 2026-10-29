// A temporary probe of how Danbooru answers XTA's tag suggestions and tag
// searches, run from CI where the host is reachable:
//   fvm flutter test test/live/booru_danbooru_probe_test.dart --dart-define=RUN_LIVE=true
// It prints what it sees and never fails.
import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

const _danbooru = 'https://danbooru.donmai.us';
const _agent = 'XTA-Booru/0.1 (by Aimdi; read-only; +https://github.com/Aimdi/XTA)';

String _short(String text, int length) => text.length <= length ? text : '${text.substring(0, length)}…';

Future<void> _raw(String label, String host, String path, Map<String, String> query, {int length = 400}) async {
  final uri = Uri.parse(host).replace(path: path, queryParameters: query);
  final watch = Stopwatch()..start();
  try {
    final response = await http.get(uri, headers: {'User-Agent': _agent, 'Accept': 'application/json'});
    print(
      'PROBE $label -> ${response.statusCode} in ${watch.elapsedMilliseconds} ms '
      '${_short(response.body.replaceAll('\n', ' '), length)}',
    );
  } catch (error) {
    print('PROBE $label -> ERROR $error');
  }
  await Future<void>.delayed(const Duration(milliseconds: 800));
}

Future<void> _autocomplete(String host, String query, {int limit = 12, int length = 3000}) => _raw(
  'autocomplete ${Uri.parse(host).host} "$query" limit $limit',
  host,
  '/autocomplete.json',
  {'search[query]': query, 'search[type]': 'tag_query', 'limit': '$limit'},
  length: length,
);

Future<void> _compact(String host, String query) async {
  final uri = Uri.parse(host).replace(
    path: '/autocomplete.json',
    queryParameters: {'search[query]': query, 'search[type]': 'tag_query', 'limit': '12'},
  );
  try {
    final response = await http.get(uri, headers: {'User-Agent': _agent, 'Accept': 'application/json'});
    final decoded = jsonDecode(response.body);
    final rows = decoded is List
        ? [
            for (final row in decoded)
              if (row is Map)
                '${row['type']}:${row['value']}(${row['post_count']},${row['category']})'
                    '${row['antecedent'] == null ? '' : ' <- ${row['antecedent']}'}',
          ]
        : ['not a list: ${_short(response.body, 200)}'];
    print('PROBE compact "$query" -> ${response.statusCode} ${rows.join(' | ')}');
  } catch (error) {
    print('PROBE compact "$query" -> ERROR $error');
  }
  await Future<void>.delayed(const Duration(milliseconds: 800));
}

void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'Danbooru: autocomplete, tag lookups, tag limit and score order',
    () async {
      for (final query in [
        'miku',
        'kancolle',
        'blue_eyse',
        '初音',
        'langley',
        'hatsune_m',
        'rating',
        '1gir',
        'fate/',
        'maid_',
      ]) {
        await _compact(_danbooru, query);
      }
      for (final query in ['kancolle', 'blue_eyse', '初音']) {
        await _autocomplete(_danbooru, query, limit: 2, length: 1200);
      }
    },
    timeout: const Timeout(Duration(minutes: 5)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit the live services',
  );
}
