import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/utils/urls.dart';

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
    final theme = Theme.of(context);

    final radius = BorderRadius.circular(kTweetMediaRadius);

    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Material(
        // Filled rather than outlined. A hairline box around two lines of text
        // read as an empty frame waiting for a preview that never comes; a
        // surface reads as a thing in its own right.
        color: theme.colorScheme.surfaceContainerHighest,
        borderRadius: radius,
        clipBehavior: Clip.antiAlias,
        child: InkWell(
          onTap: onTap,
          child: Padding(
            padding: const EdgeInsets.all(12),
            child: Row(
              children: [
                // A tile rather than a bare glyph, so the card has something
                // with weight where a thumbnail would be.
                Container(
                  width: 40,
                  height: 40,
                  decoration: BoxDecoration(
                    color: theme.colorScheme.primary.withValues(alpha: 0.14),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Icon(Icons.article_outlined, size: 22, color: theme.colorScheme.primary),
                ),
                const SizedBox(width: 12),
                // The host is dropped: "Article on X" already says where it is,
                // and "x.com" underneath was the same fact in smaller type.
                Expanded(
                  child: Text(
                    L10n.of(context).article_on_x,
                    maxLines: 2,
                    overflow: TextOverflow.ellipsis,
                    style: theme.textTheme.titleSmall!.copyWith(fontWeight: FontWeight.w700),
                  ),
                ),
                const SizedBox(width: 4),
                Icon(Icons.chevron_right, size: 20, color: theme.colorScheme.onSurfaceVariant),
              ],
            ),
          ),
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
