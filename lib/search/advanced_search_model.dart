import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';

part 'advanced_search_reader.dart';

enum AdvancedSearchFilter {
  allWords,
  exactPhrase,
  anyWords,
  noneWords,
  hashtags,
  language,
  fromAccounts,
  toAccounts,
  mentioningAccounts,
  minReplies,
  minLikes,
  minRetweets,
  onlyMedia,
  photos,
  videos,
  links,
  replies,
  includeReposts,
  excludeRetweets,
  since,
  until,
}

enum AdvancedSearchContentFilter { all, media, photos, videos }

/// Whether results keep posts of one kind among the others, keep only them, or leave them out.
enum AdvancedSearchInclusion { include, only, exclude }

const Object _unchangedDate = Object();

@immutable
class AdvancedSearchState {
  final String allWords;
  final String exactPhrase;
  final String anyWords;
  final String noneWords;
  final String hashtags;

  /// The code `lang:` takes, or empty for every language.
  final String language;
  final String fromAccounts;
  final String toAccounts;
  final String mentioningAccounts;
  final String minReplies;
  final String minLikes;
  final String minRetweets;
  final DateTime? since;
  final DateTime? until;
  final AdvancedSearchContentFilter contentFilter;
  final AdvancedSearchInclusion links;
  final AdvancedSearchInclusion replies;
  final bool includeReposts;
  final bool excludeRetweets;

  const AdvancedSearchState({
    this.allWords = '',
    this.exactPhrase = '',
    this.anyWords = '',
    this.noneWords = '',
    this.hashtags = '',
    this.language = '',
    this.fromAccounts = '',
    this.toAccounts = '',
    this.mentioningAccounts = '',
    this.minReplies = '',
    this.minLikes = '',
    this.minRetweets = '',
    this.since,
    this.until,
    bool onlyMedia = false,
    AdvancedSearchContentFilter? contentFilter,
    this.links = AdvancedSearchInclusion.include,
    this.replies = AdvancedSearchInclusion.include,
    this.includeReposts = false,
    this.excludeRetweets = false,
  }) : contentFilter =
           contentFilter ?? (onlyMedia ? AdvancedSearchContentFilter.media : AdvancedSearchContentFilter.all);

  bool get onlyMedia => contentFilter == AdvancedSearchContentFilter.media;

  /// The filters the form can show for [query]; whatever it cannot stays in [allWords] as typed.
  factory AdvancedSearchState.fromQuery(String query) => _readQuery(query);

  String get query => buildAdvancedSearchQueryFromState(this);

  List<AdvancedSearchFilter> get activeFilters {
    return AdvancedSearchFilter.values.where(_isActive).toList(growable: false);
  }

  /// Whether a filter besides the plain words is set, as when a typed query carries operators.
  bool get hasOperators => activeFilters.any((filter) => filter != AdvancedSearchFilter.allWords);

  bool _isActive(AdvancedSearchFilter filter) {
    return switch (filter) {
      AdvancedSearchFilter.allWords => allWords.trim().isNotEmpty,
      AdvancedSearchFilter.exactPhrase => exactPhrase.trim().isNotEmpty,
      AdvancedSearchFilter.anyWords => anyWords.trim().isNotEmpty,
      AdvancedSearchFilter.noneWords => noneWords.trim().isNotEmpty,
      AdvancedSearchFilter.hashtags => hashtags.trim().isNotEmpty,
      AdvancedSearchFilter.language => language.isNotEmpty,
      AdvancedSearchFilter.fromAccounts => fromAccounts.trim().isNotEmpty,
      AdvancedSearchFilter.toAccounts => toAccounts.trim().isNotEmpty,
      AdvancedSearchFilter.mentioningAccounts => mentioningAccounts.trim().isNotEmpty,
      AdvancedSearchFilter.minReplies => _positive(minReplies),
      AdvancedSearchFilter.minLikes => _positive(minLikes),
      AdvancedSearchFilter.minRetweets => _positive(minRetweets),
      AdvancedSearchFilter.onlyMedia => onlyMedia,
      AdvancedSearchFilter.photos => contentFilter == AdvancedSearchContentFilter.photos,
      AdvancedSearchFilter.videos => contentFilter == AdvancedSearchContentFilter.videos,
      AdvancedSearchFilter.links => links != AdvancedSearchInclusion.include,
      AdvancedSearchFilter.replies => replies != AdvancedSearchInclusion.include,
      AdvancedSearchFilter.includeReposts => includeReposts,
      AdvancedSearchFilter.excludeRetweets => excludeRetweets,
      AdvancedSearchFilter.since => since != null,
      AdvancedSearchFilter.until => until != null,
    };
  }

