import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_screen.dart';

class MastodonMediaGrid extends StatelessWidget {
  final List<MastodonPost> posts;
  const MastodonMediaGrid({super.key, required this.posts});

  @override
  Widget build(BuildContext context) {
    final media = posts.where((post) => post.hasMedia).toList();
    return SliverLayoutBuilder(
      builder: (context, constraints) {
        final columns = (constraints.crossAxisExtent / 135).floor().clamp(2, 5);
        return SliverPadding(
          padding: const EdgeInsets.all(4),
          sliver: SliverGrid.builder(
            itemCount: media.length,
            gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
              crossAxisCount: columns,
              mainAxisSpacing: 4,
              crossAxisSpacing: 4,
            ),
            itemBuilder: (context, index) => MastodonMediaTile(post: media[index]),
          ),
        );
      },
    );
  }
}

class MastodonMediaTile extends StatelessWidget {
  final MastodonPost post;
  const MastodonMediaTile({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final colors = Theme.of(context).colorScheme;
    final hidden = post.sensitive || post.hasSpoiler;
    return Semantics(
      button: true,
      label: '${post.authorName} · ${hidden ? l10n.content_warning : l10n.media}',
      child: Material(
        color: colors.surfaceContainerHighest,
        clipBehavior: Clip.antiAlias,
        borderRadius: BorderRadius.circular(6),
        child: InkWell(
          key: ValueKey('mastodon-media-${post.id}'),
          onTap: () => Navigator.push(context, MaterialPageRoute(builder: (_) => MastodonThreadScreen(post: post))),
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (!hidden && post.images.isNotEmpty)
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
              if (hidden)
                Center(
                  child: Padding(
                    padding: const EdgeInsets.all(8),
                    child: Column(
                      mainAxisSize: MainAxisSize.min,
                      children: [
                        const Icon(Icons.visibility_off_outlined),
                        const SizedBox(height: 4),
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
                ),
              if (post.images.length > 1)
                PositionedDirectional(
                  end: 6,
                  top: 6,
                  child: Container(
                    padding: const EdgeInsets.all(4),
                    decoration: BoxDecoration(color: Colors.black54, borderRadius: BorderRadius.circular(4)),
                    child: const Icon(Icons.collections_outlined, color: Colors.white, size: 18),
                  ),
                ),
            ],
          ),
        ),
      ),
    );
  }
}
