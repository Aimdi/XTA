import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/ui/card_page_route.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Renders the swipe-back card for review, as still frames and as the frames
/// of a clip (open, drag and drop back, throw):
///   fvm flutter test test/swipe_back_render_test.dart --dart-define=RENDER_SWIPE_BACK=true
const _render = bool.fromEnvironment('RENDER_SWIPE_BACK');

const _shot = ValueKey('swipe-back-shot');
const _out = '../review-artifacts/renders';

const _posts = [
  (
    'Mira Okafor',
    'miraokafor',
    'Repainted the bike shed this weekend. Three coats and one very patient neighbour.',
    14,
  ),
  ('Lowtide Radio', 'lowtide', 'Tonight’s set: two hours of tape loops recorded in an empty parking garage.', 200),
  ('Field Notes', 'fieldnotes', 'Fog sat on the river until noon. Shot this from the old rail bridge.', 160),
  ('Dana Voss', 'danavoss', 'If a back gesture doesn’t follow your finger, it isn’t a gesture.', 280),
  ('Pixel Garden', 'pixelgarden', 'Palette study: twelve greens, no two the same.', 110),
  ('Jun Arai', 'junarai', 'Shipping a tiny release today. The whole changelog is one line: it’s faster now.', 38),
  ('Night Owl Bakery', 'nightowlbakes', 'Cardamom buns are out of the oven. The early shift gets the corner ones.', 24),
];

const _replies = [
  ('Ana Brandt', 'anabrandt', 'That’s the good kind of patience.', 350),
  ('Kofi Mensah', 'kofim', 'Okay, but what did you end up choosing?', 190),
  ('Lea Hartmann', 'leah', 'Saving this for later.', 260),
  ('Rui Tavares', 'ruitav', 'Exactly the energy I needed today.', 40),
  ('Ines Novak', 'inesn', 'Is there a longer version of this somewhere?', 140),
  ('Sam Okoye', 'samokoye', 'Sending this to the group chat.', 300),
];

Widget _avatar(String name, int hue, double size) => CircleAvatar(
  radius: size / 2,
  backgroundColor: HSLColor.fromAHSL(1, hue.toDouble(), 0.48, 0.46).toColor(),
  child: Text(
    name.split(' ').take(2).map((word) => word[0]).join(),
    style: TextStyle(color: Colors.white, fontSize: size * 0.34, fontWeight: FontWeight.w600),
  ),
);

Widget _media(int hue) => Container(
  height: 150,
  margin: const EdgeInsets.only(top: 10),
  decoration: BoxDecoration(
    borderRadius: BorderRadius.circular(16),
    gradient: LinearGradient(
      begin: Alignment.topCenter,
      end: Alignment.bottomCenter,
      colors: [
        HSLColor.fromAHSL(1, hue.toDouble(), 0.7, 0.72).toColor(),
        HSLColor.fromAHSL(1, (hue + 40) % 360, 0.5, 0.36).toColor(),
      ],
    ),
  ),
);

class _Feed extends StatelessWidget {
  const _Feed();

  @override
  Widget build(BuildContext context) {
    final muted = Theme.of(context).colorScheme.onSurfaceVariant;
    return Scaffold(
      appBar: AppBar(
        title: const Text('XTA', style: TextStyle(fontWeight: FontWeight.w700)),
      ),
      body: ListView(
        children: [
          for (final (index, (name, handle, text, hue)) in _posts.indexed)
            InkWell(
              onTap: () => Navigator.pushNamed(context, '/post'),
              child: Container(
                padding: const EdgeInsets.fromLTRB(16, 14, 16, 12),
                decoration: BoxDecoration(
                  border: Border(bottom: BorderSide(color: Theme.of(context).dividerColor)),
                ),
                child: Row(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    _avatar(name, hue, 40),
                    const SizedBox(width: 12),
                    Expanded(
                      child: Column(
                        crossAxisAlignment: CrossAxisAlignment.start,
                        children: [
                          Text.rich(
                            TextSpan(
                              children: [
                                TextSpan(
                                  text: name,
                                  style: const TextStyle(fontWeight: FontWeight.w600),
                                ),
                                TextSpan(
                                  text: '  @$handle · ${index + 2}h',
                                  style: TextStyle(color: muted),
                                ),
                              ],
                            ),
                          ),
                          const SizedBox(height: 2),
                          Text(text, style: const TextStyle(height: 1.4)),
                          if (index.isEven) _media(hue),
                        ],
                      ),
                    ),
                  ],
                ),
              ),
            ),
        ],
      ),
    );
  }
}

