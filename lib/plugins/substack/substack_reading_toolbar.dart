import 'package:xta/plugins/plugin_home_dock.dart';
import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/plugin_home_chrome.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_home_controls.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';

/// One row above the articles; less frequent reading controls open on demand.
class SubstackReadingToolbar extends StatelessWidget {
  final String slot;
  final SubstackFeedStore feed;
  final List<SubstackPublication> publications;
  final Set<String> readIds;
  final ValueChanged<SubstackFeedFilter>? onFilter;
  final bool publishToHome;

  const SubstackReadingToolbar({
    super.key,
    required this.slot,
    required this.feed,
    required this.publications,
    required this.readIds,
    this.onFilter,
    this.publishToHome = false,
  });

  Future<void> _openPublications(BuildContext context) async {
    final pubs = context.read<SubstackPublicationsStore>();
    final publication = await showModalBottomSheet<SubstackPublication>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * .75),
      builder: (context) => SafeArea(
        top: false,
        child: _PublicationPicker(
          publications: publications,
          posts: feed.allPosts,
          readIds: readIds,
          pinnedIds: publications.where((pub) => pubs.isPinned(pub.id)).map((pub) => pub.id).toSet(),
        ),
      ),
    );
    if (publication == null || !context.mounted) return;
    await Navigator.push(context, MaterialPageRoute(builder: (_) => SubstackArchiveScreen(publication: publication)));
    if (context.mounted) await feed.refresh(force: false);
  }

  Future<void> _openOptions(BuildContext context, {bool autofocus = false}) {
    final controls = context.read<SubstackHomeControlsStore>();
    return showModalBottomSheet<void>(
      context: context,
      isScrollControlled: true,
      useSafeArea: true,
      showDragHandle: true,
      constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * .9),
      builder: (context) => Provider<SubstackHomeControlsStore>.value(
        value: controls,
        child: SafeArea(
          top: false,
          child: _ReadingOptions(slot: slot, feed: feed, onFilter: onFilter, autofocus: autofocus),
        ),
      ),
    );
  }

  Future<void> _showLoadFailure(BuildContext context) async {
    final l10n = L10n.of(context);
    final retry = await showDialog<bool>(
      context: context,
      builder: (context) => AlertDialog(
        title: Text(l10n.plugin_substack_partial_error(feed.state.failedCount)),
        actions: [
          TextButton(onPressed: () => Navigator.pop(context, false), child: Text(l10n.close)),
          FilledButton(onPressed: feed.refreshing ? null : () => Navigator.pop(context, true), child: Text(l10n.retry)),
        ],
      ),
    );
    if (retry == true && context.mounted) await feed.refresh(force: true);
  }

  @override
  Widget build(BuildContext context) {
    if (PluginHomeDockScope.maybeOf(context) != null && !publishToHome) return const SizedBox.shrink();
    final l10n = L10n.of(context);
    final options = context.read<SubstackHomeControlsStore>().options(slot);
    final filtered =
        options.order != SubstackLoadedOrder.newest || (onFilter != null && feed.filter != SubstackFeedFilter.all);
    final fallback = Padding(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      child: Row(
        children: [
          Expanded(
            child: Align(
              alignment: AlignmentDirectional.centerStart,
              child: TextButton.icon(
                style: TextButton.styleFrom(
                  foregroundColor: Theme.of(context).colorScheme.onSurfaceVariant,
                  minimumSize: const Size(48, 48),
                ),
                onPressed: () => _openPublications(context),
                icon: const Icon(Icons.newspaper_outlined, size: 20),
                label: Text(l10n.plugin_substack_library_following, maxLines: 1, overflow: TextOverflow.ellipsis),
              ),
            ),
          ),
          if (feed.state.failedCount > 0)
            IconButton(
              style: pluginActionButtonStyle,
              tooltip: l10n.plugin_substack_partial_error(feed.state.failedCount),
              onPressed: () => _showLoadFailure(context),
              icon: Icon(Icons.warning_amber_outlined, color: Theme.of(context).colorScheme.error),
            ),
          IconButton(
            style: pluginActionButtonStyle,
            tooltip: l10n.search,
            onPressed: () => _openOptions(context, autofocus: true),
            icon: Badge(
              isLabelVisible: options.query.trim().isNotEmpty,
              backgroundColor: Theme.of(context).colorScheme.primary,
              child: const Icon(Icons.search),
            ),
          ),
          IconButton(
            style: pluginActionButtonStyle,
            tooltip: l10n.filters,
            onPressed: () => _openOptions(context),
            icon: Badge(
              isLabelVisible: filtered,
              backgroundColor: Theme.of(context).colorScheme.primary,
              child: const Icon(Icons.tune),
            ),
          ),
        ],
      ),
    );
    return PluginDockContribution(
      slot: 'reading',
      content: PluginDockContent(
        leading: TextButton.icon(
          style: TextButton.styleFrom(minimumSize: const Size(48, 48)),
          onPressed: () => _openPublications(context),
          icon: const Icon(Icons.newspaper_outlined, size: 20),
          label: Text(l10n.plugin_substack_library_following, maxLines: 1, overflow: TextOverflow.ellipsis),
        ),
        search: IconButton(
          style: pluginActionButtonStyle,
          tooltip: l10n.plugin_mastodon_loaded_search,
          onPressed: () => _openOptions(context, autofocus: true),
          icon: Badge(isLabelVisible: options.query.trim().isNotEmpty, child: const Icon(Icons.search)),
        ),
        trailing: [
          if (feed.state.failedCount > 0)
            IconButton(
              style: pluginActionButtonStyle,
              tooltip: l10n.plugin_substack_partial_error(feed.state.failedCount),
              onPressed: () => _showLoadFailure(context),
              icon: Icon(Icons.warning_amber_outlined, color: Theme.of(context).colorScheme.error),
            ),
          PluginDockFilterButton(
            activeCount:
                (options.query.trim().isEmpty ? 0 : 1) +
                (options.order == SubstackLoadedOrder.newest ? 0 : 1) +
                (onFilter != null && feed.filter != SubstackFeedFilter.all ? 1 : 0),
            onPressed: () => _openOptions(context),
          ),
        ],
      ),
      fallback: fallback,
    );
  }
}

