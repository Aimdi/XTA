import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_in_flight.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/plugins/plugin_counts.dart';
import 'package:xta/plugins/plugin_view_store.dart';
import 'package:xta/ui/motion.dart';

/// Which of the reader's bookmarks Favorites shows: public or private, and
/// one tag or (null) all of them.
typedef PixivBookmarkFilter = ({String restrict, String? tag});

const _restricts = ['public', 'private'];

/// How close to the end of the list the next page is asked for.
const _pageOnExtent = 600.0;

/// What a tag filter is called on its chip and in the picker.
String pixivBookmarkTagLabel(L10n l10n, String? tag) => switch (tag) {
  null => l10n.plugin_pixiv_bookmark_tag_all,
  pixivUnclassifiedTag => l10n.plugin_pixiv_bookmark_tag_unclassified,
  final named => named,
};

/// Asks which bookmarks to show: Public and Private tabs of the reader's
/// tags, a field that suggests matches and takes any typed tag.
Future<PixivBookmarkFilter?> showPixivBookmarkTagPicker(BuildContext context, PixivBookmarkFilter current) {
  final api = PixivBookmarkApi.of(context);
  return showModalBottomSheet<PixivBookmarkFilter>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    useSafeArea: true,
    builder: (_) => PixivBookmarkTagPicker(api: api, current: current),
  );
}

class PixivBookmarkTagPicker extends StatefulWidget {
  final PixivBookmarkApi api;
  final PixivBookmarkFilter current;

  const PixivBookmarkTagPicker({super.key, required this.api, required this.current});

  @override
  State<PixivBookmarkTagPicker> createState() => _PixivBookmarkTagPickerState();
}

class _PixivBookmarkTagPickerState extends State<PixivBookmarkTagPicker> {
  late final Map<String, PixivPagedListStore<PixivBookmarkTag>> _tags = {
    for (final restrict in _restricts)
      restrict: PixivPagedListStore(
        ({nextUrl}) => widget.api.tags(restrict: restrict, nextUrl: nextUrl),
        keyOf: (tag) => tag.name,
      ),
  };
  final _query = PluginViewStore<String>('');
  final _field = TextEditingController();
  final _inFlight = PixivInFlight();

  @override
  void dispose() {
    _field.dispose();
    final stores = <Store<Object?>>[..._tags.values, _query];
    _inFlight.whenSettled(() {
      for (final store in stores) {
        store.destroy();
      }
    });
    super.dispose();
  }

  void _pick(String restrict, String? tag) =>
      Navigator.pop<PixivBookmarkFilter>(context, (restrict: restrict, tag: tag));

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.8,
        child: DefaultTabController(
          length: _restricts.length,
          initialIndex: _restricts.indexOf(widget.current.restrict).clamp(0, _restricts.length - 1),
          animationDuration: xtaMotionDuration(context, kTabScrollDuration),
          child: Builder(builder: (context) => _sheet(context, l10n)),
        ),
      ),
    );
  }

  /// The title, field and tabs scroll away with the tags, so a short sheet
  /// (landscape with the keyboard up, or large text) still fits them.
  Widget _sheet(BuildContext context, L10n l10n) => NestedScrollView(
    headerSliverBuilder: (_, _) => [
      SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
          child: Text(l10n.plugin_pixiv_bookmark_tag_filter, style: Theme.of(context).textTheme.titleLarge),
        ),
      ),
      SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 16),
          child: TextField(
            key: const ValueKey('pixiv-bookmark-tag-search'),
            controller: _field,
            autocorrect: false,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              hintText: l10n.plugin_pixiv_bookmark_tag_search,
              prefixIcon: const Icon(Icons.search),
            ),
            onChanged: _query.select,
            onSubmitted: (text) {
              final tag = text.trim();
              if (tag.isNotEmpty) _pick(_restricts[DefaultTabController.of(context).index], tag);
            },
          ),
        ),
      ),
      SliverToBoxAdapter(
        child: TabBar(
          tabs: [
            Tab(text: l10n.plugin_pixiv_bookmarks_public),
            Tab(text: l10n.plugin_pixiv_bookmarks_private),
          ],
        ),
      ),
    ],
    body: TabBarView(
      children: [
        for (final restrict in _restricts)
          _PixivBookmarkTagTab(
            store: _tags[restrict]!,
            inFlight: _inFlight,
            query: _query,
            selected: widget.current.restrict == restrict ? widget.current.tag : '',
            onPick: (tag) => _pick(restrict, tag),
          ),
      ],
    ),
  );
}

/// One visibility's tags: All and Unclassified on top, then the reader's tags
/// page by page, or up to eight matches and the typed tag while searching.
class _PixivBookmarkTagTab extends StatefulWidget {
  final PixivPagedListStore<PixivBookmarkTag> store;
  final PixivInFlight inFlight;
  final PluginViewStore<String> query;

  /// The tag shown now on this tab: null for All, empty when it is the other tab's.
  final String? selected;
  final ValueChanged<String?> onPick;

  const _PixivBookmarkTagTab({
    required this.store,
    required this.inFlight,
    required this.query,
    required this.selected,
    required this.onPick,
  });

  @override
  State<_PixivBookmarkTagTab> createState() => _PixivBookmarkTagTabState();
}

