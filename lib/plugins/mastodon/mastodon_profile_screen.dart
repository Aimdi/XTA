import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
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

class _MastodonProfileScreenState extends State<MastodonProfileScreen> {
  MastodonProfile? _profile;
  List<MastodonPost> _posts = const [];
  List<MastodonPost> _media = const [];
  String? _instance;
  Object? _error;
  var _loading = true;
  var _mediaTab = false;
  var _loadingMore = false;
  var _hasMorePosts = true;
  var _hasMoreMedia = true;
  var _mediaLoaded = false;
  var _backedOff = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _backedOff = false;
    });

    final prefs = PrefService.of(context, listen: false);
    final client = context.read<MastodonClient>();
    try {
      final candidates = mastodonInstanceCandidates(
        widget.acct,
        configured: mastodonConfiguredInstances(prefs),
      );
      final page = await client.profileAnywhere(candidates, widget.acct);
      if (mounted) {
        setState(() {
          _profile = page.profile;
          _posts = page.posts;
          _instance = page.instance;
          _hasMorePosts = page.posts.length >= 20;
          _media = const [];
          _mediaLoaded = false;
          _hasMoreMedia = true;
          _loading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _error = e;
          _loading = false;
        });
      }
    }
  }

  List<MastodonPost> get _visible => _mediaTab ? _media : _posts;

  Future<void> _showMedia() async {
    setState(() => _mediaTab = true);
    if (_mediaLoaded || _profile == null || _instance == null) return;
    final client = context.read<MastodonClient>();
    try {
      final posts = await client.getStatuses(
        _instance!,
        _profile!.id,
        onlyMedia: true,
      );
      if (!mounted) return;
      setState(() {
        _media = posts;
        _mediaLoaded = true;
        _hasMoreMedia = posts.length >= 20;
      });
    } catch (_) {
      if (mounted) setState(() => _mediaLoaded = true);
    }
  }

  Future<void> _loadMore() async {
    final profile = _profile;
    final instance = _instance;
    final current = _visible;
    if (profile == null ||
        instance == null ||
        _loadingMore ||
        _backedOff ||
        current.isEmpty) {
      return;
    }
    if (_mediaTab ? !_hasMoreMedia : !_hasMorePosts) return;

    setState(() => _loadingMore = true);
    try {
      final more = await context.read<MastodonClient>().getStatuses(
        instance,
        profile.id,
        onlyMedia: _mediaTab,
        maxId: current.last.id,
      );
      if (!mounted) return;
      setState(() {
        if (_mediaTab) {
          _media = appendUniqueMastodonPosts(_media, more);
          _hasMoreMedia = more.length >= 20;
        } else {
          _posts = appendUniqueMastodonPosts(_posts, more);
          _hasMorePosts = more.length >= 20;
        }
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

    final posts = _visible;
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is UserScrollNotification) _backedOff = false;
        if (notification.metrics.pixels >=
            notification.metrics.maxScrollExtent - 400) {
          _loadMore();
        }
        return false;
      },
      child: FeedListView(
        padding: const EdgeInsets.only(bottom: 24),
        itemCount: 1 + posts.length + (_loadingMore ? 1 : 0),
        itemBuilder: (context, index) {
          if (index == 0) {
            return Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  MastodonProfileCard(
                    profile: profile,
                    following: following,
                    onFollowToggle: () => _toggleFollow(profile),
                    onAddToGroup: () => _addToGroup(profile),
                  ),
                  const SizedBox(height: 12),
                  _ProfileTabs(
                    media: _mediaTab,
                    onPosts: () => setState(() => _mediaTab = false),
                    onMedia: _showMedia,
                  ),
                ],
              ),
            );
          }
          final postIndex = index - 1;
          if (postIndex < posts.length) {
            final post = posts[postIndex];
            return MastodonPostCard(
              key: ValueKey('${_mediaTab ? 'm' : 'p'}-${post.id}'),
              post: post,
              showSourceBadge: false,
            );
          }
          return const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          );
        },
      ),
    );
  }
}

class _ProfileTabs extends StatelessWidget {
  final bool media;
  final VoidCallback onPosts;
  final VoidCallback onMedia;

  const _ProfileTabs({
    required this.media,
    required this.onPosts,
    required this.onMedia,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Row(
      children: [
        Expanded(
          child: _TabButton(
            label: l10n.tweets,
            selected: !media,
            onTap: onPosts,
          ),
        ),
        Expanded(
          child: _TabButton(label: l10n.media, selected: media, onTap: onMedia),
        ),
      ],
    );
  }
}

class _TabButton extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _TabButton({
    required this.label,
    required this.selected,
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
      child: Padding(
        padding: const EdgeInsets.symmetric(vertical: 10),
        child: Text(
          label,
          textAlign: TextAlign.center,
          style: theme.textTheme.labelLarge?.copyWith(
            color: color,
            fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
          ),
        ),
      ),
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
                  if (profile.bot)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        l10n.plugin_mastodon_bot,
                        style: theme.textTheme.labelSmall!.copyWith(
                          color: theme.colorScheme.primary,
                          fontWeight: FontWeight.w700,
                        ),
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
        if (profile.fields.isNotEmpty) ...[
          const SizedBox(height: 12),
          for (final field in profile.fields)
            Padding(
              padding: const EdgeInsets.only(bottom: 6),
              child: Text.rich(
                TextSpan(
                  children: [
                    TextSpan(
                      text: '${field.name}: ',
                      style: const TextStyle(fontWeight: FontWeight.w700),
                    ),
                    TextSpan(text: field.value),
                  ],
                ),
                style: theme.textTheme.bodySmall,
              ),
            ),
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
