import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_store.dart';

import 'support/reader_tools_harness.dart';

const _rss = MixedFeedSource(kind: 'rss.all', label: 'All feeds');
const _tag = MixedFeedSource(kind: 'mastodon.tag', value: 'dart', label: '#dart');

MixedFeedDefinition _mix(String id, {String name = 'Morning', List<MixedFeedSource> sources = const [_rss, _tag]}) =>
    MixedFeedDefinition(id: id, name: name, sources: sources);

final _fetched = DateTime.utc(2026, 9, 30, 12);

MixedEntry _post(String id, int hour) => MixedEntry(identity: id, item: id, date: DateTime.utc(2026, 9, 30, hour));

MixedEntry _undated(String id) => MixedEntry(identity: id, item: id);

MixedSlot _slot(List<MixedEntry> entries, {bool exhausted = false}) =>
    const MixedSlot().appended(entries, exhausted: exhausted, fetchedAt: _fetched);

List<String> _shown(MixedMergeState state) => [for (final pick in state.shown) pick.entry.identity];

void main() {
  group('definitions', () {
    test('survive storage; unusable mixes and repeated sources are dropped', () {
      final raw = jsonEncode({
        'version': 1,
        'mixes': [
          {
            ..._mix('a').toJson(),
            'order': 'alternating',
            'sources': [_rss.toJson(), _rss.toJson(), _tag.toJson()],
          },
          _mix('a', name: 'Duplicate id').toJson(),
          _mix('b', name: '   ').toJson(),
          _mix('c', sources: const []).toJson(),
          'nonsense',
          {..._mix('d').toJson(), 'order': 'sideways'},
        ],
      });
      final store = MixedFeedStore(PrefServiceCache(cache: {mixedFeedPreferenceKey: raw}));
      expect(store.state.map((mix) => mix.id), ['a', 'd']);
      expect(store.state.first.order, MixedFeedOrder.alternating);
      expect(store.state.first.sources, [_rss, _tag]);
      expect(store.state.last.order, MixedFeedOrder.chronological);
    });

    test('name their Home tab and explain what is missing', () {
      expect(_mix('x1').tabId, 'mix:x1');
      expect(mixedFeedIdOfTab('mix:x1'), 'x1');
      expect(mixedFeedIdOfTab('rss'), isNull);
      expect(_mix('a', name: ' ').problem, MixedFeedProblem.emptyName);
      expect(_mix('a', sources: const []).problem, MixedFeedProblem.noSources);
      final many = [
        for (var i = 0; i <= mixedFeedMaxSources; i++) MixedFeedSource(kind: 'rss.feed', value: '$i', label: '$i'),
      ];
      expect(_mix('a', sources: many).problem, MixedFeedProblem.tooManySources);
    });

    test('are saved in order, edited in place, moved, and kept when a save fails', () async {
      final prefs = GatedPrefs();
      final store = MixedFeedStore(prefs);
      expect(await store.save(_mix('a')), isTrue);
      expect(await store.save(_mix('b', name: 'Evening')), isTrue);
      expect(await store.save(_mix('a', name: 'Dawn')), isTrue);
      expect(store.state.map((mix) => mix.name), ['Dawn', 'Evening']);
      expect(await store.move(1, 0), isTrue);
      expect(store.state.map((mix) => mix.id), ['b', 'a']);
      expect(await store.save(_mix('c')), isTrue);
      expect(await store.move(0, 2), isTrue);
      expect(store.state.map((mix) => mix.id), ['a', 'c', 'b']);
      expect(await store.remove('c'), isTrue);
      expect(await store.move(1, 0), isTrue);
      expect(await store.save(_mix('c', name: '')), isFalse);
      prefs.rejectWrites = true;
      expect(await store.remove('a'), isFalse);
      prefs
        ..rejectWrites = false
        ..throwWrites = true;
      expect(await store.save(_mix('d')), isFalse);
      expect(store.state.map((mix) => mix.id), ['b', 'a']);
      expect(MixedFeedStore(prefs).state.map((mix) => mix.id), ['b', 'a']);
      await store.destroy();
    });
  });

  group('chronological merging', () {
    test('takes the newest next post and waits for a source that may have more', () {
      final slots = [
        _slot([_post('a1', 11), _post('a2', 8)]),
        _slot([_post('b1', 10), _post('b2', 9)], exhausted: true),
      ];
      final state = mergeMixedSlots(MixedMergeState.start(2), slots, MixedFeedOrder.chronological);
      expect(_shown(state), ['a1', 'b1', 'b2', 'a2']);
      expect(mixedSlotsToRead(state, slots), {0});
    });

    test('keeps what is shown in place when more arrives', () {
      var slots = [
        _slot([_post('a1', 11)]),
        _slot([_post('b1', 10), _post('b2', 5)]),
      ];
      final first = mergeMixedSlots(MixedMergeState.start(2), slots, MixedFeedOrder.chronological);
      expect(_shown(first), ['a1']);
      expect(mixedSlotsToRead(first, slots), {0});
      slots = [
        slots[0].appended([_post('a2', 7)], exhausted: true, fetchedAt: _fetched),
        slots[1],
      ];
      final second = mergeMixedSlots(first, slots, MixedFeedOrder.chronological);
      expect(_shown(second), ['a1', 'b1', 'a2', 'b2']);
      expect(mixedSlotsToRead(second, slots), {1});
    });

    test('shows each post once, breaks ties by source order and never invents dates', () {
      final slots = [
        _slot([_post('same', 10), _post('a2', 9)], exhausted: true),
        _slot([_post('tie', 10), _post('same', 10), _undated('late'), _post('b3', 8)], exhausted: true),
      ];
      final state = mergeMixedSlots(MixedMergeState.start(2), slots, MixedFeedOrder.chronological);
      expect(_shown(state), ['same', 'tie', 'late', 'a2', 'b3']);
      expect((state.shown[2].entry.item, state.shown[2].entry.date), ('late', null));
    });

    test('an undated first post sorts by when its page arrived', () {
      final slots = [
        _slot([_undated('fresh')], exhausted: true),
        _slot([_post('noon', 12), _post('dawn', 6)], exhausted: true),
      ];
      final state = mergeMixedSlots(MixedMergeState.start(2), slots, MixedFeedOrder.chronological);
      expect(_shown(state), ['fresh', 'noon', 'dawn']);
    });

    test('a failed source is left out instead of holding the others back', () {
      final slots = [
        _slot([_post('a1', 11)]),
        const MixedSlot().withFailure(true),
      ];
      final state = mergeMixedSlots(MixedMergeState.start(2), slots, MixedFeedOrder.chronological);
      expect(_shown(state), ['a1']);
      expect(mixedSlotsToRead(state, slots), {0});
    });
  });

  group('alternating merging', () {
    test('takes turns fairly and skips sources that ran out', () {
      final slots = [
        _slot([_post('a1', 1), _post('a2', 2), _post('a3', 3)], exhausted: true),
        _slot([_post('b1', 9)], exhausted: true),
        _slot([_post('c1', 5), _post('c2', 4)], exhausted: true),
      ];
      final state = mergeMixedSlots(MixedMergeState.start(3), slots, MixedFeedOrder.alternating);
      expect(_shown(state), ['a1', 'b1', 'c1', 'a2', 'c2', 'a3']);
    });

    test('waits for the source whose turn it is, then continues from it', () {
      var slots = [
        _slot([_post('a1', 1), _post('a2', 2)], exhausted: true),
        _slot([_post('b1', 9)]),
      ];
      final first = mergeMixedSlots(MixedMergeState.start(2), slots, MixedFeedOrder.alternating);
      expect(_shown(first), ['a1', 'b1', 'a2']);
      expect(mixedSlotsToRead(first, slots), {1});
      slots = [
        slots[0],
        slots[1].appended([_post('b2', 8), _post('a1', 1)], exhausted: true, fetchedAt: _fetched),
      ];
      final second = mergeMixedSlots(first, slots, MixedFeedOrder.alternating);
      expect(_shown(second), ['a1', 'b1', 'a2', 'b2']);
    });
  });
}
