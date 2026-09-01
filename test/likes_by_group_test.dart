import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/likes_by_group.dart';

/// A like, reduced to what the grouping cares about.
typedef Like = ({String id, String? author});

SubscriptionGroupMember _member(String group, String profile) =>
    SubscriptionGroupMember(group: group, profile: profile);

List<LikesSection<Like>> _split(List<Like> likes, List<SubscriptionGroupMember> members, List<String> groups) =>
    likesByGroup<Like>(likes, authorOf: (l) => l.author, members: members, groupIds: groups);

void main() {
  const alice = (id: 'a', author: 'u1');
  const bob = (id: 'b', author: 'u2');
  const stranger = (id: 'c', author: 'u9');

  test('each group gets its own section, in the order given', () {
    final sections = _split(
      [alice, bob],
      [_member('news', 'u1'), _member('friends', 'u2')],
      ['friends', 'news'],
    );

    expect(sections.map((s) => s.groupId), ['friends', 'news']);
    expect(sections.first.items.single.id, 'b');
    expect(sections.last.items.single.id, 'a');
  });

  test('an author in two groups appears under both', () {
    // Picking one arbitrarily would hide the post from the other group.
    final sections = _split(
      [alice],
      [_member('news', 'u1'), _member('tech', 'u1')],
      ['news', 'tech'],
    );

    expect(sections, hasLength(2));
    expect(sections.every((s) => s.items.single.id == 'a'), isTrue);
  });

  test('a like from nobody\'s group lands in the ungrouped section, last', () {
    final sections = _split([alice, stranger], [_member('news', 'u1')], ['news']);

    expect(sections.map((s) => s.groupId), ['news', null]);
    expect(sections.last.isUngrouped, isTrue);
    expect(sections.last.items.single.id, 'c');
  });

  test('a like with no author recorded is ungrouped rather than dropped', () {
    const anonymous = (id: 'd', author: null);

    final sections = _split([anonymous], [_member('news', 'u1')], ['news']);

    expect(sections.single.isUngrouped, isTrue);
    expect(sections.single.items.single.id, 'd');
  });

  test('a group nobody in it has liked from is left out entirely', () {
    final sections = _split([alice], [_member('news', 'u1')], ['news', 'empty']);

    expect(sections.map((s) => s.groupId), ['news']);
  });

  test('an author whose only group is not being shown counts as ungrouped', () {
    final sections = _split([alice], [_member('hidden', 'u1')], ['news']);

    expect(sections.single.isUngrouped, isTrue);
  });

  test('no likes means no sections at all', () {
    expect(_split(const [], [_member('news', 'u1')], ['news']), isEmpty);
  });

  test('no groups puts everything under ungrouped', () {
    final sections = _split([alice, bob], const [], const []);

    expect(sections.single.isUngrouped, isTrue);
    expect(sections.single.items, hasLength(2));
  });
}
