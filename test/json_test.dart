import 'package:flutter_test/flutter_test.dart';
import 'package:xta/utils/json.dart';

void main() {
  const doc = Json({
    'data': {
      'legacy': {'favorite_count': 42, 'id_str': '169', 'price': '110.5', 'flag': true},
      'items': [
        {'name': 'first'},
        {'name': 'second'},
      ],
    },
  });

  group('stepping into a document', () {
    test('a deep path needs no checks along the way', () {
      expect(doc['data']['legacy']['favorite_count'].integer, 42);
    });

    test('a missing key anywhere on the path is null at the end, never a throw', () {
      expect(doc['data']['nope']['deeper']['still'].integer, isNull);
      expect(doc['wrong'][3]['shape'].string, isNull);
    });

    test('lists are stepped into by index, and past the end is null', () {
      expect(doc['data']['items'][1]['name'].string, 'second');
      expect(doc['data']['items'][9]['name'].string, isNull);
      expect(doc['data']['items'][-1]['name'].string, isNull);
    });

    test('indexing a map or keying a list is a shape mismatch, so null', () {
      expect(doc['data'][0].exists, isFalse);
      expect(doc['data']['items']['name'].exists, isFalse);
    });

    test('a document that is not JSON at all answers every question with null', () {
      expect(const Json('nonsense')['chart']['result'][0].exists, isFalse);
      expect(const Json(null)['anything'].exists, isFalse);
    });
  });

  group('reading values out', () {
    test('each getter answers only for its own type', () {
      final legacy = doc['data']['legacy'];

      expect(legacy['flag'].boolean, isTrue);
      expect(legacy['favorite_count'].string, isNull, reason: 'a number is not a string');
      expect(legacy['id_str'].boolean, isNull);
    });

    test('numbers tolerate the string-wrapped digits X is fond of', () {
      final legacy = doc['data']['legacy'];

      expect(legacy['price'].number, 110.5);
      expect(legacy['id_str'].integer, 169);
      expect(legacy['favorite_count'].number, 42.0);
    });

    test('words are not numbers, however hopefully read', () {
      expect(const Json('threeish').number, isNull);
      expect(const Json('threeish').integer, isNull);
    });

    test('a list wraps its elements; anything else is an empty one', () {
      expect(doc['data']['items'].list, hasLength(2));
      expect(doc['data']['legacy'].list, isEmpty);
      expect(doc['absent'].list, isEmpty);
    });

    test('exists tells presence apart from a readable value', () {
      expect(doc['data'].exists, isTrue);
      expect(doc['data'].string, isNull, reason: 'present, but not a string');
      expect(doc['gone'].exists, isFalse);
    });
  });
}
