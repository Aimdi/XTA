import 'package:xta/database/entities.dart';

/// Which on-device posts a profile's Archive tab shows.
enum ArchiveFilter {
  all,
  likes,
  bookmarks;

  bool get isAll => this == ArchiveFilter.all;
}

class ArchiveItem {
  final String id;
  final String? content;

  const ArchiveItem({required this.id, this.content});
}

/// Local likes and bookmarks for [userId], in the order the Archive tab shows.
///
/// Bookmarks keep their saved order, then likes that are not already bookmarked.
/// Both tables store the *author* id — never an X account.
List<ArchiveItem> profileArchiveItems({
  required Iterable<SavedTweet> saved,
  required Iterable<LikedTweet> liked,
  required String userId,
  required ArchiveFilter filter,
}) {
  final savedFor = saved.where((e) => e.user == userId);
  final likedFor = liked.where((e) => e.user == userId);

  List<ArchiveItem> fromSaved() => [
        for (final e in savedFor) ArchiveItem(id: e.id, content: e.content),
      ];
  List<ArchiveItem> fromLiked() => [
        for (final e in likedFor) ArchiveItem(id: e.id, content: e.content),
      ];

  return switch (filter) {
    ArchiveFilter.bookmarks => fromSaved(),
    ArchiveFilter.likes => fromLiked(),
    ArchiveFilter.all => _deduped([...fromSaved(), ...fromLiked()]),
  };
}

List<ArchiveItem> _deduped(List<ArchiveItem> items) {
  final seen = <String>{};
  return [for (final item in items) if (seen.add(item.id)) item];
}
