import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/plugins/plugin_feed_skeleton.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/mixed_feed_sources.dart';
import 'package:xta/reading/mixed_feed_view_store.dart';
import 'package:xta/reading/shared_filter_scope.dart';
import 'package:xta/tweet/tweet_context_scope.dart';
import 'package:xta/ui/empty_pane.dart';

const IconData mixedFeedIcon = Icons.dynamic_feed_outlined;

/// A mix's posts in one list, each shown with its plugin's own card. Editing the mix's name or order keeps what is
/// loaded and the scroll position; new sources are read, removed ones forgotten.
class MixedFeedView extends StatefulWidget {
  final MixedFeedDefinition definition;
  final ScrollController? scrollController;
  final VoidCallback? onEdit;

  /// The kinds sources are read with; the registered kinds unless a test provides its own.
  final List<MixedSourceKind>? kinds;

  const MixedFeedView({super.key, required this.definition, this.scrollController, this.onEdit, this.kinds});

  @override
  State<MixedFeedView> createState() => _MixedFeedViewState();
}

class _MixedFeedViewState extends State<MixedFeedView> {
  late final MixedFeedViewStore _store;
  final _paging = SharedFilterPagingGuard();

  MixedSourceKind? _kind(String id) => mixedSourceKindById(id, kinds: widget.kinds);

  MixedSourceReader? _readerFor(MixedFeedSource source) => mixedSourceReader(context, source, kinds: widget.kinds);

  @override
  void initState() {
    super.initState();
    _store = MixedFeedViewStore(widget.definition, _readerFor);
    _store.start();
  }

  @override
  void didUpdateWidget(MixedFeedView oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (!identical(oldWidget.definition, widget.definition)) {
      _store.updateDefinition(widget.definition, _readerFor);
    }
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  MixedFeedSource _sourceOf(MixedPick pick) => _store.definition.sources[pick.slot];

  String _textOf(MixedPick pick) => _kind(_sourceOf(pick).kind)?.filterText(pick.entry) ?? '';

  Widget _card(BuildContext context, MixedPick pick) =>
      _kind(_sourceOf(pick).kind)?.card(context, pick.entry) ?? const SizedBox.shrink();

  /// Reads further once the end of the list is near, unless the filters hid everything the last reads brought.
  void _nearEnd(List<MixedPick> shown, Set<MixedPick> visible) {
    final allowed = _paging.allowFetch(
      shown.length,
      (from) => shown.skip(from).every((pick) => !visible.contains(pick)),
    );
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      allowed ? _store.loadMore() : _store.hold(true);
    });
  }

  void _release() {
    if (!_paging.release()) return;
    _store.hold(false);
    _store.loadMore();
  }

  /// Sources whose account, server or members changed since they were read are read again, after this frame.
  void _followSignatures() {
    final signatures = [
      for (final source in widget.definition.sources)
        if (_kind(source.kind) case final kind?) (source, mixedSourceSignature(context, kind, source)),
    ];
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      for (final (source, signature) in signatures) {
        _store.follow(source, signature);
      }
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    _followSignatures();
    return Column(
      children: [
        PluginHomeChrome(
          title: widget.definition.name,
          mark: const Icon(mixedFeedIcon),
          actions: [
            IconButton(
              key: const ValueKey('mixed-feed-refresh'),
              tooltip: l10n.mixed_feed_refresh,
              icon: const Icon(Icons.refresh),
              onPressed: _store.refresh,
            ),
            if (widget.onEdit != null)
              IconButton(
                key: const ValueKey('mixed-feed-edit'),
                tooltip: l10n.mixed_feed_edit,
                icon: const Icon(Icons.tune),
                onPressed: widget.onEdit,
              ),
          ],
        ),
        Expanded(
          child: ScopedBuilder<MixedFeedViewStore, MixedFeedViewState>(store: _store, onState: _body),
        ),
      ],
    );
  }

  Widget _body(BuildContext context, MixedFeedViewState state) {
    if (!state.started) return const PluginFeedSkeleton();
    final footer = _MixedFeedFooter(
      state: state,
      onRetry: _store.retry,
      onRelease: _release,
      onNearEnd: () {
        final projection = sharedFilterProject(context, state.shown, _textOf);
        _nearEnd(state.shown, Set<MixedPick>.identity()..addAll(projection.visible));
      },
    );
    if (state.shown.isEmpty) {
      return EmptyPane(
        icon: mixedFeedIcon,
        message: L10n.of(context).mixed_feed_empty,
        scrollController: widget.scrollController,
        onRefresh: _store.refresh,
        action: footer,
      );
    }
    return TweetContextScope(
      child: RefreshIndicator(
        onRefresh: _store.refresh,
        child: NotificationListener<UserScrollNotification>(
          onNotification: (_) {
            if (state.held) _release();
            return false;
          },
          child: SharedFilterFeedList<MixedPick>(
            controller: widget.scrollController,
            padding: pluginFeedPadding(context),
            physics: const AlwaysScrollableScrollPhysics(),
            items: state.shown,
            textOf: _textOf,
            keyOf: (pick) => pick.entry.identity,
            itemBuilder: (context, pick, _) => _card(context, pick),
            footer: footer,
          ),
        ),
      ),
    );
  }
}

/// The end of a mix: sources that failed or are unavailable, with retry, and loading further when it comes into view.
class _MixedFeedFooter extends StatelessWidget {
  final MixedFeedViewState state;
  final void Function(MixedFeedSource source) onRetry;
  final VoidCallback onRelease;
  final VoidCallback onNearEnd;

  const _MixedFeedFooter({
    required this.state,
    required this.onRetry,
    required this.onRelease,
    required this.onNearEnd,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final loading = state.sources.any((source) => source.loading);
    if (!state.finished && !state.held && !loading) onNearEnd();
    return Column(
      mainAxisSize: MainAxisSize.min,
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        for (final source in state.sources)
          if (source.failed)
            ListTile(
              key: ValueKey('mixed-feed-failed-${source.source.key}'),
              minTileHeight: 48,
              leading: const Icon(Icons.error_outline),
              title: Text(l10n.mixed_feed_source_failed(source.source.label)),
              trailing: TextButton(
                style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
                onPressed: () => onRetry(source.source),
                child: Text(l10n.retry),
              ),
            )
          else if (!source.available)
            ListTile(
              minTileHeight: 48,
              leading: const Icon(Icons.block),
              title: Text(l10n.mixed_feed_source_unavailable(source.source.label)),
            ),
        if (state.held)
          SharedFilterHeldPaging(onLoadMore: onRelease)
        else if (loading)
          const Padding(
            padding: EdgeInsets.all(16),
            child: Center(child: CircularProgressIndicator()),
          ),
      ],
    );
  }
}
