import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_import_follows_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_list_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_import_starter_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/plugins/plugin_search_history.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

enum BlueskySearchTab { people, posts }

/// Search people and posts on the public AppView.
Future<void> showBlueskySearchSheet(
  BuildContext context, {
  String? initialQuery,
  BlueskySearchTab initialTab = BlueskySearchTab.people,
}) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (sheetContext) =>
        _BlueskySearchSheet(initialQuery: initialQuery, initialTab: initialTab),
  );
}

class _BlueskySearchSheet extends StatefulWidget {
  final String? initialQuery;
  final BlueskySearchTab initialTab;

  const _BlueskySearchSheet({
    this.initialQuery,
    this.initialTab = BlueskySearchTab.people,
  });

  @override
  State<_BlueskySearchSheet> createState() => _BlueskySearchSheetState();
}

class _BlueskySearchSheetState extends State<_BlueskySearchSheet>
    with SingleTickerProviderStateMixin {
  late final TextEditingController _controller;
  late final TabController _tabs;
  List<BlueskyProfile> _people = const [];
  List<BlueskyProfile> _suggestions = const [];
  List<BlueskyPost> _posts = const [];
  Object? _error;
  var _loading = false;
  var _searched = false;
  var _suggestionsLoading = false;

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    _tabs = TabController(
      length: 2,
      vsync: this,
      initialIndex: widget.initialTab == BlueskySearchTab.posts ? 1 : 0,
    );
    if ((widget.initialQuery ?? '').trim().isNotEmpty) {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _search();
      });
    } else {
      WidgetsBinding.instance.addPostFrameCallback((_) {
        if (mounted) _loadSuggestions();
      });
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _remember(String query) async {
    final prefs = PrefService.of(context);
    await rememberPluginSearch(prefs, optionPluginBlueskySearchHistory, query);
  }

  Future<void> _openActor(String actor) async {
    if (!mounted) return;
    Navigator.pop(context);
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => BlueskyProfileScreen(actor: actor)),
    );
  }

  Future<void> _openImport(Widget page) async {
    if (!mounted) return;
    Navigator.pop(context);
    await Navigator.push(
      context,
      MaterialPageRoute<void>(builder: (_) => page),
    );
  }

  Future<void> _loadSuggestions() async {
    setState(() {
      _suggestionsLoading = true;
      _error = null;
    });
    try {
      final results = await context.read<BlueskyClient>().getSuggestions(
        limit: 20,
      );
      if (!mounted) return;
      setState(() {
        _suggestions = results;
        _suggestionsLoading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _suggestionsLoading = false;
      });
    }
  }

  Future<void> _search() async {
    final query = _controller.text.trim();
    if (query.isEmpty) {
      return;
    }

    if (_tabs.index == 0) {
      final direct = normaliseBlueskyHandle(query);
      if (direct != null) {
        await _remember(query);
        await _openActor(direct);
        return;
      }
    }

    setState(() {
      _loading = true;
      _error = null;
      _searched = true;
    });

    try {
      final client = context.read<BlueskyClient>();
      if (_tabs.index == 0) {
        final results = await client.searchActors(query, limit: 20);
        if (!mounted) return;
        await _remember(query);
        if (!mounted) return;
        setState(() {
          _people = results;
          _loading = false;
        });
      } else {
        final q = query.startsWith('#') ? query.substring(1) : query;
        final page = await client.searchPosts(q, limit: 20);
        if (!mounted) return;
        await _remember(query);
        if (!mounted) return;
        setState(() {
          _posts = page.posts;
          _loading = false;
        });
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
        _people = const [];
        _posts = const [];
      });
    }
  }

  Future<void> _open(BlueskyProfile profile) async {
    final actor = profile.did.isNotEmpty ? profile.did : profile.handle;
    await _openActor(actor);
  }

  Future<void> _toggleFollow(BlueskyProfile profile) async {
    final accounts = context.read<BlueskyAccountsStore>();
    if (accounts.follows(profile.handle)) {
      await accounts.remove(profile.handle);
    } else {
      await accounts.add(profile.toAccount());
    }
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final bottom = MediaQuery.viewInsetsOf(context).bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.75,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: TextField(
                controller: _controller,
                autofocus: widget.initialQuery == null,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: _tabs.index == 0
                      ? l10n.plugin_bluesky_search_hint
                      : l10n.plugin_bluesky_search_posts_hint,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.arrow_forward),
                    tooltip: l10n.plugin_bluesky_search,
                    onPressed: _search,
                  ),
                ),
                onSubmitted: (_) => _search(),
              ),
            ),
            TabBar(
              controller: _tabs,
              onTap: (_) {
                setState(() {});
                if (_searched && _controller.text.trim().isNotEmpty) {
                  _search();
                }
              },
              tabs: [
                Tab(text: l10n.plugin_bluesky_search_people),
                Tab(text: l10n.plugin_bluesky_search_posts),
              ],
            ),
            Expanded(child: _body(l10n)),
          ],
        ),
      ),
    );
  }

  Widget _body(L10n l10n) {
    if (_loading || (_suggestionsLoading && !_searched && _tabs.index == 0)) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null && (_searched || _suggestions.isEmpty)) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            blueskyErrorMessage(l10n, _error!),
            textAlign: TextAlign.center,
          ),
        ),
      );
    }
    if (!_searched) {
      if (_tabs.index == 1) {
        return Center(
          child: Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              l10n.plugin_bluesky_search_posts_hint,
              textAlign: TextAlign.center,
            ),
          ),
        );
      }
      return _peopleLanding(l10n);
    }

    if (_tabs.index == 0) {
      if (_people.isEmpty) {
        return Center(child: Text(l10n.plugin_bluesky_no_results));
      }
      return ListView.separated(
        itemCount: _people.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) => _personTile(_people[index], l10n),
      );
    }

    if (_posts.isEmpty) {
      return Center(child: Text(l10n.plugin_bluesky_no_results));
    }
    return ListView.builder(
      itemCount: _posts.length,
      itemBuilder: (context, index) => BlueskyPostCard(post: _posts[index]),
    );
  }

  Widget _peopleLanding(L10n l10n) {
    final recent = readPluginSearchHistory(
      PrefService.of(context),
      optionPluginBlueskySearchHistory,
    );

    return ListView(
      children: [
        if (recent.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Text(
              l10n.plugin_bluesky_recent_searches,
              style: Theme.of(
                context,
              ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
            child: Wrap(
              spacing: 8,
              children: [
                for (final item in recent.take(8))
                  ActionChip(
                    label: Text(item),
                    onPressed: () {
                      _controller.text = item;
                      _search();
                    },
                  ),
              ],
            ),
          ),
        ],
        ListTile(
          leading: const Icon(Icons.group_add_outlined),
          title: Text(l10n.plugin_bluesky_import_following),
          onTap: () => _openImport(const BlueskyImportFollowsScreen()),
        ),
        ListTile(
          leading: const Icon(Icons.list_alt_outlined),
          title: Text(l10n.plugin_bluesky_import_list),
          onTap: () => _openImport(const BlueskyImportListScreen()),
        ),
        ListTile(
          leading: const Icon(Icons.auto_awesome_outlined),
          title: Text(l10n.plugin_bluesky_import_starter),
          onTap: () => _openImport(const BlueskyImportStarterPackScreen()),
        ),
        if (_suggestions.isNotEmpty) ...[
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 4),
            child: Text(
              l10n.plugin_bluesky_suggested,
              style: Theme.of(
                context,
              ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
          for (final profile in _suggestions) ...[
            _personTile(profile, l10n),
            const Divider(height: 1),
          ],
        ] else
          Padding(
            padding: const EdgeInsets.all(24),
            child: Text(
              l10n.plugin_bluesky_search_hint,
              textAlign: TextAlign.center,
            ),
          ),
      ],
    );
  }

  Widget _personTile(BlueskyProfile profile, L10n l10n) {
    final following = context.read<BlueskyAccountsStore>().follows(
      profile.handle,
    );
    return ListTile(
      leading: _avatar(context, profile),
      title: Text(
        profile.displayName,
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      subtitle: Text(
        '@${profile.handle}',
        maxLines: 1,
        overflow: TextOverflow.ellipsis,
      ),
      trailing: TextButton(
        onPressed: () => _toggleFollow(profile),
        child: Text(
          following ? l10n.plugin_bluesky_unfollow : l10n.plugin_bluesky_follow,
        ),
      ),
      onTap: () => _open(profile),
    );
  }

  Widget _avatar(BuildContext context, BlueskyProfile profile) {
    final theme = Theme.of(context);
    final avatar = profile.avatarUrl;
    return ClipOval(
      child: avatar == null
          ? FallbackAvatar(
              seed: profile.handle,
              displayName: profile.displayName,
              size: 40,
              accent: theme.colorScheme.primary,
            )
          : ExtendedImage.network(
              avatar,
              width: 40,
              height: 40,
              fit: BoxFit.cover,
              cacheWidth: (40 * MediaQuery.devicePixelRatioOf(context)).ceil(),
            ),
    );
  }
}
