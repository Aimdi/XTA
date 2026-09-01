/// What a plugin tab paints while its first page is on the way.
///
/// A centred spinner on an otherwise blank pane says only "wait": the tab
/// jumps from nothing to a full feed, and until it does there is no telling
/// whether anything is coming. Post-shaped bones say what is coming and where
/// it will be, and the feed then fills in place rather than replacing a
/// screen. The timeline has done this since the skeleton landed; the plugin
/// tabs kept the spinner.
library;

import 'package:flutter/material.dart';
import 'package:xta/plugins/plugin_feed_insets.dart';
import 'package:xta/tweet/tweet_skeleton.dart';

/// Placeholder posts for a plugin feed's first paint.
///
/// Deliberately not attached to any [ScrollController]: it is a pane that
/// exists for a few hundred milliseconds, and the feed that replaces it owns
/// the scroll position.
class PluginFeedSkeleton extends StatefulWidget {
  final int count;

  /// Set where the tab sits under the home strip, so the bones start below
  /// the chrome the same way the real cards will.
  final bool applyFeedInsets;

  const PluginFeedSkeleton({
    super.key,
    this.count = 5,
    this.applyFeedInsets = true,
  });

  @override
  State<PluginFeedSkeleton> createState() => _PluginFeedSkeletonState();
}

class _PluginFeedSkeletonState extends State<PluginFeedSkeleton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    applySkeletonPulse(context, _pulse);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return IgnorePointer(
      child: ListView.builder(
        // Never the primary scrollable: under the home strip that is the
        // outer controller, and a placeholder must not take it over.
        primary: false,
        physics: const NeverScrollableScrollPhysics(),
        padding: widget.applyFeedInsets
            ? pluginFeedPadding(context)
            : EdgeInsets.zero,
        itemCount: widget.count,
        itemBuilder: (context, index) => TweetSkeletonTile(pulse: _pulse),
      ),
    );
  }
}

/// Placeholder tiles for a plugin tab that shows a grid rather than a feed.
class PluginGridSkeleton extends StatefulWidget {
  final int count;
  final int columns;
  final bool applyFeedInsets;

  const PluginGridSkeleton({
    super.key,
    this.count = 9,
    this.columns = 3,
    this.applyFeedInsets = true,
  });

  @override
  State<PluginGridSkeleton> createState() => _PluginGridSkeletonState();
}

class _PluginGridSkeletonState extends State<PluginGridSkeleton>
    with SingleTickerProviderStateMixin {
  late final AnimationController _pulse = AnimationController(
    vsync: this,
    duration: const Duration(milliseconds: 1100),
  );

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    applySkeletonPulse(context, _pulse);
  }

  @override
  void dispose() {
    _pulse.dispose();
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);

    return IgnorePointer(
      child: GridView.builder(
        primary: false,
        physics: const NeverScrollableScrollPhysics(),
        padding: widget.applyFeedInsets
            ? pluginFeedPadding(context).add(const EdgeInsets.all(4))
            : const EdgeInsets.all(4),
        gridDelegate: SliverGridDelegateWithFixedCrossAxisCount(
          crossAxisCount: widget.columns,
          crossAxisSpacing: 4,
          mainAxisSpacing: 4,
        ),
        itemCount: widget.count,
        itemBuilder: (context, index) => AnimatedBuilder(
          animation: _pulse,
          builder: (context, child) => DecoratedBox(
            decoration: BoxDecoration(
              borderRadius: BorderRadius.circular(8),
              color: Color.lerp(
                theme.colorScheme.surfaceContainerHighest,
                theme.colorScheme.surfaceContainerHigh,
                Curves.easeInOut.transform(_pulse.value),
              ),
            ),
          ),
        ),
      ),
    );
  }
}
