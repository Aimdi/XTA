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
import 'package:xta/ui/x_controls.dart';

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
  final _view = SaveSheetStore();
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
    // The sheet rises with the keyboard, so the field stays in view.
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        top: false,
        child: ScopedBuilder<SavedTweetFolderModel, List<SavedTweetFolder>>(
          store: widget.folderModel,
          onState: (context, folders) => ScopedBuilder<SaveSheetStore, SaveSheetView>(
            store: _view,
            onState: (context, view) => ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
              store: widget.savedModel,
              onState: (context, _) => _sheet(context, folders, view),
            ),
          ),
        ),
      ),
    );
  }

  Widget _sheet(BuildContext context, List<SavedTweetFolder> folders, SaveSheetView view) {
    final chips = saveSheetChips(saveSheetOrdered(folders, _openOrder, view.createdIds), view);
    final current = _currentFolder;
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _SheetTitle(onShowAll: chips.hasMore ? _view.showAll : null),
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 4, 16, 6),
          child: XSearchField(
            controller: _field,
            hintText: L10n.of(context).save_sheet_search_hint,
            onChanged: _view.search,
          ),
        ),
        Flexible(
          child: SingleChildScrollView(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (chips.folders.isNotEmpty || chips.section == SaveSheetSection.noMatch)
                  _SectionLabel(section: chips.section),
                if (chips.canCreate) _CreateRow(name: view.query.trim(), onTap: _create),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final folder in chips.folders)
                      _FolderChip(folder: folder, selected: folder.id == current, onTap: () => _tap(folder)),
                  ],
                ),
                const SizedBox(height: 8),
                _RememberFolderSwitch(savedModel: widget.savedModel, tweetId: widget.tweetId),
              ],
            ),
          ),
        ),
        _StatusLine(saved: _saved, text: _status(L10n.of(context), folders, current)),
      ],
    );
  }

  String _status(L10n l10n, List<SavedTweetFolder> folders, String? current) {
    if (!_saved) return l10n.save_sheet_not_saved;
    final folder = folders.where((folder) => folder.id == current).firstOrNull;
    return folder == null ? l10n.save_sheet_saved_unfiled : l10n.saved_to_folder(folder.name);
  }
}

/// The sheet's own colour, which its accents are made readable against.
Color _sheetColor(BuildContext context) =>
    Theme.of(context).bottomSheetTheme.modalBackgroundColor ?? Theme.of(context).colorScheme.surfaceContainerLow;

/// The fill a chip and the create row sit on, a step up from the sheet.
Color _raisedFill(BuildContext context) =>
    Color.alphaBlend(tweetPrimaryColor(context).withValues(alpha: 0.06), _sheetColor(context));

/// "Save to", with All folders at its end while there are more to show.
class _SheetTitle extends StatelessWidget {
  final VoidCallback? onShowAll;

  const _SheetTitle({required this.onShowAll});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final showAll = onShowAll;
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(20, 0, 8, 4),
      // Side by side while they fit; with large text on a narrow phone All
      // folders goes under the title rather than breaking it mid-word.
      child: Wrap(
        alignment: WrapAlignment.spaceBetween,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          Text(
            l10n.save_sheet_title,
            style: Theme.of(context).textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w700),
          ),
          if (showAll != null)
            TextButton.icon(
              key: const ValueKey('save-sheet-all'),
              onPressed: showAll,
              iconAlignment: IconAlignment.end,
              icon: const Icon(Icons.chevron_right, size: 20),
              label: Text(l10n.save_sheet_all_folders),
            ),
        ],
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final SaveSheetSection section;

  const _SectionLabel({required this.section});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final text = switch (section) {
      SaveSheetSection.recent => l10n.save_sheet_recent,
      SaveSheetSection.all => l10n.save_sheet_all_folders,
      SaveSheetSection.matches => l10n.save_sheet_matches,
      SaveSheetSection.noMatch => l10n.save_sheet_no_match,
    };
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 10, 4, 8),
      child: Text(
        text,
        style: Theme.of(context).textTheme.labelLarge!.copyWith(
          fontSize: 13,
          fontWeight: FontWeight.w600,
          color: tweetSecondaryColor(context),
        ),
      ),
    );
  }
}

/// "Create “Japan” and save", for a typed name no folder has.
class _CreateRow extends StatelessWidget {
  final String name;
  final VoidCallback onTap;

  const _CreateRow({required this.name, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final accent = tweetReadableAccentColor(context, background: _raisedFill(context));
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: TextButton(
        key: const ValueKey('save-sheet-create'),
        onPressed: onTap,
        style: TextButton.styleFrom(
          foregroundColor: accent,
          backgroundColor: _raisedFill(context),
          minimumSize: const Size.fromHeight(56),
          padding: const EdgeInsets.all(8),
          alignment: AlignmentDirectional.centerStart,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Theme.of(context).colorScheme.outline, width: 1.5),
              ),
              child: const Icon(Icons.add, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                L10n.of(context).save_sheet_create(name),
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A folder as a pill with its mark, outlined in the accent with a check
/// while the post is in it.
class _FolderChip extends StatelessWidget {
  final SavedTweetFolder folder;
  final bool selected;
  final VoidCallback onTap;

  const _FolderChip({required this.folder, required this.selected, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final sheet = _sheetColor(context);
    final accent = tweetReadableAccentColor(context, background: sheet);
    return FilterChip(
      key: ValueKey('save-folder-${folder.id}'),
      selected: selected,
      onSelected: (_) => onTap(),
      showCheckmark: false,
      // The mark rides in the label: a chip's avatar may be no taller than its
      // text, and the mark is.
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          const _FolderMark(),
          const SizedBox(width: 8),
          Flexible(child: Text(folder.name, maxLines: 1, overflow: TextOverflow.ellipsis)),
          if (selected) ...[const SizedBox(width: 6), Icon(Icons.check, size: 18, color: accent)],
        ],
      ),
      labelStyle: TextStyle(color: tweetPrimaryColor(context), fontSize: 15, fontWeight: FontWeight.w500),
      backgroundColor: _raisedFill(context),
      selectedColor: Color.alphaBlend(tweetAccentColor(context).withValues(alpha: 0.18), sheet),
      side: BorderSide(color: selected ? accent : Theme.of(context).colorScheme.outline, width: 1.5),
      shape: const StadiumBorder(),
      padding: const EdgeInsetsDirectional.fromSTEB(6, 5, 10, 5),
      labelPadding: EdgeInsets.zero,
    );
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

/// Where the post is, read out as it changes.
class _StatusLine extends StatelessWidget {
  final bool saved;
  final String text;

  const _StatusLine({required this.saved, required this.text});

  @override
  Widget build(BuildContext context) {
    final color = saved
        ? tweetReadableAccentColor(context, background: _sheetColor(context))
        : tweetSecondaryColor(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: tweetDividerColor(context))),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 14, 20, 14),
        child: Semantics(
          liveRegion: true,
          child: Row(
            children: [
              Icon(saved ? Icons.check_circle_outline : Icons.bookmark_border, size: 20, color: color),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  text,
                  key: const ValueKey('save-sheet-status'),
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(color: tweetPrimaryColor(context)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
