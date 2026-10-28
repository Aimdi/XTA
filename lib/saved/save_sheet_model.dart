import 'package:xta/database/entities.dart';

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
