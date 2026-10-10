/// Pure pieces of a Pixiv search: what it looks in, how it sorts, which dates
/// and how popular, and the query Pixiv is sent. Novel search reuses them with
/// its own targets.
library;

import 'dart:convert';

import 'package:flutter/foundation.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/plugin_query_words.dart';
import 'package:xta/utils/json.dart';

enum PixivSearchTarget {
  partialTags('partial_match_for_tags'),
  exactTags('exact_match_for_tags'),
  titleCaption('title_and_caption'),

  /// A novel's body text.
  text('text'),

  /// A novel's tags, title and caption together.
  keyword('keyword');

  final String api;

  const PixivSearchTarget(this.api);
}

const pixivIllustSearchTargets = [
  PixivSearchTarget.partialTags,
  PixivSearchTarget.exactTags,
  PixivSearchTarget.titleCaption,
];

const pixivNovelSearchTargets = [
  PixivSearchTarget.partialTags,
  PixivSearchTarget.exactTags,
  PixivSearchTarget.text,
  PixivSearchTarget.keyword,
];

enum PixivSearchSort {
  newest('date_desc'),
  oldest('date_asc'),
  popular('popular_desc'),
  popularMale('popular_male_desc'),
  popularFemale('popular_female_desc');

  final String api;

  const PixivSearchSort(this.api);

  bool get byPopularity => this != newest && this != oldest;
}

/// What a search looks for. Works and novels each have their own places to
/// look, their own orders (Pixiv keeps different ones for Premium) and their
/// own remembered filter.
enum PixivSearchKind {
  works(
    targets: pixivIllustSearchTargets,
    sorts: PixivSearchSort.values,
    premiumSorts: {PixivSearchSort.oldest, PixivSearchSort.popularMale, PixivSearchSort.popularFemale},
    filterPref: optionPluginPixivSearchFilters,
  ),
  novels(
    targets: pixivNovelSearchTargets,
    sorts: [PixivSearchSort.newest, PixivSearchSort.oldest, PixivSearchSort.popular],
    premiumSorts: {PixivSearchSort.popular},
    filterPref: optionPluginPixivNovelSearchFilters,
  );

  final List<PixivSearchTarget> targets;
  final List<PixivSearchSort> sorts;

  /// Orders Pixiv refuses to an account without Premium.
  final Set<PixivSearchSort> premiumSorts;

  /// Where a remembered filter is kept.
  final String filterPref;

  const PixivSearchKind({
    required this.targets,
    required this.sorts,
    required this.premiumSorts,
    required this.filterPref,
  });

  /// Only works come as ugoira, take bookmark brackets and have a free popular preview.
  bool get isWorks => this == works;
}

/// The orders worth offering: Pixiv answers the Premium ones only to Premium.
List<PixivSearchSort> pixivSearchSorts({required bool isPremium, PixivSearchKind kind = PixivSearchKind.works}) => [
  for (final sort in kind.sorts)
    if (isPremium || !kind.premiumSorts.contains(sort)) sort,
];

enum PixivUgoiraFilter { all, only, none }

/// Works as the ugoira choice lets them through. Pixiv cannot filter this
/// itself, so it runs over each page on the device.
List<PixivIllust> pixivUgoiraFiltered(List<PixivIllust> illusts, PixivUgoiraFilter filter) => switch (filter) {
  PixivUgoiraFilter.all => illusts,
  PixivUgoiraFilter.only => [
    for (final illust in illusts)
      if (illust.isUgoira) illust,
  ],
  PixivUgoiraFilter.none => [
    for (final illust in illusts)
      if (!illust.isUgoira) illust,
  ],
};

enum PixivDatePreset { any, day, week, month, halfYear, year, custom }

/// Posting dates from [start] to [end], both whole days.
@immutable
class PixivDateRange {
  final DateTime start;
  final DateTime end;

  PixivDateRange(DateTime start, DateTime end) : start = _day(start), end = _day(end);

  @override
  bool operator ==(Object other) => other is PixivDateRange && other.start == start && other.end == end;

  @override
  int get hashCode => Object.hash(start, end);
}

DateTime _day(DateTime time) => DateTime(time.year, time.month, time.day);

/// Pixiv opened in September 2007; nothing was posted before it.
final pixivSearchEarliestDate = DateTime(2007, 9, 13);

