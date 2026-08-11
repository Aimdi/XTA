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
  List<MastodonProfile> _accounts = const [];
  Object? _error;
  var _loading = false;
  var _searched = false;

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
      final accounts = await context
          .read<MastodonClient>()
          .searchAccountsAnywhere(_discoveryInstances(context), query);
      if (!mounted) return;
      setState(() {
        _accounts = accounts;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
        _accounts = const [];
      });
    }
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
      if (_accounts.isEmpty) {
        return Center(child: Text(l10n.plugin_mastodon_no_results));
      }
      return ListView.separated(
        itemCount: _accounts.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final profile = _accounts[index];
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
  var _loading = true;

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
    });
    try {
      final posts = await context.read<MastodonClient>().getTagTimelineAnywhere(
        _discoveryInstances(context),
        widget.tag,
      );
      if (!mounted) return;
      setState(() {
        _posts = posts;
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
          : RefreshIndicator(
              onRefresh: _load,
              child: ListView.builder(
                itemCount: _posts.length,
                itemBuilder: (context, index) => MastodonPostCard(
                  key: ValueKey(_posts[index].id),
                  post: _posts[index],
                  showSourceBadge: false,
                ),
              ),
            ),
    );
  }
}
