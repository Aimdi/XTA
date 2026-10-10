import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

/// The pattern inside a muted-tag entry written `r'pattern'`, or null for a
/// plain tag name.
String? pixivMutePattern(String entry) {
  final text = entry.trim();
  if (text.length < 4 || !text.startsWith("r'") || !text.endsWith("'")) {
    return null;
  }
  return text.substring(2, text.length - 1);
}

/// How a muted tag is stored: a pattern exactly as written (lower-casing would
/// change `\D` into `\d`), a plain name trimmed and lower-cased.
String pixivNormalizeMuteTag(String entry) {
  final text = entry.trim();
  return pixivMutePattern(text) == null ? text.toLowerCase() : text;
}

/// Whether [entry] can be muted: not blank, and a pattern that compiles.
bool pixivMuteTagValid(String entry) {
  final text = entry.trim();
  final pattern = pixivMutePattern(text);
  if (pattern == null) {
    return text.isNotEmpty;
  }
  try {
    RegExp(pattern);
    return true;
  } on FormatException {
    return false;
  }
}

/// Muted tags compiled once: plain names match a tag exactly, ignoring case;
/// patterns are tested against each tag and against every tag of the work
/// joined as `#a#b`, so one rule can ask for several tags together.
class PixivTagMatcher {
  final Set<String> names;
  final List<(String, RegExp)> patterns;

  const PixivTagMatcher._(this.names, this.patterns);

  factory PixivTagMatcher(Iterable<String> entries) => PixivTagMatcher._(
    {
      for (final entry in entries)
        if (pixivMutePattern(entry) == null) entry.toLowerCase(),
    },
    [
      for (final entry in entries)
        if (_compile(entry) case final pattern?) (entry, pattern),
    ],
  );

  static RegExp? _compile(String entry) {
    final pattern = pixivMutePattern(entry);
    if (pattern == null) return null;
    try {
      return RegExp(pattern, caseSensitive: false);
    } on FormatException {
      return null;
    }
  }

  /// The entry that hides a work with [tags], or null when none does.
  String? match(List<PixivTag> tags) {
    for (final tag in tags) {
      final name = tag.name.toLowerCase();
      if (names.contains(name)) return name;
    }
    if (patterns.isEmpty || tags.isEmpty) return null;
    final joined = tags.map((tag) => '#${tag.name}').join();
    for (final (entry, pattern) in patterns) {
      if (pattern.hasMatch(joined) || tags.any((tag) => pattern.hasMatch(tag.name))) return entry;
    }
    return null;
  }
}

final _matchers = Expando<PixivTagMatcher>();

class PixivMuteState {
  final Set<int> authorIds;

  /// The name each author had when muted, so the list can say who they are.
  /// Authors muted before names were kept have none.
  final Map<int, String> authorNames;
  final Set<String> tags;
  final Set<int> illustIds;
  final Set<int> commentIds;
  final Set<int> novelIds;

  const PixivMuteState({
    this.authorIds = const {},
    this.authorNames = const {},
    this.tags = const {},
    this.illustIds = const {},
    this.commentIds = const {},
    this.novelIds = const {},
  });

  static const empty = PixivMuteState();

  bool get isEmpty => authorIds.isEmpty && tags.isEmpty && illustIds.isEmpty && commentIds.isEmpty && novelIds.isEmpty;

  PixivTagMatcher get tagMatcher => _matchers[this] ??= PixivTagMatcher(tags);

  bool isCommentMuted(int id) => commentIds.contains(id);

  bool isNovelMuted(int id) => novelIds.contains(id);

  /// The muted tag entry that hides [illust], or null.
  String? mutedTagOf(PixivIllust illust) => tagMatcher.match(illust.tags);

  bool isMuted(PixivIllust illust) {
    return authorIds.contains(illust.userId) || illustIds.contains(illust.id) || mutedTagOf(illust) != null;
  }

  List<PixivIllust> filter(List<PixivIllust> illusts) {
    if (isEmpty) {
      return illusts;
    }
    return [
      for (final illust in illusts)
        if (!isMuted(illust)) illust,
    ];
  }

  PixivMuteState copyWith({
    Set<int>? authorIds,
    Map<int, String>? authorNames,
    Set<String>? tags,
    Set<int>? illustIds,
    Set<int>? commentIds,
    Set<int>? novelIds,
  }) {
    return PixivMuteState(
      authorIds: Set.unmodifiable(authorIds ?? this.authorIds),
      authorNames: Map.unmodifiable(authorNames ?? this.authorNames),
      tags: Set.unmodifiable(tags ?? this.tags),
      illustIds: Set.unmodifiable(illustIds ?? this.illustIds),
      commentIds: Set.unmodifiable(commentIds ?? this.commentIds),
      novelIds: Set.unmodifiable(novelIds ?? this.novelIds),
    );
  }
}

class PixivMuteStore extends Store<PixivMuteState> {
  final BasePrefService prefs;

  PixivMuteStore(this.prefs) : super(PixivMuteState.empty);

