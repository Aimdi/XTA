import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_history_store.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings_mute.dart';

import 'support/memory_json_store.dart';
import 'support/pixiv_reader_harness.dart';

/// A button that opens [illust] the way every list and link does.
Widget _opener(PixivIllust illust) => Scaffold(
  body: Builder(
    builder: (context) => TextButton(onPressed: () => openPixivIllust(context, illust), child: const Text('open')),
  ),
);

void main() {
  final work = pixivWork(id: 120, pages: 1, tags: const [PixivTag(name: 'cat')]);

  Future<({PixivHistoryStore history, PixivMuteStore mute})> pump(
    WidgetTester tester, {
    String mutedTags = '[]',
    bool paused = false,
    bool detailFails = false,
  }) async {
    final history = PixivHistoryStore(storage: MemoryJsonStore());
    addTearDown(history.destroy);
    await pumpPixiv(
      tester,
      _opener(work),
      size: const Size(390, 1600),
      extraProviders: [Provider<PixivHistoryStore>.value(value: history)],
      client: (prefs) {
        prefs.set(optionPluginPixivMutedTags, mutedTags);
        prefs.set(optionPluginPixivHistoryPaused, paused);
        return detailFails ? _GoneClient(prefs) : FakePixivClient(prefs, detail: work);
      },
    );
    final mute = Provider.of<PixivMuteStore>(tester.element(find.text('open')), listen: false);
    unawaited(mute.load());
    await settlePixiv(tester);
    return (history: history, mute: mute);
  }

  testWidgets('a muted work waits behind a notice that says why, until shown this time', (tester) async {
    final stores = await pump(tester, mutedTags: '["cat"]');
    await tester.tap(find.text('open'));
    await settlePixiv(tester);

    expect(find.byKey(const ValueKey('pixiv-mute-gate')), findsOneWidget);
    expect(find.text('You muted its tag #cat.'), findsOneWidget);
    expect(find.byType(PixivIllustScreen), findsNothing);
    expect(stores.history.state, isEmpty, reason: 'a work kept behind the notice was not viewed');

    await tester.tap(find.text('Show this time'));
    await settlePixiv(tester);
    expect(find.byType(PixivIllustScreen), findsOneWidget);
    expect(stores.history.state.map((entry) => entry.id), [120]);
    expect(stores.mute.state.tags, {'cat'}, reason: 'showing it once does not unmute it');
    await disposePixiv(tester);
  });

  testWidgets('the notice opens the mute settings, and unmuting there shows the work', (tester) async {
    await pump(tester, mutedTags: '["cat"]');
    await tester.tap(find.text('open'));
    await settlePixiv(tester);

    await tester.tap(find.text('Mute settings'));
    await settlePixiv(tester);
    expect(find.byType(PixivMuteScreen), findsOneWidget);
    await tester.tap(find.byTooltip('Unmute'));
    await settlePixiv(tester);
    await tester.pageBack();
    await settlePixiv(tester);

    expect(find.byType(PixivIllustScreen), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('an unmuted work opens straight away and joins the history unless paused', (tester) async {
    final stores = await pump(tester);
    await tester.tap(find.text('open'));
    await settlePixiv(tester);
    expect(find.byKey(const ValueKey('pixiv-mute-gate')), findsNothing);
    expect(find.byType(PixivIllustScreen), findsOneWidget);
    expect(stores.history.state.single.title, 'Sommerfest');
    await disposePixiv(tester);
  });

  testWidgets('a work whose detail does not load stays out of the history', (tester) async {
    final stores = await pump(tester, detailFails: true);
    await tester.tap(find.text('open'));
    await settlePixiv(tester);
    expect(find.byType(PixivIllustScreen), findsOneWidget);
    expect(stores.history.state, isEmpty);
    await disposePixiv(tester);
  });

  testWidgets('a paused history records nothing', (tester) async {
    final stores = await pump(tester, paused: true);
    await tester.tap(find.text('open'));
    await settlePixiv(tester);
    expect(find.byType(PixivIllustScreen), findsOneWidget);
    expect(stores.history.state, isEmpty);
    await disposePixiv(tester);
  });

  testWidgets('swiping between works, each work swiped to joins the history and a muted one waits behind its notice', (
    tester,
  ) async {
    final works = [
      pixivWork(id: 1, pages: 1, title: 'One'),
      pixivWork(
        id: 2,
        pages: 1,
        title: 'Two',
        tags: const [PixivTag(name: 'cat')],
      ),
    ];
    final history = PixivHistoryStore(storage: MemoryJsonStore());
    addTearDown(history.destroy);
    await pumpPixiv(
      tester,
      Scaffold(
        body: Builder(
          builder: (context) =>
              TextButton(onPressed: () => openPixivIllustFromList(context, works, 0), child: const Text('open')),
        ),
      ),
      extraProviders: [Provider<PixivHistoryStore>.value(value: history)],
      client: (prefs) {
        prefs.set(optionPluginPixivMutedTags, '["cat"]');
        prefs.set(optionPluginPixivSwipeBetweenWorks, true);
        return _ListClient(prefs, works);
      },
    );
    unawaited(Provider.of<PixivMuteStore>(tester.element(find.text('open')), listen: false).load());
    await settlePixiv(tester);
    await tester.tap(find.text('open'));
    await settlePixiv(tester);
    expect(history.state.map((entry) => entry.id), [1]);

    await tester.dragFrom(const Offset(300, 760), const Offset(-320, 0));
    await settlePixiv(tester);
    expect(find.byKey(const ValueKey('pixiv-mute-gate')), findsOneWidget);
    expect(history.state.map((entry) => entry.id), [1], reason: 'a work kept behind the notice was not viewed');

    await tester.tap(find.text('Show this time'));
    await settlePixiv(tester);
    expect(history.state.map((entry) => entry.id), [2, 1]);
    await disposePixiv(tester);
  });
}

/// Answers each work's detail with that work from [works].
class _ListClient extends FakePixivClient {
  final List<PixivIllust> works;

  _ListClient(super.prefs, this.works);

  @override
  Future<PixivIllust> illustDetail(int illustId) async => works.firstWhere((work) => work.id == illustId);
}

/// A work Pixiv no longer has.
class _GoneClient extends FakePixivClient {
  _GoneClient(super.prefs);

  @override
  Future<PixivIllust> illustDetail(int illustId) async => throw PixivException(PixivErrorKind.notFound, 'gone');
}
