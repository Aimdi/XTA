import 'package:flutter/material.dart';
import 'package:xta/ui/motion.dart';

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
///
/// The line fades in as loading starts and out once it ends; known progress
/// glides from one value to the next and fills to the end before it goes.
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
        Positioned(
          top: atBottom ? null : 0,
          bottom: atBottom ? 0 : null,
          left: 0,
          right: 0,
          child: _Hairline(loading: loading, value: value == 0 ? null : value, semanticsLabel: semanticsLabel),
        ),
      ],
    );
  }
}

class _Hairline extends StatefulWidget {
  final bool loading;
  final double? value;
  final String? semanticsLabel;

  const _Hairline({required this.loading, required this.value, required this.semanticsLabel});

  @override
  State<_Hairline> createState() => _HairlineState();
}

class _HairlineState extends State<_Hairline> {
  /// On screen from the start of loading until the line has faded out, and
  /// gone after: an indeterminate line left behind would animate unseen.
  late bool _shown = widget.loading;

  /// The progress shown, kept while the line fades out.
  late double? _value = widget.value;

  @override
  void didUpdateWidget(_Hairline oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.loading) {
      _shown = true;
      _value = widget.value;
    } else if (_value != null) {
      _value = 1;
    }
  }

  void _faded() {
    // After the frame: with reduced motion the fade ends inside a build.
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted && !widget.loading) setState(() => _shown = false);
    });
  }

  @override
  Widget build(BuildContext context) {
    if (!_shown) return const SizedBox.shrink();
    final value = _value;
    return XtaFadeIn(
      duration: kXtaMotionFast,
      child: AnimatedOpacity(
        opacity: widget.loading ? 1 : 0,
        // Known progress takes the first half to fill to the end.
        duration: xtaMotionDuration(context, value == null ? kXtaMotionFast : kXtaMotionFast * 2),
        curve: value == null ? Curves.linear : const Interval(0.5, 1),
        onEnd: _faded,
        child: value == null
            ? _line(null)
            : TweenAnimationBuilder<double>(
                tween: Tween(end: value),
                duration: xtaMotionDuration(context, kXtaMotionFast),
                curve: Curves.easeOutCubic,
                builder: (context, value, _) => _line(value),
              ),
      ),
    );
  }

  Widget _line(double? value) => LinearProgressIndicator(
    minHeight: kLoadingHairlineHeight,
    value: value,
    backgroundColor: Colors.transparent,
    semanticsLabel: widget.semanticsLabel,
  );
}
