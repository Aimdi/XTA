// A temporary probe of how Danbooru answers XTA's tag suggestions and tag
// searches, run from CI where the host is reachable:
//   fvm flutter test test/live/booru_danbooru_probe_test.dart --dart-define=RUN_LIVE=true
// It prints what it sees and never fails.
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_models.dart';

const _host = 'https://danbooru.donmai.us';
const _agent = 'XTA-Booru/0.1 (by Aimdi; read-only; +https://github.com/Aimdi/XTA)';

String _short(String text, [int length = 360]) =>
    text.length <= length ? text : '${text.substring(0, length)}…';

Future<void> _raw(String label, String path, Map<String, String> query) async {
  final uri = Uri.parse(_host).replace(path: path, queryParameters: query);
  try {
    final response = await http.get(uri, headers: {'User-Agent': _agent, 'Accept': 'application/json'});
    print('PROBE $label -> ${response.statusCode} ${_short(response.body.replaceAll('\n', ' '))}');
  } catch (error) {
    print('PROBE $label -> ERROR $error');
  }
  await Future<void>.delayed(const Duration(milliseconds: 700));
}

Future<void> _search(BooruClient client, String label, String query) async {
  try {
    final page = await client.search(query, limit: 5);
    print('PROBE client "$label" "$query" -> ${page.posts.length} shown of ${page.allowed.length} allowed');
  } on BooruException catch (error) {
    print('PROBE client "$label" "$query" -> ${error.kind} ${error.message}');
  } catch (error) {
    print('PROBE client "$label" "$query" -> ERROR $error');
  }
  await Future<void>.delayed(const Duration(milliseconds: 700));
}

void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'Danbooru: suggestions and searches',
    () async {
      for (final prefix in ['blue', 'blue_e', 'miku', 'chainsaw', 'long_h', 'original']) {
        final client = BooruClient(PrefServiceCache(cache: {}));
        final suggestions = await client.suggestTags(prefix);
        print(
          'PROBE suggest "$prefix" -> ${suggestions.length}: '
          '${suggestions.map((s) => '${s.name}(${s.postCount},${s.category?.name})').join(' ')}',
        );
      }

      await _raw('tags blue*', '/tags.json', {
        'search[name_matches]': 'blue*',
        'search[order]': 'count',
        'limit': '5',
      });
      await _raw('tags miku*', '/tags.json', {'search[name_matches]': 'miku*', 'search[order]': 'count', 'limit': '5'});
      await _raw('autocomplete blue', '/autocomplete.json', {
        'search[query]': 'blue',
        'search[type]': 'tag_query',
        'limit': '5',
      });
      await _raw('autocomplete miku', '/autocomplete.json', {
        'search[query]': 'miku',
        'search[type]': 'tag_query',
        'limit': '5',
      });

      for (final tags in [
        'blue_eyes',
        'blue_eyes rating:g',
        'blue_eyes long_hair',
        'blue_eyes long_hair rating:g',
        'blue_eyes long_hair -rating:q -rating:e',
        'blue_eyes long_hair -rating:e',
        'blue_eyes long_hair smile',
        'blue_eyes order:score rating:g',
        'blue_eyes long_hair order:score',
        '-blue_eyes long_hair',
        '~blue_eyes ~green_eyes',
      ]) {
        await _raw('posts "$tags"', '/posts.json', {'tags': tags, 'limit': '2', 'only': 'id,rating'});
      }

      for (final (label, rating) in [('general', 'g'), ('sensitive', 's'), ('questionable', 'q'), ('explicit', 'e')]) {
        final client = BooruClient(PrefServiceCache(cache: {optionPluginBooruMaxRating: rating}));
        await _search(client, label, 'blue_eyes');
        await _search(client, label, 'blue_eyes long_hair');
        await _search(client, label, 'blue_eyes long_hair smile');
      }
    },
    timeout: const Timeout(Duration(minutes: 5)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit the live services',
  );

  test('BooruTagCategory names exist', () => expect(BooruTagCategory.values, isNotEmpty));
}
