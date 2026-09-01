import 'package:flutter_triple/flutter_triple.dart';
import 'package:quax/client/client.dart';
import 'package:quax/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:quax/user.dart';

class Profile {
  final UserWithExtra user;
  final List<String> pinnedTweets;

  Profile(this.user, this.pinnedTweets);
}

class ProfileModel extends Store<Profile> {
  ProfileModel() : super(Profile(UserWithExtra(), []));

  Future<void> loadProfileById(String id) async {
    await execute(() async => await Twitter.getProfileById(id));
  }

  Future<void> loadProfileByScreenName(String screenName) async {
    await execute(() async => await Twitter.getProfileByScreenName(screenName));
  }
}

/// Store-backed presentation state for the Profile shell.
///
/// Network/profile data stays in [ProfileModel]; this store only owns choices
/// made while reading a profile so the collapsing shell does not rely on local
/// `setState` flags.
class ProfileViewState {
  final int tabIndex;
  final MediaFilter mediaFilter;
  final bool showBackToTop;

  const ProfileViewState({
    required this.tabIndex,
    this.mediaFilter = MediaFilter.all,
    this.showBackToTop = false,
  });

  ProfileViewState copyWith({
    int? tabIndex,
    MediaFilter? mediaFilter,
    bool? showBackToTop,
  }) {
    return ProfileViewState(
      tabIndex: tabIndex ?? this.tabIndex,
      mediaFilter: mediaFilter ?? this.mediaFilter,
      showBackToTop: showBackToTop ?? this.showBackToTop,
    );
  }
}

class ProfileViewStore extends Store<ProfileViewState> {
  ProfileViewStore(int tabIndex) : super(ProfileViewState(tabIndex: tabIndex));

  void selectTab(int index) {
    if (index != state.tabIndex) {
      update(state.copyWith(tabIndex: index));
    }
  }

  void selectMediaFilter(MediaFilter filter) {
    if (filter != state.mediaFilter) {
      update(state.copyWith(mediaFilter: filter));
    }
  }

  void setBackToTopVisible(bool visible) {
    if (visible != state.showBackToTop) {
      update(state.copyWith(showBackToTop: visible));
    }
  }
}
