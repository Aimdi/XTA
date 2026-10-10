import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_filter_row.dart';

/// Favorites: the reader's own bookmarks, public or private.
class PixivFavoritesSection extends StatelessWidget {
  final String restrict;
  final ValueChanged<String> onRestrict;
  final PixivIllustListStore store;
  final ScrollController? scrollController;

  const PixivFavoritesSection({
    super.key,
    required this.restrict,
    required this.onRestrict,
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
            _chip(l10n.plugin_pixiv_bookmarks_public, 'public'),
            const SizedBox(width: 8),
            _chip(l10n.plugin_pixiv_bookmarks_private, 'private'),
          ],
        ),
        Expanded(
          child: PixivIllustFeed(
            store: store,
            emptyMessage: restrict == 'private'
                ? l10n.plugin_pixiv_bookmarks_private_empty
                : l10n.plugin_pixiv_bookmarks_empty,
            scrollController: scrollController,
          ),
        ),
      ],
    );
  }

  Widget _chip(String label, String value) =>
      ChoiceChip(label: Text(label), selected: restrict == value, onSelected: (_) => onRestrict(value));
}
