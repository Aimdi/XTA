import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/x_controls.dart';
import 'package:xta/utils/quick_pick.dart';

/// Keys the shared parts carry, for tests and for finding them.
const kQuickPickAllKey = ValueKey('quick-pick-all');
const kQuickPickCreateKey = ValueKey('quick-pick-create');
const kQuickPickStatusKey = ValueKey('quick-pick-status');

/// A quick-pick sheet: the folder a post is saved in, the groups an account
/// is in. The title row, the search field, the chips under their label with
/// the create row above them, whatever the sheet adds after them, and the
/// status line, risen with the keyboard so the field stays in view.
class QuickPickBody extends StatelessWidget {
  final Widget title;
  final TextEditingController field;
  final ValueChanged<String> onSearch;
  final String? sectionLabel;
  final Widget? createRow;
  final List<Widget> chips;
  final List<Widget> after;
  final Widget status;

  const QuickPickBody({
    super.key,
    required this.title,
    required this.field,
    required this.onSearch,
    required this.sectionLabel,
    required this.createRow,
    required this.chips,
    this.after = const [],
    required this.status,
  });

  @override
  Widget build(BuildContext context) {
    final label = sectionLabel;
    final create = createRow;
    return Padding(
      padding: EdgeInsets.only(bottom: MediaQuery.viewInsetsOf(context).bottom),
      child: SafeArea(
        top: false,
        child: Column(
          mainAxisSize: MainAxisSize.min,
          crossAxisAlignment: CrossAxisAlignment.stretch,
          children: [
            title,
            Padding(
              padding: const EdgeInsets.fromLTRB(16, 4, 16, 6),
              child: XSearchField(
                controller: field,
                hintText: L10n.of(context).picker_search_or_create,
                onChanged: onSearch,
              ),
            ),
            Flexible(
              child: SingleChildScrollView(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.stretch,
                  children: [
                    if (label != null) _SectionLabel(text: label),
                    ?create,
                    Wrap(spacing: 8, runSpacing: 8, children: chips),
                    const SizedBox(height: 8),
                    ...after,
                  ],
                ),
              ),
            ),
            status,
          ],
        ),
      ),
    );
  }
}

/// What the chips are under: [all] and [noMatch] name the sheet's own choices.
String quickPickSectionText(L10n l10n, QuickPickSection section, {required String all, required String noMatch}) =>
    switch (section) {
      QuickPickSection.recent => l10n.picker_recently_used,
      QuickPickSection.all => all,
      QuickPickSection.matches => l10n.picker_matches,
      QuickPickSection.noMatch => noMatch,
    };

/// The sheet's own colour, which its accents are made readable against.
Color _sheetColor(BuildContext context) =>
    Theme.of(context).bottomSheetTheme.modalBackgroundColor ?? Theme.of(context).colorScheme.surfaceContainerLow;

/// The fill a chip and the create row sit on, a step up from the sheet.
Color _raisedFill(BuildContext context) =>
    Color.alphaBlend(tweetPrimaryColor(context).withValues(alpha: 0.06), _sheetColor(context));

/// [title], with [allLabel] at its end while [onShowAll] has more to show.
class QuickPickTitle extends StatelessWidget {
  final String title;
  final String allLabel;
  final VoidCallback? onShowAll;

  const QuickPickTitle({super.key, required this.title, required this.allLabel, required this.onShowAll});

