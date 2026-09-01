import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/saved_tab_order.dart';

@immutable
class SavedViewState {
  final String folder;
  final bool mediaOnly;
  final bool searching;
  final String query;
  final bool likesByGroup;
  final List<SubscriptionGroupMember> groupMembers;
  final List<SubscriptionGroup> groups;

  const SavedViewState({
    this.folder = savedTabAll,
    this.mediaOnly = false,
    this.searching = false,
    this.query = '',
    this.likesByGroup = false,
    this.groupMembers = const [],
    this.groups = const [],
  });

  SavedViewState copyWith({
    String? folder,
    bool? mediaOnly,
    bool? searching,
    String? query,
    bool? likesByGroup,
    List<SubscriptionGroupMember>? groupMembers,
    List<SubscriptionGroup>? groups,
  }) {
    return SavedViewState(
      folder: folder ?? this.folder,
      mediaOnly: mediaOnly ?? this.mediaOnly,
      searching: searching ?? this.searching,
      query: query ?? this.query,
      likesByGroup: likesByGroup ?? this.likesByGroup,
      groupMembers: groupMembers ?? this.groupMembers,
      groups: groups ?? this.groups,
    );
  }
}

class SavedViewStore extends Store<SavedViewState> {
  SavedViewStore() : super(const SavedViewState());

  void selectFolder(String folder) {
    if (folder == savedTabFavorites && state.folder == savedTabFavorites) {
      update(state.copyWith(likesByGroup: !state.likesByGroup));
      return;
    }
    update(state.copyWith(folder: folder, likesByGroup: false));
  }

  void showAll() =>
      update(state.copyWith(folder: savedTabAll, likesByGroup: false));

  void toggleMedia() => update(state.copyWith(mediaOnly: !state.mediaOnly));

  void toggleSearch() {
    final searching = !state.searching;
    update(
      state.copyWith(searching: searching, query: searching ? state.query : ''),
    );
  }

  void setQuery(String value) => update(state.copyWith(query: value.trim()));

  void setGroups(
    List<SubscriptionGroupMember> members,
    List<SubscriptionGroup> groups,
  ) {
    update(state.copyWith(groupMembers: members, groups: groups));
  }

  void refresh() => update(state.copyWith());

  void reconcileFolders(
    List<SavedTweetFolder> folders, {
    required bool showUnfiled,
    required bool showFavorites,
  }) {
    final reachable =
        state.folder == savedTabAll ||
        (state.folder == savedTabUnfiled &&
            showUnfiled &&
            folders.isNotEmpty) ||
        (state.folder == savedTabFavorites && showFavorites) ||
        folders.any((folder) => folder.id == state.folder);
    if (!reachable) showAll();
  }
}

class SavedFolderEditorStore extends Store<bool> {
  SavedFolderEditorStore(super.initialState);

  void setAutoDownload(bool value) => update(value);
}

class SavedFolderManagementStore extends Store<int> {
  SavedFolderManagementStore() : super(0);

  void refresh() => update(state + 1);
}
