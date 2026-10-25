import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/_video.dart';

void main() {
  test('Android gets AAudio with an OpenSL ES fallback, and MediaCodec decoding', () {
    expect(videoDriverProperties(android: true, directHwdec: false), {
      'ao': 'aaudio,opensles',
      'hwdec': 'mediacodec-copy',
    });
    expect(videoDriverProperties(android: true, directHwdec: true)['hwdec'], 'mediacodec');
  });

  test('elsewhere libmpv keeps its own audio output and decoder', () {
    expect(videoDriverProperties(android: false, directHwdec: false), isEmpty);
    expect(videoDriverProperties(android: false, directHwdec: true), isEmpty, reason: 'the switch is Android-only');
  });
}
