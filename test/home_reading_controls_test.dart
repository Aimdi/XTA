import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/plugins/plugin_home_reading_controls.dart';

void main() {
  late HomeReadingControlsStore store;
  late PrefServiceCache prefs;
  setUp(() {
    prefs = PrefServiceCache(defaults: {});
    store = HomeReadingControlsStore()..bind('blue', prefs);
  });
  tearDown(() => store.destroy());

  void scroll(double delta, {double before = 200, double extent = 1000, double height = 48,
      bool protected = false, String source = 'blue'}) => store.observe(
    source: source, extentBefore: before, scrollExtent: extent,
    controlsHeight: height, userDelta: delta, protected: protected,
  );

  test('deliberate down hides, small reversals do not flicker, deliberate up reveals', () {
    scroll(20);
    scroll(20);
    expect(store.state.visible, isTrue);
    scroll(30);
    expect(store.state.visible, isFalse);
    scroll(-8);
    expect(store.state.visible, isFalse);
    scroll(-20);
    expect(store.state.visible, isTrue);
  });
  test('programmatic jumps and stale source notifications cannot hide controls', () {
    scroll(0, before: 900);
    expect(store.state.visible, isTrue);
    scroll(200, source: 'departed');
    expect(store.state.visible, isTrue);
  });
  test('short feeds and protected focus, modal or accessible navigation stay visible', () {
    scroll(200, extent: 100);
    expect(store.state.visible, isTrue);
    scroll(200, protected: true);
    expect(store.state.visible, isTrue);
    scroll(200);
    expect(store.state.visible, isFalse);
    scroll(0, protected: true);
    expect(store.state.visible, isTrue);
  });
  test('reclaimed space does not create a short-feed hide/reveal loop', () {
    scroll(100, extent: 160);
    expect(store.state.visible, isFalse);
    scroll(0, extent: 112);
    scroll(0, extent: 112);
    expect(store.state.visible, isFalse);
    scroll(0, before: 0, extent: 112);
    expect(store.state.visible, isTrue);
  });
  test('pin is reversible, persisted, and restored by a new Home session', () async {
    await store.setPinned(true);
    scroll(500);
    expect(store.state.visible, isTrue);
    expect(prefs.get<bool>(homeKeepControlsVisibleKey), isTrue);
    final next = HomeReadingControlsStore()..bind('masto', prefs);
    expect(next.state.pinned, isTrue);
    await next.setPinned(false);
    expect(next.state.pinned, isFalse);
    expect(prefs.get<bool>(homeKeepControlsVisibleKey), isFalse);
    await next.destroy();
  });
  test('source changes reveal controls without changing the global pin setting', () {
    scroll(100);
    expect(store.state.visible, isFalse);
    store.bind('substack', prefs);
    expect(store.state.visible, isTrue);
    expect(store.state.source, 'substack');
  });
}
