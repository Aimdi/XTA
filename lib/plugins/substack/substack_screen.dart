import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/plugin_marks.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/plugins/plugin_session.dart';
import 'package:xta/plugins/plugin_lazy_tabs.dart';
import 'package:xta/plugins/substack/substack_add_screen.dart';
import 'package:xta/plugins/substack/substack_plugin.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_home_controls.dart';
import 'package:xta/plugins/substack/substack_note_card.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_reading_toolbar.dart';
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
  late final PluginSessionLease _session;
  late final PluginViewStore<int> _view;
  late final SubstackHomeControlsStore _controls;
  int get _tab => _view.state;
  final _notesScrollController = ScrollController();
  final _inboxScrollController = ScrollController();
  final _libraryScrollController = ScrollController();

  @override
  void initState() {
    super.initState();
    _session = PluginSessionLease(context, 'substack');
    _view = _session.obtain('view', () => PluginViewStore<int>(0));
    _controls = _session.obtain('controls', SubstackHomeControlsStore.new);
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      final pubs = context.read<SubstackPublicationsStore>();
      final feed = context.read<SubstackFeedStore>();
      final read = context.read<SubstackReadStore>();
      final likes = context.read<SubstackLikesStore>();
      final saved = context.read<SubstackSavedStore>();
      if (pubs.state.isEmpty) await pubs.load();
      if (!mounted) return;
      if (read.state.isEmpty) await read.load();
      if (!mounted) return;
      if (likes.state.isEmpty) await likes.load();
      if (!mounted) return;
      if (saved.state.isEmpty) await saved.load();
      if (!mounted) return;
      feed.syncReadIds(read.state);
      if (_tab == 2) {
        _selectTab(2);
      } else if (_tab < 2 && feed.allPosts.isEmpty) {
        await feed.refresh(force: true);
      }
    });
  }

  @override
  void dispose() {
    _session.dispose();
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
      await feed.refresh(force: true);
      await notes.refresh();
    }
  }

  void _selectTab(int tab) {
    _view.select(tab);
    if (tab < 2) context.read<SubstackFeedStore>().refresh(force: false);
    if (tab == 2 && context.read<SubstackNotesStore>().state.notes.isEmpty) {
      context.read<SubstackNotesStore>().refresh();
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
    _view.restore(context, 'substack');

    return Provider<SubstackHomeControlsStore>.value(
      value: _controls,
      child: ScopedBuilder<SubstackHomeControlsStore, SubstackHomeOptions>(
        store: _controls,
        onState: (context, _) => Scaffold(
          primary: !PluginEmbedded.maybeOf(context),
          body: ScopedBuilder<PluginViewStore<int>, int>(
            store: _view,
            onState: (_, _) => Column(
              children: [
                PluginHomeChrome(
                  title: l10n.plugin_substack_title,
                  mark: pluginMark(SubstackPlugin(), size: 24),
                  accent: SubstackPlugin().brandColor,
                  tabs: [
                    PluginHomeTab(
                      selected: _tab == 0,
                      icon: Icons.home_outlined,
                      label: l10n.plugin_substack_home,
                      onTap: () => _selectTab(0),
                    ),
                    PluginHomeTab(
                      selected: _tab == 1,
                      icon: Icons.inbox_outlined,
                      label: l10n.plugin_substack_inbox,
                      onTap: () => _selectTab(1),
                    ),
                    PluginHomeTab(
                      selected: _tab == 2,
                      icon: Icons.notes_outlined,
                      label: l10n.plugin_substack_tab_notes,
                      onTap: () => _selectTab(2),
                    ),
                    PluginHomeTab(
                      selected: _tab == 3,
                      icon: Icons.person_outline,
                      label: l10n.plugin_substack_library,
                      onTap: () => _selectTab(3),
                    ),
                  ],
                  actions: [
                    PluginHomeMenu(
                      onSelected: (value) {
                        if (value == 'discover') {
                          _openDiscover();
                        } else if (value == 'add') {
                          _openAdd();
                        } else if (value == 'read') {
                          _markAllRead();
                        }
                      },
                      itemBuilder: (_) => [
                        PopupMenuItem(
                          value: 'discover',
                          child: Text(l10n.plugin_substack_discover),
                        ),
                        PopupMenuItem(
                          value: 'add',
                          child: Text(l10n.plugin_substack_add),
                        ),
                        if (_tab < 2 &&
                            feed.allPosts.any(
                              (post) => !context
                                  .read<SubstackReadStore>()
                                  .state
                                  .contains(post.id),
                            ))
                          PopupMenuItem(
                            value: 'read',
                            child: Text(l10n.plugin_substack_mark_all_read),
                          ),
                      ],
                    ),
                  ],
                ),
                const Divider(height: 1),
                Expanded(
                  child: PluginLazyTabs(
                    index: _tab,
                    children: [
                      (_) => _PostsPane(
                        scrollController: widget.scrollController,
                        pubs: pubs,
                        feed: feed,
                        onAdd: _openAdd,
                        onDiscover: _openDiscover,
                        onFilter: _setFilter,
                      ),
                      (_) => _InboxPane(
                        scrollController: _inboxScrollController,
                        feed: feed,
                        onAdd: _openAdd,
                      ),
                      (_) => _NotesPane(
                        scrollController: _notesScrollController,
                        notes: notes,
                      ),
                      (_) => _LibraryPane(
                        scrollController: _libraryScrollController,
                        pubs: pubs,
                        onAdd: _openAdd,
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
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
        await feed.refresh(force: true);
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
            onLoading: (_) => const PluginFeedSkeleton(),
            onState: (context, publications) {
              if (publications.isEmpty) {
                return ListView(
                  controller: pluginInnerScrollController(
                    context,
                    scrollController,
                  ),
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
                  onRetry: () => feed.refresh(force: true),
                ),
                onLoading: (_) =>
                    const Center(child: CircularProgressIndicator()),
                onState: (context, snapshot) {
                  return ScopedBuilder<SubstackReadStore, Set<String>>(
                    store: context.read<SubstackReadStore>(),
                    onState: (context, readIds) {
                      final controls = context
                          .read<SubstackHomeControlsStore>();
                      final options = controls.options('home');
                      final visible = filterSubstackLoaded(
                        feed.allPosts
                            .where(
                              (post) =>
                                  publications.any(
                                    (pub) =>
                                        pub.baseUrl == post.publicationBaseUrl,
                                  ) &&
                                  postMatchesSubstackFilter(
                                    post,
                                    feed.filter,
                                    readIds,
                                  ),
                            )
                            .toList(),
                        options,
                      );
                      final children = <Widget>[
                        SubstackReadingToolbar(
                          slot: 'home',
                          feed: feed,
                          publications: publications,
                          readIds: readIds,
                          onFilter: onFilter,
                        ),
                      ];

                      if (visible.isEmpty) {
                        final filtered =
                            feed.filter != SubstackFeedFilter.all ||
                            options.query.trim().isNotEmpty;
                        children.addAll([
                          const SizedBox(height: 40),
                          Icon(
                            filtered
                                ? Icons.filter_list
                                : Icons.article_outlined,
                            size: 40,
                            color: Theme.of(context).colorScheme.outline,
                          ),
                          Padding(
                            padding: const EdgeInsets.all(24),
                            child: Text(
                              filtered
                                  ? L10n.of(context).plugin_reader_empty_filter
                                  : L10n.of(context).plugin_substack_feed_empty,
                              textAlign: TextAlign.center,
                              style: Theme.of(context).textTheme.titleMedium,
                            ),
                          ),
                          Center(
                            child: TextButton.icon(
                              onPressed: filtered
                                  ? () {
                                      onFilter(SubstackFeedFilter.all);
                                      controls.configure(
                                        'home',
                                        const SubstackLoadedOptions(),
                                      );
                                    }
                                  : () => feed.refresh(force: true),
                              icon: Icon(
                                filtered
                                    ? Icons.filter_list_off
                                    : Icons.refresh,
                              ),
                              label: Text(
                                filtered
                                    ? L10n.of(
                                        context,
                                      ).plugin_reader_reset_filters
                                    : L10n.of(context).retry,
                              ),
                            ),
                          ),
                        ]);
                        children.add(_FeedStatus(feed: feed));
                        return ListView(
                          controller: pluginInnerScrollController(
                            context,
                            scrollController,
                          ),
                          padding: pluginFeedPadding(context),
                          physics: const AlwaysScrollableScrollPhysics(),
                          children: children,
                        );
                      }

                      return FeedListView(
                        controller: pluginInnerScrollController(
                          context,
                          scrollController,
                        ),
                        padding: pluginFeedPadding(context),
                        itemCount: 2 + visible.length,
                        itemBuilder: (context, index) {
                          if (index == 0) {
                            return Column(
                              mainAxisSize: MainAxisSize.min,
                              children: children,
                            );
                          }
                          final postIndex = index - 1;
                          if (postIndex < visible.length) {
                            final post = visible[postIndex];
                            return SubstackPostCard(
                              key: ValueKey(substackFeedPostKey(post)),
                              post: post,
                              showSourceBadge: false,
                              logoUrl: _logoFor(publications, post),
                            );
                          }
                          return _FeedStatus(feed: feed);
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
      onRefresh: () => feed.refresh(force: true),
      child: ScopedBuilder<SubstackFeedStore, SubstackFeedSnapshot>(
        store: feed,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.plugin_substack_load_error,
          onRetry: () => feed.refresh(force: true),
        ),
        onLoading: (_) => const PluginFeedSkeleton(),
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
                      controller: pluginInnerScrollController(
                        context,
                        scrollController,
                      ),
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

                  final controls = context.read<SubstackHomeControlsStore>();
                  final options = controls.options('inbox');
                  final unread = filterSubstackLoaded(
                    feed.allPosts
                        .where(
                          (p) =>
                              !readIds.contains(p.id) &&
                              publications.any(
                                (pub) => pub.baseUrl == p.publicationBaseUrl,
                              ),
                        )
                        .toList(),
                    options,
                  );
                  if (unread.isEmpty) {
                    return ListView(
                      controller: pluginInnerScrollController(
                        context,
                        scrollController,
                      ),
                      physics: const AlwaysScrollableScrollPhysics(),
                      children: [
                        SubstackReadingToolbar(
                          slot: 'inbox',
                          feed: feed,
                          publications: publications,
                          readIds: readIds,
                        ),
                        const SizedBox(height: 48),
                        Padding(
                          padding: const EdgeInsets.symmetric(horizontal: 32),
                          child: Text(
                            options.query.trim().isEmpty
                                ? l10n.plugin_substack_inbox_empty
                                : l10n.plugin_reader_empty_filter,
                            textAlign: TextAlign.center,
                          ),
                        ),
                        if (options.query.trim().isNotEmpty)
                          TextButton(
                            onPressed: () => controls.configure(
                              'inbox',
                              const SubstackLoadedOptions(),
                            ),
                            child: Text(l10n.plugin_reader_reset_filters),
                          ),
                        _FeedStatus(feed: feed),
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

                  return FeedListView(
                    controller: pluginInnerScrollController(
                      context,
                      scrollController,
                    ),
                    padding: const EdgeInsets.only(bottom: 24, top: 8),
                    itemCount: unread.length + 2,
                    itemBuilder: (context, index) {
                      if (index == 0) {
                        return SubstackReadingToolbar(
                          slot: 'inbox',
                          feed: feed,
                          publications: publications,
                          readIds: readIds,
                        );
                      }
                      if (index > unread.length) return _FeedStatus(feed: feed);
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
  late final SubstackHomeControlsStore _controls;
  late final TextEditingController _query;
  int get _section => _controls.state.librarySection;
  @override
  void initState() {
    super.initState();
    _controls = context.read<SubstackHomeControlsStore>();
    _query = TextEditingController(text: _controls.state.libraryQuery);
  }

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
    return pub.displayName.toLowerCase().contains(q) ||
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
            onChanged: (query) => _controls.library(query: query),
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
                    onSelected: (_) => _controls.library(section: i),
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
                        controller: pluginInnerScrollController(
                          context,
                          widget.scrollController,
                        ),
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
                      controller: pluginInnerScrollController(
                        context,
                        widget.scrollController,
                      ),
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
                          onTap: () async {
                            final feed = context.read<SubstackFeedStore>();
                            await Navigator.push(
                              context,
                              MaterialPageRoute(
                                builder: (_) =>
                                    SubstackArchiveScreen(publication: pub),
                              ),
                            );
                            if (context.mounted)
                              await feed.refresh(force: false);
                          },
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
                            controller: pluginInnerScrollController(
                              context,
                              widget.scrollController,
                            ),
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
                          controller: pluginInnerScrollController(
                            context,
                            widget.scrollController,
                          ),
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
      onRefresh: () => notes.refresh(force: true),
      child: ScopedBuilder<SubstackNotesStore, SubstackNotesPage>(
        store: notes,
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: l10n.plugin_substack_load_error,
          onRetry: () => notes.refresh(force: true),
        ),
        onLoading: (_) => const PluginFeedSkeleton(),
        onState: (context, page) {
          if (page.notes.isEmpty) {
            return ListView(
              controller: pluginInnerScrollController(
                context,
                scrollController,
              ),
              physics: const AlwaysScrollableScrollPhysics(),
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
            controller: pluginInnerScrollController(context, scrollController),
            physics: const AlwaysScrollableScrollPhysics(),
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
              return Padding(
                padding: const EdgeInsets.all(16),
                child: Column(
                  children: [
                    if (notes.refreshError != null ||
                        notes.loadMoreError != null) ...[
                      Text(l10n.plugin_substack_load_error),
                      TextButton(
                        onPressed: notes.refreshing || notes.loadingMore
                            ? null
                            : notes.loadMoreError != null
                            ? notes.retryLoadMore
                            : () => notes.refresh(force: true),
                        child: Text(l10n.retry),
                      ),
                    ],
                    if (notes.loadingMore)
                      const CircularProgressIndicator()
                    else if (page.nextCursor?.isNotEmpty == true)
                      OutlinedButton(
                        onPressed: notes.loadMore,
                        child: Text(l10n.plugin_substack_load_more),
                      ),
                  ],
                ),
              );
            },
          );
        },
      ),
    );
  }
}

class _FeedStatus extends StatelessWidget {
  final SubstackFeedStore feed;
  const _FeedStatus({required this.feed});
  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.all(16),
      child: Column(
        children: [
          if (feed.refreshError != null || feed.loadMoreError != null) ...[
            Text(l10n.plugin_substack_load_error),
            TextButton(
              onPressed: feed.refreshing || feed.loadingMore
                  ? null
                  : feed.loadMoreError != null
                  ? feed.retryLoadMore
                  : () => feed.refresh(force: true),
              child: Text(l10n.retry),
            ),
          ],
          if (feed.loadingMore || feed.refreshing)
            const CircularProgressIndicator()
          else if (feed.state.canLoadMore)
            OutlinedButton(
              onPressed: feed.loadMore,
              child: Text(l10n.plugin_substack_load_more),
            ),
        ],
      ),
    );
  }
}
