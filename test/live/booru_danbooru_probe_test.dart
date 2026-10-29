// A temporary live check of the fixed Danbooru search, run from CI where the
// host is reachable:
//   fvm flutter test test/live/booru_danbooru_probe_test.dart --dart-define=RUN_LIVE=true
// It prints what the client asked and got, and never fails.
import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_client.dart';

class _Logged extends http.BaseClient {
  final _inner = http.Client();
  final lines = <String>[];

  @override
  Future<http.StreamedResponse> send(http.BaseRequest request) async {
    final response = await _inner.send(request);
    lines.add('${response.statusCode} ${request.url.path} ${Uri.decodeQueryComponent(request.url.query)}');
    return response;
  }

  void flush(String label) {
    for (final line in lines) {
      print('PROBE   $label: $line');
    }
    lines.clear();
  }
}

Future<void> _scenario(String label, _Logged http, Future<String> Function() run) async {
  try {
    print('PROBE $label -> ${await run()}');
  } on BooruException catch (error) {
    print('PROBE $label -> ${error.kind} ${error.message}');
  } catch (error) {
    print('PROBE $label -> ERROR $error');
  }
  http.flush(label);
}

void main() {
  const enabled = bool.fromEnvironment('RUN_LIVE', defaultValue: false);

  test(
    'Danbooru: the fixed suggestions and searches',
    () async {
      final http = _Logged();
      final client = BooruClient(PrefServiceCache(cache: {}), httpClient: http);

      for (final typed in ['miku', 'kancolle', 'blue_eyse', '初音', 'frieren']) {
        await _scenario('suggest "$typed"', http, () async {
          final suggestions = await client.suggestTags(typed);
          return suggestions
              .take(5)
              .map((s) => '${s.alias == null ? '' : '${s.alias} -> '}${s.name} (${s.postCount}, ${s.category?.name})')
              .join(' | ');
        });
      }

      Future<String> search(String query, {int page = 1}) async {
        final result = await client.search(query, page: page);
        final ids = result.posts.take(3).map((post) => post.id).join(',');
        return '${result.posts.length} shown of ${result.allowed.length} allowed, more: ${result.hasMore}, first: $ids';
      }

      await _scenario('two tags', http, () => search('blue_eyes long_hair'));
      await _scenario('three tags', http, () => search('blue_eyes long_hair smile'));
      await _scenario('three tags, page 2', http, () => search('blue_eyes long_hair smile', page: 2));
      await _scenario('three tags, a rare one', http, () => search('blue_eyes long_hair frieren'));
      await _scenario('four tags with an exclusion', http, () => search('1girl blue_eyes long_hair -smile'));
      await _scenario('score order on a big tag', http, () => search('blue_eyes order:score'));
      await _scenario('score order past the limit', http, () => search('frieren long_hair order:score'));
      await _scenario('following, three tags', http, () async {
        final posts = await client.postsForTags(['blue_eyes long_hair smile']);
        return '${posts.length} posts';
      });

      final sensitive = BooruClient(PrefServiceCache(cache: {optionPluginBooruMaxRating: 's'}), httpClient: http);
      await _scenario('three tags, sensitive cap', http, () async {
        final result = await sensitive.search('blue_eyes long_hair smile');
        return '${result.posts.length} shown of ${result.allowed.length} allowed';
      });
    },
    timeout: const Timeout(Duration(minutes: 5)),
    skip: enabled ? false : 'Pass --dart-define=RUN_LIVE=true to hit the live services',
  );
}
