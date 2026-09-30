import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/home/_feed.dart';
import 'package:xta/reading/feed_appearance_scope.dart';
import 'package:xta/reading/feed_appearance_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_editor.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/mixed_feed_store.dart';
import 'package:xta/reading/mixed_feed_view.dart';
import 'package:xta/reading/shared_filter_engine.dart';
import 'package:xta/reading/shared_filter_rule.dart';
import 'package:xta/reading/shared_filter_scope.dart';
import 'package:xta/reading/shared_filter_worker.dart';

import 'support/reader_tools_harness.dart';

MixedEntry _post(String id, int hour) => MixedEntry(identity: id, item: id, date: DateTime.utc(2026, 9, 30, hour));

/// A source kind whose posts are plain labels, read from pages held in memory.
class _Kind extends MixedSourceKind {
  @override
  final String id;
  @override
  final MixedSourceInput input;
  final Map<String, List<MixedEntry>> posts;
  final Set<String> failing;
  int reads = 0;

  _Kind(this.id, {this.input = MixedSourceInput.none, this.posts = const {}, Set<String>? failing})
    : failing = failing ?? {};

  @override
  String get pluginId => 'test';

  @override
  IconData get icon => Icons.circle_outlined;

  @override
  String title(BuildContext context) => 'Kind $id';

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async => [
    MixedFeedSource(kind: id, value: 'picked', label: 'Picked choice'),
  ];

  @override
  Future<MixedFeedSource?> fromText(BuildContext context, String text) async =>
      text == 'bad' ? null : MixedFeedSource(kind: id, value: text, label: 'Typed $text');

  @override
  MixedSourceReader reader(BuildContext context, MixedFeedSource source) => MixedFunctionReader((_) async {
    reads++;
    if (failing.contains(source.value)) throw StateError('offline');
    return MixedPage(posts[source.value] ?? const []);
  });

  @override
  Widget card(BuildContext context, MixedEntry entry) => SizedBox(height: 60, child: Text('card: ${entry.identity}'));

  @override
  String filterText(MixedEntry entry) => entry.identity;
}

class _Evaluator implements SharedFilterRegexEvaluator {
  @override
  Future<List<List<int>>> evaluate(List<SharedFilterPattern> patterns, List<String> texts) async => [
    for (final _ in texts) <int>[],
  ];

  @override
  Future<void> close() async {}
}

const _a = MixedFeedSource(kind: 'feed', value: 'a', label: 'Source A');
const _b = MixedFeedSource(kind: 'feed', value: 'b', label: 'Source B');

