import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

/// A chip in the Archive's folder row: All, Unfiled, Likes, Notes or one of
/// the reader's folders, which can be renamed and deleted.
@immutable
class SavedFolderOption {
  final String value;
  final String label;
  final bool editable;

  const SavedFolderOption({
    required this.value,
    required this.label,
    this.editable = false,
  });
}

enum SavedOverflowAction { searchLibrary, createFolder, manageFolders, downloads, offline, cleanup, settings }

enum SavedLibraryAction { sortNewest, sortOldest, mediaOnly, select }

class SavedSelectionBar extends StatelessWidget implements PreferredSizeWidget {
  final int selectedCount;
  final bool allSelected;
  final VoidCallback onClose;
  final VoidCallback onSelectAll;
  final VoidCallback onMove;
  final VoidCallback onDelete;

  const SavedSelectionBar({
    super.key,
    required this.selectedCount,
    required this.allSelected,
    required this.onClose,
    required this.onSelectAll,
    required this.onMove,
    required this.onDelete,
  });

  @override
  Size get preferredSize => const Size.fromHeight(kToolbarHeight);

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AppBar(
      leading: IconButton(
        tooltip: l10n.close,
        onPressed: onClose,
        icon: const Icon(Icons.close),
      ),
      title: Text(l10n.library_selected_count(selectedCount)),
      actions: [
        IconButton(
          key: const ValueKey('saved-select-all'),
          tooltip: allSelected ? l10n.library_clear_selection : l10n.library_select_all,
          onPressed: onSelectAll,
          icon: Icon(allSelected ? Icons.deselect : Icons.select_all),
        ),
        IconButton(
          key: const ValueKey('saved-move-selected'),
          tooltip: l10n.library_move_selected,
          onPressed: selectedCount == 0 ? null : onMove,
          icon: const Icon(Icons.drive_file_move_outline),
        ),
        IconButton(
          key: const ValueKey('saved-delete-selected'),
          tooltip: l10n.delete,
          onPressed: selectedCount == 0 ? null : onDelete,
          icon: const Icon(Icons.delete_outline),
        ),
      ],
    );
  }
}

class SavedSelectableTile extends StatelessWidget {
  final String id;
  final bool selected;
  final VoidCallback onToggle;
  final Widget child;

  const SavedSelectableTile({
    super.key,
    required this.id,
    required this.selected,
    required this.onToggle,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: selected
          ? tweetAccentColor(context).withValues(alpha: 0.08)
          : Colors.transparent,
      child: InkWell(
        key: ValueKey('saved-select-$id'),
        onTap: onToggle,
        child: Stack(
          children: [
            IgnorePointer(child: child),
            PositionedDirectional(
              top: kTweetSpace2,
              end: kTweetHorizontalPadding,
              child: Semantics(
                checked: selected,
                child: Checkbox(value: selected, onChanged: (_) => onToggle()),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class SavedOverflowButton extends StatelessWidget {
  final ValueChanged<SavedOverflowAction> onSelected;

  const SavedOverflowButton({super.key, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<SavedOverflowAction>(
      tooltip: MaterialLocalizations.of(context).showMenuTooltip,
      icon: const Icon(Icons.more_vert),
      onSelected: onSelected,
      itemBuilder: (_) => [
        _item(SavedOverflowAction.searchLibrary, Icons.manage_search, l10n.reader_search_all),
        _item(
          SavedOverflowAction.createFolder,
          Icons.create_new_folder_outlined,
          l10n.create_new_folder,
        ),
        _item(
          SavedOverflowAction.manageFolders,
          Icons.folder_copy_outlined,
          l10n.manage_folders,
        ),
        _item(SavedOverflowAction.downloads, Icons.download_outlined, l10n.downloads_title),
        _item(SavedOverflowAction.offline, Icons.offline_pin_outlined, l10n.offline_library_title),
        _item(
          SavedOverflowAction.cleanup,
          Icons.cleaning_services_outlined,
          l10n.find_broken_bookmarks,
        ),
        _item(
          SavedOverflowAction.settings,
          Icons.settings_outlined,
          l10n.settings,
        ),
      ],
    );
  }

  PopupMenuItem<SavedOverflowAction> _item(
    SavedOverflowAction value,
    IconData icon,
    String label,
  ) {
    return PopupMenuItem(
      value: value,
      height: kTweetTouchTarget,
      child: Row(
        children: [
          Icon(icon, size: kTweetActionIconSize),
          const SizedBox(width: kTweetSpace3),
          Text(label),
        ],
      ),
    );
  }
}

class SavedFolderListSkeleton extends StatelessWidget {
  final int count;

  const SavedFolderListSkeleton({super.key, this.count = 6});

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    final color = tokens == null
        ? Theme.of(context).colorScheme.surfaceContainerHighest
        : xLookSkeletonSurface(tokens);
    return ListView.separated(
      physics: const NeverScrollableScrollPhysics(),
      padding: const EdgeInsets.symmetric(vertical: kTweetSpace2),
      itemCount: count,
      separatorBuilder: (_, __) => tweetHairlineDivider(context),
      itemBuilder: (_, __) => Padding(
        padding: const EdgeInsets.symmetric(
          horizontal: kTweetHorizontalPadding,
          vertical: kTweetSpace3,
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                color: color,
                borderRadius: BorderRadius.circular(12),
              ),
            ),
            const SizedBox(width: kTweetSpace3),
            Expanded(child: Container(height: 12, color: color)),
            const SizedBox(width: kTweetSpace6),
            Container(width: 40, height: 12, color: color),
          ],
        ),
      ),
    );
  }
}
