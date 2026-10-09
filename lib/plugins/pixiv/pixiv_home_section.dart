import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_following_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_view_state.dart';
import 'package:xta/plugins/plugin_filter_row.dart';

/// Home: the source chips over the chosen feed.
class PixivHomeSection extends StatelessWidget {
  final PixivHomeSource source;
  final ValueChanged<PixivHomeSource> onSource;
  final PixivIllustListStore following;
  final PixivIllustListStore recommended;

  /// The Home tab's own controller, so tapping Home scrolls Following to the top.
  final ScrollController scrollController;

  const PixivHomeSection({
    super.key,
    required this.source,
    required this.onSource,
    required this.following,
    required this.recommended,
    required this.scrollController,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Column(
      children: [
        PluginFilterRow(
          children: [
            _chip(l10n.plugin_pixiv_tab_following, PixivHomeSource.following),
            IconButton(
              tooltip: l10n.following,
              icon: const Icon(Icons.people_outline),
              onPressed: () => Navigator.push(context, MaterialPageRoute(builder: (_) => const PixivFollowingScreen())),
            ),
            const SizedBox(width: 8),
            _chip(l10n.plugin_pixiv_tab_recommended, PixivHomeSource.recommended),
          ],
        ),
        Expanded(child: _feed(l10n)),
      ],
    );
  }

  Widget _chip(String label, PixivHomeSource value) => ChoiceChip(
    label: Text(label),
    selected: source == value,
    onSelected: (_) {
      if (source != value) onSource(value);
    },
  );

  Widget _feed(L10n l10n) => switch (source) {
    PixivHomeSource.following => PixivIllustFeed(
      store: following,
      emptyMessage: l10n.plugin_pixiv_empty,
      scrollController: scrollController,
    ),
    PixivHomeSource.recommended => PixivIllustFeed(
      store: recommended,
      emptyMessage: l10n.plugin_pixiv_recommended_empty,
    ),
  };
}
