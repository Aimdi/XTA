import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/client/client.dart';
import 'package:xta/reading/shared_filter_engine.dart';
import 'package:xta/reading/shared_filter_rule.dart';
import 'package:xta/reading/shared_filter_scope.dart';
import 'package:xta/reading/shared_filter_settings.dart';
import 'package:xta/reading/shared_filter_worker.dart';
import 'package:xta/tweet/tweet_filtering.dart';

import 'support/reader_tools_harness.dart';

class _HeldEvaluator implements SharedFilterRegexEvaluator {
  final gate = Completer<void>();
  @override
  Future<List<List<int>>> evaluate(List<SharedFilterPattern> patterns, List<String> texts) async {
    await gate.future;
    return [
      for (final text in texts)
        [
          for (var index = 0; index < patterns.length; index++)
            if (RegExp(patterns[index].pattern).hasMatch(text)) index,
        ],
    ];
  }

  @override
  Future<void> close() async {}
}

class _Host {
  final prefs = PrefServiceCache();
  late final store = SharedFilterStore(prefs);
  late final engine = SharedFilterEngine(store, evaluator: () => evaluator ?? _HeldEvaluator());
  SharedFilterRegexEvaluator? evaluator;

  Future<void> add(
    String id,
    String pattern, {
    SharedFilterAction action = SharedFilterAction.hide,
    bool regex = false,
    Set<SharedFilterScope> scopes = const {SharedFilterScope.timelines, SharedFilterScope.search},
  }) async {
    expect(
      await store.save(SharedFilterRule(id: id, pattern: pattern, action: action, regex: regex, scopes: scopes)),
      isTrue,
    );
  }

  Widget app(Widget child) => readerToolsApp(prefs, SharedFilterRoot(engine: engine, child: child));

  Future<void> close(WidgetTester tester) async {
    await tester.pumpWidget(const SizedBox());
    await engine.destroy();
    await store.destroy();
  }
}

Widget _list(List<String> posts) => Scaffold(
  body: SharedFilterFeedList<String>(
    items: posts,
    textOf: (post) => post,
    keyOf: (post) => post,
    itemBuilder: (context, post, _) => SizedBox(height: 60, child: Text('card: $post')),
  ),
);

TweetChain _chain(String id, String text) {
  final tweet = TweetWithCard()
    ..idStr = id
    ..fullText = text;
  return TweetChain(id: id, tweets: [tweet], isPinned: false);
}

