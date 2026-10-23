import 'package:flutter/widgets.dart';

/// The furthest a restore moves a feed in one frame.
///
/// A list of posts lays out every post between the old offset and the new one
/// when it jumps, about 4.5 ms per 1,000 px in a profile build. Restoring a
/// place far down a long feed in one jump held a single frame for seconds; in
/// a debug build long enough for the compositor to call the app unresponsive.
const double kMaxScrollStepPerFrame = 12000;

/// The next offset on the way from [current] to [target], at most [maxStep] away.
double nextScrollStep(double current, double target, {double maxStep = kMaxScrollStepPerFrame}) {
  final distance = target - current;
  return distance.abs() <= maxStep ? target : current + maxStep * distance.sign;
}

/// Moves [position] to [target] a frame at a time, so the app keeps painting
/// and answering input on the way. [target] is re-clamped every frame because
/// a list of estimated extent grows as it lays out. Stops as soon as
/// [cancelled] says so, such as when the reader starts scrolling, and calls
/// [onArrived] once [target] is reached.
void steppedJumpTo(
  ScrollPosition position,
  double target, {
  required bool Function() cancelled,
  VoidCallback? onArrived,
  double maxStep = kMaxScrollStepPerFrame,
}) {
  if (cancelled() || !position.hasContentDimensions) return;
  final goal = target.clamp(position.minScrollExtent, position.maxScrollExtent);
  final next = nextScrollStep(position.pixels, goal, maxStep: maxStep);
  position.jumpTo(next);
  if (next == goal) {
    onArrived?.call();
    return;
  }
  WidgetsBinding.instance.addPostFrameCallback(
    (_) => steppedJumpTo(position, target, cancelled: cancelled, onArrived: onArrived, maxStep: maxStep),
  );
  WidgetsBinding.instance.ensureVisualUpdate();
}
