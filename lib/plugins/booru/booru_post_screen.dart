import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_comments.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_engines.dart';
import 'package:xta/plugins/booru/booru_load_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_actions.dart';
import 'package:xta/plugins/booru/booru_post_facts.dart';
import 'package:xta/plugins/booru/booru_post_media.dart';
import 'package:xta/plugins/booru/booru_post_pager.dart';
import 'package:xta/plugins/booru/booru_related.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_tag_list.dart';
import 'package:xta/plugins/booru/booru_tag_sheet.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';

/// One post on its own, as opened from a card in a mixed feed.
class BooruPostScreen extends StatelessWidget {
  final BooruPost post;

  /// The search this post was opened from; its tags can be added to it.
  final BooruSearchStore? search;

  const BooruPostScreen({super.key, required this.post, this.search});

  @override
  Widget build(BuildContext context) => BooruPostPager(posts: [post], search: search);
}

/// Everything about one post: the picture, its facts, its tags by kind,
/// related posts and comments.
class BooruPostDetails extends StatefulWidget {
  final BooruPost post;
  final BooruSearchStore? search;

  const BooruPostDetails({super.key, required this.post, this.search});

  @override
  State<BooruPostDetails> createState() => _BooruPostDetailsState();
}

class _BooruPostDetailsState extends State<BooruPostDetails> {
  late final BooruLoadStore<Map<String, BooruTagInfo>> _tagInfo;
  late final BooruEngine _engine;

  BooruPost get _post => widget.post;

  @override
  void initState() {
    super.initState();
    final client = context.read<BooruClient>();
    _engine = BooruEngine.tryParse(_post.engine) ?? client.engine;
    _tagInfo = BooruLoadStore(() => client.tagInfo(_post));
    unawaited(_tagInfo.ensure());
  }

  @override
  void dispose() {
    unawaited(_tagInfo.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final source = _post.source?.trim() ?? '';
    return ListView(
      padding: const EdgeInsets.only(bottom: 24),
      children: [
        BooruPostMedia(post: _post),
        BooruPostFacts(post: _post),
        ScopedBuilder<BooruLoadStore<Map<String, BooruTagInfo>>, Map<String, BooruTagInfo>?>(
          store: _tagInfo,
          onLoading: (_) => _tags(_postInfo),
          onError: (_, _) => _tags(_postInfo),
          onState: (_, info) => _tags(info ?? _postInfo),
        ),
        if (source.isNotEmpty)
          ListTile(
            leading: const Icon(Icons.link),
            title: Text(l10n.plugin_booru_source),
            subtitle: Text(source, maxLines: 2, overflow: TextOverflow.ellipsis),
            onTap: () => openBooruLink(source),
          ),
        if (_post.hasFamily)
          BooruRelatedStrip(title: l10n.plugin_booru_family, query: booruFamilyQuery(_post), exclude: _post),
        if (booruSupportsComments(_engine)) BooruComments(post: _post),
      ],
    );
  }

  /// What the post itself says, shown until the lookup answers.
  Map<String, BooruTagInfo> get _postInfo => {
    for (final MapEntry(key: tag, value: category) in _post.tagCategories.entries)
      tag: BooruTagInfo(category: category),
  };

  Widget _tags(Map<String, BooruTagInfo> info) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    final artist = booruArtistTag(_post.tags, info);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        BooruTagSection(tags: _post.tags, info: info, onTap: _openTag),
        if (_post.tags.isNotEmpty)
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
            child: Text(l10n.plugin_booru_tag_actions_hint, style: theme.textTheme.bodySmall),
          ),
        if (artist != null)
          BooruRelatedStrip(
            key: ValueKey('booru-artist-$artist'),
            title: l10n.plugin_booru_more_from(booruTagDisplayName(artist)),
            query: artist,
            exclude: _post,
          ),
      ],
    );
  }

  void _openTag(String tag, BooruTagCategory? category) =>
      showBooruTagActions(context, tag: tag, category: category, search: widget.search);
}
