import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_viewing_prefs.dart';
import 'package:xta/plugins/plugin_view_store.dart';

/// How a work's detail is laid out: pictures beside the details on wide screens, always
/// above them, or beside them wherever both fit.
enum PixivDetailLayout { auto, vertical, split }

const pixivSplitMinWidth = 840.0;
const pixivSplitMinInfo = 320.0;
const pixivSplitMinImages = 240.0;
const pixivSplitGutter = 16.0;
const pixivSplitDefaultFraction = 0.64;
const _nudge = 0.05;

PixivDetailLayout pixivDetailLayout(BasePrefService? prefs) {
  final stored = prefs?.get<String>(optionPluginPixivDetailLayout);
  return PixivDetailLayout.values.where((layout) => layout.name == stored).firstOrNull ?? PixivDetailLayout.auto;
}

/// Whether a detail [width] wide puts its pictures beside the details.
bool pixivDetailSplits(double width, PixivDetailLayout layout) => switch (layout) {
  PixivDetailLayout.auto => width >= pixivSplitMinWidth,
  PixivDetailLayout.vertical => false,
  PixivDetailLayout.split => width >= pixivSplitMinImages + pixivSplitGutter + pixivSplitMinInfo,
};

/// The picture pane of a [width]-wide split at [fraction], leaving the details at least
/// [pixivSplitMinInfo] and the pictures at least [pixivSplitMinImages].
double pixivSplitImageWidth(double width, double fraction) {
  final widest = math.max(pixivSplitMinImages, width - pixivSplitGutter - pixivSplitMinInfo);
  return (width * fraction).clamp(pixivSplitMinImages, widest);
}

/// The share of the width the pictures last had, from the reader's last drag.
double pixivSplitFraction(BasePrefService? prefs) {
  final stored = prefs?.get<Object>(optionPluginPixivDetailSplit);
  return stored is num ? stored.toDouble().clamp(0.1, 0.9) : pixivSplitDefaultFraction;
}

/// A work's pictures beside its details, parted by a divider the reader can drag. Where it
/// was let go is kept for the next work.
class PixivDetailSplit extends StatefulWidget {
  final Widget images;
  final Widget info;

  const PixivDetailSplit({super.key, required this.images, required this.info});

  @override
  State<PixivDetailSplit> createState() => _PixivDetailSplitState();
}

class _PixivDetailSplitState extends State<PixivDetailSplit> {
  late final _fraction = PluginViewStore<double>(pixivSplitFraction(pixivPrefsOf(context)));

  @override
  void dispose() {
    _fraction.destroy();
    super.dispose();
  }

  void _resize(double width, double imagesWidth) =>
      _fraction.select(pixivSplitImageWidth(width, imagesWidth / width) / width);

  void _save() => pixivPrefsOf(context)?.set(optionPluginPixivDetailSplit, _fraction.state);

  void _nudgeBy(double width, double delta) {
    _resize(width, pixivSplitImageWidth(width, _fraction.state + delta));
    _save();
  }

  @override
  Widget build(BuildContext context) => LayoutBuilder(
    builder: (context, constraints) => ScopedBuilder<PluginViewStore<double>, double>(
      store: _fraction,
      onState: (context, fraction) {
        final width = constraints.maxWidth;
        final images = pixivSplitImageWidth(width, fraction);
        return Stack(
          children: [
            Row(
              children: [
                SizedBox(width: images, child: widget.images),
                const SizedBox(width: pixivSplitGutter),
                Expanded(child: widget.info),
              ],
            ),
            PositionedDirectional(
              start: images + pixivSplitGutter / 2 - kMinInteractiveDimension / 2,
              width: kMinInteractiveDimension,
              top: 0,
              bottom: 0,
              child: _handle(context, width, images),
            ),
          ],
        );
      },
    ),
  );

  Widget _handle(BuildContext context, double width, double images) {
    final rtl = Directionality.of(context) == TextDirection.rtl;
    return Semantics(
      key: const ValueKey('pixiv-detail-split-handle'),
      label: L10n.of(context).plugin_pixiv_split_divider,
      onIncrease: () => _nudgeBy(width, _nudge),
      onDecrease: () => _nudgeBy(width, -_nudge),
      child: MouseRegion(
        cursor: SystemMouseCursors.resizeColumn,
        child: GestureDetector(
          behavior: HitTestBehavior.translucent,
          onHorizontalDragUpdate: (details) =>
              _resize(width, pixivSplitImageWidth(width, _fraction.state) + details.delta.dx * (rtl ? -1 : 1)),
          onHorizontalDragEnd: (_) => _save(),
          child: Center(child: _grip(Theme.of(context).colorScheme)),
        ),
      ),
    );
  }

  Widget _grip(ColorScheme scheme) => DecoratedBox(
    decoration: BoxDecoration(color: scheme.outlineVariant, borderRadius: BorderRadius.circular(2)),
    child: const SizedBox(width: 4, height: 40),
  );
}
