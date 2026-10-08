// Re-export keyword helpers used by settings UI and group_model.
export 'package:xta/group/muted_keyword.dart'
    show
        KeywordFilterAction,
        MutedKeyword,
        activeMutedKeywords,
        encodeMutedKeywordsStored,
        joinMutedKeywords,
        parseMutedKeywordTerms,
        parseMutedKeywords,
        parseMutedKeywordsStored;

import 'package:xta/client/client.dart';
import 'package:xta/constants.dart';
import 'package:xta/group/muted_keyword.dart';

/// Everything a custom feed filters on, beyond replies and reposts.
///
/// Kept as plain data with no Flutter or database dependency so the filtering
/// is straightforward to test.
class CustomFeedRules {
  /// `sfw` / `default` / `nsfw`, from [contentFilterDefault] and friends.
  final String contentFilter;

  /// Hide posts below this many likes. 0 disables the threshold.
  final int minLikes;

  /// Hide posts below this many reposts. 0 disables the threshold.
  final int minRetweets;

  /// Hide or fold posts matching these keywords.
  final List<MutedKeyword> mutedKeywords;

  const CustomFeedRules({
    this.contentFilter = contentFilterDefault,
    this.minLikes = 0,
    this.minRetweets = 0,
    this.mutedKeywords = const [],
  });

  bool get isEmpty =>
      contentFilter == contentFilterDefault && minLikes == 0 && minRetweets == 0 && mutedKeywords.isEmpty;

  /// Part of the feed cache key: two feeds with different rules must not share
  /// cached chunks.
  String get cacheKey => '$contentFilter|$minLikes|$minRetweets|${encodeMutedKeywordsStored(mutedKeywords)}';
}

/// Result of applying feed rules — chains to show plus fold reasons by chain id.
class FeedRuleOutcome {
  final List<TweetChain> chains;
  final Map<String, String> foldReasons;

  const FeedRuleOutcome({required this.chains, this.foldReasons = const {}});

  FeedRuleOutcome merge(FeedRuleOutcome other) =>
      FeedRuleOutcome(chains: other.chains, foldReasons: {...foldReasons, ...other.foldReasons});
}

final RegExp _wordCharacter = RegExp(r'[\p{L}\p{N}]', unicode: true);

/// Whether [text] contains [term], case-insensitively.
///
/// A single word matches on word boundaries so muting "cat" does not also hide
/// "category"; a phrase or anything with punctuation matches as a substring,
/// which is what a user typing "black friday" or "$TSLA" expects.
bool textMatchesMutedTerm(String text, String term) {
  final needle = term.trim();
  if (needle.isEmpty) {
    return false;
  }

  final haystack = text.toLowerCase();
  final lowered = needle.toLowerCase();
  final isSingleWord = !lowered.contains(' ') && lowered.split('').every(_wordCharacter.hasMatch);

  if (!isSingleWord) {
    return haystack.contains(lowered);
  }

  try {
    return RegExp('(?<![\\p{L}\\p{N}])${RegExp.escape(lowered)}(?![\\p{L}\\p{N}])', unicode: true).hasMatch(haystack);
  } catch (_) {
    // Some runtimes choke on unicode lookbehind; fail open to substring match
    // rather than blowing up the whole feed page.
    return haystack.contains(lowered);
  }
}

/// The text of a chain that keyword muting looks at: every post in the thread,
/// plus quoted text, since a muted word in a quote is still on screen.
String chainSearchText(TweetChain chain) {
  final parts = <String>[];
  for (final tweet in chain.tweets) {
    parts.add(tweet.fullText ?? tweet.text ?? '');
    final quoted = tweet.quotedStatusWithCard;
    if (quoted != null) {
      parts.add(quoted.fullText ?? quoted.text ?? '');
    }
  }
  return parts.join(' ');
}

bool _isSensitive(TweetChain chain) => chain.tweets.any((tweet) => tweet.possiblySensitive == true);

int chainLikes(TweetChain chain) => chain.tweets.firstOrNull?.favoriteCount ?? 0;

int chainRetweets(TweetChain chain) {
  final tweet = chain.tweets.firstOrNull;
  if (tweet == null) {
    return 0;
  }
  return (tweet.retweetCount ?? 0) + (tweet.quoteCount ?? 0);
}

MutedKeyword? _firstMatchingKeyword(String text, List<MutedKeyword> keywords) {
  for (final keyword in keywords) {
    if (textMatchesMutedTerm(text, keyword.term)) {
      return keyword;
    }
  }
  return null;
}

/// Keeps only the chains a custom feed should show.
///
/// Thresholds read the thread's first post, the same post the popular sort
/// ranks by, so "at least 100 likes" means the same number the footer shows.
FeedRuleOutcome applyCustomFeedRules(List<TweetChain> chains, CustomFeedRules rules, {DateTime? now}) {
  if (rules.isEmpty) {
    return FeedRuleOutcome(chains: chains);
  }

  final activeKeywords = activeMutedKeywords(rules.mutedKeywords, now: now);
  final kept = <TweetChain>[];
  final foldReasons = <String, String>{};

  for (final chain in chains) {
    switch (rules.contentFilter) {
      case contentFilterSfw:
        if (_isSensitive(chain)) continue;
      case contentFilterNsfw:
        if (!_isSensitive(chain)) continue;
      default:
        break;
    }

    if (rules.minLikes > 0 && chainLikes(chain) < rules.minLikes) {
      continue;
    }
    if (rules.minRetweets > 0 && chainRetweets(chain) < rules.minRetweets) {
      continue;
    }

    if (activeKeywords.isNotEmpty) {
      final match = _firstMatchingKeyword(chainSearchText(chain), activeKeywords);
      if (match != null) {
        if (match.action == KeywordFilterAction.fold) {
          kept.add(chain);
          foldReasons[chain.id] = match.term;
        }
        continue;
      }
    }

    kept.add(chain);
  }

  return FeedRuleOutcome(chains: kept, foldReasons: foldReasons);
}