  Future<void> load() async {
    await execute(() async {
      final authors = readPixivMutedAuthors(prefs.get<String>(optionPluginPixivMutedAuthors));
      return PixivMuteState(
        authorIds: Set.unmodifiable(authors.keys),
        authorNames: Map.unmodifiable({
          for (final MapEntry(:key, :value) in authors.entries)
            if (value.isNotEmpty) key: value,
        }),
        tags: Set.unmodifiable(
          _readStringList(prefs.get<String>(optionPluginPixivMutedTags)).map(pixivNormalizeMuteTag),
        ),
        illustIds: _readIntSet(prefs.get<String>(optionPluginPixivMutedIllusts)),
        commentIds: _readIntSet(prefs.get<String>(optionPluginPixivMutedComments)),
        novelIds: _readIntSet(prefs.get<String>(optionPluginPixivMutedNovels)),
      );
    });
  }

  Future<void> muteAuthor(int id, {String name = ''}) => _write(
    authorIds: {...state.authorIds, id},
    authorNames: {...state.authorNames, if (name.trim().isNotEmpty) id: name.trim()},
  );

  Future<void> unmuteAuthor(int id) {
    return _write(authorIds: {...state.authorIds}..remove(id), authorNames: {...state.authorNames}..remove(id));
  }

  /// Mutes a tag name or an `r'pattern'`; false, and nothing saved, when the
  /// entry is blank or its pattern does not compile.
  Future<bool> muteTag(String tag) async {
    if (!pixivMuteTagValid(tag)) {
      return false;
    }
    await _write(tags: {...state.tags, pixivNormalizeMuteTag(tag)});
    return true;
  }

  Future<void> unmuteTag(String tag) {
    return _write(tags: {...state.tags}..remove(pixivNormalizeMuteTag(tag)));
  }

  Future<void> muteIllust(int id) => _write(illustIds: {...state.illustIds, id});

  Future<void> unmuteIllust(int id) {
    return _write(illustIds: {...state.illustIds}..remove(id));
  }

  Future<void> muteComment(int id) => _write(commentIds: {...state.commentIds, id});

  Future<void> unmuteComment(int id) => _write(commentIds: {...state.commentIds}..remove(id));

  Future<void> muteNovel(int id) => _write(novelIds: {...state.novelIds, id});

  Future<void> unmuteNovel(int id) => _write(novelIds: {...state.novelIds}..remove(id));

  bool isMuted(PixivIllust illust) => state.isMuted(illust);

  List<PixivIllust> filter(List<PixivIllust> illusts) => state.filter(illusts);

  Future<void> _write({
    Set<int>? authorIds,
    Map<int, String>? authorNames,
    Set<String>? tags,
    Set<int>? illustIds,
    Set<int>? commentIds,
    Set<int>? novelIds,
  }) async {
    final next = state.copyWith(
      authorIds: authorIds,
      authorNames: authorNames,
      tags: tags,
      illustIds: illustIds,
      commentIds: commentIds,
      novelIds: novelIds,
    );
    await _save(next);
    update(next);
  }

  Future<void> _save(PixivMuteState next) async {
    await prefs.set(optionPluginPixivMutedAuthors, jsonEncode(pixivMutedAuthorsJson(next)));
    await prefs.set(optionPluginPixivMutedTags, jsonEncode(next.tags.toList()..sort()));
    await prefs.set(optionPluginPixivMutedIllusts, jsonEncode(next.illustIds.toList()..sort()));
    await prefs.set(optionPluginPixivMutedComments, jsonEncode(next.commentIds.toList()..sort()));
    await prefs.set(optionPluginPixivMutedNovels, jsonEncode(next.novelIds.toList()..sort()));
  }
}

/// Muted authors by id with the name they were muted under ('' when unknown).
/// Reads both the old list of bare ids and the `{id, name}` objects.
Map<int, String> readPixivMutedAuthors(String? raw) => {
  for (final entry in _decodeList(raw))
    if (_intFrom(entry.raw) ?? _intFrom(entry['id'].raw) case final id? when id > 0)
      id: entry['name'].string?.trim() ?? '',
};

/// Muted authors as stored: `{id, name}` objects sorted by id.
List<Map<String, Object>> pixivMutedAuthorsJson(PixivMuteState state) => [
  for (final id in state.authorIds.toList()..sort()) {'id': id, 'name': state.authorNames[id] ?? ''},
];

Set<int> _readIntSet(String? raw) => Set.unmodifiable([for (final entry in _decodeList(raw)) ?_intFrom(entry.raw)]);

List<String> _readStringList(String? raw) => [
  for (final entry in _decodeList(raw))
    if (entry.string?.trim() case final text? when text.isNotEmpty) text,
];

List<Json> _decodeList(String? raw) {
  try {
    return Json(jsonDecode(raw ?? '[]')).list;
  } on FormatException {
    return const [];
  }
}

int? _intFrom(Object? value) {
  return switch (value) {
    int id => id,
    String text => int.tryParse(text.trim()),
    _ => null,
  };
}
