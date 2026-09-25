import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_reading_list.dart';
import 'package:xta/plugins/mastodon/mastodon_timeline_controls.dart';

class MastodonTimelinePane extends StatelessWidget {
  final String slot;
  final List<MastodonPost> posts;
  final List<MastodonTrendingTag> tags;
  final ScrollController controller;
  final Widget? heading;
  final String? instance;
  final bool loadingMore;
  final Object? loadMoreError;
  final Object? refreshError;
  final Future<void> Function()? onLoadMore;
  final Future<void> Function()? onRetryMore;
  final Future<void> Function()? onRefresh;
  const MastodonTimelinePane({
    super.key,
    required this.slot,
    required this.posts,
    required this.controller,
    this.tags = const [],
    this.heading,
    this.instance,
    this.loadingMore = false,
    this.loadMoreError,
    this.refreshError,
    this.onLoadMore,
    this.onRetryMore,
    this.onRefresh,
  });

  @override
  Widget build(BuildContext context) {
    final controls = context.read<MastodonTimelineControlsStore>();
    final l10n = L10n.of(context);
    return ScopedBuilder<MastodonTimelineControlsStore, Map<String, MastodonTimelineOptions>>(
      store: controls,
      onState: (context, _) {
        final options = controls.options(slot);
        final visible = filterMastodonTimeline(posts, options);
        return Column(
          children: [
            MastodonTimelineToolbar(store: controls, slot: slot, options: options),
            Expanded(
              child: MastodonReadingList(
                slot: slot,
                posts: visible,
                snapshotPosts: posts,
                tags: tags,
                instance: instance,
                controller: controller,
                loadingMore: loadingMore,
                heading: Column(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    if (refreshError != null)
                      Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 16),
                        child: Column(
                          children: [
                            Text(l10n.plugin_mastodon_refresh_failed),
                            TextButton.icon(
                              onPressed: onRefresh,
                              icon: const Icon(Icons.refresh),
                              label: Text(l10n.retry),
                            ),
                          ],
                        ),
                      ),
                    ?heading,
                    if (visible.isEmpty && posts.isNotEmpty)
                      Padding(
                        padding: const EdgeInsets.all(24),
                        child: Column(
                          children: [
                            const Icon(Icons.filter_alt_off_outlined, size: 36),
                            const SizedBox(height: 12),
                            Text(l10n.plugin_reader_empty_filter, textAlign: TextAlign.center),
                            TextButton(
                              onPressed: () => controls.reset(slot),
                              child: Text(l10n.plugin_reader_reset_filters),
                            ),
                          ],
                        ),
                      ),
                  ],
                ),
                footer: _footer(l10n),
              ),
            ),
          ],
        );
      },
    );
  }

  Widget? _footer(L10n l10n) {
    if (loadingMore) {
      return const Padding(
        padding: EdgeInsets.all(20),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (loadMoreError != null) {
      return Padding(
        padding: const EdgeInsets.all(16),
        child: Column(
          children: [
            Text(l10n.plugin_mastodon_load_more_failed, textAlign: TextAlign.center),
            TextButton.icon(onPressed: onRetryMore, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
          ],
        ),
      );
    }
    if (onLoadMore == null) return null;
    return Padding(
      padding: const EdgeInsets.all(12),
      child: Center(
        child: TextButton.icon(
          onPressed: onLoadMore,
          icon: const Icon(Icons.expand_more),
          label: Text(l10n.plugin_mastodon_load_more),
        ),
      ),
    );
  }
}
