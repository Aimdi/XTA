import 'package:xta/plugins/social_account_groups.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:share_plus/share_plus.dart';
import 'package:xta/plugins/threads/threads_likes_store.dart';
import 'package:xta/plugins/threads/threads_profile_store.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_api.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_image.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_settings.dart';
import 'package:xta/plugins/plugin_profile_tabs.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/ui/reader_swipe_navigation.dart';
import 'package:xta/utils/urls.dart';
import 'package:xta/plugins/plugin_counts.dart';

/// What a failed Xy / guest lookup should say.
String threadsApiErrorMessage(L10n l10n, Object error) {
  if (error is ThreadsException) {
    return threadsSettingsError(l10n, error);
  }
  if (error is! ThreadsApiException) {
    return l10n.plugin_threads_error_unreachable;
  }
  final said = error.message?.trim();
  if (said != null && said.isNotEmpty) {
    return said;
  }
  return switch (error.kind) {
    ThreadsApiErrorKind.notConfigured => l10n.plugin_threads_api_not_configured,
    ThreadsApiErrorKind.unauthorized => l10n.plugin_threads_api_unauthorized,
    ThreadsApiErrorKind.notFound => l10n.plugin_threads_error_no_feed,
    ThreadsApiErrorKind.unreachable => l10n.plugin_threads_error_unreachable,
    ThreadsApiErrorKind.upstream => l10n.plugin_threads_error_unreachable,
  };
}

/// One Threads profile plus their public posts, replies, media, and the posts
/// of theirs the reader liked on this device.
class ThreadsProfileScreen extends StatefulWidget {
  final String username;

  const ThreadsProfileScreen({super.key, required this.username});

  @override
  State<ThreadsProfileScreen> createState() => _ThreadsProfileScreenState();
}

const _threadsProfileTabs = [
  PluginProfileFeedTab.posts,
  PluginProfileFeedTab.replies,
  PluginProfileFeedTab.media,
  PluginProfileFeedTab.saved,
];

class _ThreadsProfileScreenState extends State<ThreadsProfileScreen> {
  late final ThreadsProfileStore _profile = ThreadsProfileStore(
    handle: _handle,
    direct: context.read<ThreadsDirectClient>(),
    api: context.read<ThreadsApi>(),
    feed: context.read<ThreadsFeedStore>(),
    prefs: PrefService.of(context, listen: false),
  );
  final _tabs = PluginViewStore(PluginProfileFeedTab.posts);

  String get _handle => (normaliseThreadsHandle(widget.username) ?? widget.username).trim().toLowerCase();

  String get _profileUrl => '$threadsWebBase/@$_handle';

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _profile.load();
      }
    });
  }

  @override
  void dispose() {
    _profile.destroy();
    _tabs.destroy();
    super.dispose();
  }

  void _select(PluginProfileFeedTab tab) {
    _tabs.select(tab);
    if (tab == PluginProfileFeedTab.replies) {
      _profile.loadReplies();
    }
  }

  Future<void> _follow(ThreadsProfile profile) async {
    final messenger = ScaffoldMessenger.of(context);
    final added = L10n.of(context).plugin_threads_account_added;
    final feed = context.read<ThreadsFeedStore>();

    await context.read<ThreadsAccountsStore>().add(profile.toAccount());
    messenger.showSnackBar(SnackBar(content: Text(added)));
    await feed.refresh();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text('@$_handle'),
        actions: [
          IconButton(
            tooltip: l10n.share_link,
            icon: const Icon(Icons.share_outlined),
            onPressed: () => SharePlus.instance.share(ShareParams(text: _profileUrl)),
          ),
          IconButton(
            tooltip: l10n.open_in_browser,
            icon: const Icon(Icons.open_in_new),
            onPressed: () => openUri(context, _profileUrl),
          ),
        ],
      ),
      body: TripleBuilder<ThreadsProfileStore, ThreadsProfileState>(
        store: _profile,
        builder: (context, triple) {
          final state = triple.state;
          if (state.isEmpty && triple.error != null) {
            return _failure(l10n, triple.error!);
          }
          final profile = state.profile;
          if (profile == null) {
            return const Center(child: CircularProgressIndicator());
          }
          return ScopedBuilder<PluginViewStore<PluginProfileFeedTab>, PluginProfileFeedTab>(
            store: _tabs,
            onState: (context, tab) => _body(context, l10n, state, profile, tab),
          );
        },
      ),
    );
  }

  Widget _failure(L10n l10n, Object error) => Padding(
    padding: const EdgeInsets.all(24),
    child: FullPageErrorWidget(
      error: error,
      stackTrace: null,
      prefix: threadsApiErrorMessage(l10n, error),
      onRetry: _profile.load,
    ),
  );

  Widget _body(
    BuildContext context,
    L10n l10n,
    ThreadsProfileState state,
    ThreadsProfile profile,
    PluginProfileFeedTab tab,
  ) {
    final accounts = context.read<ThreadsAccountsStore>();
    final posts = state.forTab(tab, liked: tab == PluginProfileFeedTab.saved ? _liked(context) : const []);
    final waiting = tab == PluginProfileFeedTab.replies && state.loadingReplies && posts.isEmpty;

    return ReaderSwipeNavigation(
      index: _threadsProfileTabs.indexOf(tab),
      count: _threadsProfileTabs.length,
      identity: _handle,
      onChanged: (index) {
        _select(_threadsProfileTabs[index]);
        return true;
      },
      child: RefreshIndicator(
        onRefresh: () => _profile.load(force: true),
        child: FeedListView(
          physics: const AlwaysScrollableScrollPhysics(),
          padding: const EdgeInsets.only(bottom: 24),
          itemCount: 2 + (posts.isEmpty ? 1 : posts.length),
          itemBuilder: (context, index) {
            if (index == 0) {
              return Padding(
                padding: const EdgeInsets.all(16),
                child: ScopedBuilder<ThreadsAccountsStore, List<ThreadsAccount>>(
                  store: accounts,
                  onState: (context, _) => ThreadsProfileCard(
                    profile: profile,
                    onFollow: accounts.follows(profile.username) ? null : () => _follow(profile),
                    onAddToGroup: () => addThreadsAccountToGroup(context, profile.toAccount()),
                  ),
                ),
              );
            }
            if (index == 1) {
              return PluginProfileTabBar(selected: tab, onSelected: _select, tabs: _threadsProfileTabs);
            }
            if (waiting) {
              return const Padding(
                padding: EdgeInsets.all(24),
                child: Center(child: CircularProgressIndicator()),
              );
            }
            if (posts.isEmpty) {
              return Padding(
                padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
                child: Text(_emptyLabel(l10n, tab), textAlign: TextAlign.center),
              );
            }
            final post = posts[index - 2];
            return ThreadsPostCard(key: ValueKey(post.id), post: post, showSourceBadge: false);
          },
        ),
      ),
    );
  }

  /// The reader's own hearts on this account's posts — kept on this device.
  List<ThreadsPost> _liked(BuildContext context) => [
    for (final post in context.read<ThreadsLikesStore>().likedPosts)
      if (post.handle == _handle) post,
  ];

  String _emptyLabel(L10n l10n, PluginProfileFeedTab tab) => switch (tab) {
    PluginProfileFeedTab.replies => l10n.plugin_threads_replies_empty,
    PluginProfileFeedTab.saved => l10n.plugin_threads_liked_empty,
    _ => l10n.plugin_threads_no_posts,
  };
}

