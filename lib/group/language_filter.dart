import 'package:xta/client/client.dart';
import 'package:xta/group/custom_feed_rules.dart';

/// How a global language filter treats posts outside the allowed set.
enum LanguageFilterAction {
  off,
  hide,
  fold;

  static LanguageFilterAction parse(String? raw) => switch (raw) {
    'hide' => LanguageFilterAction.hide,
    'fold' => LanguageFilterAction.fold,
    _ => LanguageFilterAction.off,
  };

  String get stored => switch (this) {
    LanguageFilterAction.hide => 'hide',
    LanguageFilterAction.fold => 'fold',
    LanguageFilterAction.off => 'off',
  };
}

LanguageFilterAction parseLanguageFilterAction(String? raw) => LanguageFilterAction.parse(raw);

/// Parses [optionFeedLanguages] — a CSV of BCP-47 prefixes.
List<String> parseFeedLanguages(String? raw) {
  if (raw == null || raw.trim().isEmpty) {
    return const [];
  }
  return raw.split(',').map((part) => part.trim()).where((part) => part.isNotEmpty).toList(growable: false);
}

String? _chainLanguage(TweetChain chain) => chain.tweets.firstOrNull?.lang;

bool _languageAllowed(String? lang, List<String> allowedPrefixes) {
  if (lang == null || lang.isEmpty || allowedPrefixes.isEmpty) {
    return true;
  }
  final lowered = lang.toLowerCase();
  return allowedPrefixes.any((prefix) {
    final needle = prefix.trim().toLowerCase();
    return needle.isNotEmpty && lowered.startsWith(needle);
  });
}

/// Applies a global language allow-list with hide or fold semantics.
FeedRuleOutcome applyLanguageFilter(
  List<TweetChain> chains, {
  required List<String> allowedLanguages,
  required LanguageFilterAction action,
  Map<String, String> priorFolds = const {},
}) {
  if (action == LanguageFilterAction.off || allowedLanguages.isEmpty) {
    return FeedRuleOutcome(chains: chains, foldReasons: priorFolds);
  }

  final kept = <TweetChain>[];
  final foldReasons = Map<String, String>.from(priorFolds);

  for (final chain in chains) {
    if (_languageAllowed(_chainLanguage(chain), allowedLanguages)) {
      kept.add(chain);
      continue;
    }

    final lang = _chainLanguage(chain) ?? '?';
    if (action == LanguageFilterAction.hide) {
      continue;
    }

    kept.add(chain);
    foldReasons[chain.id] = lang;
  }

  return FeedRuleOutcome(chains: kept, foldReasons: foldReasons);
}
