import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';

enum AdvancedSearchFilter {
  allWords,
  exactPhrase,
  anyWords,
  noneWords,
  hashtags,
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
  excludeReplies,
  excludeRetweets,
  since,
  until,
}

enum AdvancedSearchContentFilter { all, media, photos, videos, links }

const Object _unchangedDate = Object();

@immutable
class AdvancedSearchState {
  final String allWords;
  final String exactPhrase;
  final String anyWords;
  final String noneWords;
  final String hashtags;
  final String fromAccounts;
  final String toAccounts;
  final String mentioningAccounts;
  final String minReplies;
  final String minLikes;
  final String minRetweets;
  final DateTime? since;
  final DateTime? until;
  final AdvancedSearchContentFilter contentFilter;
  final bool excludeReplies;
  final bool excludeRetweets;

  const AdvancedSearchState({
    this.allWords = '',
    this.exactPhrase = '',
    this.anyWords = '',
    this.noneWords = '',
    this.hashtags = '',
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
    this.excludeReplies = false,
    this.excludeRetweets = false,
  }) : contentFilter =
           contentFilter ?? (onlyMedia ? AdvancedSearchContentFilter.media : AdvancedSearchContentFilter.all);

  bool get onlyMedia => contentFilter == AdvancedSearchContentFilter.media;

  factory AdvancedSearchState.fromQuery(String query) {
    var state = AdvancedSearchState(allWords: query.trim());
    final trailingOperator = RegExp(r'(^|\s)(-?filter:\w+)$');
    while (true) {
      final match = trailingOperator.firstMatch(state.allWords);
      if (match == null) return state;
      final prefix = state.allWords.substring(0, match.start).trimRight();
      if (!_isTopLevelPrefix(prefix) || RegExp(r'(^|\s)(AND|OR|NOT)$').hasMatch(prefix)) {
        return state;
      }
      final restored = state._restoreTrailingFilter(match.group(2)!);
      if (restored == null) return state;
      state = restored.copyWith(allWords: prefix);
    }
  }

  AdvancedSearchState? _restoreTrailingFilter(String operator) {
    final content = switch (operator) {
      'filter:media' => AdvancedSearchContentFilter.media,
      'filter:images' => AdvancedSearchContentFilter.photos,
      'filter:videos' => AdvancedSearchContentFilter.videos,
      'filter:links' => AdvancedSearchContentFilter.links,
      _ => null,
    };
    if (content != null) {
      return contentFilter == AdvancedSearchContentFilter.all ? copyWith(contentFilter: content) : null;
    }
    return switch (operator) {
      '-filter:replies' when !excludeReplies => copyWith(excludeReplies: true),
      '-filter:retweets' when !excludeRetweets => copyWith(excludeRetweets: true),
      _ => null,
    };
  }

  String get query => buildAdvancedSearchQueryFromState(this);

  List<AdvancedSearchFilter> get activeFilters {
    return AdvancedSearchFilter.values.where(_isActive).toList(growable: false);
  }