/// The profile itself: face, name, what they say about themselves, and what
/// they have gathered.
class ThreadsProfileCard extends StatelessWidget {
  final ThreadsProfile profile;
  final VoidCallback? onFollow;
  final VoidCallback? onAddToGroup;

  const ThreadsProfileCard({
    super.key,
    required this.profile,
    this.onFollow,
    this.onAddToGroup,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Row(
          children: [
            ClipOval(
              child: profile.profilePicUrl.isEmpty
                  ? FallbackAvatar(
                      seed: profile.username,
                      displayName: profile.displayName,
                      size: 64,
                      accent: theme.colorScheme.primary,
                    )
                  : ThreadsNetworkImage(
                      profile.profilePicUrl,
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
                  Row(
                    children: [
                      Flexible(
                        child: Text(
                          profile.displayName,
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.titleLarge!.copyWith(
                            fontWeight: FontWeight.w700,
                          ),
                        ),
                      ),
                      if (profile.isVerified) ...[
                        const SizedBox(width: 6),
                        Icon(
                          Icons.verified,
                          size: 20,
                          color: theme.colorScheme.primary,
                        ),
                      ],
                      if (profile.isPrivate) ...[
                        const SizedBox(width: 6),
                        Icon(
                          Icons.lock,
                          size: 18,
                          color: theme.colorScheme.onSurfaceVariant,
                        ),
                      ],
                    ],
                  ),
                  Text(
                    '@${profile.username}',
                    style: theme.textTheme.bodyMedium!.copyWith(
                      color: theme.colorScheme.onSurfaceVariant,
                    ),
                  ),
                ],
              ),
            ),
          ],
        ),
        if (profile.biography.trim().isNotEmpty) ...[
          const SizedBox(height: 14),
          Text(profile.biography.trim(), style: theme.textTheme.bodyMedium),
        ],
        if (profile.externalUrl != null) ...[
          const SizedBox(height: 8),
          InkWell(
            onTap: () => openUri(context, profile.externalUrl!),
            child: Row(
              children: [
                Icon(
                  Icons.link,
                  size: 15,
                  color: theme.colorScheme.onSurfaceVariant,
                ),
                const SizedBox(width: 6),
                Flexible(
                  child: Text(
                    profile.externalUrl!,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: TextStyle(color: theme.colorScheme.primary),
                  ),
                ),
              ],
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
              compactCount(profile.followerCount),
              l10n.followers,
            ),
            _count(
              context,
              compactCount(profile.followingCount),
              l10n.following,
            ),
            _count(context, compactCount(profile.mediaCount), l10n.tweets),
          ],
        ),
        if (onFollow != null) ...[
          const SizedBox(height: 18),
          SizedBox(
            width: double.infinity,
            child: FilledButton.icon(
              onPressed: onFollow,
              icon: const Icon(Icons.person_add_alt),
              label: Text(l10n.plugin_threads_add_account),
            ),
          ),
        ],
        if (onAddToGroup != null) ...[
          const SizedBox(height: 8),
          SizedBox(
            width: double.infinity,
            child: OutlinedButton.icon(
              onPressed: onAddToGroup,
              icon: const Icon(Icons.group_add, size: 18),
              label: Text(l10n.add_to_group),
            ),
          ),
        ],
        if (profile.isPrivate) ...[
          const SizedBox(height: 14),
          Text(
            l10n.plugin_threads_profile_private,
            style: theme.textTheme.bodySmall,
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
