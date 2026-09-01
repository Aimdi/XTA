import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/immich/immich_media.dart';

MediaCandidate _photo({String? url = 'https://pbs.twimg.com/media/AbC123.jpg'}) =>
    MediaCandidate(type: 'photo', imageUrl: url);

MediaCandidate _video({
  String? thumb = 'https://pbs.twimg.com/ext_tw_video_thumb/1/pu/img/T.jpg',
  List<MediaVariant> variants = const [
    (url: 'https://video.twimg.com/x/320x180/low.mp4', contentType: 'video/mp4', bitrate: 256000),
    (url: 'https://video.twimg.com/x/1280x720/high.mp4', contentType: 'video/mp4', bitrate: 2176000),
    (url: 'https://video.twimg.com/x/pl/master.m3u8', contentType: 'application/x-mpegURL', bitrate: null),
  ],
  String type = 'video',
}) => MediaCandidate(type: type, imageUrl: thumb, variants: variants);

void main() {
  group('what a photo turns into', () {
    test('the original is asked for, not the display copy', () {
      final media = uploadableMedia([_photo()]).single;

      expect(media.url, endsWith('/AbC123.jpg:orig'));
      expect(media.isVideo, isFalse);
    });

    test('the name is the file, without the size suffix', () {
      expect(uploadableMedia([_photo()]).single.fileName, 'AbC123.jpg');
    });

    test('the id is the plain URL, so :orig does not change it', () {
      final media = uploadableMedia([_photo()]).single;

      expect(media.id, 'https://pbs.twimg.com/media/AbC123.jpg');
      expect(media.id, isNot(media.url));
    });

    test('a photo the response carried no URL for is skipped, not uploaded empty', () {
      expect(uploadableMedia([_photo(url: null), _photo(url: '')]), isEmpty);
    });
  });

  group('what a video turns into', () {
    test('the best MP4 wins, and the playlist is never chosen', () {
      final media = uploadableMedia([_video()]).single;

      expect(media.url, contains('1280x720'));
      expect(media.url, isNot(contains('m3u8')));
      expect(media.isVideo, isTrue);
    });

    // Which variant wins depends on what the response offered on the day. If the
    // id moved with it, the same video would upload again at another bitrate.
    test('the id is the thumbnail, so it does not move with the variant', () {
      expect(uploadableMedia([_video()]).single.id, endsWith('/T.jpg'));
    });

    test('a GIF is a video by the time it is a file', () {
      final media = uploadableMedia([_video(type: 'animated_gif')]).single;

      expect(media.isVideo, isTrue);
      expect(media.url, endsWith('.mp4'));
    });

    test('a variant list with no usable MP4 is nothing to upload', () {
      expect(
        uploadableMedia([
          _video(variants: const [
            (url: 'https://video.twimg.com/x/pl/master.m3u8', contentType: 'application/x-mpegURL', bitrate: null),
            (url: 'https://video.twimg.com/x/no-bitrate.mp4', contentType: 'video/mp4', bitrate: null),
          ])
        ]),
        isEmpty,
      );
    });

    test('a video with no thumbnail falls back to its own URL for an id', () {
      expect(uploadableMedia([_video(thumb: null)]).single.id, contains('1280x720'));
    });
  });

  group('what gets left out', () {
    test('videos are skipped when only photos are wanted', () {
      final media = uploadableMedia([_photo(), _video()], includeVideos: false);

      expect(media, hasLength(1));
      expect(media.single.isVideo, isFalse);
    });

    test('a kind nobody knows is passed over rather than guessed at', () {
      expect(uploadableMedia([const MediaCandidate(type: 'broadcast', imageUrl: 'https://x/y.jpg')]), isEmpty);
      expect(uploadableMedia([const MediaCandidate(type: null, imageUrl: 'https://x/y.jpg')]), isEmpty);
    });

    test('a post with nothing attached is nothing to do', () {
      expect(uploadableMedia(const []), isEmpty);
    });
  });

  test('a post with several files keeps them all, in order', () {
    final media = uploadableMedia([_photo(url: 'https://pbs.twimg.com/media/one.jpg'), _video()]);

    expect(media.map((m) => m.isVideo), [false, true]);
  });
}