void main() {
  late _Kind kind;
  setUp(() {
    kind = _Kind(
      'feed',
      posts: {
        'a': [_post('a-noon', 12), _post('a-dawn', 6)],
        'b': [_post('b-morning', 9)],
      },
    );
  });

  Widget view(BasePrefService prefs, MixedFeedDefinition mix, {SharedFilterEngine? engine}) {
    final list = Scaffold(
      body: MixedFeedView(definition: mix, kinds: [kind]),
    );
    return readerToolsApp(prefs, engine == null ? list : SharedFilterRoot(engine: engine, child: list));
  }

  testWidgets('shows every source newest first with each source’s own card', (tester) async {
    await tester.pumpWidget(
      view(PrefServiceCache(), const MixedFeedDefinition(id: 'm', name: 'Morning', sources: [_a, _b])),
    );
    await tester.pump();
    await tester.pump();
    final order = [
      for (final label in ['card: a-noon', 'card: b-morning', 'card: a-dawn']) tester.getTopLeft(find.text(label)).dy,
    ];
    expect(order, orderedEquals([...order]..sort()));
    expect(find.text('Morning'), findsWidgets);
  });

  testWidgets('a failing source says so and can be retried without touching the others', (tester) async {
    kind.failing.add('b');
    await tester.pumpWidget(
      view(PrefServiceCache(), const MixedFeedDefinition(id: 'm', name: 'Mix', sources: [_a, _b])),
    );
    await tester.pump();
    await tester.pump();
    expect(find.text('card: a-noon'), findsOneWidget);
    expect(find.text('Could not load Source B'), findsOneWidget);
    final reads = kind.reads;
    kind.failing.clear();
    await tester.tap(find.text('Retry'));
    await tester.pump();
    await tester.pump();
    expect(find.text('card: b-morning'), findsOneWidget);
    expect(find.text('Could not load Source B'), findsNothing);
    expect(kind.reads, reads + 1);
  });

  testWidgets('the shared filters apply to the mix like any other timeline', (tester) async {
    final prefs = PrefServiceCache();
    final rules = SharedFilterStore(prefs);
    await tester.runAsync(() => rules.save(const SharedFilterRule(id: 'r', pattern: 'b-morning')));
    final engine = SharedFilterEngine(rules, evaluator: _Evaluator.new);
    await tester.pumpWidget(
      view(
        prefs,
        const MixedFeedDefinition(id: 'm', name: 'Mix', sources: [_a, _b]),
        engine: engine,
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(find.text('card: a-noon'), findsOneWidget);
    expect(find.text('card: b-morning'), findsNothing);
    await tester.pumpWidget(const SizedBox());
    await engine.destroy();
    await rules.destroy();
  });

  testWidgets('a source whose kind is unknown is shown as unavailable', (tester) async {
    await tester.pumpWidget(
      view(
        PrefServiceCache(),
        const MixedFeedDefinition(
          id: 'm',
          name: 'Mix',
          sources: [
            _a,
            MixedFeedSource(kind: 'gone', label: 'Old source'),
          ],
        ),
      ),
    );
    await tester.pump();
    await tester.pump();
    expect(find.text('card: a-noon'), findsOneWidget);
    expect(find.text('Old source is not available right now'), findsOneWidget);
  });

  testWidgets('a new mix is named, given sources of every input kind, saved and checked first', (tester) async {
    final prefs = PrefServiceCache();
    final kinds = [
      _Kind('plain'),
      _Kind('pick', input: MixedSourceInput.choice),
      _Kind('typed', input: MixedSourceInput.text),
    ];
    tester.view.physicalSize = const Size(800, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(readerToolsApp(prefs, MixedFeedEditor(kinds: kinds)));
    await tester.tap(find.byKey(const ValueKey('mix-save')));
    await tester.pumpAndSettle();
    expect(find.text('Give the mixed feed a name'), findsOneWidget);
    await tester.enterText(find.byKey(const ValueKey('mix-name')), 'Evening');
    await tester.tap(find.byKey(const ValueKey('mix-save')));
    await tester.pumpAndSettle();
    expect(find.text('Add at least one source'), findsOneWidget);

    Future<void> add(String kindId) async {
      await tester.tap(find.byKey(const ValueKey('mix-add-source')));
      await tester.pumpAndSettle();
      await tester.tap(find.byKey(ValueKey('mix-kind-$kindId')));
      await tester.pumpAndSettle();
    }

    await add('plain');
    await add('pick');
    await tester.tap(find.byKey(const ValueKey('mix-choice-picked')));
    await tester.pumpAndSettle();
    await add('typed');
    await tester.enterText(find.byKey(const ValueKey('mix-source-text')), 'news');
    await tester.tap(find.byKey(const ValueKey('mix-source-text-add')));
    await tester.pumpAndSettle();
    expect(find.text('Picked choice'), findsOneWidget);
    expect(find.text('Typed news'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('mix-remove-plain\u0000')));
    await tester.pumpAndSettle();
    await tester.tap(find.text('Take turns'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mix-save')));
    await tester.pumpAndSettle();
    final saved = MixedFeedStore.forPrefs(prefs).state.single;
    expect(saved.name, 'Evening');
    expect(saved.order, MixedFeedOrder.alternating);
    expect(saved.sources.map((source) => source.label), ['Picked choice', 'Typed news']);
  });

  testWidgets('a saved mix can be deleted after confirming', (tester) async {
    final prefs = PrefServiceCache();
    final store = MixedFeedStore.forPrefs(prefs);
    const mix = MixedFeedDefinition(id: 'm', name: 'Mix', sources: [_a]);
    await tester.runAsync(() => store.save(mix));
    await tester.pumpWidget(readerToolsApp(prefs, MixedFeedEditor(mix: mix, kinds: [kind])));
    await tester.tap(find.byKey(const ValueKey('mix-delete')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('mix-delete-confirm')));
    await tester.pumpAndSettle();
    expect(store.state, isEmpty);
  });

  test('mixes join the Home strip under their own appearance identity', () async {
    final prefs = PrefServiceCache();
    await MixedFeedStore.forPrefs(prefs).save(const MixedFeedDefinition(id: 'm1', name: 'Reading', sources: [_a]));
    final tabs = availableFeedTabsFromIds(const [], prefs);
    final mix = tabs.singleWhere((option) => option.id.id == 'mix:m1');
    expect(mix.id.icon, mixedFeedIcon);
    expect(homeFeedIdentity('mix:m1'), const FeedIdentity('mix', 'm1'));
    expect(feedAppearanceCapable('mix', mixed: true), isTrue);
  });
}
