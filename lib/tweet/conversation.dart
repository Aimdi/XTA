import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/tweet/tweet.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/utils/iterables.dart';

class TweetConversation extends StatefulWidget {
  final String id;
  final String? username;
  final bool isPinned;
  final List<TweetWithCard> tweets;
  final bool tweetOpened;
  final int initialMediaIndex;

  const TweetConversation(
      {super.key,
      required this.id,
      required this.username,
      required this.isPinned,
      required this.tweets,
      this.tweetOpened = false,
      this.initialMediaIndex = 0});

  @override
  State<TweetConversation> createState() => _TweetConversationState();
}

class _TweetConversationState extends State<TweetConversation> {
  @override
  Widget build(BuildContext context) {
    if (widget.tweets.length == 1) {
      final tweet = widget.tweets.first;
      return TweetTile(
          // Feeds replace a page in place on refresh, so without an identity of
          // its own a recycled tile would keep the previous post's frozen state.
          key: ValueKey(tweet.idStr ?? widget.id),
          clickable: true,
          tweet: tweet,
          currentUsername: widget.username,
          isPinned: widget.isPinned,
          tweetOpened: widget.tweetOpened,
          initialMediaIndex: widget.initialMediaIndex);
    }

    var tiles = <Widget>[];
    var tweets = widget.tweets.sorted((a, b) => (a.idStr ?? '').compareTo(b.idStr ?? '')).toList(growable: false);

    for (var i = 0; i < tweets.length; i++) {
      tiles.add(TweetTile(
          key: ValueKey(tweets[i].idStr ?? '${widget.id}#$i'),
          clickable: true,
          tweet: tweets[i],
          currentUsername: widget.username,
          isPinned: widget.isPinned,
          isThread: i == 0,
          threadConnectTop: i > 0,
          threadConnectBottom: i < tweets.length - 1,
          initialMediaIndex: tweets[i].idStr == widget.id ? widget.initialMediaIndex : 0));
    }

    // One flat card for the whole thread so its tweets read as a single surface.
    // Trailing hairline matches the divider every standalone tweet draws below itself.
    return Column(
      children: [
        tweetFlatCard(
          color: tweetCardColor(context),
          child: Column(children: tiles),
        ),
        tweetHairlineDivider(context),
      ],
    );
  }
}
