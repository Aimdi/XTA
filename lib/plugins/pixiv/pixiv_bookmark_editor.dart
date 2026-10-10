import 'dart:async';

import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_actions.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_api.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_editor_store.dart';
import 'package:xta/plugins/pixiv/pixiv_bookmark_store.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/plugins/pixiv/pixiv_settings.dart';
import 'package:xta/plugins/plugin_counts.dart';

/// Opens the bookmark editor for [illust]: visibility, tags and removal.
Future<void> showPixivBookmarkEditor(BuildContext context, PixivIllust illust) async {
  final feedback = PixivBookmarkFeedback.of(context);
  final store = PixivBookmarkEditorStore(PixivBookmarkActions.of(context), illust);
  await showModalBottomSheet<void>(
    context: context,
    isScrollControlled: true,
    showDragHandle: true,
    useSafeArea: true,
    builder: (_) => PixivBookmarkEditor(store: store, feedback: feedback),
  );
  unawaited(store.close());
}

/// The editor sheet: a Private switch, the tag checklist with select-all and
/// an add-tag field that suggests the reader's own tags, Remove and Save.
class PixivBookmarkEditor extends StatefulWidget {
  final PixivBookmarkEditorStore store;

  /// Hears every write that lands, also one the sheet was dragged away from.
  final PixivBookmarkFeedback feedback;

  const PixivBookmarkEditor({super.key, required this.store, required this.feedback});

  @override
  State<PixivBookmarkEditor> createState() => _PixivBookmarkEditorState();
}

class _PixivBookmarkEditorState extends State<PixivBookmarkEditor> {
  final _field = TextEditingController();

  PixivBookmarkEditorStore get _store => widget.store;

  @override
  void initState() {
    super.initState();
    _store.load();
  }

  @override
  void dispose() {
    _field.dispose();
    super.dispose();
  }

  void _add(String input) {
    _store.addTags(input);
    _field.clear();
  }

  /// A tag still in the field goes along, as it would after Done.
  void _save() {
    if (_field.text.trim().isNotEmpty) _add(_field.text);
    _finish(_store.save);
  }

  /// Reports through the feedback rather than the sheet's result: a drag
  /// closes the sheet even mid-write, and the reader still hears how it went.
  Future<void> _finish(Future<PixivBookmarkOutcome?> Function() write) async {
    final navigator = Navigator.of(context);
    final feedback = widget.feedback;
    final illust = _store.illust;
    try {
      final outcome = await write();
      if (outcome == null) return;
      feedback.succeeded(illust, outcome);
      if (mounted) navigator.pop();
    } catch (error) {
      if (!mounted) feedback.failed(error);
    }
  }

