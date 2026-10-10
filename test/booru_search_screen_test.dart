import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_pager.dart';
import 'package:xta/plugins/booru/booru_post_screen.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';
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
      const BooruTagSuggestion(name: 'blue_eyes', postCount: 1203411, category: BooruTagCategory.general),
      const BooruTagSuggestion(name: 'blue_archive', postCount: 190001, category: BooruTagCategory.copyright),
    ].where((s) => s.name.startsWith(prefix)).toList();
  }

  @override
  Future<BooruPostPage> search(String query, {int page = 1, int limit = BooruClient.defaultPageSize}) async {
    queries.add(query);
    return BooruPostPage(posts: [_post('1'), _post('2')], page: page, hasMore: false);
  }
}

BooruPost _post(String id) => BooruPost(
  id: id,
  host: 'https://danbooru.donmai.us',
  engine: 'danbooru',
  tags: const ['solo', 'smile', 'hat_red'],
  rating: BooruRating.general,
  score: 3,
  width: 100,
  height: 140,
  previewUrl: null,
  sampleUrl: null,
  fileUrl: null,
  fileExt: 'jpg',
  source: null,
  createdAt: null,
);

/// Saved searches in memory; the database-backed store is covered in
/// booru_search_store_test.dart.
class _SavedSearches extends BooruTagsStore {
  _SavedSearches(List<String> initial) {
    update(initial);
  }

  @override
  Future<void> load() async {}

  @override
  Future<void> add(String tag) async {
    final query = normaliseBooruQuery(tag);
    if (query != null && !state.contains(query)) update([...state, query]);
  }

  @override
  Future<void> remove(String tag) async {
    update([
      for (final query in state)
        if (query != normaliseBooruQuery(tag)) query,
    ]);
  }
}

class _Harness {
  final PrefServiceCache prefs;
  final _Client client;
  final _SavedSearches saved;

  _Harness(this.prefs, this.client, this.saved);

  List<String> get history => readPluginSearchHistory(prefs, optionPluginBooruSearchHistory);
}

Future<_Harness> _open(
  WidgetTester tester, {
  PrefServiceCache? prefs,
  String? initialQuery,
  List<String> saved = const [],
}) async {
  final service = prefs ?? PrefServiceCache();
  final harness = _Harness(service, _Client(service), _SavedSearches(saved));
  await tester.pumpWidget(
    PrefService(
      service: service,
      child: MultiProvider(
        providers: [
          Provider<BooruClient>.value(value: harness.client),
          Provider<BooruTagsStore>.value(value: harness.saved),
          Provider<BooruMuteStore>.value(value: BooruMuteStore(service)),
        ],
        child: MaterialApp(
          key: UniqueKey(),
          localizationsDelegates: const [
            L10n.delegate,
            GlobalMaterialLocalizations.delegate,
            GlobalWidgetsLocalizations.delegate,
          ],
          home: Builder(
            builder: (context) => Scaffold(
              body: Center(
                child: TextButton(
                  onPressed: () => Navigator.push(
                    context,
                    MaterialPageRoute(builder: (_) => BooruSearchScreen(initialQuery: initialQuery)),
                  ),
                  child: const Text('open search'),
                ),
              ),
            ),
          ),
        ),
      ),
    ),
  );
  await tester.tap(find.text('open search'));
  await _settle(tester);
  return harness;
}

Future<void> _settle(WidgetTester tester) async {
  await tester.pump(const Duration(milliseconds: 300));
  await tester.pumpAndSettle();
}

Finder get _field => find.byType(TextField);

Finder _chip(String tag) => find.byKey(ValueKey('booru-tag-$tag'));

Finder _historyEntry(String query) => find.byKey(ValueKey('booru-history-$query'));

Finder _savedEntry(String query) => find.byKey(ValueKey('booru-saved-$query'));

Future<void> _type(WidgetTester tester, String text) async {
  await tester.enterText(_field, text);
  await _settle(tester);
}

