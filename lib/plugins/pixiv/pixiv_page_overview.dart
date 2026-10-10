import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_image.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_page_selection.dart';

const _gutter = 16.0;
const _spacing = 10.0;
const _maxTile = 132.0;
const _radius = 12.0;

/// Columns for a sheet [width] wide: never fewer than three thumbnails a row.
int pixivOverviewColumns(double width) => ((width - 2 * _gutter + _spacing) / (_maxTile + _spacing)).ceil().clamp(3, 8);

/// Scroll offset that brings [page]'s row near the middle of a [viewport]-tall sheet.
double pixivOverviewOffset({
  required int page,
  required int columns,
  required double width,
  required double aspectRatio,
  required double headerHeight,
  required double viewport,
}) {
  final tileWidth = (width - 2 * _gutter - _spacing * (columns - 1)) / columns;
  final tileHeight = tileWidth / aspectRatio;
  final rowTop = headerHeight + (page ~/ columns) * (tileHeight + _spacing);
  if (rowTop + tileHeight <= viewport) return 0;
  return rowTop - (viewport - tileHeight) / 2;
}

/// Every page of a work as a thumbnail grid, with the work's page actions pinned below.
/// Select pages turns the grid into a picker (from the start when [selecting]) whose
/// ticked pages come back as [PixivPageChoice.save].
Future<PixivPageChoice?> showPixivPageOverview(
  BuildContext context, {
  required PixivIllust illust,
  required int currentPage,
  bool readVertically = true,
  bool selecting = false,
}) => showModalBottomSheet<PixivPageChoice>(
  context: context,
  showDragHandle: true,
  isScrollControlled: true,
  useSafeArea: true,
  builder: (context) =>
      PixivPageOverview(illust: illust, currentPage: currentPage, readVertically: readVertically, selecting: selecting),
);

class PixivPageOverview extends StatefulWidget {
  final PixivIllust illust;
  final int currentPage;
  final bool readVertically;
  final bool selecting;

  const PixivPageOverview({
    super.key,
    required this.illust,
    required this.currentPage,
    this.readVertically = true,
    this.selecting = false,
  });

  @override
  State<PixivPageOverview> createState() => _PixivPageOverviewState();
}

class _PixivPageOverviewState extends State<PixivPageOverview> {
  final _scroll = ScrollController();
  final _header = GlobalKey();
  late final _selection = PixivPageSelectionStore(_pages.length);

  List<String> get _pages => widget.illust.viewerUrls;
  double get _aspect => widget.illust.aspectRatio.clamp(0.7, 1.0);

  @override
  void initState() {
    super.initState();
    if (widget.selecting) _selection.start();
    WidgetsBinding.instance.addPostFrameCallback((_) => _revealCurrent());
  }

  @override
  void dispose() {
    _scroll.dispose();
    _selection.destroy();
    super.dispose();
  }

