import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_feed.dart';
import 'package:xta/plugins/bluesky/bluesky_feeds_store.dart';
import 'package:xta/plugins/bluesky/bluesky_import_list_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// Discover / custom-feed tab. Loads once; tab switches reuse the cache.
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
      if (!mounted) {
        return;
      }
      context.read<BlueskyAlgoStore>().ensureLoaded(
        discoverName: L10n.of(context).plugin_bluesky_discover,
      );
    });
  }

  Future<void> _refresh() => context.read<BlueskyAlgoStore>().ensureLoaded(
    force: true,
    discoverName: L10n.of(context).plugin_bluesky_discover,
  );

  Future<void> _openFeed() async {
    final raw = await _prompt(
      context,
      title: L10n.of(context).plugin_bluesky_open_feed,
      hint: L10n.of(context).plugin_bluesky_feed_hint,
    );
    if (raw == null || !mounted) {
      return;
    }
    final ref = parseBlueskyFeedRef(raw);
    if (ref == null) {
      _snack(L10n.of(context).plugin_bluesky_invalid_feed);
      return;
    }
    final store = context.read<BlueskyAlgoStore>();
    try {
      final uri = await context.read<BlueskyClient>().resolveFeedUri(ref);
      await store.open(uri);
    } catch (e) {
      if (mounted) {
        _snack(blueskyErrorMessage(L10n.of(context), e));
      }
    }
  }

  void _snack(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<BlueskyAlgoStore>();
    return ScopedBuilder<BlueskyAlgoStore, BlueskyAlgoState>(
      store: store,
      distinct: (state) =>
          '${state.selectedUri}\n${blueskyFeedDistinct(state.posts)}\n${state.pinned.length}\n${state.popular.length}',
      onLoading: (_) {
        if (store.state.posts.isNotEmpty || store.state.selectedUri != null) {
          return _body(context, l10n, store, store.state);
        }
        return const Center(child: CircularProgressIndicator());
      },
      onError: (context, error) {
        if (store.state.posts.isNotEmpty) {
          return _body(context, l10n, store, store.state);
        }
        return Padding(
          padding: const EdgeInsets.all(24),
          child: FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: blueskyErrorMessage(l10n, error ?? Exception()),
            onRetry: _refresh,
          ),
        );
      },
      onState: (context, state) => _body(context, l10n, store, state),
    );
  }

  Widget _body(
    BuildContext context,
    L10n l10n,
    BlueskyAlgoStore store,
    BlueskyAlgoState state,
  ) {
    final chips = _algoChips(l10n, state);
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (state.hasMore &&
            notification.metrics.pixels >=
                notification.metrics.maxScrollExtent - 400) {
          store.loadMore();
        }
        return false;
      },
      child: RefreshIndicator(
        onRefresh: _refresh,
        child: state.posts.isEmpty
            ? EmptyPane(
                icon: Icons.auto_awesome_outlined,
                message: l10n.plugin_bluesky_feeds_empty,
                scrollController: widget.scrollController,
                onRefresh: _refresh,
                leading: _chipStrip(chips),
                action: TextButton.icon(
                  onPressed: _openFeed,
                  icon: const Icon(Icons.link),
                  label: Text(l10n.plugin_bluesky_open_feed),
                ),
              )
            : FeedListView(
                controller: pluginInnerScrollController(
                  context,
                  widget.scrollController,
                ),
                padding: pluginFeedPadding(context),
                itemCount: state.posts.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _chipStrip(chips);
                  }
                  if (index == 1) {
                    return _selectedHeader(
                      context,
                      title: state.selectedName.isEmpty
                          ? l10n.plugin_bluesky_discover
                          : state.selectedName,
                      pinned:
                          state.selectedUri != null &&
                          state.isPinned(state.selectedUri!),
                      onOpen: _openFeed,
                      openLabel: l10n.plugin_bluesky_open_feed,
                      onPinToggle: () {
                        final uri = state.selectedUri;
                        if (uri == null) {
                          return;
                        }
                        if (state.isPinned(uri)) {
                          store.unpin(uri);
                        } else {
                          store.pin(_selectedGenerator(state, l10n));
                        }
                      },
                    );
                  }
                  final post = state.posts[index - 2];
                  return BlueskyPostCard(
                    key: ValueKey(post.uri),
                    post: post,
                    showSourceBadge: false,
                  );
                },
              ),
      ),
    );
  }

  List<_FeedChip> _algoChips(L10n l10n, BlueskyAlgoState state) {
    final seen = <String>{};
    final chips = <_FeedChip>[];
    void add(String uri, String label) {
      if (uri.isEmpty || !seen.add(uri)) {
        return;
      }
      chips.add(
        _FeedChip(
          uri: uri,
          label: label,
          selected:
              state.selectedUri == uri ||
              (state.selectedUri == null && uri == kBlueskyDiscoverFeedUri),
        ),
      );
    }

    add(kBlueskyDiscoverFeedUri, l10n.plugin_bluesky_discover);
    for (final feed in state.pinned) {
      add(feed.uri, feed.displayName);
    }
    for (final feed in state.created) {
      add(feed.uri, feed.displayName);
    }
    for (final feed in state.popular) {
      add(feed.uri, feed.displayName);
    }
    return chips;
  }

  Widget _chipStrip(List<_FeedChip> chips) {
    final store = context.read<BlueskyAlgoStore>();
    return _ChipStrip(chips: chips, onTap: (uri) => store.open(uri));
  }
}

