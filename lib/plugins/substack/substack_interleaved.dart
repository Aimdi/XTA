import 'package:flutter/widgets.dart';
import 'package:logging/logging.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/database/entities.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/tweet/interleaved_items.dart';
import 'package:xta/ui/provenance_accent.dart';

final _log = Logger('SubstackInterleaved');

/// Posts for [publications], as dated items a timeline can slot between its
/// chains.
///
/// All publications at once; the feed used to wait on the sum of the round
/// trips. One unreachable publication must not empty the whole feed of the
/// others, nor replace a working timeline with an error screen — so a failure
/// drops that publication and keeps the rest.
///
/// Unlike the account-based sources there is no per-publication cap: a
/// newsletter publishes a handful of posts a week, not a page.
Future<List<InterleavedItem>> loadSubstackInterleaved(
  BuildContext context,
  List<SubstackSubscription> publications,
) async {
  if (publications.isEmpty) {
    return const [];
  }

  final client = context.read<SubstackClient>();
  Object? failure;
  final fetched = await Future.wait(
    publications.map((publication) async {
      try {
        return (publication, await client.fetchPosts(publicationOf(publication), limit: substackFeedPageSize));
      } catch (e) {
        failure ??= e;
        _log.warning('Unable to load Substack posts for ${publication.id}: $e');
        return null;
      }
    }),
  );

  final result = <InterleavedItem>[
    for (final (publication, posts) in fetched.nonNulls)
      for (final post in posts)
        if (post.publishedAt case final date?)
          provenanceInterleavedItem(
            date: date,
            pluginId: pluginIdSubstack,
            id: '$pluginIdSubstack:${post.publication.id}:${post.id}',
            linkUrl: post.canonicalUrl,
            snapshot: {
                    'xtaPlugin': 'link',
                    'source': pluginIdSubstack,
                    'url': post.canonicalUrl ?? '',
                    'author': post.authorName ?? post.publicationName,
                    'text': '${post.title}\n${post.description ?? ''}',
                    'images': <String>[],
                  },
            build: (_) => SubstackPostCard(post: post, logoUrl: publication.logoUrl),
          ),
  ];
  if (failure != null) throw PartialFeedFailure(result, failure!);
  return result;
}
