import 'dart:convert';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';

import 'package:quax/client/client.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/saved/likes_by_group.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/profile/media_grid/media_grid.dart';
import 'package:quax/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:quax/saved/folder_picker.dart';
import 'package:quax/saved/liked_tweet_model.dart';
import 'package:quax/saved/saved_chrome.dart';
import 'package:quax/saved/saved_cleanup.dart';
import 'package:quax/saved/saved_tab_order.dart';
import 'package:quax/saved/saved_tweet_folder_model.dart';
import 'package:quax/saved/saved_tweet_model.dart';
import 'package:quax/saved/saved_view_store.dart';
import 'package:quax/tweet/tweet.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/tweet/tweet_context_scope.dart';
import 'package:quax/tweet/tweet_skeleton.dart';
import 'package:quax/ui/errors.dart';
import 'package:quax/ui/reader_chrome.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';

class SavedScreen extends StatefulWidget {
  final ScrollController scrollController;
  final bool? showTitle;

  const SavedScreen({
    super.key,
    required this.scrollController,
    this.showTitle,
  });

  @override
  State<SavedScreen> createState() => _SavedScreenState();
}

class _SavedScreenState extends State<SavedScreen>
    with AutomaticKeepAliveClientMixin<SavedScreen> {
  late final SavedViewStore _viewStore;

  /// Focused when the search button opens the field, rather than by `autofocus`.
  ///
  /// This screen is kept alive, so the field's subtree is re-inserted whenever
  /// the folder strip or a filter chip rebuilds — with `autofocus` that raised
  /// the keyboard again each time, unasked.
  final FocusNode _searchFocusNode = FocusNode();

  @override
  bool get wantKeepAlive => true;

  @override
  void initState() {
    super.initState();
    _viewStore = SavedViewStore();

    context.read<SavedTweetModel>().listSavedTweets();
    context.read<SavedTweetFolderModel>().listFolders();
    context.read<LikedTweetModel>().listLikedTweets();
    _loadGroupMembership();
  }

  Future<void> _loadGroupMembership() async {
    final model = context.read<GroupsModel>();
    final members = await model.listGroupMembers();
    if (!mounted) return;

    _viewStore.setGroups(members, model.state);
  }

  @override
  void dispose() {
    _searchFocusNode.dispose();
    _viewStore.destroy();
    super.dispose();
  }

  // If the selected tab is no longer reachable (folder deleted elsewhere, or its
  // built-in tab was hidden in settings), fall back to "All".
  void _reconcileFilter(
    List<SavedTweetFolder> folders, {
    required bool showUnfiled,
    required bool showFavorites,
  }) {
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted)
        _viewStore.reconcileFolders(
          folders,
          showUnfiled: showUnfiled,
          showFavorites: showFavorites,
        );
    });
  }

  Future<void> _refresh() async {
    // Silent reload: keeps the current list on screen while the RefreshIndicator
    // spinner runs, and swaps in the fresh data only once it is ready.
    if (_viewStore.state.folder == savedTabFavorites) {
      await context.read<LikedTweetModel>().refreshLikedTweets();
    } else {
      await context.read<SavedTweetModel>().refreshSavedTweets();
    }
  }

  Widget _buildEmptyState(SavedViewState view) {
    return LayoutBuilder(
      builder: (context, constraints) => SingleChildScrollView(
        physics: const AlwaysScrollableScrollPhysics(),
        child: ConstrainedBox(
          constraints: BoxConstraints(minHeight: constraints.maxHeight),
          child: TweetEmptyState(
            message: view.query.isNotEmpty
                ? L10n.of(context).no_posts_match_your_search
                : switch (view.folder) {
                    savedTabAll => L10n.of(
                      context,
                    ).you_have_not_saved_any_tweets_yet,
                    savedTabFavorites => L10n.of(context).no_liked_posts_yet,
                    _ => L10n.of(context).folder_is_empty,
                  },
          ),
        ),
      ),
    );
  }

  /// Case-insensitive match of a stored tweet's JSON against the search query:
  /// post text (including long-post note text) plus author name and handle.
  bool _matchesQuery(String? content, String query) {
    if (content == null) {
      return false;
    }
    final needle = query.toLowerCase();
    try {
      final json = jsonDecode(content);
      final haystacks = [
        json['full_text'] as String?,
        json['text'] as String?,
        json['noteText'] as String?,
        json['user']?['name'] as String?,
        json['user']?['screen_name'] as String?,
      ];
      return haystacks.any(
        (h) => h != null && h.toLowerCase().contains(needle),
      );
    } catch (_) {
      return false;
    }
  }

  List<T> _applySearch<T>(
    List<T> items,
    String? Function(T) contentOf,
    String query,
  ) {
    if (query.isEmpty) {
      return items;
    }
    return items.where((e) => _matchesQuery(contentOf(e), query)).toList();
  }

  Widget _buildSearchField() {
    return SavedSearchField(
      focusNode: _searchFocusNode,
      onChanged: _viewStore.setQuery,
      onClose: _toggleSearch,
    );
  }

  Widget _buildList({
    required int itemCount,
    required SavedTweetTile Function(int) tileAt,
  }) {
    return ListView.builder(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(top: kTweetSpace1),
      itemCount: itemCount,
      itemBuilder: (context, index) => tileAt(index),
    );
  }

  /// Media entries of the given saved posts, for the media-only grid.
  List<MediaGridItem> _mediaItemsOf(Iterable<String?> contents) {
    var chains = <TweetChain>[];
    for (var content in contents) {
      if (content == null) {
        continue;
      }
      var tweet = TweetWithCard.fromJson(jsonDecode(content));
      if (tweet.idStr == null) {
        continue;
      }
      chains.add(
        TweetChain(id: tweet.idStr!, tweets: [tweet], isPinned: false),
      );
    }
    return mediaItemsFromChains(chains);
  }

  Widget _buildMediaGrid(
    Iterable<String?> contents, {
    required Future<void> Function(String id) onDelete,
  }) {
    return RefreshIndicator(
      onRefresh: _refresh,
      child: StaticMediaGrid(
        items: _mediaItemsOf(contents),
        emptyMessage: L10n.of(context).could_not_find_any_posts_with_media,
        onLongPressItem: (item) =>
            _confirmRemoveFromGallery(item.tweetId, onDelete),
      ),
    );
  }

  // Long-pressing a tile in the saved gallery removes that post — handy for
  // clearing the dead "not available" ones without leaving gallery mode.
  Future<void> _confirmRemoveFromGallery(
    String id,
    Future<void> Function(String id) onDelete,
  ) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(L10n.of(context).are_you_sure),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(L10n.of(context).cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(L10n.of(context).delete),
          ),
        ],
      ),
    );
    if (confirmed == true) {
      await onDelete(id);
    }
  }

  List<SavedTweet> _applyFilter(List<SavedTweet> tweets, String folder) {
    switch (folder) {
      case savedTabAll:
        return tweets;
      case savedTabUnfiled:
        return tweets.where((e) => e.folderId == null).toList();
      default:
        return tweets.where((e) => e.folderId == folder).toList();
    }
  }

  List<SavedFolderOption> _folderOptions(List<SavedTweetFolder> folders) {
    var prefs = PrefService.of(context, listen: false);
    var showAll = prefs.get<bool>(optionSavedShowAllTab) ?? true;
    var showUnfiled = prefs.get<bool>(optionSavedShowUnfiledTab) ?? true;
    var showFavorites = prefs.get<bool>(optionSavedShowFavoritesTab) ?? true;
    var storedOrder = prefs.get<String>(optionSavedTabOrder);
    _reconcileFilter(
      folders,
      showUnfiled: showUnfiled,
      showFavorites: showFavorites,
    );
    final byId = {for (final folder in folders) folder.id: folder};
    return orderedSavedTabs(folders, storedOrder)
        .map((token) {
          if (token == savedTabAll && showAll) {
            return SavedFolderOption(
              value: token,
              label: L10n.of(context).all,
              icon: Icons.bookmarks_outlined,
            );
          }
          if (token == savedTabUnfiled && showUnfiled && folders.isNotEmpty) {
            return SavedFolderOption(
              value: token,
              label: L10n.of(context).unfiled,
              icon: Icons.folder_open_outlined,
            );
          }
          if (token == savedTabFavorites && showFavorites) {
            return SavedFolderOption(
              value: token,
              label: L10n.of(context).favorites,
              icon: Icons.favorite_border,
            );
          }
          final folder = byId[token];
          return folder == null
              ? null
              : SavedFolderOption(
                  value: folder.id,
                  label: folder.name,
                  icon: Icons.folder_outlined,
                  editable: true,
                );
        })
        .whereType<SavedFolderOption>()
        .toList(growable: false);
  }

  SavedControlBar _buildControlBar(
    SavedViewState view,
    List<SavedTweetFolder> folders,
  ) {
    return SavedControlBar(
      selectedFolder: view.folder,
      mediaOnly: view.mediaOnly,
      likesByGroup: view.likesByGroup,
      folders: _folderOptions(folders),
      onFolderSelected: _viewStore.selectFolder,
      onFolderLongPress: (option) =>
          _showFolderMenu(option.value, option.label),
      onMediaToggle: _viewStore.toggleMedia,
    );
  }

  Future<void> _showFolderMenu(String folderId, String label) async {
    var folderModel = context.read<SavedTweetFolderModel>();
    var matches = folderModel.state.where((f) => f.id == folderId);
    if (matches.isEmpty) {
      return;
    }
    var folder = matches.first;

    await HapticFeedback.lightImpact();
    if (!mounted) {
      return;
    }

    await showModalBottomSheet(
      context: context,
      useSafeArea: true,
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ListTile(
              minTileHeight: kTweetTouchTarget,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: kTweetHorizontalPadding,
              ),
              leading: const Icon(Icons.edit_outlined),
              title: Text(L10n.of(sheetContext).rename),
              onTap: () {
                Navigator.pop(sheetContext);
                showCreateFolderDialog(context, folderModel, existing: folder);
              },
            ),
            ListTile(
              minTileHeight: kTweetTouchTarget,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: kTweetHorizontalPadding,
              ),
              leading: const Icon(Icons.delete_outline),
              title: Text(L10n.of(sheetContext).delete),
              onTap: () async {
                Navigator.pop(sheetContext);
                var deleted = await showDeleteFolderDialog(
                  context,
                  folderModel,
                  folder,
                );
                if (deleted && mounted && _viewStore.state.folder == folderId)
                  _viewStore.showAll();
              },
            ),
            ListTile(
              minTileHeight: kTweetTouchTarget,
              contentPadding: const EdgeInsets.symmetric(
                horizontal: kTweetHorizontalPadding,
              ),
              leading: const Icon(Icons.folder_copy_outlined),
              title: Text(L10n.of(sheetContext).manage_folders),
              onTap: () async {
                Navigator.pop(sheetContext);
                await Navigator.pushNamed(context, routeSavedFolders);
                if (mounted) _viewStore.refresh();
              },
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildSavedBody(SavedTweetModel model, SavedViewState view) {
    return ScopedBuilder<SavedTweetModel, List<SavedTweet>>.transition(
      store: model,
      onError: (_, e) => FullPageErrorWidget(
        error: e,
        stackTrace: null,
        prefix: L10n.current.unable_to_load_the_tweets,
        onRetry: () => model.listSavedTweets(),
      ),
      onLoading: (_) => const TweetFeedSkeleton(),
      onState: (_, data) {
        var filtered = _applySearch(
          _applyFilter(data, view.folder),
          (SavedTweet e) => e.content,
          view.query,
        );

        final child = view.mediaOnly && filtered.isNotEmpty
            ? _buildMediaGrid(
                filtered.map((e) => e.content),
                onDelete: (id) =>
                    context.read<SavedTweetModel>().deleteSavedTweet(id),
              )
            : RefreshIndicator(
                onRefresh: _refresh,
                child: filtered.isEmpty
                    ? _buildEmptyState(view)
                    : _buildList(
                        itemCount: filtered.length,
                        tileAt: (i) => SavedTweetTile(
                          id: filtered[i].id,
                          content: filtered[i].content,
                        ),
                      ),
              );
        return _modeTransition(child, view);
      },
    );
  }

  /// Likes under one heading per group their author belongs to.
  ///
  /// One flat list with headings rather than a list of lists: the reader is
  /// still scrolling their likes, just with the feeds they came from marked.
  Widget _buildLikesByGroup(List<LikedTweet> likes, SavedViewState view) {
    final sections = likesByGroup<LikedTweet>(
      likes,
      authorOf: (like) => like.user,
      members: view.groupMembers,
      groupIds: view.groups.map((g) => g.id).toList(growable: false),
    );

    final nameOf = {for (final group in view.groups) group.id: group.name};
    final rows = <Widget>[];
    for (final section in sections) {
      rows.add(
        Padding(
          padding: const EdgeInsets.fromLTRB(
            kTweetHorizontalPadding,
            kTweetSpace4,
            kTweetHorizontalPadding,
            kTweetSpace1,
          ),
          child: Text(
            section.isUngrouped
                ? L10n.of(context).likes_without_a_group
                : nameOf[section.groupId] ?? '',
            style: tweetLabelStyle(context),
          ),
        ),
      );
      rows.addAll(
        section.items.map(
          (like) => SavedTweetTile(id: like.id, content: like.content),
        ),
      );
    }

    return ListView(
      physics: const AlwaysScrollableScrollPhysics(),
      children: rows,
    );
  }

  Widget _buildFavoritesBody(SavedViewState view) {
    var model = context.read<LikedTweetModel>();

    return ScopedBuilder<LikedTweetModel, List<LikedTweet>>.transition(
      store: model,
      onError: (_, e) => FullPageErrorWidget(
        error: e,
        stackTrace: null,
        prefix: L10n.current.unable_to_load_the_tweets,
        onRetry: () => model.listLikedTweets(),
      ),
      onLoading: (_) => const TweetFeedSkeleton(),
      onState: (_, data) {
        var filtered = _applySearch(
          data,
          (LikedTweet e) => e.content,
          view.query,
        );

        if (view.mediaOnly && filtered.isNotEmpty) {
          return _modeTransition(
            _buildMediaGrid(
              filtered.map((e) => e.content),
              onDelete: (id) => context.read<LikedTweetModel>().unlikeTweet(id),
            ),
            view,
          );
        }

        if (view.likesByGroup && filtered.isNotEmpty) {
          return RefreshIndicator(
            onRefresh: _refresh,
            child: _buildLikesByGroup(filtered, view),
          );
        }

        return _modeTransition(
          RefreshIndicator(
            onRefresh: _refresh,
            child: filtered.isEmpty
                ? _buildEmptyState(view)
                : _buildList(
                    itemCount: filtered.length,
                    tileAt: (i) => SavedTweetTile(
                      id: filtered[i].id,
                      content: filtered[i].content,
                    ),
                  ),
          ),
          view,
        );
      },
    );
  }

  Widget _modeTransition(Widget child, SavedViewState view) {
    final reduceMotion =
        MediaQuery.disableAnimationsOf(context) ||
        PrefService.of(context, listen: false).get(optionDisableAnimations) ==
            true;
    return AnimatedSwitcher(
      duration: Duration(milliseconds: reduceMotion ? 0 : 160),
      switchInCurve: Curves.easeOutCubic,
      switchOutCurve: Curves.easeInCubic,
      child: KeyedSubtree(
        key: ValueKey('${view.folder}:${view.mediaOnly}'),
        child: child,
      ),
    );
  }

  void _toggleSearch() {
    final willOpen = !_viewStore.state.searching;
    _viewStore.toggleSearch();
    if (willOpen) {
      WidgetsBinding.instance.addPostFrameCallback(
        (_) => _searchFocusNode.requestFocus(),
      );
    } else {
      _searchFocusNode.unfocus();
    }
  }

  Future<void> _manageFolders() async {
    await Navigator.pushNamed(context, routeSavedFolders);
    if (mounted) _viewStore.refresh();
  }

  void _handleOverflow(SavedOverflowAction action) {
    switch (action) {
      case SavedOverflowAction.createFolder:
        showCreateFolderDialog(context, context.read<SavedTweetFolderModel>());
        return;
      case SavedOverflowAction.manageFolders:
        _manageFolders();
        return;
      case SavedOverflowAction.cleanup:
        showDialog(
          context: context,
          barrierDismissible: false,
          builder: (_) => const BrokenBookmarksDialog(),
        );
        return;
      case SavedOverflowAction.settings:
        Navigator.pushNamed(context, routeSettings);
        return;
    }
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    final savedModel = context.read<SavedTweetModel>();
    final folderModel = context.read<SavedTweetFolderModel>();
    return XtaSystemBars(
      child: ScopedBuilder<SavedViewStore, SavedViewState>(
        store: _viewStore,
        onState: (_, view) =>
            ScopedBuilder<SavedTweetFolderModel, List<SavedTweetFolder>>(
              store: folderModel,
              onState: (_, folders) => _buildScreen(savedModel, view, folders),
            ),
      ),
    );
  }

  Widget _buildScreen(
    SavedTweetModel savedModel,
    SavedViewState view,
    List<SavedTweetFolder> folders,
  ) {
    final controls = _buildControlBar(view, folders);
    return NestedScrollView(
      controller: widget.scrollController,
      floatHeaderSlivers: true,
      headerSliverBuilder: (context, innerBoxIsScrolled) => [
        if (widget.showTitle != false)
          SliverAppBar(
            backgroundColor: Theme.of(context).colorScheme.surface,
            surfaceTintColor: Colors.transparent,
            elevation: 0,
            scrolledUnderElevation: 0,
            pinned: false,
            snap: true,
            floating: true,
            centerTitle: false,
            titleSpacing: kTweetHorizontalPadding,
            title: Text(L10n.of(context).saved),
            bottom: controls,
            actions: [
              IconButton(
                isSelected: view.searching,
                icon: const Icon(Icons.search),
                tooltip: L10n.of(context).search_saved_posts,
                onPressed: _toggleSearch,
              ),
              SavedOverflowButton(onSelected: _handleOverflow),
            ],
          ),
      ],
      body: TweetContextScope(
        child: Column(
          children: [
            if (widget.showTitle == false) controls,
            if (view.searching) _buildSearchField(),
            Expanded(
              child: view.folder == savedTabFavorites
                  ? _buildFavoritesBody(view)
                  : _buildSavedBody(savedModel, view),
            ),
          ],
        ),
      ),
    );
  }
}

class SavedTweetTile extends StatelessWidget {
  final String id;
  final String? content;

  const SavedTweetTile({super.key, required this.id, this.content});

  @override
  Widget build(BuildContext context) {
    var content = this.content;
    if (content == null) {
      // The tweet is probably too big to fit inside the cursor and has been removed from the result set
      return SavedTweetTooLarge(id: id);
    }

    var tweet = TweetWithCard.fromJson(jsonDecode(content));

    return TweetTile(key: Key(tweet.idStr!), tweet: tweet, clickable: true);
  }
}

class SavedTweetTooLarge extends StatelessWidget {
  final String id;

  const SavedTweetTooLarge({super.key, required this.id});

  @override
  Widget build(BuildContext context) {
    return TweetStateTile(
      icon: Icons.error_outline,
      message: L10n.of(context).saved_tweet_too_large,
    );
  }
}

class SavedTweetTooLargeException implements Exception {
  final String id;

  SavedTweetTooLargeException(this.id);

  @override
  String toString() {
    return 'The saved tweet with the ID $id was too large';
  }
}
