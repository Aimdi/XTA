import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/saved_tab_order.dart';

void main() {
  test('built-in Notes tab is always present, after Favorites', () {
    final order = orderedSavedTabs(const [], null);
    expect(order.contains(savedTabNotes), isTrue);
    expect(
      order.indexOf(savedTabNotes),
      greaterThan(order.indexOf(savedTabFavorites)),
    );
  });

  test('Notes is a built-in, never a folder id', () {
    expect(isBuiltInSavedTab(savedTabNotes), isTrue);
    expect(isBuiltInSavedTab(savedTabAll), isTrue);
    expect(isBuiltInSavedTab('later'), isFalse);
  });

  test('stored order keeps Notes if the reader moved it', () {
    final folder = SavedTweetFolder(
      id: 'later',
      name: 'Later',
      createdAt: DateTime.utc(2026),
    );
    final order = orderedSavedTabs(
      [folder],
      '["notes","all","later","unfiled","favorites"]',
    );
    expect(order.first, savedTabNotes);
    expect(
      order,
      containsAll([savedTabAll, 'later', savedTabUnfiled, savedTabFavorites]),
    );
  });
}
