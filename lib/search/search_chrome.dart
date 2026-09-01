import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/search/advanced_search_model.dart';
import 'package:xta/tweet/tweet_chrome.dart';
import 'package:xta/ui/reader_chrome.dart';
import 'package:xta/ui/x_look_theme.dart';

const double kSearchFieldHeight = 48;
const double kSearchLargeTextFieldHeight = 56;
const double kSearchTabsHeight = 52;
const double kSearchFilterStripHeight = 56;
const double kSearchControlRadius = 12;

String advancedFilterLabel(BuildContext context, AdvancedSearchFilter filter) {
  final l10n = L10n.of(context);
  return switch (filter) {
    AdvancedSearchFilter.allWords => l10n.all_of_these_words,
    AdvancedSearchFilter.exactPhrase => l10n.this_exact_phrase,
    AdvancedSearchFilter.anyWords => l10n.any_of_these_words,
    AdvancedSearchFilter.noneWords => l10n.none_of_these_words,
    AdvancedSearchFilter.hashtags => l10n.these_hashtags,
    AdvancedSearchFilter.fromAccounts => l10n.from_these_accounts,
    AdvancedSearchFilter.toAccounts => l10n.to_these_accounts,
    AdvancedSearchFilter.mentioningAccounts => l10n.mentioning_these_accounts,
    AdvancedSearchFilter.minReplies => l10n.minimum_replies,
    AdvancedSearchFilter.minLikes => l10n.minimum_likes,
    AdvancedSearchFilter.minRetweets => l10n.minimum_reposts,
    AdvancedSearchFilter.onlyMedia => l10n.only_show_posts_with_media,
    AdvancedSearchFilter.since => l10n.since_date,
    AdvancedSearchFilter.until => l10n.until_date,
  };
}

class XtaSearchField extends StatelessWidget {
  final TextEditingController controller;
  final FocusNode focusNode;
  final int activeFilterCount;
  final ValueChanged<String> onSubmitted;
  final VoidCallback onClear;
  final VoidCallback onAdvanced;

  const XtaSearchField({
    super.key,
    required this.controller,
    required this.focusNode,
    required this.activeFilterCount,
    required this.onSubmitted,
    required this.onClear,
    required this.onAdvanced,
  });

  @override
  Widget build(BuildContext context) {
    final fieldHeight = MediaQuery.textScalerOf(context).scale(1) >= 1.3
        ? kSearchLargeTextFieldHeight
        : kSearchFieldHeight;
    final tokens = XLookTokens.maybeOf(context);
    final background = tokens == null
        ? Color.alphaBlend(
            tweetPrimaryColor(context).withValues(alpha: 0.06),
            Theme.of(context).scaffoldBackgroundColor,
          )
        : xLookInsetSurface(tokens);
    return SizedBox(
      height: fieldHeight,
      child: SearchBar(
        controller: controller,
        focusNode: focusNode,
        hintText: L10n.of(context).search,
        constraints: BoxConstraints(
          minWidth: 0,
          minHeight: fieldHeight,
          maxHeight: fieldHeight,
        ),
        textInputAction: TextInputAction.search,
        onSubmitted: onSubmitted,
        onTapOutside: (_) => focusNode.unfocus(),
        elevation: const WidgetStatePropertyAll(0),
        backgroundColor: WidgetStatePropertyAll(background),
        side: WidgetStatePropertyAll(
          BorderSide(
            color:
                tokens?.border ?? Theme.of(context).colorScheme.outlineVariant,
          ),
        ),
        shape: WidgetStatePropertyAll(
          RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(fieldHeight / 2),
          ),
        ),
        padding: const WidgetStatePropertyAll(
          EdgeInsetsDirectional.only(start: kTweetSpace3, end: kTweetSpace1),
        ),
        trailing: [
          if (controller.text.isNotEmpty)
            IconButton(
              tooltip: L10n.of(context).group_combine_clear,
              icon: const Icon(Icons.close),
              onPressed: onClear,
            ),
          _AdvancedSearchButton(
            count: activeFilterCount,
            onPressed: onAdvanced,
          ),
        ],
      ),
    );
  }
}

class _AdvancedSearchButton extends StatelessWidget {
  final int count;
  final VoidCallback onPressed;

  const _AdvancedSearchButton({required this.count, required this.onPressed});

  @override
  Widget build(BuildContext context) {
    final icon = Icon(
      Icons.tune,
      color: count > 0
          ? tweetReadableAccentColor(context)
          : tweetSecondaryColor(context),
    );
    return IconButton(
      tooltip: L10n.of(context).advanced_search,
      isSelected: count > 0,
      selectedIcon: Badge(label: Text('$count'), child: icon),
      icon: icon,
      onPressed: onPressed,
    );
  }
}

