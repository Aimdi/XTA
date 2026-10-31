/// Going back and refreshing the way a PC does it: the mouse's back button,
/// Alt+← and Esc go back as Android's back gesture does, and F5 or Ctrl+R
/// refreshes a timeline. Off the desktop each widget here is its child alone.
/// See docs/specs/desktop-release.md.
library;

import 'package:flutter/gestures.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/utils/desktop.dart';

/// Esc: back from a page, unless something nearer takes it (a dialog, a sheet
/// or a menu closes). Typing in a page, the first Esc leaves the field.
class EscapeBackIntent extends Intent {
  const EscapeBackIntent();
}

/// The mouse's back button, Alt+← and a keyboard's Back key: back from
/// whatever is on top, dialogs and sheets included, as Android's back does.
class GoBackIntent extends Intent {
  const GoBackIntent();
}

const _backShortcuts = <ShortcutActivator, Intent>{
  SingleActivator(LogicalKeyboardKey.escape): EscapeBackIntent(),
  SingleActivator(LogicalKeyboardKey.arrowLeft, alt: true): GoBackIntent(),
  SingleActivator(LogicalKeyboardKey.goBack): GoBackIntent(),
  SingleActivator(LogicalKeyboardKey.browserBack): GoBackIntent(),
};

/// Wraps the app's navigator (from [MaterialApp.builder]) so back works from
/// the mouse and keyboard on the desktop.
class DesktopNavigation extends StatelessWidget {
  final GlobalKey<NavigatorState> navigatorKey;
  final Widget child;

  const DesktopNavigation({super.key, required this.navigatorKey, required this.child});

  @override
  Widget build(BuildContext context) {
    if (!isDesktop) return child;
    return Listener(
      onPointerDown: (event) {
        if (event.buttons & kBackMouseButton != 0) navigatorKey.currentState?.maybePop();
      },
      // Nearer the focus than the app's own Esc → dismiss, which still gets
      // Esc whenever going back is not what it should do.
      child: Shortcuts(
        shortcuts: _backShortcuts,
        child: Actions(
          actions: {EscapeBackIntent: _EscapeBackAction(navigatorKey), GoBackIntent: _GoBackAction(navigatorKey)},
          child: child,
        ),
      ),
    );
  }
}

class _GoBackAction extends Action<GoBackIntent> {
  final GlobalKey<NavigatorState> navigatorKey;

  _GoBackAction(this.navigatorKey);

  @override
  bool isEnabled(GoBackIntent intent) => navigatorKey.currentState?.canPop() ?? false;

  @override
  Object? invoke(GoBackIntent intent) => navigatorKey.currentState?.maybePop();
}

class _EscapeBackAction extends Action<EscapeBackIntent> {
  final GlobalKey<NavigatorState> navigatorKey;

  _EscapeBackAction(this.navigatorKey);

  @override
  bool isEnabled(EscapeBackIntent intent) {
    final navigator = navigatorKey.currentState;
    if (navigator == null || topRoute(navigator) is! PageRoute) return false;
    final focused = FocusManager.instance.primaryFocus?.context;
    if (focused != null && isEditingText(focused)) return true;
    return navigator.canPop() && (focused == null || !canDismiss(focused));
  }

  @override
  Object? invoke(EscapeBackIntent intent) {
    final focus = FocusManager.instance.primaryFocus;
    if (focus?.context case final focused? when isEditingText(focused)) {
      focus!.unfocus();
      return null;
    }
    return navigatorKey.currentState?.maybePop();
  }
}

/// The route on top of [navigator], read without popping anything.
Route<dynamic>? topRoute(NavigatorState navigator) {
  Route<dynamic>? top;
  navigator.popUntil((route) {
    top = route;
    return true;
  });
  return top;
}

/// Whether the focus at [focused] is in a text field.
bool isEditingText(BuildContext focused) =>
    focused.widget is EditableText || focused.findAncestorWidgetOfExactType<EditableText>() != null;

/// Whether something around [focused], such as an open menu, would close on
/// Esc itself.
bool canDismiss(BuildContext focused) =>
    Actions.maybeFind<DismissIntent>(focused)?.isEnabled(const DismissIntent()) ?? false;

/// F5 and Ctrl+R run [onRefresh] while the focus is in [child], which takes
/// the focus when nothing else in its page has it.
class DesktopRefreshKeys extends StatelessWidget {
  final Future<void> Function() onRefresh;
  final Widget child;

  const DesktopRefreshKeys({super.key, required this.onRefresh, required this.child});

  @override
  Widget build(BuildContext context) {
    if (!isDesktop) return child;
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.f5): onRefresh,
        const SingleActivator(LogicalKeyboardKey.keyR, control: true): onRefresh,
      },
      child: Focus(autofocus: true, child: child),
    );
  }
}
