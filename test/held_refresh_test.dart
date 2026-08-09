import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/held_refresh.dart';

void main() {
  group('a refresh the reader has not asked for', () {
    test('runs straight away when they are at the top', () {
      final held = HeldRefresh();

      expect(held.request(atTop: true), isTrue);
      expect(held.isPending, isFalse);
    });

    // The bug: adding someone to a group refetches the feed, which empties the
    // list and jumps to the top — losing the place of a reader who was deep in
    // it and had not asked for anything.
    test('waits when they are reading further down', () {
      final held = HeldRefresh();

      expect(held.request(atTop: false), isFalse);
      expect(held.isPending, isTrue);
    });

    test('runs when they scroll back to the top', () {
      final held = HeldRefresh()..request(atTop: false);

      expect(held.returnedToTop(), isTrue);
      expect(held.isPending, isFalse);
    });

    test('runs only once, however often they return to the top', () {
      final held = HeldRefresh()..request(atTop: false);

      expect(held.returnedToTop(), isTrue);
      expect(held.returnedToTop(), isFalse);
    });

    test('returning to the top with nothing held does nothing', () {
      expect(HeldRefresh().returnedToTop(), isFalse);
    });

    test('several changes while reading still cost one refresh', () {
      final held = HeldRefresh();

      held.request(atTop: false);
      held.request(atTop: false);
      held.request(atTop: false);

      expect(held.returnedToTop(), isTrue);
      expect(held.returnedToTop(), isFalse);
    });

    test('a change while at the top clears anything already held', () {
      final held = HeldRefresh()..request(atTop: false);

      expect(held.request(atTop: true), isTrue);
      expect(held.isPending, isFalse);
    });
  });
}
