import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/pixiv/pixiv_haptics.dart';

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
}