  bool _isActive(AdvancedSearchFilter filter) {
    return switch (filter) {
      AdvancedSearchFilter.allWords => allWords.trim().isNotEmpty,
      AdvancedSearchFilter.exactPhrase => exactPhrase.trim().isNotEmpty,
      AdvancedSearchFilter.anyWords => anyWords.trim().isNotEmpty,
      AdvancedSearchFilter.noneWords => noneWords.trim().isNotEmpty,
      AdvancedSearchFilter.hashtags => hashtags.trim().isNotEmpty,
      AdvancedSearchFilter.fromAccounts => fromAccounts.trim().isNotEmpty,
      AdvancedSearchFilter.toAccounts => toAccounts.trim().isNotEmpty,
      AdvancedSearchFilter.mentioningAccounts => mentioningAccounts.trim().isNotEmpty,
      AdvancedSearchFilter.minReplies => _positive(minReplies),
      AdvancedSearchFilter.minLikes => _positive(minLikes),
      AdvancedSearchFilter.minRetweets => _positive(minRetweets),
      AdvancedSearchFilter.onlyMedia => onlyMedia,
      AdvancedSearchFilter.photos => contentFilter == AdvancedSearchContentFilter.photos,
      AdvancedSearchFilter.videos => contentFilter == AdvancedSearchContentFilter.videos,
      AdvancedSearchFilter.links => contentFilter == AdvancedSearchContentFilter.links,
      AdvancedSearchFilter.excludeReplies => excludeReplies,
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
      AdvancedSearchFilter.excludeReplies ||
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
    bool? excludeReplies,
    bool? excludeRetweets,
  }) {
    return AdvancedSearchState(
      allWords: allWords ?? this.allWords,
      exactPhrase: exactPhrase ?? this.exactPhrase,
      anyWords: anyWords ?? this.anyWords,
      noneWords: noneWords ?? this.noneWords,
      hashtags: hashtags ?? this.hashtags,
      fromAccounts: fromAccounts ?? this.fromAccounts,
      toAccounts: toAccounts ?? this.toAccounts,
      mentioningAccounts: mentioningAccounts ?? this.mentioningAccounts,
      minReplies: minReplies ?? this.minReplies,
      minLikes: minLikes ?? this.minLikes,
      minRetweets: minRetweets ?? this.minRetweets,
      since: identical(since, _unchangedDate) ? this.since : since as DateTime?,
      until: identical(until, _unchangedDate) ? this.until : until as DateTime?,
      contentFilter: contentFilter ?? _contentWithOnlyMedia(onlyMedia),
      excludeReplies: excludeReplies ?? this.excludeReplies,
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
      AdvancedSearchFilter.fromAccounts => copyWith(fromAccounts: ''),
      AdvancedSearchFilter.toAccounts => copyWith(toAccounts: ''),
      AdvancedSearchFilter.mentioningAccounts => copyWith(mentioningAccounts: ''),
      AdvancedSearchFilter.minReplies => copyWith(minReplies: ''),
      AdvancedSearchFilter.minLikes => copyWith(minLikes: ''),
      AdvancedSearchFilter.minRetweets => copyWith(minRetweets: ''),
      AdvancedSearchFilter.onlyMedia => copyWith(onlyMedia: false),
      AdvancedSearchFilter.photos => _clearContent(AdvancedSearchContentFilter.photos),
      AdvancedSearchFilter.videos => _clearContent(AdvancedSearchContentFilter.videos),
      AdvancedSearchFilter.links => _clearContent(AdvancedSearchContentFilter.links),
      AdvancedSearchFilter.excludeReplies => copyWith(excludeReplies: false),
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

  void setOnlyMedia(bool value) => update(state.copyWith(onlyMedia: value));

  void setContentFilter(AdvancedSearchContentFilter value) => update(state.copyWith(contentFilter: value));

  void setExcludeReplies(bool value) => update(state.copyWith(excludeReplies: value));

  void setExcludeRetweets(bool value) => update(state.copyWith(excludeRetweets: value));

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

void _addPrefixedGroup(List<String> parts, String input, String Function(String) toOperator) {
  final items = _tokens(input).map(toOperator).toList();
  if (items.isNotEmpty) parts.add(_orGroup(items));
}

void _addMinimum(List<String> parts, String input, String operator) {
  final number = int.tryParse(input.trim());
  if (number != null && number > 0) parts.add('$operator:$number');
}

String buildAdvancedSearchQueryFromState(AdvancedSearchState state) {
  return buildAdvancedSearchQuery(
    allWords: state.allWords,
    exactPhrase: state.exactPhrase,
    anyWords: state.anyWords,
    noneWords: state.noneWords,
    hashtags: state.hashtags,
    fromAccounts: state.fromAccounts,
    toAccounts: state.toAccounts,
    mentioningAccounts: state.mentioningAccounts,
    minReplies: state.minReplies,
    minLikes: state.minLikes,
    minRetweets: state.minRetweets,
    since: state.since,
    until: state.until,
    onlyMedia: state.onlyMedia,
    contentFilter: state.contentFilter,
    excludeReplies: state.excludeReplies,
    excludeRetweets: state.excludeRetweets,
  );
}

String buildAdvancedSearchQuery({
  required String allWords,
  required String exactPhrase,
  required String anyWords,
  required String noneWords,
  required String hashtags,
  required String fromAccounts,
  required String toAccounts,
  required String mentioningAccounts,
  required String minReplies,
  required String minLikes,
  required String minRetweets,
  DateTime? since,
  DateTime? until,
  required bool onlyMedia,
  AdvancedSearchContentFilter? contentFilter,
  bool excludeReplies = false,
  bool excludeRetweets = false,
}) {
  final parts = <String>[];
  if (allWords.trim().isNotEmpty) parts.add(allWords.trim());
  if (exactPhrase.trim().isNotEmpty) parts.add('"${exactPhrase.trim()}"');
  final any = _tokens(anyWords);
  if (any.isNotEmpty) parts.add(_orGroup(any));
  parts.addAll(_tokens(noneWords).map((word) => '-$word'));
  _addPrefixedGroup(parts, hashtags, (tag) => tag.startsWith('#') ? tag : '#$tag');
  _addPrefixedGroup(parts, fromAccounts, (user) => 'from:${user.replaceAll('@', '')}');
  _addPrefixedGroup(parts, toAccounts, (user) => 'to:${user.replaceAll('@', '')}');
  _addPrefixedGroup(parts, mentioningAccounts, (user) => '@${user.replaceAll('@', '')}');
  _addMinimum(parts, minReplies, 'min_replies');
  _addMinimum(parts, minLikes, 'min_faves');
  _addMinimum(parts, minRetweets, 'min_retweets');
  if (since != null) parts.add('since:${_formatDate(since)}');
  if (until != null) parts.add('until:${_formatDate(until)}');
  final content = contentFilter ?? (onlyMedia ? AdvancedSearchContentFilter.media : AdvancedSearchContentFilter.all);
  final contentOperator = _contentOperator(content);
  if (contentOperator != null) parts.add(contentOperator);
  if (excludeReplies) parts.add('-filter:replies');
  if (excludeRetweets) parts.add('-filter:retweets');
  return parts.join(' ');
}

String? _contentOperator(AdvancedSearchContentFilter filter) => switch (filter) {
  AdvancedSearchContentFilter.all => null,
  AdvancedSearchContentFilter.media => 'filter:media',
  AdvancedSearchContentFilter.photos => 'filter:images',
  AdvancedSearchContentFilter.videos => 'filter:videos',
  AdvancedSearchContentFilter.links => 'filter:links',
};

bool _isTopLevelPrefix(String prefix) {
  var quoted = false;
  var escaped = false;
  var parentheses = 0;
  for (final character in prefix.split('')) {
    if (escaped) {
      escaped = false;
      continue;
    }
    if (character == r'\') {
      escaped = true;
    } else if (character == '"') {
      quoted = !quoted;
    } else if (!quoted && character == '(') {
      parentheses++;
    } else if (!quoted && character == ')') {
      if (--parentheses < 0) return false;
    }
  }
  return !quoted && !escaped && parentheses == 0;
}

bool _positive(String value) {
  final number = int.tryParse(value.trim());
  return number != null && number > 0;
}

String _formatDate(DateTime? value) => value == null ? '' : DateFormat('yyyy-MM-dd').format(value);
