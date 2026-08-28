/// Long-press menu on the quote footer: view quotes, view retweets, or write
/// a local note that quotes the post. None of this posts to X.
library;

import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/saved/local_post_compose.dart';
import 'package:xta/tweet/quotes_screen.dart';

void openQuotesAndRetweets(
  BuildContext context, {
  required String tweetId,
  int initialTab = 0,
}) {
  Navigator.pushNamed(
    context,
    routeQuotes,
    arguments: QuotesScreenArguments(id: tweetId, initialTab: initialTab),
  );
}

Future<void> showQuoteActionMenu({
  required BuildContext context,
  required TweetWithCard tweet,
  required Offset globalPosition,
}) async {
  final id = tweet.idStr;
  if (id == null) {
    return;
  }
  final l10n = L10n.of(context);
  final choice = await showMenu<String>(
    context: context,
    position: RelativeRect.fromLTRB(
      globalPosition.dx,
      globalPosition.dy,
      globalPosition.dx,
      globalPosition.dy,
    ),
    items: [
      PopupMenuItem(value: 'quotes', child: Text(l10n.quotes)),
      PopupMenuItem(value: 'retweets', child: Text(l10n.retweets)),
      PopupMenuItem(value: 'note', child: Text(l10n.local_note_quote_action)),
    ],
  );
  if (!context.mounted || choice == null) {
    return;
  }
  switch (choice) {
    case 'quotes':
      openQuotesAndRetweets(context, tweetId: id);
    case 'retweets':
      openQuotesAndRetweets(context, tweetId: id, initialTab: 1);
    case 'note':
      final saved = await openLocalPostComposer(context, quotedTweet: tweet);
      if (saved != null && context.mounted) {
        ScaffoldMessenger.of(
          context,
        ).showSnackBar(SnackBar(content: Text(l10n.local_note_saved)));
      }
  }
}
