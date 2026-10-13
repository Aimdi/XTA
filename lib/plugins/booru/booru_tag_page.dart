import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_feed_view.dart';
import 'package:xta/plugins/booru/booru_load_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_actions.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/booru/booru_tag_sheet.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';

/// Kinds of tag that name someone or something with a page of their own.
const _pageKinds = {BooruTagCategory.artist, BooruTagCategory.character, BooruTagCategory.copyright};

/// Opens [tag]'s page when it names an artist, character or series, and a
/// search for it otherwise.
Future<void> openBooruTag(BuildContext context, String tag, {BooruTagCategory? category, BooruSite? site}) =>
    _pageKinds.contains(category)
    ? Navigator.push(
        context,
        MaterialPageRoute(
          builder: (_) => BooruTagPage(tag: tag, category: category, site: site),
        ),
      )
    : searchBooruTag(context, tag);

/// One tag's own page: its posts, the artist's other names and links for an
/// artist tag, and the tag's follow, wiki and search actions.
class BooruTagPage extends StatefulWidget {
  final String tag;
  final BooruTagCategory? category;

  /// The site the tag was seen on; the site in use by default.
  final BooruSite? site;

  const BooruTagPage({super.key, required this.tag, this.category, this.site});

  @override
  State<BooruTagPage> createState() => _BooruTagPageState();
}

class _BooruTagPageState extends State<BooruTagPage> {
  late final BooruFeedStore _posts;
  late final BooruLoadStore<({BooruArtist? artist})>? _artist;

  @override
  void initState() {
    super.initState();
    final client = context.read<BooruClient>();
    final site = widget.site;
    _posts = BooruFeedStore(client, ({required page}) => client.search(widget.tag, page: page, site: site));
    _artist = widget.category == BooruTagCategory.artist
        ? BooruLoadStore(() async => (artist: await client.artist(widget.tag, site: site)))
        : null;
    unawaited(_posts.refresh());
    unawaited(_artist?.ensure());
  }

  @override
  void dispose() {
    unawaited(_posts.destroy());
    unawaited(_artist?.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    final engine = (widget.site ?? context.read<BooruClient>().site).engine;
    return Scaffold(
      appBar: AppBar(
        title: Text.rich(booruTokenSpan(widget.tag, theme, category: widget.category)),
        actions: [
          _FollowButton(tag: widget.tag),
          if (booruSupportsWiki(engine))
            IconButton(
              tooltip: l10n.plugin_booru_wiki,
              icon: const Icon(Icons.menu_book_outlined),
              onPressed: () => showBooruWiki(context, widget.tag, site: widget.site),
            ),
          IconButton(
            tooltip: l10n.plugin_booru_search_tag,
            icon: const Icon(Icons.search),
            onPressed: () => searchBooruTag(context, widget.tag),
          ),
        ],
      ),
      body: Column(
        children: [
          if (_artist case final artist?) BooruArtistLinks(store: artist),
          Expanded(
            child: BooruFeedView(store: _posts, emptyLabel: l10n.plugin_booru_empty_search),
          ),
        ],
      ),
    );
  }
}

class _FollowButton extends StatelessWidget {
  final String tag;

  const _FollowButton({required this.tag});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final tags = context.read<BooruTagsStore>();
    return ScopedBuilder<BooruTagsStore, List<String>>(
      store: tags,
      onState: (context, followed) {
        final following = followed.contains(tag);
        return IconButton(
          tooltip: following ? l10n.plugin_booru_unfollow_tag : l10n.plugin_booru_follow_tag,
          icon: Icon(following ? Icons.check : Icons.sell_outlined),
          onPressed: () => following ? tags.remove(tag) : tags.add(tag),
        );
      },
    );
  }
}

/// The artist's other names and their pages elsewhere, once the host has
/// answered; nothing when it keeps no entry for them.
class BooruArtistLinks extends StatelessWidget {
  final BooruLoadStore<({BooruArtist? artist})> store;

  const BooruArtistLinks({super.key, required this.store});

  @override
  Widget build(BuildContext context) =>
      ScopedBuilder<BooruLoadStore<({BooruArtist? artist})>, ({BooruArtist? artist})?>(
        store: store,
        onState: (context, loaded) => switch (loaded?.artist) {
          final artist? when artist.otherNames.isNotEmpty || artist.urls.isNotEmpty => _links(context, artist),
          _ => const SizedBox.shrink(),
        },
      );

  Widget _links(BuildContext context, BooruArtist artist) {
    final theme = Theme.of(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(16, 8, 16, 4),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          if (artist.otherNames.isNotEmpty)
            Text(
              L10n.of(context).plugin_booru_also_known_as(artist.otherNames.map(booruTagDisplayName).join(', ')),
              style: theme.textTheme.bodySmall,
            ),
          if (artist.urls.isNotEmpty)
            Wrap(
              spacing: 8,
              children: [
                for (final url in artist.urls)
                  ActionChip(
                    avatar: const Icon(Icons.link, size: 18),
                    label: Text(_siteName(url)),
                    tooltip: url,
                    materialTapTargetSize: MaterialTapTargetSize.padded,
                    onPressed: () => openBooruLink(url),
                  ),
              ],
            ),
        ],
      ),
    );
  }
}

/// `https://www.pixiv.net/users/1` reads as `pixiv.net`.
String _siteName(String url) => (Uri.tryParse(url)?.host ?? url).replaceFirst(RegExp(r'^www\.'), '');
