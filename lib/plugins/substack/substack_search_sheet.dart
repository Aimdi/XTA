import 'package:extended_image/extended_image.dart';
import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/substack/substack_archive_screen.dart';
import 'package:xta/plugins/substack/substack_client.dart';
import 'package:xta/plugins/substack/substack_models.dart';
import 'package:xta/plugins/substack/substack_store.dart';
import 'package:xta/plugins/substack/substack_group.dart';
import 'package:xta/subscriptions/users_model.dart';
import 'package:xta/subscriptions/widgets/fallback_avatar.dart';

/// Discover and follow Substack publications — search by name/handle, or browse
/// category leaderboards (no Substack account required).
Future<bool?> showSubstackSearchSheet(BuildContext context) {
  return showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _SubstackSearchSheet(),
  );
}

class _SubstackSearchSheet extends StatefulWidget {
  const _SubstackSearchSheet();

  @override
  State<_SubstackSearchSheet> createState() => _SubstackSearchSheetState();
}

class _SubstackSearchSheetState extends State<_SubstackSearchSheet> {
  final _controller = TextEditingController();
  List<SubstackCategory> _categories = const [];
  List<SubstackPublication> _results = const [];
  SubstackCategory? _category;
  Object? _error;
  var _loading = false;
  var _searched = false;
  var _followedAny = false;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) => _loadCategories());
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _loadCategories() async {
    setState(() {
      _loading = true;
      _error = null;
    });
    try {
      final categories = await context.read<SubstackClient>().fetchCategories();
      if (!mounted) return;
      final first = categories.isEmpty ? null : categories.first;
      setState(() {
        _categories = categories;
        _category = first;
        _loading = false;
      });
      if (first != null) {
        await _loadCategory(first);
      }
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
      });
    }
  }

  Future<void> _loadCategory(SubstackCategory category) async {
    setState(() {
      _category = category;
      _loading = true;
      _error = null;
      _searched = false;
      _controller.clear();
    });
    try {
      final pubs = await context
          .read<SubstackClient>()
          .fetchCategoryPublications(category.id);
      if (!mounted) return;
      setState(() {
        _results = pubs;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
        _results = const [];
      });
    }
  }

  Future<void> _search() async {
    final query = _controller.text.trim();
    if (query.isEmpty) {
      final category = _category;
      if (category != null) await _loadCategory(category);
      return;
    }

    setState(() {
      _loading = true;
      _error = null;
      _searched = true;
    });

    try {
      final results = await context.read<SubstackClient>().discoverPublications(
        query,
      );
      if (!mounted) return;
      setState(() {
        _results = results;
        _loading = false;
      });
    } catch (e) {
      if (!mounted) return;
      setState(() {
        _error = e;
        _loading = false;
        _results = const [];
      });
    }
  }

  Future<void> _open(SubstackPublication publication) async {
    if (!mounted) return;
    Navigator.pop(context, _followedAny);
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => SubstackArchiveScreen(publication: publication),
      ),
    );
  }

  Future<void> _follow(SubstackPublication publication) async {
    final pubs = context.read<SubstackPublicationsStore>();
    final subscriptions = context.read<SubscriptionsModel>();
    final messenger = ScaffoldMessenger.of(context);
    final l10n = L10n.of(context);
    await pubs.add(publication);
    await subscriptions.reloadSubscriptions();
    _followedAny = true;
    if (!mounted) return;
    setState(() {});
    messenger.showSnackBar(
      SnackBar(content: Text(l10n.plugin_substack_followed(publication.name))),
    );
  }

  bool _isFollowing(SubstackPublication publication) {
    return context.read<SubstackPublicationsStore>().state.any(
      (p) => p.id == publication.id,
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final bottom = MediaQuery.viewInsetsOf(context).bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.85,
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: Text(
                l10n.plugin_substack_discover,
                style: Theme.of(context).textTheme.titleLarge,
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Text(
                l10n.plugin_substack_discover_intro,
                style: Theme.of(context).textTheme.bodyMedium,
              ),
            ),
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 12, 16, 8),
              child: TextField(
                controller: _controller,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  hintText: l10n.plugin_substack_search_hint,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.arrow_forward),
                    tooltip: l10n.plugin_substack_search,
                    onPressed: _search,
                  ),
                ),
                onSubmitted: (_) => _search(),
              ),
            ),
            if (_categories.isNotEmpty && !_searched)
              SizedBox(
                height: 44,
                child: ListView.separated(
                  scrollDirection: Axis.horizontal,
                  padding: const EdgeInsets.symmetric(horizontal: 16),
                  itemCount: _categories.length,
                  separatorBuilder: (_, _) => const SizedBox(width: 8),
                  itemBuilder: (context, index) {
                    final category = _categories[index];
                    final selected = category.id == _category?.id;
                    return ChoiceChip(
                      label: Text(category.name),
                      selected: selected,
                      onSelected: (_) => _loadCategory(category),
                    );
                  },
                ),
              ),
            Expanded(child: _body(l10n)),
          ],
        ),
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
              Text('$_error', textAlign: TextAlign.center),
              const SizedBox(height: 12),
              FilledButton(
                onPressed: () {
                  if (_searched) {
                    _search();
                  } else if (_category != null) {
                    _loadCategory(_category!);
                  } else {
                    _loadCategories();
                  }
                },
                child: Text(l10n.retry),
              ),
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
            _searched
                ? l10n.plugin_substack_search_empty
                : l10n.plugin_substack_discover_intro,
            textAlign: TextAlign.center,
          ),
        ),
      );
    }

    return ListView.separated(
      itemCount: _results.length,
      separatorBuilder: (_, _) => const Divider(height: 1),
      itemBuilder: (context, index) {
        final publication = _results[index];
        final following = _isFollowing(publication);
        return ListTile(
          leading: _logo(context, publication),
          title: Text(
            publication.name,
            maxLines: 1,
            overflow: TextOverflow.ellipsis,
          ),
          subtitle: Text(
            [
              publication.subdomain,
              if (publication.description != null &&
                  publication.description!.isNotEmpty)
                publication.description!,
            ].join(' · '),
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
