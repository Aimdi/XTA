import 'dart:convert';

import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_visits.dart';

BooruPost _post(String id, {String host = 'https://danbooru.donmai.us'}) => BooruPost(
  id: id,
  host: host,
  engine: 'danbooru',
  tags: const [],
  rating: BooruRating.general,
  score: 0,
  width: 1,
  height: 1,
  previewUrl: null,
  sampleUrl: null,
  fileUrl: null,
  fileExt: 'jpg',
  source: null,
  createdAt: null,
);

void main() {
  group('posts new since the last visit', () {
    test('nothing is new on a site not visited before', () {
      expect(booruPostIsNew(const {}, _post('9')), isFalse);
      expect(booruPostIsNew(const {'https://e621.net': 3}, _post('9')), isFalse);
    });

    test('a post above the newest seen on its site is new', () {
      const since = {'https://danbooru.donmai.us': 10};
      expect(booruPostIsNew(since, _post('11')), isTrue);
      expect(booruPostIsNew(since, _post('10')), isFalse);
      expect(booruPostIsNew(since, _post('9')), isFalse);
      expect(booruPostIsNew(since, _post('abc')), isFalse);
    });

    test('a visit keeps each site\'s newest post seen', () async {
      final prefs = PrefServiceCache(
        cache: {
          optionPluginBooruFollowingSeen: jsonEncode({'https://danbooru.donmai.us': 20, 'https://e621.net': 5}),
        },
      );
      await booruMarkSeen(prefs, [
        _post('12'),
        _post('30', host: 'https://yande.re'),
        _post('7', host: 'https://e621.net'),
      ]);
      expect(booruSeenIds(prefs), {'https://danbooru.donmai.us': 20, 'https://e621.net': 7, 'https://yande.re': 30});
    });

    test('seeing nothing writes nothing', () async {
      final prefs = PrefServiceCache(cache: {optionPluginBooruFollowingSeen: '{}'});
      await booruMarkSeen(prefs, const []);
      expect(prefs.get<String>(optionPluginBooruFollowingSeen), '{}');
    });

    test('a broken record reads as no visit', () {
      expect(booruSeenIds(PrefServiceCache(cache: {optionPluginBooruFollowingSeen: 'nope'})), isEmpty);
      expect(booruSeenIds(PrefServiceCache(cache: {optionPluginBooruFollowingSeen: '{"a":"b","c":4}'})), {'c': 4});
      expect(booruSeenIds(PrefServiceCache()), isEmpty);
    });
  });
}