  String valueOf(AdvancedSearchFilter filter) {
    return switch (filter) {
      AdvancedSearchFilter.allWords => allWords.trim(),
      AdvancedSearchFilter.exactPhrase => exactPhrase.trim(),
      AdvancedSearchFilter.anyWords => anyWords.trim(),
      AdvancedSearchFilter.noneWords => noneWords.trim(),
      AdvancedSearchFilter.hashtags => hashtags.trim(),
      AdvancedSearchFilter.language => language,
      AdvancedSearchFilter.fromAccounts => fromAccounts.trim(),
      AdvancedSearchFilter.toAccounts => toAccounts.trim(),
      AdvancedSearchFilter.mentioningAccounts => mentioningAccounts.trim(),
      AdvancedSearchFilter.minReplies => minReplies.trim(),
      AdvancedSearchFilter.minLikes => minLikes.trim(),
      AdvancedSearchFilter.minRetweets => minRetweets.trim(),
      AdvancedSearchFilter.onlyMedia ||
      AdvancedSearchFilter.photos ||
      AdvancedSearchFilter.videos ||
      AdvancedSearchFilter.links ||
      AdvancedSearchFilter.replies ||
      AdvancedSearchFilter.includeReposts ||
      AdvancedSearchFilter.excludeRetweets => '',
      AdvancedSearchFilter.since => _formatDate(since),
      AdvancedSearchFilter.until => _formatDate(until),
    };
  }

  AdvancedSearchState copyWith({
    String? allWords,
    String? exactPhrase,
    String? anyWords,
    String? noneWords,
    String? hashtags,
    String? language,
    String? fromAccounts,
    String? toAccounts,
    String? mentioningAccounts,
    String? minReplies,
    String? minLikes,
    String? minRetweets,
    Object? since = _unchangedDate,
    Object? until = _unchangedDate,
    bool? onlyMedia,
    AdvancedSearchContentFilter? contentFilter,
    AdvancedSearchInclusion? links,
    AdvancedSearchInclusion? replies,
    bool? includeReposts,
    bool? excludeRetweets,
  }) {
    return AdvancedSearchState(
      allWords: allWords ?? this.allWords,
      exactPhrase: exactPhrase ?? this.exactPhrase,
      anyWords: anyWords ?? this.anyWords,
      noneWords: noneWords ?? this.noneWords,
      hashtags: hashtags ?? this.hashtags,
      language: language ?? this.language,
      fromAccounts: fromAccounts ?? this.fromAccounts,
      toAccounts: toAccounts ?? this.toAccounts,
      mentioningAccounts: mentioningAccounts ?? this.mentioningAccounts,
      minReplies: minReplies ?? this.minReplies,
      minLikes: minLikes ?? this.minLikes,
      minRetweets: minRetweets ?? this.minRetweets,
      since: identical(since, _unchangedDate) ? this.since : since as DateTime?,
      until: identical(until, _unchangedDate) ? this.until : until as DateTime?,
      contentFilter: contentFilter ?? _contentWithOnlyMedia(onlyMedia),
      links: links ?? this.links,
      replies: replies ?? this.replies,
      includeReposts: includeReposts ?? this.includeReposts,
      excludeRetweets: excludeRetweets ?? this.excludeRetweets,
    );
  }

