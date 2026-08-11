import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_image.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_store.dart';

class BooruPostScreen extends StatelessWidget {
  final BooruPost post;

  const BooruPostScreen({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text('#${post.id}'),
        actions: [
          if (post.source != null && post.source!.trim().isNotEmpty)
            IconButton(
              tooltip: l10n.plugin_booru_open_source,
              icon: const Icon(Icons.open_in_new),
              onPressed: () => _open(post.source!),
            ),
          IconButton(
            tooltip: l10n.plugin_booru_open_file,
            icon: const Icon(Icons.fullscreen),
            onPressed: () {
              final url = post.fileUrl ?? post.sampleUrl ?? post.previewUrl;
              if (url != null) _open(url);
            },
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.only(bottom: 24),
        children: [
          Hero(
            tag: booruPostHeroTag(post),
            child: AspectRatio(
              aspectRatio: post.aspectRatio.clamp(0.4, 2.2),
              child: BooruNetworkImage(
                url: post.displayUrl,
                fit: BoxFit.contain,
              ),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
            child: Wrap(
              spacing: 12,
              runSpacing: 4,
              children: [
                if (post.score != null)
                  Text(l10n.plugin_booru_score(post.score!)),
                if (post.rating != null)
                  Text(l10n.plugin_booru_rating_label(post.rating!.code)),
                Text('${post.width}×${post.height}'),
              ],
            ),
          ),
          const SizedBox(height: 12),
          ScopedBuilder<BooruTagsStore, List<String>>(
            store: context.read<BooruTagsStore>(),
            onState: (context, followed) {
              final tags = followed.toSet();
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final tag in post.tags)
                      ActionChip(
                        label: Text(tag),
                        avatar: Icon(
                          tags.contains(tag) ? Icons.check : Icons.add,
                          size: 16,
                        ),
                        onPressed: () async {
                          final store = context.read<BooruTagsStore>();
                          if (tags.contains(tag)) {
                            await store.remove(tag);
                          } else {
                            await store.add(tag);
                          }
                        },
                      ),
                  ],
                ),
              );
            },
          ),
          if (post.source != null && post.source!.trim().isNotEmpty)
            ListTile(
              title: Text(l10n.plugin_booru_source),
              subtitle: Text(post.source!),
              onTap: () => _open(post.source!),
            ),
          ListTile(
            dense: true,
            title: Text(post.host, style: theme.textTheme.bodySmall),
          ),
        ],
      ),
    );
  }

  Future<void> _open(String url) async {
    final uri = Uri.tryParse(url);
    if (uri == null) return;
    await launchUrl(uri, mode: LaunchMode.externalApplication);
  }
}
