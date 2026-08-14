import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/user.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

String mastodonErrorMessage(L10n l10n, Object error) {
  if (error is! MastodonException) {
    return l10n.plugin_mastodon_error_network;
  }
  return switch (error.kind) {
    MastodonErrorKind.notConfigured => l10n.plugin_mastodon_not_configured,
    MastodonErrorKind.network => l10n.plugin_mastodon_error_network,
    MastodonErrorKind.notFound => l10n.plugin_mastodon_error_not_found,
    MastodonErrorKind.rateLimited => l10n.plugin_mastodon_error_rate_limited,
    MastodonErrorKind.unauthorized => l10n.plugin_mastodon_error_unauthorized,
    MastodonErrorKind.badResponse => l10n.plugin_mastodon_error_response,
  };
}

/// One Fediverse profile and a page of its posts, via the home instance.
class MastodonProfileScreen extends StatefulWidget {
  final String acct;

  const MastodonProfileScreen({super.key, required this.acct});

  @override
  State<MastodonProfileScreen> createState() => _MastodonProfileScreenState();
}

class _MastodonTabFeed {
  List<MastodonPost> posts = const [];
  var loaded = false;
  var loading = false;
}

class _MastodonProfileScreenState extends State<MastodonProfileScreen> {
  MastodonProfile? _profile;
  String? _instance;
  Object? _error;
  bool _loading = true;
  var _tab = PluginProfileFeedTab.posts;
  final _feeds = {
    for (final tab in PluginProfileFeedTab.values) tab: _MastodonTabFeed(),
  };

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      for (final feed in _feeds.values) {
        feed.posts = const [];
        feed.loaded = false;
        feed.loading = false;
      }
    });

    final prefs = PrefService.of(context, listen: false);
    final client = context.read<MastodonClient>();
    try {
      final candidates = mastodonInstanceCandidates(
        widget.acct,
        configured: mastodonConfiguredInstances(prefs),
      );
      final found = await client.profileAnywhere(candidates, widget.acct);
      if (!mounted) {
        return;
      }
      setState(() {
        _profile = found.profile;
        _instance = found.instance;
        _feeds[PluginProfileFeedTab.posts]!.posts = found.posts;
        _feeds[PluginProfileFeedTab.posts]!.loaded = true;
        _loading = false;
      });
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  Future<void> _selectTab(PluginProfileFeedTab tab) async {
    if (_tab == tab) {
      return;
    }
    setState(() => _tab = tab);
    final feed = _feeds[tab]!;
    if (!feed.loaded && !feed.loading) {
      await _loadTab(tab);
    }
  }

  Future<void> _loadTab(PluginProfileFeedTab tab) async {
    final profile = _profile;
    final instance = _instance;
    if (profile == null || instance == null) {
      return;
    }
    final feed = _feeds[tab]!;
    setState(() => feed.loading = true);
    final client = context.read<MastodonClient>();
    try {
      final raw = await client.getStatuses(
        instance,
        profile.id,
        limit: 40,
        excludeReplies: tab != PluginProfileFeedTab.replies,
        onlyMedia: tab == PluginProfileFeedTab.media,
      );
      final posts = tab == PluginProfileFeedTab.replies
          ? [
              for (final post in raw)
                if (post.isReply) post,
            ]
          : raw;
      if (!mounted) {
        return;
      }
      setState(() {
        feed.posts = posts;
        feed.loaded = true;
        feed.loading = false;
      });
    } catch (_) {
      if (mounted) {
        setState(() => feed.loading = false);
      }
    }
  }

  Future<void> _toggleFollow(MastodonProfile profile) async {
    final accounts = context.read<MastodonAccountsStore>();
    final feed = context.read<MastodonFeedStore>();

    if (accounts.follows(profile.acct)) {
      await accounts.remove(profile.acct);
    } else {
      await accounts.add(profile.toAccount());
    }
    if (mounted) {
      await feed.refresh();
      setState(() {});
    }
  }

  Future<void> _addToGroup(MastodonProfile profile) async {
    final accounts = context.read<MastodonAccountsStore>();
    final groupsModel = context.read<GroupsModel>();
    if (!accounts.follows(profile.acct)) {
      await accounts.add(profile.toAccount());
    }
    if (!mounted) return;
    final user = subscriptionOf(profile.toAccount());
    final groups = await groupsModel.listGroupsForUser(user.id);
    if (!mounted) return;
    await pickUserGroups(
      context,
      user: user,
      followed: true,
      groupsForUser: groups,
    );
    if (mounted) setState(() {});
  }

  @override
  Widget build(BuildContext context) {
    final title = _profile?.acct ?? widget.acct;

    return Scaffold(
      appBar: AppBar(title: Text('@$title')),
      body: _body(context),
    );
  }

  Widget _body(BuildContext context) {
    final l10n = L10n.of(context);

    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    final error = _error;
    if (error != null) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: mastodonErrorMessage(l10n, error),
          onRetry: _load,
        ),
      );
    }

    final profile = _profile!;
    final following = context.read<MastodonAccountsStore>().follows(
      profile.acct,
    );
    final feed = _feeds[_tab]!;
    final posts = feed.posts;

    return FeedListView(
      padding: const EdgeInsets.only(bottom: 24),
      itemCount: 2 + posts.length + (feed.loading ? 1 : 0),
      itemBuilder: (context, index) {
        if (index == 0) {
          return Padding(
            padding: const EdgeInsets.all(16),
            child: MastodonProfileCard(
              profile: profile,
              following: following,
              onFollowToggle: () => _toggleFollow(profile),
              onAddToGroup: () => _addToGroup(profile),
            ),
          );
        }
        if (index == 1) {
          return PluginProfileTabBar(selected: _tab, onSelected: _selectTab);
        }
        final postIndex = index - 2;
        if (postIndex < posts.length) {
          final post = posts[postIndex];
          return MastodonPostCard(
            key: ValueKey(post.id),
            post: post,
            showSourceBadge: false,
          );
        }
        return const Padding(
          padding: EdgeInsets.all(16),
          child: Center(child: CircularProgressIndicator()),
        );
      },
    );
  }
}

