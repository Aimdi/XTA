import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:intl/intl.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_errors.dart';
import 'package:xta/plugins/booru/booru_load_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_text.dart';

/// The post's comments, read-only, fetched when the section is opened.
class BooruComments extends StatefulWidget {
  final BooruPost post;

  const BooruComments({super.key, required this.post});

  @override
  State<BooruComments> createState() => _BooruCommentsState();
}

class _BooruCommentsState extends State<BooruComments> {
  late final BooruLoadStore<List<BooruComment>> _comments;

  @override
  void initState() {
    super.initState();
    final client = context.read<BooruClient>();
    _comments = BooruLoadStore(() => client.comments(widget.post));
  }

  @override
  void dispose() {
    unawaited(_comments.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return ExpansionTile(
      key: const ValueKey('booru-comments'),
      leading: const Icon(Icons.forum_outlined),
      title: Text(l10n.plugin_booru_comments),
      onExpansionChanged: (open) {
        if (open) unawaited(_comments.ensure());
      },
      children: [
        ScopedBuilder<BooruLoadStore<List<BooruComment>>, List<BooruComment>?>(
          store: _comments,
          onLoading: (_) => _progress,
          onError: (_, error) => ListTile(
            title: Text(booruErrorMessage(l10n, error)),
            trailing: TextButton(onPressed: _comments.reload, child: Text(l10n.retry)),
          ),
          onState: (context, comments) {
            if (comments == null) return _progress;
            if (comments.isEmpty) return ListTile(title: Text(l10n.plugin_booru_no_comments));
            return Column(children: [for (final comment in comments) _CommentTile(comment: comment)]);
          },
        ),
      ],
    );
  }

  static const _progress = Padding(
    padding: EdgeInsets.all(16),
    child: Center(child: CircularProgressIndicator()),
  );
}

class _CommentTile extends StatelessWidget {
  final BooruComment comment;

  const _CommentTile({required this.comment});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final locale = Localizations.localeOf(context).toString();
    final created = comment.createdAt;
    final meta = [?comment.author, if (created != null) DateFormat.yMMMd(locale).format(created.toLocal())].join(' · ');
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (meta.isNotEmpty)
            Text(meta, style: theme.textTheme.labelMedium?.copyWith(color: theme.colorScheme.onSurfaceVariant)),
          const SizedBox(height: 2),
          SelectableText(booruPlainText(comment.body), style: theme.textTheme.bodyMedium),
        ],
      ),
    );
  }
}
