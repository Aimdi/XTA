import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_discovery.dart';
import 'package:xta/plugins/instagram/instagram_errors.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_parse.dart';
import 'package:xta/plugins/instagram/instagram_post_card.dart';
import 'package:xta/plugins/instagram/instagram_profile_screen.dart';
import 'package:xta/plugins/instagram/instagram_store.dart';

Future<void> showInstagramSearchSheet(
  BuildContext context, {
  String? initialQuery,
}) async {
  final handle = await showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) => InstagramSearchSheet(
      initialQuery: initialQuery,
      onOpenProfile: (username) => Navigator.pop(sheetContext, username),
    ),
  );
  if (handle == null || !context.mounted) return;
  await Navigator.push(
    context,
    MaterialPageRoute(builder: (_) => InstagramProfileScreen(handle: handle)),
  );
}

class InstagramSearchSheet extends StatefulWidget {
  const InstagramSearchSheet({
    super.key,
    required this.onOpenProfile,
    this.initialQuery,
  });

  final ValueChanged<String> onOpenProfile;
  final String? initialQuery;

  @override
  State<InstagramSearchSheet> createState() => _InstagramSearchSheetState();
}

class _InstagramSearchSheetState extends State<InstagramSearchSheet> {
  late final TextEditingController _controller;
  var _searching = false;
  String? _error;
  List<InstagramSearchUser> _results = const [];

  @override
  void initState() {
    super.initState();
    _controller = TextEditingController(text: widget.initialQuery ?? '');
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      context.read<InstagramSearchHistoryStore>().load();
      if ((widget.initialQuery ?? '').trim().isNotEmpty) {
        _runSearch();
      }
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _openHandle(String handle) async {
    await context.read<InstagramSearchHistoryStore>().remember(handle);
    if (!mounted) return;
    widget.onOpenProfile(handle);
  }

  Future<void> _runSearch() async {
    final query = _controller.text.trim();
    if (query.isEmpty) return;
    final client = context.read<InstagramClient>();
    final handle = normaliseInstagramHandle(query);
    if (instagramSearchOpensHandle(
      hasSession: client.hasSession,
      query: query,
    )) {
      await _openHandle(handle!);
      return;
    }
    setState(() {
      _searching = true;
      _error = null;
    });
    try {
      final results = await client.searchUsers(query);
      if (!mounted) return;
      setState(() {
        _results = results;
        _searching = false;
      });
      await context.read<InstagramSearchHistoryStore>().remember(query);
    } catch (error) {
      if (!mounted) return;
      if (handle != null) {
        await _openHandle(handle);
        return;
      }
      setState(() {
        _searching = false;
        _error = instagramErrorMessage(L10n.of(context), error);
      });
    }
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final handle = normaliseInstagramHandle(_controller.text);
    final guest = !context.read<InstagramClient>().hasSession;
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.7,
        child: Padding(
          padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
          child: Column(
            children: [
              TextField(
                controller: _controller,
                autofocus: (widget.initialQuery ?? '').trim().isEmpty,
                textInputAction: TextInputAction.search,
                onChanged: (_) => setState(() {}),
                onSubmitted: (_) => _runSearch(),
                decoration: InputDecoration(
                  hintText: l10n.plugin_instagram_search_hint,
                  prefixIcon: const Icon(Icons.search),
                  suffixIcon: IconButton(
                    icon: const Icon(Icons.arrow_forward),
                    onPressed: _runSearch,
                  ),
                ),
              ),
              if (_searching) const LinearProgressIndicator(),
              if (guest)
                Padding(
                  padding: const EdgeInsets.only(top: 12),
                  child: Text(
                    l10n.plugin_instagram_search_guest_hint,
                    style: Theme.of(context).textTheme.bodySmall?.copyWith(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                    ),
                  ),
                ),
              if (handle != null)
                ListTile(
                  leading: const Icon(Icons.person_outline),
                  title: Text('@$handle'),
                  subtitle: Text(l10n.plugin_instagram_open_handle),
                  onTap: () => widget.onOpenProfile(handle),
                ),
              if (_error != null)
                Padding(
                  padding: const EdgeInsets.only(top: 8),
                  child: Text(_error!),
                ),
              Expanded(child: _searchBody(context, l10n)),
            ],
          ),
        ),
      ),
    );
  }

  Widget _searchBody(BuildContext context, L10n l10n) {
    if (_results.isNotEmpty) {
      return ListView(
        children: [
          for (final user in _results)
            ListTile(
              leading: InstagramAvatar(
                url: user.avatarUrl,
                seed: user.username,
                name: user.displayName,
              ),
              title: Row(
                children: [
                  Flexible(child: Text('@${user.username}')),
                  if (user.isVerified) ...[
                    const SizedBox(width: 4),
                    Icon(
                      Icons.verified,
                      size: 16,
                      color: Theme.of(context).colorScheme.primary,
                    ),
                  ],
                ],
              ),
              subtitle: user.fullName.isEmpty ? null : Text(user.fullName),
              trailing: user.isPrivate
                  ? const Icon(Icons.lock_outline, size: 18)
                  : null,
              onTap: () => widget.onOpenProfile(user.username),
            ),
        ],
      );
    }
    if (_searching || _error != null) return const SizedBox.shrink();
    return ScopedBuilder<InstagramSearchHistoryStore, List<String>>(
      store: context.read<InstagramSearchHistoryStore>(),
      onState: (_, history) {
        if (history.isEmpty) {
          return Padding(
            padding: const EdgeInsets.only(top: 16),
            child: Text(l10n.plugin_instagram_search_empty),
          );
        }
        return ListView(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(4, 16, 4, 4),
              child: Row(
                children: [
                  Expanded(
                    child: Text(
                      l10n.plugin_instagram_recent_searches,
                      style: Theme.of(context).textTheme.titleSmall,
                    ),
                  ),
                  TextButton(
                    onPressed: () =>
                        context.read<InstagramSearchHistoryStore>().clear(),
                    child: Text(l10n.delete),
                  ),
                ],
              ),
            ),
            for (final query in history)
              ListTile(
                leading: const Icon(Icons.history),
                title: Text(query),
                onTap: () {
                  _controller.text = query;
                  _runSearch();
                },
              ),
          ],
        );
      },
    );
  }
}
