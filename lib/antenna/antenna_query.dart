import 'package:xta/client/client.dart';
import 'package:xta/database/entities.dart';

String buildAntennaSearchQuery(Antenna antenna) {
  final parts = <String>[];

  if (antenna.includeTerms.isNotEmpty) {
    final include = antenna.includeTerms.map(_quoteTerm).join(' OR ');
    parts.add(antenna.includeTerms.length == 1 ? include : '($include)');
  }

  for (final term in antenna.excludeTerms) {
    parts.add('-${_quoteTerm(term)}');
  }

  return parts.join(' ').trim();
}

String _quoteTerm(String term) {
  final trimmed = term.trim();
  if (trimmed.contains(' ')) {
    return '"$trimmed"';
  }
  return trimmed;
}

List<TweetChain> filterAntennaFollowingScope(List<TweetChain> chains, Set<String> followedIds) {
  if (followedIds.isEmpty) {
    return const [];
  }
  return chains
      .where((chain) {
        final authorId = chain.tweets.firstOrNull?.user?.idStr;
        return authorId != null && followedIds.contains(authorId);
      })
      .toList(growable: false);
}
