import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/conversation.dart';

/// A chain folded behind a one-line reason (Mastodon CW / local filter fold).
class FoldedChain extends StatefulWidget {
  final TweetChain chain;
  final String reason;
  final String? username;

  const FoldedChain({super.key, required this.chain, required this.reason, this.username});

  @override
  State<FoldedChain> createState() => _FoldedChainState();
}

class _FoldedChainState extends State<FoldedChain> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    if (_expanded) {
      return Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          InkWell(
            onTap: () => setState(() => _expanded = false),
            child: Padding(
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
              child: Text(
                L10n.of(context).filter_fold_hide_again,
                style: Theme.of(context).textTheme.labelMedium?.copyWith(color: Theme.of(context).colorScheme.primary),
              ),
            ),
          ),
          TweetConversation(
            id: widget.chain.id,
            tweets: widget.chain.tweets,
            username: widget.username,
            isPinned: widget.chain.isPinned,
          ),
        ],
      );
    }

    return InkWell(
      onTap: () => setState(() => _expanded = true),
      child: Padding(
        padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
        child: Row(
          children: [
            Icon(Icons.visibility_off_outlined, size: 18, color: Theme.of(context).colorScheme.onSurfaceVariant),
            const SizedBox(width: 10),
            Expanded(
              child: Text(
                L10n.of(context).filter_fold_matched(widget.reason),
                style: Theme.of(
                  context,
                ).textTheme.bodyMedium?.copyWith(color: Theme.of(context).colorScheme.onSurfaceVariant),
              ),
            ),
            Icon(Icons.expand_more, color: Theme.of(context).colorScheme.onSurfaceVariant),
          ],
        ),
      ),
    );
  }
}
