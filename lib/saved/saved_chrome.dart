import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_tab_order.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

const double kSavedControlBarHeight = 56;
const double kSavedControlRadius = 12;
const double kSavedSearchHeight = 48;

@immutable
class SavedFolderOption {
  final String value;
  final String label;
  final IconData icon;
  final bool editable;

  const SavedFolderOption({
    required this.value,
    required this.label,
    required this.icon,
    this.editable = false,
  });
}

class SavedControlBar extends StatelessWidget implements PreferredSizeWidget {
  final String selectedFolder;
  final bool mediaOnly;
  final bool likesByGroup;
  final List<SavedFolderOption> folders;
  final ValueChanged<String> onFolderSelected;
  final ValueChanged<SavedFolderOption> onFolderLongPress;
  final VoidCallback onMediaToggle;
  final bool showMedia;

  const SavedControlBar({
    super.key,
    required this.selectedFolder,
    required this.mediaOnly,
    required this.likesByGroup,
    required this.folders,
    required this.onFolderSelected,
    required this.onFolderLongPress,
    required this.onMediaToggle,
    this.showMedia = true,
  });

  @override
  Size get preferredSize => const Size.fromHeight(kSavedControlBarHeight);

  @override
  Widget build(BuildContext context) {
    final background =
        XLookTokens.maybeOf(context)?.background ??
        Theme.of(context).scaffoldBackgroundColor;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: background,
        border: Border(
          bottom: BorderSide(
            color: tweetDividerColor(context),
            width: kTweetDividerThickness,
          ),
        ),
      ),
      child: SizedBox(
        height: kSavedControlBarHeight,
        child: ListView.separated(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.symmetric(
            horizontal: kTweetHorizontalPadding,
            vertical: kTweetSpace1,
          ),
          itemCount: folders.length + (showMedia ? 1 : 0),
          separatorBuilder: (_, __) => const SizedBox(width: kTweetSpace2),
          itemBuilder: (context, index) {
            if (showMedia && index == folders.length) {
              return SavedChoiceChip(
                label: L10n.of(context).media,
                icon: mediaOnly
                    ? Icons.photo_library
                    : Icons.photo_library_outlined,
                selected: mediaOnly,
                onTap: onMediaToggle,
              );
            }
            final option = folders[index];
            return SavedChoiceChip(
              label: option.label,
              icon: option.icon,
              selected: selectedFolder == option.value,
              trailing:
                  option.value == savedTabFavorites &&
                      selectedFolder == savedTabFavorites
                  ? (likesByGroup ? Icons.expand_less : Icons.expand_more)
                  : null,
              onTap: () => onFolderSelected(option.value),
              onLongPress: option.editable
                  ? () => onFolderLongPress(option)
                  : null,
            );
          },
        ),
      ),
    );
  }
}

class SavedChoiceChip extends StatelessWidget {
  final String label;
  final IconData icon;
  final IconData? trailing;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  const SavedChoiceChip({
    super.key,
    required this.label,
    required this.icon,
    required this.selected,
    required this.onTap,
    this.trailing,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    return Semantics(
      selected: selected,
      button: true,
      child: Material(
        color: selected
            ? tweetAccentColor(context).withValues(alpha: 0.12)
            : Colors.transparent,
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(kSavedControlRadius),
          side: BorderSide(
            color: selected
                ? tweetAccentColor(context)
                : tweetDividerColor(context),
          ),
        ),
        child: InkWell(
          onTap: onTap,
          onLongPress: onLongPress,
          borderRadius: BorderRadius.circular(kSavedControlRadius),
          child: ConstrainedBox(
            constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: kTweetSpace3),
              child: Row(
                mainAxisSize: MainAxisSize.min,
                children: [
                  Icon(
                    icon,
                    size: kTweetActionIconSize,
                    color: selected
                        ? tweetAccentColor(context)
                        : tweetSecondaryColor(context),
                  ),
                  const SizedBox(width: kTweetSpace2),
                  ConstrainedBox(
                    constraints: const BoxConstraints(maxWidth: 180),
                    child: Text(
                      label,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: tweetMetadataStyle(context).copyWith(
                        color: selected
                            ? tweetPrimaryColor(context)
                            : tweetSecondaryColor(context),
                        fontWeight: selected
                            ? FontWeight.w700
                            : FontWeight.w500,
                      ),
                    ),
                  ),
                  if (trailing != null) ...[
                    const SizedBox(width: kTweetSpace1),
                    Icon(
                      trailing,
                      size: 18,
                      color: tweetSecondaryColor(context),
                    ),
                  ],
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }
}

class SavedSearchField extends StatelessWidget {
  final FocusNode focusNode;
  final ValueChanged<String> onChanged;
  final VoidCallback onClose;

  const SavedSearchField({
    super.key,
    required this.focusNode,
    required this.onChanged,
    required this.onClose,
  });

  @override
  Widget build(BuildContext context) {
    final background = Color.alphaBlend(
      tweetPrimaryColor(context).withValues(alpha: 0.06),
      XLookTokens.maybeOf(context)?.background ??
          Theme.of(context).scaffoldBackgroundColor,
    );
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        kTweetHorizontalPadding,
        kTweetSpace1,
        kTweetHorizontalPadding,
        kTweetSpace2,
      ),
      child: SizedBox(
        height: kSavedSearchHeight,
        child: TextField(
          focusNode: focusNode,
          textInputAction: TextInputAction.search,
          onChanged: onChanged,
          onTapOutside: (_) => focusNode.unfocus(),
          decoration: InputDecoration(
            hintText: L10n.of(context).search_saved_posts,
            prefixIcon: const Icon(Icons.search),
            suffixIcon: IconButton(
              tooltip: L10n.of(context).close,
              icon: const Icon(Icons.close),
              onPressed: onClose,
            ),
            filled: true,
            fillColor: background,
            border: OutlineInputBorder(
              borderRadius: BorderRadius.circular(kSavedSearchHeight / 2),
            ),
            enabledBorder: OutlineInputBorder(
              borderRadius: BorderRadius.circular(kSavedSearchHeight / 2),
              borderSide: BorderSide(color: tweetDividerColor(context)),
            ),
          ),
        ),
      ),
    );
  }
}

enum SavedOverflowAction { createFolder, manageFolders, cleanup, settings }

class SavedOverflowButton extends StatelessWidget {
  final ValueChanged<SavedOverflowAction> onSelected;

  const SavedOverflowButton({super.key, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return PopupMenuButton<SavedOverflowAction>(
      icon: const Icon(Icons.more_vert),
      onSelected: onSelected,
      itemBuilder: (_) => [
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
    final color =
        XLookTokens.maybeOf(context)?.border ??
        Theme.of(context).colorScheme.surfaceContainerHighest;
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