/// The dates [preset] covers on [now]'s day; [custom] is the reader's own pick.
/// Relative presets are worked out afresh each time, so a remembered "past
/// week" always means the week before the search.
PixivDateRange? pixivPresetRange(PixivDatePreset preset, DateTime now, {PixivDateRange? custom}) {
  final today = _day(now);
  return switch (preset) {
    PixivDatePreset.any => null,
    PixivDatePreset.custom => custom,
    PixivDatePreset.day => PixivDateRange(DateTime(today.year, today.month, today.day - 1), today),
    PixivDatePreset.week => PixivDateRange(DateTime(today.year, today.month, today.day - 7), today),
    PixivDatePreset.month => PixivDateRange(_monthsBack(today, 1), today),
    PixivDatePreset.halfYear => PixivDateRange(_monthsBack(today, 6), today),
    PixivDatePreset.year => PixivDateRange(_monthsBack(today, 12), today),
  };
}

/// The same day [months] earlier, or that month's last day when it is shorter
/// (31 March less a month is 28 or 29 February, not 3 March).
DateTime _monthsBack(DateTime date, int months) {
  final first = DateTime(date.year, date.month - months);
  final lastDay = DateTime(first.year, first.month + 1, 0).day;
  return DateTime(first.year, first.month, date.day > lastDay ? lastDay : date.day);
}

/// `YYYY-MM-DD`, as Pixiv's date parameters take it.
String pixivSearchDate(DateTime date) =>
    '${date.year.toString().padLeft(4, '0')}-${date.month.toString().padLeft(2, '0')}'
    '-${date.day.toString().padLeft(2, '0')}';

/// Popularity tags Pixiv users attach (`1000users入り`); they work without Premium.
const pixivUsersIriThresholds = [100, 250, 500, 1000, 5000, 7500, 10000, 20000, 30000, 50000, 100000];

/// A bookmark-count bracket; no [max] means [min] and above.
typedef PixivBookmarkRange = ({int min, int? max});

/// The brackets Pixiv offers Premium accounts.
const pixivBookmarkRanges = <PixivBookmarkRange>[
  (min: 10000, max: null),
  (min: 50000, max: 99999),
  (min: 10000, max: 49999),
  (min: 5000, max: 9999),
  (min: 1000, max: 4999),
  (min: 500, max: 999),
  (min: 300, max: 499),
  (min: 100, max: 299),
  (min: 50, max: 99),
  (min: 30, max: 49),
  (min: 10, max: 29),
];

/// Everything a search narrows by besides its words.
@immutable
class PixivSearchFilter {
  final PixivSearchTarget target;
  final PixivSearchSort sort;
  final PixivDatePreset datePreset;

  /// The reader's own dates, used while [datePreset] is custom.
  final PixivDateRange? customRange;

  /// Only works carrying the `<N>users入り` tag; null for any.
  final int? usersIri;

  /// Premium only.
  final PixivBookmarkRange? bookmarks;

  /// Asks Pixiv to leave AI-generated works out (`search_ai_type=1`).
  final bool hideAi;
  final PixivUgoiraFilter ugoira;

  const PixivSearchFilter({
    this.target = PixivSearchTarget.partialTags,
    this.sort = PixivSearchSort.newest,
    this.datePreset = PixivDatePreset.any,
    this.customRange,
    this.usersIri,
    this.bookmarks,
    this.hideAi = false,
    this.ugoira = PixivUgoiraFilter.all,
  });

  static const _keep = Object();

  PixivSearchFilter copyWith({
    PixivSearchTarget? target,
    PixivSearchSort? sort,
    bool? hideAi,
    PixivUgoiraFilter? ugoira,
  }) => _copy(target: target, sort: sort, hideAi: hideAi, ugoira: ugoira);

  /// A custom [preset] without [custom] dates means any date.
  PixivSearchFilter withDates(PixivDatePreset preset, {PixivDateRange? custom}) {
    final usable = preset != PixivDatePreset.custom || custom != null;
    return _copy(
      datePreset: usable ? preset : PixivDatePreset.any,
      customRange: usable && preset == PixivDatePreset.custom ? custom : null,
    );
  }

  PixivSearchFilter withUsersIri(int? threshold) => _copy(usersIri: threshold);

  PixivSearchFilter withBookmarks(PixivBookmarkRange? range) => _copy(bookmarks: range);

