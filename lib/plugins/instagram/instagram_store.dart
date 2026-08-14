import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:sqflite/sqflite.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:xta/plugins/account_posts.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';

const instagramMaxFollowsPerLoad = 20;
const instagramSearchHistoryCap = 20;
const instagramLikedPostsCap = 200;

class InstagramFollowsStore extends Store<List<InstagramFollow>> {
  InstagramFollowsStore() : super(const []);

  Future<void> load() async {
    await execute(_read);
  }

  Future<List<InstagramFollow>> _read() async {
    final database = await Repository.readOnly();
    final rows = await database.query(
      tableInstagramSubscription,
      orderBy: 'created_at DESC',
    );
    return rows.map(InstagramFollow.fromMap).toList();
  }

  bool containsHandle(String handle) {
    final key = handle.toLowerCase();
    return state.any((f) => f.id == key);
  }

  Future<void> follow(InstagramProfile profile) async {
    final next = InstagramFollow.fromProfile(profile);
    update([next, ...state.where((follow) => follow.id != next.id)]);
    final database = await Repository.writable();
    await database.insert(
      tableInstagramSubscription,
      next.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> followAuthor(InstagramAuthor author, {String pk = ''}) async {
    final handle = author.username.trim().toLowerCase();
    if (handle.isEmpty) return;
    final next = InstagramFollow(
      id: handle,
      pk: pk.isEmpty ? handle : pk,
      name: author.displayName,
      avatarUrl: author.avatarUrl,
      createdAt: DateTime.now(),
    );
    update([next, ...state.where((follow) => follow.id != next.id)]);
    final database = await Repository.writable();
    await database.insert(
      tableInstagramSubscription,
      next.toMap(),
      conflictAlgorithm: ConflictAlgorithm.replace,
    );
  }

  Future<void> unfollow(String handle) async {
    final key = handle.toLowerCase();
    update(state.where((follow) => follow.id != key).toList());
    final database = await Repository.writable();
    await database.delete(
      tableInstagramSubscription,
      where: 'id = ?',
      whereArgs: [key],
    );
  }
}

class InstagramFollow with ToMappable {
  final String id;
  final String pk;
  final String name;
  final String? avatarUrl;
  final String? signature;
  final DateTime createdAt;

  InstagramFollow({
    required this.id,
    required this.pk,
    required this.name,
    this.avatarUrl,
    this.signature,
    required this.createdAt,
  });

  factory InstagramFollow.fromProfile(InstagramProfile profile) {
    return InstagramFollow(
      id: profile.username.toLowerCase(),
      pk: profile.id,
      name: profile.displayName,
      avatarUrl: profile.avatarUrl,
      signature: profile.biography,
      createdAt: DateTime.now(),
    );
  }

  factory InstagramFollow.fromMap(Map<String, Object?> map) {
    return InstagramFollow(
      id: map['id'] as String,
      pk: map['pk'] as String,
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
    'pk': pk,
    'name': name,
    'avatar_url': avatarUrl,
    'signature': signature,
    'in_feed': 1,
    'created_at': createdAt.toIso8601String(),
  };
}

class InstagramLikesStore extends Store<Set<String>> {
  final BasePrefService prefs;

  InstagramLikesStore(this.prefs) : super(const {});

  Future<void> load() async {
    await execute(() async => _read());
  }

  Set<String> _read() {
    final raw = prefs.get<String>(optionPluginInstagramLikedPosts) ?? '[]';
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
    final trimmed = next.take(instagramLikedPostsCap).toSet();
    await prefs.set(
      optionPluginInstagramLikedPosts,
      jsonEncode(trimmed.toList()),
    );
    update(trimmed);
  }
}

class InstagramSearchHistoryStore extends Store<List<String>> {
  final BasePrefService prefs;

  InstagramSearchHistoryStore(this.prefs) : super(const []);

  Future<void> load() async {
    await execute(() async => _read());
  }

  List<String> _read() {
    final raw = prefs.get<String>(optionPluginInstagramSearchHistory) ?? '[]';
    try {
      final decoded = jsonDecode(raw);
      if (decoded is List) {
        return [for (final h in decoded.whereType<String>()) h];
      }
    } catch (_) {}
    return const [];
  }

  Future<void> remember(String handle) async {
    final key = handle.trim().replaceFirst(RegExp(r'^@'), '').toLowerCase();
    if (key.isEmpty) return;
    final next = [
      key,
      ...state.where((h) => h != key),
    ].take(instagramSearchHistoryCap).toList();
    await prefs.set(optionPluginInstagramSearchHistory, jsonEncode(next));
    update(next);
  }

  Future<void> clear() async {
    await prefs.set(optionPluginInstagramSearchHistory, '[]');
    update(const []);
  }
}

class InstagramFeedStore extends Store<List<InstagramPost>> {
  final Future<InstagramItemPage> Function({String? cursor}) loader;

  String? _cursor;
  var _hasMore = true;
  var _loadingMore = false;

  InstagramFeedStore(this.loader) : super(const []);

  bool get hasMore => _hasMore;
  bool get loadingMore => _loadingMore;

  Future<void> refresh() async {
    _cursor = null;
    _hasMore = true;
    if (state.isNotEmpty) {
      try {
        final page = await loader(cursor: null);
        _cursor = page.cursor;
        _hasMore = page.hasMore;
        update(page.posts);
      } catch (_) {
        update(state);
      }
      return;
    }
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

class InstagramFollowingStore extends Store<List<InstagramPost>> {
  final InstagramClient client;
  final InstagramFollowsStore follows;

  final _posts = AccountPostCache<InstagramPost>(
    dateOf: (post) => post.createdAt,
    perAccount: 12,
    concurrency: 2,
  );

  InstagramFollowingStore(this.client, this.follows) : super(const []);

  Future<void> refresh({bool force = false}) async {
    if (follows.state.isEmpty) await follows.load();
    if (state.isNotEmpty) {
      try {
        update(await _load(force: force, onPartial: update));
      } catch (_) {
        update(state);
      }
      return;
    }
    await execute(() => _load(force: force, onPartial: update));
  }

  Future<List<InstagramPost>> _load({
    required bool force,
    void Function(List<InstagramPost>)? onPartial,
  }) {
    final keys = follows.state
        .map((follow) => follow.id)
        .toList(growable: false);
    return _posts.merge(
      keys,
      (key) async => (await client.profileMedia(key)).posts,
      forceRefresh: force,
      maxFetches: instagramMaxFollowsPerLoad,
      onPartial: onPartial,
    );
  }
}

class InstagramForYouStore extends InstagramFeedStore {
  InstagramForYouStore(InstagramClient client)
    : super(({cursor}) => client.forYou(cursor: cursor));
}

class InstagramProfileStore extends Store<InstagramProfile?> {
  final InstagramClient client;
  final String handle;

  InstagramProfileStore(this.client, this.handle) : super(null);

  Future<void> load() async {
    if (state != null) {
      try {
        update(await client.profile(handle));
      } catch (_) {
        update(state);
      }
      return;
    }
    await execute(() => client.profile(handle));
  }
}

List<InstagramPost> _dedupe(List<InstagramPost> posts) {
  final seen = <String>{};
  return [
    for (final post in posts)
      if (seen.add(post.id)) post,
  ];
}