  @override
  Widget build(BuildContext context) => Padding(
    padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
    child: SafeArea(
      top: false,
      child: ConstrainedBox(
        constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.85),
        child: ScopedBuilder<PixivBookmarkEditorStore, PixivBookmarkDraft>(
          store: _store,
          onState: (context, draft) => draft.loaded ? _whileFree(draft) : const _SheetMessage.loading(),
          onLoading: (_) => const _SheetMessage.loading(),
          onError: (context, error) => _loadFailed(context, error as Object),
        ),
      ),
    ),
  );

  Widget _loadFailed(BuildContext context, Object error) {
    final l10n = L10n.of(context);
    return _SheetMessage(
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          Text(pixivErrorMessage(l10n, error), textAlign: TextAlign.center),
          const SizedBox(height: 16),
          FilledButton.icon(onPressed: _store.load, icon: const Icon(Icons.refresh), label: Text(l10n.retry)),
        ],
      ),
    );
  }

  /// Save and Remove wait while any write for this work runs, the heart's
  /// included, since a second write would be skipped. While the editor's own
  /// write runs, back and a tap outside leave the sheet open.
  Widget _whileFree(PixivBookmarkDraft draft) {
    final bookmarks = _store.actions.bookmarks;
    final id = _store.illust.id;
    return ScopedBuilder<PixivBookmarkStore, Map<int, bool>>(
      store: bookmarks,
      distinct: (_) => bookmarks.isBusy(id),
      onState: (context, _) => PopScope(
        canPop: !draft.saving,
        child: _editor(context, draft, writing: draft.saving || bookmarks.isBusy(id)),
      ),
    );
  }

  /// The list scrolls and the buttons stay under it, yet take at most half
  /// the sheet: with large text and the keyboard up they scroll rather than
  /// overflow.
  Widget _editor(BuildContext context, PixivBookmarkDraft draft, {required bool writing}) {
    final l10n = L10n.of(context);
    return LayoutBuilder(
      builder: (context, constraints) => Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.stretch,
        children: [
          if (writing) const LinearProgressIndicator(),
          Flexible(
            child: ListView(
              shrinkWrap: true,
              children: [
                _title(context, draft, writing: writing),
                SwitchListTile(
                  key: const ValueKey('pixiv-bookmark-editor-private'),
                  secondary: const Icon(Icons.lock_outline),
                  title: Text(l10n.plugin_pixiv_bookmark_private),
                  subtitle: Text(l10n.plugin_pixiv_bookmark_private_hint),
                  value: draft.isPrivate,
                  onChanged: draft.saving ? null : _store.setPrivate,
                ),
                _tagsHeader(context, draft),
                _tagField(l10n, draft),
                for (final tag in draft.suggestions) _suggestion(tag),
                ..._checklist(context, draft),
              ],
            ),
          ),
          ConstrainedBox(
            constraints: BoxConstraints(maxHeight: constraints.maxHeight / 2),
            child: SingleChildScrollView(
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.stretch,
                children: [
                  if (draft.saveError case final Object error) _saveFailed(context, error),
                  _buttons(l10n, draft, writing: writing),
                ],
              ),
            ),
          ),
        ],
      ),
    );
  }

  Widget _title(BuildContext context, PixivBookmarkDraft draft, {required bool writing}) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 0, 8, 4),
      child: Row(
        children: [
          Expanded(
            child: Text(
              draft.isBookmarked ? l10n.plugin_pixiv_bookmark_edit : l10n.plugin_pixiv_bookmark_add,
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
          if (draft.isBookmarked)
            IconButton(
              key: const ValueKey('pixiv-bookmark-editor-remove'),
              tooltip: l10n.plugin_pixiv_unbookmark,
              icon: const Icon(Icons.heart_broken_outlined),
              onPressed: writing ? null : () => _finish(_store.remove),
            ),
        ],
      ),
    );
  }

  Widget _tagsHeader(BuildContext context, PixivBookmarkDraft draft) {
    final l10n = L10n.of(context);
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(16, 8, 8, 0),
      child: Row(
        children: [
          Expanded(child: Text(l10n.plugin_pixiv_bookmark_tags, style: Theme.of(context).textTheme.titleSmall)),
          if (draft.tags.isNotEmpty)
            TextButton(
              key: const ValueKey('pixiv-bookmark-editor-all'),
              onPressed: draft.saving ? null : _store.toggleAll,
              child: Text(
                draft.allChecked ? l10n.plugin_pixiv_bookmark_clear_all : l10n.plugin_pixiv_bookmark_select_all,
              ),
            ),
        ],
      ),
    );
  }

  Widget _tagField(L10n l10n, PixivBookmarkDraft draft) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 4, 16, 4),
    child: TextField(
      key: const ValueKey('pixiv-bookmark-editor-field'),
      controller: _field,
      enabled: !draft.saving,
      autocorrect: false,
      textInputAction: TextInputAction.done,
      decoration: InputDecoration(
        hintText: l10n.plugin_pixiv_bookmark_tag_hint,
        prefixIcon: const Icon(Icons.sell_outlined),
        suffixIcon: IconButton(
          tooltip: l10n.plugin_pixiv_bookmark_tag_add,
          icon: const Icon(Icons.add),
          onPressed: () => _add(_field.text),
        ),
      ),
      onChanged: _store.setQuery,
      onSubmitted: _add,
    ),
  );

  Widget _suggestion(PixivBookmarkTag tag) => ListTile(
    key: ValueKey('pixiv-bookmark-suggestion-${tag.name}'),
    leading: const Icon(Icons.north_west),
    title: Text(tag.name),
    trailing: Text(compactCount(tag.count)),
    onTap: () => _add(tag.name),
  );

  List<Widget> _checklist(BuildContext context, PixivBookmarkDraft draft) => [
    if (draft.tags.isEmpty)
      Padding(
        padding: const EdgeInsets.all(16),
        child: Text(
          L10n.of(context).plugin_pixiv_bookmark_tags_empty,
          style: TextStyle(color: Theme.of(context).colorScheme.onSurfaceVariant),
        ),
      ),
    for (final tag in draft.tags)
      CheckboxListTile(
        key: ValueKey('pixiv-bookmark-tag-${tag.name}'),
        title: Text(tag.name),
        value: tag.checked,
        onChanged: draft.saving ? null : (checked) => _store.setTag(tag.name, checked == true),
      ),
  ];

  Widget _saveFailed(BuildContext context, Object error) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 0),
    child: Text(
      pixivErrorMessage(L10n.of(context), error),
      style: TextStyle(color: Theme.of(context).colorScheme.error),
    ),
  );

  Widget _buttons(L10n l10n, PixivBookmarkDraft draft, {required bool writing}) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 8, 16, 16),
    child: OverflowBar(
      alignment: MainAxisAlignment.end,
      overflowAlignment: OverflowBarAlignment.end,
      spacing: 8,
      overflowSpacing: 4,
      children: [
        TextButton(onPressed: draft.saving ? null : () => Navigator.pop(context), child: Text(l10n.cancel)),
        FilledButton(
          key: const ValueKey('pixiv-bookmark-editor-save'),
          onPressed: writing ? null : _save,
          child: Text(draft.isBookmarked ? l10n.save : l10n.plugin_pixiv_bookmark),
        ),
      ],
    ),
  );
}

/// One centred message or spinner, tall enough not to jump the sheet, and
/// scrolling when the sheet is shorter than that.
class _SheetMessage extends StatelessWidget {
  final Widget child;

  const _SheetMessage({required this.child});

  const _SheetMessage.loading() : child = const CircularProgressIndicator();

  @override
  Widget build(BuildContext context) => SingleChildScrollView(
    child: ConstrainedBox(
      constraints: const BoxConstraints(minHeight: 200),
      child: Center(
        child: Padding(padding: const EdgeInsets.all(24), child: child),
      ),
    ),
  );
}
