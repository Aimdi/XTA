import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_client.dart';
import 'package:xta/plugins/booru/booru_endpoints.dart';
import 'package:xta/plugins/booru/booru_errors.dart';
import 'package:xta/plugins/booru/booru_labels.dart';
import 'package:xta/plugins/booru/booru_load_store.dart';
import 'package:xta/plugins/booru/booru_models.dart';
import 'package:xta/plugins/booru/booru_post_actions.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_search_screen.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_store.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';
import 'package:xta/plugins/booru/booru_text.dart';

enum _TagAction { search, add, exclude, follow, hide, wiki, copy }

/// What can be done with one of a post's tags. With [search], the tag can
/// also refine the search the post was opened from.
Future<void> showBooruTagActions(
  BuildContext context, {
  required String tag,
  BooruTagCategory? category,
  BooruSearchStore? search,
}) async {
  final action = await showModalBottomSheet<_TagAction>(
    context: context,
    showDragHandle: true,
    isScrollControlled: true,
    builder: (_) => _TagActionSheet(
      tag: tag,
      category: category,
      inSearch: search != null,
      followed: context.read<BooruTagsStore>().state.contains(tag),
      hidden: context.read<BooruMuteStore>().state.contains(tag),
      wiki: booruSupportsWiki(context.read<BooruClient>().engine),
    ),
  );
  if (action == null || !context.mounted) return;
  await _run(context, action, tag, search);
}

Future<void> _run(BuildContext context, _TagAction action, String tag, BooruSearchStore? search) async {
  final tags = context.read<BooruTagsStore>();
  final muted = context.read<BooruMuteStore>();
  switch (action) {
    case _TagAction.search:
      await Navigator.push(context, MaterialPageRoute(builder: (_) => BooruSearchScreen(initialQuery: tag)));
    case _TagAction.add || _TagAction.exclude:
      final token = action == _TagAction.add ? tag : '${BooruTagOperator.exclude.prefix}$tag';
      unawaited(search?.refine(token));
      Navigator.pop(context);
    case _TagAction.follow:
      await (tags.state.contains(tag) ? tags.remove(tag) : tags.add(tag));
    case _TagAction.hide:
      await (muted.state.contains(tag) ? muted.unmute(tag) : muted.mute(tag));
    case _TagAction.wiki:
      await showBooruWiki(context, tag);
    case _TagAction.copy:
      await copyBooruText(context, tag);
  }
}

class _TagActionSheet extends StatelessWidget {
  final String tag;
  final BooruTagCategory? category;
  final bool inSearch;
  final bool followed;
  final bool hidden;
  final bool wiki;

  const _TagActionSheet({
    required this.tag,
    required this.category,
    required this.inSearch,
    required this.followed,
    required this.hidden,
    required this.wiki,
  });

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return SafeArea(
      child: SingleChildScrollView(
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            ListTile(
              title: Text.rich(booruTokenSpan(tag, theme, category: category, base: theme.textTheme.titleMedium)),
              subtitle: category == null ? null : Text(booruTagCategoryLabel(l10n, category)),
            ),
            const Divider(height: 1),
            _action(context, _TagAction.search, Icons.search, l10n.plugin_booru_search_tag),
            if (inSearch) ...[
              _action(context, _TagAction.add, Icons.add, l10n.plugin_booru_add_to_search),
              _action(context, _TagAction.exclude, Icons.remove, l10n.plugin_booru_exclude_from_search),
            ],
            _action(
              context,
              _TagAction.follow,
              followed ? Icons.check : Icons.sell_outlined,
              followed ? l10n.plugin_booru_unfollow_tag : l10n.plugin_booru_follow_tag,
            ),
            _action(
              context,
              _TagAction.hide,
              hidden ? Icons.visibility_outlined : Icons.visibility_off_outlined,
              hidden ? l10n.plugin_booru_unhide_tag : l10n.plugin_booru_hide_tag,
            ),
            if (wiki) _action(context, _TagAction.wiki, Icons.menu_book_outlined, l10n.plugin_booru_wiki),
            _action(context, _TagAction.copy, Icons.copy, l10n.plugin_booru_copy_tag),
          ],
        ),
      ),
    );
  }

  Widget _action(BuildContext context, _TagAction action, IconData icon, String label) => ListTile(
    key: ValueKey('booru-tag-action-${action.name}'),
    leading: Icon(icon),
    title: Text(label),
    onTap: () => Navigator.pop(context, action),
  );
}

Future<void> showBooruWiki(BuildContext context, String tag) => showModalBottomSheet<void>(
  context: context,
  showDragHandle: true,
  isScrollControlled: true,
  builder: (_) => _WikiSheet(tag: tag, client: context.read<BooruClient>()),
);

class _WikiSheet extends StatefulWidget {
  final String tag;
  final BooruClient client;

  const _WikiSheet({required this.tag, required this.client});

  @override
  State<_WikiSheet> createState() => _WikiSheetState();
}

class _WikiSheetState extends State<_WikiSheet> {
  /// An empty body is a tag without a page.
  late final _page = BooruLoadStore<String>(() async => await widget.client.wiki(widget.tag) ?? '')..ensure();

  @override
  void dispose() {
    unawaited(_page.destroy());
    super.dispose();
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);
    return DraggableScrollableSheet(
      expand: false,
      initialChildSize: 0.6,
      maxChildSize: 0.95,
      builder: (context, controller) => ScopedBuilder<BooruLoadStore<String>, String?>(
        store: _page,
        onLoading: (_) => const Center(child: CircularProgressIndicator()),
        onError: (_, error) => _message(booruErrorMessage(l10n, error), retry: _page.reload),
        onState: (context, body) {
          if (body == null) return const Center(child: CircularProgressIndicator());
          if (body.isEmpty) return _message(l10n.plugin_booru_wiki_empty);
          return ListView(
            controller: controller,
            padding: const EdgeInsets.fromLTRB(20, 0, 20, 24),
            children: [
              Text(widget.tag, style: theme.textTheme.titleLarge),
              const SizedBox(height: 12),
              SelectableText(booruPlainText(body), style: theme.textTheme.bodyMedium),
            ],
          );
        },
      ),
    );
  }

  Widget _message(String text, {VoidCallback? retry}) => Center(
    child: Padding(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(text, textAlign: TextAlign.center),
          if (retry != null) TextButton(onPressed: retry, child: Text(L10n.of(context).retry)),
        ],
      ),
    ),
  );
}
