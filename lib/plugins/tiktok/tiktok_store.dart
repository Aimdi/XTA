import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/tiktok/tiktok_client.dart';
import 'package:xta/plugins/tiktok/tiktok_models.dart';

const tiktokMaxFollowsPerLoad = 20;
const tiktokSearchHistoryCap = 20;
const tiktokLikedPostsCap = 200;

/// Local TikTok follows — device only, never written back to TikTok.
class TikTokFollowsStore extends Store<List<TikTokFollow>> {
  TikTokFollowsStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<TikTokFollow>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableTiktokSubscription,
      orderBy: 'created_at DESC',
    );
    return rows.map(TikTokFollow.fromMap).toList();
  }

  bool containsHandle(String handle) {
    final key = handle.toLowerCase();
    return state.any((f) => f.id == key);
  }

  Future<void> follow(TikTokProfile profile) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.insert(
        tableTiktokSubscription,
        TikTokFollow.fromProfile(profile).toMap(),
        conflictAlgorithm: ConflictAlgorithm.replace,
      );
      return _read();
    });
  }

  Future<void> unfollow(String handle) async {
    await execute(() async {
      final database = await Repository.writable();
      await database.delete(
        tableTiktokSubscription,
        where: 'id = ?',
        whereArgs: [handle.toLowerCase()],
      );
      return _read();
    });
  }
}

class TikTokFollow with ToMappable {
  final String id;
  final String secUid;
  final String name;
  final String? avatarUrl;
  final String? signature;
  final DateTime createdAt;

  TikTokFollow({
    required this.id,
    required this.secUid,
    required this.name,
    this.avatarUrl,
    this.signature,
    required this.createdAt,
  });

  factory TikTokFollow.fromProfile(TikTokProfile profile) {
    return TikTokFollow(
      id: profile.uniqueId.toLowerCase(),
      secUid: profile.secUid,
      name: profile.displayName,
      avatarUrl: profile.avatarUrl,
      signature: profile.signature,
      createdAt: DateTime.now(),
    );
  }

  factory TikTokFollow.fromMap(Map<String, Object?> map) {
    return TikTokFollow(
      id: map['id'] as String,
      secUid: map['sec_uid'] as String,
      name: map['name'] as String,
      avatarUrl: map['avatar_url'] as String?,
      signature: map['signature'] as String?,
      createdAt: map['created_at'] == null
          ? DateTime.now()
          : DateTime.tryParse(map['created_at'] as String) ?? DateTime.now(),
    );
  }

  @override
  Map<String, dynamic> toMap() => {
    'id': id,
    'sec_uid': secUid,
    'name': name,
    'avatar_url': avatarUrl,
    'signature': signature,
    'in_feed': 1,
    'created_at': createdAt.toIso8601String(),
  };
}

/// Device-local hearts — nothing is sent to TikTok.
class TikTokLikesStore extends Store<Set<String>> {
  final BasePrefService prefs;

  TikTokLikesStore(this.prefs) : super(const {});

  Future<void> load() async {
    await execute(() async => _read());
  }

  Set<String> _read() {
    final raw = prefs.get<String>(optionPluginTiktokLikedPosts) ?? '[]';
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return {for (final id in decoded.whereType<String>()) id};
      }
    } catch (_) {}
    return const {};
  }

  bool isLiked(String id) => state.contains(id);

  Future<void> toggle(String id) async {
    final next = {...state};
    if (!next.add(id)) next.remove(id);
    final trimmed = next.take(tiktokLikedPostsCap).toSet();
    await prefs.set(optionPluginTiktokLikedPosts, jsonEncode(trimmed.toList()));
    update(trimmed);
  }
}

class TikTokSearchHistoryStore extends Store<List<String>> {
  final BasePrefService prefs;

  TikTokSearchHistoryStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(() async => _read());
  }

  List<String> _read() {
    final raw = prefs.get<String>(optionPluginTiktokSearchHistory) ?? '[]';
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return [for (final h in decoded.whereType<String>()) h];
      }
    } catch (_) {}
    return const [];
  }

  Future<void> remember(String handle) async {
    final key = handle.toLowerCase();
    final next = [
      key,
      ...state.where((h) => h != key),
    ].take(tiktokSearchHistoryCap).toList();
    await prefs.set(optionPluginTiktokSearchHistory, jsonEncode(next));
    update(next);
  }
}

/// Paginated posts for one profile.
class TikTokFeedStore extends Store<List<TikTokPost>> {
  final Future<TikTokItemPage> Function({String? cursor}) loader;

  String? _cursor;
  var _hasMore = true;
  var _loadingMore = false;

  TikTokFeedStore(this.loader) : super(const []);

  bool get hasMore => _hasMore;
  bool get loadingMore => _loadingMore;

  Future<void> refresh() async {
    _cursor = null;
    _hasMore = true;
    await execute(() async {
      final page = await loader(cursor: null);
      _cursor = page.cursor;
      _hasMore = page.hasMore;
      return page.posts;
    });
  }

  Future<void> loadMore() async {
    if (!_hasMore || _loadingMore || _cursor == null) return;
    _loadingMore = true;
    try {
      final page = await loader(cursor: _cursor);
      _cursor = page.cursor;
      _hasMore = page.hasMore;
      if (page.posts.isNotEmpty) {
        update(_dedupe([...state, ...page.posts]));
      }
    } catch (_) {
    } finally {
      _loadingMore = false;
    }
  }
}

/// Merged local-following feed — one unsigned list request per followed account.
class TikTokFollowingStore extends Store<List<TikTokPost>> {
  final TikTokClient client;
  final TikTokFollowsStore follows;

  final Map<String, String?> _cursors = {};
  final Map<String, bool> _more = {};
  var _loadingMore = false;

  TikTokFollowingStore(this.client, this.follows) : super(const []);

  bool get hasMore => _more.values.any((v) => v);
  bool get loadingMore => _loadingMore;

  Future<void> refresh() async {
    await execute(() async {
      _cursors.clear();
      _more.clear();
      if (follows.state.isEmpty) await follows.load();
      return _fetchRound(initial: true);
    });
  }

  Future<void> loadMore() async {
    if (!hasMore || _loadingMore) return;
    _loadingMore = true;
    try {
      final extra = await _fetchRound(initial: false);
      if (extra.isNotEmpty) update(_dedupe([...state, ...extra]));
    } catch (_) {
    } finally {
      _loadingMore = false;
    }
  }

  Future<List<TikTokPost>> _fetchRound({required bool initial}) async {
    final posts = <TikTokPost>[];
    final accounts = follows.state.take(tiktokMaxFollowsPerLoad);
    for (final account in accounts) {
      if (!initial && _more[account.secUid] != true) continue;
      try {
        final page = await client.creatorItems(
          secUid: account.secUid,
          cursor: initial ? null : _cursors[account.secUid],
        );
        posts.addAll(page.posts);
        _cursors[account.secUid] = page.cursor;
        _more[account.secUid] = page.hasMore;
      } catch (_) {
        _more[account.secUid] = false;
      }
    }
    posts.sort((a, b) => b.createdAt.compareTo(a.createdAt));
    return _dedupe(posts);
  }
}

List<TikTokPost> _dedupe(List<TikTokPost> posts) {
  final seen = <String>{};
  return [
    for (final post in posts)
      if (seen.add(post.id)) post,
  ];
}
