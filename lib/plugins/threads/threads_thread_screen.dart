import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/threads/threads_direct_client.dart';
import 'package:xta/plugins/threads/threads_models.dart';
import 'package:xta/plugins/threads/threads_post_card.dart';
import 'package:xta/plugins/threads/threads_profile_screen.dart';
import 'package:xta/tweet/threaded_conversation.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/utils/urls.dart';

/// One Threads post and any replies the public page embeds — stays in-app.
class ThreadsThreadScreen extends StatefulWidget {
  final ThreadsPost post;

  const ThreadsThreadScreen({super.key, required this.post});

  @override
  State<ThreadsThreadScreen> createState() => _ThreadsThreadScreenState();
}

class _ThreadsThreadScreenState extends State<ThreadsThreadScreen> {
  late ThreadsPost _status = widget.post;
  List<ThreadsPost> _replies = const [];
  Object? _error;
  var _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final url = _status.url?.trim();
    if (url == null || url.isEmpty) {
      if (!mounted) return;
      setState(() {
        _loading = false;
        _error = null;
      });
      return;
    }

    // Keep the seed card; only the replies strip shows a spinner.
    if (!mounted) return;
    setState(() {
      _loading = true;
      _error = null;
    });

    try {
      final client = context.read<ThreadsDirectClient>();
      final posts = await client.fetchGuestPostThread(url);
      if (!mounted) return;

      final root = _pickRoot(posts) ?? _status;
      final replies = [
        for (final post in posts)
          if (post.id != root.id) post,
      ];
      setState(() {
        _status = root;
        _replies = replies;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() => _error = e);
    } finally {
      if (mounted) {
        setState(() => _loading = false);
      }
    }
  }

  ThreadsPost? _pickRoot(List<ThreadsPost> posts) {
    if (posts.isEmpty) {
      return null;
    }
    for (final post in posts) {
      if (post.id == _status.id || post.url == _status.url) {
        return post;
      }
    }
    return posts.first;
  }

  void _openBrowser() {
    final url = _status.url;
    if (url != null) {
      openUri(context, url);
    }
  }

  void _openProfile(String handle) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ThreadsProfileScreen(username: handle)),
    );
  }

  void _openPost(ThreadsPost post) {
    if (post.id == _status.id) {
      return;
    }
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => ThreadsThreadScreen(post: post)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_threads_thread),
        actions: [
          if (_status.url != null)
            IconButton(
              tooltip: l10n.open_in_browser,
              onPressed: _openBrowser,
              icon: const Icon(Icons.open_in_new),
            ),
        ],
      ),
      body: RefreshIndicator(onRefresh: _load, child: _body(l10n)),
    );
  }

  Widget _body(L10n l10n) {
    final extra = _loading || _error != null
        ? 1
        : (_replies.isNotEmpty ? 1 + _replies.length : 1);

    return FeedListView(
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: 1 + extra,
      itemBuilder: (context, index) {
        if (index == 0) {
          return ThreadsPostCard(
            key: ValueKey(_status.id),
            post: _status,
            showSourceBadge: false,
            openOnTap: false,
            onAuthorTap: () => _openProfile(_status.handle),
            onOpenBrowser: _status.url == null ? null : _openBrowser,
          );
        }
        if (_loading) {
          return const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          );
        }
        if (_error != null) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: FullPageErrorWidget(
              error: _error,
              stackTrace: null,
              prefix: threadsApiErrorMessage(l10n, _error!),
              onRetry: _load,
            ),
          );
        }
        if (_replies.isEmpty) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(24, 8, 24, 24),
            child: Column(
              children: [
                Text(
                  l10n.plugin_threads_replies_empty,
                  textAlign: TextAlign.center,
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(
                    color: Theme.of(context).colorScheme.onSurfaceVariant,
                  ),
                ),
                if (_status.url != null)
                  TextButton.icon(
                    onPressed: _openBrowser,
                    icon: const Icon(Icons.open_in_new),
                    label: Text(l10n.open_in_browser),
                  ),
              ],
            ),
          );
        }
        if (index == 1) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
            child: Text(
              l10n.plugin_threads_replies,
              style: Theme.of(context).textTheme.titleSmall!.copyWith(
                color: Theme.of(context).colorScheme.onSurfaceVariant,
              ),
            ),
          );
        }
        final i = index - 2;
        return ThreadIndent(
          depth: 1,
          connectTop: true,
          connectBottom: i < _replies.length - 1,
          child: ThreadsPostCard(
            key: ValueKey(_replies[i].id),
            post: _replies[i],
            showSourceBadge: false,
            onOpen: () => _openPost(_replies[i]),
            onAuthorTap: () => _openProfile(_replies[i].handle),
            onOpenBrowser: _replies[i].url == null
                ? null
                : () => openUri(context, _replies[i].url!),
          ),
        );
      },
    );
  }
}
