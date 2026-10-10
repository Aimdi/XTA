import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_api.dart';
import 'package:xta/plugins/pixiv/pixiv_discovery_models.dart';
import 'package:xta/plugins/pixiv/pixiv_link_open.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_series_screen.dart';

/// Where one work sits in its series, once Pixiv says.
class PixivSeriesContextStore extends Store<PixivSeriesContext?> {
  final PixivDiscoveryApi api;

  PixivSeriesContextStore(this.api) : super(null);

  Future<void> load(int illustId) => execute(() => api.seriesContext(illustId));
}

/// "Series: <title>" as a link to the series page. [dense] is the one-line
/// form under a grid tile's title.
class PixivSeriesLink extends StatelessWidget {
  final PixivSeriesRef series;
  final bool dense;

  const PixivSeriesLink({super.key, required this.series, this.dense = false});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final style = (dense ? theme.textTheme.labelSmall : theme.textTheme.bodyMedium)!.copyWith(
      color: theme.colorScheme.primary,
      fontWeight: FontWeight.w600,
    );
    return InkWell(
      key: ValueKey('pixiv-series-link-${series.id}'),
      borderRadius: BorderRadius.circular(6),
      onTap: () => openPixivSeries(context, series.id),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kMinInteractiveDimension),
        child: Row(
          spacing: dense ? 4 : 6,
          children: [
            Icon(Icons.collections_bookmark_outlined, size: dense ? 14 : 18, color: theme.colorScheme.primary),
            Flexible(
              child: Text(
                series.title.isEmpty ? l10n.plugin_pixiv_series : l10n.plugin_pixiv_series_link(series.title),
                maxLines: dense ? 1 : 2,
                overflow: TextOverflow.ellipsis,
                style: style,
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// The detail's series block: the link to the series, then "#n of N" between
/// buttons to the previous and next works.
class PixivDetailSeries extends StatefulWidget {
  final PixivIllust illust;
  final PixivSeriesRef series;

  const PixivDetailSeries({super.key, required this.illust, required this.series});

  @override
  State<PixivDetailSeries> createState() => _PixivDetailSeriesState();
}

class _PixivDetailSeriesState extends State<PixivDetailSeries> {
  late final PixivSeriesContextStore _context;

  @override
  void initState() {
    super.initState();
    _context = PixivSeriesContextStore(PixivDiscoveryApi.of(context))..load(widget.illust.id);
  }

  @override
  void dispose() {
    _context.destroy();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => Column(
    crossAxisAlignment: CrossAxisAlignment.start,
    children: [
      PixivSeriesLink(series: widget.series),
      ScopedBuilder<PixivSeriesContextStore, PixivSeriesContext?>(
        store: _context,
        onState: (context, place) => place == null ? const SizedBox.shrink() : PixivSeriesNavigator(place: place),
      ),
    ],
  );
}

/// Previous and next buttons around the work's place in its series. Moving
/// along replaces the work rather than stacking another one.
class PixivSeriesNavigator extends StatelessWidget {
  final PixivSeriesContext place;

  const PixivSeriesNavigator({super.key, required this.place});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return Row(
      children: [
        _step(context, place.previous, Icons.chevron_left, l10n.plugin_pixiv_series_previous, 'previous'),
        if (place.total > 0)
          Flexible(
            child: Text(
              l10n.plugin_pixiv_series_position(place.order, place.total),
              key: const ValueKey('pixiv-series-position'),
              style: theme.textTheme.bodySmall!.copyWith(color: theme.colorScheme.onSurfaceVariant),
            ),
          ),
        _step(context, place.next, Icons.chevron_right, l10n.plugin_pixiv_series_next, 'next'),
      ],
    );
  }

  Widget _step(BuildContext context, PixivIllust? work, IconData icon, String label, String name) => IconButton(
    key: ValueKey('pixiv-series-$name'),
    tooltip: label,
    icon: Icon(icon),
    onPressed: work == null ? null : () => Navigator.pushReplacement(context, pixivIllustRoute(work)),
  );
}
