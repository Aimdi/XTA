import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_avatar.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_sheet.dart';
import 'package:xta/plugins/pixiv/pixiv_mute_store.dart';
import 'package:xta/plugins/pixiv/pixiv_search_store.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/plugin_search_history.dart';

/// Search before anything is searched: recent searches, suggested creators
/// and trending tags. Pulling down reloads the last two, each on its own.
class PixivSearchLanding extends StatelessWidget {
  final PixivSearchStore store;
  final PluginSearchHistoryStore history;
  final bool historyExpanded;
  final ValueChanged<String> onSearch;

  const PixivSearchLanding({
    super.key,
    required this.store,
    required this.history,
    required this.historyExpanded,
    required this.onSearch,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return RefreshIndicator(
      onRefresh: () => store.loadLanding(force: true),
      child: ListView(
        physics: const AlwaysScrollableScrollPhysics(),
        padding: const EdgeInsets.all(24),
        children: [
          Text(l10n.plugin_pixiv_search_prompt, textAlign: TextAlign.center),
          const SizedBox(height: 24),
          ScopedBuilder<PluginSearchHistoryStore, List<String>>(
            store: history,
            onState: (context, queries) => _PixivSearchHistory(
              queries: queries,
              expanded: historyExpanded,
              onSearch: onSearch,
              onForget: history.forget,
              onClear: history.clear,
              onToggle: store.toggleHistory,
            ),
          ),
          _PixivLandingSection<PixivUser>(
            title: l10n.plugin_pixiv_recommended_users,
            store: store.creators,
            height: 88,
            builder: (context, users) => _PixivCreatorStrip(users: users),
          ),
          _PixivLandingSection<PixivTrendTag>(
            title: l10n.plugin_pixiv_trending_title,
            store: store.trending,
            height: 120,
            builder: (context, tags) => ScopedBuilder<PixivMuteStore, PixivMuteState>(
              store: context.read<PixivMuteStore>(),
              onState: (context, mute) =>
                  _PixivTrendingGrid(tags: pixivVisibleTrendTags(tags, mute), onSearch: onSearch),
            ),
          ),
        ],
      ),
    );
  }
}

class _PixivSearchHistory extends StatelessWidget {
  final List<String> queries;
  final bool expanded;
  final ValueChanged<String> onSearch;
  final ValueChanged<String> onForget;
  final Future<void> Function() onClear;
  final VoidCallback onToggle;

  const _PixivSearchHistory({
    required this.queries,
    required this.expanded,
    required this.onSearch,
    required this.onForget,
    required this.onClear,
    required this.onToggle,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final folds = queries.length > pixivSearchHistoryFolded;
    final shown = expanded || !folds ? queries : queries.take(pixivSearchHistoryFolded);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(l10n.plugin_pixiv_search_history, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        if (queries.isEmpty)
          Text(l10n.plugin_pixiv_search_history_empty)
        else ...[
          Wrap(
            spacing: 8,
            runSpacing: 8,
            children: [
              for (final query in shown)
                GestureDetector(
                  onLongPress: () => onForget(query),
                  child: ActionChip(label: Text(query), onPressed: () => onSearch(query)),
                ),
              if (folds)
                ActionChip(
                  key: const ValueKey('pixiv-search-history-fold'),
                  avatar: Icon(expanded ? Icons.expand_less : Icons.expand_more),
                  label: Text(
                    expanded
                        ? l10n.plugin_pixiv_search_history_fewer
                        : l10n.plugin_pixiv_search_history_all(queries.length),
                  ),
                  onPressed: onToggle,
                ),
            ],
          ),
          const SizedBox(height: 4),
          TextButton.icon(
            key: const ValueKey('pixiv-search-history-clear'),
            onPressed: () => _clear(context),
            icon: const Icon(Icons.delete_outline),
            label: Text(l10n.clear_recent_searches),
          ),
        ],
      ],
    );
  }

  Future<void> _clear(BuildContext context) async {
    if (await confirmPixivAction(context, L10n.of(context).clear_recent_searches)) await onClear();
  }
}

/// A landing list with its own loading and its own retry: one failing leaves
/// the rest of the landing as it is. An empty answer shows nothing.
class _PixivLandingSection<T> extends StatelessWidget {
  final String title;
  final PixivLandingStore<T> store;
  final double height;
  final Widget Function(BuildContext context, List<T> items) builder;

  const _PixivLandingSection({required this.title, required this.store, required this.height, required this.builder});