void main() {
  testWidgets('hidden posts take no row; folded ones show the rule and open on demand', (tester) async {
    final host = _Host();
    await host.add('hide', 'crypto');
    await host.add('fold', 'spoiler', action: SharedFilterAction.fold);
    await tester.pumpWidget(host.app(_list(['crypto moon', 'a spoiler here', 'quiet morning'])));
    await tester.pump();
    expect(find.text('card: crypto moon'), findsNothing);
    expect(find.text('card: quiet morning'), findsOneWidget);
    expect(find.text('card: a spoiler here'), findsNothing);
    expect(find.text('Matched: spoiler'), findsOneWidget);
    expect(tester.getSize(find.byType(SharedFilterFold)).height, greaterThanOrEqualTo(48));

    await tester.tap(find.text('Show'));
    await tester.pump();
    expect(find.text('card: a spoiler here'), findsOneWidget);
    await tester.tap(find.text('Hide again'));
    await tester.pump();
    expect(find.text('card: a spoiler here'), findsNothing);
    await host.close(tester);
  });

  testWidgets('rules update the lists already on screen', (tester) async {
    final host = _Host();
    await tester.pumpWidget(host.app(_list(['crypto moon', 'quiet morning'])));
    await tester.pump();
    expect(find.text('card: crypto moon'), findsOneWidget);
    await host.add('hide', 'crypto');
    await tester.pump();
    expect(find.text('card: crypto moon'), findsNothing);
    await host.store.setEnabled('hide', false);
    await tester.pump();
    expect(find.text('card: crypto moon'), findsOneWidget);
    await host.close(tester);
  });

  testWidgets('search results only follow rules meant for search', (tester) async {
    final host = _Host();
    await host.add('timelines', 'crypto', scopes: const {SharedFilterScope.timelines});
    await tester.pumpWidget(
      host.app(SharedFilterSurface(scope: SharedFilterScope.search, child: _list(['crypto moon']))),
    );
    await tester.pump();
    expect(find.text('card: crypto moon'), findsOneWidget);
    await host.close(tester);
  });

  testWidgets('a post waiting for its pattern check is not built until the answer arrives', (tester) async {
    final host = _Host();
    final evaluator = _HeldEvaluator();
    host.evaluator = evaluator;
    await host.add('re', r'moon$', regex: true);
    await tester.pumpWidget(host.app(_list(['crypto moon', 'quiet morning'])));
    await tester.pump();
    expect(find.textContaining('card:'), findsNothing);
    await tester.runAsync(() async {
      evaluator.gate.complete();
      await Future<void>.delayed(const Duration(milliseconds: 10));
    });
    await tester.pump();
    expect(find.text('card: quiet morning'), findsOneWidget);
    expect(find.text('card: crypto moon'), findsNothing);
    await host.close(tester);
  });

  testWidgets('X chains keep their index when hidden, and folds carry their rule', (tester) async {
    final host = _Host();
    await host.add('hide', 'crypto');
    await host.add('fold', 'spoiler', action: SharedFilterAction.fold);
    late TweetChainFilter filter;
    final chains = [
      _chain('1', 'crypto moon'),
      _chain('2', 'spoiler inside'),
      _chain('3', 'quiet'),
      _chain('4', 'crypto'),
    ];
    await tester.pumpWidget(
      host.app(
        Builder(
          builder: (context) {
            filter = TweetChainFilter.of(context, chains);
            return const SizedBox();
          },
        ),
      ),
    );
    expect(filter.hidden, {'1', '4'});
    expect(filter.folds, {'2': 'spoiler'});
    expect(filter.hidesAllFrom(chains, 3), isTrue);
    expect(filter.hidesAllFrom(chains, 1), isFalse);
    await host.close(tester);
  });

  test('automatic paging stops after two fully hidden pages until released', () {
    final guard = SharedFilterPagingGuard();
    bool hiddenFrom(int from) => from >= 10;
    expect(guard.allowFetch(10, hiddenFrom), isTrue);
    expect(guard.allowFetch(20, hiddenFrom), isTrue);
    expect(guard.allowFetch(30, hiddenFrom), isFalse);
    expect(guard.held, isTrue);
    expect(guard.allowFetch(30, hiddenFrom), isFalse);
    expect(guard.release(), isTrue);
    expect(guard.allowFetch(30, hiddenFrom), isTrue);
    expect(guard.release(), isFalse);
  });

  testWidgets('filters are added, validated, switched off and deleted in settings', (tester) async {
    final host = _Host();
    tester.view.physicalSize = const Size(400, 1600);
    tester.view.devicePixelRatio = 1;
    addTearDown(tester.view.resetPhysicalSize);
    addTearDown(tester.view.resetDevicePixelRatio);
    await tester.pumpWidget(host.app(SharedFilterSettings(engine: host.engine)));
    await tester.pump();
    expect(find.textContaining('No filters yet.'), findsOneWidget);

    await tester.tap(find.byKey(const ValueKey('filter-add')));
    await tester.pumpAndSettle();
    await tester.enterText(find.byKey(const ValueKey('filter-pattern')), '(broken');
    await tester.tap(find.byKey(const ValueKey('filter-regex')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('filter-save')));
    await tester.pumpAndSettle();
    expect(find.text("This isn't a valid regular expression"), findsOneWidget);
    expect(host.store.state, isEmpty);

    await tester.enterText(find.byKey(const ValueKey('filter-pattern')), r'\bnft\b');
    await tester.tap(find.byKey(const ValueKey('filter-action-fold')));
    await tester.tap(find.byKey(const ValueKey('filter-scope-search')));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('filter-save')));
    await tester.pumpAndSettle();
    final rule = host.store.state.single;
    expect(rule.pattern, r'\bnft\b');
    expect(rule.regex, isTrue);
    expect(rule.action, SharedFilterAction.fold);
    expect(rule.scopes, {SharedFilterScope.timelines});
    expect(find.text(r'\bnft\b'), findsOneWidget);

    await tester.tap(find.byType(Switch));
    await tester.pumpAndSettle();
    expect(host.store.state.single.enabled, isFalse);

    await tester.tap(find.text(r'\bnft\b'));
    await tester.pumpAndSettle();
    await tester.tap(find.byKey(const ValueKey('filter-delete')));
    await tester.pumpAndSettle();
    expect(host.store.state, isEmpty);
    await host.close(tester);
  });
}
