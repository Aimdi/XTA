import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/utils/desktop.dart';

/// On the desktop, ← and → turn [controller]'s [count] pages, and buttons at
/// the sides do the same while there is a page that way: a viewer's pages
/// otherwise turn only with a touch drag. Elsewhere [child] alone.
class DesktopPager extends StatelessWidget {
  final ExtendedPageController controller;
  final int count;
  final Widget child;

  const DesktopPager({super.key, required this.controller, required this.count, required this.child});

  @override
  Widget build(BuildContext context) {
    if (!isDesktop || count < 2) return child;
    return CallbackShortcuts(
      bindings: {
        const SingleActivator(LogicalKeyboardKey.arrowLeft): () => _turn(context, -1),
        const SingleActivator(LogicalKeyboardKey.arrowRight): () => _turn(context, 1),
      },
      child: Focus(
        autofocus: true,
        child: Stack(
          fit: StackFit.passthrough,
          children: [
            child,
            ListenableBuilder(
              listenable: controller,
              builder: (context, _) => _PagerButtons(page: _page, count: count, onTurn: (by) => _turn(context, by)),
            ),
          ],
        ),
      ),
    );
  }

  int get _page => controller.hasClients && controller.position.haveDimensions
      ? (controller.page ?? controller.initialPage.toDouble()).round()
      : controller.initialPage;

  void _turn(BuildContext context, int by) {
    final target = _page + by;
    if (!controller.hasClients || target < 0 || target >= count) return;
    if (MediaQuery.disableAnimationsOf(context)) {
      controller.jumpToPage(target);
    } else {
      controller.animateToPage(target, duration: const Duration(milliseconds: 250), curve: Curves.easeOutCubic);
    }
  }
}

class _PagerButtons extends StatelessWidget {
  final int page;
  final int count;
  final void Function(int by) onTurn;

  const _PagerButtons({required this.page, required this.count, required this.onTurn});

  @override
  Widget build(BuildContext context) {
    final strings = MaterialLocalizations.of(context);
    return Padding(
      padding: const EdgeInsets.symmetric(horizontal: 12),
      child: Row(
        children: [
          if (page > 0) _button(Icons.chevron_left, strings.previousPageTooltip, () => onTurn(-1)),
          const Spacer(),
          if (page < count - 1) _button(Icons.chevron_right, strings.nextPageTooltip, () => onTurn(1)),
        ],
      ),
    );
  }

  // The viewers draw their own controls white on black, over any picture.
  Widget _button(IconData icon, String tooltip, VoidCallback onPressed) => IconButton.filled(
    style: IconButton.styleFrom(backgroundColor: Colors.black54, foregroundColor: Colors.white),
    tooltip: tooltip,
    icon: Icon(icon),
    onPressed: onPressed,
  );
}
