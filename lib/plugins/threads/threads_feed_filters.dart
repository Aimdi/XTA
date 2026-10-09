import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:xta/plugins/threads/threads_feed_options.dart';

/// The Threads tab's filter control: a badge-counted button in the reader
/// header, or a labelled one when the tab is shown without that header.
class ThreadsFeedFilterControl extends StatelessWidget {
  final ThreadsFeedOptionsStore store;

  const ThreadsFeedFilterControl({super.key, required this.store});

  void _open(BuildContext context) => showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (_) => ThreadsFeedFilterSheet(store: store),
  );

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<ThreadsFeedOptionsStore, ThreadsFeedOptions>(
      store: store,
      onState: (context, options) => PluginDockContribution(
        slot: 'reading',
        content: PluginDockContent(
          trailing: [
            PluginDockFilterButton(
              key: const ValueKey('threads-filters'),
              activeCount: options.activeCount,
              onPressed: () => _open(context),
            ),
          ],
        ),
        fallback: Align(
          alignment: AlignmentDirectional.centerStart,
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 8),
            child: TextButton.icon(
              style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
              onPressed: () => _open(context),
              icon: Badge(isLabelVisible: options.filtered, child: const Icon(Icons.tune)),
              label: Text(L10n.of(context).filters),
            ),
          ),
        ),
      ),
    );
  }
}

/// What the Threads tab shows: all posts, only media or only links, with or
/// without replies and reposts.
class ThreadsFeedFilterSheet extends StatelessWidget {
  final ThreadsFeedOptionsStore store;

  const ThreadsFeedFilterSheet({super.key, required this.store});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<ThreadsFeedOptionsStore, ThreadsFeedOptions>(
      store: store,
      onState: (context, options) => ListView(
        shrinkWrap: true,
        padding: const EdgeInsets.fromLTRB(16, 0, 16, 24),
        children: [
          Text(l10n.filters, style: Theme.of(context).textTheme.titleLarge),
          const SizedBox(height: 16),
          SegmentedButton<ThreadsFeedContent>(
            segments: [
              ButtonSegment(value: ThreadsFeedContent.all, label: Text(l10n.all)),
              ButtonSegment(
                value: ThreadsFeedContent.media,
                icon: const Icon(Icons.photo_outlined),
                label: Text(l10n.media),
              ),
              ButtonSegment(
                value: ThreadsFeedContent.links,
                icon: const Icon(Icons.link),
                label: Text(l10n.search_links),
              ),
            ],
            selected: {options.content},
            onSelectionChanged: (selected) => store.set(options.copy(content: selected.first)),
          ),
          const SizedBox(height: 8),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.hide_replies),
            value: options.hideReplies,
            onChanged: (value) => store.set(options.copy(hideReplies: value)),
          ),
          SwitchListTile(
            contentPadding: EdgeInsets.zero,
            title: Text(l10n.plugin_threads_hide_reposts),
            value: options.hideReposts,
            onChanged: (value) => store.set(options.copy(hideReposts: value)),
          ),
          if (options.filtered)
            Align(
              alignment: AlignmentDirectional.centerStart,
              child: TextButton.icon(
                onPressed: store.reset,
                icon: const Icon(Icons.filter_alt_off_outlined),
                label: Text(l10n.plugin_reader_reset_filters),
              ),
            ),
        ],
      ),
    );
  }
}
