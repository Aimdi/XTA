import 'dart:convert';

/// What happens when a muted keyword matches a post.
enum KeywordFilterAction {
  hide,
  fold;

  static KeywordFilterAction parse(String? raw) => switch (raw) {
    'fold' => KeywordFilterAction.fold,
    _ => KeywordFilterAction.hide,
  };

  String get stored => switch (this) {
    KeywordFilterAction.fold => 'fold',
    KeywordFilterAction.hide => 'hide',
  };
}

/// One muted keyword with optional expiry and fold/hide action.
///
/// Stored in `subscription_group.muted_keywords` as a JSON array of objects.
/// Legacy comma-separated terms are read as hide actions with no expiry.
class MutedKeyword {
  final String term;
  final DateTime? until;
  final KeywordFilterAction action;

  const MutedKeyword({required this.term, this.until, this.action = KeywordFilterAction.hide});

  Map<String, dynamic> toJson() => {
    'term': term,
    if (until != null) 'until': until!.toIso8601String(),
    if (action != KeywordFilterAction.hide) 'action': action.stored,
  };

  factory MutedKeyword.fromJson(Map<String, dynamic> json) {
    final untilRaw = json['until'] as String?;
    return MutedKeyword(
      term: (json['term'] as String?)?.trim() ?? '',
      until: untilRaw == null || untilRaw.isEmpty ? null : DateTime.tryParse(untilRaw),
      action: KeywordFilterAction.parse(json['action'] as String?),
    );
  }

  static const _unset = Object();

  MutedKeyword copyWith({String? term, Object? until = _unset, KeywordFilterAction? action, bool clearUntil = false}) {
    return MutedKeyword(
      term: term ?? this.term,
      until: clearUntil ? null : (identical(until, _unset) ? this.until : until as DateTime?),
      action: action ?? this.action,
    );
  }
}

/// Reads legacy CSV and JSON array forms from the database column.
List<MutedKeyword> parseMutedKeywordsStored(String? raw) {
  if (raw == null || raw.trim().isEmpty) {
    return const [];
  }

  final trimmed = raw.trim();
  if (trimmed.startsWith('[')) {
    return _parseJsonArray(trimmed);
  }

  return parseMutedKeywords(raw).map((term) => MutedKeyword(term: term)).toList(growable: false);
}

List<MutedKeyword> _parseJsonArray(String raw) {
  try {
    final decoded = jsonDecode(raw);
    if (decoded is! List) {
      return const [];
    }
    return decoded
        .whereType<Map>()
        .map((row) => MutedKeyword.fromJson(Map<String, dynamic>.from(row)))
        .where((keyword) => keyword.term.isNotEmpty)
        .toList(growable: false);
  } catch (_) {
    return const [];
  }
}

/// New writers always store JSON.
String encodeMutedKeywordsStored(List<MutedKeyword> keywords) {
  if (keywords.isEmpty) {
    return '';
  }
  return jsonEncode(keywords.map((keyword) => keyword.toJson()).toList(growable: false));
}

/// Drops keywords whose [until] is in the past.
List<MutedKeyword> activeMutedKeywords(List<MutedKeyword> keywords, {DateTime? now}) {
  final clock = now ?? DateTime.now();
  return keywords
      .where((keyword) {
        final until = keyword.until;
        return until == null || !until.isBefore(clock);
      })
      .toList(growable: false);
}

/// Splits what the user typed into plain terms (commas and newlines).
List<String> parseMutedKeywords(String? raw) {
  if (raw == null || raw.trim().isEmpty) {
    return const [];
  }
  return raw
      .split(RegExp(r'[,\n]'))
      .map((term) => term.trim())
      .where((term) => term.isNotEmpty)
      .toList(growable: false);
}

String joinMutedKeywords(List<String> keywords) => keywords.join(', ');

/// Alias used by antenna term columns and the muted-keyword text field.
List<String> parseMutedKeywordTerms(String? raw) => parseMutedKeywords(raw);
