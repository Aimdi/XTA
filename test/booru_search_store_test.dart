import 'dart:io';

import 'package:flutter_test/flutter_test.dart';
import 'package:http/http.dart' as http;
import 'package:http/testing.dart';
import 'package:pref/pref.dart';
import 'package:sqflite_common_ffi/sqflite_ffi.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_parse.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';

class _Client extends BooruClient {
  _Client(super.prefs);

  final prefixes = <String>[];
  final queries = <String>[];

  @override
  Future<List<BooruTagSuggestion>> suggestTags(String prefix, {int limit = 12}) async {
    prefixes.add(prefix);
    return [
      const BooruTagSuggestion(name: 'blue_eyes', postCount: 1200, category: BooruTagCategory.general),
      const BooruTagSuggestion(name: 'blue_archive', postCount: 900, category: BooruTagCategory.copyright),
    ].where((s) => s.name.startsWith(prefix)).toList();
  }

  @override
  Future<BooruPostPage> search(String query, {int page = 1, int limit = BooruClient.defaultPageSize}) async {
    queries.add(query);
    return const BooruPostPage(posts: [], page: 1, hasMore: false);
  }
}

PluginSearchHistoryStore _history(BasePrefService prefs) =>
    PluginSearchHistoryStore(prefs, optionPluginBooruSearchHistory, identity: booruTagSetKey);

BooruSearchStore _store(PrefServiceCache prefs, {String initialQuery = ''}) =>
    BooruSearchStore(_Client(prefs), _history(prefs), initialQuery: initialQuery, debounce: Duration.zero);

Future<void> _settle() => Future<void>.delayed(const Duration(milliseconds: 80));

BooruPost _post(String id, List<String> tags) => BooruPost(
  id: id,
  host: 'https://danbooru.donmai.us',
  engine: 'danbooru',
  tags: tags,
  rating: BooruRating.general,
  score: 1,
  width: 10,
  height: 10,
  previewUrl: null,
  sampleUrl: null,
  fileUrl: null,
  fileExt: 'jpg',
  source: null,
  createdAt: null,
);

