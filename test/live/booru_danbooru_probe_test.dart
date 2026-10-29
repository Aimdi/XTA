// A temporary probe of how Danbooru answers XTA's tag suggestions and tag
// searches, run from CI where the host is reachable:
//   fvm flutter test test/live/booru_danbooru_probe_test.dart --dart-define=RUN_LIVE=true
// It prints what it sees and never fails.
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;

const _danbooru = 'https://danbooru.donmai.us';
const _aibooru = 'https://aibooru.online';
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

Future<void> _posts(String host, String tags) => _raw('posts ${Uri.parse(host).host} "$tags"', host, '/posts.json', {
  'tags': tags,
  'limit': '1',
  'only': 'id,rating',
}, length: 220);

void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'Danbooru: autocomplete, tag lookups, tag limit and score order',
    () async {
      for (final query in ['miku', 'kancolle', 'blue_eyse', '初音', 'langley', 'frieren', 'ord', '-blu']) {
        await _autocomplete(_danbooru, query);
      }
      await _autocomplete(_danbooru, 'bl', limit: 20, length: 600);
      await _autocomplete(_danbooru, 'blue', limit: 25, length: 200);

      await _raw('tags name_comma', _danbooru, '/tags.json', {
        'search[name_comma]': 'blue_eyes,long_hair,kancolle,frieren,no_such_tag_xyz',
        'only': 'name,post_count,category',
        'limit': '10',
      }, length: 800);
      await _raw('aliases kancolle', _danbooru, '/tag_aliases.json', {
        'search[antecedent_name_comma]': 'kancolle,blue_eyes',
        'search[status]': 'active',
        'only': 'antecedent_name,consequent_name',
      }, length: 500);

      for (final tags in [
        'blue_eyes long_hair status:active',
        'blue_eyes long_hair -status:deleted',
        'blue_eyes long_hair limit:5',
        'blue_eyes long_hair is:sfw',
        'blue_eyes long_hair parent:none',
        'blue_eyes long_hair filetype:png',
        'blue_eyes long_hair score:>10',
        'blue_eyes long_hair age:<1w',
        'blue_eyes long_hair order:id_desc',
        'blue_eyes long_hair rating:g,s',
        'blue_eyes long_hair -rating:q -rating:e -rating:s',
        'blue_eyes -long_hair -smile',
        '~blue_eyes ~green_eyes long_hair',
        'kancolle',
      ]) {
        await _posts(_danbooru, tags);
      }

      for (final tags in [
        'blue_eyes order:score',
        'blue_eyes order:score rating:g',
        'blue_eyes order:score -rating:e',
        'blue_eyes order:score rating:g,s',
        'order:score rating:g',
        'frieren order:score rating:g',
        'blue_eyes order:favcount rating:g',
        'blue_eyes order:rank rating:g',
        'blue_eyes order:score rating:g',
      ]) {
        await _posts(_danbooru, tags);
      }

      await _autocomplete(_aibooru, 'blue', length: 800);
      await _posts(_aibooru, 'blue_eyes long_hair smile');
      await _posts(_aibooru, 'blue_eyes long_hair');
    },
    timeout: const Timeout(Duration(minutes: 5)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit the live services',
  );
}
