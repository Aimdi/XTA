import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_grid.dart';
import 'package:xta/plugins/booru/booru_image.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';
import 'package:xta/plugins/booru/booru_store.dart';

/// Posts sharing [post]'s parent, the parent included where the host lists it.
String booruFamilyQuery(BooruPost post) => 'parent:${post.parentId ?? post.id}';

/// A row of posts for [query] under [title], leaving out the post on screen.
/// Shows nothing until there is something to show.
class BooruRelatedStrip extends StatefulWidget {
  static const height = 132.0;

  final String title;
  final String query;
  final BooruPost exclude;

  const BooruRelatedStrip({super.key, required this.title, required this.query, required this.exclude});

  @override
  State<BooruRelatedStrip> createState() => _BooruRelatedStripState();
}

class _BooruRelatedStripState extends State<BooruRelatedStrip> {
  late final BooruFeedStore _feed;

  @override
  void initState() {
    super.initState();
    final client = context.read<BooruClient>();
    _feed = BooruFeedStore(client, ({required page}) => client.search(widget.query, page: page, limit: 20));
    unawaited(_feed.refresh());
  }

  @override
  void dispose() {
    unawaited(_feed.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<BooruFeedStore, List<BooruPost>>(
      store: _feed,
      onLoading: (_) => const SizedBox.shrink(),
      onError: (_, _) => const SizedBox.shrink(),
      onState: (context, posts) {
        final others = [
          for (final post in posts)
            if (post.id != widget.exclude.id) post,
        ];
        return others.isEmpty ? const SizedBox.shrink() : _strip(context, others);
      },
    );
  }

  Widget _strip(BuildContext context, List<BooruPost> posts) {
    final theme = Theme.of(context);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsetsDirectional.fromSTEB(16, 12, 4, 0),
          child: Row(
            children: [
              Expanded(child: Text(widget.title, style: theme.textTheme.titleSmall)),
              TextButton(
                onPressed: () => Navigator.push(
                  context,
                  MaterialPageRoute(builder: (_) => BooruSearchScreen(initialQuery: widget.query)),
                ),
                child: Text(L10n.of(context).plugin_booru_see_all),
              ),
            ],
          ),
        ),
        SizedBox(
          height: BooruRelatedStrip.height,
          child: ListView.separated(
            scrollDirection: Axis.horizontal,
            padding: const EdgeInsets.symmetric(horizontal: 16),
            itemCount: posts.length,
            separatorBuilder: (_, _) => const SizedBox(width: 8),
            itemBuilder: (context, index) => _thumb(context, posts, index),
          ),
        ),
      ],
    );
  }

  Widget _thumb(BuildContext context, List<BooruPost> posts, int index) {
    final post = posts[index];
    final width = BooruRelatedStrip.height * post.aspectRatio.clamp(0.6, 1.5);
    return ClipRRect(
      borderRadius: BorderRadius.circular(8),
      child: Material(
        child: InkWell(
          onTap: () => openBooruPost(context, posts, index),
          child: SizedBox(
            width: width,
            child: BooruNetworkImage(url: post.thumbnailUrl, fit: BoxFit.cover),
          ),
        ),
      ),
    );
  }
}
