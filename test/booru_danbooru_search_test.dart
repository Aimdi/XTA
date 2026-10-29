import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';

const _limitError =
    '{"success":false,"error":"PostQuery::TagLimitError",'
    '"message":"You cannot search for more than 2 tags at a time.","backtrace":[]}';

const _timeoutError =
    '{"success":false,"error":"ActiveRecord::QueryCanceled",'
    '"message":"The database timed out running your query.","backtrace":[]}';

/// Danbooru as it answers a guest, from the live checks of 2026-10-29: two
/// counted tags per search, rating terms free.
class _Danbooru {
  final requests = <Uri>[];

  /// Posts by id, with their tags.
  final posts = {
    1: 'blue_eyes long_hair frieren',
    2: 'blue_eyes frieren',
    3: 'blue_eyes long_hair',
    4: 'long_hair frieren smile',
  };

  final uses = {'blue_eyes': 2524807, 'long_hair': 6357605, 'frieren': 16526, 'smile': 3000000};

  Iterable<Uri> get searches => requests.where((uri) => uri.path == '/posts.json');
  Iterable<Uri> get lookups => requests.where((uri) => uri.path == '/tags.json');

  http.Response answer(http.Request request) {
    final uri = request.url;
    requests.add(uri);
    return switch (uri.path) {
      '/posts.json' => _posts(uri.queryParameters['tags'] ?? ''),
      '/tags.json' => _tags(uri.queryParameters['search[name_comma]'] ?? ''),
      _ => http.Response('', 404),
    };
  }

  http.Response _posts(String query) {
    final tokens = query.split(' ').where((token) => token.isNotEmpty);
    final counted = tokens.where((token) => !token.contains('rating:')).toList();
    if (counted.length > 2) return http.Response(_limitError, 422);
    final matching = [
      for (final MapEntry(key: id, value: tags) in posts.entries)
        if (counted.every((tag) => tags.split(' ').contains(tag)))
          {'id': id, 'tag_string': tags, 'rating': 'g', 'file_url': 'https://cdn.example/$id.png'},
    ];
    return http.Response(jsonEncode(matching), 200);
  }

  http.Response _tags(String names) => http.Response(
    jsonEncode([
      for (final name in names.split(','))
        if (uses[name] case final count?) {'name': name, 'post_count': count, 'category': 0},
    ]),
    200,
  );
}

BooruClient _client(_Danbooru host) => BooruClient(
  PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru'}),
  httpClient: MockClient((request) async => host.answer(request)),
);

List<String> _ids(BooruPostPage page) => [for (final post in page.posts) post.id];

