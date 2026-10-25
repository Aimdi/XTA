import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/_ExpandableTweetText.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/tweet/unified_card.dart';
import 'package:xta/user.dart';

/// A shared Grok conversation: the question asked, and the start of the answer
/// beside Grok's picture. A tap opens the whole conversation.
class GrokShareCard extends StatelessWidget {
  static const _answerLines = 8;

  final GrokShareCardData share;
  final VoidCallback onTap;

  const GrokShareCard({super.key, required this.share, required this.onTap});

  @override
  Widget build(BuildContext context) {
    return TweetEmbedSurface(
      onTap: onTap,
      padding: const EdgeInsets.all(kTweetSpace3),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          _GrokQuestion(message: share.question),
          if (share.answer.isNotEmpty) ...[
            const SizedBox(height: kTweetSpace3),
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                _GrokAvatar(share: share),
                const SizedBox(width: kTweetSpace2),
                Expanded(
                  child: Text(
                    share.answer,
                    maxLines: _answerLines,
                    overflow: TextOverflow.ellipsis,
                    style: tweetBodyStyle(context),
                  ),
                ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}

class _GrokQuestion extends StatefulWidget {
  final String message;

  const _GrokQuestion({required this.message});

  @override
  State<_GrokQuestion> createState() => _GrokQuestionState();
}

class _GrokQuestionState extends State<_GrokQuestion> {
  static const _maxLines = 3;

  // Kept across builds: new spans fold a question the reader unfolded.
  late List<InlineSpan> _spans = [TextSpan(text: widget.message)];

  @override
  void didUpdateWidget(_GrokQuestion oldWidget) {
    super.didUpdateWidget(oldWidget);
    if (widget.message != oldWidget.message) {
      _spans = [TextSpan(text: widget.message)];
    }
  }

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    return Align(
      alignment: AlignmentDirectional.centerEnd,
      child: FractionallySizedBox(
        widthFactor: 0.8,
        alignment: AlignmentDirectional.centerEnd,
        child: Align(
          alignment: AlignmentDirectional.centerEnd,
          child: DecoratedBox(
            decoration: BoxDecoration(color: colors.secondaryContainer, borderRadius: BorderRadius.circular(18)),
            child: Padding(
              padding: const EdgeInsets.symmetric(horizontal: kTweetSpace3, vertical: kTweetSpace2),
              // A long question offers the rest, as a long post does; the card
              // around it still opens the conversation on a tap.
              child: DefaultTextStyle.merge(
                style: tweetBodyStyle(context).copyWith(color: colors.onSecondaryContainer),
                child: ExpandableTweetText(textSpans: _spans, maxLines: _maxLines),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Grok's picture, opening its profile; an icon when the card has none.
class _GrokAvatar extends StatelessWidget {
  static const _size = 32.0;

  final GrokShareCardData share;

  const _GrokAvatar({required this.share});

  @override
  Widget build(BuildContext context) {
    final colors = Theme.of(context).colorScheme;
    final image = share.grokImageUrl;
    return InkResponse(
      radius: kTweetTouchTarget / 2,
      onTap: () => Navigator.pushNamed(
        context,
        routeProfile,
        arguments: ProfileScreenArguments.fromScreenName(share.grokScreenName, null),
      ),
      child: image == null
          ? CircleAvatar(
              radius: _size / 2,
              backgroundColor: colors.tertiaryContainer,
              child: Icon(Icons.auto_awesome, size: 18, color: colors.onTertiaryContainer),
            )
          : UserAvatar(uri: image, size: _size),
    );
  }
}
