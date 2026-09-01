import 'package:flutter/material.dart';
import 'package:quax/tweet/tweet_chrome.dart';

/// Author hierarchy shared by ordinary, quoted and threaded posts.
class TweetAuthorBlock extends StatelessWidget {
  final String? displayName;
  final String? handle;
  final bool verified;
  final Widget? timestamp;
  final Widget? trailing;

  const TweetAuthorBlock({
    super.key,
    required this.displayName,
    required this.handle,
    required this.verified,
    this.timestamp,
    this.trailing,
  });

  @override
  Widget build(BuildContext context) {
    if (displayName == null && handle == null) {
      return Align(alignment: Alignment.centerRight, child: trailing);
    }

    return Row(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Expanded(
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisSize: MainAxisSize.min,
            children: [
              Row(
                children: [
                  Flexible(
                    child: Text(
                      displayName ?? '',
                      overflow: TextOverflow.ellipsis,
                      maxLines: 1,
                      style: tweetDisplayNameStyle(context),
                    ),
                  ),
                  if (verified) ...[
                    const SizedBox(width: kTweetSpace1),
                    Icon(
                      Icons.verified,
                      size: 16,
                      color: tweetAccentColor(context),
                    ),
                  ],
                ],
              ),
              if (handle != null || timestamp != null)
                Row(
                  children: [
                    if (handle != null)
                      Flexible(
                        child: Text(
                          '@$handle',
                          overflow: TextOverflow.ellipsis,
                          maxLines: 1,
                          style: tweetMetadataStyle(context),
                        ),
                      ),
                    if (handle != null && timestamp != null)
                      const SizedBox(width: kTweetSpace2),
                    if (timestamp != null)
                      DefaultTextStyle.merge(
                        style: tweetMetadataStyle(context),
                        child: timestamp!,
                      ),
                  ],
                ),
            ],
          ),
        ),
        if (trailing != null) ...[
          const SizedBox(width: kTweetSpace1),
          SizedBox.square(
            dimension: kTweetTouchTarget,
            child: Center(child: trailing),
          ),
        ],
      ],
    );
  }
}

class TweetHeader extends StatelessWidget {
  final Widget avatar;
  final VoidCallback onOpenProfile;
  final String? displayName;
  final String? handle;
  final bool verified;
  final Widget? timestamp;
  final Widget? trailing;
  final bool compact;

  const TweetHeader({
    super.key,
    required this.avatar,
    required this.onOpenProfile,
    required this.displayName,
    required this.handle,
    required this.verified,
    this.timestamp,
    this.trailing,
    this.compact = false,
  });

  @override
  Widget build(BuildContext context) {
    final avatarSize = compact ? kTweetQuotedAvatarSize : kTweetAvatarSize;
    final top = compact ? kTweetSpace2 : kTweetVerticalPadding;
    return Padding(
      padding: EdgeInsets.fromLTRB(
        kTweetHorizontalPadding,
        top,
        kTweetSpace2,
        kTweetSpace1,
      ),
      child: Row(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Semantics(
            button: true,
            child: InkResponse(
              onTap: onOpenProfile,
              radius: kTweetTouchTarget / 2,
              containedInkWell: true,
              customBorder: const CircleBorder(),
              child: SizedBox.square(
                dimension: kTweetTouchTarget,
                child: Center(
                  child: SizedBox.square(dimension: avatarSize, child: avatar),
                ),
              ),
            ),
          ),
          const SizedBox(width: kTweetSpace3),
          Expanded(
            child: Padding(
              padding: const EdgeInsets.only(top: kTweetSpace1),
              child: TweetAuthorBlock(
                displayName: displayName,
                handle: handle,
                verified: verified,
                timestamp: timestamp,
                trailing: trailing,
              ),
            ),
          ),
        ],
      ),
    );
  }
}
