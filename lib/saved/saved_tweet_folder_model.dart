import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/database/repository.dart';
import 'package:logging/logging.dart';
import 'package:uuid/uuid.dart';

class SavedTweetFolderModel extends Store<List<SavedTweetFolder>> {
  static final log = Logger('SavedTweetFolderModel');

  SavedTweetFolderModel() : super([]);

  Future<void> listFolders() async {
    log.info('Listing saved tweet folders');

    await execute(() async {
      var database = await Repository.readOnly();

      return (await database.query(tableSavedTweetFolder, orderBy: 'position ASC, created_at ASC'))
          .map((e) => SavedTweetFolder.fromMap(e))
          .toList();
    });
  }

  Future<SavedTweetFolder> createFolder(String name, {bool autoDownload = false, bool autoUpload = false}) async {
    var database = await Repository.writable();

    var folder = SavedTweetFolder(
        id: const Uuid().v4(),
        name: name,
        position: state.length,
        createdAt: DateTime.now(),
        autoDownload: autoDownload,
        autoUpload: autoUpload);

    await database.insert(tableSavedTweetFolder, folder.toMap());
    update([...state, folder], force: true);

    return folder;
  }

  Future<void> updateFolder(String id, String name, {bool? autoDownload, bool? autoUpload}) async {
    var database = await Repository.writable();

    await database.update(
        tableSavedTweetFolder,
        {
          'name': name,
          if (autoDownload != null) 'auto_download': autoDownload ? 1 : 0,
          if (autoUpload != null) 'auto_upload': autoUpload ? 1 : 0,
        },
        where: 'id = ?',
        whereArgs: [id]);

    update(
        state.map((e) => e.id == id ? e.copyWith(name: name, autoDownload: autoDownload, autoUpload: autoUpload) : e).toList(),
        force: true);
  }

  /// Deletes a folder and moves its posts back to "unfiled" (folder_id = NULL).
  Future<void> deleteFolder(String id) async {
    var database = await Repository.writable();

    await database.update(tableSavedTweet, {'folder_id': null}, where: 'folder_id = ?', whereArgs: [id]);
    await database.delete(tableSavedTweetFolder, where: 'id = ?', whereArgs: [id]);

    update(state.where((e) => e.id != id).toList(), force: true);
  }
}
