import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_search_filters.dart';

import 'support/pixiv_reader_harness.dart';

({String? start, String? end}) _dates(PixivSearchFilter filter, DateTime now) {
  final query = pixivSearchQuery(filter, 'miku', now: now);
  return (start: query['start_date'], end: query['end_date']);
}

PixivSearchFilter _preset(PixivDatePreset preset) => const PixivSearchFilter().withDates(preset);

void main() {
  group('posting-date presets', () {
    test('past day and week step back across a month edge', () {
      final now = DateTime(2026, 3, 1, 23, 59);
      expect(_dates(_preset(PixivDatePreset.day), now), (start: '2026-02-28', end: '2026-03-01'));
      expect(_dates(_preset(PixivDatePreset.week), now), (start: '2026-02-22', end: '2026-03-01'));
    });

    test('a month back from the 31st lands on the shorter month\'s last day', () {
      expect(_dates(_preset(PixivDatePreset.month), DateTime(2026, 3, 31)), (start: '2026-02-28', end: '2026-03-31'));
      expect(_dates(_preset(PixivDatePreset.month), DateTime(2024, 3, 31)), (start: '2024-02-29', end: '2024-03-31'));
    });

    test('month, six months and a year cross the year edge', () {
      final january = DateTime(2026, 1, 5);
      expect(_dates(_preset(PixivDatePreset.month), january), (start: '2025-12-05', end: '2026-01-05'));
      expect(_dates(_preset(PixivDatePreset.halfYear), january), (start: '2025-07-05', end: '2026-01-05'));
      expect(_dates(_preset(PixivDatePreset.year), DateTime(2024, 2, 29)), (start: '2023-02-28', end: '2024-02-29'));
      expect(_dates(_preset(PixivDatePreset.halfYear), DateTime(2026, 8, 31)), (
        start: '2026-02-28',
        end: '2026-08-31',
      ));
    });

    test('any time sends no dates; a custom range sends its own, padded', () {
      expect(_dates(const PixivSearchFilter(), DateTime(2026, 5, 5)), (start: null, end: null));
      final custom = const PixivSearchFilter().withDates(
        PixivDatePreset.custom,
        custom: PixivDateRange(DateTime(2008, 1, 2, 13), DateTime(2009, 11, 30)),
      );
      expect(_dates(custom, DateTime(2026, 5, 5)), (start: '2008-01-02', end: '2009-11-30'));
    });

    test('custom without dates means any time', () {
      expect(const PixivSearchFilter().withDates(PixivDatePreset.custom).datePreset, PixivDatePreset.any);
    });
  });

  group('the query sent to Pixiv', () {
    final now = DateTime(2026, 10, 10);

    test('carries target, sort and the fixed parameters', () {
      final query = pixivSearchQuery(
        const PixivSearchFilter(target: PixivSearchTarget.exactTags, sort: PixivSearchSort.oldest),
        ' 初音ミク ',
        now: now,
      );
      expect(query, {
        'word': '初音ミク',
        'search_target': 'exact_match_for_tags',
        'sort': 'date_asc',
        'search_ai_type': '0',
        'merge_plain_keyword_results': 'true',
        'filter': 'for_android',
      });
    });

    test('the users入り threshold is appended to the sent word only', () {
      final filter = const PixivSearchFilter().withUsersIri(1000);
      expect(pixivSearchQuery(filter, 'miku rin', now: now)['word'], 'miku rin 1000users入り');
      expect(pixivSearchWord('miku', null), 'miku');
      expect(filter.withUsersIri(null).usersIri, isNull);
    });

    test('Premium bookmark brackets map to min and max', () {
      final bounded = pixivSearchQuery(
        const PixivSearchFilter().withBookmarks((min: 1000, max: 4999)),
        'miku',
        now: now,
      );
      expect((bounded['bookmark_num_min'], bounded['bookmark_num_max']), ('1000', '4999'));
      final open = pixivSearchQuery(
        const PixivSearchFilter().withBookmarks(pixivBookmarkRanges.first),
        'miku',
        now: now,
      );
      expect((open['bookmark_num_min'], open.containsKey('bookmark_num_max')), ('10000', false));
    });

    test('the AI switch sends search_ai_type 1 to hide and 0 to include', () {
      expect(pixivSearchQuery(const PixivSearchFilter(hideAi: true), 'x', now: now)['search_ai_type'], '1');
      expect(pixivSearchQuery(const PixivSearchFilter(), 'x', now: now)['search_ai_type'], '0');
    });

    test('the popular preview asks for translated tags too', () {
      expect(pixivPopularPreviewQuery(PixivSearchTarget.titleCaption, ' miku '), {
        'word': 'miku',
        'search_target': 'title_and_caption',
        'merge_plain_keyword_results': 'true',
        'include_translated_tag_results': 'true',
        'filter': 'for_android',
      });
    });
  });

  group('Premium', () {
    test('oldest and the audience sorts are offered only to Premium', () {
      expect(pixivSearchSorts(isPremium: false), [PixivSearchSort.newest, PixivSearchSort.popular]);
      expect(pixivSearchSorts(isPremium: true), PixivSearchSort.values);
    });

    test('an account without Premium drops a Premium order and the bookmark bracket', () {
      final filter = const PixivSearchFilter(
        sort: PixivSearchSort.popularFemale,
        hideAi: true,
      ).withBookmarks((min: 50, max: 99)).withUsersIri(500);
      final free = filter.forAccount(isPremium: false);
      expect((free.sort, free.bookmarks, free.usersIri, free.hideAi), (PixivSearchSort.newest, null, 500, true));
      expect(filter.forAccount(isPremium: true), filter);
      expect(
        const PixivSearchFilter(sort: PixivSearchSort.popular).forAccount(isPremium: false).sort,
        PixivSearchSort.popular,
      );
    });
  });

  group('remembering', () {
    test('a filter survives a JSON round trip', () {
      final filter =
          const PixivSearchFilter(
                target: PixivSearchTarget.titleCaption,
                sort: PixivSearchSort.popularMale,
                hideAi: true,
                ugoira: PixivUgoiraFilter.none,
              )
              .withDates(PixivDatePreset.custom, custom: PixivDateRange(DateTime(2020), DateTime(2021, 6, 1)))
              .withUsersIri(250)
              .withBookmarks((min: 10000, max: null));
      expect(PixivSearchFilter.fromJson(jsonDecode(jsonEncode(filter.toJson()))), filter);
    });

    test('unknown or reshaped fields keep the fallback', () {
      const fallback = PixivSearchFilter(hideAi: true);
      final read = PixivSearchFilter.fromJson({
        'target': 'everywhere',
        'sort': 42,
        'date': 'week',
        'users_iri': '1000',
        'hide_ai': 'yes',
        'ugoira': 'only',
      }, fallback: fallback);
      expect(read.target, PixivSearchTarget.partialTags);
      expect(read.sort, PixivSearchSort.newest);
      expect(read.datePreset, PixivDatePreset.week);
      expect(read.usersIri, 1000);
      expect(read.hideAi, isTrue);
      expect(read.ugoira, PixivUgoiraFilter.only);
      expect(PixivSearchFilter.fromJson('not a map'), const PixivSearchFilter());
    });

    test('prefs keep a filter, forget it, and shrug off garbage', () async {
      final prefs = PrefServiceCache(cache: {optionPluginPixivSearchFilters: '', optionPluginPixivHideAi: true});
      expect(readPixivSearchFilter(prefs), isNull);
      expect(pixivStartingFilter(prefs, isPremium: false), const PixivSearchFilter(hideAi: true));

      await savePixivSearchFilter(prefs, const PixivSearchFilter(sort: PixivSearchSort.oldest));
      expect(readPixivSearchFilter(prefs)?.sort, PixivSearchSort.oldest);
      expect(pixivStartingFilter(prefs, isPremium: true).sort, PixivSearchSort.oldest);
      expect(pixivStartingFilter(prefs, isPremium: false).sort, PixivSearchSort.newest);

      await savePixivSearchFilter(prefs, null);
      expect(prefs.get<String>(optionPluginPixivSearchFilters), '');
      await prefs.set(optionPluginPixivSearchFilters, '{broken');
      expect(readPixivSearchFilter(prefs), isNull);
    });
  });

  group('typing several tags', () {
    test('only the word after the last space is being typed', () {
      expect(pixivTypedWord('miku'), 'miku');
      expect(pixivTypedWord('miku ri'), 'ri');
      expect(pixivTypedWord('miku '), '');
      expect(pixivTypedWord('初音ミク　鏡音'), '鏡音');
      expect(pixivHasEarlierWords('miku'), isFalse);
      expect(pixivHasEarlierWords('  miku'), isFalse);
      expect(pixivHasEarlierWords('miku ri'), isTrue);
    });

    test('a picked suggestion replaces the last word and leaves room for the next', () {
      expect(pixivReplaceTypedWord('miku  ri', '鏡音リン'), 'miku 鏡音リン ');
      expect(pixivReplaceTypedWord('a b c', 'd'), 'a b d ');
    });

    test('digits alone name an id', () {
      expect(pixivNumericQuery(' 12345 '), 12345);
      expect(pixivNumericQuery('12a'), isNull);
      expect(pixivNumericQuery('0'), isNull);
      expect(pixivNumericQuery('1 2'), isNull);
      expect(pixivNumericQuery(''), isNull);
    });
  });

  test('the ugoira choice keeps all, only ugoira, or none', () {
    final works = [pixivWork(id: 1, type: 'illust'), pixivWork(id: 2, type: 'ugoira'), pixivWork(id: 3)];
    List<int> ids(List<PixivIllust> illusts) => [for (final illust in illusts) illust.id];
    expect(ids(pixivUgoiraFiltered(works, PixivUgoiraFilter.all)), [1, 2, 3]);
    expect(ids(pixivUgoiraFiltered(works, PixivUgoiraFilter.only)), [2]);
    expect(ids(pixivUgoiraFiltered(works, PixivUgoiraFilter.none)), [1, 3]);
  });
}
