import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/utils/json.dart';

/// Pixiv matches tags without regard to case, so `Miku` and `miku` are one favourite.
bool pixivSameTag(String a, String b) => a.trim().toLowerCase() == b.trim().toLowerCase();

/// The favourite tags kept in prefs, in the reader's order. A bare list of
/// names reads too; anything else reads as none.
List<PixivTag> readPixivFavoriteTags(BasePrefService prefs) {
  final Object? decoded;
  try {
    decoded = jsonDecode(prefs.get<String>(optionPluginPixivFavoriteTags) ?? '[]');
  } on FormatException {
    return const [];
  }
  return [
    for (final entry in Json(decoded).list)
      if ((entry.string ?? entry['name'].string)?.trim() case final name? when name.isNotEmpty)
        PixivTag(name: name, translatedName: entry['translated_name'].string?.trim()),
  ];
}

Future<void> savePixivFavoriteTags(BasePrefService prefs, List<PixivTag> tags) async {
  await prefs.set(
    optionPluginPixivFavoriteTags,
    jsonEncode([
      for (final tag in tags) {'name': tag.name, 'translated_name': ?tag.translatedName},
    ]),
  );
}

/// [items] with the one at [from] moved so it ends up at index [to].
List<T> pixivMoved<T>(List<T> items, int from, int to) {
  if (from < 0 || from >= items.length) return items;
  final rest = [...items]..removeAt(from);
  final at = to.clamp(0, rest.length);
  return [...rest.take(at), items[from], ...rest.skip(at)];
}

/// Tags pinned as saved searches. The settings backup carries them with the
/// other preferences.
class PixivFavoriteTagsStore extends Store<List<PixivTag>> {
  final BasePrefService prefs;

  PixivFavoriteTagsStore(this.prefs) : super(readPixivFavoriteTags(prefs));

  void load() => update(readPixivFavoriteTags(prefs));

  bool contains(String name) => state.any((tag) => pixivSameTag(tag.name, name));

  /// Adds [tag] at the end, replacing an entry that differs only in case.
  Future<void> add(PixivTag tag) => _save([
    for (final kept in state)
      if (!pixivSameTag(kept.name, tag.name)) kept,
    tag,
  ]);

  Future<void> remove(String name) => _save([
    for (final kept in state)
      if (!pixivSameTag(kept.name, name)) kept,
  ]);

  Future<void> reorder(int from, int to) => _save(pixivMoved(state, from, to));

  /// Shows the change before writing it: a swiped-away row must leave the
  /// list in the same frame, not after the write.
  Future<void> _save(List<PixivTag> tags) async {
    update(tags);
    await savePixivFavoriteTags(prefs, tags);
  }
}
