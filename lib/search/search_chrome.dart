import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/search/advanced_search_model.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/reader_chrome.dart';
import 'package:quax/ui/x_look_theme.dart';

const double kSearchFieldHeight = 48;
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
    final tokens = XLookTokens.maybeOf(context);
    final background = Color.alphaBlend(
      tweetPrimaryColor(context).withValues(alpha: 0.06),
      tokens?.background ?? Theme.of(context).scaffoldBackgroundColor,
    );
    return SizedBox(
      height: kSearchFieldHeight,
      child: SearchBar(
        controller: controller,
        focusNode: focusNode,
        hintText: L10n.of(context).search,
        constraints: const BoxConstraints(
          minWidth: 0,
          minHeight: kSearchFieldHeight,
          maxHeight: kSearchFieldHeight,
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
            borderRadius: BorderRadius.circular(kSearchFieldHeight / 2),
          ),
        ),
        padding: const WidgetStatePropertyAll(
          EdgeInsetsDirectional.only(start: kTweetSpace3, end: kTweetSpace1),
        ),
        leading: Icon(
          Icons.search,
          size: kTweetActionIconSize,
          color: tweetSecondaryColor(context),
        ),
        trailing: [
          if (controller.text.isNotEmpty)
            IconButton(
              tooltip: L10n.of(context).delete,
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
          ? tweetAccentColor(context)
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
    return InputChip(
      label: ConstrainedBox(
        constraints: const BoxConstraints(maxWidth: 240),
        child: Text(label, maxLines: 1, overflow: TextOverflow.ellipsis),
      ),
      labelStyle: tweetMetadataStyle(
        context,
      ).copyWith(color: tweetPrimaryColor(context)),
      deleteIconColor: tweetSecondaryColor(context),
      onDeleted: onDeleted,
      side: BorderSide(color: tweetDividerColor(context)),
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(kSearchControlRadius),
      ),
      backgroundColor: tweetSurfaceColor(context),
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
  final String title;
  final List<Widget> children;

  const AdvancedFilterSection({
    super.key,
    required this.title,
    required this.children,
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
          child: Text(title, style: Theme.of(context).textTheme.titleMedium),
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
          suffixIcon: controller.text.isEmpty
              ? null
              : IconButton(
                  tooltip: L10n.of(context).delete,
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
          SelectableText(query, style: tweetBodyStyle(context)),
        ],
      ),
    );
  }
}

class SearchSystemBars extends XtaSystemBars {
  const SearchSystemBars({super.key, required super.child});
}
