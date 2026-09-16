import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_thread_screen.dart';

class BlueskyMediaGrid extends StatelessWidget {
  final List<BlueskyPost> posts;
  const BlueskyMediaGrid({super.key, required this.posts});

  @override
  Widget build(BuildContext context) {
    final media = posts.where((post) => post.hasMedia).toList(growable: false);
    return SliverPadding(
      padding: const EdgeInsets.all(4),
      sliver: SliverLayoutBuilder(
        builder: (context, constraints) => SliverGrid.builder(
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: (constraints.crossAxisExtent / 150).floor().clamp(2, 5),
            mainAxisSpacing: 4,
            crossAxisSpacing: 4,
          ),
          itemCount: media.length,
          itemBuilder: (context, index) => BlueskyMediaTile(post: media[index]),
        ),
      ),
    );
  }
}

class BlueskyMediaTile extends StatelessWidget {
  final BlueskyPost post;
  const BlueskyMediaTile({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final l10n = L10n.of(context);
    return Semantics(
      button: true,
      label: '${post.authorName} · ${post.sensitive ? l10n.content_warning : l10n.media}',
      child: Material(
        clipBehavior: Clip.antiAlias,
        borderRadius: BorderRadius.circular(6),
        color: colors.surfaceContainerHighest,
        child: InkWell(
          key: ValueKey('bluesky-media-${post.uri}'),
          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => BlueskyThreadScreen(post: post))),
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (post.sensitive)
                Center(
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.visibility_off_outlined),
                        const SizedBox(height: 6),
                        Text(
                          l10n.content_warning,
                          textAlign: TextAlign.center,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: Theme.of(context).textTheme.labelSmall,
                        ),
                      ],
                    ),
                  ),
                )
              else if (post.hasMedia)
                LayoutBuilder(
                  builder: (context, constraints) => ExtendedImage.network(
                    post.images.first,
                    fit: BoxFit.cover,
                    cacheWidth: (constraints.maxWidth * MediaQuery.devicePixelRatioOf(context)).ceil(),
                    loadStateChanged: (state) => state.extendedImageLoadState == LoadState.failed
                        ? Icon(Icons.broken_image_outlined, color: colors.onSurfaceVariant)
                        : null,
                  ),
                ),
              if (!post.sensitive && post.imageIsVideo.isNotEmpty && post.imageIsVideo.first)
                const Center(child: _MediaBadge(icon: Icons.play_arrow_rounded)),
              if (post.images.length > 1)
                const PositionedDirectional(end: 6, top: 6, child: _MediaBadge(icon: Icons.collections_outlined)),
            ],
          ),
        ),
      ),
    );
  }
}

class _MediaBadge extends StatelessWidget {
  final IconData icon;
  const _MediaBadge({required this.icon});
  @override
  Widget build(BuildContext context) => DecoratedBox(
    decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(4)),
    child: Padding(
      padding: const EdgeInsets.all(5),
      child: Icon(icon, color: Colors.white, size: 20),
    ),
  );
}
