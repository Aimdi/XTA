import 'dart:async';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/tweet/feed_snapshot_cache.dart';
import 'package:xta/tweet/interleaved_items.dart';

typedef SourceLoader = Future<List<InterleavedItem>> Function();

class FeedSourceState {
  final bool loading;
  final DateTime? cachedAt;
  final Object? error;
  const FeedSourceState({this.loading = false, this.cachedAt, this.error});
}

class ProgressiveFeedState {
  final Map<String, List<InterleavedItem>> visible;
  final Map<String, List<InterleavedItem>>? pending;
  final Map<String, FeedSourceState> sources;
  const ProgressiveFeedState({this.visible = const {}, this.pending, this.sources = const {}});
  List<InterleavedItem> get items =>
      [for (final posts in visible.values) ...posts]..sort((a, b) => b.date.compareTo(a.date));
  bool get hasPending => pending != null;
}

class ProgressiveFeedStore extends Store<ProgressiveFeedState> {
  final FeedSnapshotCache cache;
  final Duration timeout;
  final _runs = <String, int>{};
  Map<String, SourceLoader> _loaders = {};
  Map<String, String> _keys = {};
  bool _away = false;
  bool _closed = false;
  ProgressiveFeedStore({FeedSnapshotCache? cache, this.timeout = const Duration(seconds: 30)})
    : cache = cache ?? FeedSnapshotCache(),
      super(const ProgressiveFeedState());

  void setReadingAway(bool away) {
    _away = away;
  }

  void reveal() {
    final pending = state.pending;
    if (pending != null) update(ProgressiveFeedState(visible: pending, sources: state.sources));
  }

  Future<void> load(Map<String, SourceLoader> loaders, Map<String, String> keys) async {
    final retained = {
      for (final key in loaders.keys)
        if (_keys[key] == keys[key] && state.visible.containsKey(key)) key: state.visible[key]!,
    };
    for (final key in _runs.keys.toList()) {
      _runs[key] = _runs[key]! + 1;
    }
    _loaders = loaders;
    _keys = keys;
    if (_closed) return;
    update(
      ProgressiveFeedState(
        visible: retained,
        sources: {for (final id in loaders.keys) id: const FeedSourceState(loading: true)},
      ),
    );
    await Future.wait(loaders.keys.map(retry));
  }

  Future<void> retry(String source) async {
    final loader = _loaders[source];
    final key = _keys[source];
    if (_closed || loader == null || key == null) return;
    final run = (_runs[source] ?? 0) + 1;
    _runs[source] = run;
    bool current() => !_closed && _runs[source] == run && _keys[source] == key;
    _status(source, FeedSourceState(loading: true, cachedAt: state.sources[source]?.cachedAt));
    if (!state.visible.containsKey(source)) {
      final cached = await cache.read(key);
      if (!current()) return;
      if (cached.items.isNotEmpty) _accept(source, cached.items, FeedSourceState(loading: true, cachedAt: cached.at));
    }
    try {
      final posts = await loader().timeout(timeout);
      if (!current()) return;
      _accept(source, posts, const FeedSourceState());
      unawaited(cache.write(key, posts));
    } catch (error) {
      if (!current()) return;
      if (error is PartialFeedFailure) {
        final combined = <String, InterleavedItem>{};
        for (final item in [...?state.visible[source], ...error.items]) {
          combined[item.id ?? '${item.date}:${combined.length}'] = item;
        }
        _accept(
          source,
          combined.values.toList(),
          FeedSourceState(error: error.cause, cachedAt: state.sources[source]?.cachedAt),
        );
      } else {
        _status(source, FeedSourceState(error: error, cachedAt: state.sources[source]?.cachedAt));
      }
    }
  }

  void _status(String source, FeedSourceState status) => update(
    ProgressiveFeedState(visible: state.visible, pending: state.pending, sources: {...state.sources, source: status}),
  );
  void _accept(String source, List<InterleavedItem> posts, FeedSourceState status) {
    final next = {...?state.pending, ...state.visible};
    if (state.pending != null) next.addAll(state.pending!);
    next[source] = List.unmodifiable(posts);
    final hold = _away && state.visible.values.any((items) => items.isNotEmpty);
    update(
      ProgressiveFeedState(
        visible: hold ? state.visible : next,
        pending: hold ? next : null,
        sources: {...state.sources, source: status},
      ),
    );
  }

  @override
  Future<void> destroy() {
    _closed = true;
    return super.destroy();
  }
}
