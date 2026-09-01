import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/errors.dart';

/// The discussion under a post, read-only — often half of what a newsletter is.
class SubstackCommentsScreen extends StatefulWidget {
  final SubstackPost post;

  const SubstackCommentsScreen({super.key, required this.post});

  @override
  State<SubstackCommentsScreen> createState() => _SubstackCommentsScreenState();
}

class _SubstackCommentsScreenState extends State<SubstackCommentsScreen> {
  List<SubstackComment>? _comments;
  Object? _error;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _load());
  }

  Future<void> _load() async {
    setState(() => _error = null);
    try {
      final comments =
          await context.read<SubstackClient>().fetchComments(widget.post.publication, widget.post.id);
      if (mounted) setState(() => _comments = comments);
    } catch (e) {
      if (mounted) setState(() => _error = e);
    }
  }

  @override
  Widget build(BuildContext context) {
    final comments = _comments;

    return Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).plugin_substack_comments)),
      body: _error != null
          ? FullPageErrorWidget(
              error: _error,
              stackTrace: null,
              prefix: L10n.of(context).plugin_substack_load_error,
              onRetry: _load,
            )
          : comments == null
              ? const Center(child: CircularProgressIndicator())
              : comments.isEmpty
                  ? Center(child: Text(L10n.of(context).plugin_substack_no_comments))
                  : ListView.builder(
                      itemCount: comments.length,
                      itemBuilder: (context, index) => _CommentRow(comment: comments[index]),
                    ),
    );
  }
}

class _CommentRow extends StatelessWidget {
  final SubstackComment comment;

  const _CommentRow({required this.comment});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final date = comment.at;

    return Padding(
      // Depth becomes indentation, capped so a long argument stays readable.
      padding: EdgeInsets.only(left: 16.0 + 14.0 * comment.depth.clamp(0, 6), right: 16, top: 10, bottom: 2),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Flexible(
                child: Text(
                  comment.author ?? '—',
                  overflow: TextOverflow.ellipsis,
                  style: theme.textTheme.bodySmall!.copyWith(fontWeight: FontWeight.w700),
                ),
              ),
              if (date != null) ...[
                const SizedBox(width: 6),
                Text('· ${createRelativeDate(date)}', style: theme.textTheme.bodySmall),
              ],
            ],
          ),
          const SizedBox(height: 2),
          Text(comment.body, style: theme.textTheme.bodyMedium),
          const SizedBox(height: 8),
          Divider(height: 1, color: theme.colorScheme.outlineVariant.withValues(alpha: 0.4)),
        ],
      ),
    );
  }
}
