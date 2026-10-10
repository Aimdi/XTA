import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_novel_models.dart';
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
/// change `\D` into `\d`), a plain name trimmed and lower-cased. The `#` the
/// mute list shows before a name is dropped, so typing `#cat` mutes `cat`.
String pixivNormalizeMuteTag(String entry) {
  final text = entry.trim();
  if (pixivMutePattern(text) != null) return text;
  return (text.startsWith('#') ? text.substring(1) : text).trim().toLowerCase();
}

/// Whether [entry] can be muted: not blank, and a pattern that compiles.
bool pixivMuteTagValid(String entry) {
  final text = entry.trim();
  final pattern = pixivMutePattern(text);
  if (pattern == null) {
    return pixivNormalizeMuteTag(text).isNotEmpty;
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

  /// [items] without those by a muted author, [authorOf] naming each one's.
  List<T> withoutMutedAuthors<T>(List<T> items, int Function(T item) authorOf) => authorIds.isEmpty
      ? items
      : [
          for (final item in items)
            if (!authorIds.contains(authorOf(item))) item,
        ];

  /// Whether a muted author or tag, or the novel's own id, hides [novel].
  bool hidesNovel(PixivNovel novel) =>
      authorIds.contains(novel.user.id) || novelIds.contains(novel.id) || tagMatcher.match(novel.tags) != null;

  List<PixivNovel> filterNovels(List<PixivNovel> novels) => filterNovelsOf(novels, (novel) => novel);

  /// [items] without those whose novel ([novelOf]) the mutes hide, such as a series' chapters.
  List<T> filterNovelsOf<T>(List<T> items, PixivNovel Function(T item) novelOf) {
    if (isEmpty) {
      return items;
    }
    return [
      for (final item in items)
        if (!hidesNovel(novelOf(item))) item,
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

/// The reader's mutes. Every change builds on what is stored rather than on
/// [state], so mutes a settings import or sync just wrote are never saved over.
class PixivMuteStore extends Store<PixivMuteState> {
  final BasePrefService prefs;

  PixivMuteStore(this.prefs) : super(PixivMuteState.empty);

  /// Reads the stored mutes again. Preferences answer at once, so there is no
  /// loading state to show, and a caller awaiting it never waits on a frame.
  Future<void> load() async => update(_read());

  PixivMuteState _read() {
    final authors = readPixivMutedAuthors(prefs.get<String>(optionPluginPixivMutedAuthors));
    return PixivMuteState(
      authorIds: Set.unmodifiable(authors.keys),
      authorNames: Map.unmodifiable({
        for (final MapEntry(:key, :value) in authors.entries)
          if (value.isNotEmpty) key: value,
      }),
      tags: Set.unmodifiable(_readStringList(prefs.get<String>(optionPluginPixivMutedTags)).map(pixivNormalizeMuteTag)),
      illustIds: _readIntSet(prefs.get<String>(optionPluginPixivMutedIllusts)),
      commentIds: _readIntSet(prefs.get<String>(optionPluginPixivMutedComments)),
      novelIds: _readIntSet(prefs.get<String>(optionPluginPixivMutedNovels)),
    );
  }

  Future<void> muteAuthor(int id, {String name = ''}) => _write(
    (mute) => mute.copyWith(
      authorIds: {...mute.authorIds, id},
      authorNames: {...mute.authorNames, if (name.trim().isNotEmpty) id: name.trim()},
    ),
  );

  Future<void> unmuteAuthor(int id) => _write(
    (mute) => mute.copyWith(authorIds: {...mute.authorIds}..remove(id), authorNames: {...mute.authorNames}..remove(id)),
  );

  /// Mutes a tag name or an `r'pattern'`; false, and nothing saved, when the
  /// entry is blank or its pattern does not compile.
  Future<bool> muteTag(String tag) async {
    if (!pixivMuteTagValid(tag)) {
      return false;
    }
    await _write((mute) => mute.copyWith(tags: {...mute.tags, pixivNormalizeMuteTag(tag)}));
    return true;
  }

  Future<void> unmuteTag(String tag) =>
      _write((mute) => mute.copyWith(tags: {...mute.tags}..remove(pixivNormalizeMuteTag(tag))));

  Future<void> muteIllust(int id) => _write((mute) => mute.copyWith(illustIds: {...mute.illustIds, id}));

  Future<void> unmuteIllust(int id) => _write((mute) => mute.copyWith(illustIds: {...mute.illustIds}..remove(id)));

  Future<void> muteComment(int id) => _write((mute) => mute.copyWith(commentIds: {...mute.commentIds, id}));

  Future<void> unmuteComment(int id) => _write((mute) => mute.copyWith(commentIds: {...mute.commentIds}..remove(id)));

  Future<void> muteNovel(int id) => _write((mute) => mute.copyWith(novelIds: {...mute.novelIds, id}));

  Future<void> unmuteNovel(int id) => _write((mute) => mute.copyWith(novelIds: {...mute.novelIds}..remove(id)));

  bool isMuted(PixivIllust illust) => state.isMuted(illust);

  List<PixivIllust> filter(List<PixivIllust> illusts) => state.filter(illusts);

  List<PixivNovel> filterNovels(List<PixivNovel> novels) => state.filterNovels(novels);

  Future<void> _write(PixivMuteState Function(PixivMuteState stored) change) async {
    final next = change(_read());
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
