import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/mastodon/mastodon_client.dart';
import 'package:xta/plugins/mastodon/mastodon_models.dart';
import 'package:xta/plugins/mastodon/mastodon_post_card.dart';
import 'package:xta/plugins/mastodon/mastodon_profile_screen.dart';
import 'package:xta/plugins/mastodon/mastodon_store.dart';
import 'package:xta/plugins/mastodon/mastodon_thread_store.dart';
import 'package:xta/ui/errors.dart';
import 'package:xta/ui/feed_list.dart';
import 'package:xta/utils/urls.dart';

class MastodonThreadScreen extends StatefulWidget {
  final MastodonPost post;
  const MastodonThreadScreen({super.key, required this.post});
  @override
  State<MastodonThreadScreen> createState() => _MastodonThreadScreenState();
}

class _MastodonThreadScreenState extends State<MastodonThreadScreen> {
  late final MastodonThreadStore _store;
  final _scroll = ScrollController();

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _store = MastodonThreadStore(
      context.read<MastodonClient>(),
      mastodonInstanceCandidates(widget.post.acct, configured: mastodonConfiguredInstances(prefs)),
      widget.post,
    );
    _store.refresh();
  }

  @override
  void dispose() {
    _store.destroy();
    _scroll.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<MastodonThreadStore, MastodonThreadState>(
    store: _store,
    onState: (context, state) => Scaffold(
      appBar: AppBar(
        title: Text(L10n.of(context).thread),
        actions: [
          IconButton(
            key: const ValueKey('mastodon-thread-jump'),
            tooltip: L10n.of(context).mastodon_thread_selected,
            icon: const Icon(Icons.vertical_align_top),
            onPressed: _returnToSelected,
          ),
          IconButton(
            tooltip: L10n.of(context).open_in_browser,
            icon: const Icon(Icons.open_in_new),
            onPressed: () => openUri(context, state.thread.status.url),
          ),
          PopupMenuButton<bool>(
            key: const ValueKey('mastodon-thread-branches'),
            enabled: state.thread.descendants.isNotEmpty,
            onSelected: _store.setAllExpanded,
            itemBuilder: (context) => [
              PopupMenuItem(value: true, child: Text(L10n.of(context).mastodon_thread_expand_all)),
              PopupMenuItem(value: false, child: Text(L10n.of(context).mastodon_thread_collapse_all)),
            ],
          ),
        ],
      ),
      body: RefreshIndicator(onRefresh: _store.refresh, child: _body(context, state)),
    ),
  );

  void _returnToSelected() {
    _store.focusSelected();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted || !_scroll.hasClients) return;
      if (MediaQuery.disableAnimationsOf(context)) {
        _scroll.jumpTo(0);
      } else {
        _scroll.animateTo(0, duration: const Duration(milliseconds: 250), curve: Curves.easeOut);
      }
    });
  }

  Widget _body(BuildContext context, MastodonThreadState state) {
    final l10n = L10n.of(context);
    final colors = Theme.of(context).colorScheme;
    final thread = state.thread;
    final replies = mastodonReplyRows(thread, state.collapsed, authorOnly: state.authorOnly);
    final ancestors = mastodonThreadAncestors(thread);
    final leading = <Widget>[
      if (ancestors.isNotEmpty)
        ListTile(
          key: const ValueKey('mastodon-thread-context'),
          leading: const Icon(Icons.subdirectory_arrow_right),
          title: Text(l10n.replying_to),
          subtitle: Text('@${ancestors.last.acct}', maxLines: 1, overflow: TextOverflow.ellipsis),
          trailing: Icon(state.ancestorsOpen ? Icons.expand_less : Icons.expand_more),
          onTap: _store.toggleAncestors,
        ),
      if (state.ancestorsOpen) ...ancestors.map((post) => _post(post)),
      Container(
        key: const ValueKey('mastodon-thread-selected'),
        decoration: BoxDecoration(
          border: BorderDirectional(start: BorderSide(color: colors.primary, width: 3)),
        ),
        child: Padding(
          padding: const EdgeInsetsDirectional.only(start: 3),
          child: _post(thread.status, selected: true),
        ),
      ),
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
            prefix: mastodonErrorMessage(l10n, state.error!),
            onRetry: _store.refresh,
          ),
        ),
      if (thread.descendants.isNotEmpty) _filters(context, state),
      if (replies.isNotEmpty)
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
          child: Text(l10n.plugin_profile_replies, style: Theme.of(context).textTheme.titleSmall),
        ),
      if (!state.loading && state.error == null && replies.isEmpty)
        Padding(
          key: const ValueKey('mastodon-thread-empty'),
          padding: const EdgeInsets.all(24),
          child: Text(
            state.authorOnly ? l10n.mastodon_thread_no_author_replies : l10n.mastodon_thread_no_replies,
            textAlign: TextAlign.center,
            style: Theme.of(context).textTheme.bodyMedium?.copyWith(color: colors.onSurfaceVariant),
          ),
        ),
    ];
    return FeedListView(
      controller: _scroll,
      physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: 32),
      itemCount: leading.length + replies.length,
      itemBuilder: (context, index) {
        if (index < leading.length) return leading[index];
        final row = replies[index - leading.length];
        return Padding(
          padding: EdgeInsetsDirectional.only(start: 12.0 * row.depth.clamp(0, 2)),
          child: DecoratedBox(
            decoration: BoxDecoration(
              border: row.depth == 0
                  ? null
                  : BorderDirectional(start: BorderSide(color: colors.outlineVariant, width: 2)),
            ),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                if (row.contextOnly)
                  Padding(
                    padding: const EdgeInsetsDirectional.fromSTEB(16, 8, 16, 0),
                    child: Text(
                      l10n.mastodon_thread_context,
                      style: Theme.of(context).textTheme.labelMedium?.copyWith(color: colors.onSurfaceVariant),
                    ),
                  ),
                _post(row.post),
                if (row.descendants > 0)
                  Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: TextButton.icon(
                      key: ValueKey('mastodon-collapse-${row.post.id}'),
                      onPressed: () => _store.toggle(row.post.id),
                      icon: Icon(row.collapsed ? Icons.expand_more : Icons.expand_less),
                      label: Text('${row.collapsed ? l10n.show : l10n.hide} · ${row.descendants}'),
                    ),
                  ),
              ],
            ),
          ),
        );
      },
    );
  }

  Widget _filters(BuildContext context, MastodonThreadState state) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 0),
      child: Wrap(
        spacing: 8,
        runSpacing: 4,
        children: [
          ChoiceChip(
            key: const ValueKey('mastodon-thread-all'),
            label: Text(l10n.all),
            selected: !state.authorOnly,
            onSelected: (_) => _store.selectAuthor(false),
          ),
          ChoiceChip(
            key: const ValueKey('mastodon-thread-author'),
            label: Text(l10n.mastodon_thread_author),
            selected: state.authorOnly,
            onSelected: (_) => _store.selectAuthor(true),
          ),
        ],
      ),
    );
  }

  Widget _post(MastodonPost post, {bool selected = false}) => MastodonPostCard(
    key: ValueKey('thread-post-${post.id}'),
    post: post,
    showSourceBadge: false,
    openOnTap: !selected,
    onOpen: selected ? () {} : null,
  );
}