class SearchResultsTabBar extends ReaderTabBar {
  const SearchResultsTabBar({
    super.key,
    required super.controller,
    required super.tabs,
  }) : super(height: kSearchTabsHeight);
}

class SearchFilterStrip extends StatelessWidget {
  final List<Widget> chips;

  const SearchFilterStrip({super.key, required this.chips});

  @override
  Widget build(BuildContext context) {
    if (chips.isEmpty) return const SizedBox.shrink();
    return SizedBox(
      height: kSearchFilterStripHeight,
      child: ListView.separated(
        padding: const EdgeInsets.symmetric(
          horizontal: kTweetHorizontalPadding,
          vertical: kTweetSpace1,
        ),
        scrollDirection: Axis.horizontal,
        itemCount: chips.length,
        separatorBuilder: (_, _) => const SizedBox(width: kTweetSpace2),
        itemBuilder: (_, index) => chips[index],
      ),
    );
  }
}

class SearchActiveFilterChip extends StatelessWidget {
  final String label;
  final VoidCallback onDeleted;

  const SearchActiveFilterChip({
    super.key,
    required this.label,
    required this.onDeleted,
  });

  @override
  Widget build(BuildContext context) {
    return ConstrainedBox(
      constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
      child: InputChip(
        label: ConstrainedBox(
          constraints: const BoxConstraints(maxWidth: 240),
          child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
        ),
        labelStyle: tweetMetadataStyle(
          context,
        ).copyWith(color: tweetPrimaryColor(context)),
        deleteIconColor: tweetSecondaryColor(context),
        deleteButtonTooltipMessage: L10n.of(context).group_combine_clear,
        onDeleted: onDeleted,
        side: BorderSide(color: tweetDividerColor(context)),
        shape: RoundedRectangleBorder(
          borderRadius: BorderRadius.circular(kSearchControlRadius),
        ),
        backgroundColor: tweetSurfaceColor(context),
      ),
    );
  }
}

class SearchStartState extends StatelessWidget {
  const SearchStartState({super.key});

  @override
  Widget build(BuildContext context) {
    return Center(
      child: Padding(
        padding: const EdgeInsets.all(kTweetSpace6),
        child: Column(
          mainAxisSize: MainAxisSize.min,
          children: [
            Icon(Icons.search, size: 48, color: tweetSecondaryColor(context)),
            const SizedBox(height: kTweetSpace4),
            Text(
              L10n.of(context).search,
              textAlign: TextAlign.center,
              style: tweetBodyStyle(
                context,
              ).copyWith(color: tweetSecondaryColor(context)),
            ),
          ],
        ),
      ),
    );
  }
}

class AdvancedFilterSection extends StatelessWidget {
  final IconData? icon;
  final String title;
  final List<Widget> children;

  const AdvancedFilterSection({
    super.key,
    required this.title,
    required this.children,
    this.icon,
  });

  @override
  Widget build(BuildContext context) {
    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Padding(
          padding: const EdgeInsets.fromLTRB(
            kTweetHorizontalPadding,
            kTweetSpace4,
            kTweetHorizontalPadding,
            kTweetSpace2,
          ),
          child: Row(
            children: [
              if (icon != null) ...[
                Icon(
                  icon,
                  size: kTweetActionIconSize,
                  color: tweetReadableAccentColor(context),
                ),
                const SizedBox(width: kTweetSpace2),
              ],
              Expanded(child: Text(title, style: tweetLabelStyle(context))),
            ],
          ),
        ),
        ...children,
        const SizedBox(height: kTweetSpace2),
        tweetHairlineDivider(context),
      ],
    );
  }
}

class AdvancedSearchField extends StatelessWidget {
  final TextEditingController controller;
  final String label;
  final ValueChanged<String> onChanged;
  final VoidCallback onClear;
  final bool number;

  const AdvancedSearchField({
    super.key,
    required this.controller,
    required this.label,
    required this.onChanged,
    required this.onClear,
    this.number = false,
  });

  @override
  Widget build(BuildContext context) {
    final tokens = XLookTokens.maybeOf(context);
    return Padding(
      padding: const EdgeInsets.fromLTRB(
        kTweetHorizontalPadding,
        kTweetSpace1,
        kTweetHorizontalPadding,
        kTweetSpace2,
      ),
      child: TextField(
        controller: controller,
        onChanged: onChanged,
        keyboardType: number ? TextInputType.number : TextInputType.text,
        inputFormatters: number
            ? [FilteringTextInputFormatter.digitsOnly]
            : null,
        textInputAction: TextInputAction.next,
        decoration: InputDecoration(
          labelText: label,
          filled: true,
          fillColor: tokens == null
              ? Color.alphaBlend(
                  tweetPrimaryColor(context).withValues(alpha: 0.04),
                  Theme.of(context).scaffoldBackgroundColor,
                )
              : xLookInsetSurface(tokens),
          suffixIcon: controller.text.isEmpty
              ? null
              : IconButton(
                  tooltip: L10n.of(context).group_combine_clear,
                  icon: const Icon(Icons.close),
                  onPressed: onClear,
                ),
          border: OutlineInputBorder(
            borderRadius: BorderRadius.circular(kSearchControlRadius),
          ),
        ),
      ),
    );
  }
}

