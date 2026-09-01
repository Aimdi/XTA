import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:quax/constants.dart';
import 'package:quax/database/entities.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/group_custom_settings.dart';
import 'package:quax/group/group_chrome.dart';
import 'package:quax/group/group_model.dart';
import 'package:quax/tweet/tweet_chrome.dart';

int _sortModeOf(SubscriptionGroupGet group) =>
    group.custom ? 2 : (group.popular ? 1 : 0);

String _sortModeLabel(BuildContext context, SubscriptionGroupGet group) {
  switch (_sortModeOf(group)) {
    case 1:
      return L10n.of(context).popular;
    case 2:
      return L10n.of(context).custom;
    default:
      return L10n.of(context).recent;
  }
}

void showFeedSettings(BuildContext context, GroupModel model) {
  showModalBottomSheet(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (context) => FractionallySizedBox(
      heightFactor: 0.82,
      child: SafeArea(
        top: false,
        child: SingleChildScrollView(
          child: Column(
            mainAxisSize: MainAxisSize.min,
            children: [
              _SheetHeader(onClose: () => Navigator.of(context).pop()),
              Padding(
                padding: const EdgeInsets.fromLTRB(
                  kTweetHorizontalPadding,
                  0,
                  kTweetHorizontalPadding,
                  kTweetSpace3,
                ),
                child: Align(
                  alignment: AlignmentDirectional.centerStart,
                  child: Text(
                    L10n.of(
                      context,
                    ).note_due_to_a_twitter_limitation_not_all_tweets_may_be_included,
                    style: tweetMetadataStyle(context),
                  ),
                ),
              ),
              _FeedSettingsContent(model: model),
            ],
          ),
        ),
      ),
    ),
  );
}

class _SheetHeader extends StatelessWidget {
  final VoidCallback onClose;

  const _SheetHeader({required this.onClose});

  @override
  Widget build(BuildContext context) {
    return ListTile(
      title: Text(
        L10n.of(context).filters,
        style: Theme.of(context).textTheme.titleLarge,
      ),
      trailing: IconButton(
        tooltip: L10n.of(context).close,
        icon: const Icon(Icons.close),
        onPressed: onClose,
      ),
    );
  }
}

class _FeedSettingsContent extends StatelessWidget {
  final GroupModel model;

  const _FeedSettingsContent({required this.model});

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<GroupModel, SubscriptionGroupGet>(
      store: model,
      onState: (_, state) {
        final prefs = PrefService.of(context);
        final includeReplies =
            state.includeReplies ??
            prefs.get<bool>(optionGlobalIncludeReplies) ??
            true;
        final includeRetweets =
            state.includeRetweets ??
            prefs.get<bool>(optionGlobalIncludeRetweets) ??
            true;
        return Column(
          children: [
            GroupSettingsSection(
              icon: Icons.tune,
              title: L10n.of(context).filters,
              child: Column(
                children: [
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(L10n.of(context).include_replies),
                    value: includeReplies,
                    onChanged: model.toggleSubscriptionGroupIncludeReplies,
                  ),
                  SwitchListTile(
                    contentPadding: EdgeInsets.zero,
                    title: Text(L10n.of(context).include_retweets),
                    value: includeRetweets,
                    onChanged: model.toggleSubscriptionGroupIncludeRetweets,
                  ),
                ],
              ),
            ),
            _OrderSection(model: model, state: state),
          ],
        );
      },
    );
  }
}

class _OrderSection extends StatelessWidget {
  final GroupModel model;
  final SubscriptionGroupGet state;

  const _OrderSection({required this.model, required this.state});

  @override
  Widget build(BuildContext context) {
    return GroupSettingsSection(
      icon: Icons.sort,
      title: _sortModeLabel(context, state),
      description: L10n.of(context).popular_feed_description,
      child: Column(
        children: [
          _orderTile(context, L10n.of(context).recent, 0),
          _orderTile(context, L10n.of(context).popular, 1),
          _orderTile(context, L10n.of(context).custom, 2, customSettings: true),
        ],
      ),
    );
  }

  Widget _orderTile(
    BuildContext context,
    String title,
    int value, {
    bool customSettings = false,
  }) {
    return RadioListTile<int>(
      contentPadding: EdgeInsets.zero,
      title: Text(title),
      value: value,
      groupValue: _sortModeOf(state),
      onChanged: (_) => value == 2
          ? model.toggleSubscriptionGroupCustom(true)
          : model.toggleSubscriptionGroupPopular(value == 1),
      secondary: customSettings
          ? IconButton(
              tooltip: L10n.of(context).custom,
              icon: const Icon(Icons.tune),
              onPressed: () => _openCustomSettings(context),
            )
          : null,
    );
  }

  Future<void> _openCustomSettings(BuildContext context) async {
    if (!model.state.custom) await model.toggleSubscriptionGroupCustom(true);
    if (!context.mounted) return;
    await Navigator.push(
      context,
      MaterialPageRoute(
        builder: (_) => GroupCustomSettingsScreen(model: model),
      ),
    );
  }
}
