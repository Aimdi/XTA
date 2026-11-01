import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/saved_chrome.dart';
import 'package:xta/saved/saved_tab_order.dart';
import 'package:xta/saved/saved_view_store.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/x_look_theme.dart';

/// The line the Archive keeps behind its lock: what [filter] holds stays on
/// this device.
String savedOnDeviceNotice(L10n l10n, String filter) => switch (filter) {
  savedTabFavorites => l10n.likes_stay_on_device_notice,
  savedTabNotes => l10n.local_note_device_notice,
  _ => l10n.saves_stay_on_device_notice,
};

/// How tall a row of the Archive's top is at the reader's text size: [base]
/// at normal sizes, more once the text needs it.
double _rowHeight(BuildContext context, double base, double text) =>
    math.max(base, MediaQuery.textScalerOf(context).scale(text) + 18);

/// The title row's height, for the bar that holds it.
double savedTitleRowHeight(BuildContext context) => _rowHeight(context, 52, 34);

/// The title at 28 sp with a small lock that says what stays on the device,
/// and the Archive's menu at the end.
class SavedTitleRow extends StatelessWidget {
  final String title;
  final String notice;
  final Widget menu;

  const SavedTitleRow({super.key, required this.title, required this.notice, required this.menu});

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: BoxConstraints(minHeight: savedTitleRowHeight(context)),
      child: Padding(
        padding: const EdgeInsetsDirectional.only(start: kTweetHorizontalPadding, end: kTweetSpace1),
        child: Row(
          children: [
            // The title and its lock take all the room before the menu; the
            // title gives way first.
            Expanded(
              child: Row(
                children: [
                  Flexible(
                    child: Text(
                      title,
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                      style: Theme.of(context).textTheme.headlineMedium!.copyWith(
                        fontSize: 28,
                        height: 34 / 28,
                        fontWeight: FontWeight.w700,
                        letterSpacing: -0.56,
                        color: tweetPrimaryColor(context),
                      ),
                    ),
                  ),
                  IconButton(
                    tooltip: notice,
                    onPressed: () => showSnackBar(context, icon: '🔒', message: notice),
                    icon: Icon(Icons.lock_outline, size: 16, color: tweetSecondaryColor(context)),
                  ),
                ],
              ),
            ),
            menu,
          ],
        ),
      ),
    );
  }
}

/// The Archive's search field, always there: the magnifier, the words, a
/// clear button once something is typed, and Filter and sort at its end.
class SavedSearchRow extends StatelessWidget {
  final TextEditingController controller;
  final FocusNode focusNode;
  final ValueChanged<String> onChanged;
  final VoidCallback onClear;
  final Widget filter;

  const SavedSearchRow({
    super.key,
    required this.controller,
    required this.focusNode,
    required this.onChanged,
    required this.onClear,
    required this.filter,
  });

  @override
  Widget build(BuildContext context) {
    final muted = tweetSecondaryColor(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(kTweetHorizontalPadding, kTweetSpace1, kTweetHorizontalPadding, kTweetSpace1),
      child: DecoratedBox(
        decoration: ShapeDecoration(
          color: xLookInsetSurfaceOf(context),
          shape: StadiumBorder(side: BorderSide(color: tweetDividerColor(context))),
        ),
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: _rowHeight(context, 44, 20)),
          child: Row(
            children: [
              const SizedBox(width: 14),
              Icon(Icons.search, size: 18, color: muted),
              const SizedBox(width: 10),
              Expanded(child: _field(context)),
              ValueListenableBuilder<TextEditingValue>(
                valueListenable: controller,
                builder: (context, value, _) => value.text.isEmpty
                    ? const SizedBox.shrink()
                    : IconButton(
                        tooltip: MaterialLocalizations.of(context).clearButtonTooltip,
                        onPressed: onClear,
                        icon: Icon(Icons.close, size: 18, color: muted),
                      ),
              ),
              SizedBox(height: 20, child: VerticalDivider(width: 1, thickness: 1, color: tweetDividerColor(context))),
              filter,
            ],
          ),
        ),
      ),
    );
  }

  Widget _field(BuildContext context) {
    return TextField(
      controller: controller,
      focusNode: focusNode,
      onChanged: onChanged,
      onTapOutside: (_) => focusNode.unfocus(),
      textInputAction: TextInputAction.search,
      style: TextStyle(fontSize: 15, color: tweetPrimaryColor(context)),
      // Every border and fill spelled out: the theme's own would draw a second
      // box inside the pill.
      decoration: InputDecoration(
        isCollapsed: true,
        filled: false,
        border: InputBorder.none,
        enabledBorder: InputBorder.none,
        focusedBorder: InputBorder.none,
        contentPadding: EdgeInsets.zero,
        hintText: L10n.of(context).saved_search_hint,
        hintStyle: TextStyle(fontSize: 15, color: tweetSecondaryColor(context)),
      ),
    );
  }
}