  @override
  Widget build(BuildContext context) => ScopedBuilder<PixivLandingStore<T>, List<T>>(
    store: store,
    onLoading: (context) => store.state.isEmpty
        ? _framed(
            context,
            SizedBox(
              height: height,
              child: const Center(child: CircularProgressIndicator()),
            ),
          )
        : _framed(context, builder(context, store.state)),
    onError: (context, error) => store.state.isEmpty
        ? _framed(context, _retry(context, error))
        : _framed(context, builder(context, store.state)),
    onState: (context, items) => items.isEmpty ? const SizedBox.shrink() : _framed(context, builder(context, items)),
  );

  Widget _framed(BuildContext context, Widget child) => Padding(
    padding: const EdgeInsets.only(top: 24),
    child: Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(title, style: Theme.of(context).textTheme.titleSmall),
        const SizedBox(height: 8),
        child,
      ],
    ),
  );

  Widget _retry(BuildContext context, Object? error) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Row(
      children: [
        Icon(Icons.error_outline, color: theme.colorScheme.error),
        const SizedBox(width: 12),
        Expanded(child: Text(pixivErrorMessage(l10n, error ?? Exception()), style: theme.textTheme.bodyMedium)),
        TextButton(onPressed: store.load, child: Text(l10n.retry)),
      ],
    );
  }
}

/// Suggested creators in a row, before the trending tags.
class _PixivCreatorStrip extends StatelessWidget {
  final List<PixivUser> users;

  const _PixivCreatorStrip({required this.users});

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    return SizedBox(
      height: 88 * MediaQuery.textScalerOf(context).scale(1).clamp(1.0, 1.6),
      child: ListView.separated(
        scrollDirection: Axis.horizontal,
        itemCount: users.length,
        separatorBuilder: (_, _) => const SizedBox(width: 12),
        itemBuilder: (context, index) {
          final user = users[index];
          return InkWell(
            onTap: () => openPixivUser(context, user.id),
            child: SizedBox(
              width: 72,
              child: Column(
                children: [
                  PixivAvatar.user(user, size: 56),
                  const SizedBox(height: 6),
                  Text(
                    user.name,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.labelSmall,
                    textAlign: TextAlign.center,
                  ),
                ],
              ),
            ),
          );
        },
      ),
    );
  }
}

/// Each trending tag over the work Pixiv picked for it, its translation
/// under the name. A tap searches the tag; a long press opens the work.
class _PixivTrendingGrid extends StatelessWidget {
  final List<PixivTrendTag> tags;
  final ValueChanged<String> onSearch;

  const _PixivTrendingGrid({required this.tags, required this.onSearch});

  @override
  Widget build(BuildContext context) => GridView.builder(
    shrinkWrap: true,
    physics: const NeverScrollableScrollPhysics(),
    gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
      crossAxisCount: 3,
      mainAxisSpacing: 4,
      crossAxisSpacing: 4,
    ),
    itemCount: tags.length,
    itemBuilder: (context, index) => _tile(context, tags[index]),
  );

  Widget _tile(BuildContext context, PixivTrendTag tag) {
    final illust = tag.illust;
    return InkWell(
      key: ValueKey('pixiv-trend-${tag.name}'),
      borderRadius: BorderRadius.circular(8),
      onTap: () => onSearch(tag.name),
      onLongPress: illust == null ? null : () => openPixivIllust(context, illust),
      child: ClipRRect(
        borderRadius: BorderRadius.circular(8),
        child: Stack(
          fit: StackFit.expand,
          children: [
            if (illust != null)
              PixivNetworkImage(
                url: illust.thumbnailUrl,
                fit: BoxFit.cover,
                cacheWidth: (140 * MediaQuery.devicePixelRatioOf(context)).ceil(),
              )
            else
              ColoredBox(color: Theme.of(context).colorScheme.surfaceContainerHighest),
            Align(alignment: Alignment.bottomCenter, child: _caption(tag)),
          ],
        ),
      ),
    );
  }

  Widget _caption(PixivTrendTag tag) {
    final translated = tag.translatedName?.trim() ?? '';
    return Container(
      width: double.infinity,
      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 4),
      color: Colors.black.withValues(alpha: 0.6),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            '#${tag.name}',
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
            style: const TextStyle(color: Colors.white, fontSize: 12, fontWeight: FontWeight.w600),
          ),
          if (translated.isNotEmpty && translated != tag.name)
            Text(
              translated,
              maxLines: 1,
              overflow: TextOverflow.ellipsis,
              style: const TextStyle(color: Colors.white70, fontSize: 11),
            ),
        ],
      ),
    );
  }
}
