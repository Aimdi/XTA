import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/scheduler.dart';
import 'package:xta/home/home_chrome.dart';
import 'package:xta/ui/height_probe.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// Floats [notice] over the top edge of [child] instead of laying it out
/// above it, so a failure that leaves the content readable never pushes that
/// content down, and nothing moves when it comes or goes.
///
/// [notice] is placed as given: wrap what should show in a [NoticePill]. It
/// may stay mounted while it shows nothing (a [ReadRecovery] keeping its retry
/// budget, say); an empty notice is drawn as nothing.
///
/// A floating notice inside another one's [child] — the home feed's plugin
/// chips over its X notice over its cached posts — floats below the outer
/// one's pill instead of on it. Nested notices are assumed to share the outer
/// one's top edge, as surfaces filling the same feed do.
class FloatingNotice extends StatefulWidget {
  final Widget? notice;
  final Widget child;

  const FloatingNotice({super.key, required this.notice, required this.child});

  @override
  State<FloatingNotice> createState() => _FloatingNoticeState();
}

class _FloatingNoticeState extends State<FloatingNotice> {
  static final _none = ValueNotifier<double>(0);

  /// This notice's height while it shows something.
  final _height = ValueNotifier<double>(0);

  /// Where notices inside [FloatingNotice.child] start: below every pill above.
  final _below = ValueNotifier<double>(0);

  ValueListenable<double> _above = _none;

  @override
  void initState() {
    super.initState();
    _height.addListener(_place);
  }

  @override
  void didChangeDependencies() {
    super.didChangeDependencies();
    final above = _NoticeShelf.maybeOf(context) ?? _none;
    if (identical(above, _above)) return;
    _above.removeListener(_place);
    _above = above..addListener(_place);
    _place();
  }

  @override
  void didUpdateWidget(FloatingNotice oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.notice == null) _measured(0);
  }

  @override
  void dispose() {
    _above.removeListener(_place);
    _height.dispose();
    _below.dispose();
    super.dispose();
  }

  void _place() {
    final height = _height.value;
    _below.value = height > 0 ? _above.value + height + kTweetSpace2 : _above.value;
  }

  // Measured in layout, applied after the frame: a notice changing size moves
  // the ones below it a frame later, never mid-layout.
  void _measured(double height) {
    if (height == _height.value) return;
    SchedulerBinding.instance.addPostFrameCallback((_) {
      if (mounted) _height.value = height;
    });
  }

  @override
  Widget build(BuildContext context) {
    final notice = widget.notice;
    return Stack(
      // The child is laid out exactly as it would be without the notice.
      fit: StackFit.passthrough,
      children: [
        _NoticeShelf(below: _below, child: widget.child),
        if (notice != null)
          ValueListenableBuilder<double>(
            valueListenable: _above,
            builder: (context, above, _) => Positioned(
              // Below the status bar when the content runs under it.
              top: MediaQuery.paddingOf(context).top + kTweetSpace2 + above,
              left: kTweetSpace3,
              right: kTweetSpace3,
              child: Center(
                child: HeightProbe(onHeight: _measured, child: notice),
              ),
            ),
          ),
      ],
    );
  }
}

/// A notice's surface while it floats over content: the frosted pill Home's
/// navigation bar sits on, sized to what it holds, and announced when it
/// appears since nothing moves to draw the eye.
class NoticePill extends StatelessWidget {
  final Widget child;

  const NoticePill({super.key, required this.child});

  @override
  Widget build(BuildContext context) {
    return Semantics(
      container: true,
      liveRegion: true,
      child: FrostedPill(
        palette: HomeNavigationPalette.of(context),
        height: kTweetTouchTarget,
        // A notice row would otherwise stretch the pill across the screen.
        child: IntrinsicWidth(child: child),
      ),
    );
  }
}

class _NoticeShelf extends InheritedWidget {
  final ValueListenable<double> below;

  const _NoticeShelf({required this.below, required super.child});

  static ValueListenable<double>? maybeOf(BuildContext context) =>
      context.dependOnInheritedWidgetOfExactType<_NoticeShelf>()?.below;

  @override
  bool updateShouldNotify(_NoticeShelf oldWidget) => !identical(below, oldWidget.below);
}