class _PixivBookmarkTagTabState extends State<_PixivBookmarkTagTab> {
  late final void Function() _stopWatchingQuery;

  /// The list as last laid out, to tell whether it fills the sheet.
  ScrollMetrics? _metrics;

  /// The search and list length the tab last asked for a page at by itself.
  (String, int)? _askedAt;

  PixivPagedListStore<PixivBookmarkTag> get _store => widget.store;

  /// Shows the spinner from the start, so an empty list is never mistaken
  /// for a reader without tags while the first page is on its way.
  Future<void> _refresh() async {
    _store.setLoading(true);
    await widget.inFlight.track(_store.refresh());
    await _pageOnWhileShort();
  }

  Future<void> _loadMore() async {
    await widget.inFlight.track(_store.loadMore());
    await _pageOnWhileShort();
  }

  /// Tags that do not fill the sheet never scroll, nor do a search's few
  /// matches, so the tab asks for pages itself until the list fills or the
  /// search has its matches. A page that brings nothing (it failed) stops it
  /// until the reader types or scrolls again.
  Future<void> _pageOnWhileShort() async {
    await WidgetsBinding.instance.endOfFrame;
    if (!mounted || !_store.hasMore || _store.loadingMore) return;
    final query = widget.query.state.trim();
    final tags = _store.state;
    final short = query.isEmpty
        ? (_metrics?.extentAfter ?? 0) < _pageOnExtent
        : pixivBookmarkTagMatches(tags, query).length < pixivTagSuggestionLimit;
    if (!short || _askedAt == (query, tags.length)) return;
    _askedAt = (query, tags.length);
    await _loadMore();
  }

  bool _onScroll(ScrollMetrics metrics) {
    _metrics = metrics;
    if (metrics.extentAfter < _pageOnExtent) _loadMore();
    return false;
  }

  @override
  void initState() {
    super.initState();
    _stopWatchingQuery = widget.query.observer(onState: (_) => _pageOnWhileShort());
    if (_store.state.isEmpty && !_store.isLoading) {
      _refresh();
    } else {
      _pageOnWhileShort();
    }
  }

  @override
  void dispose() {
    _stopWatchingQuery();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<PluginViewStore<String>, String>(
    store: widget.query,
    onState: (context, query) => ScopedBuilder<PixivPagedListStore<PixivBookmarkTag>, List<PixivBookmarkTag>>(
      store: _store,
      onState: (context, tags) => _list(context, query.trim(), tags),
      onLoading: (context) => _list(context, query.trim(), _store.state, loading: true),
      onError: (context, error) => _list(context, query.trim(), _store.state, error: error as Object),
    ),
  );

  Widget _list(
    BuildContext context,
    String query,
    List<PixivBookmarkTag> tags, {
    bool loading = false,
    Object? error,
  }) => NotificationListener<ScrollMetricsNotification>(
    onNotification: (notification) => _onScroll(notification.metrics),
    child: NotificationListener<ScrollNotification>(
      onNotification: (notification) => _onScroll(notification.metrics),
      child: ListView(
        children: query.isEmpty
            ? _browse(context, tags, loading: loading, error: error)
            : _search(context, query, tags),
      ),
    ),
  );

  List<Widget> _browse(BuildContext context, List<PixivBookmarkTag> tags, {required bool loading, Object? error}) {
    final l10n = L10n.of(context);
    return [
      _tile(context, icon: Icons.collections_outlined, tag: null),
      _tile(context, icon: Icons.label_off_outlined, tag: pixivUnclassifiedTag),
      const Divider(height: 1),
      for (final tag in tags) _tile(context, icon: Icons.sell_outlined, tag: tag.name, count: tag.count),
      if (loading || _store.loadingMore)
        const Padding(
          padding: EdgeInsets.all(16),
          child: Center(child: CircularProgressIndicator()),
        ),
      if (error != null && tags.isEmpty)
        ListTile(
          title: Text(pixivErrorMessage(l10n, error)),
          trailing: TextButton(onPressed: _refresh, child: Text(l10n.retry)),
        ),
      if (error == null && !loading && tags.isEmpty)
        ListTile(enabled: false, title: Text(l10n.plugin_pixiv_bookmark_tags_none)),
    ];
  }

  List<Widget> _search(BuildContext context, String query, List<PixivBookmarkTag> tags) {
    final matches = pixivBookmarkTagMatches(tags, query);
    return [
      for (final tag in matches) _tile(context, icon: Icons.sell_outlined, tag: tag.name, count: tag.count),
      if (!matches.any((tag) => tag.name == query))
        ListTile(
          key: const ValueKey('pixiv-bookmark-tag-use'),
          leading: const Icon(Icons.search),
          title: Text(L10n.of(context).plugin_pixiv_bookmark_tag_use(query)),
          onTap: () => widget.onPick(query),
        ),
    ];
  }

  Widget _tile(BuildContext context, {required IconData icon, required String? tag, int? count}) => ListTile(
    key: ValueKey('pixiv-bookmark-tag-pick-${tag ?? ''}'),
    leading: Icon(icon),
    title: Text(pixivBookmarkTagLabel(L10n.of(context), tag)),
    trailing: count == null ? null : Text(compactCount(count)),
    selected: widget.selected == tag,
    onTap: () => widget.onPick(tag),
  );
}
