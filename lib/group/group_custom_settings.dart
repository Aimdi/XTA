import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/custom_feed_rules.dart';
import 'package:quax/group/group_chrome.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/tweet/tweet_chrome.dart';
import 'package:quax/ui/reader_chrome.dart';

/// Full-screen customization for a group's custom feed mode, opened from the
/// filter sheet — a bottom sheet is too cramped for these controls.
class GroupCustomSettingsScreen extends StatelessWidget {
  final GroupModel model;

  const GroupCustomSettingsScreen({super.key, required this.model});

  @override
  Widget build(BuildContext context) {
    return XtaSystemBars(
      child: Scaffold(
        appBar: AppBar(title: Text(L10n.of(context).custom)),
        body: ScopedBuilder<GroupModel, SubscriptionGroupGet>(
          store: model,
          onState: (_, state) => SafeArea(
            top: false,
            child: ListView(
              padding: const EdgeInsets.only(bottom: kTweetSpace6),
              children: [
                _ContentFilterSection(model: model, state: state),
                _EngagementSection(model: model, state: state),
                _MutedKeywordsSection(model: model, state: state),
              ],
            ),
          ),
        ),
      ),
    );
  }
}

/// Shared frame: a flat surface with a hairline border, a titled header and a
/// short explanation — no Material tonal cards.
class _Section extends StatelessWidget {
  final IconData icon;
  final String title;
  final String description;
  final Widget child;

  const _Section({
    required this.icon,
    required this.title,
    required this.description,
    required this.child,
  });

  @override
  Widget build(BuildContext context) {
    return GroupSettingsSection(
      icon: icon,
      title: title,
      description: description,
      child: child,
    );
  }
}

/// SFW only / default / NSFW only.
///
/// The gradient slider this replaces duplicated the buttons underneath it and
/// imported a rainbow no theme asked for; a segmented control says the same
/// thing in the theme's own colours.
class _ContentFilterSection extends StatelessWidget {
  final GroupModel model;
  final SubscriptionGroupGet state;

  const _ContentFilterSection({required this.model, required this.state});

  static const _values = [
    contentFilterSfw,
    contentFilterDefault,
    contentFilterNsfw,
  ];

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final current = _values.contains(state.contentFilter)
        ? state.contentFilter
        : contentFilterDefault;

    return _Section(
      icon: Icons.filter_alt_outlined,
      title: l10n.content_filter,
      description: l10n.content_filter_description,
      child: LayoutBuilder(
        builder: (context, constraints) => SingleChildScrollView(
          scrollDirection: Axis.horizontal,
          child: ConstrainedBox(
            constraints: BoxConstraints(minWidth: constraints.maxWidth),
            child: SegmentedButton<String>(
              showSelectedIcon: false,
              segments: [
                ButtonSegment(
                  value: contentFilterSfw,
                  label: Text(l10n.content_filter_sfw),
                ),
                ButtonSegment(
                  value: contentFilterDefault,
                  label: Text(l10n.content_filter_default),
                ),
                ButtonSegment(
                  value: contentFilterNsfw,
                  label: Text(l10n.content_filter_nsfw),
                ),
              ],
              selected: {current},
              onSelectionChanged: (selection) =>
                  model.setSubscriptionGroupContentFilter(selection.first),
            ),
          ),
        ),
      ),
    );
  }
}

/// Minimum likes and reposts.
class _EngagementSection extends StatelessWidget {
  final GroupModel model;
  final SubscriptionGroupGet state;

  const _EngagementSection({required this.model, required this.state});

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);

    return _Section(
      icon: Icons.trending_up,
      title: l10n.custom_feed_engagement,
      description: l10n.custom_feed_engagement_description,
      child: Column(
        children: [
          _ThresholdRow(
            label: l10n.custom_feed_min_likes,
            value: state.minLikes,
            onChanged: model.setSubscriptionGroupMinLikes,
          ),
          const SizedBox(height: 12),
          _ThresholdRow(
            label: l10n.custom_feed_min_retweets,
            value: state.minRetweets,
            onChanged: model.setSubscriptionGroupMinRetweets,
          ),
        ],
      ),
    );
  }
}

/// One threshold as a row of choices, so it is a single tap rather than typing
/// a number. 0 means the threshold is off.
class _ThresholdRow extends StatelessWidget {
  final String label;
  final int value;
  final ValueChanged<int> onChanged;

  const _ThresholdRow({
    required this.label,
    required this.value,
    required this.onChanged,
  });

  static const choices = [0, 10, 50, 100, 500, 1000];

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final theme = Theme.of(context);