  void _revealCurrent() {
    final header = _header.currentContext?.size?.height;
    if (!mounted || header == null || !_scroll.hasClients) return;
    final position = _scroll.position;
    final width = context.size?.width ?? MediaQuery.sizeOf(context).width;
    final target = pixivOverviewOffset(
      page: widget.currentPage,
      columns: pixivOverviewColumns(width),
      width: width,
      aspectRatio: _aspect,
      headerHeight: header,
      viewport: position.viewportDimension,
    );
    _scroll.jumpTo(target.clamp(0.0, position.maxScrollExtent));
  }

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.85),
      child: ScopedBuilder<PixivPageSelectionStore, PixivPageSelection>(
        store: _selection,
        onState: (context, selection) => Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Flexible(
              child: LayoutBuilder(
                builder: (context, constraints) =>
                    _pagesGrid(context, pixivOverviewColumns(constraints.maxWidth), selection),
              ),
            ),
            if (selection.active) _selectionBar(context, selection) else _actionBar(context),
          ],
        ),
      ),
    );
  }

  void _jump(int page) => Navigator.pop(context, PixivPageChoice.jump(page));

  Widget _pagesGrid(BuildContext context, int columns, PixivPageSelection selection) => CustomScrollView(
    controller: _scroll,
    shrinkWrap: true,
    slivers: [
      SliverToBoxAdapter(
        child: KeyedSubtree(key: _header, child: _headerSection(context, selection)),
      ),
      SliverPadding(
        padding: const EdgeInsets.fromLTRB(_gutter, 0, _gutter, _gutter),
        sliver: SliverGrid.builder(
          gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
            crossAxisCount: columns,
            mainAxisSpacing: _spacing,
            crossAxisSpacing: _spacing,
            childAspectRatio: _aspect,
          ),
          itemCount: _pages.length,
          itemBuilder: (context, index) => PixivPageThumb(
            illust: widget.illust,
            page: index,
            current: index == widget.currentPage,
            selected: selection.active ? selection.pages.contains(index) : null,
            onTap: selection.active ? () => _selection.toggle(index) : () => _jump(index),
            // Picking keeps a way to the page itself.
            onLongPress: () => _jump(index),
          ),
        ),
      ),
    ],
  );

  Widget _headerSection(BuildContext context, PixivPageSelection selection) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final title = widget.illust.title.isEmpty ? l10n.plugin_pixiv_title : widget.illust.title;
    return Padding(
      padding: const EdgeInsets.fromLTRB(_gutter, 0, _gutter, 16),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            header: true,
            child: Text(
              title,
              maxLines: 2,
              overflow: TextOverflow.ellipsis,
              style: theme.textTheme.titleMedium!.copyWith(fontWeight: FontWeight.w700),
            ),
          ),
          const SizedBox(height: 2),
          Semantics(
            liveRegion: selection.active,
            child: Text(
              selection.active
                  ? l10n.plugin_pixiv_selected_pages(selection.pages.length)
                  : l10n.plugin_pixiv_pages(_pages.length),
              style: theme.textTheme.bodyMedium!.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
        ],
      ),
    );
  }

  /// Large text gets full-width rows so no label has to be cut short.
  bool _stacked(BuildContext context) => MediaQuery.textScalerOf(context).scale(10) > 13;

  Widget _actionBar(BuildContext context) {
    final actions = [
      PixivPageAction.downloadPage,
      if (_pages.length > 1) ...[PixivPageAction.downloadAll, PixivPageAction.selectPages],
      PixivPageAction.direction,
    ];
    final stacked = _stacked(context);
    return _bar(
      context,
      padding: const EdgeInsets.fromLTRB(8, 8, 8, 4),
      direction: stacked ? Axis.vertical : Axis.horizontal,
      children: [
        for (final action in actions)
          Flexible(
            fit: stacked ? FlexFit.loose : FlexFit.tight,
            child: PixivSheetAction(
              key: ValueKey('pixiv-overview-${action.name}'),
              icon: pixivPageActionIcon(action, readVertically: widget.readVertically),
              label: pixivPageActionLabel(L10n.of(context), action, readVertically: widget.readVertically),
              inline: stacked,
              // Picking happens right here; every other action closes the sheet first.
              onTap: action == PixivPageAction.selectPages
                  ? _selection.start
                  : () => Navigator.pop(context, PixivPageChoice.action(action)),
            ),
          ),
      ],
    );
  }

  /// Cancel and All take their labels' width and Save the rest; large text stacks them full width.
  Widget _selectionBar(BuildContext context, PixivPageSelection selection) {
    final l10n = L10n.of(context);
    final count = selection.pages.length;
    final stacked = _stacked(context);
    final style = OutlinedButton.styleFrom(
      minimumSize: const Size(0, kMinInteractiveDimension),
      padding: const EdgeInsets.symmetric(horizontal: 12),
    );
    return _bar(
      context,
      padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
      direction: stacked ? Axis.vertical : Axis.horizontal,
      spacing: 8,
      children: [
        OutlinedButton(
          key: const ValueKey('pixiv-overview-select-cancel'),
          style: style,
          onPressed: _selection.stop,
          child: Text(l10n.cancel),
        ),
        OutlinedButton(
          key: const ValueKey('pixiv-overview-select-all'),
          style: style,
          onPressed: _selection.toggleAll,
          child: Text(_selection.allSelected ? l10n.plugin_pixiv_select_none : l10n.all),
        ),
        Flexible(
          fit: stacked ? FlexFit.loose : FlexFit.tight,
          child: FilledButton.icon(
            key: const ValueKey('pixiv-overview-save-selected'),
            style: FilledButton.styleFrom(minimumSize: const Size(0, kMinInteractiveDimension)),
            onPressed: count == 0 ? null : () => Navigator.pop(context, PixivPageChoice.save(selection.ordered)),
            icon: const Icon(Icons.download_outlined),
            label: Text(l10n.plugin_pixiv_save_pages(count), textAlign: TextAlign.center),
          ),
        ),
      ],
    );
  }

  Widget _bar(
    BuildContext context, {
    required EdgeInsets padding,
    required Axis direction,
    required List<Widget> children,
    double spacing = 0,
  }) => DecoratedBox(
    decoration: BoxDecoration(
      border: Border(top: BorderSide(color: Theme.of(context).dividerColor)),
    ),
    child: SafeArea(
      top: false,
      child: Padding(
        padding: padding,
        child: Flex(
          direction: direction,
          mainAxisSize: MainAxisSize.min,
          spacing: spacing,
          crossAxisAlignment: direction == Axis.vertical ? CrossAxisAlignment.stretch : CrossAxisAlignment.start,
          children: children,
        ),
      ),
    ),
  );
}