  PixivSearchFilter _copy({
    PixivSearchTarget? target,
    PixivSearchSort? sort,
    PixivDatePreset? datePreset,
    Object? customRange = _keep,
    Object? usersIri = _keep,
    Object? bookmarks = _keep,
    bool? hideAi,
    PixivUgoiraFilter? ugoira,
  }) => PixivSearchFilter(
    target: target ?? this.target,
    sort: sort ?? this.sort,
    datePreset: datePreset ?? this.datePreset,
    customRange: identical(customRange, _keep) ? this.customRange : customRange as PixivDateRange?,
    usersIri: identical(usersIri, _keep) ? this.usersIri : usersIri as int?,
    bookmarks: identical(bookmarks, _keep) ? this.bookmarks : bookmarks as PixivBookmarkRange?,
    hideAi: hideAi ?? this.hideAi,
    ugoira: ugoira ?? this.ugoira,
  );

  /// What the account may send for [kind]: a place to look or an order the
  /// kind does not have goes back to the first one, without Premium a Premium
  /// order falls back to newest and the bookmark bracket goes, and novels take
  /// no bracket and no ugoira choice.
  PixivSearchFilter forAccount({required bool isPremium, PixivSearchKind kind = PixivSearchKind.works}) {
    final usable = pixivSearchSorts(isPremium: isPremium, kind: kind);
    return _copy(
      target: kind.targets.contains(target) ? target : kind.targets.first,
      sort: usable.contains(sort) ? sort : PixivSearchSort.newest,
      bookmarks: isPremium && kind.isWorks ? bookmarks : null,
      ugoira: kind.isWorks ? ugoira : PixivUgoiraFilter.all,
    );
  }

  /// Whether the filter sheet's own choices differ from [base].
  bool sheetDiffersFrom(PixivSearchFilter base) =>
      target != base.target || sort != base.sort || hideAi != base.hideAi || ugoira != base.ugoira;

  Map<String, Object?> toJson() => {
    'target': target.api,
    'sort': sort.api,
    'date': datePreset.name,
    if (customRange case final range?) 'start': pixivSearchDate(range.start),
    if (customRange case final range?) 'end': pixivSearchDate(range.end),
    'users_iri': ?usersIri,
    'bookmark_min': ?bookmarks?.min,
    'bookmark_max': ?bookmarks?.max,
    'hide_ai': hideAi,
    'ugoira': ugoira.name,
  };

  /// A stored filter; anything missing or unknown keeps [fallback]'s choice.
  factory PixivSearchFilter.fromJson(Object? json, {PixivSearchFilter fallback = const PixivSearchFilter()}) {
    final data = Json(json);
    final start = DateTime.tryParse(data['start'].string ?? '');
    final end = DateTime.tryParse(data['end'].string ?? '');
    final min = data['bookmark_min'].integer;
    return fallback
        ._copy(
          target: _named(PixivSearchTarget.values, data['target'].string, (target) => target.api),
          sort: _named(PixivSearchSort.values, data['sort'].string, (sort) => sort.api),
          usersIri: data['users_iri'].integer ?? fallback.usersIri,
          bookmarks: min != null ? (min: min, max: data['bookmark_max'].integer) : fallback.bookmarks,
          hideAi: data['hide_ai'].boolean,
          ugoira: _named(PixivUgoiraFilter.values, data['ugoira'].string, (ugoira) => ugoira.name),
        )
        .withDates(
          _named(PixivDatePreset.values, data['date'].string, (preset) => preset.name) ?? fallback.datePreset,
          custom: start != null && end != null ? PixivDateRange(start, end) : fallback.customRange,
        );
  }

  @override
  bool operator ==(Object other) =>
      other is PixivSearchFilter &&
      other.target == target &&
      other.sort == sort &&
      other.datePreset == datePreset &&
      other.customRange == customRange &&
      other.usersIri == usersIri &&
      other.bookmarks == bookmarks &&
      other.hideAi == hideAi &&
      other.ugoira == ugoira;

  @override
  int get hashCode => Object.hash(target, sort, datePreset, customRange, usersIri, bookmarks, hideAi, ugoira);
}

T? _named<T>(List<T> values, String? raw, String Function(T value) name) {
  for (final value in values) {
    if (name(value) == raw) return value;
  }
  return null;
}

