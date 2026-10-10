import 'package:dart_twitter_api/twitter_api.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:xta/tweet/_media.dart';

Media _media(String type, List<Map<String, dynamic>> variants) => Media.fromJson({
  'id_str': '1',
  'type': type,
  'media_url_https': 'https://pbs.twimg.com/tweet_video_thumb/poster.jpg',
  'video_info': {
    'aspect_ratio': [1, 1],
    'variants': variants,
  },
});

const _gifMp4 = 'https://video.twimg.com/tweet_video/loop.mp4';
const _high = 'https://video.twimg.com/ext_tw_video/1/pu/vid/1280x720/high.mp4';
const _low = 'https://video.twimg.com/ext_tw_video/1/pu/vid/480x270/low.mp4';
const _hls = 'https://video.twimg.com/ext_tw_video/1/pu/pl/master.m3u8';

void main() {
  test('a GIF saves its MP4, not the poster frame', () {
    final gif = _media('animated_gif', [
      {'bitrate': 0, 'content_type': 'video/mp4', 'url': _gifMp4},
    ]);
    expect(viewerVideoDownloadUrl(gif), _gifMp4);
  });

  test('a video saves the variant its player is on, else the best MP4', () {
    final video = _media('video', [
      {'content_type': 'application/x-mpegURL', 'url': _hls},
      {'bitrate': 256000, 'content_type': 'video/mp4', 'url': _low},
      {'bitrate': 2176000, 'content_type': 'video/mp4', 'url': _high},
    ]);
    expect(viewerVideoDownloadUrl(video, playingUrl: _low), _low);
    expect(viewerVideoDownloadUrl(video, playingUrl: _hls), _high);
    expect(viewerVideoDownloadUrl(video), _high);
  });

  test('a photo, or a stream with no MP4, has no video to save', () {
    expect(viewerVideoDownloadUrl(_media('photo', const [])), isNull);
    expect(
      viewerVideoDownloadUrl(
        _media('video', [
          {'content_type': 'application/x-mpegURL', 'url': _hls},
        ]),
      ),
      isNull,
    );
  });
}
