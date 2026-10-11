import 'package:flutter/widgets.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/home/home_navigation_visibility.dart';
import 'package:xta/home/home_timeline_controls.dart';

/// The home header leaves with the bottom bar and comes back with it, as X's
/// does, so a feed being read gets the whole screen. It collapses instead of
/// sliding over the feed: the home body stays out of a NestedScrollView, which
/// once moved a newly picked plugin under the header.
///
/// Outside the home scaffold there is no bar to follow, and the header stays.
class HomeHidingHeader extends StatelessWidget {
  final Widget child;

  const HomeHidingHeader({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    final store = Provider.of<HomeNavigationVisibilityStore?>(context, listen: false);
    if (store == null) return child;
    return ScopedBuilder<HomeNavigationVisibilityStore, bool>(
      store: store,
      onState: (context, shown) => HomeCollapsingControls(visible: shown, child: child),
    );
  }
}
