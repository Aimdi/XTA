import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_post_card.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/reading/mixed_feed_definition.dart';
import 'package:xta/reading/mixed_feed_merge.dart';
import 'package:xta/reading/mixed_feed_source.dart';
import 'package:xta/reading/reader_source_text.dart';

const _pageSize = 12;

/// A post and the publication it came from, whose logo its card shows.
class SubstackMixedPost {
  final SubstackPost post;
  final SubstackPublication publication;
  const SubstackMixedPost(this.post, this.publication);
}

MixedEntry substackMixedEntry(SubstackPost post, SubstackPublication publication) => MixedEntry(
  identity: 'substack:${substackFeedPostKey(post)}',
  item: SubstackMixedPost(post, publication),
  date: post.publishedAt,
);

/// A followed publication, paged by offset the way its own page reads it.
class SubstackPublicationMixedSource extends MixedSourceKind {
  const SubstackPublicationMixedSource();

  @override
  String get id => 'substack.publication';

  @override
  String get pluginId => pluginIdSubstack;

  @override
  IconData get icon => Icons.article_outlined;

  @override
  MixedSourceInput get input => MixedSourceInput.choice;

  @override
  String title(BuildContext context) => L10n.of(context).plugin_substack_publication;

  @override
  Future<List<MixedFeedSource>> choices(BuildContext context) async => [
    for (final publication in context.read<SubstackPublicationsStore>().state)
      MixedFeedSource(kind: id, value: publication.id, label: publication.name),
  ];

  @override
  MixedSourceReader? reader(BuildContext context, MixedFeedSource source) {
    final publication = context
        .read<SubstackPublicationsStore>()
        .state
        .where((publication) => publication.id == source.value)
        .firstOrNull;
    if (publication == null) return null;
    final client = context.read<SubstackClient>();
    return MixedFunctionReader((cursor) async {
      final offset = cursor as int? ?? 0;
      final posts = await client.fetchPosts(publication, limit: _pageSize, offset: offset);
      return MixedPage([
        for (final post in posts) substackMixedEntry(post, publication),
      ], next: posts.length >= _pageSize ? offset + posts.length : null);
    });
  }

  @override
  Widget card(BuildContext context, MixedEntry entry) {
    final item = entry.item as SubstackMixedPost;
    return SubstackPostCard(post: item.post, logoUrl: item.publication.logoUrl);
  }

  @override
  String filterText(MixedEntry entry) => substackFilterText((entry.item as SubstackMixedPost).post);
}

const substackMixedSourceKinds = <MixedSourceKind>[SubstackPublicationMixedSource()];
