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
    _store = MastodonThreadStore(context.read<MastodonClient>(),
      mastodonInstanceCandidates(widget.post.acct, configured: mastodonConfiguredInstances(prefs)), widget.post);
    _store.refresh();
  }

  @override
  void dispose() { _store.destroy(); _scroll.dispose(); super.dispose(); }

  @override
  Widget build(BuildContext context) => ScopedBuilder<MastodonThreadStore, MastodonThreadState>(
    store: _store,
    onState: (context, state) => Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).thread), actions: [
        IconButton(tooltip: L10n.of(context).open_in_browser, icon: const Icon(Icons.open_in_new),
          onPressed: () => openUri(context, state.thread.status.url)),
      ]),
      body: RefreshIndicator(onRefresh: _store.refresh, child: _body(context, state)),
    ),
  );

  Widget _body(BuildContext context, MastodonThreadState state) {
    final l10n = L10n.of(context);
    final colors = Theme.of(context).colorScheme;
    final thread = state.thread;
    final replies = mastodonReplyRows(thread, state.collapsed);
    final leading = <Widget>[
      if (thread.ancestors.isNotEmpty) ListTile(
        key: const ValueKey('mastodon-thread-context'),
        leading: const Icon(Icons.subdirectory_arrow_right),
        title: Text(l10n.replying_to), subtitle: Text('@${thread.ancestors.last.acct}', maxLines: 1,
          overflow: TextOverflow.ellipsis),
        trailing: Icon(state.ancestorsOpen ? Icons.expand_less : Icons.expand_more),
        onTap: _store.toggleAncestors,
      ),
      if (state.ancestorsOpen) ...thread.ancestors.map((post) => _post(post)),
      Container(key: const ValueKey('mastodon-thread-selected'),
        decoration: BoxDecoration(border: BorderDirectional(start: BorderSide(color: colors.primary, width: 3))),
        child: Padding(padding: const EdgeInsetsDirectional.only(start: 3), child: _post(thread.status, selected: true))),
      if (state.loading) const Padding(padding: EdgeInsets.all(16), child: Center(child: CircularProgressIndicator())),
      if (state.error != null) Padding(padding: const EdgeInsets.all(16),
        child: FullPageErrorWidget(error: state.error, stackTrace: null,
          prefix: mastodonErrorMessage(l10n, state.error!), onRetry: _store.refresh)),
      if (replies.isNotEmpty) Padding(padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
        child: Text(l10n.plugin_profile_replies, style: Theme.of(context).textTheme.titleSmall)),
    ];
    return FeedListView(controller: _scroll, physics: const AlwaysScrollableScrollPhysics(),
      padding: const EdgeInsets.only(bottom: 32), itemCount: leading.length + replies.length,
      itemBuilder: (context, index) {
        if (index < leading.length) return leading[index];
        final row = replies[index - leading.length];
        return Padding(padding: EdgeInsetsDirectional.only(start: 12.0 * row.depth.clamp(0, 2)),
          child: DecoratedBox(
            decoration: BoxDecoration(border: row.depth == 0 ? null : BorderDirectional(
              start: BorderSide(color: colors.outlineVariant, width: 2))),
            child: Column(crossAxisAlignment: CrossAxisAlignment.stretch, children: [
              _post(row.post),
              if (row.descendants > 0) Align(alignment: AlignmentDirectional.centerStart,
                child: TextButton.icon(key: ValueKey('mastodon-collapse-${row.post.id}'),
                  onPressed: () => _store.toggle(row.post.id),
                  icon: Icon(row.collapsed ? Icons.expand_more : Icons.expand_less),
                  label: Text('${row.collapsed ? l10n.show : l10n.hide} · ${row.descendants}'))),
            ]),
          ),
        );
      },
    );
  }

  Widget _post(MastodonPost post, {bool selected = false}) => MastodonPostCard(
    key: ValueKey('thread-post-${post.id}'), post: post, showSourceBadge: false, openOnTap: !selected,
    onOpen: selected ? () {} : null,
  );
}
