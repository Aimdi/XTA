import 'package:flutter_triple/flutter_triple.dart';
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
    if (!_hasMore || _loadingMore || state.isEmpty) return;
    _loadingMore = true;
    try {
      final next = _page + 1;
      final page = await loader(page: next);
      _page = next;
      _hasMore = page.hasMore;
      update([...state, ...page.posts]);
    } catch (_) {
      // Keep what we have; the reader can pull-to-refresh.
    } finally {
      _loadingMore = false;
    }
  }
}
