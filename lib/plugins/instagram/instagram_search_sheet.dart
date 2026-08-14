import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/instagram/instagram_client.dart';
import 'package:xta/plugins/instagram/instagram_errors.dart';
import 'package:xta/plugins/instagram/instagram_models.dart';
import 'package:xta/plugins/instagram/instagram_parse.dart';
import 'package:xta/plugins/instagram/instagram_post_card.dart';
import 'package:xta/plugins/instagram/instagram_profile_screen.dart';
import 'package:xta/plugins/instagram/instagram_store.dart';

Future<void> showInstagramSearchSheet(BuildContext context) async {
  final handle = await showModalBottomSheet<String>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) => InstagramSearchSheet(
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
  const InstagramSearchSheet({super.key, required this.onOpenProfile});

  final ValueChanged<String> onOpenProfile;

  @override
  State<InstagramSearchSheet> createState() => _InstagramSearchSheetState();
}

class _InstagramSearchSheetState extends State<InstagramSearchSheet> {
  final _controller = TextEditingController();
  var _searching = false;
  String? _error;
  List<InstagramSearchUser> _results = const [];

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (!mounted) return;
      context.read<InstagramSearchHistoryStore>().load();
    });
  }

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _runSearch() async {
    final query = _controller.text.trim();
    if (query.isEmpty) return;
    setState(() {
      _searching = true;
      _error = null;
    });
    try {
      final results = await context.read<InstagramClient>().searchUsers(query);
      if (!mounted) return;
      setState(() {
        _results = results;
        _searching = false;
      });
      await context.read<InstagramSearchHistoryStore>().remember(query);
    } catch (error) {
      if (!mounted) return;
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
    return SafeArea(
      child: Padding(
        padding: EdgeInsets.only(
          left: 16,
          right: 16,
          top: 8,
          bottom: MediaQuery.viewInsetsOf(context).bottom + 16,
        ),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            TextField(
              controller: _controller,
              autofocus: true,
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
            if (_results.isNotEmpty)
              Flexible(
                child: ListView(
                  shrinkWrap: true,
                  children: [
                    for (final user in _results)
                      ListTile(
                        leading: InstagramAvatar(
                          url: user.avatarUrl,
                          seed: user.username,
                          name: user.displayName,
                        ),
                        title: Text('@${user.username}'),
                        subtitle: user.fullName.isEmpty
                            ? null
                            : Text(user.fullName),
                        trailing: user.isPrivate
                            ? const Icon(Icons.lock_outline, size: 18)
                            : null,
                        onTap: () => widget.onOpenProfile(user.username),
                      ),
                  ],
                ),
              )
            else if (!_searching && _error == null && _results.isEmpty)
              ScopedBuilder<InstagramSearchHistoryStore, List<String>>(
                store: context.read<InstagramSearchHistoryStore>(),
                onState: (_, history) {
                  if (history.isEmpty) {
                    return Padding(
                      padding: const EdgeInsets.only(top: 16),
                      child: Text(l10n.plugin_instagram_search_empty),
                    );
                  }
                  return Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
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
                              onPressed: () => context
                                  .read<InstagramSearchHistoryStore>()
                                  .clear(),
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
              ),
          ],
        ),
      ),
    );
  }
}
