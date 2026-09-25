import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_reader_store.dart';
import 'package:xta/plugins/plugin_reading_view.dart';

/// Local controls never change the feed generator's source order or page cursor.
class BlueskyReaderView extends StatefulWidget {
  final List<BlueskyPost> posts;
  final String slot;
  final ScrollController controller;
  final Widget? heading;
  final Widget? footer;
  const BlueskyReaderView({
    super.key,
    required this.posts,
    required this.slot,
    required this.controller,
    this.heading,
    this.footer,
  });
  @override
  State<BlueskyReaderView> createState() => _BlueskyReaderViewState();
}

class _BlueskyReaderViewState extends State<BlueskyReaderView> {
  late final BlueskyReaderStore _store;
  PluginReadingPosition<BlueskyPost>? _initial;
  bool _restore = false;
  @override
  void initState() {
    super.initState();
    _store = context.read<BlueskyReaderStore>();
    if (widget.slot == 'following') {
      final position = _store.layoutPoint();
      _initial = position.point;
      _restore = position.restore;
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<BlueskyReaderStore, BlueskyReaderState>(
      store: _store,
      onState: (context, _) {
        final options = _store.options(widget.slot);
        final visible = filterBlueskyReader(widget.posts, options);
        return Column(
          children: [
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 8),
              child: Row(
                children: [
                  Expanded(
                    child: Align(
                      alignment: AlignmentDirectional.centerStart,
                      child: TextButton.icon(
                        onPressed: () => showModalBottomSheet<void>(
                          context: context,
                          isScrollControlled: true,
                          useSafeArea: true,
                          builder: (_) => _ReaderFilters(store: _store, slot: widget.slot),
                        ),
                        icon: Badge(isLabelVisible: options.filtered, child: const Icon(Icons.tune)),
                        label: Text(l10n.filters),
                      ),
                    ),
                  ),
                  Flexible(
                    child: PopupMenuButton<BlueskyReaderOrder>(
                      tooltip: l10n.plugin_mastodon_sort,
                      initialValue: options.order,
                      onSelected: (order) => _store.configure(widget.slot, options.copy(order: order)),
                      itemBuilder: (_) => [
                        for (final order in BlueskyReaderOrder.values)
                          CheckedPopupMenuItem(
                            value: order,
                            checked: options.order == order,
                            child: Text(_orderLabel(l10n, order)),
                          ),
                      ],
                      child: Padding(
                        padding: const EdgeInsets.all(12),
                        child: Row(
                          mainAxisSize: MainAxisSize.min,
                          children: [
                            Flexible(child: Text(_orderLabel(l10n, options.order))),
                            const SizedBox(width: 4),
                            const Icon(Icons.sort, size: 20),
                          ],
                        ),
                      ),
                    ),
                  ),
                ],
              ),
            ),
            Expanded(
              child: PluginReadingView<BlueskyPost>(
                posts: visible,
                snapshotPosts: widget.posts,
                controller: widget.controller,
                initial: _initial,
                restoreInitial: _restore,
                keyOf: (post) => post.uri,
                onRemember: (point) {
                  if (widget.slot == 'following') _store.remember(point);
                },
                onFlush: _store.flush,
                heading: widget.heading,
                footer: visible.isEmpty
                    ? Column(
                        children: [
                          Padding(padding: const EdgeInsets.all(24), child: Text(l10n.plugin_reader_empty_filter)),
                          TextButton(
                            onPressed: () => _store.configure(widget.slot, const BlueskyReaderOptions()),
                            child: Text(l10n.plugin_reader_reset_filters),
                          ),
                          if (widget.footer != null) widget.footer!,
                        ],
                      )
                    : widget.footer,
                itemBuilder: (_, post) => BlueskyPostCard(key: ValueKey(post.uri), post: post, showSourceBadge: false),
              ),
            ),
          ],
        );
      },
    );
  }
}

String _orderLabel(L10n l10n, BlueskyReaderOrder order) => switch (order) {
  BlueskyReaderOrder.feed => l10n.plugin_mastodon_order_server,
  BlueskyReaderOrder.newest => l10n.plugin_mastodon_order_newest,
  BlueskyReaderOrder.oldest => l10n.plugin_mastodon_order_oldest,
};

class _ReaderFilters extends StatefulWidget {
  final BlueskyReaderStore store;
  final String slot;
  const _ReaderFilters({required this.store, required this.slot});
  @override
  State<_ReaderFilters> createState() => _ReaderFiltersState();
}

class _ReaderFiltersState extends State<_ReaderFilters> {
  late final _query = TextEditingController(text: widget.store.options(widget.slot).query);
  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<BlueskyReaderStore, BlueskyReaderState>(
      store: widget.store,
      onState: (context, _) {
        final options = widget.store.options(widget.slot);
        void select(BlueskyReaderOptions value) => widget.store.configure(widget.slot, value);
        return SingleChildScrollView(
          padding: EdgeInsets.fromLTRB(16, 16, 16, MediaQuery.viewInsetsOf(context).bottom + 24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              Row(
                children: [
                  Expanded(child: Text(l10n.filters, style: Theme.of(context).textTheme.titleLarge)),
                  IconButton(
                    tooltip: l10n.close,
                    onPressed: () => Navigator.pop(context),
                    icon: const Icon(Icons.close),
                  ),
                ],
              ),
              Text(l10n.plugin_mastodon_loaded_controls, style: Theme.of(context).textTheme.bodySmall),
              const SizedBox(height: 16),
              TextField(
                controller: _query,
                onChanged: (value) => select(options.copy(query: value)),
                decoration: InputDecoration(
                  labelText: l10n.plugin_mastodon_loaded_search,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: options.query.isEmpty
                      ? null
                      : IconButton(
                          tooltip: l10n.plugin_mastodon_clear_search,
                          icon: const Icon(Icons.clear),
                          onPressed: () {
                            _query.clear();
                            select(options.copy(query: ''));
                          },
                        ),
                ),
              ),
              const SizedBox(height: 12),
              Wrap(
                spacing: 8,
                children: [
                  for (final content in BlueskyReaderContent.values)
                    ChoiceChip(
                      selected: options.content == content,
                      onSelected: (_) => select(options.copy(content: content)),
                      label: Text(switch (content) {
                        BlueskyReaderContent.all => l10n.all,
                        BlueskyReaderContent.images => l10n.photos,
                        BlueskyReaderContent.videos => l10n.videos,
                        BlueskyReaderContent.links => l10n.search_links,
                      }),
                    ),
                ],
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(l10n.plugin_bluesky_hide_reposts),
                value: options.hideReposts,
                onChanged: (value) => select(options.copy(hideReposts: value)),
              ),
              SwitchListTile(
                contentPadding: EdgeInsets.zero,
                title: Text(l10n.hide_replies),
                value: options.hideReplies,
                onChanged: (value) => select(options.copy(hideReplies: value)),
              ),
              TextButton.icon(
                icon: const Icon(Icons.restart_alt),
                label: Text(l10n.plugin_reader_reset_filters),
                onPressed: () {
                  _query.clear();
                  select(const BlueskyReaderOptions());
                },
              ),
            ],
          ),
        );
      },
    );
  }
}
