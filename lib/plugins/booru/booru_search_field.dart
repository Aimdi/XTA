import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/booru/booru_query.dart';
import 'package:xta/plugins/booru/booru_search_store.dart';
import 'package:xta/plugins/booru/booru_tag_style.dart';

/// The search field: finished tags as chips, then the tag being typed.
class BooruTagField extends StatelessWidget {
  final BooruSearchStore store;
  final BooruSearchState state;
  final TextEditingController controller;
  final FocusNode focusNode;
  final bool autofocus;
  final VoidCallback onSearch;
  final ValueChanged<String> onEditTag;

  const BooruTagField({
    super.key,
    required this.store,
    required this.state,
    required this.controller,
    required this.focusNode,
    this.autofocus = false,
    required this.onSearch,
    required this.onEditTag,
  });

  @override
  Widget build(BuildContext context) {
    final theme = Theme.of(context);
    final l10n = L10n.of(context);
    return Material(
      color: theme.colorScheme.surfaceContainerHighest,
      borderRadius: BorderRadius.circular(24),
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: _focus,
        child: Row(
          children: [
            Expanded(
              child: ConstrainedBox(
                constraints: BoxConstraints(maxHeight: MediaQuery.sizeOf(context).height * 0.3),
                child: LayoutBuilder(
                  builder: (context, constraints) => SingleChildScrollView(
                    padding: const EdgeInsetsDirectional.only(start: 8),
                    child: Wrap(
                      spacing: 4,
                      crossAxisAlignment: WrapCrossAlignment.center,
                      children: [
                        for (final tag in state.tags) _chip(context, tag),
                        KeyedSubtree(
                          key: const ValueKey('booru-tag-input'),
                          // Alone, the input takes the whole row so its hint fits.
                          child: _input(l10n, minWidth: state.tags.isEmpty ? constraints.maxWidth - 8 : 112),
                        ),
                      ],
                    ),
                  ),
                ),
              ),
            ),
            IconButton(tooltip: l10n.search, icon: const Icon(Icons.search), onPressed: onSearch),
          ],
        ),
      ),
    );
  }

  Widget _chip(BuildContext context, String tag) {
    final theme = Theme.of(context);
    return InputChip(
      key: ValueKey('booru-tag-$tag'),
      label: Text.rich(booruTokenSpan(tag, theme, category: state.categories[BooruQueryToken.parse(tag).value])),
      tooltip: L10n.of(context).plugin_booru_edit_tag,
      visualDensity: VisualDensity.compact,
      materialTapTargetSize: MaterialTapTargetSize.padded,
      onPressed: () => onEditTag(tag),
      onDeleted: () => store.removeTag(tag),
      deleteButtonTooltipMessage: MaterialLocalizations.of(context).deleteButtonTooltip,
    );
  }

  Widget _input(L10n l10n, {required double minWidth}) {
    return ConstrainedBox(
      constraints: BoxConstraints(minWidth: minWidth),
      child: IntrinsicWidth(
        child: Focus(
          canRequestFocus: false,
          skipTraversal: true,
          onKeyEvent: _onKey,
          child: TextField(
            controller: controller,
            focusNode: focusNode,
            autofocus: autofocus,
            // Tags are written left to right; keep `-tag` from reading `tag-`.
            textDirection: state.input.isEmpty ? null : TextDirection.ltr,
            autocorrect: false,
            enableSuggestions: false,
            textInputAction: TextInputAction.search,
            decoration: InputDecoration(
              hintText: l10n.plugin_booru_add_tag_hint,
              filled: false,
              border: InputBorder.none,
              enabledBorder: InputBorder.none,
              focusedBorder: InputBorder.none,
              isDense: true,
              contentPadding: const EdgeInsetsDirectional.fromSTEB(4, 14, 4, 14),
            ),
            onTap: store.edit,
            onChanged: _onChanged,
            onSubmitted: (_) => onSearch(),
          ),
        ),
      ),
    );
  }

  void _onChanged(String text) {
    final rest = store.type(text);
    if (rest == text) return;
    controller.value = TextEditingValue(
      text: rest,
      selection: TextSelection.collapsed(offset: rest.length),
    );
  }

  KeyEventResult _onKey(FocusNode node, KeyEvent event) {
    final backspace = event is KeyDownEvent && event.logicalKey == LogicalKeyboardKey.backspace;
    if (!backspace || controller.text.isNotEmpty) return KeyEventResult.ignored;
    return store.removeLast() ? KeyEventResult.handled : KeyEventResult.ignored;
  }

  void _focus() {
    store.edit();
    focusNode.requestFocus();
  }
}
