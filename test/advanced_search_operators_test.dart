import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/advanced_search_model.dart';
import 'package:xta/search/search_chrome.dart';
import 'package:xta/search/search_languages.dart';
import 'package:xta/search/search_model.dart';

/// What the chips and the filters screen show for [state].
Map<String, Object?> _shown(AdvancedSearchState state) => {
  for (final filter in state.activeFilters) filter.name: state.valueOf(filter),
  'content': state.contentFilter,
  'links': state.links,
  'replies': state.replies,
};

void main() {
  test('each new filter writes one operator, and its default writes none', () {
    const base = AdvancedSearchState(allWords: 'cats');
    final queries = {
      base.copyWith(language: 'fr'): 'cats lang:fr',
      base.copyWith(replies: AdvancedSearchInclusion.include, links: AdvancedSearchInclusion.include): 'cats',
      base.copyWith(replies: AdvancedSearchInclusion.only): 'cats filter:replies',
      base.copyWith(replies: AdvancedSearchInclusion.exclude): 'cats -filter:replies',
      base.copyWith(links: AdvancedSearchInclusion.only): 'cats filter:links',
      base.copyWith(links: AdvancedSearchInclusion.exclude): 'cats -filter:links',
      base.copyWith(includeReposts: true): 'cats include:nativeretweets',
    };

    queries.forEach((state, query) => expect(state.query, query));
  });

  test('every operator has a fixed place, so the same filters always send the same query', () {
    final state = AdvancedSearchState(
      allWords: 'cats',
      exactPhrase: 'quiet signal',
      anyWords: 'reader timeline',
      noneWords: 'spam',
      hashtags: 'flutter',
      language: 'fr',
      fromAccounts: 'alice',
      toAccounts: 'bob',
      mentioningAccounts: 'carol',
      minReplies: '2',
      minLikes: '10',
      minRetweets: '3',
      since: DateTime(2026, 1, 2),
      until: DateTime(2026, 8, 31),
      contentFilter: AdvancedSearchContentFilter.photos,
      links: AdvancedSearchInclusion.exclude,
      replies: AdvancedSearchInclusion.only,
      includeReposts: true,
    );

    expect(
      state.query,
      'cats "quiet signal" (reader OR timeline) -spam #flutter lang:fr from:alice to:bob @carol '
      'min_replies:2 min_faves:10 min_retweets:3 since:2026-01-02 until:2026-08-31 '
      'filter:images -filter:links filter:replies include:nativeretweets',
    );
  });

  test('clearing one of the new filters keeps the others', () {
    final state = AdvancedSearchState.fromQuery('cats lang:fr -filter:links filter:replies include:nativeretweets');

    expect(
      state.clear(AdvancedSearchFilter.language).query,
      'cats -filter:links filter:replies include:nativeretweets',
    );
    expect(state.clear(AdvancedSearchFilter.links).query, 'cats lang:fr filter:replies include:nativeretweets');
    expect(state.clear(AdvancedSearchFilter.replies).query, 'cats lang:fr -filter:links include:nativeretweets');
    expect(state.clear(AdvancedSearchFilter.includeReposts).query, 'cats lang:fr -filter:links filter:replies');
  });

  test('including reposts and hiding retweets turn each other off', () {
    final store = AdvancedSearchStore(const AdvancedSearchState(allWords: 'cats'));
    addTearDown(store.destroy);

    store.setIncludeReposts(true);
    expect(store.state.query, 'cats include:nativeretweets');
    store.setExcludeRetweets(true);
    expect(store.state.query, 'cats -filter:retweets');
    store.setIncludeReposts(true);
    expect(store.state.query, 'cats include:nativeretweets');
    store.setIncludeReposts(false);
    expect(store.state.query, 'cats');
  });

  test('every operator the form can show is read wherever it stands in the query', () {
    final read = AdvancedSearchState.fromQuery(
      'min_retweets:3 lang:fr since:2026-01-02 cats to:bob filter:replies until:2026-08-31 dogs '
      '-filter:links min_replies:2 include:nativeretweets min_faves:10 from:alice filter:media',
    );

    expect(read.allWords, 'cats dogs');
    expect(read.language, 'fr');
    expect(read.fromAccounts, 'alice');
    expect(read.toAccounts, 'bob');
    expect([read.minReplies, read.minLikes, read.minRetweets], ['2', '10', '3']);
    expect(read.since, DateTime(2026, 1, 2));
    expect(read.until, DateTime(2026, 8, 31));
    expect(read.contentFilter, AdvancedSearchContentFilter.media);
    expect(read.links, AdvancedSearchInclusion.exclude);
    expect(read.replies, AdvancedSearchInclusion.only);
    expect(read.includeReposts, isTrue);
    expect(
      read.query,
      'cats dogs lang:fr from:alice to:bob min_replies:2 min_faves:10 min_retweets:3 since:2026-01-02 '
      'until:2026-08-31 filter:media -filter:links filter:replies include:nativeretweets',
    );
  });

  test('a query the form wrote reads back to the same filters and the same text', () {
    final generated = [
      const AdvancedSearchState(allWords: 'cats', language: 'fr'),
      const AdvancedSearchState(fromAccounts: 'alice bob', toAccounts: 'carol', minLikes: '10', minRetweets: '3'),
      AdvancedSearchState(
        allWords: 'cats OR dogs',
        minReplies: '2',
        since: DateTime(2026, 1, 2),
        until: DateTime(2026, 8, 31),
      ),
      const AdvancedSearchState(
        contentFilter: AdvancedSearchContentFilter.photos,
        links: AdvancedSearchInclusion.exclude,
        replies: AdvancedSearchInclusion.only,
      ),
      const AdvancedSearchState(
        allWords: '"quiet signal" #flutter',
        onlyMedia: true,
        links: AdvancedSearchInclusion.only,
        replies: AdvancedSearchInclusion.exclude,
        includeReposts: true,
      ),
      const AdvancedSearchState(
        allWords: 'news',
        language: 'zh-cn',
        contentFilter: AdvancedSearchContentFilter.videos,
        excludeRetweets: true,
      ),
    ];

    for (final state in generated) {
      final read = AdvancedSearchState.fromQuery(state.query);

      expect(_shown(read), _shown(state), reason: state.query);
      expect(read.query, state.query);
    }
  });

  test('a typed query in the form order is rebuilt unchanged', () {
    const typed = [
      'cats lang:fr',
      'from:alice filter:images -filter:links',
      'cats (from:alice OR from:bob) min_faves:10 since:2026-01-02 filter:replies',
      'news lang:en filter:videos -filter:replies include:nativeretweets',
      '"exact phrase" -spam #tag @dart to:bob -filter:retweets',
    ];

    for (final query in typed) {
      final read = AdvancedSearchState.fromQuery(query);

      expect(read.hasOperators, isTrue, reason: query);
      expect(read.query, query);
    }
  });

  test('a typed query in another order is rebuilt once into the form order, then stays', () {
    final read = AdvancedSearchState.fromQuery('filter:images lang:fr cats  from:alice');

    expect(read.allWords, 'cats');
    expect(read.query, 'cats lang:fr from:alice filter:images');
    expect(AdvancedSearchState.fromQuery(read.query).query, read.query);
  });

  test('operators the form cannot show stay in the words as typed', () {
    const kept = [
      '-lang:fr cats',
      '-from:alice',
      'lang:fr OR lang:en',
      'cats from:alice OR from:bob',
      'NOT filter:replies',
      'lang:fr lang:en',
      'filter:images filter:videos',
      'filter:replies -filter:replies',
      'include:nativeretweets -filter:retweets',
      'min_faves:0',
      'min_faves:05',
      'since:2026-02-30',
      'since:2026-01-02_10:00:00_UTC',
      'lang:FR',
      'from:@alice',
      '"lang:fr" cats',
      '(lang:fr OR lang:en)',
      '(from:alice OR to:bob)',
      '(from:alice  OR from:bob)',
      '#flutter @dart "exact phrase" -spam',
      'cats "from:alice',
      'cats  dogs',
    ];

    for (final query in kept) {
      final read = AdvancedSearchState.fromQuery(query);

      expect(read.allWords, query, reason: query);
      expect(read.hasOperators, isFalse, reason: query);
      expect(read.query, query);
    }
  });

  test('a typed query with operators shows them as filters and keeps its text', () {
    final store = SearchViewStore();
    addTearDown(store.destroy);

    store.commitQuery('lang:fr cats');
    store.invalidateAdvancedForDraft('lang:fr cats');

    expect(store.state.query, 'lang:fr cats');
    expect(store.state.advanced.activeFilters, [AdvancedSearchFilter.allWords, AdvancedSearchFilter.language]);
    final advanced = store.clearFilter(AdvancedSearchFilter.language);
    expect(advanced.query, 'cats');
    expect(store.state.query, 'cats');
  });

  test('editing a typed query drops its filters until it is committed again', () {
    final store = SearchViewStore();
    addTearDown(store.destroy);
    store.commitQuery('cats lang:fr');

    store.invalidateAdvancedForDraft('cats lang:f');
    expect(store.state.advanced.activeFilters, isEmpty);
    store.commitQuery('cats lang:de');
    expect(store.state.advanced.language, 'de');
  });

  test('an opened saved search shows its filters, and plain words show none', () {
    final saved = SearchViewStore(initialQuery: ' from:alice filter:images ');
    final words = SearchViewStore(initialQuery: '#flutter dart');
    addTearDown(saved.destroy);
    addTearDown(words.destroy);

    expect(saved.state.query, 'from:alice filter:images');
    expect(saved.state.advanced.activeFilters, [AdvancedSearchFilter.fromAccounts, AdvancedSearchFilter.photos]);
    expect(words.state.advanced.activeFilters, isEmpty);
    words.commitQuery('cats OR from:alice');
    expect(words.state.advanced.activeFilters, isEmpty);
  });

  test('the language picker names each language in its own language, in the order of those names', () {
    String byName(String code) => searchLanguageName(code).toLowerCase();
    final choices = searchLanguageChoices('');

    expect(searchLanguageCodes.where((code) => searchLanguageName(code) == code), isEmpty);
    expect(searchLanguageName('fr'), 'français');
    expect(searchLanguageName('no'), 'norsk');
    expect(choices.toSet(), searchLanguageCodes.toSet());
    expect(choices.map(byName).toList(), [...choices.map(byName)]..sort());
    expect(searchLanguageChoices('qme'), contains('qme'));
    expect(searchLanguageName('qme'), 'qme');
  });

  testWidgets('the chips name the filters typed into the query', (tester) async {
    final state = AdvancedSearchState.fromQuery(
      'cats lang:fr filter:replies -filter:links include:nativeretweets from:alice',
    );
    late List<String> labels;
    await tester.pumpWidget(
      MaterialApp(
        localizationsDelegates: const [
          L10n.delegate,
          GlobalMaterialLocalizations.delegate,
          GlobalWidgetsLocalizations.delegate,
          GlobalCupertinoLocalizations.delegate,
        ],
        supportedLocales: L10n.delegate.supportedLocales,
        locale: const Locale('en'),
        home: Builder(
          builder: (context) {
            labels = [for (final filter in state.activeFilters) advancedFilterChipLabel(context, state, filter)];
            return const SizedBox.shrink();
          },
        ),
      ),
    );

    expect(labels, [
      'All of these words: cats',
      'Language: français',
      'From these accounts: alice',
      'Hide links',
      'Only replies',
      'Include reposts',
    ]);
  });
}
