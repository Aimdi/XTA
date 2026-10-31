import 'package:flutter_test/flutter_test.dart';
import 'package:xta/plugins/booru/booru_tag_limit.dart';

void _expectSplit(BooruTagSplit? split, {required List<String> host, required List<String> device}) {
  expect(split?.host, host);
  expect(split?.device, device);
}

void main() {
  group('what counts', () {
    test('tags, excluded and either tags and order: count; the metatags Danbooru lets through do not', () {
      expect(
        booruCountedTokens([
          'blue_eyes',
          '-smile',
          '~hat',
          'order:score',
          'rating:g',
          '-rating:e',
          'rating:g,s',
          'status:active',
          '-status:deleted',
          'limit:5',
          'is:sfw',
          'parent:none',
          'filetype:png',
          'score:>10',
          'age:<1w',
        ]),
        4,
      );
    });

    test('a metatag it was not seen letting through counts, to be safe', () {
      expect(booruCountedTokens(['ordfav:someone', 'pool:123', 'user:someone']), 3);
    });
  });

  group('sharing a search out', () {
    const uses = {'blue_eyes': 2524807, 'long_hair': 6357605, 'frieren': 16526};

    test('a search that fits goes to the host whole', () {
      _expectSplit(
        splitBooruQuery(['blue_eyes', 'long_hair', 'rating:g'], limit: 2, uses: uses),
        host: ['blue_eyes', 'long_hair', 'rating:g'],
        device: [],
      );
    });

    test('the host gets the tags with the fewest posts, in the order they were typed', () {
      _expectSplit(
        splitBooruQuery(['blue_eyes', 'long_hair', 'frieren'], limit: 2, uses: uses),
        host: ['blue_eyes', 'frieren'],
        device: ['long_hair'],
      );
    });

    test('without counts the tags typed first go to the host', () {
      _expectSplit(
        splitBooruQuery(['long_hair', 'blue_eyes', 'frieren'], limit: 2),
        host: ['long_hair', 'blue_eyes'],
        device: ['frieren'],
      );
    });

    test('a tag the host does not list, an alias or a typo, goes to the host first', () {
      _expectSplit(
        splitBooruQuery(['blue_eyes', 'long_hair', 'kancolle'], limit: 2, uses: uses),
        host: ['blue_eyes', 'kancolle'],
        device: ['long_hair'],
      );
    });

    test('metatags stay with the host and take their room; free ones take none', () {
      _expectSplit(
        splitBooruQuery(['blue_eyes', 'frieren', 'order:score', 'rating:g'], limit: 2, uses: uses),
        host: ['frieren', 'order:score', 'rating:g'],
        device: ['blue_eyes'],
      );
    });

    test('a wildcard stays with the host: the device cannot match it by name', () {
      _expectSplit(
        splitBooruQuery(['blue*', 'long_hair', 'frieren'], limit: 2, uses: uses),
        host: ['blue*', 'frieren'],
        device: ['long_hair'],
      );
    });

    test('an either group stays together, on the host when it fits and on the device when not', () {
      _expectSplit(
        splitBooruQuery(['blue_eyes', '~hat', '~cap'], limit: 2, uses: uses),
        host: ['blue_eyes'],
        device: ['~hat', '~cap'],
      );
      _expectSplit(splitBooruQuery(['~hat', '~cap', '-smile'], limit: 2), host: ['~hat', '~cap'], device: ['-smile']);
    });

    test('excluded tags go to the host last', () {
      _expectSplit(
        splitBooruQuery(['-smile', 'blue_eyes', '-hat'], limit: 2, uses: uses),
        host: ['-smile', 'blue_eyes'],
        device: ['-hat'],
      );
    });

    test('nothing can be shared out when the metatags alone are more than the host takes', () {
      expect(splitBooruQuery(['order:score', 'ordfav:someone', 'blue_eyes'], limit: 1), isNull);
    });
  });

  group("the host's limit", () {
    test('is read from what Danbooru says', () {
      expect(
        booruTagLimitIn(
          '{"success":false,"error":"PostQuery::TagLimitError",'
          '"message":"You cannot search for more than 2 tags at a time."}',
        ),
        2,
      );
      expect(booruTagLimitIn('You cannot search for more than 6 tags at a time.'), 6);
    });

    test("is Danbooru's lowest when its error names no number, and unknown for anything else", () {
      expect(booruTagLimitIn('{"error":"PostQuery::TagLimitError"}'), 2);
      expect(booruTagLimitIn('{"error":"Something else"}'), isNull);
    });
  });
}
