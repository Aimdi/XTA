import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/bluesky/bluesky_client.dart';
import 'package:xta/plugins/bluesky/bluesky_models.dart';
import 'package:xta/plugins/bluesky/bluesky_post_card.dart';
import 'package:xta/plugins/bluesky/bluesky_profile_screen.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/utils/urls.dart';

/// One Bluesky post and its public replies, read through the AppView.
class BlueskyThreadScreen extends StatefulWidget {
  final BlueskyPost post;

  const BlueskyThreadScreen({super.key, required this.post});

  @override
  State<BlueskyThreadScreen> createState() => _BlueskyThreadScreenState();
}

class _BlueskyThreadScreenState extends State<BlueskyThreadScreen> {
  late BlueskyPost _status = widget.post;
  List<BlueskyPost> _ancestors = const [];
  List<BlueskyPost> _replies = const [];
  Object? _error;
  var _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });

    final client = context.read<BlueskyClient>();
    try {
      final thread = await client.getPostThread(_status.uri);
      if (!mounted) return;
      setState(() {
        _status = thread.post;
        _ancestors = thread.ancestors;
        _replies = thread.replies;
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

  void _openBrowser() => openUri(context, _status.url);

  void _openProfile(String actor) {
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => BlueskyProfileScreen(actor: actor)),
    );
  }

  void _openPost(BlueskyPost post) {
    if (post.uri == _status.uri) {
      return;
    }
    Navigator.push(
      context,
      MaterialPageRoute(builder: (_) => BlueskyThreadScreen(post: post)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_bluesky_title),
        actions: [
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
    if (_loading && _ancestors.isEmpty && _replies.isEmpty) {
      return FeedListView(
        physics: const AlwaysScrollableScrollPhysics(),
        itemCount: 2,
        itemBuilder: (context, index) {
          if (index == 0) {
            return BlueskyPostCard(
              key: ValueKey(_status.uri),
              post: _status,
              showSourceBadge: false,
              openOnTap: false,
              onAuthorTap: () => _openProfile(_status.handle),
              onOpenBrowser: _openBrowser,
            );
          }
          return const Padding(
            padding: EdgeInsets.all(24),
            child: Center(child: CircularProgressIndicator()),
          );
        },
      );
    }

    final errorSlot = _error != null ? 1 : 0;
    return FeedListView(
      physics: const AlwaysScrollableScrollPhysics(),
      itemCount: _ancestors.length + 1 + errorSlot + _replies.length,
      itemBuilder: (context, index) {
        if (index < _ancestors.length) {
          final ancestor = _ancestors[index];
          return BlueskyPostCard(
            key: ValueKey(ancestor.uri),
            post: ancestor,
            showSourceBadge: false,
            onOpen: () => _openPost(ancestor),
            onAuthorTap: () => _openProfile(ancestor.handle),
            onOpenBrowser: () => openUri(context, ancestor.url),
          );
        }
        if (index == _ancestors.length) {
          return BlueskyPostCard(
            key: ValueKey(_status.uri),
            post: _status,
            showSourceBadge: false,
            openOnTap: false,
            onAuthorTap: () => _openProfile(_status.handle),
            onOpenBrowser: _openBrowser,
          );
        }
        if (errorSlot == 1 && index == _ancestors.length + 1) {
          return Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
            child: FullPageErrorWidget(
              error: _error,
              stackTrace: null,
              prefix: blueskyErrorMessage(l10n, _error!),
              onRetry: _load,
            ),
          );
        }
        final reply = _replies[index - _ancestors.length - 1 - errorSlot];
        return BlueskyPostCard(
          key: ValueKey(reply.uri),
          post: reply,
          showSourceBadge: false,
          onOpen: () => _openPost(reply),
          onAuthorTap: () => _openProfile(reply.handle),
          onOpenBrowser: () => openUri(context, reply.url),
        );
      },
    );
  }
}
