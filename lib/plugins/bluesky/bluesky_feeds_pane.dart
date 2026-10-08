import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_store.dart';
import 'package:xta/plugins/bluesky/bluesky_import_list_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_source_reader.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_filter_row.dart';
import 'package:xta/ui/feed_list.dart';

class BlueskyAlgoPane extends StatefulWidget {
  final ScrollController scrollController;
  const BlueskyAlgoPane({super.key, required this.scrollController});
  @override
  State<BlueskyAlgoPane> createState() => _BlueskyAlgoPaneState();
}

class _BlueskyAlgoPaneState extends State<BlueskyAlgoPane> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _load();
    });
  }

  Future<void> _load({bool force = false}) => context.read<BlueskyAlgoStore>().ensureLoaded(
    force: force,
    discoverName: L10n.of(context).plugin_bluesky_discover,
  );

  Future<void> _openFeed() async {
    final l10n = L10n.of(context);
    final raw = await _prompt(context, title: l10n.plugin_bluesky_open_feed, hint: l10n.plugin_bluesky_feed_hint);
    if (raw == null || !mounted) return;
    final ref = parseBlueskyFeedRef(raw);
    if (ref == null) return _snack(context, l10n.plugin_bluesky_invalid_feed);
    final store = context.read<BlueskyAlgoStore>();
    final client = context.read<BlueskyClient>();
    try {
      final uri = await client.resolveFeedUri(ref);
      if (mounted) await store.open(uri);
    } catch (error) {
      if (mounted) _snack(context, blueskyErrorMessage(l10n, error));
    }
  }

  void _browse() => Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => BlueskyFeedCatalogScreen(store: context.read<BlueskyAlgoStore>())),
  );

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<BlueskyAlgoStore>();
    return ScopedBuilder<BlueskyAlgoStore, BlueskyAlgoState>(
      store: store,
      onState: (_, state) {
        final selected = _selectedGenerator(state, l10n);
        return _SourceTimeline(
          scrollController: widget.scrollController,
          page: state.page,
          onRefresh: () => _load(force: true),
          onMore: store.loadMore,
          onRetry: () => store.open(state.selectedUri ?? kBlueskyDiscoverFeedUri, force: true),
          header: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _ChipStrip(
                chips: _algoChips(l10n, state),
                onTap: (uri) =>
                    store.open(uri, name: uri == kBlueskyDiscoverFeedUri ? l10n.plugin_bluesky_discover : null),
              ),
              _selectedHeader(
                context,
                title: selected.displayName,
                description: selected.description,
                creator: selected.creatorHandle,
                pinned: state.isPinned(selected.uri),
                onOpen: _openFeed,
                openLabel: l10n.plugin_bluesky_open_feed,
                onPinToggle: () => _runPin(
                  context,
                  () => state.isPinned(selected.uri) ? store.unpin(selected.uri) : store.pin(selected),
                ),
                extra: TextButton.icon(
                  onPressed: _browse,
                  icon: const Icon(Icons.manage_search),
                  label: Text(l10n.plugin_bluesky_find_feeds),
                ),
              ),
              if (state.catalogError != null || state.createdError != null)
                _FeedFailure(
                  error: state.catalogError ?? state.createdError!,
                  onRetry: () => store.loadCatalog(force: true),
                ),
            ],
          ),
          emptyMessage: l10n.plugin_bluesky_feed_empty,
        );
      },
    );
  }
}

class BlueskyListsPane extends StatefulWidget {
  final ScrollController scrollController;
  const BlueskyListsPane({super.key, required this.scrollController});
  @override
  State<BlueskyListsPane> createState() => _BlueskyListsPaneState();
}

