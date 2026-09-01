import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';

const pixivSearchHistoryCap = 20;

class PixivMuteState {
  final Set<int> authorIds;
  final Set<String> tags;
  final Set<int> illustIds;

  const PixivMuteState({
    this.authorIds = const {},
    this.tags = const {},
    this.illustIds = const {},
  });

  static const empty = PixivMuteState();

  bool get isEmpty => authorIds.isEmpty && tags.isEmpty && illustIds.isEmpty;

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
  }) {
    return PixivMuteState(
      authorIds: Set.unmodifiable(authorIds ?? this.authorIds),
      tags: Set.unmodifiable(tags ?? this.tags),
      illustIds: Set.unmodifiable(illustIds ?? this.illustIds),
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

  bool isMuted(PixivIllust illust) => state.isMuted(illust);

  List<PixivIllust> filter(List<PixivIllust> illusts) => state.filter(illusts);

  Future<void> _write({
    Set<int>? authorIds,
    Set<String>? tags,
    Set<int>? illustIds,
  }) async {
    final next = state.copyWith(
      authorIds: authorIds,
      tags: tags,
      illustIds: illustIds,
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
  }
}

class PixivSearchHistoryStore extends Store<List<String>> {
  final BasePrefService prefs;

  PixivSearchHistoryStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(() async {
      return _readStringList(
        prefs.get<String>(optionPluginPixivSearchHistory),
      ).take(pixivSearchHistoryCap).toList();
    });
  }

  Future<void> add(String query) async {
    final trimmed = query.trim();
    if (trimmed.isEmpty) {
      return;
    }
    final next = [
      trimmed,
      for (final existing in state)
        if (existing.toLowerCase() != trimmed.toLowerCase()) existing,
    ].take(pixivSearchHistoryCap).toList(growable: false);
    await prefs.set(optionPluginPixivSearchHistory, jsonEncode(next));
    update(next);
  }

  Future<void> remove(String query) async {
    final next = [
      for (final existing in state)
        if (existing != query) existing,
    ];
    await prefs.set(optionPluginPixivSearchHistory, jsonEncode(next));
    update(next);
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
