import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:sqflite/sqflite.dart';

/// Followed tags, stored so they can be group members.
class BooruTagsStore extends Store<List<String>> {
  BooruTagsStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<String>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableBooruSubscription,
      orderBy: 'name COLLATE NOCASE',
    );
    return rows.map((e) => e['name'] as String).toList(growable: false);
  }

  Future<void> add(String tag) async {
    await execute(() async {
      await _write(tag);
      return _read();
    });
  }

  Future<void> remove(String tag) async {
    await execute(() async {
      final id = normaliseBooruTag(tag);
      if (id == null) return _read();
      final database = await Repository.writable();
      await database.delete(
        tableBooruSubscription,
        where: 'id = ?',
        whereArgs: [id],
      );
      await database.delete(
        tableSubscriptionGroupMember,
        where: 'profile_id = ?',
        whereArgs: [id],
      );
      return _read();
    });
  }

  Future<void> _write(String tag) async {
    final normalised = normaliseBooruTag(tag);
    if (normalised == null) return;

    final database = await Repository.writable();
    await database.insert(
      tableBooruSubscription,
      BooruSubscription(
        id: normalised,
        name: normalised,
        createdAt: DateTime.now(),
        inFeed: true,
      ).toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }
}

/// Local mute list — preference-backed, like Pixiv muted tags.
class BooruMuteStore extends Store<Set<String>> {
  final BasePrefService prefs;

  BooruMuteStore(this.prefs) : super(const {});

  Future<void> load() async {
    await execute(() async => _read());
  }

  Set<String> _read() {
    final raw = prefs.get<String>(optionPluginBooruMutedTags) ?? '[]';
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return {
          for (final tag in decoded.whereType<String>())
            ?normaliseBooruTag(tag),
        };
      }
    } catch (_) {}
    return const {};
  }

  Future<void> _persist(Set<String> tags) async {
    final sorted = tags.toList()..sort();
    await prefs.set(optionPluginBooruMutedTags, jsonEncode(sorted));
    update(tags);
  }

  Future<void> mute(String tag) async {
    final normalised = normaliseBooruTag(tag);
    if (normalised == null) return;
    await _persist({...state, normalised});
  }

  Future<void> unmute(String tag) async {
    final normalised = normaliseBooruTag(tag);
    if (normalised == null) return;
    await _persist({
      for (final t in state)
        if (t != normalised) t,
    });
  }
}

/// Paginated post list for Latest / Search / Following.
class BooruFeedStore extends Store<List<BooruPost>> {
  final BooruClient client;
  final Future<BooruPostPage> Function({required int page}) loader;

  var _page = 0;
  var _hasMore = true;
  var _loadingMore = false;

  BooruFeedStore(this.client, this.loader) : super(const []);

  bool get hasMore => _hasMore;
  bool get loadingMore => _loadingMore;

  Future<void> refresh() async {
    _page = 0;
    _hasMore = true;
    await execute(() async {
      final page = await loader(page: 1);
      _page = 1;
      _hasMore = page.hasMore;
      return page.posts;
    });
  }

  Future<void> loadMore() async {
    if (!_hasMore || _loadingMore) return;
    _loadingMore = true;
    try {
      // Skip empty filtered pages while the API still has more raw results.
      for (var attempt = 0; attempt < 3 && _hasMore; attempt++) {
        final next = _page + 1;
        final page = await loader(page: next);
        _page = next;
        _hasMore = page.hasMore;
        if (page.posts.isEmpty) {
          if (!_hasMore) break;
          continue;
        }
        update([...state, ...page.posts]);
        break;
      }
    } catch (_) {
      // Keep what we have; the reader can pull-to-refresh.
    } finally {
      _loadingMore = false;
    }
  }
}
