import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/profile/archive_filter.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/posts_filter.dart';

class ProfileViewState {
  final PostsFilter postsFilter;
  final MediaFilter mediaFilter;
  final ArchiveFilter archiveFilter;

  const ProfileViewState({
    this.postsFilter = PostsFilter.all,
    this.mediaFilter = MediaFilter.all,
    this.archiveFilter = ArchiveFilter.all,
  });

  ProfileViewState copyWith({
    PostsFilter? postsFilter,
    MediaFilter? mediaFilter,
    ArchiveFilter? archiveFilter,
  }) => ProfileViewState(
    postsFilter: postsFilter ?? this.postsFilter,
    mediaFilter: mediaFilter ?? this.mediaFilter,
    archiveFilter: archiveFilter ?? this.archiveFilter,
  );
}

class ProfileViewStore extends Store<ProfileViewState> {
  ProfileViewStore() : super(const ProfileViewState());

  void selectPostsFilter(PostsFilter value) =>
      update(state.copyWith(postsFilter: value));

  void selectMediaFilter(MediaFilter value) =>
      update(state.copyWith(mediaFilter: value));

  void selectArchiveFilter(ArchiveFilter value) =>
      update(state.copyWith(archiveFilter: value));
}

class ProfileScrollStore extends Store<bool> {
  ProfileScrollStore() : super(false);

  void showBackToTop(bool value) {
    if (state != value) update(value);
  }
}