/// The published actions outlive recycled list items, but not their active pane.
class SubstackHomeReadingDock extends StatelessWidget {
  final String slot;
  final ValueChanged<SubstackFeedFilter>? onFilter;
  const SubstackHomeReadingDock({super.key, required this.slot, this.onFilter});

  @override
  Widget build(BuildContext context) {
    if (PluginHomeDockScope.maybeOf(context) == null) return const SizedBox.shrink();
    final feed = context.read<SubstackFeedStore>();
    return TripleBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
      store: context.read<SubstackPublicationsStore>(),
      builder: (context, pubs) => pubs.state.isEmpty
          ? const SizedBox.shrink()
          : TripleBuilder<SubstackFeedStore, SubstackFeedSnapshot>(
              store: feed,
              builder: (context, _) => TripleBuilder<SubstackReadStore, Set<String>>(
                store: context.read<SubstackReadStore>(),
                builder: (context, read) => SubstackReadingToolbar(
                  key: ValueKey(slot),
                  slot: slot,
                  feed: feed,
                  publications: pubs.state,
                  readIds: read.state,
                  onFilter: onFilter,
                  publishToHome: true,
                ),
              ),
            ),
    );
  }
}

class _SheetHeading extends StatelessWidget {
  final String title;
  const _SheetHeading(this.title);

  @override
  Widget build(BuildContext context) => Padding(
    padding: const EdgeInsetsDirectional.only(start: 16, end: 8),
    child: Row(
      children: [
        Expanded(child: Text(title, style: Theme.of(context).textTheme.titleLarge)),
        IconButton(
          style: pluginActionButtonStyle,
          tooltip: L10n.of(context).close,
          onPressed: () => Navigator.pop(context),
          icon: const Icon(Icons.close),
        ),
      ],
    ),
  );
}

class _ReadingOptions extends StatelessWidget {
  final String slot;
  final SubstackFeedStore feed;
  final ValueChanged<SubstackFeedFilter>? onFilter;
  final bool autofocus;
  const _ReadingOptions({required this.slot, required this.feed, required this.onFilter, required this.autofocus});

