import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/progressive_feed_store.dart';
import 'package:xta/ui/floating_notice.dart';
import 'package:xta/ui/loading_hairline.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/read_recovery.dart';

class ProgressiveFeedView extends StatelessWidget {
  final ProgressiveFeedStore store;
  final Widget Function(List<InterleavedItem>) builder;
  const ProgressiveFeedView({super.key, required this.store, required this.builder});
  @override
  Widget build(BuildContext context) => ScopedBuilder<ProgressiveFeedStore, ProgressiveFeedState>(
    store: store,
    // Nothing here takes room of its own. New posts waiting float over the top
    // of the feed; a hairline over the posts says a source is loading, and a
    // chip floats below for something the reader can act on — a failure or
    // cached posts.
    onState: (context, state) => FloatingNotice(
      notice: state.hasPending
          ? NoticePill(
              child: TextButton.icon(
                onPressed: store.reveal,
                icon: const Icon(Icons.arrow_upward),
                label: Text(L10n.of(context).reader_new_posts),
              ),
            )
          : null,
      child: FloatingNotice(
        notice: state.sources.values.any(_worthAChip)
            ? NoticePill(
                child: _SourceChips(store: store, sources: state.sources),
              )
            : null,
        child: LoadingHairline(
          loading: state.sources.values.any((source) => source.loading),
          semanticsLabel: L10n.of(context).reader_source_loading,
          child: builder(state.items),
        ),
      ),
    ),
  );
}

bool _worthAChip(FeedSourceState source) => source.error != null || (source.cachedAt != null && !source.loading);

/// A source that will retry on its own shows a clock rather than an error.
Widget _waiting(BuildContext context, int seconds) => const Icon(Icons.schedule, size: 16);

/// One chip per source worth acting on: a failure to retry or explain, or
/// cached posts to explain.
class _SourceChips extends StatelessWidget {
  final ProgressiveFeedStore store;
  final Map<String, FeedSourceState> sources;
  const _SourceChips({required this.store, required this.sources});

  @override
  Widget build(BuildContext context) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      // Clear of the pill's rounded ends.
      padding: const EdgeInsets.symmetric(horizontal: 8),
      child: Row(
        children: [
          for (final entry in sources.entries)
            if (_worthAChip(entry.value))
              ReadRecovery(
                key: ValueKey('source-recovery-${entry.key}'),
                isLoading: () => store.state.sources[entry.key]?.loading ?? false,
                recoverableFailure: () => recoverableReadFailure(store.state.sources[entry.key]?.error),
                retry: () => store.retry(entry.key),
                child: Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 4),
                  child: ActionChip(
                    avatar: entry.value.error != null
                        ? const ScheduledReadRetry(idle: Icon(Icons.error_outline, size: 16), builder: _waiting)
                        : const Icon(Icons.history, size: 16),
                    label: Text(pluginById(entry.key)?.title(context) ?? entry.key),
                    onPressed: () {
                      if (entry.value.error != null) {
                        showReaderFailureDetails(
                          context,
                          source: entry.key,
                          error: entry.value.error,
                          onRetry: () => store.retry(entry.key),
                        );
                        return;
                      }
                      showModalBottomSheet<void>(
                        context: context,
                        showDragHandle: true,
                        builder: (sheetContext) => SafeArea(
                          child: Padding(
                            padding: const EdgeInsets.all(16),
                            child: Text(L10n.of(context).reader_cached_content),
                          ),
                        ),
                      );
                    },
                  ),
                ),
              ),
        ],
      ),
    );
  }
}