/// The filter the reader asked to keep for [kind], or null when they keep none.
PixivSearchFilter? readPixivSearchFilter(
  BasePrefService prefs, {
  PixivSearchFilter fallback = const PixivSearchFilter(),
  PixivSearchKind kind = PixivSearchKind.works,
}) {
  final raw = prefs.get<String>(kind.filterPref) ?? '';
  if (raw.trim().isEmpty) return null;
  try {
    return PixivSearchFilter.fromJson(jsonDecode(raw), fallback: fallback);
  } on FormatException {
    return null;
  }
}

/// Keeps [filter] for later searches of [kind], or forgets the kept one when null.
Future<void> savePixivSearchFilter(
  BasePrefService prefs,
  PixivSearchFilter? filter, {
  PixivSearchKind kind = PixivSearchKind.works,
}) async {
  await prefs.set(kind.filterPref, filter == null ? '' : jsonEncode(filter.toJson()));
}

/// A filter nobody has touched: it hides AI works exactly when the feeds do.
PixivSearchFilter pixivFreshFilter(BasePrefService prefs) =>
    PixivSearchFilter(hideAi: prefs.get<bool>(optionPluginPixivHideAi) == true);

/// Where a new search of [kind] starts: the remembered filter, else a fresh one.
PixivSearchFilter pixivStartingFilter(
  BasePrefService prefs, {
  required bool isPremium,
  PixivSearchKind kind = PixivSearchKind.works,
}) {
  final fresh = pixivFreshFilter(prefs);
  final kept = readPixivSearchFilter(prefs, fallback: fresh, kind: kind);
  return (kept ?? fresh).forAccount(isPremium: isPremium, kind: kind);
}

/// The words sent to Pixiv. The popularity tag goes on here and nowhere else,
/// so the field and the search history keep what the reader typed.
String pixivSearchWord(String word, int? usersIri) =>
    usersIri == null ? word.trim() : '${word.trim()} ${usersIri}users入り';

/// The `/v1/search/illust` query, or with [kind] novels the `/v1/search/novel`
/// one, for [word] under [filter], dates as of [now]. Novel search takes no
/// AI or bookmark parameters; Hide AI applies to its pages on the device.
Map<String, String> pixivSearchQuery(
  PixivSearchFilter filter,
  String word, {
  required DateTime now,
  PixivSearchKind kind = PixivSearchKind.works,
}) {
  final range = pixivPresetRange(filter.datePreset, now, custom: filter.customRange);
  final bookmarks = kind.isWorks ? filter.bookmarks : null;
  return {
    'word': pixivSearchWord(word, filter.usersIri),
    'search_target': filter.target.api,
    'sort': filter.sort.api,
    if (kind.isWorks) 'search_ai_type': filter.hideAi ? '1' : '0',
    if (range != null) 'start_date': pixivSearchDate(range.start),
    if (range != null) 'end_date': pixivSearchDate(range.end),
    if (bookmarks != null) 'bookmark_num_min': '${bookmarks.min}',
    if (bookmarks?.max case final max?) 'bookmark_num_max': '$max',
    'merge_plain_keyword_results': 'true',
    'filter': 'for_android',
  };
}

/// The query for Pixiv's free popular preview of [word].
Map<String, String> pixivPopularPreviewQuery(PixivSearchTarget target, String word) => {
  'word': word.trim(),
  'search_target': target.api,
  'merge_plain_keyword_results': 'true',
  'include_translated_tag_results': 'true',
  'filter': 'for_android',
};

/// The word still being typed: whatever follows the last space.
String pixivTypedWord(String text) => splitQueryInput(text).rest.trim();

/// Whether [text] has finished words before the one being typed.
bool pixivHasEarlierWords(String text) => splitQueryInput(text).done.isNotEmpty;

/// [text] with the word being typed replaced by [tag], and a space after it
/// for the next one.
String pixivReplaceTypedWord(String text, String tag) => '${queryText([...splitQueryInput(text).done, tag])} ';

/// The id a query of digits alone names, for the open-by-id shortcuts.
int? pixivNumericQuery(String text) {
  final digits = text.trim();
  if (!RegExp(r'^\d{1,12}$').hasMatch(digits)) return null;
  final id = int.parse(digits);
  return id > 0 ? id : null;
}
