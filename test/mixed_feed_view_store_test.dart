import 'dart:async';

import 'package:flutter_test/flutter_test.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/mixed_feed_view_store.dart';

MixedEntry _post(String id, int hour) => MixedEntry(identity: id, item: id, date: DateTime.utc(2026, 9, 30, hour));

/// Pages served in order; the cursor is the index of the next page.
class _Reader implements MixedSourceReader {
  List<List<MixedEntry>> pages;
  Object? failure;
  Completer<void>? gate;
  int reads = 0;
  _Reader(this.pages);

  @override
  Future<MixedPage> read(Object? cursor) async {
    reads++;
    await gate?.future;
    if (failure != null) throw failure!;
    final index = cursor as int? ?? 0;
    return MixedPage(pages[index], next: index + 1 < pages.length ? index + 1 : null);
  }
}

const _a = MixedFeedSource(kind: 'test', value: 'a', label: 'A');
const _b = MixedFeedSource(kind: 'test', value: 'b', label: 'B');
const _c = MixedFeedSource(kind: 'test', value: 'c', label: 'C');

MixedFeedDefinition _mix(List<MixedFeedSource> sources, {MixedFeedOrder order = MixedFeedOrder.chronological}) =>
    MixedFeedDefinition(id: 'm', name: 'Mix', sources: sources, order: order);

List<String> _shown(MixedFeedViewStore store) => [for (final pick in store.state.shown) pick.entry.identity];

MixedSourceStatus _status(MixedFeedViewStore store, MixedFeedSource source) =>
    store.state.sources.singleWhere((status) => status.source.key == source.key);

void main() {
  late Map<String, _Reader> readers;
  MixedSourceReader? readerFor(MixedFeedSource source) => readers[source.value];

  setUp(() {
    readers = {
      'a': _Reader([
        [_post('a1', 11), _post('a2', 7)],
        [_post('a3', 3)],
      ]),
      'b': _Reader([
        [_post('b1', 10), _post('b2', 9)],
      ]),
      'c': _Reader([
        [_post('c1', 12)],
      ]),
    };
  });

  test('reads every source, merges by date and reads further only the source that holds the mix back', () async {
    final store = MixedFeedViewStore(_mix([_a, _b]), readerFor);
    await store.start();
    expect(_shown(store), ['a1', 'b1', 'b2', 'a2']);
    expect(store.state.finished, isFalse);
    await store.loadMore();
    expect(_shown(store), ['a1', 'b1', 'b2', 'a2', 'a3']);
    expect((readers['a']!.reads, readers['b']!.reads), (2, 1));
    expect(store.state.finished, isTrue);
    await store.destroy();
  });

  test('a failing source is left out and retried on its own', () async {
    readers['b']!.failure = StateError('offline');
    final store = MixedFeedViewStore(_mix([_a, _b]), readerFor);
    await store.start();
    expect(_shown(store), ['a1', 'a2']);
    expect(_status(store, _b).failed, isTrue);
    readers['b']!.failure = null;
    await store.retry(_b);
    expect(_status(store, _b).failed, isFalse);
    await store.loadMore();
    expect(_shown(store), ['a1', 'a2', 'b1', 'b2', 'a3']);
    await store.destroy();
  });

  test('a refresh that fails for one source keeps what that source showed', () async {
    final store = MixedFeedViewStore(_mix([_a, _b]), readerFor);
    await store.start();
    readers['a']!.pages = [
      [_post('a0', 13)],
    ];
    readers['b']!.failure = StateError('offline');
    await store.refresh();
    expect(_shown(store), ['a0', 'b1', 'b2']);
    expect(_status(store, _b).failed, isTrue);
    expect(store.state.refreshing, isFalse);
    await store.destroy();
  });

  test('renaming or reordering reads nothing again; new sources are read and removed ones forgotten', () async {
    final store = MixedFeedViewStore(_mix([_a, _b], order: MixedFeedOrder.alternating), readerFor);
    await store.start();
    expect(_shown(store), ['a1', 'b1', 'a2', 'b2']);
    await store.updateDefinition(store.definition.copyWith(name: 'Renamed'), readerFor);
    await store.updateDefinition(_mix([_b, _a], order: MixedFeedOrder.alternating), readerFor);
    expect(_shown(store), ['b1', 'a1', 'b2', 'a2']);
    expect((readers['a']!.reads, readers['b']!.reads), (1, 1));
    await store.updateDefinition(_mix([_b, _c]), readerFor);
    expect(_shown(store), ['c1', 'b1', 'b2']);
    expect((readers['a']!.reads, readers['b']!.reads, readers['c']!.reads), (1, 1, 1));
    await store.destroy();
  });

  test('answers that arrive after a source was removed or the mix closed are ignored', () async {
    readers['c']!.gate = Completer();
    final store = MixedFeedViewStore(_mix([_a, _c]), readerFor);
    final started = store.start();
    await Future<void>.delayed(Duration.zero);
    await store.updateDefinition(_mix([_a]), readerFor);
    readers['c']!.gate!.complete();
    await started;
    expect(_shown(store), ['a1', 'a2']);
    readers['a']!.gate = Completer();
    final more = store.loadMore();
    await Future<void>.delayed(Duration.zero);
    await store.destroy();
    readers['a']!.gate!.complete();
    await more;
    expect(_shown(store), ['a1', 'a2']);
  });

  test('a source without a reader is shown as unavailable and holds nothing back', () async {
    final store = MixedFeedViewStore(
      _mix([_a, const MixedFeedSource(kind: 'test', value: 'gone', label: 'Gone')]),
      readerFor,
    );
    await store.start();
    expect(_shown(store), ['a1', 'a2']);
    expect(store.state.sources.last.available, isFalse);
    expect(store.state.started, isTrue);
    await store.destroy();
  });

  test('a source whose account or server changed is read again, and a refresh builds fresh readers', () async {
    var built = 0;
    MixedSourceReader? counting(MixedFeedSource source) {
      built++;
      return readerFor(source);
    }

    final store = MixedFeedViewStore(_mix([_a, _b]), counting);
    await store.start();
    await store.follow(_a, 'server one');
    expect(readers['a']!.reads, 1, reason: 'The first signature is only remembered.');
    readers['a']!.pages = [
      [_post('other-a', 8)],
    ];
    await store.follow(_a, 'server two');
    expect(readers['a']!.reads, 2);
    expect(_shown(store), ['b1', 'b2', 'other-a']);
    final before = built;
    await store.refresh();
    expect(built, before + 2);
    await store.destroy();
  });

  test('a source that keeps answering with empty pages is treated as finished', () async {
    readers['a']!.pages = [for (var i = 0; i < 6; i++) <MixedEntry>[]];
    final store = MixedFeedViewStore(_mix([_a, _b]), readerFor);
    await store.start();
    for (var i = 0; i < 3; i++) {
      await store.loadMore();
    }
    expect(_shown(store), ['b1', 'b2']);
    expect(readers['a']!.reads, 3);
    expect(store.state.finished, isTrue);
    await store.destroy();
  });
}
