import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_client.dart';
import 'package:xta/plugins/pixiv/pixiv_grid.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_screen.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_links.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/pixiv/pixiv_user_screen.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';

/// Tag / keyword / user search — Pixez's second home.
class PixivSearchScreen extends StatefulWidget {
  final String? initialQuery;

  const PixivSearchScreen({super.key, this.initialQuery});

  @override
  State<PixivSearchScreen> createState() => _PixivSearchScreenState();
}

class _PixivSearchScreenState extends State<PixivSearchScreen>
    with SingleTickerProviderStateMixin {
  late final TextEditingController _query;
  late final TabController _tabs;
  late final PixivIllustListStore _illusts;
  List<PixivUser> _users = const [];
  String? _usersNext;
  Object? _usersError;
  var _usersLoading = false;
  var _searched = false;
  var _searchTarget = 'partial_match_for_tags';
  var _sort = 'date_desc';
  List<PixivTrendTag> _trending = const [];
  List<PixivUser> _recommendedUsers = const [];
  List<PixivIllust> _popular = const [];
  List<PixivTrendTag> _suggestions = const [];
  Timer? _suggestDebounce;

  static const _searchTargets = [
    'partial_match_for_tags',
    'exact_match_for_tags',
    'title_and_caption',
  ];
  static const _sorts = ['date_desc', 'popular_desc'];

  @override
  void initState() {
    super.initState();
    _query = TextEditingController(text: widget.initialQuery ?? '');
    _tabs = TabController(length: 2, vsync: this);
    _illusts = PixivIllustListStore(({nextUrl}) {
      return context.read<PixivClient>().searchIllust(
        _query.text,
        searchTarget: _searchTarget,
        sort: _sort,
        nextUrl: nextUrl,
      );
    }, filter: context.read<PixivMuteStore>().filter);
    WidgetsBinding.instance.addPostFrameCallback((_) async {
      if (!mounted) return;
      await context.read<PixivSearchHistoryStore>().load();
      if (!mounted) return;
      if ((widget.initialQuery ?? '').trim().isNotEmpty) {
        _search();
      } else {
        _loadTrending();
      }
    });
  }

  @override
  void dispose() {
    _suggestDebounce?.cancel();
    _query.dispose();
    _tabs.dispose();
    _illusts.destroy();
    super.dispose();
  }

  Future<void> _search() async {
    final word = _query.text.trim();
    if (word.isEmpty) {
      return;
    }
    final link = parsePixivLink(word);
    if (link != null) {
      await _openLink(link);
      return;
    }

    final client = context.read<PixivClient>();
    FocusScope.of(context).unfocus();
    _suggestDebounce?.cancel();
    setState(() {
      _searched = true;
      _suggestions = const [];
      _popular = const [];
      _usersError = null;
      _usersLoading = true;
      _users = const [];
      _usersNext = null;
    });
    await context.read<PixivSearchHistoryStore>().add(word);
    if (!mounted) return;
    // Start users while illusts refresh; always clear the users spinner (a soft
    // refresh throw used to leave `_usersLoading` stuck forever).
    final usersFuture = client.searchUsers(word);
    _loadPopularPreview(client, word);
    try {
      await _illusts.refresh();
      if (!mounted) return;
      final page = await usersFuture;
      setState(() {
        _users = page.users;
        _usersNext = page.nextUrl;
        _usersError = null;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _usersError = e);
    } finally {
      if (mounted) {
        setState(() => _usersLoading = false);
      }
    }
  }

  Future<void> _loadTrending() async {
    if (_trending.isNotEmpty && _recommendedUsers.isNotEmpty) return;
    final client = context.read<PixivClient>();
    try {
      List<PixivTrendTag>? tags;
      List<PixivUser>? users;
      if (_trending.isEmpty) {
        tags = await client.trendingTags();
      }
      if (_recommendedUsers.isEmpty) {
        users = (await client.recommendedUsers()).users;
      }
      if (!mounted) return;
      final mute = context.read<PixivMuteStore>().state;
      setState(() {
        if (tags != null) {
          _trending = [
            for (final tag in tags)
              if (!mute.tags.contains(tag.name.toLowerCase()))
                switch (tag.illust) {
                  final illust? when mute.isMuted(illust) => PixivTrendTag(
                    name: tag.name,
                    translatedName: tag.translatedName,
                  ),
                  _ => tag,
                },
          ];
        }
        if (users != null) {
          _recommendedUsers = users;
        }
      });
    } catch (_) {
      // The landing page works without Discover chrome; history still shows.
    }
  }

  /// The community's free stand-in for Premium `popular_desc`: one page of the
  /// most popular results, drawn as a strip above the date-sorted grid.
  Future<void> _loadPopularPreview(PixivClient client, String word) async {
    if (_sort == 'popular_desc') return;
    try {
      final page = await client.popularPreview(
        word,
        searchTarget: _searchTarget,
      );
      if (!mounted || _query.text.trim() != word) return;
      setState(
        () => _popular = context.read<PixivMuteStore>().filter(page.illusts),
      );
    } catch (_) {
      // Best-effort — the main grid is the answer, this is garnish.
    }
  }

  void _onQueryChanged(String text) {
    _suggestDebounce?.cancel();
    final word = text.trim();
    if (word.isEmpty || parsePixivLink(word) != null) {
      if (_suggestions.isNotEmpty) setState(() => _suggestions = const []);
      return;
    }
    _suggestDebounce = Timer(const Duration(milliseconds: 300), () async {
      try {
        final tags = await context.read<PixivClient>().autocomplete(word);
        if (!mounted || _query.text.trim() != word) return;
        setState(() => _suggestions = tags);
      } catch (_) {
        // Typing goes on; suggestions are a convenience, not a gate.
      }
    });
  }

  void _searchFor(String word) {
    _query.text = word;
    _search();
  }

  Future<void> _openLink(PixivLinkRef link) async {
    final navigator = Navigator.of(context);
    if (link case PixivUserLinkRef(:final id)) {
      await navigator.push(
        MaterialPageRoute(builder: (_) => PixivUserScreen(userId: id)),
      );
      return;
    }

    final client = context.read<PixivClient>();
    final messenger = ScaffoldMessenger.of(context);
    final message = L10n.of(context).plugin_pixiv_open_link_failed;
    try {
      final illust = await client.illustDetail(link.id);
      if (!mounted) return;
      await navigator.push(
        MaterialPageRoute(builder: (_) => PixivIllustScreen(illust: illust)),
      );
    } catch (_) {
      if (mounted) {
        messenger.showSnackBar(SnackBar(content: Text(message)));
      }
    }
  }

  Future<void> _loadMoreUsers() async {
    if (_usersLoading || _usersNext == null || _usersNext!.isEmpty) {
      return;
    }
    setState(() => _usersLoading = true);
    try {
      final page = await context.read<PixivClient>().searchUsers(
        _query.text,
        nextUrl: _usersNext,
      );
      if (!mounted) return;
      setState(() {
        _users = [..._users, ...page.users];
        _usersNext = page.nextUrl;
        _usersLoading = false;
      });
    } catch (_) {
      if (mounted) setState(() => _usersLoading = false);
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: TextField(
          controller: _query,
          textInputAction: TextInputAction.search,
          autofocus: (widget.initialQuery ?? '').isEmpty,
          decoration: InputDecoration(
            hintText: l10n.plugin_pixiv_search_hint,
            border: InputBorder.none,
          ),
          onChanged: _onQueryChanged,
          onSubmitted: (_) => _search(),
        ),
        actions: [
          IconButton(
            tooltip: l10n.search,
            onPressed: _search,
            icon: const Icon(Icons.search),
          ),
        ],
        bottom: TabBar(
          controller: _tabs,
          tabs: [
            Tab(text: l10n.plugin_pixiv_tab_illusts),
            Tab(text: l10n.plugin_pixiv_tab_users),
          ],
        ),
      ),
      body: _suggestions.isEmpty
          ? TabBarView(
              controller: _tabs,
              children: [_illustTab(l10n), _usersTab(l10n)],
            )
          : _suggestionList(),
    );
  }

  Widget _illustTab(L10n l10n) {
    if (!_searched) {
      return _searchHome(l10n);
    }

    return Column(
      children: [
        _searchControls(l10n),
        if (_popular.isNotEmpty) _popularStrip(l10n),
        Expanded(
          child: ScopedBuilder<PixivIllustListStore, List<PixivIllust>>(
            store: _illusts,
            onLoading: (_) {
              if (_illusts.state.isNotEmpty) {
                return PixivIllustGrid(
                  illusts: _illusts.state,
                  onRefresh: _illusts.refresh,
                  loadingMore: _illusts.loadingMore,
                );
              }
              return const Center(child: CircularProgressIndicator());
            },
            onError: (context, error) => Padding(
              padding: const EdgeInsets.all(24),
              child: FullPageErrorWidget(
                error: error,
                stackTrace: null,
                prefix: pixivErrorMessage(l10n, error ?? Exception()),
                onRetry: _search,
              ),
            ),
            onState: (context, illusts) {
              if (illusts.isEmpty) {
                return Center(
                  child: Text(
                    l10n.plugin_pixiv_search_empty,
                    textAlign: TextAlign.center,
                  ),
                );
              }
              return NotificationListener<ScrollNotification>(
                onNotification: (n) {
                  if (n.metrics.pixels > n.metrics.maxScrollExtent - 1400) {
                    _illusts.loadMore();
                  }
                  return false;
                },
                child: PixivIllustGrid(
                  illusts: illusts,
                  onRefresh: _illusts.refresh,
                  loadingMore: _illusts.loadingMore,
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _searchHome(L10n l10n) {
    return ListView(
      padding: const EdgeInsets.all(24),
      children: [
        ScopedBuilder<PixivSearchHistoryStore, List<String>>.transition(
          store: context.read<PixivSearchHistoryStore>(),
          onState: (context, history) => Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                l10n.plugin_pixiv_search_prompt,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 24),
              Text(
                l10n.plugin_pixiv_search_history,
                style: Theme.of(context).textTheme.titleSmall,
              ),
              const SizedBox(height: 8),
              if (history.isEmpty)
                Text(l10n.plugin_pixiv_search_history_empty)
              else
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    for (final query in history)
                      GestureDetector(
                        onLongPress: () => context
                            .read<PixivSearchHistoryStore>()
                            .remove(query),
                        child: ActionChip(
                          label: Text(query),
                          onPressed: () => _searchFor(query),
                        ),
                      ),
                  ],
                ),
            ],
          ),
        ),
        if (_recommendedUsers.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text(
            l10n.plugin_pixiv_recommended_users,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          _recommendedUsersStrip(),
        ],
        if (_trending.isNotEmpty) ...[
          const SizedBox(height: 24),
          Text(
            l10n.plugin_pixiv_trending_title,
            style: Theme.of(context).textTheme.titleSmall,
          ),
          const SizedBox(height: 8),
          _trendingGrid(),
        ],
      ],
    );
  }

  /// Flare Discover "users" — horizontal creators before trending tags.
  Widget _recommendedUsersStrip() {
    return SizedBox(
      height: 88,
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: _recommendedUsers.length,
        separatorBuilder: (_, _) => const SizedBox(width: 12),
        itemBuilder: (context, index) {
          final user = _recommendedUsers[index];
          final theme = Theme.of(context);
          final avatar = user.avatarUrl;
          return InkWell(
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => PixivUserScreen(userId: user.id),
              ),
            ),
            child: SizedBox(
              width: 72,
              child: Column(
                children: [
                  ClipOval(
                    child: avatar == null || avatar.isEmpty
                        ? FallbackAvatar(
                            seed: '${user.id}',
                            displayName: user.name,
                            size: 56,
                            accent: theme.colorScheme.primary,
                          )
                        : SizedBox(
                            width: 56,
                            height: 56,
                            child: PixivNetworkImage(
                              url: avatar,
                              fit: BoxFit.cover,
                              cacheWidth:
                                  (56 * MediaQuery.devicePixelRatioOf(context))
                                      .ceil(),
                            ),
                          ),
                  ),
                  const SizedBox(height: 6),
                  Text(
                    user.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.labelSmall,
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }

  /// The tappable image grid every Pixiv client opens search with — each
  /// trending tag drawn over the illust Pixiv picked to represent it.
  Widget _trendingGrid() {
    return GridView.builder(
      shrinkWrap: true,
      physics: const NeverScrollableScrollPhysics(),
      gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
        crossAxisCount: 3,
        mainAxisSpacing: 4,
        crossAxisSpacing: 4,
      ),
      itemCount: _trending.length,
      itemBuilder: (context, index) {
        final tag = _trending[index];
        final illust = tag.illust;
        return InkWell(
          onTap: () => _searchFor(tag.name),
          child: Stack(
            fit: StackFit.expand,
            children: [
              if (illust != null)
                ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: PixivNetworkImage(
                    url: illust.thumbnailUrl,
                    fit: BoxFit.cover,
                    cacheWidth: (140 * MediaQuery.devicePixelRatioOf(context))
                        .ceil(),
                  ),
                )
              else
                DecoratedBox(
                  decoration: BoxDecoration(
                    color: Theme.of(
                      context,
                    ).colorScheme.surfaceContainerHighest,
                    borderRadius: BorderRadius.circular(8),
                  ),
                ),
              Align(
                alignment: Alignment.bottomCenter,
                child: Container(
                  width: double.infinity,
                  padding: const EdgeInsets.symmetric(
                    horizontal: 6,
                    vertical: 4,
                  ),
                  decoration: BoxDecoration(
                    borderRadius: const BorderRadius.vertical(
                      bottom: Radius.circular(8),
                    ),
                    color: Colors.black.withValues(alpha: 0.55),
                  ),
                  child: Text(
                    '#${tag.name}',
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: const TextStyle(color: Colors.white, fontSize: 12),
                  ),
                ),
              ),
            ],
          ),
        );
      },
    );
  }

  Widget _popularStrip(L10n l10n) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(12, 8, 12, 4),
          child: Text(
            l10n.plugin_pixiv_popular_title,
            style: Theme.of(context).textTheme.titleSmall,
          ),
        ),
        SizedBox(
          height: 110,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 12),
            itemCount: _popular.length,
            separatorBuilder: (_, _) => const SizedBox(width: 6),
            itemBuilder: (context, index) {
              final illust = _popular[index];
              return InkWell(
                onTap: () => Navigator.push(
                  context,
                  MaterialPageRoute(
                    builder: (_) => PixivIllustScreen(illust: illust),
                  ),
                ),
                child: ClipRRect(
                  borderRadius: BorderRadius.circular(8),
                  child: SizedBox(
                    width: 110,
                    child: PixivNetworkImage(
                      url: illust.thumbnailUrl,
                      fit: BoxFit.cover,
                      cacheWidth: (110 * MediaQuery.devicePixelRatioOf(context))
                          .ceil(),
                    ),
                  ),
                ),
              );
            },
          ),
        ),
      ],
    );
  }

  Widget _suggestionList() {
    return ListView.builder(
      itemCount: _suggestions.length,
      itemBuilder: (context, index) {
        final tag = _suggestions[index];
        return ListTile(
          leading: const Icon(Icons.tag),
          title: Text(tag.name),
          subtitle: tag.translatedName == null
              ? null
              : Text(tag.translatedName!),
          onTap: () => _searchFor(tag.name),
        );
      },
    );
  }

  Widget _searchControls(L10n l10n) {
    return SingleChildScrollView(
      scrollDirection: Axis.horizontal,
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 4),
      child: Row(
        children: [
          for (final target in _searchTargets)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: ChoiceChip(
                label: Text(_targetLabel(l10n, target)),
                selected: _searchTarget == target,
                onSelected: (_) => _changeSearchTarget(target),
              ),
            ),
          const SizedBox(width: 8),
          for (final sort in _sorts)
            Padding(
              padding: const EdgeInsets.only(right: 6),
              child: ChoiceChip(
                label: Text(_sortLabel(l10n, sort)),
                selected: _sort == sort,
                onSelected: (_) => _changeSort(sort),
              ),
            ),
        ],
      ),
    );
  }

  Future<void> _changeSearchTarget(String target) async {
    if (target == _searchTarget) return;
    setState(() => _searchTarget = target);
    if (_searched) await _search();
  }

  Future<void> _changeSort(String sort) async {
    if (sort == _sort) return;
    setState(() => _sort = sort);
    if (_searched) await _search();
  }

  String _targetLabel(L10n l10n, String target) => switch (target) {
    'exact_match_for_tags' => l10n.plugin_pixiv_search_target_exact,
    'title_and_caption' => l10n.plugin_pixiv_search_target_title,
    _ => l10n.plugin_pixiv_search_target_partial,
  };

  String _sortLabel(L10n l10n, String sort) => switch (sort) {
    'popular_desc' => l10n.plugin_pixiv_search_sort_popular,
    _ => l10n.plugin_pixiv_search_sort_newest,
  };

  Widget _usersTab(L10n l10n) {
    if (!_searched) {
      return _searchHome(l10n);
    }
    if (_usersLoading && _users.isEmpty) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_usersError != null && _users.isEmpty) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: FullPageErrorWidget(
          error: _usersError,
          stackTrace: null,
          prefix: pixivErrorMessage(l10n, _usersError!),
          onRetry: _search,
        ),
      );
    }
    if (_users.isEmpty) {
      return Center(
        child: Text(
          l10n.plugin_pixiv_search_empty,
          textAlign: TextAlign.center,
        ),
      );
    }

    final theme = Theme.of(context);
    return NotificationListener<ScrollNotification>(
      onNotification: (n) {
        if (n.metrics.pixels > n.metrics.maxScrollExtent - 400) {
          _loadMoreUsers();
        }
        return false;
      },
      child: ListView.separated(
        itemCount: _users.length + (_usersLoading ? 1 : 0),
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          if (index >= _users.length) {
            return const Padding(
              padding: EdgeInsets.all(16),
              child: Center(child: CircularProgressIndicator()),
            );
          }
          final user = _users[index];
          final avatar = user.avatarUrl;
          return ListTile(
            leading: ClipOval(
              child: avatar == null
                  ? FallbackAvatar(
                      seed: '${user.id}',
                      displayName: user.name,
                      size: 44,
                      accent: theme.colorScheme.primary,
                    )
                  : SizedBox(
                      width: 44,
                      height: 44,
                      child: PixivNetworkImage(
                        url: avatar,
                        fit: BoxFit.cover,
                        cacheWidth:
                            (44 * MediaQuery.devicePixelRatioOf(context))
                                .ceil(),
                        cacheHeight:
                            (44 * MediaQuery.devicePixelRatioOf(context))
                                .ceil(),
                      ),
                    ),
            ),
            title: Text(user.name),
            subtitle: Text('@${user.account}'),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => PixivUserScreen(userId: user.id),
              ),
            ),
          );
        },
      ),
    );
  }
}
