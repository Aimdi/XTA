import 'package:flutter/material.dart';
import 'package:xta/plugins/pixiv/pixiv_haptics.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_page_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_page_overview.dart';

/// The page overview and page actions of any screen that shows a work's pages.
mixin PixivPageSurface<T extends StatefulWidget> on State<T> {
  PixivIllust get pageIllust;
  int get currentPage;

  /// Whether the direction action offers vertical reading (else horizontal).
  bool get offersVertical;
  void showPage(int page);
  void changeDirection();

  Future<void> openPageOverview() async {
    final page = currentPage;
    final choice = await showPixivPageOverview(
      context,
      illust: pageIllust,
      currentPage: page,
      readVertically: offersVertical,
    );
    if (!mounted || choice == null) return;
    final target = choice.page;
    if (target != null) {
      showPage(target);
    } else {
      await runPageAction(choice.action!, page);
    }
  }

  /// The page sheet a long press on [page] opens, with a firmer buzz.
  Future<void> openPageActions(int page) {
    playPixivHaptic(context, PixivHaptic.medium);
    return showPageActions(page);
  }

  /// The page sheet a button opens, without the long press's buzz.
  Future<void> showPageActions(int page) async {
    final action = await showPixivPageActions(context, illust: pageIllust, page: page);
    if (mounted && action != null) await runPageAction(action, page);
  }

  Future<void> runPageAction(PixivPageAction action, int page) async {
    if (await runPixivPageAction(context, action, pageIllust, page) || !mounted) return;
    if (action == PixivPageAction.allPages) await openPageOverview();
    if (action == PixivPageAction.direction) changeDirection();
  }
}
