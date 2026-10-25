/// The X search one chunk of a group or feed sends, and how its members are
/// packed into chunks that fit one search.
library;

import 'package:xta/database/entities.dart';

/// The longest query X reads for one search.
const feedSearchQueryLimit = 512;

String userSearchTerm(String screenName) => 'from:$screenName';

/// In brackets, a saved search keeps its operators (`list:`, `from:`) and
/// means what it means on the search screen. In quotes, X looked for the
/// operators as words, and a group showed nothing of that search.
String savedSearchTerm(String query) => '($query)';

/// What [member] puts into its chunk's search. Only X members reach a chunk.
String feedSearchTermFor(Subscription member) =>
    member is SearchSubscription ? savedSearchTerm(member.id) : userSearchTerm(member.screenName);

List<String> _filters({required bool includeReplies, required bool includeRetweets}) => [
  if (!includeReplies) '-filter:replies',
  includeRetweets ? 'include:nativeretweets' : '-filter:retweets',
];

/// One chunk's query: its members' [terms] in one bracket, so the reply and
/// repost filters after it cover every member and not only the last.
String buildFeedSearchQuery(Iterable<String> terms, {required bool includeReplies, required bool includeRetweets}) => [
  if (terms.isNotEmpty) '(${terms.join(' OR ')})',
  ..._filters(includeReplies: includeReplies, includeRetweets: includeRetweets),
].join(' ');

/// Splits [members], in feed order, into the fewest chunks whose queries fit
/// [feedSearchQueryLimit]: short screen names share a search, so a group asks
/// X fewer times. Walking in order means a new member, which comes last, never
/// moves anyone into another chunk. A member too long to share a query gets a
/// chunk of its own.
List<List<T>> packFeedChunks<T>(
  List<T> members,
  String Function(T member) termOf, {
  required bool includeReplies,
  required bool includeRetweets,
}) {
  // The brackets and the space before the filters.
  final overhead = _filters(includeReplies: includeReplies, includeRetweets: includeRetweets).join(' ').length + 3;
  const separator = ' OR ';
  final chunks = <List<T>>[];
  var termsLength = 0;
  for (final member in members) {
    final length = termOf(member).length;
    if (chunks.isNotEmpty && overhead + termsLength + separator.length + length <= feedSearchQueryLimit) {
      chunks.last.add(member);
      termsLength += separator.length + length;
    } else {
      chunks.add([member]);
      termsLength = length;
    }
  }
  return chunks;
}
