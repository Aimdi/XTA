import 'package:flutter/material.dart';
import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/status.dart';
import 'package:xta/tweet/conversation.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/tweet_open.dart';
import 'package:xta/user.dart';
import 'package:xta/utils/rich_text.dart';

/// A horizontal row of compact boost cards for consecutive retweets.
///
/// Stays compact by default; the reader can expand the run into full timeline
/// posts in place, then collapse it again.
class BoostRunCarousel extends StatefulWidget {
  final List<TweetChain> chains;
  final String? username;

  const BoostRunCarousel({super.key, required this.chains, this.username});

  @override
  State<BoostRunCarousel> createState() => _BoostRunCarouselState();
}

class _BoostRunCarouselState extends State<BoostRunCarousel> {
  bool _expanded = false;

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final labelStyle = theme.textTheme.labelSmall?.copyWith(color: theme.colorScheme.onSurfaceVariant);
    final l10n = L10n.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 8, 0),
          child: Row(
            children: [
              Expanded(child: Text(l10n.boosts_row_label, style: labelStyle)),
              TextButton.icon(
                onPressed: () => setState(() => _expanded = !_expanded),
                style: TextButton.styleFrom(
                  visualDensity: VisualDensity.compact,
                  padding: const EdgeInsets.symmetric(horizontal: 8),
                  tapTargetSize: MaterialTapTargetSize.shrinkWrap,
                  foregroundColor: theme.colorScheme.onSurfaceVariant,
                  textStyle: theme.textTheme.labelSmall,
                ),
                icon: Icon(_expanded ? Icons.unfold_less : Icons.unfold_more, size: 18),
                label: Text(_expanded ? l10n.collapse_reposts : l10n.expand_reposts),
              ),
            ],
          ),
        ),
        if (_expanded)
          for (final chain in widget.chains)
            TweetConversation(
              key: ValueKey('boost-expanded-${chain.id}'),
              id: chain.id,
              tweets: chain.tweets,
              username: widget.username,
              isPinned: chain.isPinned,
            )
        else
          SizedBox(
            height: 88,
            child: ListView.separated(
              scrollDirection: Axis.horizontal,
              padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
              itemCount: widget.chains.length,
              separatorBuilder: (_, _) => const SizedBox(width: 8),
              itemBuilder: (context, index) => _BoostCard(chain: widget.chains[index], username: widget.username),
            ),
          ),
      ],
    );
  }
}

class _BoostCard extends StatelessWidget {
  final TweetChain chain;
  final String? username;

  const _BoostCard({required this.chain, this.username});

  @override
  Widget build(BuildContext context) {
    final boost = chain.tweets.firstOrNull;
    if (boost == null) {
      return const SizedBox.shrink();
    }
    final boosted = boost.retweetedStatusWithCard;
    final booster = boost.user;
    // On a profile every card in the row was reposted by that same profile, so the card names whose post it is.
    final author = boosted?.user ?? booster;
    final profile = openableProfile(author, currentUsername: username);
    final theme = Theme.of(context);
    final preview = boostPreviewText(boosted?.fullText ?? boosted?.text);

    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(tweetMediaRadiusOf(context)),
      child: InkWell(
        borderRadius: BorderRadius.circular(tweetMediaRadiusOf(context)),
        onTap: boost.idStr == null || booster?.screenName == null
            ? null
            : () => Navigator.pushNamed(
                context,
                routeStatus,
                arguments: StatusScreenArguments(id: boost.idStr!, username: booster!.screenName!),
              ),
        child: SizedBox(
          width: 200,
          child: Padding(
            padding: const EdgeInsets.all(10),
            child: Row(
              children: [
                if (author != null)
                  _OpenProfile(profile: profile, child: UserAvatar(uri: author.profileImageUrlHttps, size: 32)),
                const SizedBox(width: 4),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      _OpenProfile(
                        profile: profile,
                        fullTarget: false,
                        child: Text(
                          author?.name ?? author?.screenName ?? '',
                          maxLines: 1,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.labelLarge,
                        ),
                      ),
                      if (preview.isNotEmpty)
                        Text(
                          preview,
                          maxLines: 2,
                          overflow: TextOverflow.ellipsis,
                          style: theme.textTheme.bodySmall?.copyWith(color: theme.colorScheme.onSurfaceVariant),
                        ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Opens [profile] on tap; a tap anywhere else on the card still opens the post.
class _OpenProfile extends StatelessWidget {
  final ({String? id, String screenName})? profile;
  final Widget child;

  /// The avatar carries the full 48 dp target; the name beside it is tappable at its own height to fit the card.
  final bool fullTarget;

  const _OpenProfile({required this.profile, required this.child, this.fullTarget = true});

  @override
  Widget build(BuildContext context) {
    final target = profile;
    if (target == null) return child;
    return InkWell(
      customBorder: const StadiumBorder(),
      onTap: () => Navigator.pushNamed(
        context,
        routeProfile,
        arguments: ProfileScreenArguments(target.id, target.screenName, null),
      ),
      child: fullTarget
          ? ConstrainedBox(
              constraints: const BoxConstraints(minWidth: kTweetTouchTarget, minHeight: kTweetTouchTarget),
              child: Center(widthFactor: 1, heightFactor: 1, child: child),
            )
          : child,
    );
  }
}

/// The first line or so of a post, as text rather than as X sent it.
///
/// X escapes `<`, `>` and `&` in the text it returns, and this card showed them
/// raw: a post reading `>,,<` arrived as `&gt;,,&lt;`. Every other renderer
/// unescapes; this one had been written without.
String boostPreviewText(String? text) {
  final trimmed = unescapeHtml(text ?? '').replaceAll('\n', ' ').trim();
  if (trimmed.length <= 80) {
    return trimmed;
  }
  return '${trimmed.substring(0, 77)}…';
}
