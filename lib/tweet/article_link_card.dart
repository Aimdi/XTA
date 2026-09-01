import 'package:flutter/material.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/utils/urls.dart';

/// A link to a long-form X article, shown as a card rather than a raw URL.
///
/// X sends no title, author or thumbnail for one of these — only the link — so
/// there is nothing to build a real preview from. What the card can do is say
/// what the link is and give it something worth tapping, instead of the
/// truncated `x.com/i/artic…` that used to sit above the footer.
class ArticleLinkCard extends StatelessWidget {
  final String url;
  final VoidCallback onTap;

  const ArticleLinkCard({super.key, required this.url, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return TweetEmbedSurface(
      onTap: onTap,
      semanticLabel: L10n.of(context).article_on_x,
      padding: const EdgeInsets.all(kTweetSpace3),
      child: ConstrainedBox(
        constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
        child: Row(
          children: [
            Icon(
              Icons.article_outlined,
              size: kTweetActionIconSize,
              color: tweetSecondaryColor(context),
            ),
            const SizedBox(width: kTweetSpace3),
            Expanded(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  Text(
                    L10n.of(context).article_on_x,
                    style: tweetLabelStyle(context),
                  ),
                  const SizedBox(height: kTweetSpace1),
                  Text(
                    Uri.tryParse(url)?.host ?? url,
                    maxLines: 1,
                    overflow: TextOverflow.ellipsis,
                    style: tweetMetadataStyle(context),
                  ),
                ],
              ),
            ),
            const SizedBox(width: kTweetSpace2),
            Icon(
              Icons.chevron_right,
              size: kTweetActionIconSize,
              color: tweetSecondaryColor(context),
            ),
          ],
        ),
      ),
    );
  }
}

/// The first article link among a post's URL entities, if it has one.
String? firstArticleLink(Iterable<String?> expandedUrls) {
  for (final url in expandedUrls) {
    if (articleIdIn(url) != null) {
      return url;
    }
  }
  return null;
}
