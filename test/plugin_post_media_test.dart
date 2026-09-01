import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/plugin_post_media.dart';

void main() {
  group('clampPluginMediaAspect', () {
    test('defaults when the ratio is missing or nonsense', () {
      expect(clampPluginMediaAspect(null), 16 / 9);
      expect(clampPluginMediaAspect(0), 16 / 9);
      expect(clampPluginMediaAspect(-1), 16 / 9);
      expect(clampPluginMediaAspect(double.nan), 16 / 9);
    });

    test('clamps extreme portraits and banners', () {
      expect(clampPluginMediaAspect(0.2), 0.45);
      expect(clampPluginMediaAspect(4), 2.4);
      expect(clampPluginMediaAspect(1.5), 1.5);
    });
  });

  group('pluginMediaAspectFrom', () {
    test('reads width/height and a precomputed aspect', () {
      expect(
        pluginMediaAspectFrom({'width': 2200, 'height': 1312}),
        closeTo(2200 / 1312, 0.001),
      );
      expect(pluginMediaAspectFrom({'aspect': 1.777}), closeTo(1.777, 0.001));
      expect(pluginMediaAspectFrom(null), isNull);
      expect(pluginMediaAspectFrom({'width': 0, 'height': 10}), isNull);
    });
  });

  test('pluginMediaItemsFrom pairs urls with aspects and video flags', () {
    final items = pluginMediaItemsFrom(
      urls: ['a.jpg', 'b.jpg'],
      aspects: [1.5],
      videos: [false, true],
    );
    expect(items, hasLength(2));
    expect(items.first.aspectRatio, 1.5);
    expect(items.last.isVideo, isTrue);
    expect(items.last.aspectRatio, isNull);
  });
}
