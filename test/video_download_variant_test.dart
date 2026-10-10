import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/video_quality.dart';

void main() {
  const high = 'https://video.twimg.com/ext_tw_video/1/pu/vid/1280x720/high.mp4';
  const low = 'https://video.twimg.com/ext_tw_video/1/pu/vid/480x270/low.mp4';
  const qualities = [TweetVideoQuality(high, '720p'), TweetVideoQuality(low, '270p')];

  test('saves the quality being watched', () {
    expect(downloadUrlFor(low, qualities, high), low);
    expect(downloadUrlFor(high, qualities, high), high);
  });

  test('falls back to the best file when the player is on HLS or nothing known', () {
    expect(downloadUrlFor('https://video.twimg.com/1/pl/master.m3u8', qualities, high), high);
    expect(downloadUrlFor(null, qualities, high), high);
    expect(downloadUrlFor(low, const [], high), high);
  });

  test('a stream without an MP4 variant has nothing to save', () {
    expect(downloadUrlFor('https://video.twimg.com/1/pl/master.m3u8', const [], null), isNull);
  });
}
