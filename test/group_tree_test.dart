import 'package:flutter_test/flutter_test.dart';
import 'package:xta/group/group_tree.dart';

/// news
///  ├─ tech
///  │   └─ ai
///  └─ sport
/// friends
const _parents = <String, String?>{
  'news': null,
  'tech': 'news',
  'ai': 'tech',
  'sport': 'news',
  'friends': null,
};

void main() {
  group('what a group\'s feed is made of', () {
    test('a parent takes everything nested inside it, however deep', () {
      expect(groupAndDescendants('news', _parents), {
        'news',
        'tech',
        'ai',
        'sport',
      });
    });

    test('a middle group takes only its own branch', () {
      expect(groupAndDescendants('tech', _parents), {'tech', 'ai'});
    });

    test('a group with nothing inside it is just itself', () {
      expect(groupAndDescendants('friends', _parents), {'friends'});
    });

    test('an id nobody has heard of is still itself', () {
      expect(groupAndDescendants('nowhere', _parents), {'nowhere'});
    });

    test('a loop in stored data resolves instead of hanging', () {
      final looped = {'a': 'b', 'b': 'a'};

      expect(groupAndDescendants('a', looped), {'a', 'b'});
    });
  });

  group('children', () {
    test('are the groups directly inside', () {
      expect(
        childrenOf('news', _parents),
        containsAll(<String>['tech', 'sport']),
      );
      expect(childrenOf('news', _parents), isNot(contains('ai')));
    });

    test('none for a leaf', () {
      expect(childrenOf('ai', _parents), isEmpty);
    });
  });

  group('what the board shows', () {
    test('only the groups that stand on their own', () {
      expect(topLevelGroups(_parents.keys, _parents), ['news', 'friends']);
    });

    test(
      'a group whose parent was deleted comes back to the top rather than vanishing',
      () {
        final orphaned = {'orphan': 'deleted-group', 'kept': null};

        expect(topLevelGroups(orphaned.keys, orphaned), ['orphan', 'kept']);
      },
    );

    test('a group that somehow parents itself is treated as top level', () {
      expect(topLevelGroups(['self'], {'self': 'self'}), ['self']);
    });
  });

  group('refusing a nesting that would loop', () {
    test('a group cannot go inside itself', () {
      expect(wouldNestInsideItself('news', 'news', _parents), isTrue);
    });

    test('a group cannot go inside its own descendant', () {
      expect(
        wouldNestInsideItself('news', 'ai', _parents),
        isTrue,
        reason: 'ai already sits under news',
      );
    });

    test('an unrelated pair is fine', () {
      expect(wouldNestInsideItself('friends', 'tech', _parents), isFalse);
    });

    test('moving a child up to a sibling branch is fine', () {
      expect(wouldNestInsideItself('ai', 'sport', _parents), isFalse);
    });
  });

  group('depth', () {
    test('counts from zero at the top', () {
      expect(depthOf('news', _parents), 0);
      expect(depthOf('tech', _parents), 1);
      expect(depthOf('ai', _parents), 2);
    });

    test('a loop stops rather than counting forever', () {
      expect(depthOf('a', {'a': 'b', 'b': 'a'}), lessThan(3));
    });
  });

  group('tree order', () {
    test('a child follows its parent instead of vanishing', () {
      const parents = {'a': null, 'b': 'a', 'c': null};

      expect(groupsInTreeOrder(['a', 'b', 'c'], parents), ['a', 'b', 'c']);
    });

    test('depth follows depth, not the order the ids arrived in', () {
      const parents = {'a': null, 'b': 'a', 'c': 'b', 'd': null};

      expect(groupsInTreeOrder(['d', 'c', 'b', 'a'], parents), [
        'd',
        'a',
        'b',
        'c',
      ]);
    });

    test('every group appears exactly once, cycle or no cycle', () {
      const parents = {'a': 'b', 'b': 'a', 'c': null};

      final ordered = groupsInTreeOrder(['a', 'b', 'c'], parents);
      expect(ordered.toSet(), {'a', 'b', 'c'});
      expect(ordered, hasLength(3));
    });

    test('a group whose parent is filtered out is still shown', () {
      const parents = {'a': null, 'b': 'a'};

      expect(groupsInTreeOrder(['b'], parents), ['b']);
    });
  });

  group('partitionNsfwGroups', () {
    test('keeps relative order and sinks NSFW to the second list', () {
      final parts = partitionNsfwGroups([
        'a',
        'nsfw1',
        'b',
        'nsfw2',
        'c',
      ], (id) => id.startsWith('nsfw'));

      expect(parts.safe, ['a', 'b', 'c']);
      expect(parts.nsfw, ['nsfw1', 'nsfw2']);
    });

    test('an all-safe list has an empty NSFW section', () {
      final parts = partitionNsfwGroups(['a', 'b'], (_) => false);
      expect(parts.safe, ['a', 'b']);
      expect(parts.nsfw, isEmpty);
    });
  });
}
