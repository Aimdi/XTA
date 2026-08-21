import 'dart:convert';

import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/plugin_search_history.dart';

const hnLikedPostsCap = 400;
const hnSavedPostsCap = 200;
const hnFollowsCap = 80;

class HnFeedStore extends Store<List<HnStory>> {
  final HackerNewsClient client;
  final HnFeed feed;

  var _page = 0;
  var _hasMore = true;
  var _loadingMore = false;

  HnFeedStore(this.client, this.feed) : super(const []);

  bool get hasMore => _hasMore;

  Future<void> refresh() async {
    _page = 0;
    _hasMore = true;
    await execute(() async {
      final page = await client.feed(feed);
      _hasMore = page.hasMore;
      return page.stories;
    });
  }

  Future<void> loadMore() async {
    if (!_hasMore || _loadingMore || state.isEmpty) {
      return;
    }
    _loadingMore = true;
    try {
      final page = await client.feed(feed, page: _page + 1);
      _page = page.page;
      _hasMore = page.hasMore;
      update([...state, ...page.stories]);
    } finally {
      _loadingMore = false;
    }
  }
}

class HnFollowingStore extends Store<List<HnStory>> {
  final HackerNewsClient client;
  final HnFollowsStore follows;

  HnFollowingStore(this.client, this.follows) : super(const []);

  Future<void> refresh() async {
    await execute(() async {
      final authors = follows.state;
      if (authors.isEmpty) {
        return const <HnStory>[];
      }
      final pages = await Future.wait(
        authors.map((author) => client.submissions(author)),
      );
      final byId = <int, HnStory>{};
      for (final page in pages) {
        for (final story in page.stories) {
          byId[story.id] = story;
        }
      }
      final stories = byId.values.toList()
        ..sort((a, b) => _timeOf(b).compareTo(_timeOf(a)));
      return stories.take(hnPageSize * 2).toList(growable: false);
    });
  }

  DateTime _timeOf(HnStory story) =>
      story.createdAt ?? DateTime.fromMillisecondsSinceEpoch(0);
}

class HnLikesStore extends Store<Set<String>> {
  final BasePrefService prefs;

  HnLikesStore(this.prefs) : super(const {});

  Future<void> load() async => execute(() async => _read());

  Set<String> _read() =>
      _stringSet(prefs.get<String>(optionPluginHnLikedPosts));

  bool isLiked(int id) => state.contains('$id');

  Future<void> toggle(int id) async {
    final next = {...state};
    if (!next.add('$id')) {
      next.remove('$id');
    }
    final trimmed = next.take(hnLikedPostsCap).toSet();
    await prefs.set(optionPluginHnLikedPosts, jsonEncode(trimmed.toList()));
    update(trimmed);
  }
}

class HnSavedStore extends Store<List<HnStory>> {
  final BasePrefService prefs;

  HnSavedStore(this.prefs) : super(const []);

  Future<void> load() async => execute(() async => _read());

  List<HnStory> _read() {
    final raw = prefs.get<String>(optionPluginHnSavedPosts) ?? '[]';
    try {
      final decoded = jsonDecode(raw);
      if (decoded is! List) {
        return const [];
      }
      return [
        for (final item in decoded)
          if (item is Map) HnStory.fromJson(Map<String, Object?>.from(item)),
      ];
    } catch (_) {
      return const [];
    }
  }

  bool isSaved(int id) => state.any((story) => story.id == id);

  Future<void> toggle(HnStory story) async {
    final next = [
      if (!isSaved(story.id)) story,
      ...state.where((item) => item.id != story.id),
    ].take(hnSavedPostsCap).toList();
    await prefs.set(
      optionPluginHnSavedPosts,
      jsonEncode([for (final item in next) item.toJson()]),
    );
    update(next);
  }
}

class HnFollowsStore extends Store<List<String>> {
  final BasePrefService prefs;

  HnFollowsStore(this.prefs) : super(const []);

  Future<void> load() async => execute(() async => _read());

  List<String> _read() {
    final ids = _stringSet(prefs.get<String>(optionPluginHnFollows)).toList()
      ..sort();
    return ids;
  }

  bool isFollowing(String id) =>
      state.any((item) => item.toLowerCase() == id.toLowerCase());

  Future<void> toggle(String id) async {
    final key = id.trim();
    if (key.isEmpty) {
      return;
    }
    final next = isFollowing(key)
        ? [
            for (final item in state)
              if (item.toLowerCase() != key.toLowerCase()) item,
          ]
        : [key, ...state].take(hnFollowsCap).toList();
    await prefs.set(optionPluginHnFollows, jsonEncode(next));
    update(next);
  }
}

class HnSearchHistoryStore extends Store<List<String>> {
  final BasePrefService prefs;

  HnSearchHistoryStore(this.prefs) : super(const []);

  Future<void> load() async {
    update(readPluginSearchHistory(prefs, optionPluginHnSearchHistory));
  }

  Future<void> remember(String query) async {
    await rememberPluginSearch(prefs, optionPluginHnSearchHistory, query);
    await load();
  }
}

class HnCollapseStore extends Store<Set<int>> {
  HnCollapseStore() : super(const {});

  void toggle(int id) {
    final next = {...state};
    if (!next.add(id)) {
      next.remove(id);
    }
    update(next);
  }
}

Set<String> _stringSet(String? raw) {
  try {
    final decoded = jsonDecode(raw ?? '[]');
    if (decoded is List) {
      return {for (final id in decoded.whereType<String>()) id};
    }
  } catch (_) {}
  return const {};
}
