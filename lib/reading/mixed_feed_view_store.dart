import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';

/// Loads stop once this many new posts are placed, or after [mixedFeedLoadRounds] reads.
const mixedFeedPageGrowth = 12;
const mixedFeedLoadRounds = 4;

/// A source that keeps answering with empty pages is treated as finished after this many.
const _maxEmptyPages = 3;

/// How one source of a mix is doing, for its status line and retry.
class MixedSourceStatus {
  final MixedFeedSource source;
  final bool available;
  final bool answered;
  final bool loading;
  final bool failed;
  final bool exhausted;
  const MixedSourceStatus({
    required this.source,
    required this.available,
    required this.answered,
    required this.loading,
    required this.failed,
    required this.exhausted,
  });
}

class MixedFeedViewState {
  final List<MixedPick> shown;
  final List<MixedSourceStatus> sources;
  final bool refreshing;

  /// Reading further waits for the reader, because the shared filters hid everything the last reads brought.
  final bool held;
  const MixedFeedViewState({
    this.shown = const [],
    this.sources = const [],
    this.refreshing = false,
    this.held = false,
  });

  /// Something can be shown: a post, or every available source has answered once.
  bool get started => shown.isNotEmpty || sources.every((source) => !source.available || source.answered);

  bool get finished => sources.every((source) => !source.available || source.exhausted);
}

class _Slot {
  final MixedFeedSource source;
  final MixedSourceReader? reader;
  String? signature;
  MixedSlot data;
  Object? cursor;
  int generation = 0;
  int emptyPages = 0;
  bool loading = false;
  bool requested = false;
  bool answered = false;

  _Slot(this.source, this.reader, [this.signature]) : data = MixedSlot(exhausted: reader == null);

  /// This slot as it was before a refresh that failed for it, marked failed so it can be retried.
  _Slot keptAfterFailure() => this
    ..loading = false
    ..data = data.withFailure(true);
}

/// What one mix shows: each source is read with its own cursor, and the pages are merged into one list that only
/// grows at the end. A failing source is left out and retried on its own; the others keep what they loaded.
class MixedFeedViewStore extends Store<MixedFeedViewState> {
  MixedFeedDefinition _definition;
  MixedSourceReader? Function(MixedFeedSource source) _readerFor;
  List<_Slot> _slots;
  MixedMergeState _merge;
  final DateTime Function() _clock;
  bool _loadingMore = false;
  bool _refreshing = false;
  bool _held = false;
  bool _closed = false;

  MixedFeedViewStore(
    MixedFeedDefinition definition,
    MixedSourceReader? Function(MixedFeedSource source) readerFor, {
    DateTime Function()? clock,
  }) : _definition = definition,
       _readerFor = readerFor,
       _slots = [for (final source in definition.sources) _Slot(source, readerFor(source))],
       _merge = MixedMergeState.start(definition.sources.length),
       _clock = clock ?? DateTime.now,
       super(const MixedFeedViewState()) {
    _publish();
  }

  MixedFeedDefinition get definition => _definition;

  List<MixedSlot> get _data => [for (final slot in _slots) slot.data];

  void _publish() {
    if (_closed) return;
    update(
      MixedFeedViewState(
        shown: _merge.shown,
        refreshing: _refreshing,
        held: _held,
        sources: [
          for (final slot in _slots)
            MixedSourceStatus(
              source: slot.source,
              available: slot.reader != null,
              answered: slot.answered,
              loading: slot.loading,
              failed: slot.data.failed,
              exhausted: slot.data.exhausted,
            ),
        ],
      ),
    );
  }

  void _remerge() {
    _merge = mergeMixedSlots(_merge, _data, _definition.order);
    _publish();
  }

  /// Reads the first page of every source that has not been read yet.
  Future<void> start() => Future.wait([
    for (final slot in _slots)
      if (!slot.requested) _read(slot),
  ]);

  Future<void> _read(_Slot slot) async {
    final reader = slot.reader;
    if (reader == null || slot.loading || slot.data.exhausted || _closed) return;
    final generation = slot.generation;
    slot
      ..requested = true
      ..loading = true;
    _publish();
    try {
      final page = await reader.read(slot.cursor);
      if (_closed || generation != slot.generation) return;
      slot.emptyPages = page.entries.isEmpty ? slot.emptyPages + 1 : 0;
      slot.cursor = page.next;
      slot.data = slot.data.appended(
        page.entries,
        exhausted: page.next == null || slot.emptyPages >= _maxEmptyPages,
        fetchedAt: _clock(),
      );
    } catch (_) {
      if (_closed || generation != slot.generation) return;
      slot.data = slot.data.withFailure(true);
    }
    slot
      ..loading = false
      ..answered = true;
    _remerge();
  }