/// Lists tab: pinned lists, lookup by handle, and that list's posts.
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
      if (mounted) {
        context.read<BlueskyListsStore>().ensureLoaded();
      }
    });
  }

  Future<void> _refresh() =>
      context.read<BlueskyListsStore>().ensureLoaded(force: true);

  Future<void> _lookup() async {
    final l10n = L10n.of(context);
    final raw = await _prompt(
      context,
      title: l10n.plugin_bluesky_lookup_lists,
      hint: l10n.plugin_bluesky_import_list_hint,
    );
    if (raw == null || !mounted) {
      return;
    }
    final store = context.read<BlueskyListsStore>();
    final client = context.read<BlueskyClient>();
    final listRef = parseBlueskyListRef(raw);
    try {
      if (listRef != null) {
        final uri = await client.resolveListUri(listRef);
        await store.open(uri);
        return;
      }
      final handle = normaliseBlueskyHandle(raw);
      if (handle == null) {
        _snack(l10n.plugin_bluesky_invalid_list);
        return;
      }
      await store.lookupActor(handle);
    } catch (e) {
      if (mounted) {
        _snack(blueskyErrorMessage(l10n, e));
      }
    }
  }

  void _snack(String message) {
    ScaffoldMessenger.of(
      context,
    ).showSnackBar(SnackBar(content: Text(message)));
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final store = context.read<BlueskyListsStore>();
    return ScopedBuilder<BlueskyListsStore, BlueskyListsState>(
      store: store,
      distinct: (state) =>
          '${state.selectedUri}\n${blueskyFeedDistinct(state.posts)}\n${state.pinned.length}\n${state.actorLists.length}',
      onLoading: (_) {
        if (store.state.posts.isNotEmpty ||
            store.state.pinned.isNotEmpty ||
            store.state.actorLists.isNotEmpty) {
          return _body(context, l10n, store, store.state);
        }
        return const Center(child: CircularProgressIndicator());
      },
      onError: (context, error) {
        if (store.state.posts.isNotEmpty) {
          return _body(context, l10n, store, store.state);
        }
        return Padding(
          padding: const EdgeInsets.all(24),
          child: FullPageErrorWidget(
            error: error,
            stackTrace: null,
            prefix: blueskyErrorMessage(l10n, error ?? Exception()),
            onRetry: _refresh,
          ),
        );
      },
      onState: (context, state) => _body(context, l10n, store, state),
    );
  }

  Widget _body(
    BuildContext context,
    L10n l10n,
    BlueskyListsStore store,
    BlueskyListsState state,
  ) {
    final chips = [
      for (final list in [...state.pinned, ...state.actorLists])
        _FeedChip(
          uri: list.uri,
          label: list.name.isEmpty
              ? (blueskyRkeyOf(list.uri) ?? list.uri)
              : list.name,
          selected: state.selectedUri == list.uri,
        ),
    ];
    final seen = <String>{};
    final unique = [
      for (final chip in chips)
        if (seen.add(chip.uri)) chip,
    ];

    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (state.hasMore &&
            notification.metrics.pixels >=
                notification.metrics.maxScrollExtent - 400) {
          store.loadMore();
        }
        return false;
      },
      child: RefreshIndicator(
        onRefresh: _refresh,
        child: state.posts.isEmpty
            ? EmptyPane(
                icon: Icons.list_alt_outlined,
                message: l10n.plugin_bluesky_lists_empty,
                scrollController: widget.scrollController,
                onRefresh: _refresh,
                leading: unique.isEmpty
                    ? null
                    : _ChipStrip(
                        chips: unique,
                        onTap: (uri) => store.open(uri),
                      ),
                action: Column(
                  children: [
                    FilledButton.icon(
                      onPressed: _lookup,
                      icon: const Icon(Icons.search),
                      label: Text(l10n.plugin_bluesky_lookup_lists),
                    ),
                    if (state.selectedUri != null) ...[
                      const SizedBox(height: 8),
                      TextButton.icon(
                        onPressed: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => BlueskyImportListScreen(
                              initialList: state.selectedUri,
                            ),
                          ),
                        ),
                        icon: const Icon(Icons.cloud_download_outlined),
                        label: Text(l10n.plugin_bluesky_import_list),
                      ),
                    ],
                  ],
                ),
              )
            : FeedListView(
                controller: pluginInnerScrollController(
                  context,
                  widget.scrollController,
                ),
                padding: pluginFeedPadding(context),
                itemCount: state.posts.length + 2,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _ChipStrip(
                      chips: unique,
                      onTap: (uri) => store.open(uri),
                    );
                  }
                  if (index == 1) {
                    return _selectedHeader(
                      context,
                      title: state.selectedName,
                      pinned:
                          state.selectedUri != null &&
                          state.isPinned(state.selectedUri!),
                      onOpen: _lookup,
                      openLabel: l10n.plugin_bluesky_lookup_lists,
                      onPinToggle: () {
                        final uri = state.selectedUri;
                        if (uri == null) {
                          return;
                        }
                        if (state.isPinned(uri)) {
                          store.unpin(uri);
                        } else {
                          store.pin(
                            BlueskyListInfo(uri: uri, name: state.selectedName),
                          );
                        }
                      },
                      extra: TextButton(
                        onPressed: () => Navigator.push(
                          context,
                          MaterialPageRoute(
                            builder: (_) => BlueskyImportListScreen(
                              initialList: state.selectedUri,
                            ),
                          ),
                        ),
                        child: Text(l10n.plugin_bluesky_import_list),
                      ),
                    );
                  }
                  final post = state.posts[index - 2];
                  return BlueskyPostCard(
                    key: ValueKey('list-${post.uri}'),
                    post: post,
                    showSourceBadge: false,
                  );
                },
              ),
      ),
    );
  }
}

