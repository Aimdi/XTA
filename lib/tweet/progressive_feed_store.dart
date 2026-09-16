import 'dart:async';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/tweet/feed_snapshot_cache.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/utils/read_request_scope.dart';

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
  final List<InterleavedItem> items;
  ProgressiveFeedState({
    this.visible = const {},
    this.pending,
    this.sources = const {},
    List<InterleavedItem>? orderedItems,
  }) : items =
           orderedItems ??
           List.unmodifiable([for (final posts in visible.values) ...posts]..sort((a, b) => b.date.compareTo(a.date)));
  bool get hasPending => pending != null;
}

class ProgressiveFeedStore extends Store<ProgressiveFeedState> {
  final FeedSnapshotCache cache;
  final Duration timeout;
  final Duration cacheTimeout;
  final _runs = <String, int>{};
  final _reads = <String, ReadRequestScope>{};
  final _active = <String, Future<void>>{};
  Map<String, SourceLoader> _loaders = {};
  Map<String, String> _keys = {};
  bool _away = false;
  bool _closed = false;
  ProgressiveFeedStore({
    FeedSnapshotCache? cache,
    this.timeout = const Duration(seconds: 30),
    this.cacheTimeout = const Duration(seconds: 1),
  }) : cache = cache ?? FeedSnapshotCache(),
       super(ProgressiveFeedState());

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
    for (final read in _reads.values) {
      read.cancel();
    }
    _reads.clear();
    _active.clear();
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

  Future<void> retry(String source) {
    final active = _active[source];
    if (active != null) return active;
    late final Future<void> attempt;
    attempt = _retry(source).whenComplete(() {
      if (identical(_active[source], attempt)) _active.remove(source);
    });
    _active[source] = attempt;
    return attempt;
  }

  Future<void> _retry(String source) async {
    final loader = _loaders[source];
    final key = _keys[source];
    if (_closed || loader == null || key == null) return;
    final run = (_runs[source] ?? 0) + 1;
    _runs[source] = run;
    final reads = _reads[source] = ReadRequestScope();
    bool current() => !_closed && _runs[source] == run && _keys[source] == key;
    _status(source, FeedSourceState(loading: true, cachedAt: state.sources[source]?.cachedAt));
    try {
      final posts = await reads.start(() async {
        if (!state.visible.containsKey(source)) {
          try {
            final cached = await cache.read(key).timeout(cacheTimeout);
            ReadWork.checkpoint();
            if (current() && cached.items.isNotEmpty) {
              _accept(source, cached.items, FeedSourceState(loading: true, cachedAt: cached.at));
            }
          } catch (_) {
            /* Cached content is optional, including damaged snapshots. */
          }
        }
        ReadWork.checkpoint();
        if (!current()) throw const ReadCancelled();
        return loader();
      }, timeout: timeout);
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
    } finally {
      if (identical(_reads[source], reads)) _reads.remove(source);
    }
  }

  void _status(String source, FeedSourceState status) => update(
    ProgressiveFeedState(
      visible: state.visible,
      pending: state.pending,
      sources: {...state.sources, source: status},
      orderedItems: state.items,
    ),
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
        orderedItems: hold ? state.items : null,
      ),
    );
  }

  @override
  Future<void> destroy() {
    _closed = true;
    for (final read in _reads.values) {
      read.cancel();
    }
    _reads.clear();
    return super.destroy();
  }
}
