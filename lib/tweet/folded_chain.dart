import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/conversation.dart';
import 'package:xta/tweet/tweet_chrome.dart';

/// A chain folded behind a one-line reason (Mastodon CW / local filter fold).
class FoldedChain extends StatefulWidget {
  final TweetChain chain;
  final String reason;
  final String? username;

  const FoldedChain({
    super.key,
    required this.chain,
    required this.reason,
    this.username,
  });

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
          Semantics(
            button: true,
            child: InkWell(
              onTap: () => setState(() => _expanded = false),
              child: ConstrainedBox(
                constraints: const BoxConstraints(
                  minHeight: kTweetTouchTarget,
                ),
                child: Padding(
                  padding: const EdgeInsetsDirectional.symmetric(
                    horizontal: kTweetHorizontalPadding,
                    vertical: kTweetSpace2,
                  ),
                  child: Row(
                    children: [
                      Icon(
                        Icons.expand_less,
                        size: kTweetActionIconSize,
                        color: tweetReadableAccentColor(context),
                      ),
                      const SizedBox(width: kTweetSpace2),
                      Text(
                        L10n.of(context).filter_fold_hide_again,
                        style: tweetLabelStyle(context).copyWith(
                          color: tweetReadableAccentColor(context),
                        ),
                      ),
                    ],
                  ),
                ),
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

    return Semantics(
      button: true,
      child: InkWell(
        onTap: () => setState(() => _expanded = true),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
          child: Padding(
            padding: const EdgeInsetsDirectional.symmetric(
              horizontal: kTweetHorizontalPadding,
              vertical: kTweetSpace2,
            ),
            child: Row(
              children: [
                Icon(
                  Icons.visibility_off_outlined,
                  size: kTweetActionIconSize,
                  color: tweetSecondaryColor(context),
                ),
                const SizedBox(width: kTweetSpace2),
                Expanded(
                  child: Text(
                    L10n.of(context).filter_fold_matched(widget.reason),
                    style: tweetMetadataStyle(context),
                  ),
                ),
                Icon(
                  Icons.expand_more,
                  size: kTweetActionIconSize,
                  color: tweetSecondaryColor(context),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}
