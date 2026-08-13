import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// Similar publications for discovery — author recs, then name-search hits.
Future<void> showSubstackSimilarSheet(
  BuildContext context,
  SubstackPublication publication,
) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => _SubstackSimilarSheet(publication: publication),
  );
}

class _SubstackSimilarSheet extends StatefulWidget {
  final SubstackPublication publication;

  const _SubstackSimilarSheet({required this.publication});

  @override
  State<_SubstackSimilarSheet> createState() => _SubstackSimilarSheetState();
}

class _SubstackSimilarSheetState extends State<_SubstackSimilarSheet> {
  List<SubstackRecommendation> _results = const [];
  Object? _error;
  var _loading = true;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final results = await context
          .read<SubstackClient>()
          .fetchSimilarPublications(widget.publication);
      if (!mounted) {
        return;
      }
      setState(() {
        _results = results;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) {
        return;
      }
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  Future<void> _follow(SubstackPublication publication) async {
    final pubs = context.read<SubstackPublicationsStore>();
    final subscriptions = context.read<SubscriptionsModel>();
    final messenger = ScaffoldMessenger.of(context);
    final followed = L10n.of(
      context,
    ).plugin_substack_followed(publication.name);
    await pubs.add(publication);
    await subscriptions.reloadSubscriptions();
    if (!mounted) {
      return;
    }
    setState(() {});
    messenger.showSnackBar(SnackBar(content: Text(followed)));
  }

  bool _isFollowing(SubstackPublication publication) {
    return context.read<SubstackPublicationsStore>().state.any(
      (p) => p.id == publication.id,
    );
  }

  void _open(SubstackPublication publication) {
    Navigator.of(context, rootNavigator: true).push(
      MaterialPageRoute(
        builder: (_) => SubstackArchiveScreen(publication: publication),
      ),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return SizedBox(
      height: MediaQuery.sizeOf(context).height * 0.85,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
            child: Text(
              l10n.plugin_substack_similar_title,
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(
              l10n.plugin_substack_similar_intro,
              style: Theme.of(context).textTheme.bodyMedium,
            ),
          ),
          const SizedBox(height: 8),
          Expanded(child: _body(l10n)),
        ],
      ),
    );
  }

  Widget _body(L10n l10n) {
    if (_loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (_error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(
                l10n.plugin_substack_load_error,
                textAlign: TextAlign.center,
              ),
              const SizedBox(height: 12),
              FilledButton(onPressed: _load, child: Text(l10n.retry)),
            ],
          ),
        ),
      );
    }
    if (_results.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(
            l10n.plugin_substack_similar_empty,
            textAlign: TextAlign.center,
          ),
        ),
      );
    }

    return ListView.separated(
      itemCount: _results.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final rec = _results[index];
        final publication = rec.publication;
        final following = _isFollowing(publication);
        final subtitle = rec.blurb?.trim().isNotEmpty == true
            ? rec.blurb!
            : [
                publication.subdomain,
                if (publication.description != null &&
                    publication.description!.isNotEmpty)
                  publication.description!,
              ].join(' · ');
        return ListTile(
          leading: _logo(context, publication),
          title: Text(
            publication.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          subtitle: Text(
            subtitle,
            maxLines: 2,
            overflow: TextOverflow.ellipsis,
          ),
          trailing: Row(
            mainAxisSize: MainAxisSize.min,
            children: [
              if (!following)
                IconButton(
                  tooltip: l10n.plugin_substack_follow,
                  icon: const Icon(Icons.add_circle_outline),
                  onPressed: () => _follow(publication),
                )
              else
                Icon(
                  Icons.check_circle_outline,
                  color: Theme.of(context).colorScheme.primary,
                ),
              IconButton(
                tooltip: l10n.add_to_group,
                icon: const Icon(Icons.group_add_outlined),
                onPressed: () =>
                    addSubstackPublicationToGroup(context, publication),
              ),
            ],
          ),
          onTap: () => _open(publication),
        );
      },
    );
  }

  Widget _logo(BuildContext context, SubstackPublication publication) {
    final theme = Theme.of(context);
    final logo = publication.logoUrl;
    return ClipOval(
      child: logo == null || logo.isEmpty
          ? FallbackAvatar(
              seed: publication.subdomain,
              displayName: publication.name,
              size: 40,
              accent: theme.colorScheme.primary,
            )
          : ExtendedImage.network(
              logo,
              width: 40,
              height: 40,
              fit: BoxFit.cover,
              cacheWidth: (40 * MediaQuery.devicePixelRatioOf(context)).ceil(),
            ),
    );
  }
}