  @override
  Widget build(BuildContext context) {
    final controls = context.read<SubstackHomeControlsStore>();
    final l10n = L10n.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SingleChildScrollView(
        padding: const EdgeInsets.only(bottom: 16),
        child: ScopedBuilder<SubstackHomeControlsStore, SubstackHomeOptions>(
          store: controls,
          onState: (context, _) => ScopedBuilder<SubstackFeedStore, SubstackFeedSnapshot>(
            store: feed,
            onState: (context, _) => Column(
              mainAxisSize: MainAxisSize.min,
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                _SheetHeading(l10n.filters),
                SubstackLoadedControls(slot: slot, autofocus: autofocus),
                if (onFilter != null) _ContentFilters(selected: feed.filter, onSelected: onFilter!),
                Padding(
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  child: Align(
                    alignment: AlignmentDirectional.centerStart,
                    child: TextButton.icon(
                      onPressed: () {
                        controls.configure(slot, const SubstackLoadedOptions());
                        onFilter?.call(SubstackFeedFilter.all);
                      },
                      icon: const Icon(Icons.filter_list_off),
                      label: Text(l10n.plugin_reader_reset_filters),
                    ),
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class _ContentFilters extends StatelessWidget {
  final SubstackFeedFilter selected;
  final ValueChanged<SubstackFeedFilter> onSelected;
  const _ContentFilters({required this.selected, required this.onSelected});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final labels = {
      SubstackFeedFilter.all: l10n.plugin_substack_filter_all,
      SubstackFeedFilter.unread: l10n.plugin_substack_filter_unread,
      SubstackFeedFilter.free: l10n.plugin_substack_filter_free,
      SubstackFeedFilter.podcast: l10n.plugin_substack_filter_podcast,
      SubstackFeedFilter.video: l10n.videos,
    };
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
      child: Wrap(
        spacing: 8,
        children: [
          for (final filter in SubstackFeedFilter.values)
            ChoiceChip(
              label: Text(labels[filter]!),
              selected: selected == filter,
              showCheckmark: true,
              materialTapTargetSize: MaterialTapTargetSize.padded,
              onSelected: (_) => onSelected(filter),
            ),
        ],
      ),
    );
  }
}

class _PublicationPicker extends StatelessWidget {
  final List<SubstackPublication> publications;
  final List<SubstackPost> posts;
  final Set<String> readIds;
  final Set<String> pinnedIds;
  const _PublicationPicker({
    required this.publications,
    required this.posts,
    required this.readIds,
    required this.pinnedIds,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final unreadPublications = posts
        .where((post) => !readIds.contains(post.id))
        .map((post) => post.publicationBaseUrl.toLowerCase())
        .toSet();
    return Column(
      mainAxisSize: MainAxisSize.min,
      children: [
        _SheetHeading(l10n.plugin_substack_library_following),
        Flexible(
          child: ListView.builder(
            shrinkWrap: true,
            padding: const EdgeInsets.only(bottom: 16),
            itemCount: publications.length,
            itemBuilder: (context, index) {
              final pub = publications[index];
              final unread = unreadPublications.contains(pub.baseUrl.toLowerCase());
              return ListTile(
                leading: Badge(
                  isLabelVisible: unread,
                  backgroundColor: Theme.of(context).colorScheme.primary,
                  child: ClipRRect(
                    borderRadius: BorderRadius.circular(8),
                    child: pub.logoUrl == null
                        ? const SizedBox.square(dimension: 36, child: Icon(Icons.newspaper_outlined))
                        : ExtendedImage.network(
                            pub.logoUrl!,
                            width: 36,
                            height: 36,
                            fit: BoxFit.cover,
                            cacheWidth: (36 * MediaQuery.devicePixelRatioOf(context)).ceil(),
                          ),
                  ),
                ),
                title: Text(pub.displayName),
                subtitle: unread ? Text(l10n.plugin_substack_filter_unread) : null,
                trailing: pinnedIds.contains(pub.id) ? const Icon(Icons.push_pin_outlined, size: 18) : null,
                onTap: () => Navigator.pop(context, pub),
              );
            },
          ),
        ),
      ],
    );
  }
}
