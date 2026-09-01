import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/media_quality.dart';

void main() {
  group('reading the stored quality', () {
    test('each stored word finds its quality', () {
      for (final quality in MediaQuality.values) {
        expect(MediaQuality.fromStored(quality.stored, fallback: MediaQuality.thumb), quality);
      }
    });

    test('a word the vocabulary does not know is the fallback, not a guess', () {
      expect(MediaQuality.fromStored('meduim', fallback: MediaQuality.large), MediaQuality.large);
      expect(MediaQuality.fromStored(null, fallback: MediaQuality.medium), MediaQuality.medium);
      expect(MediaQuality.fromStored('', fallback: MediaQuality.small), MediaQuality.small);
    });

    test('the retired disabled value reads as the fallback too', () {
      expect(MediaQuality.fromStored('disabled', fallback: MediaQuality.medium), MediaQuality.medium);
    });

    test('what is stored round-trips through the enum unchanged', () {
      // The dropdown writes `stored`, the readers parse it back; if these ever
      // disagree, the setting silently stops reaching the screens.
      expect(MediaQuality.values.map((q) => q.stored), ['thumb', 'small', 'medium', 'large']);
    });
  });
}