/// Filter and sort: newest or oldest saved first, Media only, and Select.
/// Sorting and selecting are for saved posts, Media only for saved posts and
/// likes; with neither the button is off.
class SavedFilterButton extends StatelessWidget {
  final SavedSort sort;
  final bool sortable;
  final bool showMedia;
  final bool mediaOnly;
  final ValueChanged<SavedLibraryAction> onSelected;

  const SavedFilterButton({
    super.key,
    required this.sort,
    required this.sortable,
    required this.showMedia,
    required this.mediaOnly,
    required this.onSelected,
  });

  @override
  Widget build(BuildContext context) {
    return PopupMenuButton<SavedLibraryAction>(
      key: const ValueKey('saved-library-actions'),
      tooltip: L10n.of(context).saved_filter_and_sort,
      enabled: sortable || showMedia,
      icon: Icon(Icons.tune, size: 20, color: tweetPrimaryColor(context)),
      position: PopupMenuPosition.under,
      onSelected: onSelected,
      itemBuilder: _items,
    );
  }

  List<PopupMenuEntry<SavedLibraryAction>> _items(BuildContext context) {
    final l10n = L10n.of(context);
    return [
      if (sortable) ...[
        _checked(SavedLibraryAction.sortNewest, l10n.library_sort_newest, sort == SavedSort.newest),
        _checked(SavedLibraryAction.sortOldest, l10n.library_sort_oldest, sort == SavedSort.oldest),
      ],
      if (sortable && showMedia) const PopupMenuDivider(),
      if (showMedia) _checked(SavedLibraryAction.mediaOnly, l10n.saved_media_only, mediaOnly),
      if (sortable) ...[
        const PopupMenuDivider(),
        PopupMenuItem(
          value: SavedLibraryAction.select,
          height: kTweetTouchTarget,
          child: Row(
            children: [
              const Icon(Icons.checklist_outlined, size: kTweetActionIconSize),
              const SizedBox(width: kTweetSpace3),
              Text(l10n.select),
            ],
          ),
        ),
      ],
    ];
  }

  PopupMenuEntry<SavedLibraryAction> _checked(SavedLibraryAction value, String label, bool checked) =>
      CheckedPopupMenuItem(value: value, checked: checked, height: kTweetTouchTarget, child: Text(label));
}

/// The chips under the search field, scrolling sideways: the network picker,
/// a thin rule, the folders and a dashed + for a new one.
class SavedChipRow extends StatelessWidget {
  final Widget? networkPicker;
  final List<SavedFolderOption> folders;
  final String selectedFolder;
  final bool likesByGroup;
  final ValueChanged<String> onFolderSelected;
  final ValueChanged<SavedFolderOption> onFolderLongPress;
  final VoidCallback onNewFolder;

  const SavedChipRow({
    super.key,
    required this.networkPicker,
    required this.folders,
    required this.selectedFolder,
    required this.likesByGroup,
    required this.onFolderSelected,
    required this.onFolderLongPress,
    required this.onNewFolder,
  });

