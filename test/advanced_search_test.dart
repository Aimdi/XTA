import 'package:flutter_test/flutter_test.dart';
import 'package:xta/search/advanced_search_model.dart';
import 'package:xta/search/search_model.dart';

void main() {
  test('advanced search composes every supported operator', () {
    final state = AdvancedSearchState(
      allWords: 'flutter android',
      exactPhrase: 'quiet signal',
      anyWords: 'reader, timeline',
      noneWords: 'spam ads',
      hashtags: 'design #accessibility',
      fromAccounts: '@alice bob',
      toAccounts: 'carol',
      mentioningAccounts: '@dave',
      minReplies: '2',
      minLikes: '10',
      minRetweets: '3',
      since: DateTime(2026, 1, 2),
      until: DateTime(2026, 8, 31),
      onlyMedia: true,
    );

    expect(
      state.query,
      'flutter android "quiet signal" (reader OR timeline) -spam -ads '
      '(#design OR #accessibility) (from:alice OR from:bob) to:carol '
      '@dave min_replies:2 min_faves:10 min_retweets:3 '
      'since:2026-01-02 until:2026-08-31 filter:media',
    );
  });

  test('individual filters clear without disturbing the rest of the query', () {
    const state = AdvancedSearchState(allWords: 'flutter', hashtags: 'android', onlyMedia: true);

    final cleared = state.clear(AdvancedSearchFilter.hashtags);

    expect(cleared.query, 'flutter filter:media');
    expect(cleared.activeFilters, [AdvancedSearchFilter.allWords, AdvancedSearchFilter.onlyMedia]);
  });

  test('advanced Store resets the complete form', () {
    final store = AdvancedSearchStore(const AdvancedSearchState(allWords: 'flutter', onlyMedia: true));
    addTearDown(store.destroy);

    store.reset();

    expect(store.state.query, isEmpty);
    expect(store.state.activeFilters, isEmpty);
  });

  test('result state keeps structured filters while the query matches', () {
    final store = SearchViewStore(initialQuery: 'flutter');
    addTearDown(store.destroy);
    const advanced = AdvancedSearchState(allWords: 'flutter', hashtags: 'android', onlyMedia: true);

    store.applyAdvanced(advanced);
    store.commitQuery(advanced.query);

    expect(store.state.query, 'flutter #android filter:media');
    expect(store.state.advanced.activeFilters, [
      AdvancedSearchFilter.allWords,
      AdvancedSearchFilter.hashtags,
      AdvancedSearchFilter.onlyMedia,
    ]);
  });

  test('editing a structured query clears stale filter metadata', () {
    final store = SearchViewStore();
    addTearDown(store.destroy);
    store.applyAdvanced(const AdvancedSearchState(allWords: 'flutter', onlyMedia: true));

    store.invalidateAdvancedForDraft('flutter android');
    store.commitQuery('flutter android');

    expect(store.state.query, 'flutter android');
    expect(store.state.advanced.activeFilters, isEmpty);
  });

  test('one advanced filter can be removed without clearing the others', () {
    final store = SearchViewStore();
    addTearDown(store.destroy);
    store.applyAdvanced(const AdvancedSearchState(allWords: 'flutter', hashtags: 'android', onlyMedia: true));

    final advanced = store.clearFilter(AdvancedSearchFilter.hashtags);

    expect(advanced.query, 'flutter filter:media');
    expect(store.state.query, advanced.query);
    expect(store.state.advanced.activeFilters, [AdvancedSearchFilter.allWords, AdvancedSearchFilter.onlyMedia]);
  });

  test('content choices replace the preceding content operator', () {
    final store = AdvancedSearchStore(const AdvancedSearchState(allWords: 'flutter', onlyMedia: true));
    addTearDown(store.destroy);
    const choices = {
      AdvancedSearchContentFilter.photos: 'filter:images',
      AdvancedSearchContentFilter.videos: 'filter:videos',
      AdvancedSearchContentFilter.links: 'filter:links',
      AdvancedSearchContentFilter.media: 'filter:media',
      AdvancedSearchContentFilter.all: '',
    };

    for (final choice in choices.entries) {
      store.setContentFilter(choice.key);

      expect(store.state.query, 'flutter ${choice.value}'.trim());
      expect(store.state.activeFilters.length, choice.key == AdvancedSearchContentFilter.all ? 1 : 2);
    }
  });

  test('legacy media setters still add and remove only the media choice', () {
    const state = AdvancedSearchState(onlyMedia: true);
    expect(state.contentFilter, AdvancedSearchContentFilter.media);
    expect(state.onlyMedia, isTrue);
    expect(state.copyWith(onlyMedia: false).query, isEmpty);

    final photos = state.copyWith(contentFilter: AdvancedSearchContentFilter.photos);
    expect(photos.onlyMedia, isFalse);
    expect(photos.copyWith(onlyMedia: false).query, 'filter:images');
    expect(photos.copyWith(onlyMedia: true).query, 'filter:media');
    expect(photos.copyWith(allWords: 'flutter').query, 'flutter filter:images');
  });

  test('clearing any content choice retains exclusions and other fields', () {
    const filters = {
      AdvancedSearchContentFilter.media: AdvancedSearchFilter.onlyMedia,
      AdvancedSearchContentFilter.photos: AdvancedSearchFilter.photos,
      AdvancedSearchContentFilter.videos: AdvancedSearchFilter.videos,
      AdvancedSearchContentFilter.links: AdvancedSearchFilter.links,
    };
    for (final entry in filters.entries) {
      final state = AdvancedSearchState(
        allWords: 'flutter',
        fromAccounts: 'alice',
        contentFilter: entry.key,
        excludeReplies: true,
        excludeRetweets: true,
      );

      final cleared = state.clear(entry.value);

      expect(cleared.query, 'flutter from:alice -filter:replies -filter:retweets');
      expect(cleared.contentFilter, AdvancedSearchContentFilter.all);
      expect(cleared.activeFilters, [
        AdvancedSearchFilter.allWords,
        AdvancedSearchFilter.fromAccounts,
        AdvancedSearchFilter.excludeReplies,
        AdvancedSearchFilter.excludeRetweets,
      ]);
      expect(state.valueOf(entry.value), isEmpty);
    }
  });

  test('clearing an inactive content filter retains the active choice', () {
    const state = AdvancedSearchState(contentFilter: AdvancedSearchContentFilter.links);
    for (final filter in [AdvancedSearchFilter.onlyMedia, AdvancedSearchFilter.photos, AdvancedSearchFilter.videos]) {
      expect(state.clear(filter).query, 'filter:links');
    }
  });

  test('reply and repost exclusions toggle and clear independently', () {
    final store = AdvancedSearchStore(
      const AdvancedSearchState(fromAccounts: 'alice', contentFilter: AdvancedSearchContentFilter.photos),
    );
    addTearDown(store.destroy);
    store.setExcludeReplies(true);
    store.setExcludeRetweets(true);
    expect(store.state.query, 'from:alice filter:images -filter:replies -filter:retweets');

    store.clear(AdvancedSearchFilter.excludeReplies);
    expect(store.state.query, 'from:alice filter:images -filter:retweets');
    store.setExcludeReplies(true);
    store.clear(AdvancedSearchFilter.excludeRetweets);
    expect(store.state.query, 'from:alice filter:images -filter:replies');

    store.reset();
    expect(store.state.query, isEmpty);
    expect(store.state.activeFilters, isEmpty);
    expect(store.state.contentFilter, AdvancedSearchContentFilter.all);
    expect(store.state.excludeReplies, isFalse);
    expect(store.state.excludeRetweets, isFalse);
  });

  test('result filter removal preserves structured content and exclusions', () {
    final store = SearchViewStore();
    addTearDown(store.destroy);
    store.applyAdvanced(
      const AdvancedSearchState(
        allWords: 'flutter',
        contentFilter: AdvancedSearchContentFilter.videos,
        excludeReplies: true,
        excludeRetweets: true,
      ),
    );

    final advanced = store.clearFilter(AdvancedSearchFilter.excludeReplies);

    expect(advanced.query, 'flutter filter:videos -filter:retweets');
    expect(store.state.query, advanced.query);
    expect(store.state.advanced.activeFilters, [
      AdvancedSearchFilter.allWords,
      AdvancedSearchFilter.videos,
      AdvancedSearchFilter.excludeRetweets,
    ]);
  });

  test('reopening history restores filters without conflicting content types', () {
    const query = 'cats from:alice filter:images -filter:replies -filter:retweets';
    final state = AdvancedSearchState.fromQuery(query);

    expect(state.allWords, 'cats from:alice');
    expect(state.contentFilter, AdvancedSearchContentFilter.photos);
    expect(state.excludeReplies, isTrue);
    expect(state.excludeRetweets, isTrue);
    expect(state.query, query);
    expect(
      state.copyWith(contentFilter: AdvancedSearchContentFilter.videos).query,
      'cats from:alice filter:videos -filter:replies -filter:retweets',
    );
    expect(state.clear(AdvancedSearchFilter.photos).query, 'cats from:alice -filter:replies -filter:retweets');
  });

  test('history restoration preserves literals, groups and unsupported text', () {
    const queries = [
      'cats "filter:images"',
      '(cats filter:images)',
      'cats (filter:images OR filter:videos)',
      'cats filter:images lang:en',
      'cats "filter:images',
      'cats (filter:images',
      'cats ) filter:images',
      'cats OR filter:images',
      'cats AND filter:videos',
      r'cats \ filter:images',
    ];
    for (final query in queries) {
      final state = AdvancedSearchState.fromQuery(query);

      expect(state.allWords, query);
      expect(state.query, query);
      expect(state.contentFilter, AdvancedSearchContentFilter.all);
    }
  });

  test('quoted and grouped prefix stays intact when a trailing filter restores', () {
    const prefix = '"filter:images" (cats OR dogs)';
    final state = AdvancedSearchState.fromQuery('$prefix filter:links');

    expect(state.allWords, prefix);
    expect(state.contentFilter, AdvancedSearchContentFilter.links);
    expect(state.clear(AdvancedSearchFilter.links).query, prefix);
  });
}
