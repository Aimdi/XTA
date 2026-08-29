/// Conversation of a local note and the replies written under it.
///
/// Stays on this device. Opening it never talks to X.
library;

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_compose.dart';
import 'package:xta/saved/local_post_logic.dart';
import 'package:xta/saved/local_post_model.dart';
import 'package:xta/saved/local_post_tile.dart';
import 'package:xta/ui/errors.dart';

Future<void> openLocalNoteThread(BuildContext context, {required String rootId}) {
  return Navigator.push<void>(
    context,
    MaterialPageRoute<void>(
      builder: (_) => LocalNoteThreadScreen(rootId: rootId),
    ),
  );
}

class LocalNoteThreadScreen extends StatelessWidget {
  final String rootId;

  const LocalNoteThreadScreen({super.key, required this.rootId});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final model = context.read<LocalPostModel>();
    return Scaffold(
      appBar: AppBar(title: Text(l10n.local_note_thread_title)),
      body: ScopedBuilder<LocalPostModel, List<LocalPost>>(
        store: model,
        onError: (_, e) => FullPageErrorWidget(
          error: e,
          stackTrace: null,
          prefix: l10n.unable_to_load_the_tweets,
          onRetry: () => model.listLocalPosts(),
        ),
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onState: (context, posts) => _ThreadBody(rootId: rootId, posts: posts),
      ),
    );
  }
}

class _ThreadBody extends StatelessWidget {
  final String rootId;
  final List<LocalPost> posts;

  const _ThreadBody({required this.rootId, required this.posts});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final thread = localPostThread(posts, rootId);
    if (thread.isEmpty) {
      return Center(child: Text(l10n.local_note_empty));
    }
    return ListView.builder(
      padding: EdgeInsets.only(bottom: MediaQuery.paddingOf(context).bottom + 16),
      itemCount: thread.length,
      itemBuilder: (context, index) {
        final (:post, :depth) = thread[index];
        return Padding(
          padding: EdgeInsets.only(left: (depth.clamp(0, 4)) * 16.0),
          child: LocalPostTile(
            post: post,
            replyCount: localPostDirectReplyCount(posts, post.id),
            onEdit: () => openLocalPostComposer(context, existing: post),
            onDelete: () => _delete(context, post),
            onReply: () => openLocalPostComposer(context, replyTo: post),
          ),
        );
      },
    );
  }

  Future<void> _delete(BuildContext context, LocalPost post) async {
    final l10n = L10n.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.are_you_sure),
        content: Text(l10n.local_note_delete_confirm),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(context, false),
            child: Text(l10n.cancel),
          ),
          TextButton(
            onPressed: () => Navigator.pop(context, true),
            child: Text(l10n.delete),
          ),
        ],
      ),
    );
    if (confirmed == true && context.mounted) {
      await context.read<LocalPostModel>().deleteLocalPost(post.id);
      if (post.id == rootId && context.mounted) {
        Navigator.pop(context);
      }
    }
  }
}