  AdvancedSearchContentFilter _contentWithOnlyMedia(bool? onlyMedia) {
    if (onlyMedia == true) return AdvancedSearchContentFilter.media;
    if (onlyMedia == false && this.onlyMedia) {
      return AdvancedSearchContentFilter.all;
    }
    return contentFilter;
  }

  AdvancedSearchState _clearContent(AdvancedSearchContentFilter value) {
    return contentFilter == value ? copyWith(contentFilter: AdvancedSearchContentFilter.all) : this;
  }

  AdvancedSearchState clear(AdvancedSearchFilter filter) {
    return switch (filter) {
      AdvancedSearchFilter.allWords => copyWith(allWords: ''),
      AdvancedSearchFilter.exactPhrase => copyWith(exactPhrase: ''),
      AdvancedSearchFilter.anyWords => copyWith(anyWords: ''),
      AdvancedSearchFilter.noneWords => copyWith(noneWords: ''),
      AdvancedSearchFilter.hashtags => copyWith(hashtags: ''),
      AdvancedSearchFilter.language => copyWith(language: ''),
      AdvancedSearchFilter.fromAccounts => copyWith(fromAccounts: ''),
      AdvancedSearchFilter.toAccounts => copyWith(toAccounts: ''),
      AdvancedSearchFilter.mentioningAccounts => copyWith(mentioningAccounts: ''),
      AdvancedSearchFilter.minReplies => copyWith(minReplies: ''),
      AdvancedSearchFilter.minLikes => copyWith(minLikes: ''),
      AdvancedSearchFilter.minRetweets => copyWith(minRetweets: ''),
      AdvancedSearchFilter.onlyMedia => copyWith(onlyMedia: false),
      AdvancedSearchFilter.photos => _clearContent(AdvancedSearchContentFilter.photos),
      AdvancedSearchFilter.videos => _clearContent(AdvancedSearchContentFilter.videos),
      AdvancedSearchFilter.links => copyWith(links: AdvancedSearchInclusion.include),
      AdvancedSearchFilter.replies => copyWith(replies: AdvancedSearchInclusion.include),
      AdvancedSearchFilter.includeReposts => copyWith(includeReposts: false),
      AdvancedSearchFilter.excludeRetweets => copyWith(excludeRetweets: false),
      AdvancedSearchFilter.since => copyWith(since: null),
      AdvancedSearchFilter.until => copyWith(until: null),
    };
  }
}

class AdvancedSearchStore extends Store<AdvancedSearchState> {
  AdvancedSearchStore(super.initialState);

  void updateText(AdvancedSearchFilter filter, String value) {
    final next = switch (filter) {
      AdvancedSearchFilter.allWords => state.copyWith(allWords: value),
      AdvancedSearchFilter.exactPhrase => state.copyWith(exactPhrase: value),
      AdvancedSearchFilter.anyWords => state.copyWith(anyWords: value),
      AdvancedSearchFilter.noneWords => state.copyWith(noneWords: value),
      AdvancedSearchFilter.hashtags => state.copyWith(hashtags: value),
      AdvancedSearchFilter.fromAccounts => state.copyWith(fromAccounts: value),
      AdvancedSearchFilter.toAccounts => state.copyWith(toAccounts: value),
      AdvancedSearchFilter.mentioningAccounts => state.copyWith(mentioningAccounts: value),
      AdvancedSearchFilter.minReplies => state.copyWith(minReplies: value),
      AdvancedSearchFilter.minLikes => state.copyWith(minLikes: value),
      AdvancedSearchFilter.minRetweets => state.copyWith(minRetweets: value),
      _ => state,
    };
    update(next);
  }

  void setLanguage(String value) => update(state.copyWith(language: value));

  void setOnlyMedia(bool value) => update(state.copyWith(onlyMedia: value));

  void setContentFilter(AdvancedSearchContentFilter value) => update(state.copyWith(contentFilter: value));

  void setLinks(AdvancedSearchInclusion value) => update(state.copyWith(links: value));

  void setReplies(AdvancedSearchInclusion value) => update(state.copyWith(replies: value));

