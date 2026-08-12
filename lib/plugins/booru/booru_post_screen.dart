import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:url_launcher/url_launcher.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_image.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';
import 'package:xta/plugins/booru/booru_store.dart';

class BooruPostScreen extends StatelessWidget {
  final BooruPost post;

  const BooruPostScreen({super.key, required this.post});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final hostPage = post.hostPageUrl;

    return Scaffold(
      appBar: AppBar(
        title: Text('#${post.id}'),
        actions: [
          if (hostPage != null)
            IconButton(
              tooltip: l10n.plugin_booru_open_on_host,
              icon: const Icon(Icons.public),
              onPressed: () => _open(hostPage),
            ),
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
              child: Stack(
                fit: StackFit.expand,
                children: [
                  BooruNetworkImage(
                    url: post.isVideo ? post.thumbnailUrl : post.displayUrl,
                    fit: BoxFit.contain,
                  ),
                  if (post.isVideo)
                    Material(
                      color: Colors.black45,
                      child: InkWell(
                        onTap: () {
                          final url = post.fileUrl ?? post.sampleUrl;
                          if (url != null) _open(url);
                        },
                        child: Center(
                          child: Column(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              const Icon(
                                Icons.play_circle_outline,
                                size: 56,
                                color: Colors.white,
                              ),
                              const SizedBox(height: 8),
                              Text(
                                l10n.plugin_booru_open_video,
                                style: theme.textTheme.titleMedium?.copyWith(
                                  color: Colors.white,
                                ),
                              ),
                            ],
                          ),
                        ),
                      ),
                    ),
                ],
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
              final muted = context.read<BooruMuteStore>().state;
              return Padding(
                padding: const EdgeInsets.symmetric(horizontal: 12),
                child: Wrap(
                  spacing: 6,
                  runSpacing: 6,
                  children: [
                    for (final tag in post.tags)
                      GestureDetector(
                        onLongPress: () async {
                          final store = context.read<BooruTagsStore>();
                          if (tags.contains(tag)) {
                            await store.remove(tag);
                          } else {
                            await store.add(tag);
                          }
                        },
                        child: ActionChip(
                          label: Text(tag),
                          avatar: Icon(
                            tags.contains(tag)
                                ? Icons.check
                                : muted.contains(tag)
                                ? Icons.volume_off
                                : Icons.add,
                            size: 16,
                          ),
                          onPressed: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) =>
                                  BooruSearchScreen(initialQuery: tag),
                            ),
                          ),
                        ),
                      ),
                  ],
                ),
              );
            },
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Text(
              l10n.plugin_booru_tag_hint,
              style: theme.textTheme.bodySmall,
            ),
          ),
          if (post.source != null && post.source!.trim().isNotEmpty)
            ListTile(
              title: Text(l10n.plugin_booru_source),
              subtitle: Text(post.source!),
              onTap: () => _open(post.source!),
            ),
          if (hostPage != null)
            ListTile(
              title: Text(l10n.plugin_booru_open_on_host),
              subtitle: Text(post.host),
              onTap: () => _open(hostPage),
            )
          else
            ListTile(
              dense: true,
              title: Text(post.host, style: theme.textTheme.bodySmall),
            ),
          ScopedBuilder<BooruMuteStore, Set<String>>(
            store: context.read<BooruMuteStore>(),
            onState: (context, muted) {
              return ExpansionTile(
                title: Text(l10n.plugin_booru_mute_section),
                children: [
                  for (final tag in post.tags.take(40))
                    SwitchListTile(
                      dense: true,
                      title: Text(tag),
                      value: muted.contains(tag),
                      onChanged: (value) async {
                        final store = context.read<BooruMuteStore>();
                        if (value) {
                          await store.mute(tag);
                        } else {
                          await store.unmute(tag);
                        }
                      },
                    ),
                ],
              );
            },
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
