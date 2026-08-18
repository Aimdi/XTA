import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_plugin.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_card.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/errors.dart';

class HnStoryScreen extends StatefulWidget {
  final HnStory story;

  const HnStoryScreen({super.key, required this.story});

  @override
  State<HnStoryScreen> createState() => _HnStoryScreenState();
}

class _HnStoryScreenState extends State<HnStoryScreen> {
  late final _HnThreadStore _thread;
  final _collapse = HnCollapseStore();

  @override
  void initState() {
    super.initState();
    _thread = _HnThreadStore(context.read<HackerNewsClient>(), widget.story);
    WidgetsBinding.instance.addPostFrameCallback((_) => _thread.refresh());
  }

  @override
  void dispose() {
    _thread.destroy();
    _collapse.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_hn_comments),
        actions: [
          if (widget.story.url != null)
            IconButton(
              tooltip: l10n.plugin_hn_open_article,
              icon: const Icon(Icons.open_in_new),
              onPressed: () => openLink(context, widget.story.url!),
            ),
        ],
      ),
      body: ScopedBuilder<_HnThreadStore, _HnThread>.transition(
        store: _thread,
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: error.toString(),
          onRetry: _thread.refresh,
        ),
        onState: (_, thread) => RefreshIndicator(
          onRefresh: _thread.refresh,
          child: ListView(
            padding: const EdgeInsets.only(bottom: 32),
            children: [
              _StoryHeader(story: thread.story),
              const Divider(height: 1),
              if (thread.story.text != null && thread.story.text!.isNotEmpty)
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
                  child: Text(thread.story.text!),
                ),
              ScopedBuilder<HnCollapseStore, Set<int>>(
                store: _collapse,
                onState: (_, collapsed) => Column(
                  children: [
                    for (final comment in thread.comments)
                      _CommentTile(
                        comment: comment,
                        depth: 0,
                        collapsed: collapsed,
                        onToggle: _collapse.toggle,
                      ),
                  ],
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

class _StoryHeader extends StatelessWidget {
  final HnStory story;

  const _StoryHeader({required this.story});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 16, 16, 12),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            story.title,
            style: theme.textTheme.titleLarge?.copyWith(
              fontWeight: FontWeight.w700,
            ),
          ),
          if (story.host != null) ...[
            const SizedBox(height: 4),
            Text(story.host!, style: theme.textTheme.bodySmall),
          ],
          const SizedBox(height: 8),
          Wrap(
            spacing: 8,
            children: [
              Text(l10n.plugin_hn_points(story.score)),
              if (story.author != null)
                InkWell(
                  onTap: () => openHnUser(context, story.author!),
                  child: Text(
                    l10n.plugin_hn_by(story.author!),
                    style: TextStyle(color: HackerNewsPlugin().brandColor),
                  ),
                ),
              if (story.createdAt != null)
                Text(createCompactDate(story.createdAt!)),
            ],
          ),
        ],
      ),
    );
  }
}

class _CommentTile extends StatelessWidget {
  final HnComment comment;
  final int depth;
  final Set<int> collapsed;
  final void Function(int id) onToggle;

  const _CommentTile({
    required this.comment,
    required this.depth,
    required this.collapsed,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final closed = collapsed.contains(comment.id);
    final indent = 12.0 + depth * 14.0;
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        InkWell(
          onTap: () => onToggle(comment.id),
          child: Container(
            decoration: BoxDecoration(
              border: Border(
                left: BorderSide(
                  color: HackerNewsPlugin().brandColor.withValues(
                    alpha: 0.35 + (depth % 4) * 0.12,
                  ),
                  width: 2,
                ),
              ),
            ),
            padding: EdgeInsets.fromLTRB(indent, 8, 16, 8),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Text(
                  [
                    comment.deleted
                        ? l10n.plugin_hn_deleted
                        : (comment.author ?? l10n.plugin_hn_deleted),
                    if (comment.createdAt != null)
                      createCompactDate(comment.createdAt!),
                    if (closed && comment.children.isNotEmpty)
                      l10n.plugin_hn_comment_count(comment.children.length),
                  ].join(' · '),
                  style: theme.textTheme.labelMedium?.copyWith(
                    color: theme.hintColor,
                  ),
                ),
                if (!closed &&
                    !comment.deleted &&
                    (comment.text ?? '').isNotEmpty) ...[
                  const SizedBox(height: 4),
                  Text(comment.text!),
                ],
              ],
            ),
          ),
        ),
        if (!closed)
          for (final child in comment.children)
            _CommentTile(
              comment: child,
              depth: depth + 1,
              collapsed: collapsed,
              onToggle: onToggle,
            ),
      ],
    );
  }
}

class _HnThread {
  final HnStory story;
  final List<HnComment> comments;

  const _HnThread(this.story, this.comments);
}

class _HnThreadStore extends Store<_HnThread> {
  final HackerNewsClient client;
  final HnStory stub;

  _HnThreadStore(this.client, this.stub) : super(_HnThread(stub, const []));

  Future<void> refresh() async {
    await execute(() async {
      final loaded = await client.thread(stub.id);
      return _HnThread(loaded.$1, loaded.$2);
    });
  }
}
