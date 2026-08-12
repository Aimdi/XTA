import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_add_screen.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_note_card.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_search_sheet.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

/// Substack Home / Inbox / Notes / Library — account features as local stand-ins.
class SubstackScreen extends StatefulWidget {
  final ScrollController scrollController;

  const SubstackScreen({super.key, required this.scrollController});

  @override
  State<SubstackScreen> createState() => _SubstackScreenState();
}

class _SubstackScreenState extends State<SubstackScreen> {
  var _tab = 0;
  final _notesScrollController = ScrollController();
  final _inboxScrollController = ScrollController();
  final _libraryScrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      final pubs = context.read<SubstackPublicationsStore>();
      final feed = context.read<SubstackFeedStore>();
      final read = context.read<SubstackReadStore>();
      final notes = context.read<SubstackNotesStore>();
      final likes = context.read<SubstackLikesStore>();
      final saved = context.read<SubstackSavedStore>();
      await pubs.load();
      await read.load();
      await likes.load();
      await saved.load();
      feed.syncReadIds(read.state);
      await feed.refresh();
      await notes.refresh();
    });
  }

  @override
  void dispose() {
    _notesScrollController.dispose();
    _inboxScrollController.dispose();
    _libraryScrollController.dispose();
    super.dispose();
  }

  Future<void> _openAdd() async {
    final added = await Navigator.push<bool>(
      context,
      MaterialPageRoute(builder: (_) => const SubstackAddScreen()),
    );
    if (added == true && mounted) {
      await context.read<SubstackFeedStore>().refresh();
    }
  }

  Future<void> _openDiscover() async {
    final feed = context.read<SubstackFeedStore>();
    final notes = context.read<SubstackNotesStore>();
    final followed = await showSubstackSearchSheet(context);
    if (followed == true && mounted) {
      await feed.refresh();
      await notes.refresh();
    }
  }

  void _setFilter(SubstackFeedFilter filter) {
    final read = context.read<SubstackReadStore>().state;
    context.read<SubstackFeedStore>().setFilter(filter, read);
  }

  Future<void> _markAllRead() async {
    final feed = context.read<SubstackFeedStore>();
    final read = context.read<SubstackReadStore>();
    await read.markAllRead(feed.allPosts.map((p) => p.id));
    feed.syncReadIds(read.state);
  }

  @override
  Widget build(BuildContext context) {
    final pubs = context.read<SubstackPublicationsStore>();
    final feed = context.read<SubstackFeedStore>();
    final notes = context.read<SubstackNotesStore>();
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_substack_title),
        actions: [
          if (_tab == 0 || _tab == 1)
            ScopedBuilder<SubstackFeedStore, SubstackFeedSnapshot>(
              store: feed,
              onState: (context, _) {
                final readIds = context.read<SubstackReadStore>().state;
                final hasUnread = feed.allPosts.any(
                  (p) => !readIds.contains(p.id),
                );
                if (!hasUnread) {
                  return const SizedBox.shrink();
                }
                return IconButton(
                  tooltip: l10n.plugin_substack_mark_all_read,
                  icon: const Icon(Icons.done_all),
                  onPressed: _markAllRead,
                );
              },
            ),
          IconButton(
            tooltip: l10n.plugin_substack_discover,
            icon: const Icon(Icons.explore_outlined),
            onPressed: _openDiscover,
          ),
          IconButton(
            tooltip: l10n.plugin_substack_add,
            icon: const Icon(Icons.add),
            onPressed: _openAdd,
          ),
        ],
      ),
      body: Column(
        children: [
          Material(
            color: Theme.of(context).colorScheme.surface,
            child: SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: IntrinsicHeight(
                child: Row(
                  children: [
                    _ShellTab(
                      selected: _tab == 0,
                      icon: Icons.home_outlined,
                      label: l10n.plugin_substack_home,
                      onTap: () => setState(() => _tab = 0),
                    ),
                    _ShellTab(
                      selected: _tab == 1,
                      icon: Icons.inbox_outlined,
                      label: l10n.plugin_substack_inbox,
                      onTap: () => setState(() => _tab = 1),
                    ),
                    _ShellTab(
                      selected: _tab == 2,
                      icon: Icons.notes_outlined,
                      label: l10n.plugin_substack_tab_notes,
                      onTap: () => setState(() => _tab = 2),
                    ),
                    _ShellTab(
                      selected: _tab == 3,
                      icon: Icons.person_outline,
                      label: l10n.plugin_substack_library,
                      onTap: () => setState(() => _tab = 3),
                    ),
                  ],
                ),
              ),
            ),
          ),
          const Divider(height: 1),
          Expanded(
            child: IndexedStack(
              index: _tab,
              children: [
                _PostsPane(
                  scrollController: widget.scrollController,
                  pubs: pubs,
                  feed: feed,
                  onAdd: _openAdd,
                  onDiscover: _openDiscover,
                  onFilter: _setFilter,
                ),
                _InboxPane(
                  scrollController: _inboxScrollController,
                  feed: feed,
                  onAdd: _openAdd,
                ),
                _NotesPane(
                  scrollController: _notesScrollController,
                  notes: notes,
                ),
                _LibraryPane(
                  scrollController: _libraryScrollController,
                  pubs: pubs,
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

class _ShellTab extends StatelessWidget {
  final bool selected;
  final IconData icon;
  final String label;
  final VoidCallback onTap;

  const _ShellTab({
    required this.selected,
    required this.icon,
    required this.label,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final color = selected
        ? theme.colorScheme.primary
        : theme.colorScheme.onSurfaceVariant;
    return InkWell(
      onTap: onTap,
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          border: Border(
            bottom: BorderSide(
              color: selected ? theme.colorScheme.primary : Colors.transparent,
              width: 2,
            ),
          ),
        ),
        child: Row(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(icon, size: 18, color: color),
            const SizedBox(width: 6),
            Text(
              label,
              style: theme.textTheme.titleSmall!.copyWith(
                color: color,
                fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

class _PostsPane extends StatelessWidget {
  final ScrollController scrollController;
  final SubstackPublicationsStore pubs;
  final SubstackFeedStore feed;
  final Future<void> Function() onAdd;
  final Future<void> Function() onDiscover;
  final void Function(SubstackFeedFilter) onFilter;

  const _PostsPane({
    required this.scrollController,
    required this.pubs,
    required this.feed,
    required this.onAdd,
    required this.onDiscover,
    required this.onFilter,
  });

  String? _logoFor(List<SubstackPublication> publications, SubstackPost post) {
    final base = post.publicationBaseUrl.toLowerCase();
    for (final pub in publications) {
      if (pub.baseUrl.toLowerCase() == base ||
          pub.name == post.publicationName) {
        return pub.logoUrl;
      }
    }
    return null;
  }

  @override
  Widget build(BuildContext context) {
    return RefreshIndicator(
      onRefresh: () async {
        await pubs.load();
        await feed.refresh();
      },
      child:
          ScopedBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
            store: pubs,
            onError: (_, error) => FullPageErrorWidget(
              error: error,
              stackTrace: null,
              prefix: L10n.of(context).plugin_substack_load_error,
              onRetry: pubs.load,
            ),
            onLoading: (_) => const Center(child: CircularProgressIndicator()),
            onState: (context, publications) {
              if (publications.isEmpty) {
                return ListView(
                  controller: scrollController,
                  children: [
                    const SizedBox(height: 80),
                    Icon(
                      Icons.newspaper_outlined,
                      size: 48,
                      color: Theme.of(context).colorScheme.outline,
                    ),
                    const SizedBox(height: 16),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 32),
                      child: Text(
                        L10n.of(context).plugin_substack_empty,
                        textAlign: TextAlign.center,
                        style: Theme.of(context).textTheme.titleMedium,
                      ),
                    ),
                    const SizedBox(height: 8),
                    Padding(
                      padding: const EdgeInsets.symmetric(horizontal: 32),
                      child: Text(
                        L10n.of(context).plugin_substack_empty_description,
                        textAlign: TextAlign.center,
                      ),
                    ),
                    const SizedBox(height: 24),
                    Center(
                      child: FilledButton.icon(
                        onPressed: onDiscover,
                        icon: const Icon(Icons.explore_outlined),
                        label: Text(L10n.of(context).plugin_substack_discover),
                      ),
                    ),
                    const SizedBox(height: 12),
                    Center(
                      child: OutlinedButton.icon(
                        onPressed: onAdd,
                        icon: const Icon(Icons.add),
                        label: Text(L10n.of(context).plugin_substack_add),
                      ),
                    ),
                  ],
                );
              }

              return ScopedBuilder<SubstackFeedStore, SubstackFeedSnapshot>(
                store: feed,
                onError: (_, error) => FullPageErrorWidget(
                  error: error,
                  stackTrace: null,
                  prefix: L10n.of(context).plugin_substack_load_error,
                  onRetry: feed.refresh,
                ),
                onLoading: (_) =>
                    const Center(child: CircularProgressIndicator()),
                onState: (context, snapshot) {
                  return ScopedBuilder<SubstackReadStore, Set<String>>(
                    store: context.read<SubstackReadStore>(),
                    onState: (context, readIds) {
                      final children = <Widget>[
                        _PublicationStrip(
                          publications: publications,
                          posts: snapshot.posts,
                          readIds: readIds,
                          onOpen: (pub) {
                            Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) =>
                                    SubstackArchiveScreen(publication: pub),
                              ),
                            );
                          },
                        ),
                        _FilterBar(selected: feed.filter, onSelected: onFilter),
                      ];

                      if (snapshot.failedCount > 0) {
                        children.add(
                          ListTile(
                            leading: Icon(
                              Icons.warning_amber_outlined,
                              color: Theme.of(context).colorScheme.error,
                            ),
                            title: Text(
                              L10n.of(context).plugin_substack_partial_error(
                                snapshot.failedCount,
                              ),
                            ),
                          ),
                        );
                      }

                      if (snapshot.posts.isEmpty) {
                        children.addAll([
                          const SizedBox(height: 48),
                          Icon(
                            Icons.article_outlined,
                            size: 52,
                            color: Theme.of(context).colorScheme.outline,
                          ),
                          const SizedBox(height: 16),
                          Center(
                            child: Text(
                              L10n.of(context).plugin_substack_feed_empty,
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.titleMedium!
                                  .copyWith(fontWeight: FontWeight.w700),
                            ),
                          ),
                          const SizedBox(height: 24),
                          Center(
                            child: FilledButton.icon(
                              onPressed: onDiscover,
                              icon: const Icon(Icons.explore_outlined),
                              label: Text(
                                L10n.of(context).plugin_substack_discover,
                              ),
                            ),
                          ),
                          const SizedBox(height: 12),
                          Center(
                            child: OutlinedButton.icon(
                              onPressed: onAdd,
                              icon: const Icon(Icons.add),
                              label: Text(L10n.of(context).plugin_substack_add),
                            ),
                          ),
                        ]);
                        return ListView(
                          controller: scrollController,
                          children: children,
                        );
                      }

                      return FeedListView(
                        controller: scrollController,
                        padding: const EdgeInsets.only(bottom: 24),
                        itemCount:
                            1 +
                            snapshot.posts.length +
                            (snapshot.canLoadMore ? 1 : 0),
                        itemBuilder: (context, index) {
                          if (index == 0) {
                            return Column(
                              mainAxisSize: MainAxisSize.min,
                              children: children,
                            );
                          }
                          final postIndex = index - 1;
                          if (postIndex < snapshot.posts.length) {
                            final post = snapshot.posts[postIndex];
                            return SubstackPostCard(
                              key: ValueKey(post.id),
                              post: post,
                              showSourceBadge: false,
                              logoUrl: _logoFor(publications, post),
                            );
                          }
                          return Padding(
                            padding: const EdgeInsets.all(16),
                            child: Center(
                              child: OutlinedButton(
                                onPressed: feed.loadMore,
                                child: Text(
                                  L10n.of(context).plugin_substack_load_more,
                                ),
                              ),
                            ),
                          );
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
  final SubstackFeedFilter selected;
  final void Function(SubstackFeedFilter) onSelected;

  const _FilterBar({required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final labels = {
      SubstackFeedFilter.all: l10n.plugin_substack_filter_all,
      SubstackFeedFilter.unread: l10n.plugin_substack_filter_unread,
      SubstackFeedFilter.free: l10n.plugin_substack_filter_free,
      SubstackFeedFilter.podcast: l10n.plugin_substack_filter_podcast,
    };

    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(12, 4, 12, 8),
      child: Row(
        children: [
          for (final filter in SubstackFeedFilter.values)
            Padding(
              padding: const EdgeInsets.only(right: 8),
              child: ChoiceChip(
                label: Text(labels[filter]!),
                selected: selected == filter,
                onSelected: (_) => onSelected(filter),
              ),
            ),
        ],
      ),
    );
  }
}

class _InboxPane extends StatelessWidget {
  final ScrollController scrollController;
  final SubstackFeedStore feed;
  final Future<void> Function() onAdd;

  const _InboxPane({
    required this.scrollController,
    required this.feed,
    required this.onAdd,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: feed.refresh,
      child: ScopedBuilder<SubstackFeedStore, SubstackFeedSnapshot>(
        store: feed,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.plugin_substack_load_error,
          onRetry: feed.refresh,
        ),
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (context, _) {
          return ScopedBuilder<SubstackReadStore, Set<String>>(
            store: context.read<SubstackReadStore>(),
            onState: (context, readIds) {
              return ScopedBuilder<
                SubstackPublicationsStore,
                List<SubstackPublication>
              >(
                store: context.read<SubstackPublicationsStore>(),
                onState: (context, publications) {
                  if (publications.isEmpty) {
                    return ListView(
                      controller: scrollController,
                      children: [
                        const SizedBox(height: 80),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 32),
                          child: Text(
                            l10n.plugin_substack_empty,
                            textAlign: TextAlign.center,
                          ),
                        ),
                        const SizedBox(height: 24),
                        Center(
                          child: FilledButton.icon(
                            onPressed: onAdd,
                            icon: const Icon(Icons.add),
                            label: Text(l10n.plugin_substack_add),
                          ),
                        ),
                      ],
                    );
                  }

                  final unread = feed.allPosts
                      .where((p) => !readIds.contains(p.id))
                      .toList();
                  if (unread.isEmpty) {
                    return ListView(
                      controller: scrollController,
                      children: [
                        const SizedBox(height: 48),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 32),
                          child: Text(
                            l10n.plugin_substack_inbox_empty,
                            textAlign: TextAlign.center,
                          ),
                        ),
                        const SizedBox(height: 8),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 32),
                          child: Text(
                            l10n.plugin_substack_inbox_intro,
                            textAlign: TextAlign.center,
                          ),
                        ),
                      ],
                    );
                  }

                  return ListView.builder(
                    controller: scrollController,
                    padding: const EdgeInsets.only(bottom: 24, top: 8),
                    itemCount: unread.length + 1,
                    itemBuilder: (context, index) {
                      if (index == 0) {
                        return Padding(
                          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                          child: Text(
                            l10n.plugin_substack_inbox_intro,
                            style: Theme.of(context).textTheme.bodySmall,
                          ),
                        );
                      }
                      final post = unread[index - 1];
                      return SubstackPostCard(
                        post: post,
                        showSourceBadge: false,
                        logoUrl: _logoForPub(publications, post),
                      );
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

String? _logoForPub(List<SubstackPublication> publications, SubstackPost post) {
  final base = post.publicationBaseUrl.toLowerCase();
  for (final pub in publications) {
    if (pub.baseUrl.toLowerCase() == base || pub.name == post.publicationName) {
      return pub.logoUrl;
    }
  }
  return null;
}

class _LibraryPane extends StatefulWidget {
  final ScrollController scrollController;
  final SubstackPublicationsStore pubs;
  final Future<void> Function() onAdd;

  const _LibraryPane({
    required this.scrollController,
    required this.pubs,
    required this.onAdd,
  });

  @override
  State<_LibraryPane> createState() => _LibraryPaneState();
}

class _LibraryPaneState extends State<_LibraryPane> {
  var _section = 0;
  final _query = TextEditingController();

  @override
  void dispose() {
    _query.dispose();
    super.dispose();
  }

  bool _matches(String query, SubstackPost post) {
    if (query.isEmpty) return true;
    final q = query.toLowerCase();
    return post.title.toLowerCase().contains(q) ||
        post.publicationName.toLowerCase().contains(q) ||
        (post.excerpt?.toLowerCase().contains(q) ?? false);
  }

  bool _matchesPub(String query, SubstackPublication pub) {
    if (query.isEmpty) return true;
    final q = query.toLowerCase();
    return pub.name.toLowerCase().contains(q) ||
        pub.baseUrl.toLowerCase().contains(q);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final likes = context.read<SubstackLikesStore>();
    final saved = context.read<SubstackSavedStore>();
    final sections = [
      l10n.plugin_substack_library_following,
      l10n.plugin_substack_library_saved,
      l10n.plugin_substack_library_liked,
    ];

    return Column(
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 0),
          child: TextField(
            controller: _query,
            onChanged: (_) => setState(() {}),
            decoration: InputDecoration(
              hintText: l10n.plugin_substack_library_search,
              prefixIcon: const Icon(Icons.search),
              isDense: true,
              border: const OutlineInputBorder(),
            ),
          ),
        ),
        SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
          child: Row(
            children: [
              for (var i = 0; i < sections.length; i++)
                Padding(
                  padding: const EdgeInsets.only(right: 8),
                  child: ChoiceChip(
                    label: Text(sections[i]),
                    selected: _section == i,
                    onSelected: (_) => setState(() => _section = i),
                  ),
                ),
            ],
          ),
        ),
        Expanded(
          child: _section == 0
              ? ScopedBuilder<
                  SubstackPublicationsStore,
                  List<SubstackPublication>
                >(
                  store: widget.pubs,
                  onState: (context, publications) {
                    final q = _query.text.trim();
                    final visible = publications
                        .where((p) => _matchesPub(q, p))
                        .toList();
                    if (visible.isEmpty) {
                      return ListView(
                        controller: widget.scrollController,
                        children: [
                          const SizedBox(height: 48),
                          Padding(
                            padding: const EdgeInsets.symmetric(horizontal: 32),
                            child: Text(
                              q.isEmpty
                                  ? l10n.plugin_substack_empty
                                  : l10n.plugin_substack_library_search_empty,
                              textAlign: TextAlign.center,
                            ),
                          ),
                          if (q.isEmpty) ...[
                            const SizedBox(height: 24),
                            Center(
                              child: FilledButton.icon(
                                onPressed: widget.onAdd,
                                icon: const Icon(Icons.add),
                                label: Text(l10n.plugin_substack_add),
                              ),
                            ),
                          ],
                        ],
                      );
                    }
                    return ListView.separated(
                      controller: widget.scrollController,
                      padding: const EdgeInsets.only(bottom: 24),
                      itemCount: visible.length,
                      separatorBuilder: (_, _) => const Divider(height: 1),
                      itemBuilder: (context, index) {
                        final pub = visible[index];
                        final pinned = widget.pubs.isPinned(pub.id);
                        return ListTile(
                          leading: pub.logoUrl == null
                              ? const CircleAvatar(child: Icon(Icons.newspaper))
                              : ClipOval(
                                  child: ExtendedImage.network(
                                    pub.logoUrl!,
                                    width: 56,
                                    height: 56,
                                    fit: BoxFit.cover,
                                    cache: true,
                                    cacheWidth:
                                        (56 *
                                                MediaQuery.devicePixelRatioOf(
                                                  context,
                                                ))
                                            .ceil(),
                                  ),
                                ),
                          title: Text(pub.name),
                          subtitle: Text(
                            Uri.tryParse(pub.baseUrl)?.host ?? pub.baseUrl,
                          ),
                          trailing: Row(
                            mainAxisSize: MainAxisSize.min,
                            children: [
                              IconButton(
                                tooltip: pinned ? l10n.unpin : l10n.pin,
                                onPressed: () =>
                                    widget.pubs.togglePinned(pub.id),
                                icon: Icon(
                                  pinned
                                      ? Icons.push_pin
                                      : Icons.push_pin_outlined,
                                  color: pinned
                                      ? Theme.of(context).colorScheme.primary
                                      : Theme.of(context).hintColor,
                                ),
                              ),
                              PopupMenuButton<String>(
                                onSelected: (value) async {
                                  if (value == 'group') {
                                    await addSubstackPublicationToGroup(
                                      context,
                                      pub,
                                    );
                                    return;
                                  }
                                  if (value != 'unfollow') return;
                                  final subscriptions = context
                                      .read<SubscriptionsModel>();
                                  final ok = await showDialog<bool>(
                                    context: context,
                                    builder: (context) => AlertDialog(
                                      title: Text(
                                        l10n.plugin_substack_unfollow,
                                      ),
                                      content: Text(pub.name),
                                      actions: [
                                        TextButton(
                                          onPressed: () =>
                                              Navigator.pop(context, false),
                                          child: Text(l10n.cancel),
                                        ),
                                        FilledButton(
                                          onPressed: () =>
                                              Navigator.pop(context, true),
                                          child: Text(
                                            l10n.plugin_substack_unfollow,
                                          ),
                                        ),
                                      ],
                                    ),
                                  );
                                  if (ok != true) return;
                                  await widget.pubs.remove(pub.id);
                                  await subscriptions.reloadSubscriptions();
                                },
                                itemBuilder: (context) => [
                                  PopupMenuItem(
                                    value: 'group',
                                    child: Text(l10n.add_to_group),
                                  ),
                                  PopupMenuItem(
                                    value: 'unfollow',
                                    child: Text(l10n.plugin_substack_unfollow),
                                  ),
                                ],
                              ),
                            ],
                          ),
                          onTap: () => Navigator.push(
                            context,
                            MaterialPageRoute(
                              builder: (_) =>
                                  SubstackArchiveScreen(publication: pub),
                            ),
                          ),
                        );
                      },
                    );
                  },
                )
              : ScopedBuilder<SubstackSavedStore, List<SubstackPost>>(
                  store: saved,
                  onState: (context, savedPosts) {
                    return ScopedBuilder<
                      SubstackLikesStore,
                      List<SubstackPost>
                    >(
                      store: likes,
                      onState: (context, likedPosts) {
                        final source = _section == 1 ? savedPosts : likedPosts;
                        final q = _query.text.trim();
                        final visible = source
                            .where((p) => _matches(q, p))
                            .toList();
                        if (visible.isEmpty) {
                          return ListView(
                            controller: widget.scrollController,
                            children: [
                              const SizedBox(height: 48),
                              Padding(
                                padding: const EdgeInsets.symmetric(
                                  horizontal: 32,
                                ),
                                child: Text(
                                  q.isEmpty
                                      ? (_section == 1
                                            ? l10n.plugin_substack_saved_empty
                                            : l10n.plugin_substack_liked_empty)
                                      : l10n.plugin_substack_library_search_empty,
                                  textAlign: TextAlign.center,
                                ),
                              ),
                            ],
                          );
                        }
                        return ListView.builder(
                          controller: widget.scrollController,
                          padding: const EdgeInsets.only(bottom: 24),
                          itemCount: visible.length,
                          itemBuilder: (context, index) => SubstackPostCard(
                            post: visible[index],
                            showSourceBadge: false,
                          ),
                        );
                      },
                    );
                  },
                ),
        ),
      ],
    );
  }
}

class _NotesPane extends StatelessWidget {
  final ScrollController scrollController;
  final SubstackNotesStore notes;

  const _NotesPane({required this.scrollController, required this.notes});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: notes.refresh,
      child: ScopedBuilder<SubstackNotesStore, SubstackNotesPage>(
        store: notes,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.plugin_substack_load_error,
          onRetry: notes.refresh,
        ),
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (context, page) {
          if (page.notes.isEmpty) {
            return ListView(
              controller: scrollController,
              children: [
                const SizedBox(height: 48),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: Text(
                    l10n.plugin_substack_notes_empty,
                    textAlign: TextAlign.center,
                  ),
                ),
                const SizedBox(height: 8),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 32),
                  child: Text(
                    l10n.plugin_substack_notes_intro,
                    textAlign: TextAlign.center,
                  ),
                ),
              ],
            );
          }

          return ListView.builder(
            controller: scrollController,
            padding: const EdgeInsets.only(bottom: 24, top: 8),
            itemCount: page.notes.length + 2,
            itemBuilder: (context, index) {
              if (index == 0) {
                return Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                  child: Text(
                    l10n.plugin_substack_notes_intro,
                    style: Theme.of(context).textTheme.bodySmall,
                  ),
                );
              }
              final noteIndex = index - 1;
              if (noteIndex < page.notes.length) {
                return SubstackNoteCard(note: page.notes[noteIndex]);
              }
              if (page.nextCursor == null || page.nextCursor!.isEmpty) {
                return const SizedBox.shrink();
              }
              return Padding(
                padding: const EdgeInsets.all(16),
                child: Center(
                  child: OutlinedButton(
                    onPressed: notes.loadMore,
                    child: Text(l10n.plugin_substack_load_more),
                  ),
                ),
              );
            },
          );
        },
      ),
    );
  }
}

/// Story-style circular publications with an unread dot when that pub has mail.
class _PublicationStrip extends StatelessWidget {
  final List<SubstackPublication> publications;
  final List<SubstackPost> posts;
  final Set<String> readIds;
  final void Function(SubstackPublication publication) onOpen;

  const _PublicationStrip({
    required this.publications,
    required this.posts,
    required this.readIds,
    required this.onOpen,
  });

  bool _hasUnread(SubstackPublication pub) {
    final base = pub.baseUrl.toLowerCase();
    return posts.any(
      (p) =>
          !readIds.contains(p.id) && p.publicationBaseUrl.toLowerCase() == base,
    );
  }

  Future<void> _confirmUnfollow(
    BuildContext context,
    SubstackPublication pub,
  ) async {
    final l10n = L10n.of(context);
    final pubs = context.read<SubstackPublicationsStore>();
    final subscriptions = context.read<SubscriptionsModel>();
    final feed = context.read<SubstackFeedStore>();
    final ok = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.plugin_substack_unfollow),
        content: Text(pub.name),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.cancel),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.plugin_substack_unfollow),
          ),
        ],
      ),
    );
    if (ok != true) return;
    await pubs.remove(pub.id);
    await subscriptions.reloadSubscriptions();
    await feed.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      height: 96,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        padding: const EdgeInsets.fromLTRB(12, 10, 12, 4),
        itemCount: publications.length,
        separatorBuilder: (_, _) => const SizedBox(width: 14),
        itemBuilder: (context, index) {
          final pub = publications[index];
          final unread = _hasUnread(pub);
          final pinned = context.read<SubstackPublicationsStore>().isPinned(
            pub.id,
          );
          return InkWell(
            onTap: () => onOpen(pub),
            onLongPress: () => _confirmUnfollow(context, pub),
            borderRadius: BorderRadius.circular(12),
            child: SizedBox(
              width: 72,
              child: Column(
                children: [
                  Stack(
                    clipBehavior: Clip.none,
                    children: [
                      Container(
                        width: 56,
                        height: 56,
                        padding: const EdgeInsets.all(2),
                        decoration: BoxDecoration(
                          shape: BoxShape.circle,
                          border: Border.all(
                            color: unread
                                ? theme.colorScheme.primary
                                : theme.colorScheme.outlineVariant,
                            width: unread ? 2.5 : 1,
                          ),
                        ),
                        child: ClipOval(
                          child: pub.logoUrl == null
                              ? ColoredBox(
                                  color:
                                      theme.colorScheme.surfaceContainerHighest,
                                  child: Icon(
                                    Icons.newspaper,
                                    color: theme.colorScheme.onSurfaceVariant,
                                  ),
                                )
                              : ExtendedImage.network(
                                  pub.logoUrl!,
                                  fit: BoxFit.cover,
                                  cacheWidth:
                                      (56 *
                                              MediaQuery.devicePixelRatioOf(
                                                context,
                                              ))
                                          .ceil(),
                                ),
                        ),
                      ),
                      if (unread)
                        Positioned(
                          right: 2,
                          top: 2,
                          child: Container(
                            width: 12,
                            height: 12,
                            decoration: BoxDecoration(
                              color: theme.colorScheme.primary,
                              shape: BoxShape.circle,
                              border: Border.all(
                                color: theme.colorScheme.surface,
                                width: 2,
                              ),
                            ),
                          ),
                        ),
                      if (pinned)
                        Positioned(
                          left: 0,
                          bottom: 0,
                          child: Container(
                            padding: const EdgeInsets.all(2),
                            decoration: BoxDecoration(
                              color: theme.colorScheme.surface,
                              shape: BoxShape.circle,
                            ),
                            child: Icon(
                              Icons.push_pin,
                              size: 12,
                              color: theme.colorScheme.primary,
                            ),
                          ),
                        ),
                    ],
                  ),
                  const SizedBox(height: 6),
                  Text(
                    pub.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    textAlign: TextAlign.center,
                    style: theme.textTheme.labelSmall!.copyWith(
                      fontWeight: unread ? FontWeight.w700 : FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}
