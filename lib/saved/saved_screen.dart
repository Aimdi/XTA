import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:flutter_triple/flutter_triple.dart';

import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/saved/likes_by_group.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/media_grid/media_grid.dart';
import 'package:xta/profile/media_grid/media_grid_items/media_grid_item.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/saved/folder_picker.dart';
import 'package:xta/saved/liked_tweet_model.dart';
import 'package:xta/saved/saved_chrome.dart';
import 'package:xta/saved/saved_cleanup.dart';
import 'package:xta/saved/saved_tab_order.dart';
import 'package:xta/saved/saved_tweet_folder_model.dart';
import 'package:xta/saved/saved_tweet_model.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/saved/library_on_device.dart';
import 'package:xta/saved/saved_content_index.dart';
import 'package:xta/saved/local_post_compose.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/saved/local_post_model.dart';
import 'package:xta/saved/local_post_tile.dart';
import 'package:xta/saved/local_note_thread.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/reddit/reddit_client.dart';
import 'package:xta/plugins/reddit/reddit_post_card.dart';
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
  // Selected folder filter: savedTabAll, savedTabUnfiled, or a folder id.
  String _filter = savedTabAll;
  bool _mediaOnly = false;
  bool _searching = false;

  /// Lowercased and trimmed, so filtering compares without re-allocating.
  String _query = '';

  /// A fast typist would otherwise filter the whole table once per character.
  Timer? _searchDebounce;
  static const _searchDebounceDuration = Duration(milliseconds: 200);

  /// Whether likes are broken out by the group their author belongs to.
  bool _likesByGroup = false;

  /// Group membership and group names, read once so the breakdown does not
  /// query per like.
  List<SubscriptionGroupMember> _groupMembers = const [];
  List<SubscriptionGroup> _groups = const [];

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

    context.read<SavedTweetModel>().listSavedTweets();
    context.read<SavedTweetFolderModel>().listFolders();
    context.read<LikedTweetModel>().listLikedTweets();
    context.read<LocalPostModel>().listLocalPosts();
    _loadGroupMembership();
  }

  Future<void> _loadGroupMembership() async {
    final model = context.read<GroupsModel>();
    final members = await model.listGroupMembers();
    if (!mounted) return;

    setState(() {
      _groupMembers = members;
      _groups = model.state;
    });
  }

  @override
  void dispose() {
    _searchDebounce?.cancel();
    _searchFocusNode.dispose();
    super.dispose();
  }

  // If the selected tab is no longer reachable (folder deleted elsewhere, or its
  // built-in tab was hidden in settings), fall back to "All".
  void _reconcileFilter(
    List<SavedTweetFolder> folders, {
    required bool showUnfiled,
    required bool showFavorites,
  }) {
    var reachable =
        _filter == savedTabAll ||
        (_filter == savedTabUnfiled && showUnfiled && folders.isNotEmpty) ||
        (_filter == savedTabFavorites && showFavorites) ||
        _filter == savedTabNotes ||
        folders.any((f) => f.id == _filter);
    if (reachable) {
      return;
    }

    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        setState(() => _filter = savedTabAll);
      }
    });
  }

  Future<void> _refresh() async {
    // Silent reload: keeps the current list on screen while the RefreshIndicator
    // spinner runs, and swaps in the fresh data only once it is ready.
    if (_filter == savedTabFavorites) {
      await context.read<LikedTweetModel>().refreshLikedTweets();
    } else if (_filter == savedTabNotes) {
      await context.read<LocalPostModel>().refreshLocalPosts();
    } else {
      await context.read<SavedTweetModel>().refreshSavedTweets();
    }
  }

  Widget _buildEmptyState() {
    return SavedLibraryEmpty(
      kind: savedLibraryEmptyKind(query: _query, filter: _filter),
      onWriteNote: _composeNote,
    );
  }

  /// Case-insensitive match against the parsed post the store already holds.
  List<T> _applySearch<T>(
    List<T> items,
    String Function(T) idOf,
    SavedContent? Function(String) contentOf,
  ) {
    if (_query.isEmpty) {
      return items;
    }
    return items
        .where((e) => contentOf(idOf(e))?.matches(_query) ?? false)
        .toList();
  }

  /// Case-insensitive match against note text and parsed post content.
  List<SavedTweet> _applySavedSearch(
    List<SavedTweet> items,
    SavedContent? Function(String) contentOf,
  ) {
    if (_query.isEmpty) {
      return items;
    }
    return items.where((saved) {
      final note = saved.note?.toLowerCase() ?? '';
      if (note.contains(_query)) {
        return true;
      }
      return contentOf(saved.id)?.matches(_query) ?? false;
    }).toList();
  }

  void _onQueryChanged(String value) {
    _searchDebounce?.cancel();
    _searchDebounce = Timer(_searchDebounceDuration, () {
      if (mounted) {
        setState(() => _query = value.trim().toLowerCase());
      }
    });
  }

  Widget _buildSearchField() {
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
      child: TextField(
        focusNode: _searchFocusNode,
        decoration: InputDecoration(
          hintText: L10n.of(context).clip_note_search_hint,
          prefixIcon: const Icon(Icons.search),
          isDense: true,
          border: OutlineInputBorder(borderRadius: BorderRadius.circular(24)),
        ),
        onChanged: _onQueryChanged,
      ),
    );
  }

  Widget _buildList({
    required int itemCount,
    required Widget Function(int) tileAt,
    EdgeInsetsGeometry? padding,
  }) {
    return FeedListView(
      physics: const AlwaysScrollableScrollPhysics(),
      padding: padding ?? const EdgeInsets.only(top: 4),
      itemCount: itemCount,
      itemBuilder: (context, index) => tileAt(index),
    );
  }

  /// Media entries of the given saved posts, for the media-only grid.
  List<MediaGridItem> _mediaItemsOf(Iterable<TweetWithCard?> tweets) {
    var chains = tweets
        .whereType<TweetWithCard>()
        .where((tweet) => tweet.idStr != null)
        .map(
          (tweet) =>
              TweetChain(id: tweet.idStr!, tweets: [tweet], isPinned: false),
        )
        .toList();

    return mediaItemsFromChains(chains);
  }

  Widget _buildMediaGrid(
    Iterable<TweetWithCard?> tweets, {
    required Future<void> Function(String id) onDelete,
  }) {
    return RefreshIndicator(
      onRefresh: _refresh,
      child: StaticMediaGrid(
        items: _mediaItemsOf(tweets),
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

  List<SavedTweet> _applyFilter(List<SavedTweet> tweets) {
    switch (_filter) {
      case savedTabAll:
        return tweets;
      case savedTabUnfiled:
        return tweets.where((e) => e.folderId == null).toList();
      default:
        return tweets.where((e) => e.folderId == _filter).toList();
    }
  }

  Widget _buildFolderStrip() {
    var prefs = PrefService.of(context, listen: false);
    var showAll = prefs.get<bool>(optionSavedShowAllTab) ?? true;
    var showUnfiled = prefs.get<bool>(optionSavedShowUnfiledTab) ?? true;
    var showFavorites = prefs.get<bool>(optionSavedShowFavoritesTab) ?? true;
    var storedOrder = prefs.get<String>(optionSavedTabOrder);

    return ScopedBuilder<SavedTweetFolderModel, List<SavedTweetFolder>>(
      store: context.read<SavedTweetFolderModel>(),
      onState: (context, folders) {
        // Reconcile before the empty check, otherwise deleting the last folder would
        // leave `_filter` stranded on a now-deleted id (the strip returns early).
        _reconcileFilter(
          folders,
          showUnfiled: showUnfiled,
          showFavorites: showFavorites,
        );

        var options = <SavedFolderOption>[];
        for (var token in orderedSavedTabs(folders, storedOrder)) {
          if (token == savedTabAll) {
            if (showAll)
              options.add(
                SavedFolderOption(
                  value: savedTabAll,
                  label: L10n.of(context).all,
                  icon: Icons.bookmarks_outlined,
                ),
              );
          } else if (token == savedTabUnfiled) {
            // "Unfiled" only makes sense with folders — otherwise it duplicates "All".
            if (showUnfiled && folders.isNotEmpty) {
              options.add(
                SavedFolderOption(
                  value: savedTabUnfiled,
                  label: L10n.of(context).unfiled,
                  icon: Icons.folder_off_outlined,
                ),
              );
            }
          } else if (token == savedTabFavorites) {
            if (showFavorites)
              options.add(
                SavedFolderOption(
                  value: savedTabFavorites,
                  label: L10n.of(context).favorites,
                  icon: Icons.favorite_outline,
                ),
              );
          } else if (token == savedTabNotes) {
            options.add(
              SavedFolderOption(
                value: savedTabNotes,
                label: L10n.of(context).local_notes_tab,
                icon: Icons.edit_note_outlined,
              ),
            );
          } else {
            var matches = folders.where((f) => f.id == token);
            if (matches.isNotEmpty)
              options.add(
                SavedFolderOption(
                  value: token,
                  label: matches.first.name,
                  icon: Icons.folder_outlined,
                  editable: true,
                ),
              );
          }
        }

        return SavedControlBar(
          selectedFolder: _filter,
          mediaOnly: _mediaOnly,
          likesByGroup: _likesByGroup,
          folders: options,
          showMedia: _filter != savedTabNotes,
          onFolderSelected: (value) => setState(() {
            if (value == savedTabFavorites && _filter == savedTabFavorites) {
              _likesByGroup = !_likesByGroup;
            } else {
              _likesByGroup = false;
              _filter = value;
            }
          }),
          onFolderLongPress: (option) =>
              _showFolderMenu(option.value, option.label),
          onMediaToggle: () => setState(() => _mediaOnly = !_mediaOnly),
        );
      },
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
      showDragHandle: true,
      builder: (sheetContext) => SafeArea(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 24),
              leading: const Icon(Icons.edit_outlined),
              title: Text(L10n.of(sheetContext).rename),
              onTap: () {
                Navigator.pop(sheetContext);
                showCreateFolderDialog(context, folderModel, existing: folder);
              },
            ),
            ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 24),
              leading: const Icon(Icons.delete_outline),
              title: Text(L10n.of(sheetContext).delete),
              onTap: () async {
                Navigator.pop(sheetContext);
                var deleted = await showDeleteFolderDialog(
                  context,
                  folderModel,
                  folder,
                );
                if (deleted && mounted && _filter == folderId) {
                  setState(() => _filter = savedTabAll);
                }
              },
            ),
            ListTile(
              contentPadding: const EdgeInsets.symmetric(horizontal: 24),
              leading: const Icon(Icons.folder_copy_outlined),
              title: Text(L10n.of(sheetContext).manage_folders),
              onTap: () async {
                Navigator.pop(sheetContext);
                await Navigator.pushNamed(context, routeSavedFolders);
                if (mounted) {
                  setState(() {});
                }
              },
            ),
          ],
        ),
      ),
    );
  }

  Future<void> _handleOverflow(SavedOverflowAction action) async {
    switch (action) {
      case SavedOverflowAction.createFolder:
        await showCreateFolderDialog(
          context,
          context.read<SavedTweetFolderModel>(),
        );
        return;
      case SavedOverflowAction.manageFolders:
        await Navigator.pushNamed(context, routeSavedFolders);
        if (mounted) setState(() {});
        return;
      case SavedOverflowAction.cleanup:
        if (!mounted) return;
        await showDialog<void>(
          context: context,
          barrierDismissible: false,
          builder: (_) => const BrokenBookmarksDialog(),
        );
        return;
      case SavedOverflowAction.settings:
        if (mounted) await Navigator.pushNamed(context, routeSettings);
        return;
    }
  }

  Future<void> _composeNote([LocalPost? existing]) async {
    final saved = await openLocalPostComposer(context, existing: existing);
    if (saved != null && mounted) {
      setState(() => _filter = savedTabNotes);
    }
  }

  Future<void> _replyToNote(LocalPost parent) async {
    final saved = await openLocalPostComposer(context, replyTo: parent);
    if (saved == null || !mounted) {
      return;
    }
    setState(() => _filter = savedTabNotes);
    await openLocalNoteThread(
      context,
      rootId: localPostThreadRootId(
        context.read<LocalPostModel>().state,
        parent.id,
      ),
    );
  }

  Future<void> _deleteNote(LocalPost post) async {
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(L10n.of(context).are_you_sure),
        content: Text(L10n.of(context).local_note_delete_confirm),
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
    if (confirmed == true && mounted) {
      await context.read<LocalPostModel>().deleteLocalPost(post.id);
    }
  }

  Widget _buildNotesBody() {
    final model = context.read<LocalPostModel>();
    return ScopedBuilder<LocalPostModel, List<LocalPost>>(
      store: model,
      onError: (_, e) => FullPageErrorWidget(
        error: e,
        stackTrace: null,
        prefix: L10n.current.unable_to_load_the_tweets,
        onRetry: () => model.listLocalPosts(),
      ),
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onState: (_, data) {
        final filtered = _query.isEmpty
            ? data
            : data
                  .where((post) => localPostRecordMatches(post, _query))
                  .toList();
        final roots = localPostRoots(filtered);
        return RefreshIndicator(
          onRefresh: _refresh,
          child: roots.isEmpty
              ? _buildEmptyState()
              : _buildList(
                  itemCount: roots.length,
                  padding: const EdgeInsets.only(
                    top: 4,
                    bottom: kPluginHomeNavClearance + 72,
                  ),
                  tileAt: (i) {
                    final post = roots[i];
                    return LocalPostTile(
                      post: post,
                      replyCount: localPostDirectReplyCount(data, post.id),
                      onEdit: () => _composeNote(post),
                      onDelete: () => _deleteNote(post),
                      onReply: () => _replyToNote(post),
                      onOpen: () => openLocalNoteThread(
                        context,
                        rootId: localPostThreadRootId(data, post.id),
                      ),
                    );
                  },
                ),
        );
      },
    );
  }

  Widget _buildSavedBody(SavedTweetModel model) {
    return ScopedBuilder<SavedTweetModel, List<SavedTweet>>(
      store: model,
      onError: (_, e) => FullPageErrorWidget(
        error: e,
        stackTrace: null,
        prefix: L10n.current.unable_to_load_the_tweets,
        onRetry: () => model.listSavedTweets(),
      ),
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onState: (_, data) {
        var filtered = _applySavedSearch(_applyFilter(data), model.contentOf);

        if (_mediaOnly && filtered.isNotEmpty) {
          return _buildMediaGrid(
            filtered.map((e) => model.contentOf(e.id)?.tweet),
            onDelete: (id) => model.deleteSavedTweet(id),
          );
        }

        return RefreshIndicator(
          onRefresh: _refresh,
          child: filtered.isEmpty
              ? _buildEmptyState()
              : _buildList(
                  itemCount: filtered.length,
                  tileAt: (i) => SavedClipTile(
                    saved: filtered[i],
                    tweet: model.contentOf(filtered[i].id)?.tweet,
                    reddit: model.contentOf(filtered[i].id)?.reddit,
                    onNoteChanged: (note) =>
                        model.setNote(filtered[i].id, note),
                  ),
                ),
        );
      },
    );
  }

  /// Likes under one heading per group their author belongs to.
  ///
  /// One flat list with headings rather than a list of lists: the reader is
  /// still scrolling their likes, just with the feeds they came from marked.
  Widget _buildLikesByGroup(List<LikedTweet> likes, LikedTweetModel model) {
    final sections = likesByGroup<LikedTweet>(
      likes,
      authorOf: (like) => like.user,
      members: _groupMembers,
      groupIds: _groups.map((g) => g.id).toList(growable: false),
    );

    final nameOf = {for (final group in _groups) group.id: group.name};
    final rows = <({String? heading, LikedTweet? like})>[];
    for (final section in sections) {
      rows.add((
        heading: section.isUngrouped
            ? L10n.of(context).likes_without_a_group
            : nameOf[section.groupId] ?? '',
        like: null,
      ));
      for (final like in section.items) {
        rows.add((heading: null, like: like));
      }
    }

    return FeedListView(
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: rows.length,
      itemBuilder: (context, index) {
        final row = rows[index];
        final heading = row.heading;
        if (heading != null) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
            child: Text(
              heading,
              style: Theme.of(
                context,
              ).textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700),
            ),
          );
        }
        final like = row.like!;
        return SavedTweetTile(
          key: ValueKey(like.id),
          id: like.id,
          tweet: model.contentOf(like.id)?.tweet,
          reddit: model.contentOf(like.id)?.reddit,
        );
      },
    );
  }

  Widget _buildFavoritesBody() {
    var model = context.read<LikedTweetModel>();

    return ScopedBuilder<LikedTweetModel, List<LikedTweet>>(
      store: model,
      onError: (_, e) => FullPageErrorWidget(
        error: e,
        stackTrace: null,
        prefix: L10n.current.unable_to_load_the_tweets,
        onRetry: () => model.listLikedTweets(),
      ),
      onLoading: (_) => const Center(child: CircularProgressIndicator()),
      onState: (_, data) {
        var filtered = _applySearch(
          data,
          (LikedTweet e) => e.id,
          model.contentOf,
        );

        if (_mediaOnly && filtered.isNotEmpty) {
          return _buildMediaGrid(
            filtered.map((e) => model.contentOf(e.id)?.tweet),
            onDelete: (id) => model.unlikeTweet(id),
          );
        }

        if (_likesByGroup && filtered.isNotEmpty) {
          return RefreshIndicator(
            onRefresh: _refresh,
            child: _buildLikesByGroup(filtered, model),
          );
        }

        return RefreshIndicator(
          onRefresh: _refresh,
          child: filtered.isEmpty
              ? _buildEmptyState()
              : _buildList(
                  itemCount: filtered.length,
                  tileAt: (i) => SavedTweetTile(
                    id: filtered[i].id,
                    tweet: model.contentOf(filtered[i].id)?.tweet,
                    reddit: model.contentOf(filtered[i].id)?.reddit,
                  ),
                ),
        );
      },
    );
  }

  @override
  Widget build(BuildContext context) {
    super.build(context);
    var model = context.read<SavedTweetModel>();

    var prefs = PrefService.of(context, listen: false);

    return XtaSystemBars(
      child: Scaffold(
        backgroundColor: Colors.transparent,
        floatingActionButton: Padding(
          padding: const EdgeInsets.only(bottom: kPluginHomeNavClearance),
          child: FloatingActionButton(
            heroTag: 'local-note-compose',
            tooltip: L10n.of(context).local_note_fab_tooltip,
            onPressed: () => _composeNote(),
            child: const Icon(Icons.edit_note),
          ),
        ),
        body: NestedScrollView(
          controller: widget.scrollController,
          headerSliverBuilder: (context, innerBoxIsScrolled) {
            return [
              if (widget.showTitle != false)
                SliverAppBar(
                  pinned: true,
                  surfaceTintColor: Colors.transparent,
                  scrolledUnderElevation: 0,
                  titleSpacing: kTweetHorizontalPadding,
                  title: Text(L10n.current.saved),
                  actions: [
                    IconButton(
                      isSelected: _searching,
                      icon: const Icon(Icons.search),
                      tooltip: L10n.current.search_saved_posts,
                      onPressed: () => setState(() {
                        _searching = !_searching;
                        if (_searching) {
                          WidgetsBinding.instance.addPostFrameCallback(
                            (_) => _searchFocusNode.requestFocus(),
                          );
                        } else {
                          _searchDebounce?.cancel();
                          _query = '';
                          _searchFocusNode.unfocus();
                        }
                      }),
                    ),
                    SavedOverflowButton(onSelected: _handleOverflow),
                  ],
                ),
            ];
          },
          body: MultiProvider(
            providers: [
              ChangeNotifierProvider<TweetContextState>(
                create: (_) => TweetContextState.fromPrefs(prefs),
              ),
            ],
            child: Column(
              children: [
                _buildFolderStrip(),
                SavedLibraryOnDeviceNotice(filter: _filter),
                if (_searching) _buildSearchField(),
                Expanded(
                  child: _filter == savedTabFavorites
                      ? _buildFavoritesBody()
                      : _filter == savedTabNotes
                      ? _buildNotesBody()
                      : _buildSavedBody(model),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Saved post with an optional local note shown underneath.
class SavedClipTile extends StatefulWidget {
  final SavedTweet saved;
  final TweetWithCard? tweet;
  final RedditPost? reddit;
  final Future<void> Function(String?) onNoteChanged;

  const SavedClipTile({
    super.key,
    required this.saved,
    this.tweet,
    this.reddit,
    required this.onNoteChanged,
  });

  @override
  State<SavedClipTile> createState() => _SavedClipTileState();
}

class _SavedClipTileState extends State<SavedClipTile> {
  bool _editing = false;
  late final TextEditingController _controller;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.saved.note ?? '');
  }

  @override
  void didUpdateWidget(covariant SavedClipTile oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (oldWidget.saved.note != widget.saved.note && !_editing) {
      _controller.text = widget.saved.note ?? '';
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _saveNote() async {
    await widget.onNoteChanged(_controller.text);
    if (mounted) {
      setState(() => _editing = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final note = widget.saved.note;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        SavedTweetTile(
          id: widget.saved.id,
          tweet: widget.tweet,
          reddit: widget.reddit,
        ),
        if (_editing)
          Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(16, 0, 16, 8),
            child: Row(
              children: [
                Expanded(
                  child: TextField(
                    controller: _controller,
                    minLines: 1,
                    maxLines: 4,
                    decoration: InputDecoration(
                      hintText: L10n.of(context).clip_note_hint,
                      isDense: true,
                      border: const OutlineInputBorder(),
                    ),
                  ),
                ),
                IconButton(
                  tooltip: L10n.of(context).profile_note_save,
                  icon: const Icon(Icons.check),
                  onPressed: _saveNote,
                ),
              ],
            ),
          )
        else if (note != null && note.isNotEmpty)
          Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(16, 0, 16, 8),
            child: Semantics(
              button: true,
              child: InkWell(
                onTap: () => setState(() => _editing = true),
                child: ConstrainedBox(
                  constraints: const BoxConstraints(
                    minHeight: kTweetTouchTarget,
                  ),
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: Text(
                      note,
                      style: Theme.of(context).textTheme.bodySmall,
                    ),
                  ),
                ),
              ),
            ),
          )
        else
          Align(
            alignment: Alignment.centerLeft,
            child: TextButton.icon(
              onPressed: () => setState(() => _editing = true),
              icon: const Icon(Icons.note_add_outlined, size: 18),
              label: Text(L10n.of(context).clip_note_hint),
            ),
          ),
      ],
    );
  }
}

/// A stored post, rendered from an already-parsed [tweet] where the caller has
/// one — the store parses each blob once, so a scrolling list does not re-parse
/// tens of kilobytes of JSON per tile per build. [content] is the fallback for
/// callers that only hold the raw blob.
class SavedTweetTile extends StatelessWidget {
  final String id;
  final String? content;
  final TweetWithCard? tweet;
  final RedditPost? reddit;

  const SavedTweetTile({
    super.key,
    required this.id,
    this.content,
    this.tweet,
    this.reddit,
  });

  @override
  Widget build(BuildContext context) {
    final stored = parseSavedContent(content);
    final redditPost = reddit ?? stored.reddit;
    if (redditPost != null) {
      return RedditPostCard(post: redditPost, showSourceBadge: true);
    }

    var parsed = tweet ?? stored.tweet;
    if (parsed == null || parsed.idStr == null) {
      // The tweet is probably too big to fit inside the cursor and has been removed from the result set
      return SavedTweetTooLarge(id: id);
    }

    return TweetTile(key: Key(parsed.idStr!), tweet: parsed, clickable: true);
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