/// An icon in a soft accent well with its label below (or beside it when [inline]).
class PixivSheetAction extends StatelessWidget {
  final IconData icon;
  final String label;
  final bool inline;
  final VoidCallback onTap;

  const PixivSheetAction({
    super.key,
    required this.icon,
    required this.label,
    required this.onTap,
    this.inline = false,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final well = Container(
      width: 56,
      height: 40,
      decoration: BoxDecoration(color: theme.colorScheme.primaryContainer, borderRadius: BorderRadius.circular(20)),
      child: Icon(icon, size: 22, color: theme.colorScheme.primary),
    );
    final text = Text(
      label,
      textAlign: inline ? TextAlign.start : TextAlign.center,
      maxLines: inline ? null : 3,
      overflow: inline ? null : TextOverflow.ellipsis,
      style: theme.textTheme.labelMedium!.copyWith(color: theme.colorScheme.onSurface),
    );
    return Semantics(
      button: true,
      label: label,
      onTap: onTap,
      excludeSemantics: true,
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: onTap,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(4, 6, 4, 8),
          child: inline
              ? Row(
                  children: [
                    well,
                    const SizedBox(width: 12),
                    Expanded(child: text),
                  ],
                )
              : Column(mainAxisSize: MainAxisSize.min, children: [well, const SizedBox(height: 6), text]),
        ),
      ),
    );
  }
}

/// A rounded page thumbnail with its number; the current page wears an accent
/// ring. While pages are picked, [selected] is non-null and the ring and a
/// check mark show whether this page is ticked.
class PixivPageThumb extends StatelessWidget {
  final PixivIllust illust;
  final int page;
  final bool current;
  final bool? selected;
  final VoidCallback onTap;
  final VoidCallback? onLongPress;

  const PixivPageThumb({
    super.key,
    required this.illust,
    required this.page,
    required this.current,
    required this.onTap,
    this.selected,
    this.onLongPress,
  });

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final pages = illust.viewerUrls.length;
    final ringed = selected ?? current;
    return Semantics(
      button: true,
      selected: selected == null && current,
      checked: selected,
      label: L10n.of(context).plugin_pixiv_current_page(page + 1, pages),
      onTap: onTap,
      onLongPress: onLongPress,
      excludeSemantics: true,
      child: DecoratedBox(
        key: ValueKey('pixiv-overview-page-$page'),
        decoration: BoxDecoration(
          borderRadius: BorderRadius.circular(_radius + 3),
          border: Border.all(color: ringed ? scheme.primary : Colors.transparent, width: 2),
        ),
        child: Padding(
          padding: const EdgeInsets.all(3),
          child: ClipRRect(
            borderRadius: BorderRadius.circular(_radius - 2),
            child: Stack(
              fit: StackFit.expand,
              children: [
                ColoredBox(color: scheme.surfaceContainerHighest),
                _image(context),
                PositionedDirectional(start: 6, bottom: 6, child: _badge(context)),
                if (selected case final ticked?) PositionedDirectional(top: 6, end: 6, child: _check(context, ticked)),
                Material(
                  type: MaterialType.transparency,
                  child: InkWell(onTap: onTap, onLongPress: onLongPress),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }

  Widget _image(BuildContext context) => PixivNetworkImage(
    url: illust.thumbUrlAt(page),
    fit: BoxFit.cover,
    loadStateChanged: (state) => pixivTileLoadState(context, state),
  );

  Widget _check(BuildContext context, bool ticked) {
    final scheme = Theme.of(context).colorScheme;
    return Container(
      key: ValueKey('pixiv-overview-check-$page'),
      width: 24,
      height: 24,
      decoration: BoxDecoration(
        shape: BoxShape.circle,
        color: ticked ? scheme.primary : Colors.black.withValues(alpha: 0.45),
        border: Border.all(color: ticked ? scheme.primary : Colors.white, width: 2),
      ),
      child: ticked ? Icon(Icons.check, size: 16, color: scheme.onPrimary) : null,
    );
  }

  Widget _badge(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: current ? scheme.primary : Colors.black.withValues(alpha: 0.62),
        borderRadius: BorderRadius.circular(8),
      ),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 7, vertical: 2),
        child: Text(
          '${page + 1}',
          style: Theme.of(context).textTheme.labelMedium!.copyWith(
            color: current ? scheme.onPrimary : Colors.white,
            fontWeight: FontWeight.w700,
          ),
        ),
      ),
    );
  }
}
