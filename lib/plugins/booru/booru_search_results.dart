import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_errors.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/ui/errors.dart';

/// Posts for the current query, with the tags they share most as one-tap
/// refinements.
class BooruSearchResults extends StatelessWidget {
  final BooruSearchStore store;
  final BooruFeedStore results;

  const BooruSearchResults({super.key, required this.store, required this.results});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<BooruFeedStore, List<BooruPost>>(
      store: results,
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onError: (_, error) => FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: booruErrorMessage(l10n, error),
        onRetry: results.refresh,
      ),
      onState: (context, posts) {
        if (posts.isEmpty) {
          return Center(child: Text(l10n.plugin_booru_empty_search));
        }
        final related = booruRelatedTags(posts, store.state.tags);
        return Column(
          children: [
            if (related.isNotEmpty) _RelatedTags(tags: related, onAdd: store.refine),
            Expanded(
              child: BooruPostGrid(
                posts: posts,
                onRefresh: results.refresh,
                loadingMore: results.loadingMore,
                onNearEnd: results.loadMore,
              ),
            ),
          ],
        );
      },
    );
  }
}

class _RelatedTags extends StatelessWidget {
  final List<String> tags;
  final ValueChanged<String> onAdd;

  const _RelatedTags({required this.tags, required this.onAdd});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Semantics(
      container: true,
      label: l10n.plugin_booru_related_tags,
      child: SingleChildScrollView(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsetsDirectional.fromSTEB(4, 4, 12, 0),
        child: Row(
          children: [
            Tooltip(
              message: l10n.plugin_booru_related_hint,
              triggerMode: TooltipTriggerMode.tap,
              child: SizedBox(
                width: 48,
                height: 48,
                child: Icon(Icons.auto_awesome_outlined, size: 20, color: theme.colorScheme.onSurfaceVariant),
              ),
            ),
            for (final tag in tags)
              Padding(
                padding: const EdgeInsetsDirectional.only(end: 8),
                child: Semantics(
                  onTapHint: l10n.plugin_booru_add_to_search,
                  onLongPressHint: l10n.plugin_booru_exclude_tag,
                  child: GestureDetector(
                    onLongPress: () => onAdd('${BooruTagOperator.exclude.prefix}$tag'),
                    child: ActionChip(
                      avatar: const Icon(Icons.add, size: 18),
                      label: Text(tag),
                      materialTapTargetSize: MaterialTapTargetSize.padded,
                      onPressed: () => onAdd(tag),
                    ),
                  ),
                ),
              ),
          ],
        ),
      ),
    );
  }
}
