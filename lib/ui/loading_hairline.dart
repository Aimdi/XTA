import 'package:flutter/material.dart';

/// The height of the loading line drawn over a surface's top edge.
const kLoadingHairlineHeight = 2.0;

/// Shows that [child] is loading with a line drawn over its top edge, so the
/// line takes no room: nothing below it moves when loading starts or stops,
/// and no gap is kept for it while nothing loads.
///
/// The line has no track: only its moving part is drawn, so the content under
/// it stays visible, and two surfaces loading at once (a feed and the plugin
/// posts around it) both show. [value] makes it determinate; at zero it stays
/// indeterminate, since an empty line says nothing. [semanticsLabel] names
/// what loads.
///
/// [atBottom] draws it over the bottom edge instead, for a header whose
/// content loads below it.
class LoadingHairline extends StatelessWidget {
  final bool loading;
  final double? value;
  final String? semanticsLabel;
  final bool atBottom;
  final Widget child;

  const LoadingHairline({
    super.key,
    required this.loading,
    required this.child,
    this.value,
    this.semanticsLabel,
    this.atBottom = false,
  });

  @override
  Widget build(BuildContext context) {
    return Stack(
      // The child is laid out exactly as it would be without the line.
      fit: StackFit.passthrough,
      children: [
        child,
        if (loading)
          Positioned(
            top: atBottom ? null : 0,
            bottom: atBottom ? 0 : null,
            left: 0,
            right: 0,
            child: LinearProgressIndicator(
              minHeight: kLoadingHairlineHeight,
              value: value == 0 ? null : value,
              backgroundColor: Colors.transparent,
              semanticsLabel: semanticsLabel,
            ),
          ),
      ],
    );
  }
}
