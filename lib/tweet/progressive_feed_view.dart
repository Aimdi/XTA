import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_registry.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/tweet/progressive_feed_store.dart';
import 'package:xta/ui/reader_failure.dart';
import 'package:xta/utils/read_recovery.dart';

class ProgressiveFeedView extends StatelessWidget {
  final ProgressiveFeedStore store;
  final Widget Function(List<InterleavedItem>) builder;
  const ProgressiveFeedView({super.key, required this.store, required this.builder});
  @override
  Widget build(BuildContext context) => ScopedBuilder<ProgressiveFeedStore, ProgressiveFeedState>(
    store: store,
    onState: (context, state) => Column(
      children: [
        if (state.hasPending)
          TextButton.icon(
            onPressed: store.reveal,
            icon: const Icon(Icons.arrow_upward),
            label: Text(L10n.of(context).reader_new_posts),
          ),
        if (state.sources.values.any((source) => source.loading || source.error != null || source.cachedAt != null))
          SingleChildScrollView(
            scrollDirection: Axis.horizontal,
            child: Row(
              children: [
                for (final entry in state.sources.entries)
                  if (entry.value.loading || entry.value.error != null || entry.value.cachedAt != null)
                    ReadRecovery(
                      key: ValueKey('source-recovery-${entry.key}'),
                      isLoading: () => store.state.sources[entry.key]?.loading ?? false,
                      recoverableFailure: () => recoverableReadFailure(store.state.sources[entry.key]?.error),
                      retry: () => store.retry(entry.key),
                      child: Padding(
                        padding: const EdgeInsets.symmetric(horizontal: 4),
                        child: ActionChip(
                          avatar: entry.value.loading
                              ? const SizedBox(width: 14, height: 14, child: CircularProgressIndicator(strokeWidth: 2))
                              : Icon(entry.value.error != null ? Icons.error_outline : Icons.history, size: 16),
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
                                  child: Text(
                                    entry.value.loading
                                        ? L10n.of(context).reader_source_loading
                                        : L10n.of(context).reader_cached_content,
                                  ),
                                ),
                              ),
                            );
                          },
                        ),
                      ),
                    ),
              ],
            ),
          ),
        Expanded(child: builder(state.items)),
      ],
    ),
  );
}
