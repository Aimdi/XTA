import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_client.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_image.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

String _threadsSearchError(L10n l10n, Object error) {
  if (error is! ThreadsException) {
    return l10n.plugin_threads_error_unreachable;
  }
  return switch (error.kind) {
    ThreadsErrorKind.notConfigured => l10n.plugin_threads_not_configured,
    ThreadsErrorKind.noSuchFeed => l10n.plugin_threads_error_no_feed,
    ThreadsErrorKind.throttled => l10n.plugin_threads_error_throttled,
    ThreadsErrorKind.unreachable => l10n.plugin_threads_error_unreachable,
    ThreadsErrorKind.unauthorized => l10n.plugin_threads_error_unauthorized,
    ThreadsErrorKind.sessionSuspended =>
      l10n.plugin_threads_error_session_suspended,
  };
}

/// Discover Threads accounts — multi-result search when cookies are set;
/// exact `@handle` open when browsing as a guest.
Future<void> showThreadsSearchSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _ThreadsSearchSheet(),
  );
}

class _ThreadsSearchSheet extends StatefulWidget {
  const _ThreadsSearchSheet();

  @override
  State<_ThreadsSearchSheet> createState() => _ThreadsSearchSheetState();
}

class _ThreadsSearchSheetState extends State<_ThreadsSearchSheet> {
  final _controller = TextEditingController();
  List<ThreadsProfile> _results = const [];
  Object? _error;
  var _loading = false;
  var _searched = false;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _openProfile(String handle) async {
    if (!mounted) return;
    Navigator.pop(context);
    await Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ThreadsProfileScreen(username: handle)),
    );
  }

  Future<void> _search() async {
    final query = _controller.text.trim().replaceFirst(RegExp(r'^@'), '');
    if (query.isEmpty) return;

    final direct = context.read<ThreadsDirectClient>();
    if (!direct.hasCookies) {
      await _openProfile(query);
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _searched = true;
    });
    try {
      final users = await direct.searchUsers(query);
      if (!mounted) return;
      if (users.length == 1) {
        await _openProfile(users.first.username);
        return;
      }
      setState(() {
        _results = users;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      // Fall back to exact guest/profile open when search fails.
      try {
        await _openProfile(query);
      } catch (_) {
        if (!mounted) return;
        setState(() {
          _error = e;
          _loading = false;
          _results = const [];
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final direct = context.read<ThreadsDirectClient>();
    final bottom = MediaQuery.viewInsetsOf(context).bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.7,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: TextField(
                controller: _controller,
                autofocus: true,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: l10n.plugin_threads_search_hint,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.arrow_forward),
                    tooltip: l10n.plugin_threads_search,
                    onPressed: _search,
                  ),
                ),
                onSubmitted: (_) => _search(),
              ),
            ),
            if (!direct.hasCookies)
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Text(
                  l10n.plugin_threads_search_guest_hint,
                  style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
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
            _threadsSearchError(l10n, _error!),
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
            l10n.plugin_threads_search_hint,
            textAlign: TextAlign.center,
          ),
        ),
      );
    }
    if (_results.isEmpty) {
      return Center(child: Text(l10n.plugin_threads_no_results));
    }
    return ListView.separated(
      itemCount: _results.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final profile = _results[index];
        return ListTile(
          leading: _avatar(context, profile),
          title: Text(
            profile.fullName.isEmpty ? profile.username : profile.fullName,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          subtitle: Text(
            '@${profile.username}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          onTap: () => _openProfile(profile.username),
        );
      },
    );
  }

  Widget _avatar(BuildContext context, ThreadsProfile profile) {
    final theme = Theme.of(context);
    final url = profile.profilePicUrl;
    return ClipOval(
      child: url.isEmpty
          ? FallbackAvatar(
              seed: profile.username,
              displayName: profile.fullName,
              size: 40,
              accent: theme.colorScheme.primary,
            )
          : ThreadsNetworkImage(url, width: 40, height: 40, fit: BoxFit.cover),
    );
  }
}