  @override
  Widget build(BuildContext context) {
    final showAll = onShowAll;
    return Padding(
      padding: const EdgeInsetsDirectional.fromSTEB(20, 0, 8, 4),
      // Side by side while they fit; with large text on a narrow phone the
      // link goes under the title rather than breaking it mid-word.
      child: Wrap(
        alignment: WrapAlignment.spaceBetween,
        crossAxisAlignment: WrapCrossAlignment.center,
        children: [
          Text(title, style: Theme.of(context).textTheme.titleLarge!.copyWith(fontWeight: FontWeight.w700)),
          if (showAll != null)
            TextButton.icon(
              key: kQuickPickAllKey,
              onPressed: showAll,
              iconAlignment: IconAlignment.end,
              icon: const Icon(Icons.chevron_right, size: 20),
              label: Text(allLabel),
            ),
        ],
      ),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String text;

  const _SectionLabel({required this.text});

  @override
  Widget build(BuildContext context) {
    return Padding(
      padding: const EdgeInsets.fromLTRB(4, 10, 4, 8),
      child: Text(
        text,
        style: Theme.of(context).textTheme.labelLarge!.copyWith(
          fontSize: 13,
          fontWeight: FontWeight.w600,
          color: tweetSecondaryColor(context),
        ),
      ),
    );
  }
}

/// "Create “Japan” and …", for a typed name nothing has yet.
class QuickPickCreateRow extends StatelessWidget {
  final String label;
  final VoidCallback onTap;

  const QuickPickCreateRow({super.key, required this.label, required this.onTap});

  @override
  Widget build(BuildContext context) {
    final accent = tweetReadableAccentColor(context, background: _raisedFill(context));
    return Padding(
      padding: const EdgeInsets.only(bottom: 8),
      child: TextButton(
        key: kQuickPickCreateKey,
        onPressed: onTap,
        style: TextButton.styleFrom(
          foregroundColor: accent,
          backgroundColor: _raisedFill(context),
          minimumSize: const Size.fromHeight(56),
          padding: const EdgeInsets.all(8),
          alignment: AlignmentDirectional.centerStart,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(14)),
        ),
        child: Row(
          children: [
            Container(
              width: 40,
              height: 40,
              decoration: BoxDecoration(
                borderRadius: BorderRadius.circular(10),
                border: Border.all(color: Theme.of(context).colorScheme.outline, width: 1.5),
              ),
              child: const Icon(Icons.add, size: 20),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: Text(
                label,
                maxLines: 3,
                overflow: TextOverflow.ellipsis,
                style: const TextStyle(fontSize: 15, fontWeight: FontWeight.w600),
              ),
            ),
          ],
        ),
      ),
    );
  }
}

/// A choice as a pill with its [mark] and [label], outlined in the accent with
/// a check while [selected].
class QuickPickChip extends StatelessWidget {
  final Widget mark;
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const QuickPickChip({
    super.key,
    required this.mark,
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final sheet = _sheetColor(context);
    final accent = tweetReadableAccentColor(context, background: sheet);
    return FilterChip(
      selected: selected,
      onSelected: (_) => onTap(),
      showCheckmark: false,
      // The mark rides in the label: a chip's avatar may be no taller than its
      // text, and the mark is.
      label: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          mark,
          const SizedBox(width: 8),
          Flexible(child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis)),
          if (selected) ...[const SizedBox(width: 6), Icon(Icons.check, size: 18, color: accent)],
        ],
      ),
      labelStyle: TextStyle(color: tweetPrimaryColor(context), fontSize: 15, fontWeight: FontWeight.w500),
      backgroundColor: _raisedFill(context),
      selectedColor: Color.alphaBlend(tweetAccentColor(context).withValues(alpha: 0.18), sheet),
      side: BorderSide(color: selected ? accent : Theme.of(context).colorScheme.outline, width: 1.5),
      shape: const StadiumBorder(),
      padding: const EdgeInsetsDirectional.fromSTEB(6, 5, 10, 5),
      labelPadding: EdgeInsets.zero,
    );
  }
}

/// [text], where the choice stands, read out as it changes; the icon is in the
/// accent while [placed].
class QuickPickStatusLine extends StatelessWidget {
  final bool placed;
  final IconData icon;
  final String text;

  const QuickPickStatusLine({super.key, required this.placed, required this.icon, required this.text});

  @override
  Widget build(BuildContext context) {
    final color = placed
        ? tweetReadableAccentColor(context, background: _sheetColor(context))
        : tweetSecondaryColor(context);
    return DecoratedBox(
      decoration: BoxDecoration(
        border: Border(top: BorderSide(color: tweetDividerColor(context))),
      ),
      child: Padding(
        padding: const EdgeInsets.fromLTRB(20, 14, 20, 14),
        child: Semantics(
          liveRegion: true,
          child: Row(
            children: [
              Icon(icon, size: 20, color: color),
              const SizedBox(width: 10),
              Expanded(
                child: Text(
                  text,
                  key: kQuickPickStatusKey,
                  maxLines: 2,
                  overflow: TextOverflow.ellipsis,
                  style: Theme.of(context).textTheme.bodyMedium!.copyWith(color: tweetPrimaryColor(context)),
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }
}
