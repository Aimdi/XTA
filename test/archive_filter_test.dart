import 'package:flutter_test/flutter_test.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/profile/archive_filter.dart';

SavedTweet _saved(String id, {String user = 'u1'}) =>
    SavedTweet(id: id, user: user, content: 'saved-$id');

LikedTweet _liked(String id, {String user = 'u1'}) =>
    LikedTweet(id: id, user: user, content: 'liked-$id');

void main() {
  const user = 'u1';
  final saved = [_saved('s1'), _saved('both'), _saved('s2', user: 'other')];
  final liked = [_liked('l1'), _liked('both'), _liked('l2', user: 'other')];

  test('bookmarks keeps this author\'s saves', () {
    final items = profileArchiveItems(
      saved: saved,
      liked: liked,
      userId: user,
      filter: ArchiveFilter.bookmarks,
    );
    expect(items.map((e) => e.id), ['s1', 'both']);
  });

  test('likes keeps this author\'s hearts', () {
    final items = profileArchiveItems(
      saved: saved,
      liked: liked,
      userId: user,
      filter: ArchiveFilter.likes,
    );
    expect(items.map((e) => e.id), ['l1', 'both']);
  });

  test('all is saves then hearts, once each', () {
    final items = profileArchiveItems(
      saved: saved,
      liked: liked,
      userId: user,
      filter: ArchiveFilter.all,
    );
    expect(items.map((e) => e.id), ['s1', 'both', 'l1']);
  });

  test('another author is empty', () {
    expect(
      profileArchiveItems(
        saved: saved,
        liked: liked,
        userId: 'nobody',
        filter: ArchiveFilter.all,
      ),
      isEmpty,
    );
  });
}