void main() {
  group('a search past the tag limit', () {
    test('goes out whole, then runs on the two rarest tags, the device checking the third', () async {
      final host = _Danbooru();
      final page = await _client(host).search('blue_eyes long_hair frieren');

      expect(_ids(page), ['1']);
      expect(host.searches.map((uri) => uri.queryParameters['tags']), [
        'blue_eyes long_hair frieren rating:g',
        'blue_eyes frieren rating:g',
      ]);
      expect(host.lookups, hasLength(1));
    });

    test('is shared out at once after the host has said, and keeps its split on every page', () async {
      final host = _Danbooru();
      final client = _client(host);
      await client.search('blue_eyes long_hair frieren');
      host.requests.clear();

      final first = await client.search('long_hair smile frieren');
      await client.search('long_hair smile frieren', page: 2);

      expect(_ids(first), ['4']);
      expect(host.lookups, hasLength(1), reason: 'the split is decided once');
      expect(host.searches.map((uri) => uri.queryParameters['tags']), [
        'smile frieren rating:g',
        'smile frieren rating:g',
      ]);
      expect(host.searches.map((uri) => uri.queryParameters['page']), ['1', '2']);
    });

    test('a search that fits still goes out whole', () async {
      final host = _Danbooru();
      final client = _client(host);
      await client.search('blue_eyes long_hair frieren');
      host.requests.clear();

      final page = await client.search('blue_eyes long_hair');

      expect(_ids(page), ['1', '3']);
      expect(host.lookups, isEmpty);
      expect(host.searches.single.queryParameters['tags'], 'blue_eyes long_hair rating:g');
    });

    test('works for a saved search in Following', () async {
      final host = _Danbooru();
      final posts = await _client(host).postsForTags(['blue_eyes long_hair frieren']);

      expect(posts.map((post) => post.id), ['1']);
    });

    test('keeps the tag-limit error when its metatags alone are past the limit', () async {
      final host = _Danbooru();
      final client = _client(host);
      await client.search('blue_eyes long_hair frieren');

      await expectLater(
        client.search('order:score ordfav:someone fav:someone blue_eyes'),
        throwsA(isA<BooruException>().having((error) => error.kind, 'kind', BooruErrorKind.tagLimit)),
      );
    });
  });

  test('a search the host gave up on says so', () async {
    final client = BooruClient(
      PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru'}),
      httpClient: MockClient((request) async => http.Response(_timeoutError, 500)),
    );

    await expectLater(
      client.search('blue_eyes order:score'),
      throwsA(isA<BooruException>().having((error) => error.kind, 'kind', BooruErrorKind.timedOut)),
    );
  });

  group('suggestions', () {
    test("come from Danbooru's autocomplete, with the other name each tag was found through", () async {
      final asked = <Uri>[];
      final client = BooruClient(
        PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru'}),
        httpClient: MockClient((request) async {
          asked.add(request.url);
          return http.Response(
            jsonEncode([
              {
                'type': 'tag-word',
                'value': 'kantai_collection',
                'category': 3,
                'post_count': 548021,
                'antecedent': 'kancolle',
              },
              {'type': 'tag-word', 'value': 'admiral_(kancolle)', 'category': 4, 'post_count': 39123},
            ]),
            200,
          );
        }),
      );

      final suggestions = await client.suggestTags('kancolle');

      expect(asked.single.path, '/autocomplete.json');
      expect(asked.single.queryParameters, containsPair('search[query]', 'kancolle'));
      expect(asked.single.queryParameters, containsPair('search[type]', 'tag_query'));
      expect(suggestions.map((s) => s.name), ['kantai_collection', 'admiral_(kancolle)']);
      expect(suggestions.map((s) => s.alias), ['kancolle', null]);
      expect(suggestions.first.category, BooruTagCategory.copyright);
    });

    test('fall back to the tags whose names start with what is typed when autocomplete fails', () async {
      final asked = <String>[];
      final client = BooruClient(
        PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru'}),
        httpClient: MockClient((request) async {
          asked.add(request.url.path);
          if (request.url.path == '/autocomplete.json') return http.Response('<html>no</html>', 404);
          return http.Response(
            jsonEncode([
              {'name': 'blue_eyes', 'post_count': 2524807, 'category': 0},
            ]),
            200,
          );
        }),
      );

      final suggestions = await client.suggestTags('blue');

      expect(asked, ['/autocomplete.json', '/tags.json']);
      expect(suggestions.single.name, 'blue_eyes');
    });

    test('are none, with no second request, when the host cannot be reached', () async {
      final asked = <String>[];
      final client = BooruClient(
        PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru'}),
        httpClient: MockClient((request) async {
          asked.add(request.url.path);
          throw http.ClientException('offline');
        }),
      );

      expect(await client.suggestTags('blue'), isEmpty);
      expect(asked, ['/autocomplete.json']);
    });

    test('other engines keep their tag lookup', () async {
      final asked = <String>[];
      final client = BooruClient(
        PrefServiceCache(cache: {optionPluginBooruEngine: 'e621', optionPluginBooruHost: 'https://e621.net'}),
        httpClient: MockClient((request) async {
          asked.add(request.url.path);
          return http.Response('[]', 200);
        }),
      );

      await client.suggestTags('blue');

      expect(asked, ['/tags.json']);
    });
  });

  group('autocomplete rows', () {
    test('word matches, aliases, corrected typos and other names; anything not a tag is left out', () {
      final suggestions = parseBooruAutocomplete([
        {'type': 'tag-word', 'value': 'hatsune_miku', 'category': 4, 'post_count': 148527},
        {
          'type': 'tag-autocorrect',
          'value': 'blue_eyes',
          'category': 0,
          'post_count': 2524807,
          'antecedent': 'blue_eyse',
        },
        {'type': 'tag-other-name', 'value': 'hatsune_miku', 'category': 4, 'post_count': 148527, 'antecedent': '初音ミク'},
        {'type': 'tag-word', 'value': 'same', 'antecedent': 'same'},
        {'type': 'user', 'value': 'someone'},
        {'type': 'tag-word'},
      ])!;

      expect(suggestions.map((s) => s.name), ['hatsune_miku', 'blue_eyes', 'hatsune_miku', 'same']);
      expect(suggestions.map((s) => s.alias), [null, 'blue_eyse', '初音ミク', null]);
      expect(suggestions.first.category, BooruTagCategory.character);
      expect(suggestions.first.postCount, 148527);
    });

    test('an answer that is not a list of rows is no answer', () {
      expect(parseBooruAutocomplete({'error': 'nope'}), isNull);
      expect(parseBooruAutocomplete([]), isEmpty);
    });
  });
}
