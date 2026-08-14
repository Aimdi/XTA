import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/feed_list.dart';

/// Discover Mastodon accounts and trending tags on the reader's instances.
Future<void> showMastodonSearchSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _MastodonSearchSheet(),
  );
}

List<String> _discoveryInstances(BuildContext context) {
  final configured = mastodonConfiguredInstances(
    PrefService.of(context, listen: false),
  );
  final ordered = [...configured, ...kMastodonDefaultInstances];
  final seen = <String>{};
  return [
    for (final candidate in ordered)
      if (normaliseMastodonInstance(candidate) case final instance?
          when seen.add(instance))
        instance,
  ];
}

class _MastodonSearchSheet extends StatefulWidget {
  const _MastodonSearchSheet();

  @override
  State<_MastodonSearchSheet> createState() => _MastodonSearchSheetState();
}

class _MastodonSearchSheetState extends State<_MastodonSearchSheet> {
  final _controller = TextEditingController();
  List<MastodonTrendingTag> _tags = const [];
  MastodonSearchPage _results = const MastodonSearchPage();
  Object? _error;
  var _loading = false;
  var _searched = false;
  var _tab = 0;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _loadTrends();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _loadTrends() async {
    setState(() {
      _loading = true;
      _error = null;
      _searched = false;
    });
    try {
      final tags = await context.read<MastodonClient>().getTrendingTagsAnywhere(
        _discoveryInstances(context),
      );
      if (!mounted) return;
      setState(() {
        _tags = tags;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  Future<void> _search() async {
    final query = _controller.text.trim();
    if (query.isEmpty) {
      await _loadTrends();
      return;
    }

    final direct = normaliseMastodonAcct(query);
    if (direct != null && query.contains('@')) {
      if (!mounted) return;
      Navigator.pop(context);
      await Navigator.push(
        context,
        MaterialPageRoute(builder: (_) => MastodonProfileScreen(acct: direct)),
      );
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _searched = true;
    });
    try {
      final results = await context.read<MastodonClient>().searchAnywhere(
        _discoveryInstances(context),
        query,
      );
      if (!mounted) return;
      setState(() {
        _results = results;
        _tab = _defaultTab(query, results);
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
        _results = const MastodonSearchPage();
      });
    }
  }

  int _defaultTab(String query, MastodonSearchPage results) {
    if (query.startsWith('#') && results.tags.isNotEmpty) return 2;
    if (results.accounts.isNotEmpty) return 0;
    if (results.posts.isNotEmpty) return 1;
    if (results.tags.isNotEmpty) return 2;
    return 0;
  }

  Future<void> _openTag(MastodonTrendingTag tag) async {
    if (!mounted) return;
    Navigator.pop(context);
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => MastodonTagScreen(tag: tag.name)),
    );
  }

  Future<void> _openProfile(MastodonProfile profile) async {
    if (!mounted) return;
    Navigator.pop(context);
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => MastodonProfileScreen(acct: profile.acct),
      ),
    );
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
                autofocus: true,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: l10n.plugin_mastodon_search_hint,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.arrow_forward),
                    tooltip: l10n.plugin_mastodon_search,
                    onPressed: _search,
                  ),
                ),
                onSubmitted: (_) => _search(),
              ),
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
            mastodonErrorMessage(l10n, _error!),
            textAlign: TextAlign.center,
          ),
        ),
      );
    }

    if (_searched) {
      return Column(
        children: [
          _SearchTabs(
            selected: _tab,
            onSelected: (index) => setState(() => _tab = index),
          ),
          const Divider(height: 1),
          Expanded(child: _resultsPane(l10n)),
        ],
      );
    }

    if (_tags.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            l10n.plugin_mastodon_search_hint,
            textAlign: TextAlign.center,
          ),
        ),
      );
    }

    return ListView(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 24),
      children: [
        Text(
          l10n.plugin_mastodon_trending,
          style: Theme.of(
            context,
          ).textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
        ),
        const SizedBox(height: 12),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final tag in _tags)
              ActionChip(
                label: Text('#${tag.name}'),
                onPressed: () => _openTag(tag),
              ),
          ],
        ),
      ],
    );
  }

  Widget _resultsPane(L10n l10n) {
    if (_tab == 1) {
      if (_results.posts.isEmpty) {
        return Center(child: Text(l10n.plugin_mastodon_no_posts));
      }
      return ListView.builder(
        itemCount: _results.posts.length,
        itemBuilder: (context, index) => MastodonPostCard(
          key: ValueKey(_results.posts[index].id),
          post: _results.posts[index],
          showSourceBadge: false,
        ),
      );
    }
    if (_tab == 2) {
      if (_results.tags.isEmpty) {
        return Center(child: Text(l10n.plugin_mastodon_no_hashtags));
      }
      return ListView(
        padding: const EdgeInsets.fromLTRB(16, 12, 16, 24),
        children: [
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final tag in _results.tags)
                ActionChip(
                  label: Text('#${tag.name}'),
                  onPressed: () => _openTag(tag),
                ),
            ],
          ),
        ],
      );
    }
    if (_results.accounts.isEmpty) {
      return Center(child: Text(l10n.plugin_mastodon_no_results));
    }
    return ListView.separated(
      itemCount: _results.accounts.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final profile = _results.accounts[index];
        return ListTile(
          leading: _avatar(context, profile),
          title: Text(
            profile.displayName.isEmpty ? profile.acct : profile.displayName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          subtitle: Text(
            '@${profile.acct}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          onTap: () => _openProfile(profile),
        );
      },
    );
  }

  Widget _avatar(BuildContext context, MastodonProfile profile) {
    final theme = Theme.of(context);
    final avatar = profile.avatarUrl;
    return ClipOval(
      child: avatar == null || avatar.isEmpty
          ? FallbackAvatar(
              seed: profile.acct,
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

class _SearchTabs extends StatelessWidget {
  final int selected;
  final ValueChanged<int> onSelected;

  const _SearchTabs({required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Row(
      children: [
        _SearchTab(
          label: l10n.plugin_mastodon_accounts,
          selected: selected == 0,
          onTap: () => onSelected(0),
        ),
        _SearchTab(
          label: l10n.tweets,
          selected: selected == 1,
          onTap: () => onSelected(1),
        ),
        _SearchTab(
          label: l10n.plugin_mastodon_hashtags,
          selected: selected == 2,
          onTap: () => onSelected(2),
        ),
      ],
    );
  }
}

class _SearchTab extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _SearchTab({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return Expanded(
      child: InkWell(
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.symmetric(vertical: 10),
          child: Text(
            label,
            textAlign: TextAlign.center,
            style: theme.textTheme.labelLarge?.copyWith(
              color: selected
                  ? theme.colorScheme.primary
                  : theme.colorScheme.onSurfaceVariant,
              fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
            ),
          ),
        ),
      ),
    );
  }
}

/// Public posts for one hashtag on the reader's discovery instances.
class MastodonTagScreen extends StatefulWidget {
  final String tag;

  const MastodonTagScreen({super.key, required this.tag});

  @override
  State<MastodonTagScreen> createState() => _MastodonTagScreenState();
}

class _MastodonTagScreenState extends State<MastodonTagScreen> {
  List<MastodonPost> _posts = const [];
  Object? _error;
  String? _instance;
  var _loading = true;
  var _loadingMore = false;
  var _hasMore = true;
  var _backedOff = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _load();
    });
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _backedOff = false;
    });
    try {
      final client = context.read<MastodonClient>();
      final page = await client.firstInstanceThat(
        _discoveryInstances(context),
        (instance) async {
          final posts = await client.getTagTimeline(instance, widget.tag);
          return (instance: instance, posts: posts);
        },
      );
      if (!mounted) return;
      setState(() {
        _posts = page.posts;
        _instance = page.instance;
        _hasMore = page.posts.length >= 30;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  Future<void> _loadMore() async {
    final instance = _instance;
    if (!_hasMore ||
        _loadingMore ||
        _backedOff ||
        instance == null ||
        _posts.isEmpty) {
      return;
    }
    setState(() => _loadingMore = true);
    try {
      final more = await context.read<MastodonClient>().getTagTimeline(
        instance,
        widget.tag,
        maxId: _posts.last.id,
      );
      if (!mounted) return;
      setState(() {
        _posts = appendUniqueMastodonPosts(_posts, more);
        _hasMore = more.length >= 30;
        _loadingMore = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() {
          _loadingMore = false;
          _backedOff = true;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final title = '#${widget.tag.replaceFirst(RegExp(r'^#'), '')}';

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: _loading
          ? const Center(child: CircularProgressIndicator())
          : _error != null
          ? Center(
              child: Padding(
                padding: const EdgeInsets.all(24),
                child: Text(
                  mastodonErrorMessage(l10n, _error!),
                  textAlign: TextAlign.center,
                ),
              ),
            )
          : _posts.isEmpty
          ? Center(child: Text(l10n.plugin_mastodon_no_posts))
          : NotificationListener<ScrollNotification>(
              onNotification: (notification) {
                if (notification is UserScrollNotification) {
                  _backedOff = false;
                }
                if (notification.metrics.pixels >
                    notification.metrics.maxScrollExtent - 1200) {
                  _loadMore();
                }
                return false;
              },
              child: RefreshIndicator(
                onRefresh: _load,
                child: FeedListView(
                  itemCount: _posts.length + (_loadingMore ? 1 : 0),
                  itemBuilder: (context, index) {
                    if (index >= _posts.length) {
                      return const Padding(
                        padding: EdgeInsets.all(16),
                        child: Center(child: CircularProgressIndicator()),
                      );
                    }
                    return MastodonPostCard(
                      key: ValueKey(_posts[index].id),
                      post: _posts[index],
                      showSourceBadge: false,
                    );
                  },
                ),
              ),
            ),
    );
  }
}