class _BlueskyListsPaneState extends State<BlueskyListsPane> {
  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) context.read<BlueskyListsStore>().ensureLoaded();
    });
  }

  Future<void> _lookup() async {
    final l10n = L10n.of(context);
    final raw = await _prompt(
      context,
      title: l10n.plugin_bluesky_lookup_lists,
      hint: l10n.plugin_bluesky_import_list_hint,
    );
    if (raw == null || !mounted) return;
    final store = context.read<BlueskyListsStore>();
    final client = context.read<BlueskyClient>();
    try {
      final ref = parseBlueskyListRef(raw);
      if (ref != null) {
        final uri = await client.resolveListUri(ref);
        if (mounted) await store.open(uri);
      } else {
        final handle = normaliseBlueskyHandle(raw);
        if (handle == null) return _snack(context, l10n.plugin_bluesky_invalid_list);
        await store.lookupActor(handle);
      }
    } catch (error) {
      if (mounted) _snack(context, blueskyErrorMessage(l10n, error));
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<BlueskyListsStore>();
    return ScopedBuilder<BlueskyListsStore, BlueskyListsState>(
      store: store,
      onState: (_, state) {
        final lists = <String, BlueskyListInfo>{};
        for (final list in [...state.pinned, ...state.actorLists]) {
          lists.putIfAbsent(list.uri, () => list);
        }
        final selected =
            lists[state.selectedUri] ?? BlueskyListInfo(uri: state.selectedUri ?? '', name: state.selectedName);
        return _SourceTimeline(
          scrollController: widget.scrollController,
          page: state.page,
          onRefresh: () => store.ensureLoaded(force: true),
          onMore: store.loadMore,
          onRetry: () =>
              state.selectedUri == null ? store.ensureLoaded(force: true) : store.open(state.selectedUri!, force: true),
          header: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              _ChipStrip(
                chips: [
                  for (final list in lists.values)
                    _FeedChip(uri: list.uri, label: list.name, selected: state.selectedUri == list.uri),
                ],
                onTap: (uri) => store.open(uri),
              ),
              if (state.listsLoading) const LinearProgressIndicator(),
              if (state.listsError != null)
                _FeedFailure(error: state.listsError!, onRetry: () => store.lookupActor(state.actor)),
              if (state.listsLoaded && state.actorLists.isEmpty)
                Padding(padding: const EdgeInsets.all(16), child: Text(l10n.plugin_bluesky_no_lists_found)),
              if (state.listsCursor != null)
                TextButton(
                  onPressed: state.listsLoading ? null : store.loadMoreLists,
                  child: Text(l10n.plugin_bluesky_load_more),
                ),
              _selectedHeader(
                context,
                title: selected.name.isEmpty ? l10n.plugin_bluesky_lists : selected.name,
                description: selected.description,
                creator: selected.creatorHandle,
                pinned: state.isPinned(selected.uri),
                onOpen: _lookup,
                openLabel: l10n.plugin_bluesky_lookup_lists,
                onPinToggle: selected.uri.isEmpty
                    ? null
                    : () => _runPin(
                        context,
                        () => state.isPinned(selected.uri) ? store.unpin(selected.uri) : store.pin(selected),
                      ),
                extra: selected.uri.isEmpty
                    ? null
                    : TextButton.icon(
                        onPressed: () => Navigator.push(
                          context,
                          MaterialPageRoute(builder: (_) => BlueskyImportListScreen(initialList: selected.uri)),
                        ),
                        icon: const Icon(Icons.cloud_download_outlined),
                        label: Text(l10n.plugin_bluesky_import_list),
                      ),
              ),
            ],
          ),
          emptyMessage: state.selectedUri == null ? l10n.plugin_bluesky_lists_empty : l10n.plugin_bluesky_feed_empty,
        );
      },
    );
  }
}

class _SourceTimeline extends StatelessWidget {
  final ScrollController scrollController;
  final BlueskySourcePage page;
  final Widget header;
  final String emptyMessage;
  final Future<void> Function() onRefresh;
  final Future<void> Function() onRetry;
  final Future<void> Function() onMore;
  const _SourceTimeline({
    required this.scrollController,
    required this.page,
    required this.header,
    required this.emptyMessage,
    required this.onRefresh,
    required this.onRetry,
    required this.onMore,
  });

  @override
  Widget build(BuildContext context) => NotificationListener<ScrollNotification>(
    onNotification: (notification) {
      if (notification.depth == 0 &&
          notification.metrics.axis == Axis.vertical &&
          page.hasMore &&
          page.moreError == null &&
          notification.metrics.pixels > 0 &&
          notification.metrics.extentAfter < 400) {
        onMore();
      }
      return false;
    },
    child: RefreshIndicator(
      onRefresh: onRefresh,
      child: FeedListView(
        controller: pluginInnerScrollController(context, scrollController),
        padding: pluginFeedPadding(context),
        itemCount: page.posts.length + 3,
        itemBuilder: (context, index) {
          if (index == 0) return header;
          if (index == 1) {
            return Column(
              children: [
                if (page.loading) const LinearProgressIndicator(),
                if (page.error != null) _FeedFailure(error: page.error!, onRetry: onRetry),
                if (!page.loading && page.error == null && page.posts.isEmpty)
                  Padding(
                    padding: const EdgeInsets.all(32),
                    child: Text(emptyMessage, textAlign: TextAlign.center),
                  ),
              ],
            );
          }
          if (index == page.posts.length + 2) return _SourceFooter(page: page, onMore: onMore);
          final post = page.posts[index - 2];
          return BlueskyPostCard(key: ValueKey(post.uri), post: post, showSourceBadge: false);
        },
      ),
    ),
  );
}

