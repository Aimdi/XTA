import 'package:flutter/material.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_haptics.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_reader_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_user_card.dart';

import 'support/pixiv_reader_harness.dart';

/// Mounts [home] with haptics on, recording each buzz a second after the last.
Future<List<PixivHaptic>> _pumpRecording(WidgetTester tester, Widget home) async {
  var now = DateTime(2026);
  final played = <PixivHaptic>[];
  final haptics = PixivHaptics(
    clock: () => now = now.add(const Duration(seconds: 1)),
    play: (kind) async => played.add(kind),
  );
  await pumpPixiv(
    tester,
    home,
    client: (cache) {
      cache.set(optionPluginPixivHaptics, true);
      return FakePixivClient(cache);
    },
    extraProviders: [Provider<PixivHaptics>.value(value: haptics)],
  );
  return played;
}

void main() {
  test('buzzes are at least the gap apart', () {
    final start = DateTime(2026);
    expect(pixivHapticDue(null, start), isTrue);
    expect(pixivHapticDue(start, start.add(const Duration(milliseconds: 99))), isFalse);
    expect(pixivHapticDue(start, start.add(pixivHapticGap)), isTrue);
  });

  test('plays only when the reader left haptics on, and spaces a burst out', () {
    var now = DateTime(2026);
    final played = <PixivHaptic>[];
    final haptics = PixivHaptics(clock: () => now, play: (kind) async => played.add(kind));
    final prefs = PrefServiceCache(cache: {optionPluginPixivHaptics: false});

    haptics.play(prefs, PixivHaptic.light);
    expect(played, isEmpty);

    prefs.set(optionPluginPixivHaptics, true);
    haptics.play(prefs, PixivHaptic.light);
    now = now.add(const Duration(milliseconds: 40));
    haptics.play(prefs, PixivHaptic.medium);
    now = now.add(const Duration(milliseconds: 100));
    haptics.play(prefs, PixivHaptic.medium);
    expect(played, [PixivHaptic.light, PixivHaptic.medium]);
  });

  testWidgets('the reader\'s page sheet buzzes when a long press opens it, not from its button', (tester) async {
    final played = await _pumpRecording(tester, PixivReaderScreen(illust: pixivWork(pages: 1), vertical: false));
    await tester.tap(find.byKey(const ValueKey('pixiv-reader-more')));
    await settlePixiv(tester);
    expect(find.text('Download this page'), findsOneWidget);
    expect(played, isEmpty);

    await tester.binding.handlePopRoute();
    await settlePixiv(tester);
    expect(find.text('Download this page'), findsNothing);
    // Beside the failed image's retry button, on the page itself.
    await tester.longPressAt(tester.getCenter(find.byType(PageView)) + const Offset(120, 0));
    await settlePixiv(tester);
    expect(find.text('Download this page'), findsOneWidget);
    expect(played, [PixivHaptic.medium]);
    await disposePixiv(tester);
  });

  testWidgets('a follow from a creator\'s button buzzes lightly once Pixiv took it', (tester) async {
    const user = PixivUser(id: 9, name: 'Mika', account: 'mika', comment: '');
    final played = await _pumpRecording(
      tester,
      const Scaffold(
        body: Center(child: PixivFollowButton(user: user)),
      ),
    );
    await tester.tap(find.byKey(const ValueKey('pixiv-follow-9')));
    await settlePixiv(tester);
    expect(find.text('Unfollow'), findsOneWidget);
    expect(played, [PixivHaptic.light]);
    await disposePixiv(tester);
  });
}
