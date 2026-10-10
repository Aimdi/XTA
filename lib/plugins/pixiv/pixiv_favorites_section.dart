import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_tag_picker.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_filter_row.dart';

/// Favorites: the reader's own bookmarks, public or private, all of them or
/// one bookmark tag's.
class PixivFavoritesSection extends StatelessWidget {
  final String restrict;

  /// The bookmark tag shown; null shows every bookmark.
  final String? tag;

  /// Picks visibility and tag together; a new visibility starts on All.
  final ValueChanged<PixivBookmarkFilter> onFilter;
  final PixivIllustListStore store;
  final ScrollController? scrollController;

  const PixivFavoritesSection({
    super.key,
    required this.restrict,
    this.tag,
    required this.onFilter,
    required this.store,
    this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        PluginFilterRow(
          children: [
            ...pixivBookmarkRestrictChips(
              l10n,
              restrict: restrict,
              onSelected: (value) => onFilter((restrict: value, tag: restrict == value ? tag : null)),
            ),
            _tagChip(context, l10n),
          ],
        ),
        Expanded(
          child: PixivIllustFeed(store: store, emptyMessage: _emptyMessage(l10n), scrollController: scrollController),
        ),
      ],
    );
  }

  String _emptyMessage(L10n l10n) {
    if (tag != null) return l10n.plugin_pixiv_bookmark_tag_empty;
    return restrict == 'private' ? l10n.plugin_pixiv_bookmarks_private_empty : l10n.plugin_pixiv_bookmarks_empty;
  }

  Widget _tagChip(BuildContext context, L10n l10n) => InputChip(
    key: const ValueKey('pixiv-favorites-tag'),
    avatar: const Icon(Icons.sell_outlined, size: 18),
    label: Text(pixivBookmarkTagLabel(l10n, tag)),
    tooltip: l10n.plugin_pixiv_bookmark_tag_filter,
    selected: tag != null,
    showCheckmark: false,
    onPressed: () async {
      final chosen = await showPixivBookmarkTagPicker(context, (restrict: restrict, tag: tag));
      if (chosen != null) onFilter(chosen);
    },
    onDeleted: tag == null ? null : () => onFilter((restrict: restrict, tag: null)),
    deleteButtonTooltipMessage: l10n.plugin_pixiv_bookmark_tag_clear,
  );
}

/// Public and Private chips for whose bookmarks a list shows, works or
/// novels. The chip already chosen is offered too, so a tap on it can bring
/// the list back to the top.
List<Widget> pixivBookmarkRestrictChips(
  L10n l10n, {
  required String restrict,
  required ValueChanged<String> onSelected,
}) => [
  for (final (value, label) in [
    ('public', l10n.plugin_pixiv_bookmarks_public),
    ('private', l10n.plugin_pixiv_bookmarks_private),
  ])
    ChoiceChip(
      key: ValueKey('pixiv-bookmarks-restrict-$value'),
      label: Text(label),
      selected: restrict == value,
      onSelected: (_) => onSelected(value),
    ),
];