class _FeedChip {
  final String uri;
  final String label;
  final bool selected;

  const _FeedChip({
    required this.uri,
    required this.label,
    required this.selected,
  });
}

class _ChipStrip extends StatelessWidget {
  final List<_FeedChip> chips;
  final ValueChanged<String> onTap;

  const _ChipStrip({required this.chips, required this.onTap});

  @override
  Widget build(BuildContext context) {
    if (chips.isEmpty) {
      return const SizedBox.shrink();
    }
    return SizedBox(
      height: 48,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
        itemCount: chips.length,
        separatorBuilder: (_, _) => const SizedBox(width: 8),
        itemBuilder: (context, index) {
          final chip = chips[index];
          return ChoiceChip(
            label: Text(chip.label, overflow: TextOverflow.ellipsis),
            selected: chip.selected,
            onSelected: (_) => onTap(chip.uri),
          );
        },
      ),
    );
  }
}

Widget _selectedHeader(
  BuildContext context, {
  required String title,
  required bool pinned,
  required VoidCallback onOpen,
  required String openLabel,
  required VoidCallback onPinToggle,
  Widget? extra,
}) {
  final l10n = L10n.of(context);
  return Padding(
    padding: const EdgeInsets.fromLTRB(12, 8, 4, 0),
    child: Row(
      children: [
        Expanded(
          child: Text(title, style: Theme.of(context).textTheme.titleSmall),
        ),
        TextButton(onPressed: onOpen, child: Text(openLabel)),
        IconButton(
          tooltip: pinned ? l10n.unpin : l10n.pin,
          icon: Icon(pinned ? Icons.push_pin : Icons.push_pin_outlined),
          onPressed: onPinToggle,
        ),
        ?extra,
      ],
    ),
  );
}

BlueskyFeedGenerator _selectedGenerator(BlueskyAlgoState state, L10n l10n) {
  final uri = state.selectedUri ?? kBlueskyDiscoverFeedUri;
  for (final feed in [...state.pinned, ...state.created, ...state.popular]) {
    if (feed.uri == uri) {
      return feed;
    }
  }
  return BlueskyFeedGenerator(
    uri: uri,
    displayName: state.selectedName.isEmpty
        ? l10n.plugin_bluesky_discover
        : state.selectedName,
  );
}

Future<String?> _prompt(
  BuildContext context, {
  required String title,
  required String hint,
}) {
  final controller = TextEditingController();
  return showDialog<String>(
    context: context,
    builder: (dialogContext) {
      final l10n = L10n.of(dialogContext);
      return AlertDialog(
        title: Text(title),
        content: TextField(
          controller: controller,
          autofocus: true,
          decoration: InputDecoration(hintText: hint),
          onSubmitted: (value) => Navigator.pop(dialogContext, value.trim()),
        ),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext),
            child: Text(l10n.cancel),
          ),
          TextButton(
            onPressed: () =>
                Navigator.pop(dialogContext, controller.text.trim()),
            child: Text(l10n.ok),
          ),
        ],
      );
    },
  );
}