class MastodonProfileCard extends StatelessWidget {
  final MastodonProfile profile;
  final bool following;
  final VoidCallback? onFollowToggle;
  final VoidCallback? onAddToGroup;

  const MastodonProfileCard({
    super.key,
    required this.profile,
    required this.following,
    this.onFollowToggle,
    this.onAddToGroup,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final numbers = NumberFormat.compact();
    final avatar = profile.avatarUrl;

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            ClipOval(
              child: avatar == null
                  ? FallbackAvatar(
                      seed: profile.acct,
                      displayName: profile.displayName,
                      size: 64,
                      accent: theme.colorScheme.primary,
                    )
                  : ExtendedImage.network(
                      avatar,
                      width: 64,
                      height: 64,
                      fit: BoxFit.cover,
                      cacheWidth: (64 * MediaQuery.devicePixelRatioOf(context))
                          .ceil(),
                    ),
            ),
            const SizedBox(width: 14),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(
                    profile.displayName,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleLarge!.copyWith(
                      fontWeight: FontWeight.w700,
                    ),
                  ),
                  Text(
                    '@${profile.acct}',
                    style: theme.textTheme.bodyMedium!.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        if (profile.note.trim().isNotEmpty) ...[
          const SizedBox(height: 14),
          Text(profile.note.trim(), style: theme.textTheme.bodyMedium),
        ],
        const SizedBox(height: 14),
        Wrap(
          spacing: 18,
          runSpacing: 6,
          children: [
            _count(
              context,
              numbers.format(profile.followersCount),
              l10n.followers,
            ),
            _count(
              context,
              numbers.format(profile.followingCount),
              l10n.following,
            ),
            _count(context, numbers.format(profile.statusesCount), l10n.tweets),
          ],
        ),
        if (onFollowToggle != null || onAddToGroup != null) ...[
          const SizedBox(height: 18),
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              if (onFollowToggle != null)
                FilledButton.tonalIcon(
                  onPressed: onFollowToggle,
                  icon: Icon(
                    following
                        ? Icons.person_remove_alt_1
                        : Icons.person_add_alt,
                  ),
                  label: Text(
                    following
                        ? l10n.plugin_mastodon_unfollow
                        : l10n.plugin_mastodon_follow,
                  ),
                ),
              if (onAddToGroup != null)
                OutlinedButton.icon(
                  onPressed: onAddToGroup,
                  icon: const Icon(Icons.group_add, size: 18),
                  label: Text(l10n.add_to_group),
                ),
            ],
          ),
        ],
      ],
    );
  }

  Widget _count(BuildContext context, String value, String label) {
    final theme = Theme.of(context);

    return Text.rich(
      TextSpan(
        children: [
          TextSpan(
            text: value,
            style: const TextStyle(fontWeight: FontWeight.w700),
          ),
          TextSpan(
            text: ' $label',
            style: TextStyle(color: theme.colorScheme.onSurfaceVariant),
          ),
        ],
      ),
      style: theme.textTheme.bodyMedium,
    );
  }
}
