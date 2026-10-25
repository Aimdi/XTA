import 'package:flutter/widgets.dart';

/// A PrefSwitch or PrefCheckbox read as one control. The pref package puts the switch or the box beside the
/// title of a ListTile, so a screen reader would otherwise announce "On, switch" with no word of what it sets.
class LabeledPref extends StatelessWidget {
  final Widget child;

  const LabeledPref({super.key, required this.child});

  @override
  Widget build(BuildContext context) => MergeSemantics(child: child);
}
