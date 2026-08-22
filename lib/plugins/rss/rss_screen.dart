import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/rss/rss_add_screen.dart';
import 'package:xta/plugins/rss/rss_card.dart';
import 'package:xta/plugins/rss/rss_feed_screen.dart';
import 'package:xta/plugins/rss/rss_group.dart';
import 'package:xta/plugins/rss/rss_models.dart';
import 'package:xta/plugins/rss/rss_plugin.dart';
import 'package:xta/plugins/rss/rss_settings.dart';
import 'package:xta/plugins/rss/rss_store.dart';
import 'package:xta/ui/empty_pane.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

class RssScreen extends StatefulWidget {
  final ScrollController scrollController;

  const RssScreen({super.key, required this.scrollController});

  @override
  State<RssScreen> createState() => _RssScreenState();
}

class _RssScreenState extends State<RssScreen> {
  var _tab = 0;
  final _feedsScroll = ScrollController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final feeds = context.read<RssFeedsStore>();
      final read = context.read<RssReadStore>();
      final tags = context.read<RssTagsStore>();
      final timeline = context.read<RssTimelineStore>();
      if (feeds.state.isEmpty) await feeds.load();
      if (!mounted) return;
      if (read.state.isEmpty) await read.load();
      if (!mounted) return;
      if (tags.state.isEmpty) await tags.load();
      if (!mounted) return;
      timeline.syncReadIds(read.state);
      timeline.syncTags(tags.state);
      await timeline.refresh();
    });
  }

  @override
  void dispose() {
    _feedsScroll.dispose();
    super.dispose();
  }

  Future<void> _openAdd() async {
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => const RssAddScreen()),
    );
    if (!mounted) return;
    await context.read<RssTimelineStore>().refresh(force: true);
  }

  void _setFilter(RssFeedFilter filter) {
    final read = context.read<RssReadStore>().state;
    context.read<RssTimelineStore>().setFilter(filter, read);
  }

  Future<void> _markAllRead() async {
    final timeline = context.read<RssTimelineStore>();
    final read = context.read<RssReadStore>();
    await read.markAllRead(timeline.allItems.map((e) => e.id));
    timeline.syncReadIds(read.state);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final feeds = context.read<RssFeedsStore>();
    final timeline = context.read<RssTimelineStore>();

    return Scaffold(
      primary: !PluginEmbedded.maybeOf(context),
      body: Column(
        children: [
          PluginHomeChrome(
            accent: rssBrand,
            tabs: [
              PluginHomeTab(
                selected: _tab == 0,
                icon: Icons.home_outlined,
                label: l10n.plugin_rss_home,
                onTap: () => setState(() => _tab = 0),
              ),
              PluginHomeTab(
                selected: _tab == 1,
                icon: Icons.rss_feed,
                label: l10n.plugin_rss_feeds,
                onTap: () => setState(() => _tab = 1),
              ),
            ],
            actions: [
              if (_tab == 0)
                ScopedBuilder<RssTimelineStore, RssFeedSnapshot>(
                  store: timeline,
                  onState: (context, _) {
                    final readIds = context.read<RssReadStore>().state;
                    final hasUnread = timeline.allItems.any(
                      (item) => !readIds.contains(item.id),
                    );
                    if (!hasUnread) return const SizedBox.shrink();
                    return IconButton(
                      tooltip: l10n.plugin_rss_mark_all_read,
                      icon: const Icon(Icons.done_all),
                      onPressed: _markAllRead,
                    );
                  },
                ),
              IconButton(
                tooltip: l10n.settings,
                icon: const Icon(Icons.settings_outlined),
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => const RssSettingsScreen()),
                ),
              ),
              IconButton(
                tooltip: l10n.plugin_rss_add,
                icon: const Icon(Icons.add),
                onPressed: _openAdd,
              ),
            ],
          ),
          const Divider(height: 1),
          Expanded(
            child: PluginLazyTabs(
              index: _tab,
              children: [
                (_) => _HomePane(
                  scrollController: widget.scrollController,
                  feeds: feeds,
                  timeline: timeline,
                  onAdd: _openAdd,
                  onFilter: _setFilter,
                ),
                (_) => _FeedsPane(
                  scrollController: _feedsScroll,
                  feeds: feeds,
                  onAdd: _openAdd,
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }
}

class _HomePane extends StatelessWidget {
  final ScrollController scrollController;
  final RssFeedsStore feeds;
  final RssTimelineStore timeline;
  final Future<void> Function() onAdd;
  final void Function(RssFeedFilter) onFilter;

  const _HomePane({
    required this.scrollController,
    required this.feeds,
    required this.timeline,
    required this.onAdd,
    required this.onFilter,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: () async {
        await feeds.load();
        await timeline.refresh(force: true);
      },
      child: ScopedBuilder<RssFeedsStore, List<RssFeed>>(
        store: feeds,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.plugin_rss_load_error,
          onRetry: feeds.load,
        ),
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (context, followed) {
          if (followed.isEmpty) {
            return EmptyPane(
              icon: Icons.rss_feed,
              message: l10n.plugin_rss_empty,
              scrollController: scrollController,
              action: FilledButton.icon(
                onPressed: onAdd,
                icon: const Icon(Icons.add),
                label: Text(l10n.plugin_rss_add),
              ),
            );
          }
          return ScopedBuilder<RssTimelineStore, RssFeedSnapshot>(
            store: timeline,
            onError: (_, error) => FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: l10n.plugin_rss_load_error,
              onRetry: () => timeline.refresh(force: true),
            ),
            onLoading: (_) => const Center(child: CircularProgressIndicator()),
            onState: (context, snapshot) {
              return ScopedBuilder<RssTagsStore, Map<String, List<String>>>(
                store: context.read<RssTagsStore>(),
                onState: (context, tags) {
                  return FeedListView(
                    controller: pluginInnerScrollController(
                      context,
                      scrollController,
                    ),
                    itemCount: snapshot.items.length + 1,
                    itemBuilder: (context, index) {
                      if (index == 0) {
                        return _FilterBar(
                          selected: timeline.filter,
                          selectedTag: timeline.tag,
                          tags: context.read<RssTagsStore>().allTags,
                          onFilter: onFilter,
                          onTag: timeline.setTag,
                        );
                      }
                      return RssItemCard(item: snapshot.items[index - 1]);
                    },
                  );
                },
              );
            },
          );
        },
      ),
    );
  }
}

class _FilterBar extends StatelessWidget {
  final RssFeedFilter selected;
  final String? selectedTag;
  final List<String> tags;
  final void Function(RssFeedFilter) onFilter;
  final void Function(String?) onTag;

  const _FilterBar({
    required this.selected,
    required this.selectedTag,
    required this.tags,
    required this.onFilter,
    required this.onTag,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(12, 8, 12, 6),
      child: Row(
        children: [
          _chip(
            context,
            label: l10n.plugin_rss_all,
            selected: selected == RssFeedFilter.all && selectedTag == null,
            onTap: () {
              onTag(null);
              onFilter(RssFeedFilter.all);
            },
          ),
          _chip(
            context,
            label: l10n.plugin_rss_unread,
            selected: selected == RssFeedFilter.unread,
            onTap: () {
              onTag(null);
              onFilter(RssFeedFilter.unread);
            },
          ),
          for (final tag in tags)
            _chip(
              context,
              label: tag,
              selected: selectedTag == tag,
              onTap: () {
                onFilter(RssFeedFilter.all);
                onTag(selectedTag == tag ? null : tag);
              },
            ),
        ],
      ),
    );
  }

  Widget _chip(
    BuildContext context, {
    required String label,
    required bool selected,
    required VoidCallback onTap,
  }) {
    return Padding(
      padding: const EdgeInsets.only(right: 6),
      child: FilterChip(
        label: Text(label),
        selected: selected,
        showCheckmark: false,
        visualDensity: VisualDensity.compact,
        materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
        onSelected: (_) => onTap(),
      ),
    );
  }
}

class _FeedsPane extends StatelessWidget {
  final ScrollController scrollController;
  final RssFeedsStore feeds;
  final Future<void> Function() onAdd;

  const _FeedsPane({
    required this.scrollController,
    required this.feeds,
    required this.onAdd,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ScopedBuilder<RssFeedsStore, List<RssFeed>>(
      store: feeds,
      onState: (context, followed) {
        if (followed.isEmpty) {
          return EmptyPane(
            icon: Icons.rss_feed,
            message: l10n.plugin_rss_following_empty,
            scrollController: scrollController,
            action: FilledButton.icon(
              onPressed: onAdd,
              icon: const Icon(Icons.add),
              label: Text(l10n.plugin_rss_add),
            ),
          );
        }
        return ListView.builder(
          controller: pluginInnerScrollController(context, scrollController),
          itemCount: followed.length,
          itemBuilder: (context, index) {
            final feed = followed[index];
            return ListTile(
              leading: const CircleAvatar(child: Icon(Icons.rss_feed)),
              title: Text(feed.name),
              subtitle: Text(
                Uri.tryParse(feed.siteUrl ?? feed.feedUrl)?.host ??
                    feed.feedUrl,
              ),
              onTap: () => Navigator.push(
                context,
                MaterialPageRoute(builder: (_) => RssFeedScreen(feed: feed)),
              ),
              trailing: PopupMenuButton<String>(
                onSelected: (value) async {
                  if (value == 'tag') {
                    await _editTag(context, feed);
                  } else if (value == 'group') {
                    await addRssFeedToGroup(context, feed);
                  } else if (value == 'unfollow') {
                    await feeds.remove(feed.id);
                  }
                },
                itemBuilder: (context) => [
                  PopupMenuItem(
                    value: 'tag',
                    child: Text(l10n.plugin_rss_set_tag),
                  ),
                  PopupMenuItem(
                    value: 'group',
                    child: Text(l10n.plugin_rss_add_to_group),
                  ),
                  PopupMenuItem(
                    value: 'unfollow',
                    child: Text(l10n.plugin_rss_unfollow),
                  ),
                ],
              ),
            );
          },
        );
      },
    );
  }

  Future<void> _editTag(BuildContext context, RssFeed feed) async {
    final tags = context.read<RssTagsStore>();
    final current = tags.tagsFor(feed.id).join(', ');
    final next = await showDialog<String>(
      context: context,
      builder: (_) => _RssTagDialog(initial: current),
    );
    if (next == null || !context.mounted) return;
    final parts = [
      for (final part in next.split(RegExp(r'[,/]')))
        if (part.trim().isNotEmpty) part.trim(),
    ];
    await tags.setTags(feed.id, parts);
    if (context.mounted) {
      context.read<RssTimelineStore>().syncTags(tags.state);
    }
  }
}

/// Owns the field so cancel does not dispose it while the route is animating.
class _RssTagDialog extends StatefulWidget {
  final String initial;

  const _RssTagDialog({required this.initial});

  @override
  State<_RssTagDialog> createState() => _RssTagDialogState();
}

class _RssTagDialogState extends State<_RssTagDialog> {
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initial);
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return AlertDialog(
      title: Text(l10n.plugin_rss_set_tag),
      content: TextField(
        controller: _controller,
        decoration: InputDecoration(hintText: l10n.plugin_rss_tag_hint),
        autofocus: true,
      ),
      actions: [
        TextButton(
          onPressed: () => Navigator.pop(context),
          child: Text(l10n.cancel),
        ),
        TextButton(
          onPressed: () => Navigator.pop(context, _controller.text),
          child: Text(l10n.plugin_rss_tag),
        ),
      ],
    );
  }
}
