import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';

/// How many folders the sheet shows before All folders.
const kSaveSheetRecentCount = 6;

/// Every folder, those of the latest saved posts first, each once, then the
/// rest in their own order. [savedNewestFirst] is the saved list as the store
/// holds it, newest first.
List<SavedTweetFolder> saveSheetFolderOrder(List<SavedTweet> savedNewestFirst, List<SavedTweetFolder> folders) {
  final byId = {for (final folder in folders) folder.id: folder};
  final ids = <String>{
    for (final post in savedNewestFirst)
      if (post.folderId case final id? when byId.containsKey(id)) id,
    ...byId.keys,
  };
  return [for (final id in ids) byId[id]!];
}

/// The folders whose names hold [query], whatever its case.
List<SavedTweetFolder> folderMatches(Iterable<SavedTweetFolder> folders, String query) {
  final needle = query.trim().toLowerCase();
  return [
    for (final folder in folders)
      if (folder.name.toLowerCase().contains(needle)) folder,
  ];
}

/// Whether a folder is already called [query], whatever its case.
bool folderNameTaken(Iterable<SavedTweetFolder> folders, String query) {
  final name = query.trim().toLowerCase();
  return folders.any((folder) => folder.name.trim().toLowerCase() == name);
}

enum SaveSheetSection { recent, all, matches, noMatch }

/// What the chip area holds: the folders, under which label, whether the
/// typed name can become a folder, and whether All folders has more to show.
typedef SaveSheetChips = ({List<SavedTweetFolder> folders, SaveSheetSection section, bool canCreate, bool hasMore});

/// The search, whether every folder is out, and the folders created in the
/// sheet, newest first.
typedef SaveSheetView = ({String query, bool showAll, List<String> createdIds});

/// The chips for [view], from [ordered], every folder in the order the sheet
/// opened with.
SaveSheetChips saveSheetChips(List<SavedTweetFolder> ordered, SaveSheetView view) {
  final query = view.query.trim();
  if (query.isNotEmpty) {
    final matches = folderMatches(ordered, query);
    return (
      folders: matches,
      section: matches.isEmpty ? SaveSheetSection.noMatch : SaveSheetSection.matches,
      canCreate: !folderNameTaken(ordered, query),
      hasMore: false,
    );
  }
  final hasMore = !view.showAll && ordered.length > kSaveSheetRecentCount;
  return (
    folders: hasMore ? ordered.take(kSaveSheetRecentCount).toList() : ordered,
    section: view.showAll ? SaveSheetSection.all : SaveSheetSection.recent,
    canCreate: false,
    hasMore: hasMore,
  );
}

/// The folders created in the sheet lead, then [openOrder], then any folder
/// that came since, each once and only while it exists.
List<SavedTweetFolder> saveSheetOrdered(
  List<SavedTweetFolder> folders,
  List<String> openOrder,
  List<String> createdIds,
) {
  final byId = {for (final folder in folders) folder.id: folder};
  final ids = <String>{...createdIds, ...openOrder, ...byId.keys};
  return [for (final id in ids) ?byId[id]];
}

class SaveSheetStore extends Store<SaveSheetView> {
  SaveSheetStore() : super((query: '', showAll: false, createdIds: const []));

  void search(String query) => update((query: query, showAll: state.showAll, createdIds: state.createdIds));

  void showAll() => update((query: state.query, showAll: true, createdIds: state.createdIds));

  /// [id] was created from the search, which it empties.
  void created(String id) => update((query: '', showAll: state.showAll, createdIds: [id, ...state.createdIds]));
}
