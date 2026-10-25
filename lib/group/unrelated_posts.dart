import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/ui/floating_notice.dart';

/// Whether a chunk's search brought posts by accounts outside the chunk, which
/// X sometimes does for a search over several accounts.
///
/// Compared by id, so an account that changed its name is still itself. A
/// chunk holding a saved search can't tell: that search's posts come from
/// anyone.
bool chunkHasUnrelatedPosts(Iterable<TweetChain> chains, List<Subscription> members) {
  if (members.any((member) => member is SearchSubscription)) return false;
  final ids = {for (final member in members) member.id};
  return chains
      .expand((chain) => chain.tweets)
      .map((tweet) => tweet.user?.idStr)
      .any((id) => id != null && !ids.contains(id));
}

/// Floats over a feed's posts while its searches brought posts from outside
/// it, where a dialog used to stop the reader; [onExplain] says why.
class UnrelatedPostsPill extends StatelessWidget {
  final VoidCallback onExplain;

  const UnrelatedPostsPill({super.key, required this.onExplain});

  @override
  Widget build(BuildContext context) {
    return NoticePill(
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Padding(
            padding: const EdgeInsetsDirectional.only(start: 16, end: 8),
            child: Icon(Icons.warning_amber_rounded, size: 18, color: Theme.of(context).colorScheme.error),
          ),
          Text(L10n.of(context).feed_issue_detected),
          IconButton(tooltip: L10n.of(context).more_info, onPressed: onExplain, icon: const Icon(Icons.info_outline)),
        ],
      ),
    );
  }
}
