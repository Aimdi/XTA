import 'package:flutter/foundation.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/saved/saved_tab_order.dart';

enum SavedSort { newest, oldest }

List<T> applySavedSort<T>(Iterable<T> items, SavedSort sort) {
  final list = items.toList();
  return sort == SavedSort.oldest ? list.reversed.toList() : list;
}

@immutable
class SavedViewState {
  final String folder;
  final bool mediaOnly;
  final bool searching;
  final String query;
  final bool likesByGroup;
  final SavedSort sort;
  final bool selecting;
  final Set<String> selectedIds;
  final List<SubscriptionGroupMember> groupMembers;
  final List<SubscriptionGroup> groups;

  const SavedViewState({
    this.folder = savedTabAll,
    this.mediaOnly = false,
    this.searching = false,
    this.query = '',
    this.likesByGroup = false,
    this.sort = SavedSort.newest,
    this.selecting = false,
    this.selectedIds = const <String>{},
    this.groupMembers = const [],
    this.groups = const [],
  });

  SavedViewState copyWith({
    String? folder,
    bool? mediaOnly,
    bool? searching,
    String? query,
    bool? likesByGroup,
    SavedSort? sort,
    bool? selecting,
    Set<String>? selectedIds,
    List<SubscriptionGroupMember>? groupMembers,
    List<SubscriptionGroup>? groups,
  }) {
    return SavedViewState(
      folder: folder ?? this.folder,
      mediaOnly: mediaOnly ?? this.mediaOnly,
      searching: searching ?? this.searching,
      query: query ?? this.query,
      likesByGroup: likesByGroup ?? this.likesByGroup,
      sort: sort ?? this.sort,
      selecting: selecting ?? this.selecting,
      selectedIds: selectedIds ?? this.selectedIds,
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
    update(
      state.copyWith(
        folder: folder,
        likesByGroup: false,
        selecting: false,
        selectedIds: const <String>{},
      ),
    );
  }

  void showAll() => update(
    state.copyWith(
      folder: savedTabAll,
      likesByGroup: false,
      selecting: false,
      selectedIds: const <String>{},
    ),
  );

  void setSort(SavedSort sort) => update(state.copyWith(sort: sort));

  void beginSelection([String? id]) {
    final selected = <String>{...state.selectedIds};
    if (id != null) selected.add(id);
    update(
      state.copyWith(
        selecting: true,
        selectedIds: Set.unmodifiable(selected),
      ),
    );
  }

  void toggleSelected(String id) {
    final selected = <String>{...state.selectedIds};
    selected.contains(id) ? selected.remove(id) : selected.add(id);
    update(
      state.copyWith(
        selecting: true,
        selectedIds: Set.unmodifiable(selected),
      ),
    );
  }

  void selectAll(Iterable<String> ids) => update(
    state.copyWith(
      selecting: true,
      selectedIds: Set.unmodifiable(ids.toSet()),
    ),
  );

  void finishSelection() => update(
    state.copyWith(
      selecting: false,
      selectedIds: const <String>{},
    ),
  );

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