class SearchQueryPreview extends StatelessWidget {
  final String query;

  const SearchQueryPreview({super.key, required this.query});

  @override
  Widget build(BuildContext context) {
    return Container(
      width: double.infinity,
      margin: const EdgeInsets.all(kTweetHorizontalPadding),
      padding: const EdgeInsets.all(kTweetSpace3),
      decoration: BoxDecoration(
        color: tweetSurfaceColor(context),
        border: Border.all(color: tweetDividerColor(context)),
        borderRadius: BorderRadius.circular(kSearchControlRadius),
      ),
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(L10n.of(context).search_term, style: tweetLabelStyle(context)),
          const SizedBox(height: kTweetSpace2),
          SelectableText(
            query.isEmpty ? L10n.of(context).not_set : query,
            style: tweetBodyStyle(context).copyWith(
              color: query.isEmpty
                  ? tweetSecondaryColor(context)
                  : tweetPrimaryColor(context),
            ),
          ),
        ],
      ),
    );
  }
}

class AdvancedSearchDateRow extends StatelessWidget {
  final String label;
  final String value;
  final bool selected;
  final VoidCallback onTap;
  final VoidCallback onClear;

  const AdvancedSearchDateRow({
    super.key,
    required this.label,
    required this.value,
    required this.selected,
    required this.onTap,
    required this.onClear,
  });

  @override
  Widget build(BuildContext context) {
    return Material(
      color: Colors.transparent,
      child: InkWell(
        onTap: onTap,
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
          child: Padding(
            padding: const EdgeInsetsDirectional.fromSTEB(
              kTweetHorizontalPadding,
              kTweetSpace2,
              kTweetSpace1,
              kTweetSpace2,
            ),
            child: Row(
              children: [
                Icon(
                  Icons.calendar_today_outlined,
                  size: kTweetActionIconSize,
                  color: tweetSecondaryColor(context),
                ),
                const SizedBox(width: kTweetSpace3),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(label, style: tweetBodyStyle(context)),
                      const SizedBox(height: kTweetSpace1),
                      Text(value, style: tweetMetadataStyle(context)),
                    ],
                  ),
                ),
                if (selected)
                  IconButton(
                    tooltip: L10n.of(context).group_combine_clear,
                    icon: const Icon(Icons.close),
                    onPressed: onClear,
                  )
                else
                  const SizedBox.square(
                    dimension: kTweetTouchTarget,
                    child: Icon(Icons.chevron_right),
                  ),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

class AdvancedSearchToggleRow extends StatelessWidget {
  final String label;
  final bool value;
  final ValueChanged<bool> onChanged;

  const AdvancedSearchToggleRow({
    super.key,
    required this.label,
    required this.value,
    required this.onChanged,
  });

  @override
  Widget build(BuildContext context) {
    return SwitchListTile.adaptive(
      title: Text(label, style: tweetBodyStyle(context)),
      value: value,
      onChanged: onChanged,
      contentPadding: const EdgeInsets.symmetric(
        horizontal: kTweetHorizontalPadding,
      ),
    );
  }
}

class SearchApplyBar extends StatelessWidget {
  final bool enabled;
  final VoidCallback onPressed;

  const SearchApplyBar({
    super.key,
    required this.enabled,
    required this.onPressed,
  });

  @override
  Widget build(BuildContext context) {
    final background =
        XLookTokens.maybeOf(context)?.background ??
        Theme.of(context).scaffoldBackgroundColor;
    return DecoratedBox(
      decoration: BoxDecoration(
        color: background,
        border: Border(
          top: BorderSide(
            color: tweetDividerColor(context),
            width: kTweetDividerThickness,
          ),
        ),
      ),
      child: SafeArea(
        top: false,
        child: Padding(
          padding: const EdgeInsets.all(kTweetSpace3),
          child: FilledButton.icon(
            style: FilledButton.styleFrom(
              minimumSize: const Size.fromHeight(kTweetTouchTarget),
            ),
            onPressed: enabled ? onPressed : null,
            icon: const Icon(Icons.search),
            label: Text(L10n.of(context).search),
          ),
        ),
      ),
    );
  }
}

class SearchSystemBars extends XtaSystemBars {
  const SearchSystemBars({super.key, required super.child});
}
