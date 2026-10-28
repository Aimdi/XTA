import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/save_sheet_model.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/action_feedback.dart';
import 'package:xta/ui/quick_pick_sheet.dart';
import 'package:xta/utils/quick_pick.dart';

const _markSize = 30.0;

/// Files the post in a folder, or takes it out of its folder with null.
typedef FileInFolder = Future<void> Function(BuildContext context, String? folderId);

/// The sheet a long press on the bookmark opens: the folders as chips that
/// file the post with a tap, a search that can create a folder, and a line
/// that says where the post is.
class SaveToFolderSheet extends StatefulWidget {
  final String tweetId;
  final SavedTweetModel savedModel;
  final SavedTweetFolderModel folderModel;
  final FileInFolder file;

  const SaveToFolderSheet({
    super.key,
    required this.tweetId,
    required this.savedModel,
    required this.folderModel,
    required this.file,
  });

  @override
  State<SaveToFolderSheet> createState() => _SaveToFolderSheetState();
}

class _SaveToFolderSheetState extends State<SaveToFolderSheet> {
  final _view = QuickPickStore();
  final _field = TextEditingController();

  /// The order the chips open in, fixed once, so a chip never moves from
  /// under a finger.
  late final List<String> _openOrder = [
    for (final folder in saveSheetFolderOrder(widget.savedModel.state, widget.folderModel.state)) folder.id,
  ];

  @override
  void dispose() {
    _view.destroy();
    _field.dispose();
    super.dispose();
  }

  bool get _saved => widget.savedModel.isSaved(widget.tweetId);

  String? get _currentFolder => _saved ? widget.savedModel.folderOf(widget.tweetId) : null;

  Future<void> _file(String? folderId) async {
    await widget.file(context, folderId);
    if (mounted) selectionHaptic(context);
  }

  /// A tap on the post's folder takes it out; on any other, files it there.
  Future<void> _tap(SavedTweetFolder folder) => _file(_currentFolder == folder.id ? null : folder.id);

  Future<void> _create() async {
    final name = _view.state.query.trim();
    if (name.isEmpty) return;
    final folder = await widget.folderModel.createFolder(name);
    _field.clear();
    _view.created(folder.id);
    if (mounted) await _file(folder.id);
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SavedTweetFolderModel, List<SavedTweetFolder>>(
      store: widget.folderModel,
      onState: (context, folders) => ScopedBuilder<QuickPickStore, QuickPickView>(
        store: _view,
        onState: (context, view) => ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
          store: widget.savedModel,
          onState: (context, _) => _sheet(context, folders, view),
        ),
      ),
    );
  }

  Widget _sheet(BuildContext context, List<SavedTweetFolder> folders, QuickPickView view) {
    final l10n = L10n.of(context);
    final ordered = quickPickOrdered(folders, _openOrder, view.createdIds, (folder) => folder.id);
    final chips = quickPickChips(ordered, view, (folder) => folder.name);
    final current = _currentFolder;
    return QuickPickBody(
      title: QuickPickTitle(
        title: l10n.save_sheet_title,
        allLabel: l10n.save_sheet_all_folders,
        onShowAll: chips.hasMore ? _view.showAll : null,
      ),
      field: _field,
      onSearch: _view.search,
      sectionLabel: chips.items.isEmpty && chips.section != QuickPickSection.noMatch
          ? null
          : quickPickSectionText(
              l10n,
              chips.section,
              all: l10n.save_sheet_all_folders,
              noMatch: l10n.save_sheet_no_match,
            ),
      createRow: chips.canCreate
          ? QuickPickCreateRow(label: l10n.save_sheet_create(view.query.trim()), onTap: _create)
          : null,
      chips: [
        for (final folder in chips.items)
          QuickPickChip(
            key: ValueKey('save-folder-${folder.id}'),
            mark: const _FolderMark(),
            label: folder.name,
            selected: folder.id == current,
            onTap: () => _tap(folder),
          ),
      ],
      after: [_RememberFolderSwitch(savedModel: widget.savedModel, tweetId: widget.tweetId)],
      status: QuickPickStatusLine(
        placed: _saved,
        icon: _saved ? Icons.check_circle_outline : Icons.bookmark_border,
        text: _status(l10n, folders, current),
      ),
    );
  }

  String _status(L10n l10n, List<SavedTweetFolder> folders, String? current) {
    if (!_saved) return l10n.save_sheet_not_saved;
    final folder = folders.where((folder) => folder.id == current).firstOrNull;
    return folder == null ? l10n.save_sheet_saved_unfiled : l10n.saved_to_folder(folder.name);
  }
}

/// Folders have no cover, so each carries the same round folder mark.
class _FolderMark extends StatelessWidget {
  const _FolderMark();

  @override
  Widget build(BuildContext context) {
    return Container(
      width: _markSize,
      height: _markSize,
      decoration: BoxDecoration(color: Theme.of(context).colorScheme.surfaceContainerHighest, shape: BoxShape.circle),
      child: Icon(Icons.folder_outlined, size: 16, color: tweetSecondaryColor(context)),
    );
  }
}

/// Whether a plain tap on the bookmark files into the folder picked last.
class _RememberFolderSwitch extends StatelessWidget {
  final SavedTweetModel savedModel;
  final String tweetId;

  const _RememberFolderSwitch({required this.savedModel, required this.tweetId});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    // Switched off, every plain tap on the bookmark lands unfiled and filing is
    // a long-press each time. Switched on, the folder just picked becomes where
    // saves go until another is picked.
    return SwitchListTile(
      contentPadding: const EdgeInsets.symmetric(horizontal: 4),
      secondary: const Icon(Icons.push_pin_outlined),
      title: Text(l10n.save_folder_remember),
      subtitle: Text(l10n.save_folder_remember_description),
      value: PrefService.of(context).get<bool>(optionSavedStickyFolderEnabled) == true,
      onChanged: (value) async {
        final prefs = PrefService.of(context, listen: false);
        await prefs.set(optionSavedStickyFolderEnabled, value);
        // Turning it on adopts wherever this post already is, so the switch
        // means something before the next pick rather than waiting for one.
        if (value) {
          await prefs.set(optionSavedStickyFolderId, savedModel.folderOf(tweetId) ?? '');
        }
      },
    );
  }
}
