import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/plugins/bluesky/bluesky_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';
import 'package:xta/ui/errors.dart';

/// Which public graph edge to show for a Bluesky profile.
enum BlueskyFollowsKind { following, followers }

/// Paginated list of accounts someone follows, or who follow them.
///
/// Read-only AppView calls — opening a row navigates to that profile.
class BlueskyFollowsScreen extends StatefulWidget {
  final String actor;
  final BlueskyFollowsKind kind;

  const BlueskyFollowsScreen({
    super.key,
    required this.actor,
    required this.kind,
  });

  @override
  State<BlueskyFollowsScreen> createState() => _BlueskyFollowsScreenState();
}

class _BlueskyFollowsScreenState extends State<BlueskyFollowsScreen> {
  final _profiles = <BlueskyProfile>[];
  final _seen = <String>{};
  String? _cursor;
  Object? _error;
  var _loading = true;
  var _loadingMore = false;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<({List<BlueskyProfile> profiles, String? cursor})> _fetchPage(
    String? cursor,
  ) async {
    final client = context.read<BlueskyClient>();
    if (widget.kind == BlueskyFollowsKind.following) {
      final page = await client.getFollows(widget.actor, cursor: cursor);
      return (profiles: page.follows, cursor: page.cursor);
    }
    final page = await client.getFollowers(widget.actor, cursor: cursor);
    return (profiles: page.followers, cursor: page.cursor);
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
      _profiles.clear();
      _seen.clear();
      _cursor = null;
    });

    try {
      final page = await _fetchPage(null);
      if (!mounted) return;
      setState(() {
        _append(page.profiles);
        _cursor = _nextCursor(page.cursor, page.profiles);
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

  Future<void> _loadMore({bool retry = false}) async {
    final cursor = _cursor;
    if (cursor == null || _loadingMore || _loading) {
      return;
    }
    // Don't auto-retry from scroll after a page failure — only the retry control.
    if (_error != null && !retry) {
      return;
    }

    setState(() {
      _loadingMore = true;
      _error = null;
    });
    try {
      final page = await _fetchPage(cursor);
      if (!mounted) return;
      setState(() {
        _append(page.profiles);
        _cursor = _nextCursor(page.cursor, page.profiles, previous: cursor);
        _loadingMore = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loadingMore = false;
      });
    }
  }

  void _append(List<BlueskyProfile> page) {
    for (final profile in page) {
      final key = profile.did.isNotEmpty
          ? profile.did
          : profile.handle.toLowerCase();
      if (key.isEmpty || !_seen.add(key)) {
        continue;
      }
      _profiles.add(profile);
    }
  }

  String? _nextCursor(
    String? next,
    List<BlueskyProfile> page, {
    String? previous,
  }) {
    if (next == null || next.isEmpty || next == previous || page.isEmpty) {
      return null;
    }
    return next;
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

  Future<void> _open(BlueskyProfile profile) async {
    final actor = profile.did.isNotEmpty ? profile.did : profile.handle;
    if (actor.isEmpty) {
      return;
    }
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => BlueskyProfileScreen(actor: actor)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final title = widget.kind == BlueskyFollowsKind.following
        ? l10n.following
        : l10n.followers;

    return Scaffold(
      appBar: AppBar(title: Text(title)),
      body: _body(l10n),
    );
  }

  Widget _body(L10n l10n) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }

    if (_error != null && _profiles.isEmpty) {
      return FullPageErrorWidget(
        error: _error,
        stackTrace: null,
        prefix: l10n.unable_to_load_the_list_of_follows,
        onRetry: _load,
      );
    }

    if (_profiles.isEmpty) {
      final empty = widget.kind == BlueskyFollowsKind.following
          ? l10n.this_user_does_not_follow_anyone
          : l10n.this_user_does_not_have_anyone_following_them;
      return Center(child: Text(empty));
    }

    final showFooter = _cursor != null || _loadingMore || _error != null;
    return NotificationListener<ScrollNotification>(
      onNotification: (notification) {
        if (notification.metrics.extentAfter < 400) {
          _loadMore();
        }
        return false;
      },
      child: ListView.separated(
        itemCount: _profiles.length + (showFooter ? 1 : 0),
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          if (index >= _profiles.length) {
            return _footer(l10n);
          }
          final profile = _profiles[index];
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
                following
                    ? l10n.plugin_bluesky_unfollow
                    : l10n.plugin_bluesky_follow,
              ),
            ),
            onTap: () => _open(profile),
          );
        },
      ),
    );
  }

  Widget _footer(L10n l10n) {
    if (_loadingMore) {
      return const Padding(
        padding: EdgeInsets.symmetric(vertical: 24),
        child: Center(child: CircularProgressIndicator()),
      );
    }
    if (_error != null) {
      return FullPageErrorWidget(
        error: _error,
        stackTrace: null,
        prefix: l10n.unable_to_load_the_next_page_of_follows,
        onRetry: () => _loadMore(retry: true),
      );
    }
    return const SizedBox(height: 24);
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