class _Post extends StatelessWidget {
  const _Post();

  @override
  Widget build(BuildContext context) {
    final (name, handle, text, hue) = _posts.first;
    final muted = Theme.of(context).colorScheme.onSurfaceVariant;
    final line = BorderSide(color: Theme.of(context).dividerColor);
    return Scaffold(
      appBar: AppBar(title: const Text('Post')),
      body: ListView(
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 14, 16, 0),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Row(
                  children: [
                    _avatar(name, hue, 46),
                    const SizedBox(width: 12),
                    Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text(name, style: const TextStyle(fontWeight: FontWeight.w600)),
                        Text('@$handle', style: TextStyle(color: muted)),
                      ],
                    ),
                  ],
                ),
                const SizedBox(height: 12),
                Text(text, style: const TextStyle(fontSize: 18, height: 1.45)),
                _media(hue),
                const SizedBox(height: 14),
                Text('14:32 · Oct 28, 2026 · 2.1K views', style: TextStyle(color: muted)),
                Container(
                  margin: const EdgeInsets.only(top: 12),
                  padding: const EdgeInsets.symmetric(vertical: 12),
                  decoration: BoxDecoration(
                    border: Border(top: line, bottom: line),
                  ),
                  child: const Text('4 reposts    88 likes'),
                ),
              ],
            ),
          ),
          for (final (name, handle, reply, hue) in _replies)
            Container(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 12),
              decoration: BoxDecoration(border: Border(bottom: line)),
              child: Row(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  _avatar(name, hue, 32),
                  const SizedBox(width: 12),
                  Expanded(
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text.rich(
                          TextSpan(
                            children: [
                              TextSpan(
                                text: name,
                                style: const TextStyle(fontWeight: FontWeight.w600),
                              ),
                              TextSpan(
                                text: '  @$handle',
                                style: TextStyle(color: muted),
                              ),
                            ],
                          ),
                        ),
                        Text(reply),
                      ],
                    ),
                  ),
                ],
              ),
            ),
        ],
      ),
    );
  }
}

/// Where the finger is, drawn over the screen.
final _finger = ValueNotifier<Offset?>(null);

Future<void> _pump(WidgetTester tester, {required ThemeData theme, double ratio = 2, bool rtl = false}) async {
  tester.view.physicalSize = const Size(390, 844) * ratio;
  tester.view.devicePixelRatio = ratio;
  tester.view.padding = FakeViewPadding(top: 24 * ratio);
  addTearDown(tester.view.reset);
  _finger.value = null;
  await tester.pumpWidget(
    PrefService(
      service: PrefServiceCache(
        defaults: {optionDisableAnimations: false, optionSwipeBackAnywhere: true, optionSwipeBackTilt: true},
      ),
      child: MaterialApp(
        debugShowCheckedModeBanner: false,
        theme: theme,
        builder: (context, child) => RepaintBoundary(
          key: _shot,
          child: Directionality(
            textDirection: rtl ? TextDirection.rtl : TextDirection.ltr,
            child: Stack(
              children: [
                child!,
                ValueListenableBuilder<Offset?>(
                  valueListenable: _finger,
                  builder: (context, finger, _) => finger == null
                      ? const SizedBox.shrink()
                      : Positioned(
                          left: finger.dx - 20,
                          top: finger.dy - 20,
                          child: IgnorePointer(
                            child: Container(
                              width: 40,
                              height: 40,
                              decoration: BoxDecoration(
                                shape: BoxShape.circle,
                                color: Colors.white.withValues(alpha: 0.45),
                                border: Border.all(color: Colors.black.withValues(alpha: 0.35), width: 1.5),
                              ),
                            ),
                          ),
                        ),
                ),
              ],
            ),
          ),
        ),
        onGenerateRoute: (settings) => switch (settings.name) {
          '/post' => XtaCardPageRoute<dynamic>(settings: settings, builder: (_) => const _Post()),
          _ => MaterialPageRoute<dynamic>(settings: settings, builder: (_) => const _Feed()),
        },
      ),
    ),
  );
  await tester.pumpAndSettle();
}

final _light = xLookThemeData(XLookTokens.light, null).copyWith(platform: TargetPlatform.android);
final _dark = xLookThemeData(XLookTokens.lightsOut, null).copyWith(platform: TargetPlatform.android);

