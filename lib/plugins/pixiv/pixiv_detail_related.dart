import 'package:flutter/material.dart';
import 'package:flutter_staggered_grid_view/flutter_staggered_grid_view.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_grid_columns.dart';
import 'package:xta/plugins/pixiv/pixiv_illust_tile.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/pixiv/pixiv_store.dart';
import 'package:xta/ui/errors.dart';

/// "Similar works" at the foot of a work, as slivers. The work above stays
/// readable whatever happens here; a failure shows only while nothing loaded.
class PixivDetailRelated extends StatelessWidget {
  final PixivIllustListStore store;

  /// Why the work's own detail failed, shown when there is nothing below either.
  final Object? detailError;
  final Future<void> Function() onRetry;

  const PixivDetailRelated({super.key, required this.store, required this.onRetry, this.detailError});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<PixivIllustListStore, List<PixivIllust>>(
      store: store,
      onLoading: (context) => store.state.isEmpty ? _spinner(24) : _works(context, store.state),
      onError: (context, error) => store.state.isEmpty ? _error(context, error) : _works(context, store.state),
      onState: (context, works) => switch ((works.isEmpty, detailError)) {
        (false, _) => _works(context, works),
        (true, final error?) => _error(context, error),
        _ => const SliverToBoxAdapter(),
      },
    );
  }

  Widget _spinner(double padding) => SliverToBoxAdapter(
    child: Padding(
      padding: EdgeInsets.all(padding),
      child: const Center(child: CircularProgressIndicator()),
    ),
  );

  Widget _error(BuildContext context, Object? error) => SliverToBoxAdapter(
    child: Padding(
      padding: const EdgeInsets.all(16),
      child: FullPageErrorWidget(
        error: error,
        stackTrace: null,
        prefix: pixivErrorMessage(L10n.of(context), error ?? Exception()),
        onRetry: onRetry,
      ),
    ),
  );

  Widget _works(BuildContext context, List<PixivIllust> works) => SliverMainAxisGroup(
    slivers: [
      SliverToBoxAdapter(
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
          child: Text(
            L10n.of(context).plugin_pixiv_related,
            style: Theme.of(context).textTheme.titleMedium!.copyWith(fontWeight: FontWeight.w700),
          ),
        ),
      ),
      SliverPadding(
        padding: const EdgeInsets.fromLTRB(4, 0, 4, 24),
        sliver: SliverLayoutBuilder(
          builder: (context, constraints) => SliverMasonryGrid.count(
            crossAxisCount: pixivGridColumnsFor(context, constraints.crossAxisExtent),
            mainAxisSpacing: 4,
            crossAxisSpacing: 4,
            childCount: works.length,
            itemBuilder: (context, index) =>
                PixivIllustTile(illust: works[index], siblings: works, index: index, source: store),
          ),
        ),
      ),
      if (store.loadingMore) _spinner(16),
    ],
  );
}
