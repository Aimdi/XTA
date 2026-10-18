import 'package:flutter_test/flutter_test.dart';
import 'package:xta/media/podcast_places.dart';

String _url(int i) => 'https://cdn.example/$i.mp3';

void main() {
  test('places keep the newest 50 episodes, newest first, and round-trip', () {
    var places = PodcastPlaces.empty;
    for (var i = 0; i < 60; i++) {
      places = places.leftAt(_url(i), Duration(seconds: i + 1), const Duration(hours: 1));
    }
    expect(places.length, podcastPlacesLimit);
    expect(places.of(_url(9)), isNull, reason: 'the oldest are dropped');
    final back = PodcastPlaces.decode(places.encode());
    expect(back.of(_url(59))?.position, const Duration(seconds: 60));
    expect(back.length, podcastPlacesLimit);
  });

  test('a place at the very start is no place, and damaged entries are skipped', () {
    final places = PodcastPlaces.empty
        .leftAt(_url(1), const Duration(seconds: 5), Duration.zero)
        .leftAt(_url(1), Duration.zero, Duration.zero);
    expect(places.of(_url(1)), isNull);

    final read = PodcastPlaces.decode(
      '[{"url":"ftp://x/a.mp3","position":5},{"url":"${_url(2)}","position":"9000"},{"url":7},'
      '{"url":"${_url(3)}","position":-1}]',
    );
    expect(read.length, 1);
    expect(read.of(_url(2))?.position, const Duration(seconds: 9));
    expect(PodcastPlaces.decode('not json').length, 0);
    expect(PodcastPlaces.decode('{"a":1}').length, 0);
  });
}
