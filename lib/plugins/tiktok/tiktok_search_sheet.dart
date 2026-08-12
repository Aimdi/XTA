import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/tiktok/tiktok_parse.dart';
import 'package:xta/plugins/tiktok/tiktok_profile_screen.dart';
import 'package:xta/plugins/tiktok/tiktok_store.dart';

Future<void> showTikTokSearchSheet(BuildContext context) {
  return showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    builder: (_) => const _TikTokSearchSheet(),
  );
}

class _TikTokSearchSheet extends StatefulWidget {
  const _TikTokSearchSheet();

  @override
  State<_TikTokSearchSheet> createState() => _TikTokSearchSheetState();
}

class _TikTokSearchSheetState extends State<_TikTokSearchSheet> {
  final _controller = TextEditingController();
  String? _error;

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _open(String raw) async {
    final handle = normaliseTikTokHandle(raw);
    if (handle == null) {
      setState(() => _error = L10n.of(context).plugin_tiktok_invalid_handle);
      return;
    }
    final navigator = Navigator.of(context);
    navigator.pop();
    await navigator.push(
      MaterialPageRoute(builder: (_) => TikTokProfileScreen(handle: handle)),
    );
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final bottom = MediaQuery.viewInsetsOf(context).bottom;

    return Padding(
      padding: EdgeInsets.only(bottom: bottom),
      child: SizedBox(
        height: MediaQuery.sizeOf(context).height * 0.55,
        child: Column(
          children: [
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
              child: TextField(
                controller: _controller,
                autofocus: true,
                textInputAction: TextInputAction.search,
                decoration: InputDecoration(
                  labelText: l10n.plugin_tiktok_search_hint,
                  errorText: _error,
                  prefixText: '@',
                ),
                onSubmitted: _open,
              ),
            ),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 16),
              child: Align(
                alignment: Alignment.centerRight,
                child: FilledButton(
                  onPressed: () => _open(_controller.text),
                  child: Text(l10n.plugin_tiktok_open_handle),
                ),
              ),
            ),
            Expanded(
              child: ScopedBuilder<TikTokSearchHistoryStore, List<String>>(
                store: context.read<TikTokSearchHistoryStore>(),
                onState: (context, history) {
                  if (history.isEmpty) return const SizedBox.shrink();
                  return ListView(
                    children: [
                      Padding(
                        padding: const EdgeInsets.fromLTRB(16, 16, 16, 8),
                        child: Text(l10n.plugin_tiktok_recent_handles),
                      ),
                      for (final handle in history)
                        ListTile(
                          leading: const Icon(Icons.history),
                          title: Text('@$handle'),
                          onTap: () => _open(handle),
                        ),
                    ],
                  );
                },
              ),
            ),
          ],
        ),
      ),
    );
  }
}