  @override
  Widget build(BuildContext context) {
    final picker = networkPicker;
    final items = <Widget>[
      if (picker != null) ...[picker, const _ChipRule()],
      for (final option in folders) _chip(option),
      SavedNewFolderChip(onTap: onNewFolder),
    ];
    return SizedBox(
      height: _rowHeight(context, kTweetTouchTarget, 18),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.symmetric(horizontal: kTweetHorizontalPadding),
        itemCount: items.length,
        separatorBuilder: (_, _) => const SizedBox(width: kTweetSpace2),
        itemBuilder: (_, index) => Center(child: items[index]),
      ),
    );
  }

  Widget _chip(SavedFolderOption option) {
    final favorites = option.value == savedTabFavorites && selectedFolder == savedTabFavorites;
    return SavedFolderChip(
      label: option.label,
      selected: selectedFolder == option.value,
      trailing: favorites ? (likesByGroup ? Icons.expand_less : Icons.expand_more) : null,
      onTap: () => onFolderSelected(option.value),
      onLongPress: option.editable ? () => onFolderLongPress(option) : null,
    );
  }
}

class _ChipRule extends StatelessWidget {
  const _ChipRule();

  @override
  Widget build(BuildContext context) {
    return SizedBox(height: 24, child: VerticalDivider(width: 1, thickness: 1, color: tweetDividerColor(context)));
  }
}

/// A folder as a plain chip, 32 dp to the eye and 48 to the touch; the
/// selected one filled with the accent at 18 % and outlined in it.
class SavedFolderChip extends StatelessWidget {
  final String label;
  final bool selected;
  final IconData? trailing;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  const SavedFolderChip({
    super.key,
    required this.label,
    required this.selected,
    required this.onTap,
    this.trailing,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final accent = tweetReadableAccentColor(context, background: Theme.of(context).scaffoldBackgroundColor);
    final trailing = this.trailing;
    return GestureDetector(
      onLongPress: onLongPress,
      child: ChoiceChip(
        label: Row(
          mainAxisSize: MainAxisSize.min,
          children: [
            ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 180),
              child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
            ),
            if (trailing != null) ...[const SizedBox(width: 2), Icon(trailing, size: 16)],
          ],
        ),
        selected: selected,
        onSelected: (_) => onTap(),
        showCheckmark: false,
        materialTapTargetSize: MaterialTapTargetSize.padded,
        labelPadding: const EdgeInsets.symmetric(horizontal: 4),
        padding: const EdgeInsets.symmetric(horizontal: 6),
        backgroundColor: Colors.transparent,
        selectedColor: accent.withValues(alpha: 0.18),
        side: BorderSide(color: selected ? accent : tweetDividerColor(context)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(8)),
        labelStyle: TextStyle(
          fontSize: 13,
          fontWeight: selected ? FontWeight.w600 : FontWeight.w500,
          color: tweetPrimaryColor(context),
        ),
      ),
    );
  }
}

/// The dashed + at the end of the folders: a new folder.
class SavedNewFolderChip extends StatelessWidget {
  final VoidCallback onTap;

  const SavedNewFolderChip({super.key, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final muted = tweetSecondaryColor(context);
    return IconButton(
      tooltip: L10n.of(context).create_new_folder,
      onPressed: onTap,
      icon: CustomPaint(
        painter: _DashedRoundedRectPainter(color: tweetDividerColor(context)),
        child: SizedBox.square(dimension: 32, child: Icon(Icons.add, size: 16, color: muted)),
      ),
    );
  }
}

class _DashedRoundedRectPainter extends CustomPainter {
  final Color color;

  _DashedRoundedRectPainter({required this.color});

  static const _dash = 3.0;
  static const _gap = 3.0;

  @override
  void paint(Canvas canvas, Size size) {
    final outline = Path()
      ..addRRect(RRect.fromRectAndRadius((Offset.zero & size).deflate(0.5), const Radius.circular(8)));
    final paint = Paint()
      ..color = color
      ..style = PaintingStyle.stroke
      ..strokeWidth = 1;
    for (final metric in outline.computeMetrics()) {
      for (var start = 0.0; start < metric.length; start += _dash + _gap) {
        canvas.drawPath(metric.extractPath(start, math.min(start + _dash, metric.length)), paint);
      }
    }
  }

  @override
  bool shouldRepaint(_DashedRoundedRectPainter old) => old.color != color;
}
