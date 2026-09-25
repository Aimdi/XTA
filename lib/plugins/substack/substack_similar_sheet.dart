import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/substack/substack_publication_store.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// Similar publications for discovery — author recs, then name-search hits.
Future<void> showSubstackSimilarSheet(BuildContext context, SubstackPublication publication) {
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
  late final SubstackSimilarStore _store;

  @override
  void initState() {
    super.initState();
    _store = SubstackSimilarStore(context.read(), widget.publication);
    _store.load();
  }

  @override
  void dispose() {
    _store.destroy();
    super.dispose();
  }

  void _open(SubstackPublication publication) {
    Navigator.of(
      context,
      rootNavigator: true,
    ).push(MaterialPageRoute(builder: (_) => SubstackArchiveScreen(publication: publication)));
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
            child: Row(
              children: [
                Expanded(
                  child: Text(l10n.plugin_substack_similar_title, style: Theme.of(context).textTheme.titleLarge),
                ),
                CloseButton(onPressed: () => Navigator.of(context).pop()),
              ],
            ),
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: Text(l10n.plugin_substack_similar_intro, style: Theme.of(context).textTheme.bodyMedium),
          ),
          const SizedBox(height: 8),
          Expanded(
            child: ScopedBuilder<SubstackSimilarStore, SubstackSimilarState>(
              store: _store,
              onState: (context, state) => _body(l10n, state),
            ),
          ),
        ],
      ),
    );
  }

  Widget _body(L10n l10n, SubstackSimilarState state) {
    if (state.loading) {
      return const Center(child: CircularProgressIndicator());
    }
    if (state.error != null) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              Text(l10n.plugin_substack_load_error, textAlign: TextAlign.center),
              const SizedBox(height: 12),
              FilledButton(onPressed: _store.load, child: Text(l10n.retry)),
            ],
          ),
        ),
      );
    }
    if (state.recommendations.isEmpty) {
      return Center(
        child: Padding(
          padding: const EdgeInsets.all(24),
          child: Text(l10n.plugin_substack_similar_empty, textAlign: TextAlign.center),
        ),
      );
    }

    return ScopedBuilder<SubstackPublicationsStore, List<SubstackPublication>>(
      store: context.read<SubstackPublicationsStore>(),
      onState: (context, _) => ListView.separated(
        padding: const EdgeInsets.only(bottom: 24),
        itemCount: state.recommendations.length,
        separatorBuilder: (_, _) => const Divider(height: 1),
        itemBuilder: (context, index) {
          final rec = state.recommendations[index];
          final publication = rec.publication;
          final subtitle = rec.blurb?.trim().isNotEmpty == true
              ? rec.blurb!
              : publication.description ?? publication.subdomain;
          return Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 12),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.stretch,
              children: [
                InkWell(
                  onTap: () => _open(publication),
                  child: Padding(
                    padding: const EdgeInsets.symmetric(vertical: 4),
                    child: Row(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        _logo(context, publication),
                        const SizedBox(width: 12),
                        Expanded(
                          child: Column(
                            crossAxisAlignment: CrossAxisAlignment.start,
                            children: [
                              Text(publication.displayName, style: Theme.of(context).textTheme.titleMedium),
                              Text(subtitle, maxLines: 3, overflow: TextOverflow.ellipsis),
                            ],
                          ),
                        ),
                      ],
                    ),
                  ),
                ),
                const SizedBox(height: 8),
                Wrap(
                  spacing: 8,
                  runSpacing: 8,
                  children: [
                    SubstackFollowButton(publication: publication),
                    OutlinedButton.icon(
                      icon: const Icon(Icons.group_add_outlined, size: 18),
                      label: Text(l10n.add_to_group),
                      onPressed: () => addSubstackPublicationToGroup(context, publication),
                    ),
                  ],
                ),
              ],
            ),
          );
        },
      ),
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
