import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_comments_store.dart';
import 'package:xta/plugins/substack/substack_discussion_text.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/utils/urls.dart';

class SubstackCommentsScreen extends StatefulWidget {
  final SubstackPost post;
  const SubstackCommentsScreen({super.key, required this.post});
  @override
  State<SubstackCommentsScreen> createState() => _SubstackCommentsScreenState();
}

class _SubstackCommentsScreenState extends State<SubstackCommentsScreen> {
  late final SubstackCommentsStore _store;
  final _query = TextEditingController();
  final _scroll = ScrollController();
  @override
  void initState() {
    super.initState();
    _store = SubstackCommentsStore(context.read<SubstackClient>(), widget.post);
    _store.refresh();
  }

  @override
  void dispose() {
    _store.destroy();
    _query.dispose();
    _scroll.dispose();
    super.dispose();
  }

  String _orderLabel(L10n l10n, SubstackCommentOrder order) => switch (order) {
    SubstackCommentOrder.original => l10n.bluesky_thread_sort_default,
    SubstackCommentOrder.newest => l10n.plugin_mastodon_order_newest,
    SubstackCommentOrder.oldest => l10n.plugin_mastodon_order_oldest,
  };

  @override
  Widget build(BuildContext context) => ScopedBuilder<SubstackCommentsStore, SubstackCommentsState>(
    store: _store,
    onState: (context, state) {
      final l10n = L10n.of(context);
      final url =
          substackDiscussionUrl(widget.post.canonicalUrl) ??
          substackDiscussionUrl('${widget.post.publicationBaseUrl}/p/${widget.post.slug}');
      return Scaffold(
        appBar: AppBar(
          title: Text(l10n.plugin_substack_comments),
          actions: [
            IconButton(
              tooltip: l10n.open_in_browser,
              onPressed: url == null ? null : () => openUri(context, url),
              icon: const Icon(Icons.open_in_new),
            ),
            PopupMenuButton<bool>(
              key: const ValueKey('substack-comment-branches'),
              enabled: state.comments.isNotEmpty,
              onSelected: _store.setExpanded,
              itemBuilder: (_) => [
                PopupMenuItem(value: true, child: Text(l10n.plugin_reddit_expand_all)),
                PopupMenuItem(value: false, child: Text(l10n.plugin_reddit_collapse_all)),
              ],
            ),
          ],
        ),
        body: RefreshIndicator(onRefresh: _store.refresh, child: _discussion(context, state)),
      );
    },
  );

  Widget _discussion(BuildContext context, SubstackCommentsState state) {
    final l10n = L10n.of(context);
    final rows = state.rows;
    final leading = <Widget>[
      Padding(
        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
        child: Text(widget.post.title, style: Theme.of(context).textTheme.titleMedium),
      ),
      if (state.comments.isNotEmpty) _controls(context, state),
      if (state.loading)
        const Padding(
          padding: EdgeInsets.all(16),
          child: Center(child: CircularProgressIndicator()),
        ),
      if (state.error != null)
        Padding(
          padding: const EdgeInsets.all(16),
          child: FullPageErrorWidget(
            error: state.error,
            stackTrace: null,
            prefix: l10n.plugin_substack_load_error,
            onRetry: _store.refresh,
          ),
        ),
      if (!state.loading && state.error == null && rows.isEmpty)
        Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            state.query.trim().isEmpty ? l10n.plugin_substack_no_comments : l10n.substack_comment_no_match,
            textAlign: TextAlign.center,
          ),
        ),
    ];
    return ListView.builder(
      controller: _scroll,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: 32),
      itemCount: leading.length + rows.length,
      itemBuilder: (context, index) => index < leading.length
          ? leading[index]
          : _CommentRow(
              key: ValueKey('substack-comment-${rows[index - leading.length].comment.id}'),
              row: rows[index - leading.length],
              onToggle: () => _store.toggle(rows[index - leading.length].comment.id),
            ),
    );
  }

  Widget _controls(BuildContext context, SubstackCommentsState state) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16),
      child: Column(
        children: [
          TextField(
            key: const ValueKey('substack-comment-search'),
            controller: _query,
            onChanged: _store.search,
            decoration: InputDecoration(
              labelText: l10n.substack_comment_search,
              prefixIcon: const Icon(Icons.search),
              suffixIcon: state.query.isEmpty
                  ? null
                  : IconButton(
                      tooltip: l10n.group_combine_clear,
                      onPressed: () {
                        _query.clear();
                        _store.search('');
                      },
                      icon: const Icon(Icons.close),
                    ),
            ),
          ),
          Align(
            alignment: AlignmentDirectional.centerEnd,
            child: PopupMenuButton<SubstackCommentOrder>(
              key: const ValueKey('substack-comment-sort'),
              tooltip: l10n.plugin_reddit_sort,
              onSelected: _store.sort,
              initialValue: state.order,
              itemBuilder: (_) => [
                for (final order in SubstackCommentOrder.values)
                  CheckedPopupMenuItem(
                    value: order,
                    checked: order == state.order,
                    child: Text(_orderLabel(l10n, order)),
                  ),
              ],
              child: Padding(
                padding: const EdgeInsets.symmetric(vertical: 14),
                child: Row(
                  mainAxisSize: MainAxisSize.min,
                  children: [
                    const Icon(Icons.sort, size: 20),
                    const SizedBox(width: 8),
                    Flexible(child: Text(_orderLabel(l10n, state.order))),
                  ],
                ),
              ),
            ),
          ),
        ],
      ),
    );
  }
}

class _CommentRow extends StatelessWidget {
  final SubstackCommentRow row;
  final VoidCallback onToggle;
  const _CommentRow({super.key, required this.row, required this.onToggle});
  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final comment = row.comment;
    return Padding(
      padding: EdgeInsetsDirectional.fromSTEB(16.0 + 12.0 * row.depth.clamp(0, 2), 12, 16, 0),
      child: DecoratedBox(
        decoration: BoxDecoration(
          border: row.depth == 0 ? null : BorderDirectional(start: BorderSide(color: theme.colorScheme.outlineVariant)),
        ),
        child: Padding(
          padding: EdgeInsetsDirectional.only(start: row.depth == 0 ? 0 : 8),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              if (row.contextOnly)
                Text(
                  l10n.mastodon_thread_context,
                  style: theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                ),
              Wrap(
                spacing: 8,
                runSpacing: 4,
                crossAxisAlignment: WrapCrossAlignment.center,
                children: [
                  Text(
                    comment.author?.trim().isNotEmpty == true ? comment.author! : l10n.unknown,
                    style: theme.textTheme.titleSmall?.copyWith(fontWeight: FontWeight.w700),
                  ),
                  if (comment.at != null) Text(createRelativeDate(comment.at!), style: theme.textTheme.bodySmall),
                ],
              ),
              const SizedBox(height: 6),
              SubstackDiscussionText(
                text: comment.body,
                selectable: true,
                style: theme.textTheme.bodyMedium?.copyWith(height: 1.4),
              ),
              if (row.descendants > 0)
                Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: TextButton.icon(
                    key: ValueKey('substack-comment-collapse-${comment.id}'),
                    onPressed: onToggle,
                    icon: Icon(row.collapsed ? Icons.expand_more : Icons.expand_less),
                    label: Text(
                      '${row.collapsed ? l10n.show : l10n.hide} · ${l10n.plugin_hn_comment_count(row.descendants)}',
                    ),
                  ),
                ),
              const SizedBox(height: 8),
              Divider(height: 1, color: theme.colorScheme.outlineVariant),
            ],
          ),
        ),
      ),
    );
  }
}