class _SourceFooter extends StatelessWidget {
  final BlueskySourcePage page;
  final Future<void> Function() onMore;
  const _SourceFooter({required this.page, required this.onMore});
  @override
  Widget build(BuildContext context) {
    if (page.loadingMore) {
      return const Padding(
        padding: EdgeInsets.all(20),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (page.moreError != null) return _FeedFailure(error: page.moreError!, onRetry: onMore);
    if (!page.hasMore) return const SizedBox.shrink();
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Center(
        child: TextButton.icon(
          onPressed: page.loading ? null : onMore,
          icon: const Icon(Icons.expand_more),
          label: Text(L10n.of(context).plugin_bluesky_load_more),
        ),
      ),
    );
  }
}

class _FeedFailure extends StatelessWidget {
  final Object error;
  final Future<void> Function() onRetry;
  const _FeedFailure({required this.error, required this.onRetry});
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          Text(blueskyErrorMessage(l10n, error), textAlign: TextAlign.center),
          TextButton.icon(onPressed: onRetry, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
        ],
      ),
    );
  }
}

class BlueskyFeedCatalogScreen extends StatefulWidget {
  final BlueskyAlgoStore store;
  const BlueskyFeedCatalogScreen({super.key, required this.store});
  @override
  State<BlueskyFeedCatalogScreen> createState() => _BlueskyFeedCatalogScreenState();
}

class _BlueskyFeedCatalogScreenState extends State<BlueskyFeedCatalogScreen> {
  late final TextEditingController _query = TextEditingController(text: widget.store.state.query);
  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = widget.store;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.plugin_bluesky_find_feeds)),
      body: Column(
        children: [
          Padding(
            padding: const EdgeInsets.all(16),
            child: TextField(
              controller: _query,
              textInputAction: TextInputAction.search,
              decoration: InputDecoration(
                labelText: l10n.plugin_bluesky_search_feeds_hint,
                prefixIcon: const Icon(Icons.search),
                suffixIcon: IconButton(
                  tooltip: l10n.search,
                  onPressed: () => store.searchCatalog(_query.text),
                  icon: const Icon(Icons.arrow_forward),
                ),
              ),
              onSubmitted: store.searchCatalog,
            ),
          ),
          Expanded(
            child: ScopedBuilder<BlueskyAlgoStore, BlueskyAlgoState>(
              store: store,
              onState: (_, state) {
                final feeds = <String, BlueskyFeedGenerator>{};
                for (final feed in [
                  ...(state.query.isEmpty ? [...state.pinned, ...state.created] : <BlueskyFeedGenerator>[]),
                  ...state.popular,
                ]) {
                  feeds.putIfAbsent(feed.uri, () => feed);
                }
                final entries = feeds.values.toList(growable: false);
                return RefreshIndicator(
                  onRefresh: () => store.searchCatalog(state.query),
                  child: ListView.builder(
                    physics: const AlwaysScrollableScrollPhysics(),
                    itemCount: entries.length + 1,
                    itemBuilder: (context, index) {
                      if (index == entries.length) {
                        return Column(
                          children: [
                            if (state.catalogLoading)
                              const Padding(padding: EdgeInsets.all(24), child: CircularProgressIndicator()),
                            if (state.catalogError != null)
                              _FeedFailure(
                                error: state.catalogError!,
                                onRetry: () => state.catalogCursor == null
                                    ? store.searchCatalog(state.query)
                                    : store.loadMoreCatalog(),
                              ),
                            if (!state.catalogLoading && state.catalogError == null && entries.isEmpty)
                              Padding(
                                padding: const EdgeInsets.all(24),
                                child: Text(l10n.plugin_bluesky_no_feeds_found),
                              ),
                            if (!state.catalogLoading && state.catalogError == null && state.catalogCursor != null)
                              TextButton(onPressed: store.loadMoreCatalog, child: Text(l10n.plugin_bluesky_load_more)),
                          ],
                        );
                      }
                      final feed = entries[index];
                      return ListTile(
                        isThreeLine: feed.description.isNotEmpty && feed.creatorHandle != null,
                        title: Text(feed.displayName),
                        selected: state.selectedUri == feed.uri,
                        subtitle: Text(
                          [
                            if (feed.creatorHandle != null) '@${feed.creatorHandle}',
                            if (feed.description.isNotEmpty) feed.description,
                          ].join('\n'),
                        ),
                        trailing: IconButton(
                          tooltip: state.isPinned(feed.uri) ? l10n.unpin : l10n.pin,
                          icon: Icon(state.isPinned(feed.uri) ? Icons.push_pin : Icons.push_pin_outlined),
                          onPressed: () => _runPin(
                            context,
                            () => state.isPinned(feed.uri) ? store.unpin(feed.uri) : store.pin(feed),
                          ),
                        ),
                        onTap: () {
                          Navigator.pop(context);
                          store.open(feed.uri, name: feed.displayName);
                        },
                      );
                    },
                  ),
                );
              },
            ),
          ),
        ],
      ),
    );
  }
}

