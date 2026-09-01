import 'package:flutter_test/flutter_test.dart';
import 'package:xta/search/advanced_search_model.dart';

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
    const state = AdvancedSearchState(
      allWords: 'flutter',
      hashtags: 'android',
      onlyMedia: true,
    );

    final cleared = state.clear(AdvancedSearchFilter.hashtags);

    expect(cleared.query, 'flutter filter:media');
    expect(cleared.activeFilters, [
      AdvancedSearchFilter.allWords,
      AdvancedSearchFilter.onlyMedia,
    ]);
  });

  test('advanced Store resets the complete form', () {
    final store = AdvancedSearchStore(
      const AdvancedSearchState(allWords: 'flutter', onlyMedia: true),
    );
    addTearDown(store.destroy);

    store.reset();

    expect(store.state.query, isEmpty);
    expect(store.state.activeFilters, isEmpty);
  });
}
