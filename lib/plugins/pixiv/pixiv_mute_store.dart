import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

class PixivMuteState {
  final Set<int> authorIds;
  final Set<String> tags;
  final Set<int> illustIds;
  final Set<int> commentIds;
  final Set<int> novelIds;

  const PixivMuteState({
    this.authorIds = const {},
    this.tags = const {},
    this.illustIds = const {},
    this.commentIds = const {},
    this.novelIds = const {},
  });

  static const empty = PixivMuteState();

  bool get isEmpty =>
      authorIds.isEmpty && tags.isEmpty && illustIds.isEmpty && commentIds.isEmpty && novelIds.isEmpty;

  bool isCommentMuted(int id) => commentIds.contains(id);

  bool isNovelMuted(int id) => novelIds.contains(id);

  bool isMuted(PixivIllust illust) {
    return authorIds.contains(illust.userId) ||
        illustIds.contains(illust.id) ||
        illust.tags.any((tag) => tags.contains(tag.name.toLowerCase()));
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
    Set<String>? tags,
    Set<int>? illustIds,
    Set<int>? commentIds,
    Set<int>? novelIds,
  }) {
    return PixivMuteState(
      authorIds: Set.unmodifiable(authorIds ?? this.authorIds),
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
      return PixivMuteState(
        authorIds: _readIntSet(
          prefs.get<String>(optionPluginPixivMutedAuthors),
        ),
        tags: _readStringSet(
          prefs.get<String>(optionPluginPixivMutedTags),
          lowerCase: true,
        ),
        illustIds: _readIntSet(
          prefs.get<String>(optionPluginPixivMutedIllusts),
        ),
        commentIds: _readIntSet(prefs.get<String>(optionPluginPixivMutedComments)),
        novelIds: _readIntSet(prefs.get<String>(optionPluginPixivMutedNovels)),
      );
    });
  }

  Future<void> muteAuthor(int id) =>
      _write(authorIds: {...state.authorIds, id});

  Future<void> unmuteAuthor(int id) {
    return _write(authorIds: {...state.authorIds}..remove(id));
  }

  Future<void> muteTag(String tag) {
    final normalized = tag.trim().toLowerCase();
    if (normalized.isEmpty) {
      return Future.value();
    }
    return _write(tags: {...state.tags, normalized});
  }

  Future<void> unmuteTag(String tag) {
    return _write(tags: {...state.tags}..remove(tag.trim().toLowerCase()));
  }

  Future<void> muteIllust(int id) =>
      _write(illustIds: {...state.illustIds, id});

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
    Set<String>? tags,
    Set<int>? illustIds,
    Set<int>? commentIds,
    Set<int>? novelIds,
  }) async {
    final next = state.copyWith(
      authorIds: authorIds,
      tags: tags,
      illustIds: illustIds,
      commentIds: commentIds,
      novelIds: novelIds,
    );
    await _save(next);
    update(next);
  }

  Future<void> _save(PixivMuteState next) async {
    await prefs.set(
      optionPluginPixivMutedAuthors,
      jsonEncode(next.authorIds.toList()..sort()),
    );
    await prefs.set(
      optionPluginPixivMutedTags,
      jsonEncode(next.tags.toList()..sort()),
    );
    await prefs.set(
      optionPluginPixivMutedIllusts,
      jsonEncode(next.illustIds.toList()..sort()),
    );
    await prefs.set(optionPluginPixivMutedComments, jsonEncode(next.commentIds.toList()..sort()));
    await prefs.set(optionPluginPixivMutedNovels, jsonEncode(next.novelIds.toList()..sort()));
  }
}

Set<int> _readIntSet(String? raw) => Set.unmodifiable(_readIntList(raw));

Set<String> _readStringSet(String? raw, {bool lowerCase = false}) {
  return Set.unmodifiable(_readStringList(raw, lowerCase: lowerCase));
}

List<int> _readIntList(String? raw) {
  try {
    final decoded = jsonDecode(raw ?? '[]');
    if (decoded is! List) {
      return const [];
    }
    return [for (final value in decoded) ?_intFrom(value)];
  } catch (_) {
    return const [];
  }
}

List<String> _readStringList(String? raw, {bool lowerCase = false}) {
  try {
    final decoded = jsonDecode(raw ?? '[]');
    if (decoded is! List) {
      return const [];
    }
    return [
      for (final value in decoded.whereType<String>())
        if (value.trim() case final text when text.isNotEmpty)
          lowerCase ? text.toLowerCase() : text,
    ];
  } catch (_) {
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
