import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_store.dart';
import 'package:xta/plugins/mastodon/mastodon_media_grid.dart';
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
import 'package:xta/plugins/plugin_counts.dart';

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
  late final MastodonProfileStore _store;
  final _postsScroll = ScrollController();
  final _mediaScroll = ScrollController();

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _store = MastodonProfileStore(context.read<MastodonClient>(),
      mastodonInstanceCandidates(widget.acct, configured: mastodonConfiguredInstances(prefs)), widget.acct);
    _store.refresh();
  }

  @override
  void dispose() {
    _store.destroy();
    _postsScroll.dispose();
    _mediaScroll.dispose();
    super.dispose();
  }

  Future<void> _toggleFollow(MastodonProfile profile) async {
    final accounts = context.read<MastodonAccountsStore>();
    final feed = context.read<MastodonFeedStore>();
    try {
      if (accounts.follows(profile.acct)) {
        await accounts.remove(profile.acct);
      } else {
        await accounts.add(profile.toAccount());
      }
      await feed.refresh();
    } catch (error) {
      if (mounted) ScaffoldMessenger.of(context).showSnackBar(
        SnackBar(content: Text(mastodonErrorMessage(L10n.of(context), error))));
    }
  }

  Future<void> _addToGroup(MastodonProfile profile) async {
    final accounts = context.read<MastodonAccountsStore>();
    final groupsModel = context.read<GroupsModel>();
    if (!accounts.follows(profile.acct)) await accounts.add(profile.toAccount());
    if (!mounted) return;
    final user = subscriptionOf(profile.toAccount());
    final groups = await groupsModel.listGroupsForUser(user.id);
    if (!mounted) return;
    await pickUserGroups(context, user: user, followed: true, groupsForUser: groups);
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<MastodonProfileStore, MastodonProfileState>(
    store: _store,
    onState: (context, state) => Scaffold(
      appBar: AppBar(title: Text('@${state.profile?.acct ?? widget.acct}')),
      body: _body(context, state),
    ),
  );

  Widget _body(BuildContext context, MastodonProfileState state) {
    final l10n = L10n.of(context);
    final profile = state.profile;
    if (profile == null) {
      if (state.error != null) return Padding(padding: const EdgeInsets.all(24),
        child: FullPageErrorWidget(error: state.error, stackTrace: null,
          prefix: mastodonErrorMessage(l10n, state.error!), onRetry: _store.refresh));
      return const Center(child: CircularProgressIndicator());
    }
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification is ScrollUpdateNotification && notification.depth == 0 &&
            state.error == null && notification.metrics.extentAfter < 500) _store.loadMore();
        return false;
      },
      child: RefreshIndicator(onRefresh: _store.refresh,
        child: CustomScrollView(
          key: ValueKey('mastodon-profile-${state.mediaSelected}'),
          controller: state.mediaSelected ? _mediaScroll : _postsScroll,
          physics: const AlwaysScrollableScrollPhysics(),
          slivers: [
            SliverToBoxAdapter(child: Padding(padding: const EdgeInsets.all(16),
              child: ScopedBuilder<MastodonAccountsStore, List<MastodonAccount>>(
                store: context.read<MastodonAccountsStore>(),
                onState: (context, accounts) => MastodonProfileCard(profile: profile,
                  following: accounts.any((account) => account.acct == profile.acct),
                  onFollowToggle: () => _toggleFollow(profile), onAddToGroup: () => _addToGroup(profile)),
              ))),
            SliverToBoxAdapter(child: _ProfileTabs(media: state.mediaSelected,
              onPosts: () => _store.selectMedia(false), onMedia: () => _store.selectMedia(true))),
            if (state.mediaSelected) MastodonMediaGrid(posts: state.media)
            else SliverList.builder(itemCount: state.posts.length,
              itemBuilder: (context, index) => MastodonPostCard(key: ValueKey(state.posts[index].id),
                post: state.posts[index], showSourceBadge: false, pinned: state.pinnedIds.contains(state.posts[index].id))),
            if (state.loading || state.loadingMore) const SliverToBoxAdapter(
              child: Padding(padding: EdgeInsets.all(24), child: Center(child: CircularProgressIndicator()))),
            if (!state.loading && !state.loadingMore && state.visible.isEmpty && state.error == null)
              SliverToBoxAdapter(child: Padding(padding: const EdgeInsets.all(32),
                child: Text(l10n.plugin_mastodon_no_posts, textAlign: TextAlign.center))),
            if (state.error != null) SliverToBoxAdapter(child: Padding(padding: const EdgeInsets.all(16),
              child: FullPageErrorWidget(error: state.error, stackTrace: null,
                prefix: mastodonErrorMessage(l10n, state.error!),
                onRetry: state.visible.isEmpty && !state.mediaSelected ? _store.refresh : _store.loadMore))),
            const SliverToBoxAdapter(child: SizedBox(height: 32)),
          ],
        ),
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
                  if (profile.bot || profile.locked)
                    Padding(
                      padding: const EdgeInsets.only(top: 4),
                      child: Text(
                        [
                          if (profile.bot) l10n.plugin_mastodon_bot,
                          if (profile.locked) l10n.plugin_mastodon_locked,
                        ].join(' · '),
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
              compactCount(profile.followersCount),
              l10n.followers,
            ),
            _count(
              context,
              compactCount(profile.followingCount),
              l10n.following,
            ),
            _count(context, compactCount(profile.statusesCount), l10n.tweets),
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
