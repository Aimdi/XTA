import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter_localizations/flutter_localizations.dart';
import 'package:flutter_test/flutter_test.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/folder_picker.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';

SavedTweetFolder savedFolder(String id, String name, {int position = 0}) =>
    SavedTweetFolder(id: id, name: name, position: position, createdAt: DateTime.utc(2024));

SavedTweet savedPost(String id, String? folderId) => SavedTweet(id: id, user: 'u', content: '{}', folderId: folderId);

/// Saved posts in memory, newest first, as the store holds them.
class MemorySaves extends SavedTweetModel {
  MemorySaves(List<SavedTweet> saved) {
    update(saved);
  }

  @override
  Future<void> saveTweet(String id, String? user, Map<String, dynamic> content, {String? folderId}) async {
    update([SavedTweet(id: id, user: user, content: jsonEncode(content), folderId: folderId), ...state], force: true);
  }

  @override
  Future<void> setFolders(Iterable<String> ids, String? folderId) async {
    final selected = ids.toSet();
    update([
      for (final post in state) selected.contains(post.id) ? post.copyWith(folderId: folderId) : post,
    ], force: true);
  }
}

/// Folders in memory; a created one is called `new-<name>`.
class MemoryFolders extends SavedTweetFolderModel {
  MemoryFolders(List<SavedTweetFolder> folders) {
    update(folders);
  }

  @override
  Future<void> listFolders() async {}

  @override
  Future<SavedTweetFolder> createFolder(String name, {bool autoDownload = false, bool autoUpload = false}) async {
    final folder = savedFolder('new-$name', name, position: state.length);
    update([...state, folder], force: true);
    return folder;
  }
}

/// A page whose "open" button opens the save sheet for post `t1`.
class SaveSheetHarness {
  final PrefServiceCache prefs;
  final MemorySaves saves;
  final MemoryFolders folders;

  SaveSheetHarness({
    List<SavedTweet> saved = const [],
    required List<SavedTweetFolder> folders,
    Map<String, dynamic> cache = const {},
  }) : prefs = PrefServiceCache(
         cache: {
           optionSavedStickyFolderEnabled: false,
           optionSavedStickyFolderId: '',
           optionDisableAnimations: true,
           ...cache,
         },
       ),
       saves = MemorySaves(saved),
       folders = MemoryFolders(folders);

  String? get folderOfPost => saves.folderOf('t1');

  Future<void> open(WidgetTester tester, {ThemeData? theme, Locale? locale, Key? shotKey}) async {
    addTearDown(saves.destroy);
    addTearDown(folders.destroy);
    await tester.pumpWidget(
      MultiProvider(
        providers: [
          Provider<SavedTweetModel>.value(value: saves),
          Provider<SavedTweetFolderModel>.value(value: folders),
        ],
        child: PrefService(
          service: prefs,
          child: MaterialApp(
            debugShowCheckedModeBanner: false,
            theme: theme,
            locale: locale,
            localizationsDelegates: const [
              L10n.delegate,
              GlobalMaterialLocalizations.delegate,
              GlobalWidgetsLocalizations.delegate,
              GlobalCupertinoLocalizations.delegate,
            ],
            supportedLocales: L10n.delegate.supportedLocales,
            builder: (context, child) => RepaintBoundary(key: shotKey, child: child),
            home: Scaffold(
              body: Builder(
                builder: (context) => Center(
                  child: TextButton(
                    onPressed: () => showSaveToFolderSheet(context, tweetId: 't1', userId: 'u', content: const {}),
                    child: const Text('open'),
                  ),
                ),
              ),
            ),
          ),
        ),
      ),
    );
    await tester.tap(find.text('open'));
    await tester.pumpAndSettle();
  }
}
