import 'package:flutter/material.dart';
import 'package:pref/pref.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/profile/profile.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/motion.dart';

/// Initial hide-sensitive flag: respect global hide unless "always show" won.
bool initialHideSensitive(BasePrefService prefs) {
  if (prefs.get(optionAlwaysShowSensitiveMedia) == true) {
    return false;
  }
  return prefs.get(optionTweetsHideSensitive) == true;
}

/// Bluesky-style sensitive gate: show once, or remember forever.
///
/// When [child] is provided, it is shown unless [sensitive] is gated.
/// When [child] is omitted, this widget *is* the gate (for profile-level blocks).
class SensitiveMediaGate extends StatelessWidget {
  final bool sensitive;
  final String? errorMessage;
  final bool wrapInCard;
  final Widget? child;

  const SensitiveMediaGate({
    super.key,
    this.sensitive = true,
    this.errorMessage,
    this.child,
    this.wrapInCard = true,
  });

  @override
  Widget build(BuildContext context) {
    final content = child;
    if (content == null) {
      return _gate(context, PrefService.of(context));
    }

    // Ordinary tiles have nothing to hide: listening here made every media
    // tile rebuild when the reader revealed one sensitive post.
    if (!sensitive) {
      return content;
    }

    return Consumer<TweetContextState>(
      builder: (context, model, _) {
        return XtaAnimatedSwitcher(
          child: model.hideSensitive
              ? KeyedSubtree(
                  key: const ValueKey('sensitive-gate'),
                  child: _gate(
                    context,
                    PrefService.of(context),
                    model: model,
                  ),
                )
              : KeyedSubtree(
                  key: const ValueKey('sensitive-content'),
                  child: content,
                ),
        );
      },
    );
  }

  Widget _gate(
    BuildContext context,
    BasePrefService prefs, {
    TweetContextState? model,
  }) {
    final l10n = L10n.of(context);
    final state = model ?? context.read<TweetContextState>();
    final body = Padding(
      padding: const EdgeInsets.all(kTweetSpace4),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(
            Icons.visibility_off_outlined,
            size: 24,
            color: tweetSecondaryColor(context),
          ),
          const SizedBox(height: kTweetSpace2),
          Text(
            l10n.possibly_sensitive,
            textAlign: TextAlign.center,
            style: tweetLabelStyle(context),
          ),
          const SizedBox(height: kTweetSpace1),
          Text(
            errorMessage ?? l10n.possibly_sensitive_tweet,
            textAlign: TextAlign.center,
            style: tweetMetadataStyle(context),
          ),
          const SizedBox(height: kTweetSpace3),
          Wrap(
            alignment: WrapAlignment.center,
            spacing: kTweetSpace2,
            runSpacing: kTweetSpace2,
            children: [
              FilledButton(
                style: FilledButton.styleFrom(
                  backgroundColor: tweetAccentColor(context),
                  foregroundColor: tweetOnAccentColor(context),
                  minimumSize: const Size(0, kTweetTouchTarget),
                  elevation: 0,
                  shape: const StadiumBorder(),
                ),
                onPressed: () => state.setHideSensitive(false),
                child: Text(l10n.sensitive_media_show),
              ),
              TextButton(
                style: TextButton.styleFrom(
                  foregroundColor: tweetReadableAccentColor(context),
                  minimumSize: const Size(0, kTweetTouchTarget),
                ),
                onPressed: () => state.alwaysShowSensitive(prefs),
                child: Text(l10n.sensitive_media_always_show),
              ),
            ],
          ),
        ],
      ),
    );

    if (!wrapInCard) {
      return Center(child: body);
    }
    return TweetEmbedSurface(child: Center(child: body));
  }
}
