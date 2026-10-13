import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_load_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_query_page.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';

/// The pools [post] is in, each opening to be read in its own order;
/// nothing when it is in none.
class BooruPoolsSection extends StatefulWidget {
  final BooruPost post;

  const BooruPoolsSection({super.key, required this.post});

  @override
  State<BooruPoolsSection> createState() => _BooruPoolsSectionState();
}

class _BooruPoolsSectionState extends State<BooruPoolsSection> {
  late final BooruLoadStore<List<BooruPool>> _pools;

  @override
  void initState() {
    super.initState();
    final client = context.read<BooruClient>();
    _pools = BooruLoadStore(() => client.pools(widget.post));
    unawaited(_pools.ensure());
  }

  @override
  void dispose() {
    unawaited(_pools.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) => ScopedBuilder<BooruLoadStore<List<BooruPool>>, List<BooruPool>?>(
    store: _pools,
    onState: (context, pools) => pools == null || pools.isEmpty ? const SizedBox.shrink() : _list(context, pools),
  );

  Widget _list(BuildContext context, List<BooruPool> pools) {
    final l10n = L10n.of(context);
    final site = context.read<BooruClient>().siteOf(widget.post);
    return Column(
      crossAxisAlignment: CrossAxisAlignment.stretch,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(16, 16, 16, 0),
          child: Semantics(
            header: true,
            child: Text(l10n.plugin_booru_pools, style: Theme.of(context).textTheme.titleSmall),
          ),
        ),
        for (final pool in pools)
          ListTile(
            leading: const Icon(Icons.collections_bookmark_outlined),
            title: Text(booruTagDisplayName(pool.name)),
            subtitle: pool.postCount == null ? null : Text(l10n.plugin_booru_pool_size(pool.postCount!)),
            onTap: () => Navigator.push(
              context,
              MaterialPageRoute(
                builder: (_) => BooruQueryPage(
                  query: booruPoolQuery(pool.id),
                  site: site,
                  title: Text(booruTagDisplayName(pool.name)),
                ),
              ),
            ),
          ),
      ],
    );
  }
}