Future<void> _submit(WidgetTester tester) async {
  await tester.testTextInput.receiveAction(TextInputAction.search);
  await _settle(tester);
}

Future<void> _focusField(WidgetTester tester) async {
  await tester.tap(_field);
  await _settle(tester);
}

void main() {
  testWidgets('a search shows up in recent searches as soon as the field is focused again', (tester) async {
    final harness = await _open(tester);
    await _type(tester, 'cat_ears');
    await _submit(tester);
    expect(harness.client.queries, ['cat_ears']);

    await _focusField(tester);
    expect(_historyEntry('cat_ears'), findsOneWidget);
  });

  testWidgets('saving a search keeps the whole tag set', (tester) async {
    final harness = await _open(tester);
    await _type(tester, 'blue_eyes solo rating:g');
    await tester.tap(find.byTooltip('Save search'));
    await _settle(tester);
    expect(harness.saved.state, ['blue_eyes solo rating:g']);

    await tester.tap(find.byTooltip('Remove from saved searches'));
    await _settle(tester);
    expect(harness.saved.state, isEmpty);
  });

  group('tags as chips', () {
    testWidgets('space finishes a tag, ✕ removes it and backspace in an empty field drops the last', (tester) async {
      await _open(tester);
      await _type(tester, 'cat_ears solo ');
      expect(_chip('cat_ears'), findsOneWidget);
      expect(_chip('solo'), findsOneWidget);
      expect(tester.widget<TextField>(_field).controller!.text, isEmpty);

      await tester.tap(find.descendant(of: _chip('cat_ears'), matching: find.byTooltip('Delete')));
      await _settle(tester);
      expect(_chip('cat_ears'), findsNothing);

      await tester.sendKeyEvent(LogicalKeyboardKey.backspace);
      await _settle(tester);
      expect(_chip('solo'), findsNothing);
    });

    testWidgets('a picked suggestion is appended to the tags already there', (tester) async {
      final harness = await _open(tester);
      await _type(tester, 'cat_ears ');
      await _type(tester, 'blu');
      expect(find.text('1.2M'), findsOneWidget);

      await tester.tap(find.text('blue_archive'));
      await _settle(tester);
      expect(_chip('cat_ears'), findsOneWidget);
      expect(_chip('blue_archive'), findsOneWidget);
      expect(tester.widget<TextField>(_field).controller!.text, isEmpty);

      await _submit(tester);
      expect(harness.client.queries.last, 'cat_ears blue_archive');
    });

    testWidgets('a minus typed before a tag survives the suggestion', (tester) async {
      final harness = await _open(tester);
      await _type(tester, 'cat_ears ');
      await _type(tester, '-blu');
      expect(harness.client.prefixes.last, 'blu');
      await tester.tap(find.text('blue_eyes'));
      await _settle(tester);
      expect(_chip('-blue_eyes'), findsOneWidget);

      await _submit(tester);
      expect(harness.client.queries.last, 'cat_ears -blue_eyes');
      expect(harness.history.first, 'cat_ears -blue_eyes');
    });

    testWidgets('the exclude button puts a minus in front of the tag being typed', (tester) async {
      await _open(tester);
      await _type(tester, 'hat');
      await tester.tap(find.text('Exclude'));
      await _settle(tester);
      expect(tester.widget<TextField>(_field).controller!.text, '-hat');
    });

    testWidgets('the rating shortcut adds the host’s metatag and takes it off again', (tester) async {
      await _open(tester);
      await tester.tap(find.widgetWithText(FilterChip, 'rating:g'));
      await _settle(tester);
      expect(_chip('rating:g'), findsOneWidget);

      await tester.tap(find.widgetWithText(FilterChip, 'rating:g'));
      await _settle(tester);
      expect(_chip('rating:g'), findsNothing);
    });

    testWidgets('tapping a chip moves it back into the field', (tester) async {
      await _open(tester);
      await _type(tester, 'cat_ears solo ');
      await tester.tap(_chip('cat_ears'));
      await _settle(tester);
      expect(_chip('cat_ears'), findsNothing);
      expect(tester.widget<TextField>(_field).controller!.text, 'cat_ears');
    });

    testWidgets('the whole query can be edited as text', (tester) async {
      final harness = await _open(tester);
      await _type(tester, 'cat_ears solo ');
      await tester.ensureVisible(find.text('Edit as text'));
      await tester.tap(find.text('Edit as text'));
      await _settle(tester);
      await tester.enterText(
        find.descendant(of: find.byType(AlertDialog), matching: find.byType(TextField)),
        'dog -hat',
      );
      await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.text('Search')));
      await _settle(tester);
      expect(harness.client.queries.last, 'dog -hat');
      expect(_chip('-hat'), findsOneWidget);
    });
  });

  group('history', () {
    testWidgets('every way into a search is remembered', (tester) async {
      final harness = await _open(tester, saved: ['night']);
      await _type(tester, 'typed');
      await _submit(tester);

      await _focusField(tester);
      await _type(tester, 'button');
      await tester.tap(find.byTooltip('Search'));
      await _settle(tester);

      await _focusField(tester);
      await tester.tap(_savedEntry('night'));
      await _settle(tester);

      await tester.tap(find.widgetWithText(ActionChip, 'smile'));
      await _settle(tester);

      expect(harness.history, ['night smile', 'night', 'typed button', 'typed']);
    });

    testWidgets('a search opened from a post tag is remembered', (tester) async {
      final harness = await _open(tester, initialQuery: 'from_post');
      expect(harness.client.queries, ['from_post']);
      expect(harness.history, ['from_post']);
    });

    testWidgets('an entry re-runs with one tap and moves to the top', (tester) async {
      final prefs = PrefServiceCache(cache: {optionPluginBooruSearchHistory: '["newer","older one"]'});
      final harness = await _open(tester, prefs: prefs);
      await tester.tap(_historyEntry('older one'));
      await _settle(tester);
      expect(harness.client.queries, ['older one']);
      expect(harness.history, ['older one', 'newer']);
    });

    testWidgets('one entry can be removed with ✕ or a swipe, and all can be cleared', (tester) async {
      final prefs = PrefServiceCache(cache: {optionPluginBooruSearchHistory: '["a","b","c"]'});
      final harness = await _open(tester, prefs: prefs);
      await tester.tap(find.descendant(of: _historyEntry('a'), matching: find.byTooltip('Delete')));
      await _settle(tester);
      expect(_historyEntry('a'), findsNothing);
      expect(harness.history, ['b', 'c']);

      await tester.drag(_historyEntry('b'), const Offset(-600, 0));
      await _settle(tester);
      expect(_historyEntry('b'), findsNothing);
      expect(harness.history, ['c']);

      await tester.tap(find.byTooltip('Clear search history'));
      await _settle(tester);
      expect(harness.history, isEmpty);
      expect(find.text('No recent searches yet'), findsOneWidget);
    });

    testWidgets('history outlives the screen and a restart', (tester) async {
      final prefs = PrefServiceCache();
      await _open(tester, prefs: prefs);
      await _type(tester, 'kept');
      await _submit(tester);
      await tester.pageBack();
      await _settle(tester);

      await _open(tester, prefs: prefs);
      expect(_historyEntry('kept'), findsOneWidget);

      await _open(tester, prefs: PrefServiceCache(cache: Map.of(prefs.toMap())));
      expect(_historyEntry('kept'), findsOneWidget);
    });
  });

  group('saved searches and results', () {
    testWidgets('saved searches sit above history and run with one tap', (tester) async {
      final prefs = PrefServiceCache(cache: {optionPluginBooruSearchHistory: '["older"]'});
      final harness = await _open(tester, prefs: prefs, saved: ['blue_eyes solo']);
      final savedTop = tester.getTopLeft(_savedEntry('blue_eyes solo')).dy;
      expect(savedTop, lessThan(tester.getTopLeft(_historyEntry('older')).dy));

      await tester.tap(_savedEntry('blue_eyes solo'));
      await _settle(tester);
      expect(harness.client.queries, ['blue_eyes solo']);
      expect(find.byTooltip('Remove from saved searches'), findsOneWidget);
    });

    testWidgets('back closes the field over the results before leaving', (tester) async {
      final harness = await _open(tester, initialQuery: 'cat');
      await _focusField(tester);
      await _type(tester, 'dog ');
      expect(find.byType(BooruSearchScreen), findsOneWidget);

      await tester.pageBack();
      await _settle(tester);
      expect(find.byType(BooruSearchScreen), findsOneWidget);
      expect(_chip('cat'), findsOneWidget);
      expect(_chip('dog'), findsNothing);
      expect(harness.client.queries, ['cat']);

      await tester.pageBack();
      await _settle(tester);
      expect(find.byType(BooruSearchScreen), findsNothing);
    });

    testWidgets('related tags refine the search; a long press excludes', (tester) async {
      final harness = await _open(tester, initialQuery: 'cat');
      await tester.tap(find.widgetWithText(ActionChip, 'solo'));
      await _settle(tester);
      expect(harness.client.queries.last, 'cat solo');

      await tester.longPress(find.widgetWithText(ActionChip, 'smile'));
      await _settle(tester);
      expect(harness.client.queries.last, 'cat solo -smile');
    });

    testWidgets('a tag on a post can be added to the search it came from', (tester) async {
      final harness = await _open(tester, initialQuery: 'cat');
      await _openTagActions(tester, 'hat_red');
      await tester.tap(find.byKey(const ValueKey('booru-tag-action-add')));
      await _settle(tester);
      expect(find.byType(BooruPostPager), findsNothing);
      expect(harness.client.queries.last, 'cat hat_red');
      expect(harness.history.first, 'cat hat_red');
    });

    testWidgets('a tag on a post can be excluded from the search', (tester) async {
      final harness = await _open(tester, initialQuery: 'cat');
      await _openTagActions(tester, 'hat_red');
      await tester.tap(find.byKey(const ValueKey('booru-tag-action-exclude')));
      await _settle(tester);
      expect(harness.client.queries.last, 'cat -hat_red');
    });

    testWidgets('a tag can be followed and hidden from its sheet', (tester) async {
      final harness = await _open(tester, initialQuery: 'cat');
      await _openTagActions(tester, 'smile');
      await tester.tap(find.byKey(const ValueKey('booru-tag-action-follow')));
      await _settle(tester);
      expect(harness.saved.state, contains('smile'));

      await tester.tap(find.byKey(const ValueKey('booru-post-tag-smile')));
      await _settle(tester);
      expect(find.text('Unfollow tag'), findsOneWidget);
      await tester.tap(find.byKey(const ValueKey('booru-tag-action-hide')));
      await _settle(tester);
      expect(readMuted(harness.prefs), contains('smile'));
    });

    testWidgets('the viewer swipes on to the next post', (tester) async {
      await _open(tester, initialQuery: 'cat');
      await tester.tap(find.byType(BooruPostTile).first);
      await _settle(tester);
      expect(find.text('#1'), findsOneWidget);
      await tester.fling(find.byType(PageView), const Offset(-600, 0), 1000);
      await _settle(tester);
      expect(find.text('#2'), findsOneWidget);
    });
  });
}

/// Opens the first result and the actions of its [tag].
Future<void> _openTagActions(WidgetTester tester, String tag) async {
  await tester.tap(find.byType(BooruPostTile).first);
  await _settle(tester);
  expect(find.byType(BooruPostPager), findsOneWidget);
  final chip = find.byKey(ValueKey('booru-post-tag-$tag'));
  await tester.scrollUntilVisible(
    chip,
    300,
    scrollable: find.descendant(of: find.byType(BooruPostDetails), matching: find.byType(Scrollable)).first,
  );
  await tester.tap(chip);
  await _settle(tester);
}

List<String> readMuted(PrefServiceCache prefs) =>
    (jsonDecode(prefs.get<String>(optionPluginBooruMutedTags) ?? '[]') as List).cast<String>();
