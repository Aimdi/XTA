import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_gallery_layout.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';

/// What every section but More shows before a Pixiv account is connected:
/// the sign-in button over a preview of works Pixiv shows without one.
class PixivSignInBody extends StatefulWidget {
  final bool signingIn;
  final VoidCallback onSignIn;

  const PixivSignInBody({super.key, required this.signingIn, required this.onSignIn});

  @override
  State<PixivSignInBody> createState() => _PixivSignInBodyState();
}

class _PixivSignInBodyState extends State<PixivSignInBody> {
  late final PixivIllustListStore _preview;

  @override
  void initState() {
    super.initState();
    final api = PixivDiscoveryApi.of(context);
    _preview = PixivIllustListStore(
      ({nextUrl}) => api.walkthrough(nextUrl: nextUrl),
      filter: context.read<PixivMuteStore>().filter,
    )..refresh();
  }

  @override
  void dispose() {
    _preview.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => NotificationListener<ScrollNotification>(
    onNotification: (notification) {
      if (notification.metrics.pixels > notification.metrics.maxScrollExtent - 800) _preview.loadMore();
      return false;
    },
    child: CustomScrollView(
      controller: pluginInnerScrollController(context, null),
      primary: PluginEmbedded.maybeOf(context) ? false : null,
      slivers: [
        SliverToBoxAdapter(child: _prompt(context)),
        ScopedBuilder<PixivIllustListStore, List<PixivIllust>>(
          store: _preview,
          onLoading: (_) => const SliverToBoxAdapter(child: SizedBox.shrink()),
          onError: (_, _) => const SliverToBoxAdapter(child: SizedBox.shrink()),
          onState: (context, works) => works.isEmpty ? const SliverToBoxAdapter() : _previewGrid(context, works),
        ),
      ],
    ),
  );

  Widget _prompt(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(32, 32, 32, 16),
      child: Column(
        children: [
          Text(l10n.plugin_pixiv_not_configured, textAlign: TextAlign.center),
          const SizedBox(height: 20),
          FilledButton(
            onPressed: widget.signingIn ? null : widget.onSignIn,
            child: widget.signingIn
                ? const SizedBox(width: 18, height: 18, child: CircularProgressIndicator(strokeWidth: 2))
                : Text(l10n.plugin_pixiv_sign_in),
          ),
        ],
      ),
    );
  }

  Widget _previewGrid(BuildContext context, List<PixivIllust> works) => SliverMainAxisGroup(
    slivers: [
      SliverPadding(
        padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
        sliver: SliverToBoxAdapter(
          child: Text(L10n.of(context).plugin_pixiv_walkthrough_title, style: Theme.of(context).textTheme.titleSmall),
        ),
      ),
      SliverPadding(
        padding: pluginFeedPadding(context, extra: const EdgeInsets.symmetric(horizontal: 8)),
        sliver: SliverLayoutBuilder(
          builder: (context, constraints) => SliverMasonryGrid.count(
            crossAxisCount: pluginGalleryColumns(constraints.crossAxisExtent, MediaQuery.textScalerOf(context)),
            mainAxisSpacing: 8,
            crossAxisSpacing: 8,
            childCount: works.length,
            itemBuilder: (context, index) => PixivWalkthroughTile(illust: works[index]),
          ),
        ),
      ),
    ],
  );
}

/// A preview work: only its picture, since opening it needs an account.
class PixivWalkthroughTile extends StatelessWidget {
  final PixivIllust illust;

  const PixivWalkthroughTile({super.key, required this.illust});

  void _askToSignIn(BuildContext context) {
    final messenger = ScaffoldMessenger.of(context);
    messenger.hideCurrentSnackBar();
    messenger.showSnackBar(SnackBar(content: Text(L10n.of(context).plugin_pixiv_walkthrough_sign_in)));
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    // The label replaces the picture's own semantics, so the tap is given here too.
    return Semantics(
      button: true,
      label: illust.title,
      excludeSemantics: true,
      onTap: () => _askToSignIn(context),
      child: Material(
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: BorderRadius.circular(8),
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          key: ValueKey('pixiv-walkthrough-${illust.id}'),
          onTap: () => _askToSignIn(context),
          child: AspectRatio(
            aspectRatio: illust.aspectRatio.clamp(0.45, 1.6),
            child: PixivNetworkImage(
              url: illust.thumbnailUrl,
              fit: BoxFit.cover,
              loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed
                  ? Icon(Icons.broken_image_outlined, color: theme.colorScheme.outline)
                  : null,
            ),
          ),
        ),
      ),
    );
  }
}