  /// Reads further the sources that hold the mix back, until enough new posts are placed.
  Future<void> loadMore() async {
    if (_loadingMore || _refreshing || _closed) return;
    _loadingMore = true;
    try {
      for (var round = 0; round < mixedFeedLoadRounds; round++) {
        final before = _merge.shown.length;
        final waiting = mixedSlotsToRead(_merge, _data).map((index) => _slots[index]).where((slot) => !slot.loading);
        if (waiting.isEmpty || _closed) return;
        await Future.wait(waiting.map(_read));
        if (_merge.shown.length - before >= mixedFeedPageGrowth) return;
      }
    } finally {
      _loadingMore = false;
    }
  }

  /// Marks whether reading further waits for the reader.
  void hold(bool held) {
    if (held == _held) return;
    _held = held;
    _publish();
  }

  /// Reads a failed source again from where it stopped.
  Future<void> retry(MixedFeedSource source) async {
    final slot = _slots.where((slot) => slot.source.key == source.key).firstOrNull;
    if (slot == null || !slot.data.failed) return;
    slot.data = slot.data.withFailure(false);
    await _read(slot);
  }

  /// Reads every source from the start. A source that fails keeps what it showed before, marked for retry.
  Future<void> refresh() async {
    if (_closed || _refreshing) return;
    _refreshing = true;
    _publish();
    for (final slot in _slots) {
      slot
        ..generation += 1
        ..loading = false;
    }
    final before = _slots;
    final fresh = [for (final slot in before) _Slot(slot.source, _readerFor(slot.source), slot.signature)];
    await Future.wait(fresh.map(_read));
    _refreshing = false;
    if (_closed) return;
    if (!identical(before, _slots)) return _publish();
    _slots = [
      for (var index = 0; index < fresh.length; index++)
        fresh[index].data.failed ? _slots[index].keptAfterFailure() : fresh[index],
    ];
    _merge = MixedMergeState.start(_slots.length);
    _remerge();
  }

  /// Follows an edited mix. Sources it still has keep what they loaded; new ones are read; a new source order or
  /// merge order is merged again from what is loaded, without reading anything twice. A rename changes nothing.
  Future<void> updateDefinition(
    MixedFeedDefinition next,
    MixedSourceReader? Function(MixedFeedSource source) readerFor,
  ) async {
    final previous = _definition;
    _definition = next;
    _readerFor = readerFor;
    final keys = [for (final source in next.sources) source.key];
    final unchanged = previous.order == next.order && _sameKeys(keys);
    if (unchanged) return;
    final kept = {for (final slot in _slots) slot.source.key: slot};
    for (final slot in _slots.where((slot) => !keys.contains(slot.source.key))) {
      slot.generation++;
    }
    _slots = [for (final source in next.sources) kept[source.key] ?? _Slot(source, readerFor(source))];
    _merge = MixedMergeState.start(_slots.length);
    _remerge();
    await start();
  }

  /// Reads [source] again from the start when what it depends on changed to [signature]; the first signature seen
  /// is only remembered.
  Future<void> follow(MixedFeedSource source, String signature) async {
    final index = _slots.indexWhere((slot) => slot.source.key == source.key);
    if (index < 0 || _closed) return;
    final slot = _slots[index];
    final previous = slot.signature;
    slot.signature = signature;
    if (previous == null || previous == signature) return;
    slot
      ..generation += 1
      ..loading = false;
    _slots = [..._slots]..[index] = _Slot(source, _readerFor(source), signature);
    _merge = MixedMergeState.start(_slots.length);
    _remerge();
    await _read(_slots[index]);
  }

  bool _sameKeys(List<String> keys) =>
      keys.length == _slots.length &&
      Iterable.generate(keys.length).every((index) => keys[index] == _slots[index].source.key);

  @override
  Future<void> destroy() async {
    _closed = true;
    for (final slot in _slots) {
      slot.generation++;
    }
    await super.destroy();
  }
}
