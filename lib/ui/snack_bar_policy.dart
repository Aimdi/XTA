import 'dart:async';

import 'package:flutter/material.dart';

/// How long a message stays before it leaves on its own.
const kSnackBarDuration = Duration(seconds: 3);

/// How long a message with a button (Undo, Retry) gives you to press it.
const kSnackBarActionDuration = Duration(seconds: 5);

/// A snackbar for work still under way. Its caller replaces it when the work ends, so it is never timed out.
class WorkingSnackBar extends SnackBar {
  const WorkingSnackBar({super.key, required super.content}) : super(duration: const Duration(minutes: 2));
}

/// How long [snackBar] may stay on screen, or null to leave it to its caller.
Duration? snackBarTimeLimit(SnackBar snackBar, {required bool accessibleNavigation}) {
  if (snackBar is WorkingSnackBar) return null;
  if (snackBar.action == null) return _shorter(snackBar.duration, kSnackBarDuration);
  // A screen reader user needs the button to stay until they reach it, which is why Flutter keeps it there.
  if (accessibleNavigation) return null;
  return _shorter(snackBar.duration, kSnackBarActionDuration);
}

Duration _shorter(Duration a, Duration b) => a < b ? a : b;

/// The app's messenger: a new message replaces the one on screen instead of queueing behind it, and every message
/// leaves after [snackBarTimeLimit].
///
/// Flutter keeps a snackbar with an action until it is tapped, which left Undo bars up for good, and queued messages
/// each took their full turn, so a burst of errors held the bottom of the screen for many seconds.
class XtaScaffoldMessenger extends ScaffoldMessenger {
  const XtaScaffoldMessenger({super.key, required super.child});

  @override
  ScaffoldMessengerState createState() => _XtaScaffoldMessengerState();
}

class _XtaScaffoldMessengerState extends ScaffoldMessengerState {
  Timer? _limit;

  @override
  ScaffoldFeatureController<SnackBar, SnackBarClosedReason> showSnackBar(
    SnackBar snackBar, {
    AnimationStyle? snackBarAnimationStyle,
  }) {
    _limit?.cancel();
    removeCurrentSnackBar();
    final controller = super.showSnackBar(snackBar, snackBarAnimationStyle: snackBarAnimationStyle);
    final limit = snackBarTimeLimit(snackBar, accessibleNavigation: MediaQuery.accessibleNavigationOf(context));
    if (limit != null) {
      final timer = Timer(limit, () => hideCurrentSnackBar(reason: SnackBarClosedReason.timeout));
      _limit = timer;
      controller.closed.whenComplete(timer.cancel);
    }
    return controller;
  }

  @override
  void dispose() {
    _limit?.cancel();
    super.dispose();
  }
}