  /// Reposts brought in and retweets left out contradict each other, so turning one on turns the other off.
  void setIncludeReposts(bool value) =>
      update(state.copyWith(includeReposts: value, excludeRetweets: value ? false : null));

  void setExcludeRetweets(bool value) =>
      update(state.copyWith(excludeRetweets: value, includeReposts: value ? false : null));

  void setSince(DateTime? value) => update(state.copyWith(since: value));

  void setUntil(DateTime? value) => update(state.copyWith(until: value));

  void clear(AdvancedSearchFilter filter) => update(state.clear(filter));

  void reset() => update(const AdvancedSearchState());
}

List<String> _tokens(String input) => input.split(RegExp(r'[,\s]+')).where((item) => item.isNotEmpty).toList();

String _orGroup(Iterable<String> items) {
  final list = items.toList();
  return list.length == 1 ? list.first : '(${list.join(' OR ')})';
}

String? _prefixedGroup(String input, String Function(String) toOperator) {
  final items = _tokens(input).map(toOperator).toList();
  return items.isEmpty ? null : _orGroup(items);
}

String? _minimum(String input, String operator) {
  final number = int.tryParse(input.trim());
  return number != null && number > 0 ? '$operator:$number' : null;
}

/// The query X receives, in a fixed order so the same filters always send the same text.
String buildAdvancedSearchQueryFromState(AdvancedSearchState state) => [
  ..._wordOperators(state),
  if (state.language.isNotEmpty) 'lang:${state.language}',
  ?_prefixedGroup(state.fromAccounts, (user) => 'from:${user.replaceAll('@', '')}'),
  ?_prefixedGroup(state.toAccounts, (user) => 'to:${user.replaceAll('@', '')}'),
  ?_prefixedGroup(state.mentioningAccounts, (user) => '@${user.replaceAll('@', '')}'),
  ?_minimum(state.minReplies, 'min_replies'),
  ?_minimum(state.minLikes, 'min_faves'),
  ?_minimum(state.minRetweets, 'min_retweets'),
  if (state.since != null) 'since:${_formatDate(state.since)}',
  if (state.until != null) 'until:${_formatDate(state.until)}',
  ..._kindOperators(state),
].join(' ');

List<String> _wordOperators(AdvancedSearchState state) {
  final any = _tokens(state.anyWords);
  return [
    if (state.allWords.trim().isNotEmpty) state.allWords.trim(),
    if (state.exactPhrase.trim().isNotEmpty) '"${state.exactPhrase.trim()}"',
    if (any.isNotEmpty) _orGroup(any),
    ..._tokens(state.noneWords).map((word) => '-$word'),
    ?_prefixedGroup(state.hashtags, (tag) => tag.startsWith('#') ? tag : '#$tag'),
  ];
}

List<String> _kindOperators(AdvancedSearchState state) => [
  ?_contentOperator(state.contentFilter),
  ?_inclusionOperator('links', state.links),
  ?_inclusionOperator('replies', state.replies),
  if (state.includeReposts) 'include:nativeretweets',
  if (state.excludeRetweets) '-filter:retweets',
];

String? _contentOperator(AdvancedSearchContentFilter filter) => switch (filter) {
  AdvancedSearchContentFilter.all => null,
  AdvancedSearchContentFilter.media => 'filter:media',
  AdvancedSearchContentFilter.photos => 'filter:images',
  AdvancedSearchContentFilter.videos => 'filter:videos',
};

String? _inclusionOperator(String kind, AdvancedSearchInclusion inclusion) => switch (inclusion) {
  AdvancedSearchInclusion.include => null,
  AdvancedSearchInclusion.only => 'filter:$kind',
  AdvancedSearchInclusion.exclude => '-filter:$kind',
};

bool _positive(String value) {
  final number = int.tryParse(value.trim());
  return number != null && number > 0;
}

/// X's query syntax, not the reader's: `since:2026-01-31` in Western digits.
String _formatDate(DateTime? value) => value == null ? '' : DateFormat('yyyy-MM-dd', 'en_US').format(value);
