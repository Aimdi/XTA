import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
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
  List<BlueskyPost> _posts = const [];
  Object? _error;
  var _loading = false;
  var _searched = false;

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
    }
  }

  @override
  void dispose() {
    _controller.dispose();
    _tabs.dispose();
    super.dispose();
  }

  Future<void> _openActor(String actor) async {
    if (!mounted) return;
    Navigator.pop(context);
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => BlueskyProfileScreen(actor: actor)),
    );
  }

  Future<void> _search() async {
    final query = _controller.text.trim();
    if (query.isEmpty) {
      return;
    }

    // Exact handle, DID, or profile URL — open that profile without searching.
    if (_tabs.index == 0) {
      final direct = normaliseBlueskyHandle(query);
      if (direct != null) {
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
        setState(() {
          _people = results;
          _loading = false;
        });
      } else {
        final q = query.startsWith('#') ? query.substring(1) : query;
        final page = await client.searchPosts(q, limit: 20);
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
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
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
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            _tabs.index == 0
                ? l10n.plugin_bluesky_search_hint
                : l10n.plugin_bluesky_search_posts_hint,
            textAlign: TextAlign.center,
          ),
        ),
      );
    }

    if (_tabs.index == 0) {
      if (_people.isEmpty) {
        return Center(child: Text(l10n.plugin_bluesky_no_results));
      }
      return ListView.separated(
        itemCount: _people.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final profile = _people[index];
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
            onTap: () => _open(profile),
          );
        },
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