void main() {
  group('query helpers', () {
    test('appending skips repeats and lets a tag take its rival’s place', () {
      expect(appendBooruTokens(['cat'], ['cat', 'dog']), ['cat', 'dog']);
      expect(appendBooruTokens(['cat', 'dog'], ['-cat']), ['dog', '-cat']);
      expect(appendBooruTokens(['rating:g', 'cat'], ['rating:e']), ['cat', 'rating:e']);
      expect(appendBooruTokens(['order:score'], ['sort:random']), ['sort:random']);
      expect(appendBooruTokens(['-rating:e'], ['-rating:q']), ['-rating:e', '-rating:q']);
      expect(appendBooruTokens(['cat'], ['-', '~', 'rating:']), ['cat']);
    });

    test('typing splits finished tags from the one still being typed', () {
      List<Object> parts(String text) {
        final split = splitBooruInput(text);
        return [split.done, split.rest];
      }

      expect(parts('blu'), [<String>[], 'blu']);
      expect(parts('cat_ears '), [
        ['cat_ears'],
        '',
      ]);
      expect(parts('a  b -c'), [
        ['a', 'b'],
        '-c',
      ]);
    });

    test('suggestions ask for the tag without its operator and skip metatags', () {
      expect(booruSuggestionPrefix('-Blu'), 'blu');
      expect(booruSuggestionPrefix('~blu'), 'blu');
      expect(booruSuggestionPrefix('b'), isNull);
      expect(booruSuggestionPrefix('rating:g'), isNull);
      expect(booruSuggestionPrefix('re:ze'), 're:ze');
      expect(booruTokenFor('-blu', 'blue_eyes'), '-blue_eyes');
      expect(booruTokenFor('blu', 'blue_eyes'), 'blue_eyes');
    });

    test('a tag set is one history entry whatever its order', () {
      expect(booruTagSetKey('solo Blue_Eyes'), booruTagSetKey('blue_eyes  solo'));
      expect(normaliseBooruQuery('  Blue_Eyes   solo solo '), 'blue_eyes solo');
      expect(normaliseBooruQuery('   '), isNull);
    });

    test('operators toggle on the tag being typed', () {
      expect(toggleBooruOperator('', BooruTagOperator.exclude), '-');
      expect(toggleBooruOperator('blue', BooruTagOperator.exclude), '-blue');
      expect(toggleBooruOperator('-blue', BooruTagOperator.exclude), 'blue');
      expect(toggleBooruOperator('-blue', BooruTagOperator.either), '~blue');
    });

    test('related tags are the ones shared most, minus the query', () {
      final posts = [
        _post('1', ['cat', 'solo', 'smile']),
        _post('2', ['cat', 'solo', 'hat']),
        _post('3', ['cat', 'smile', 'solo']),
      ];
      expect(booruRelatedTags(posts, ['cat']), ['solo', 'smile']);
      expect(booruRelatedTags(posts, ['-solo', 'cat']), ['smile']);
    });

    test('rating metatags follow each engine’s spelling', () {
      const danbooru = 'https://danbooru.donmai.us';
      expect(booruRatingMetatag(BooruEngine.danbooru, BooruRating.general, host: danbooru), 'rating:g');
      expect(booruRatingMetatag(BooruEngine.e621, BooruRating.general, host: 'https://e621.net'), 'rating:s');
      expect(booruRatingMetatag(BooruEngine.moebooru, BooruRating.sensitive, host: 'https://yande.re'), isNull);
      expect(
        booruRatingMetatag(BooruEngine.gelbooruV2, BooruRating.general, host: 'https://gelbooru.com'),
        'rating:general',
      );
      expect(
        booruRatingMetatag(BooruEngine.gelbooruV2, BooruRating.general, host: 'https://rule34.xxx'),
        'rating:safe',
      );
      expect(booruScoreOrderMetatag(BooruEngine.gelbooruV2), 'sort:score');
      expect(booruSupportsEither(BooruEngine.gelbooruV2), isFalse);
    });
  });

  group('suggestion categories', () {
    test('each engine’s numbering maps to the same kinds', () {
      List<BooruTagCategory?> kinds(Object raw, BooruEngine engine) => [
        for (final s in parseBooruTagSuggestions(raw, engine: engine)) s.category,
      ];
      expect(
        kinds([
          {'name': 'a', 'post_count': 1, 'category': 1},
          {'name': 'b', 'post_count': 1, 'category': 4},
          {'name': 'c', 'post_count': 1, 'category': 5},
        ], BooruEngine.danbooru),
        [BooruTagCategory.artist, BooruTagCategory.character, BooruTagCategory.meta],
      );
      expect(
        kinds([
          {'name': 'a', 'post_count': 1, 'category': 5},
          {'name': 'b', 'post_count': 1, 'category': 7},
        ], BooruEngine.e621),
        [BooruTagCategory.species, BooruTagCategory.meta],
      );
      expect(
        kinds([
          {'name': 'a', 'count': 1, 'type': 3},
        ], BooruEngine.moebooru),
        [BooruTagCategory.copyright],
      );
      expect(
        kinds({
          'tag': [
            {'name': 'a', 'count': '4', 'type': 'artist'},
            {'name': 'b', 'count': '4', 'type': '4'},
          ],
        }, BooruEngine.gelbooruV2),
        [BooruTagCategory.artist, BooruTagCategory.character],
      );
    });
  });

  group('history store', () {
    test('remembers newest first, one entry per tag set, and survives a restart', () async {
      final prefs = PrefServiceCache();
      final history = _history(prefs);
      await history.remember('cat solo');
      await history.remember('dog');
      await history.remember('solo cat');
      expect(history.state, ['solo cat', 'dog']);

      final restarted = _history(PrefServiceCache(cache: Map.of(prefs.toMap())));
      expect(restarted.state, ['solo cat', 'dog']);
    });

    test('forgets one entry or all of them', () async {
      final history = _history(PrefServiceCache());
      await history.remember('a');
      await history.remember('b');
      await history.forget('a');
      expect(history.state, ['b']);
      await history.clear();
      expect(history.state, isEmpty);
    });

    test('keeps at most the shared cap', () async {
      final history = _history(PrefServiceCache());
      for (var i = 0; i < pluginSearchHistoryCap + 5; i++) {
        await history.remember('tag_$i');
      }
      expect(history.state, hasLength(pluginSearchHistoryCap));
      expect(history.state.first, 'tag_${pluginSearchHistoryCap + 4}');
    });
  });

  group('search store', () {
    test('a space finishes a tag; what follows stays in the field', () {
      final store = _store(PrefServiceCache());
      expect(store.type('cat_ears blu'), 'blu');
      expect(store.state.tags, ['cat_ears']);
      expect(store.state.input, 'blu');
    });

    test('a picked suggestion is appended and keeps a typed minus', () async {
      final prefs = PrefServiceCache();
      final client = _Client(prefs);
      final store = BooruSearchStore(client, _history(prefs), debounce: Duration.zero);
      store.type('cat_ears -blu');
      await _settle();
      expect(client.prefixes, ['blu']);
      expect(store.state.suggestions.map((s) => s.name), ['blue_eyes', 'blue_archive']);

      store.pick(store.state.suggestions.last);
      expect(store.state.tags, ['cat_ears', '-blue_archive']);
      expect(store.state.input, isEmpty);
      expect(store.state.categories['blue_archive'], BooruTagCategory.copyright);
    });

    test('every search is remembered, however it started', () async {
      final prefs = PrefServiceCache();
      final store = _store(prefs, initialQuery: 'from_a_post');
      await store.search();
      store.type('typed ');
      await store.search();
      await store.run('from history');
      await store.refine('related');
      expect(readPluginSearchHistory(prefs, optionPluginBooruSearchHistory), [
        'from history related',
        'from history',
        'from_a_post typed',
        'from_a_post',
      ]);
      expect(store.state.query, 'from history related');
      expect(store.state.showsResults, isTrue);
    });

    test('backspace in an empty field drops the last chip only', () {
      final store = _store(PrefServiceCache());
      store.type('a b ');
      expect(store.removeLast(), isTrue);
      expect(store.state.tags, ['a']);
      store.type('x');
      expect(store.removeLast(), isFalse);
      expect(store.state.tags, ['a']);
    });

    test('a chip reopened for editing goes back into the field', () {
      final store = _store(PrefServiceCache());
      store.type('a b c');
      expect(store.reopen('a'), 'a');
      expect(store.state.tags, ['b', 'c']);
      expect(store.state.input, 'a');
    });

    test('closing the editor returns to the results that are loaded', () async {
      final store = _store(PrefServiceCache());
      await store.run('cat');
      store.edit();
      store.type('dog ');
      expect(store.state.showsResults, isFalse);
      expect(store.closeEditor(), isTrue);
      expect(store.state.tags, ['cat']);
      expect(store.state.showsResults, isTrue);
    });
  });

  group('saved searches', () {
    setUpAll(() async {
      sqfliteFfiInit();
      databaseFactory = databaseFactoryFfi;
      final dir = await Directory.systemTemp.createTemp('xta_booru_saved_search');
      await databaseFactory.setDatabasesPath(dir.path);
      await Repository().migrate();
    });

    test('a whole tag set is saved as one entry and removed again', () async {
      final tags = BooruTagsStore();
      await tags.add('Blue_Eyes  solo rating:g');
      expect(tags.state, contains('blue_eyes solo rating:g'));
      await tags.remove('blue_eyes solo rating:g');
      expect(tags.state, isNot(contains('blue_eyes solo rating:g')));
    });

    test('a save is not lost when a reload starts right after it', () async {
      final tags = BooruTagsStore();
      final saving = tags.add('kept_tag');
      await tags.load();
      await saving;
      await Future<void>.delayed(const Duration(milliseconds: 120));
      final rows = await (await Repository.readOnly()).query(tableBooruSubscription);
      expect(rows.map((row) => row['id']), contains('kept_tag'));
    });

    test('a saved search feeds Following as one multi-tag query', () async {
      final requested = <String>[];
      final prefs = PrefServiceCache(cache: {optionPluginBooruEngine: 'danbooru', optionPluginBooruMaxRating: 'e'});
      final client = BooruClient(
        prefs,
        httpClient: MockClient((request) async {
          requested.add(request.url.queryParameters['tags'] ?? '');
          return http.Response('[]', 200);
        }),
      );
      await client.postsForTags(['blue_eyes solo', 'night']);
      expect(requested, unorderedEquals(['blue_eyes solo', 'night']));
    });
  });
}
