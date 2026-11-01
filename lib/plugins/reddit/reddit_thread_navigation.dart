import 'dart:math' as math;

import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

/// Reddit's comment orders, as the thread asks for them; '' is the site's
/// default, best.
const redditCommentSorts = ['', 'top', 'new', 'controversial', 'old', 'qa'];

String redditCommentSortLabel(L10n l10n, String? sort) => switch (sort) {
  'top' => l10n.plugin_reddit_sort_top,
  'new' => l10n.plugin_reddit_sort_new,
  'controversial' => l10n.plugin_reddit_sort_controversial,
  'old' => l10n.plugin_reddit_sort_old,
  'qa' => l10n.plugin_reddit_sort_qa,
  _ => l10n.plugin_reddit_sort_best,
};

/// Above the comments: how many there are, the order they are in, and
/// Collapse all / Expand all when any top-level comment has replies.
class RedditCommentsHeader extends StatelessWidget {
  final int count;
  final String? sort;
  final ValueChanged<String?> onSort;

  /// Null when there is nothing to fold.
  final bool? allFolded;
  final VoidCallback onToggleAll;

  const RedditCommentsHeader({
    super.key,
    required this.count,
    required this.sort,
    required this.onSort,
    required this.allFolded,
    required this.onToggleAll,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final allFolded = this.allFolded;
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: tweetDividerColor(context))),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(kTweetHorizontalPadding, kTweetSpace1, kTweetSpace2, kTweetSpace1),
        child: Wrap(
          alignment: WrapAlignment.spaceBetween,
          crossAxisAlignment: WrapCrossAlignment.center,
          children: [
            Text(
              l10n.plugin_reddit_comment_count(count),
              style: TextStyle(fontSize: 15, fontWeight: FontWeight.w600, color: tweetPrimaryColor(context)),
            ),
            Wrap(
              crossAxisAlignment: WrapCrossAlignment.center,
              children: [
                _SortButton(sort: sort, onSort: onSort),
                if (allFolded != null)
                  TextButton(
                    onPressed: onToggleAll,
                    style: _quietButton(context),
                    child: Text(allFolded ? l10n.expand_all : l10n.collapse_all),
                  ),
              ],
            ),
          ],
        ),
      ),
    );
  }
}

ButtonStyle _quietButton(BuildContext context) => TextButton.styleFrom(
  foregroundColor: tweetPrimaryColor(context),
  minimumSize: const Size(kTweetTouchTarget, kTweetTouchTarget),
  padding: const EdgeInsets.symmetric(horizontal: 10),
  textStyle: Theme.of(context).textTheme.labelLarge!.copyWith(fontSize: 13, fontWeight: FontWeight.w500),
);

class _SortButton extends StatelessWidget {
  final String? sort;
  final ValueChanged<String?> onSort;

  const _SortButton({required this.sort, required this.onSort});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final colour = tweetPrimaryColor(context);
    return PopupMenuButton<String>(
      tooltip: l10n.plugin_reddit_sort,
      initialValue: sort ?? '',
      onSelected: (value) => onSort(value.isEmpty ? null : value),
      itemBuilder: (context) => [
        for (final value in redditCommentSorts)
          CheckedPopupMenuItem(
            value: value,
            checked: value == (sort ?? ''),
            child: Text(redditCommentSortLabel(l10n, value)),
          ),
      ],
      child: ConstrainedBox(
        constraints: const BoxConstraints(minWidth: kTweetTouchTarget, minHeight: kTweetTouchTarget),
        child: Padding(
          padding: const EdgeInsets.symmetric(horizontal: 10),
          child: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              Icon(Icons.sort, size: 16, color: colour),
              const SizedBox(width: 6),
              Text(
                redditCommentSortLabel(l10n, sort),
                style: TextStyle(fontSize: 13, fontWeight: FontWeight.w500, color: colour),
              ),
            ],
          ),
        ),
      ),
    );
  }
}

/// The round button that jumps to the next top-level comment.
class RedditJumpButton extends StatelessWidget {
  final VoidCallback onPressed;

  const RedditJumpButton({super.key, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    return IconButton(
      tooltip: L10n.of(context).plugin_reddit_jump_next_comment,
      onPressed: onPressed,
      style: IconButton.styleFrom(
        fixedSize: const Size.square(52),
        backgroundColor: xLookFloatingSurfaceOf(context),
        foregroundColor: tweetPrimaryColor(context),
        side: BorderSide(color: tweetDividerColor(context)),
        shape: const CircleBorder(),
      ),
      icon: const Icon(Icons.keyboard_double_arrow_down, size: 22),
    );
  }
}

/// The nearest of [offsets] below [from], with a pixel's leeway so the one
/// already at the top is passed over; null when none is.
double? redditNextTargetOffset(Iterable<double> offsets, double from) {
  final below = offsets.where((offset) => offset > from + 1);
  return below.isEmpty ? null : below.reduce(math.min);
}

const _jumpSteps = 200;

/// Scrolls [controller] so the first of [targets] below the top of the list
/// starts at the top, laying out the list a screen at a time until one is
/// built; to the end when none is left.
Future<void> redditJumpToNext(
  ScrollController controller,
  List<GlobalKey> targets, {
  required bool reduceMotion,
}) async {
  if (!controller.hasClients) {
    return;
  }
  final position = controller.position;
  final from = position.pixels;
  for (var step = 0; step < _jumpSteps; step++) {
    final next = redditNextTargetOffset(targets.map(_revealOffset).nonNulls, from);
    if (next != null) {
      return _settle(position, next, reduceMotion: reduceMotion);
    }
    if (position.pixels >= position.maxScrollExtent) {
      break;
    }
    position.jumpTo(math.min(position.pixels + position.viewportDimension, position.maxScrollExtent));
    await WidgetsBinding.instance.endOfFrame;
  }
  await _settle(position, position.maxScrollExtent, reduceMotion: reduceMotion);
}

/// The scroll offset that puts [key]'s row at the top, while it is laid out.
double? _revealOffset(GlobalKey key) {
  final box = key.currentContext?.findRenderObject();
  if (box == null || !box.attached) {
    return null;
  }
  return RenderAbstractViewport.maybeOf(box)?.getOffsetToReveal(box, 0).offset;
}

Future<void> _settle(ScrollPosition position, double offset, {required bool reduceMotion}) {
  final target = offset.clamp(position.minScrollExtent, position.maxScrollExtent);
  if (reduceMotion) {
    position.jumpTo(target);
    return Future.value();
  }
  return position.animateTo(target, duration: const Duration(milliseconds: 300), curve: Curves.easeOutCubic);
}
