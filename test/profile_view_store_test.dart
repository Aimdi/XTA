import 'package:flutter_test/flutter_test.dart';
import 'package:xta/profile/archive_filter.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/posts_filter.dart';
import 'package:xta/profile/profile_view_store.dart';

void main() {
  test('profile filters update independently', () {
    final store = ProfileViewStore();
    addTearDown(store.destroy);

    store.selectPostsFilter(PostsFilter.retweets);
    store.selectMediaFilter(MediaFilter.videos);
    store.selectArchiveFilter(ArchiveFilter.likes);

    expect(store.state.postsFilter, PostsFilter.retweets);
    expect(store.state.mediaFilter, MediaFilter.videos);
    expect(store.state.archiveFilter, ArchiveFilter.likes);
  });

  test('back-to-top state only reflects the latest threshold result', () {
    final store = ProfileScrollStore();
    addTearDown(store.destroy);

    store.showBackToTop(true);
    expect(store.state, isTrue);
    store.showBackToTop(false);
    expect(store.state, isFalse);
  });
}