    return Column(
      crossAxisAlignment: CrossAxisAlignment.start,
      children: [
        Text(
          label,
          style: theme.textTheme.bodyMedium?.copyWith(
            color: Theme.of(context).colorScheme.onSurface,
          ),
        ),
        const SizedBox(height: 8),
        Wrap(
          spacing: 8,
          runSpacing: 8,
          children: [
            for (final choice in choices)
              _Pill(
                label: choice == 0
                    ? l10n.custom_feed_threshold_off
                    : '$choice+',
                selected: value == choice,
                onTap: () => onChanged(choice),
              ),
            // A value from an earlier edit that is not one of the presets stays
            // visible and selected rather than silently snapping to another.
            if (!choices.contains(value))
              _Pill(
                label: '$value+',
                selected: true,
                onTap: () => onChanged(value),
              ),
          ],
        ),
      ],
    );
  }
}

class _Pill extends StatelessWidget {
  final String label;
  final bool selected;
  final VoidCallback onTap;

  const _Pill({
    required this.label,
    required this.selected,
    required this.onTap,
  });

  @override
  Widget build(BuildContext context) {
    final accent = Theme.of(context).colorScheme.primary;
    final onSurface = Theme.of(context).colorScheme.onSurface;

    return Material(
      color: selected ? accent : Colors.transparent,
      shape: StadiumBorder(
        side: BorderSide(
          color: selected
              ? accent
              : Theme.of(context).colorScheme.outlineVariant,
        ),
      ),
      child: InkWell(
        onTap: onTap,
        customBorder: const StadiumBorder(),
        child: ConstrainedBox(
          constraints: const BoxConstraints(minHeight: kTweetTouchTarget),
          child: Padding(
            padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
            child: Center(
              child: Text(
                label,
                style: TextStyle(
                  fontSize: 13,
                  fontWeight: selected ? FontWeight.w700 : FontWeight.w500,
                  color: selected
                      ? (accent.computeLuminance() > 0.5
                            ? Colors.black
                            : Colors.white)
                      : onSurface,
                ),
              ),
            ),
          ),
        ),
      ),
    );
  }
}

/// Muted keywords for this feed.
class _MutedKeywordsSection extends StatefulWidget {
  final GroupModel model;
  final SubscriptionGroupGet state;

  const _MutedKeywordsSection({required this.model, required this.state});

  @override
  State<_MutedKeywordsSection> createState() => _MutedKeywordsSectionState();
}

class _MutedKeywordsSectionState extends State<_MutedKeywordsSection> {
  final TextEditingController _controller = TextEditingController();

  @override
  void dispose() {
    _controller.dispose();
    super.dispose();
  }

  Future<void> _add() async {
    final terms = parseMutedKeywords(_controller.text);
    if (terms.isEmpty) {
      return;
    }

    final existing = widget.state.mutedKeywords
        .map((e) => e.toLowerCase())
        .toSet();
    final next = [
      ...widget.state.mutedKeywords,
      ...terms.where((term) => !existing.contains(term.toLowerCase())),
    ];

    _controller.clear();
    await widget.model.setSubscriptionGroupMutedKeywords(next);
  }

  Future<void> _remove(String term) async {
    final next = widget.state.mutedKeywords
        .where((e) => e != term)
        .toList(growable: false);
    await widget.model.setSubscriptionGroupMutedKeywords(next);
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final keywords = widget.state.mutedKeywords;

    return _Section(
      icon: Icons.speaker_notes_off_outlined,
      title: l10n.custom_feed_muted_keywords,
      description: l10n.custom_feed_muted_keywords_description,
      child: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Expanded(
                child: TextField(
                  controller: _controller,
                  textInputAction: TextInputAction.done,
                  autocorrect: false,
                  style: TextStyle(
                    color: Theme.of(context).colorScheme.onSurface,
                    fontSize: 15,
                  ),
                  decoration: InputDecoration(
                    isDense: true,
                    hintText: l10n.custom_feed_muted_keywords_hint,
                    hintStyle: TextStyle(
                      color: Theme.of(context).colorScheme.onSurfaceVariant,
                      fontSize: 14,
                    ),
                    border: const UnderlineInputBorder(),
                  ),
                  onSubmitted: (_) => _add(),
                ),
              ),
              const SizedBox(width: 8),
              IconButton(
                tooltip: l10n.custom_feed_muted_keywords_hint,
                icon: const Icon(Icons.add),
                color: Theme.of(context).colorScheme.primary,
                onPressed: _add,
              ),
            ],
          ),
          if (keywords.isNotEmpty) ...[
            const SizedBox(height: 12),
            Wrap(
              spacing: 8,
              runSpacing: 8,
              children: [
                for (final term in keywords)
                  InputChip(
                    label: Text(term),
                    onDeleted: () => _remove(term),
                    deleteIcon: const Icon(Icons.close, size: 16),
                  ),
              ],
            ),
          ],
        ],
      ),
    );
  }
}
