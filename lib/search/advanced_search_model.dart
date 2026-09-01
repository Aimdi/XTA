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
  since,
  until,
}

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
  final bool onlyMedia;

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
    this.onlyMedia = false,
  });

  factory AdvancedSearchState.fromQuery(String query) {
    return AdvancedSearchState(allWords: query.trim());
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
      AdvancedSearchFilter.mentioningAccounts =>
        mentioningAccounts.trim().isNotEmpty,
      AdvancedSearchFilter.minReplies => _positive(minReplies),
      AdvancedSearchFilter.minLikes => _positive(minLikes),
      AdvancedSearchFilter.minRetweets => _positive(minRetweets),
      AdvancedSearchFilter.onlyMedia => onlyMedia,
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
      AdvancedSearchFilter.onlyMedia => '',
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
      onlyMedia: onlyMedia ?? this.onlyMedia,
    );
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
      AdvancedSearchFilter.mentioningAccounts => copyWith(
        mentioningAccounts: '',
      ),
      AdvancedSearchFilter.minReplies => copyWith(minReplies: ''),
      AdvancedSearchFilter.minLikes => copyWith(minLikes: ''),
      AdvancedSearchFilter.minRetweets => copyWith(minRetweets: ''),
      AdvancedSearchFilter.onlyMedia => copyWith(onlyMedia: false),
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
      AdvancedSearchFilter.mentioningAccounts => state.copyWith(
        mentioningAccounts: value,
      ),
      AdvancedSearchFilter.minReplies => state.copyWith(minReplies: value),
      AdvancedSearchFilter.minLikes => state.copyWith(minLikes: value),
      AdvancedSearchFilter.minRetweets => state.copyWith(minRetweets: value),
      _ => state,
    };
    update(next);
  }

  void setOnlyMedia(bool value) => update(state.copyWith(onlyMedia: value));

  void setSince(DateTime? value) => update(state.copyWith(since: value));

  void setUntil(DateTime? value) => update(state.copyWith(until: value));

  void clear(AdvancedSearchFilter filter) => update(state.clear(filter));

  void reset() => update(const AdvancedSearchState());
}

List<String> _tokens(String input) =>
    input.split(RegExp(r'[,\s]+')).where((item) => item.isNotEmpty).toList();

String _orGroup(Iterable<String> items) {
  final list = items.toList();
  return list.length == 1 ? list.first : '(${list.join(' OR ')})';
}

void _addPrefixedGroup(
  List<String> parts,
  String input,
  String Function(String) toOperator,
) {
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
}) {
  final parts = <String>[];
  if (allWords.trim().isNotEmpty) parts.add(allWords.trim());
  if (exactPhrase.trim().isNotEmpty) parts.add('"${exactPhrase.trim()}"');
  final any = _tokens(anyWords);
  if (any.isNotEmpty) parts.add(_orGroup(any));
  parts.addAll(_tokens(noneWords).map((word) => '-$word'));
  _addPrefixedGroup(
    parts,
    hashtags,
    (tag) => tag.startsWith('#') ? tag : '#$tag',
  );
  _addPrefixedGroup(
    parts,
    fromAccounts,
    (user) => 'from:${user.replaceAll('@', '')}',
  );
  _addPrefixedGroup(
    parts,
    toAccounts,
    (user) => 'to:${user.replaceAll('@', '')}',
  );
  _addPrefixedGroup(
    parts,
    mentioningAccounts,
    (user) => '@${user.replaceAll('@', '')}',
  );
  _addMinimum(parts, minReplies, 'min_replies');
  _addMinimum(parts, minLikes, 'min_faves');
  _addMinimum(parts, minRetweets, 'min_retweets');
  if (since != null) parts.add('since:${_formatDate(since)}');
  if (until != null) parts.add('until:${_formatDate(until)}');
  if (onlyMedia) parts.add('filter:media');
  return parts.join(' ');
}

bool _positive(String value) {
  final number = int.tryParse(value.trim());
  return number != null && number > 0;
}

String _formatDate(DateTime? value) =>
    value == null ? '' : DateFormat('yyyy-MM-dd').format(value);
