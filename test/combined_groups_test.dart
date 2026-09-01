import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/combined_groups.dart';
import 'package:xta/group/group_tree.dart';

void main() {
  group('groups read together', () {
    test('nothing is combined to begin with', () {
      expect(CombinedGroupsStore().state, isEmpty);
    });

    test('holding one adds it, holding it again takes it back out', () {
      final store = CombinedGroupsStore();

      store.toggle('a');
      expect(store.state, {'a'});

      store.toggle('b');
      expect(store.state, {'a', 'b'});

      store.toggle('a');
      expect(store.state, {'b'});
    });

    test('clearing drops the lot', () {
      final store = CombinedGroupsStore()
        ..toggle('a')
        ..toggle('b');

      store.clear();

      expect(store.state, isEmpty);
    });

    test('a group cannot be read alongside itself', () {
      final store = CombinedGroupsStore()..toggle('a');

      store.release('a');

      expect(store.state, isEmpty);
    });

    test('releasing one that is not in the combination changes nothing', () {
      final store = CombinedGroupsStore()..toggle('a');

      store.release('b');

      expect(store.state, {'a'});
    });
  });

  // Combining is the same question the feed already asks for nesting, put to
  // more roots — so a combined group brings whatever is nested inside it.
  group('what a combination actually covers', () {
    const parents = {'child': 'a', 'grandchild': 'child', 'other': null, 'b': null, 'a': null};

    test('each combined group brings everything nested inside it', () {
      final ids = {
        ...groupAndDescendants('a', parents),
        ...groupAndDescendants('b', parents),
      };

      expect(ids, {'a', 'child', 'grandchild', 'b'});
    });

    test('groups that share nothing stay separate', () {
      expect(groupAndDescendants('other', parents), {'other'});
    });
  });
}
