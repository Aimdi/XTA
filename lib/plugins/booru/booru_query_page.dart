import 'dart:async';

import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_download.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_feed_view.dart';
import 'package:xta/plugins/booru/booru_store.dart';

/// One query's posts on a page of their own, such as a tag's or a pool's:
/// the feed under [title], with [actions] and Download all in the app bar
/// and an optional [header] above the posts.
class BooruQueryPage extends StatefulWidget {
  final String query;

  /// The site to ask; the site in use by default.
  final BooruSite? site;
  final Widget title;
  final List<Widget> actions;
  final Widget? header;

  const BooruQueryPage({
    super.key,
    required this.query,
    required this.title,
    this.site,
    this.actions = const [],
    this.header,
  });

  @override
  State<BooruQueryPage> createState() => _BooruQueryPageState();
}

class _BooruQueryPageState extends State<BooruQueryPage> {
  late final BooruFeedStore _posts;

  @override
  void initState() {
    super.initState();
    final client = context.read<BooruClient>();
    final site = widget.site;
    _posts = BooruFeedStore(client, ({required page}) => client.search(widget.query, page: page, site: site));
    unawaited(_posts.refresh());
  }

  @override
  void dispose() {
    unawaited(_posts.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: widget.title,
        actions: [
          ...widget.actions,
          IconButton(
            tooltip: l10n.plugin_booru_download_all,
            icon: const Icon(Icons.download_for_offline_outlined),
            onPressed: () => confirmBooruDownloadAll(context, _posts.state),
          ),
        ],
      ),
      body: Column(
        children: [
          ?widget.header,
          Expanded(
            child: BooruFeedView(store: _posts, emptyLabel: l10n.plugin_booru_empty_search),
          ),
        ],
      ),
    );
  }
}
