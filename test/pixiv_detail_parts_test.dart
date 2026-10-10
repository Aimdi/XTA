import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:provider/provider.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_detail_menu.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/ui/errors.dart';

import 'support/pixiv_reader_harness.dart';

/// Fails the work's detail and its similar works until told to recover.
class _FlakyPixivClient extends FakePixivClient {
  var failing = true;

  _FlakyPixivClient(super.prefs);

  @override
  Future<PixivIllust> illustDetail(int illustId) async {
    if (failing) throw PixivException(PixivErrorKind.network, 'offline');
    return super.illustDetail(illustId);
  }

  @override
  Future<PixivIllustPage> related(int illustId, {String? nextUrl, bool? includeR18}) async {
    if (failing) throw PixivException(PixivErrorKind.network, 'offline');
    return PixivIllustPage(illusts: [pixivWork(id: 300, pages: 1)]);
  }
}

Future<void> _openMenu(WidgetTester tester) async {
  await tester.tap(find.byKey(const ValueKey('pixiv-illust-menu')));
  await settlePixiv(tester);
}

Widget _opener(PixivIllust work) => Scaffold(
  body: Builder(
    builder: (context) => TextButton(onPressed: () => openPixivIllust(context, work), child: const Text('open')),
  ),
);

void main() {
  testWidgets('the overflow menu offers every entry, downloading all only for many pages', (tester) async {
    await pumpPixiv(
      tester,
      PixivIllustScreen(illust: pixivWork(pages: 1)),
      client: (prefs) => FakePixivClient(prefs, detail: pixivWork(pages: 1)),
    );
    await _openMenu(tester);

    final offered = [
      for (final entry in pixivDetailMenuEntries)
        if (tester.any(find.byKey(ValueKey('pixiv-illust-menu-${entry.id}')))) entry.id,
    ];
    expect(offered, ['folder', 'copyLink', 'copyInfo', 'open', 'mute', 'more']);
    await disposePixiv(tester);
  });

  testWidgets('muting the author asks first, mutes and leaves the work', (tester) async {
    await pumpPixiv(tester, _opener(pixivWork()));
    await tester.tap(find.text('open'));
    await settlePixiv(tester);
    expect(find.byType(PixivIllustScreen), findsOneWidget);

    await _openMenu(tester);
    await tester.tap(find.byKey(const ValueKey('pixiv-illust-menu-mute')));
    await settlePixiv(tester);
    await tester.tap(find.text('Mute author'));
    await settlePixiv(tester);
    expect(find.byType(AlertDialog), findsOneWidget);

    await tester.tap(find.descendant(of: find.byType(AlertDialog), matching: find.byType(FilledButton)));
    await settlePixiv(tester);
    final mute = Provider.of<PixivMuteStore>(tester.element(find.text('open')), listen: false);
    expect(mute.state.authorIds, {42});
    expect(find.byType(PixivIllustScreen), findsNothing);
    await disposePixiv(tester);
  });

  testWidgets('cancelling the mute keeps the work open and mutes nothing', (tester) async {
    await pumpPixiv(tester, _opener(pixivWork()));
    await tester.tap(find.text('open'));
    await settlePixiv(tester);

    await _openMenu(tester);
    await tester.tap(find.byKey(const ValueKey('pixiv-illust-menu-mute')));
    await settlePixiv(tester);
    await tester.tap(find.text('Mute this work'));
    await settlePixiv(tester);
    await tester.tap(find.text('Cancel'));
    await settlePixiv(tester);

    final mute = Provider.of<PixivMuteStore>(tester.element(find.byType(PixivIllustScreen)), listen: false);
    expect(mute.state.isEmpty, isTrue);
    expect(find.byType(PixivIllustScreen), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a work that cannot load keeps its picture, says why and recovers on retry', (tester) async {
    final harness = await pumpPixiv(
      tester,
      PixivIllustScreen(illust: pixivWork(title: 'Seed')),
      size: const Size(390, 1600),
      client: _FlakyPixivClient.new,
    );
    expect(find.text('Seed'), findsWidgets);
    expect(find.byType(FullPageErrorWidget), findsOneWidget);

    (harness.client as _FlakyPixivClient).failing = false;
    await tester.ensureVisible(find.text('Retry'));
    await tester.tap(find.text('Retry'));
    await settlePixiv(tester);
    expect(find.byType(FullPageErrorWidget), findsNothing);
    expect(find.text('Related works'), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('a Pixiv link opens the work or the creator, and says when the work will not load', (tester) async {
    final opened = <bool>[];
    Widget link(PixivLinkRef ref, String label) => Builder(
      builder: (context) =>
          TextButton(onPressed: () async => opened.add(await openPixivLinkRef(context, ref)), child: Text(label)),
    );
    final harness = await pumpPixiv(
      tester,
      Scaffold(
        body: Column(children: [link(const PixivLinkRef.artwork(120), '120'), link(const PixivLinkRef.user(42), '42')]),
      ),
      client: _FlakyPixivClient.new,
    );

    await tester.tap(find.text('120'));
    await settlePixiv(tester);
    expect(opened, [false]);
    expect(find.byType(PixivIllustScreen), findsNothing);

    (harness.client as _FlakyPixivClient).failing = false;
    await tester.tap(find.text('120'));
    await settlePixiv(tester);
    expect(find.byWidgetPredicate((w) => w is PixivIllustScreen && w.illust.id == 120), findsOneWidget);

    await tester.pageBack();
    await settlePixiv(tester);
    await tester.tap(find.text('42'));
    await settlePixiv(tester);
    expect(find.byWidgetPredicate((w) => w is PixivUserScreen && w.userId == 42), findsOneWidget);
    await disposePixiv(tester);
  });

  testWidgets('the detail fits a narrow phone with large text', (tester) async {
    final tags = [const PixivTag(name: 'オリジナル', translatedName: 'original'), const PixivTag(name: '風景')];
    final work = pixivWork(title: 'A rather long title for a narrow screen', tags: tags);
    await pumpPixiv(
      tester,
      PixivIllustScreen(illust: work),
      size: const Size(320, 2400),
      textScale: 2,
      client: (prefs) => FakePixivClient(prefs, detail: work, authorWorks: [pixivWork(id: 121, pages: 1)]),
    );
    expect(tester.takeException(), isNull);
    expect(find.byKey(const ValueKey('pixiv-illust-counter')), findsOneWidget);
    await disposePixiv(tester);
  });
}