List<_FeedChip> _algoChips(L10n l10n, BlueskyAlgoState state) {
  final seen = <String>{kBlueskyDiscoverFeedUri};
  return [
    _FeedChip(
      uri: kBlueskyDiscoverFeedUri,
      label: l10n.plugin_bluesky_discover,
      selected: state.selectedUri == null || state.selectedUri == kBlueskyDiscoverFeedUri,
    ),
    for (final feed in [...state.pinned, ...state.created, ...state.popular])
      if (feed.uri.isNotEmpty && seen.add(feed.uri))
        _FeedChip(uri: feed.uri, label: feed.displayName, selected: state.selectedUri == feed.uri),
  ];
}

class _FeedChip {
  final String uri;
  final String label;
  final bool selected;
  const _FeedChip({required this.uri, required this.label, required this.selected});
}

class _ChipStrip extends StatelessWidget {
  final List<_FeedChip> chips;
  final ValueChanged<String> onTap;
  const _ChipStrip({required this.chips, required this.onTap});
  @override
  Widget build(BuildContext context) => chips.isEmpty
      ? const SizedBox.shrink()
      : PluginFilterRow(
          children: [
            for (final chip in chips)
              ChoiceChip(
                label: ConstrainedBox(
                  constraints: const BoxConstraints(maxWidth: 240),
                  child: Text(chip.label, maxLines: 2, overflow: TextOverflow.ellipsis),
                ),
                selected: chip.selected,
                showCheckmark: true,
                materialTapTargetSize: MaterialTapTargetSize.padded,
                onSelected: (_) => onTap(chip.uri),
              ),
          ],
        );
}

Widget _selectedHeader(
  BuildContext context, {
  required String title,
  String description = '',
  String? creator,
  required bool pinned,
  required VoidCallback onOpen,
  required String openLabel,
  VoidCallback? onPinToggle,
  Widget? extra,
}) {
  final l10n = L10n.of(context);
  return Padding(
    padding: const EdgeInsetsDirectional.fromSTEB(16, 12, 16, 8),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Semantics(
          header: true,
          child: Text(title, style: Theme.of(context).textTheme.titleMedium?.copyWith(fontWeight: FontWeight.w700)),
        ),
        if (creator != null) Text('@$creator', style: Theme.of(context).textTheme.bodySmall),
        if (description.isNotEmpty) Padding(padding: const EdgeInsets.only(top: 4), child: Text(description)),
        Wrap(
          spacing: 8,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            TextButton.icon(onPressed: onOpen, icon: const Icon(Icons.link), label: Text(openLabel)),
            if (onPinToggle != null)
              TextButton.icon(
                icon: Icon(pinned ? Icons.push_pin : Icons.push_pin_outlined),
                onPressed: onPinToggle,
                label: Text(pinned ? l10n.unpin : l10n.pin),
              ),
            ?extra,
          ],
        ),
      ],
    ),
  );
}

BlueskyFeedGenerator _selectedGenerator(BlueskyAlgoState state, L10n l10n) {
  final uri = state.selectedUri ?? kBlueskyDiscoverFeedUri;
  for (final feed in [...state.pinned, ...state.created, ...state.popular]) {
    if (feed.uri == uri) return feed;
  }
  return BlueskyFeedGenerator(
    uri: uri,
    displayName: state.selectedName.isEmpty ? l10n.plugin_bluesky_discover : state.selectedName,
  );
}

Future<void> _runPin(BuildContext context, Future<void> Function() action) async {
  try {
    await action();
  } catch (error) {
    if (context.mounted) _snack(context, blueskyErrorMessage(L10n.of(context), error));
  }
}

void _snack(BuildContext context, String message) =>
    ScaffoldMessenger.of(context).showSnackBar(SnackBar(content: Text(message)));

Future<String?> _prompt(BuildContext context, {required String title, required String hint}) => showDialog<String>(
  context: context,
  builder: (_) => _BlueskyPromptDialog(title: title, hint: hint),
);

class _BlueskyPromptDialog extends StatefulWidget {
  final String title;
  final String hint;
  const _BlueskyPromptDialog({required this.title, required this.hint});
  @override
  State<_BlueskyPromptDialog> createState() => _BlueskyPromptDialogState();
}

class _BlueskyPromptDialogState extends State<_BlueskyPromptDialog> {
  final _controller = TextEditingController();
  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(widget.title),
      content: TextField(
        controller: _controller,
        autofocus: true,
        decoration: InputDecoration(hintText: widget.hint),
        onSubmitted: (value) => Navigator.pop(context, value.trim()),
      ),
      actions: [
        TextButton(onPressed: () => Navigator.pop(context), child: Text(l10n.cancel)),
        TextButton(onPressed: () => Navigator.pop(context, _controller.text.trim()), child: Text(l10n.ok)),
      ],
    );
  }
}
