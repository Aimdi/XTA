import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/hackernews/hn_client.dart';
import 'package:xta/plugins/hackernews/hn_models.dart';
import 'package:xta/plugins/hackernews/hn_plugin.dart';
import 'package:xta/plugins/hackernews/hn_store.dart';
import 'package:xta/plugins/hackernews/hn_story_card.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_links.dart';
import 'package:xta/ui/dates.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';

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
      body: ScopedBuilder<_HnThreadStore, _HnThread>(
        store: _thread,
        onLoading: (_) => const PluginFeedSkeleton(applyFeedInsets: false),
        onError: (_, error) => FullPageErrorWidget(
          error: error,
          stackTrace: null,
          prefix: error.toString(),
          onRetry: _thread.refresh,
        ),
        onState: (_, thread) => ScopedBuilder<HnCollapseStore, Set<int>>(
          store: _collapse,
          onState: (_, collapsed) {
            final rows = visibleHnComments(thread.comments, collapsed);
            return RefreshIndicator(
              onRefresh: _thread.refresh,
              child: FeedListView(
                padding: const EdgeInsets.only(bottom: 48),
                itemCount: 1 + rows.length,
                itemBuilder: (context, index) {
                  if (index == 0) {
                    return _ThreadLead(story: thread.story);
                  }
                  final row = rows[index - 1];
                  return _CommentTile(
                    comment: row.$1,
                    depth: row.$2,
                    collapsed: collapsed.contains(row.$1.id),
                    hiddenCount: row.$1.children.length,
                    onToggle: _collapse.toggle,
                  );
                },
              ),
            );
          },
        ),
      ),
    );
  }
}

class _ThreadLead extends StatelessWidget {
  final HnStory story;

  const _ThreadLead({required this.story});

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        _StoryHeader(story: story),
        const Divider(height: 1),
        if (story.text != null && story.text!.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
            child: Text(story.text!),
          ),
      ],
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
                    style: const TextStyle(color: hackerNewsBrand),
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
  final bool collapsed;
  final int hiddenCount;
  final void Function(int id) onToggle;

  const _CommentTile({
    required this.comment,
    required this.depth,
    required this.collapsed,
    required this.hiddenCount,
    required this.onToggle,
  });

  static const _maxIndentDepth = 8;
  static const _indentPerLevel = 12.0;

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final steps = depth.clamp(0, _maxIndentDepth);
    final railColor = hackerNewsBrand.withValues(
      alpha: 0.28 + (depth % 5) * 0.1,
    );
    return InkWell(
      onTap: () => onToggle(comment.id),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(8, 0, 12, 0),
        child: IntrinsicHeight(
          child: Row(
            crossAxisAlignment: CrossAxisAlignment.stretch,
            children: [
              for (var i = 0; i < steps; i++)
                Container(
                  width: _indentPerLevel,
                  alignment: Alignment.center,
                  child: Container(
                    width: 2,
                    color: i == steps - 1
                        ? railColor
                        : theme.dividerColor.withValues(alpha: 0.45),
                  ),
                ),
              Expanded(
                child: Padding(
                  padding: const EdgeInsets.fromLTRB(8, 8, 0, 8),
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
                          if (collapsed && hiddenCount > 0)
                            l10n.plugin_hn_comment_count(hiddenCount),
                        ].join(' · '),
                        style: theme.textTheme.labelMedium?.copyWith(
                          color: theme.hintColor,
                        ),
                      ),
                      if (!collapsed &&
                          !comment.deleted &&
                          (comment.text ?? '').isNotEmpty) ...[
                        const SizedBox(height: 4),
                        Text(comment.text!),
                      ],
                    ],
                  ),
                ),
              ),
            ],
          ),
        ),
      ),
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
