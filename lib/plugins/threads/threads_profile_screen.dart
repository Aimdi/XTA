import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_api.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_image.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_settings.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/plugins/threads/threads_store.dart';
import 'package:xta/user.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/utils/urls.dart';

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

/// One Threads profile plus their public posts.
class ThreadsProfileScreen extends StatefulWidget {
  final String username;

  const ThreadsProfileScreen({super.key, required this.username});

  @override
  State<ThreadsProfileScreen> createState() => _ThreadsProfileScreenState();
}

class _ThreadsProfileScreenState extends State<ThreadsProfileScreen> {
  ThreadsProfile? _profile;
  List<ThreadsPost> _posts = const [];
  Object? _error;
  var _loading = true;

  String get _handle =>
      (normaliseThreadsHandle(widget.username) ?? widget.username)
          .trim()
          .toLowerCase();

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) {
        _load();
      }
    });
  }

  Future<void> _load({bool forceRefresh = false}) async {
    final keepContent = _profile != null || _posts.isNotEmpty;
    if (!keepContent) {
      setState(() {
        _loading = true;
        _error = null;
      });
    }

    final handle = _handle;
    if (handle.isEmpty) {
      if (mounted) {
        setState(() {
          _error = ThreadsException(
            ThreadsErrorKind.noSuchFeed,
            'empty handle',
          );
          _loading = false;
        });
      }
      return;
    }

    final prefs = PrefService.of(context, listen: false);
    final direct = context.read<ThreadsDirectClient>();
    final api = context.read<ThreadsApi>();
    final feed = context.read<ThreadsFeedStore>();
    final apiBase =
        prefs.get<String>(optionPluginThreadsApiBase) ?? kThreadsApiDefaultBase;
    final apiToken = prefs.get<String>(optionPluginThreadsApiToken) ?? '';

    // Header + posts for one handle: guest HTML is single-flight in the client
    // so this is not two paced GETs. Prefer cache on first open; pull forces.
    final profileFuture = _resolveProfile(
      direct,
      api,
      apiBase,
      apiToken,
      handle,
    );
    final postsFuture = feed.postsFor([handle], forceRefresh: forceRefresh);

    ThreadsProfile? profile;
    Object? profileError;
    List<ThreadsPost> posts = const [];
    Object? postsError;

    try {
      try {
        profile = await profileFuture;
      } catch (e) {
        profileError = e;
      }

      try {
        posts = await postsFuture;
      } catch (e) {
        postsError = e;
      }

      if (!mounted) {
        return;
      }

      profile ??= threadsProfileFromPosts(handle, posts);

      if (profile == null && posts.isEmpty) {
        if (!keepContent) {
          setState(() {
            _error =
                profileError ??
                postsError ??
                ThreadsException(
                  ThreadsErrorKind.noSuchFeed,
                  'profile missing',
                );
          });
        }
        return;
      }

      setState(() {
        _profile = profile;
        _posts = posts;
        _error = null;
      });
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  Future<ThreadsProfile?> _resolveProfile(
    ThreadsDirectClient direct,
    ThreadsApi api,
    String apiBase,
    String apiToken,
    String handle,
  ) async {
    try {
      return await direct.fetchGuestProfile(handle);
    } catch (_) {
      // Fall through.
    }
    if (direct.hasCookies) {
      try {
        return await direct.fetchProfile(handle);
      } catch (_) {
        // Fall through.
      }
    }
    try {
      return await api.profile(apiBase, apiToken, handle);
    } catch (_) {
      return null;
    }
  }

  Future<void> _follow(ThreadsProfile profile) async {
    final messenger = ScaffoldMessenger.of(context);
    final added = L10n.of(context).plugin_threads_account_added;
    final accounts = context.read<ThreadsAccountsStore>();
    final feed = context.read<ThreadsFeedStore>();

    await accounts.add(profile.toAccount());
    if (mounted) {
      await feed.refresh();
    }
    messenger.showSnackBar(SnackBar(content: Text(added)));
    if (mounted) {
      setState(() {});
    }
  }

  Future<void> _addToGroup(ThreadsProfile profile) async {
    final accounts = context.read<ThreadsAccountsStore>();
    final groupsModel = context.read<GroupsModel>();
    if (!accounts.state.any((a) => a.handle == profile.username)) {
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
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(title: Text('@$_handle')),
      body: _body(context, l10n),
    );
  }

  Widget _body(BuildContext context, L10n l10n) {
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
          prefix: threadsApiErrorMessage(l10n, error),
          onRetry: _load,
        ),
      );
    }

    final profile = _profile;
    if (profile == null) {
      return Padding(
        padding: const EdgeInsets.all(24),
        child: FullPageErrorWidget(
          error: ThreadsException(
            ThreadsErrorKind.noSuchFeed,
            'profile missing',
          ),
          stackTrace: null,
          prefix: l10n.plugin_threads_error_no_feed,
          onRetry: _load,
        ),
      );
    }
    final alreadyFollows = context.read<ThreadsAccountsStore>().state.any(
      (a) => a.handle == profile.username,
    );

    return RefreshIndicator(
      onRefresh: () => _load(forceRefresh: true),
      child: FeedListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.only(bottom: 24),
        itemCount: 1 + (_posts.isEmpty ? 1 : _posts.length),
        itemBuilder: (context, index) {
          if (index == 0) {
            return Padding(
              padding: const EdgeInsets.all(16),
              child: ThreadsProfileCard(
                profile: profile,
                onFollow: alreadyFollows ? null : () => _follow(profile),
                onAddToGroup: () => _addToGroup(profile),
              ),
            );
          }
          if (_posts.isEmpty) {
            return Padding(
              padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
              child: Text(
                l10n.plugin_threads_no_posts,
                textAlign: TextAlign.center,
              ),
            );
          }
          final post = _posts[index - 1];
          return ThreadsPostCard(
            key: ValueKey(post.id),
            post: post,
            showSourceBadge: false,
          );
        },
      ),
    );
  }
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
    final numbers = NumberFormat.compact();

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
              numbers.format(profile.followerCount),
              l10n.followers,
            ),
            _count(
              context,
              numbers.format(profile.followingCount),
              l10n.following,
            ),
            _count(context, numbers.format(profile.mediaCount), l10n.tweets),
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