Future<void> _openPost(WidgetTester tester) async {
  await tester.tap(find.text(_posts.first.$3));
  await tester.pumpAndSettle();
}

/// A drag by [by] over [frames] frames of [frame], the finger drawn.
Future<TestGesture> _dragBy(
  WidgetTester tester,
  Offset from,
  Offset by, {
  int frames = 10,
  Duration frame = const Duration(milliseconds: 16),
  Future<void> Function()? onFrame,
}) async {
  final gesture = await tester.startGesture(from);
  _finger.value = from;
  var time = Duration.zero;
  for (var i = 1; i <= frames; i++) {
    time += frame;
    final at = from + by * (i / frames);
    await gesture.moveTo(at, timeStamp: time);
    _finger.value = at;
    await tester.pump(frame);
    await onFrame?.call();
  }
  return gesture;
}

void main() {
  setUpAll(() async {
    autoUpdateGoldenFiles = true;
    for (final weight in ['Regular', 'Medium', 'SemiBold', 'Bold']) {
      await (FontLoader('Inter')..addFont(rootBundle.load('assets/fonts/Inter-$weight.ttf'))).load();
    }
    await (FontLoader('MaterialIcons')..addFont(rootBundle.load('fonts/MaterialIcons-Regular.otf'))).load();
  });

  Future<void> still(WidgetTester tester, String name) =>
      expectLater(find.byKey(_shot), matchesGoldenFile('$_out/swipe-back-$name.png'));

  for (final (tag, theme) in [('light', _light), ('dark', _dark)]) {
    testWidgets('swipe-back-stills-$tag', skip: !_render, (tester) async {
      await _pump(tester, theme: theme);
      await tester.tap(find.text(_posts.first.$3));
      await tester.pump();
      await tester.pump(const Duration(milliseconds: 90));
      await still(tester, 'opening-$tag');
      await tester.pumpAndSettle();

      var gesture = await _dragBy(tester, const Offset(110, 250), const Offset(110, 20));
      await still(tester, 'drag-top-$tag');
      await tester.pump(const Duration(milliseconds: 100));
      await gesture.up();
      _finger.value = null;
      await tester.pump(const Duration(milliseconds: 50));
      await still(tester, 'dropping-back-$tag');
      await tester.pumpAndSettle();

      gesture = await _dragBy(tester, const Offset(150, 640), const Offset(170, -20));
      await still(tester, 'drag-bottom-$tag');
      await gesture.up();
      _finger.value = null;
      await tester.pumpAndSettle();
      await _openPost(tester);

      gesture = await _dragBy(tester, const Offset(120, 300), const Offset(110, 30), frames: 4);
      await gesture.up();
      _finger.value = null;
      await tester.pump(const Duration(milliseconds: 80));
      await still(tester, 'thrown-$tag');
      await tester.pumpAndSettle();
      expect(find.text('Post'), findsNothing);
    });
  }

  testWidgets('swipe-back-stills-rtl', skip: !_render, (tester) async {
    await _pump(tester, theme: _light, rtl: true);
    await _openPost(tester);
    final gesture = await _dragBy(tester, const Offset(280, 250), const Offset(-150, 24));
    await still(tester, 'drag-rtl-light');
    await gesture.up();
    _finger.value = null;
    await tester.pumpAndSettle();
  });

  testWidgets('swipe-back-clip', skip: !_render, (tester) async {
    var index = 0;
    Future<void> frame() => expectLater(
      find.byKey(_shot),
      matchesGoldenFile('$_out/swipe-back-clip/f${(index++).toString().padLeft(3, '0')}.png'),
    );
    const step = Duration(microseconds: 33333);
    Future<void> hold(int frames) async {
      for (var i = 0; i < frames; i++) {
        await tester.pump(step);
        await frame();
      }
    }

    await _pump(tester, theme: _light, ratio: 1);
    await hold(10);
    await tester.tap(find.text(_posts.first.$3));
    await tester.pump();
    await hold(16);

    var gesture = await _dragBy(
      tester,
      const Offset(110, 250),
      const Offset(90, 16),
      frames: 12,
      frame: step,
      onFrame: frame,
    );
    await hold(6);
    await gesture.up();
    _finger.value = null;
    await hold(16);

    gesture = await _dragBy(
      tester,
      const Offset(140, 620),
      const Offset(150, -12),
      frames: 5,
      frame: step,
      onFrame: frame,
    );
    await gesture.up();
    _finger.value = null;
    await hold(18);
  });
}
