import 'package:flutter/material.dart';
import 'package:provider/provider.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/group/group_model.dart';
import 'package:xta/group/language_filter.dart';
import 'package:pref/pref.dart';
import 'package:xta/settings/settings_chrome.dart';

class SettingsPostsFragment extends StatefulWidget {
  const SettingsPostsFragment({super.key});

  @override
  State<SettingsPostsFragment> createState() => _SettingsPostsFragmentState();
}

class _SettingsPostsFragmentState extends State<SettingsPostsFragment> {
  ({int replies, int retweets})? _overrides;
  late final TextEditingController _languageController;
  late final FocusNode _languageFocus;
  LanguageFilterAction _languageAction = LanguageFilterAction.off;

  bool get _languageFilterActive =>
      parseFeedLanguages(_languageController.text).isNotEmpty;

  @override
  void initState() {
    super.initState();
    final prefs = PrefService.of(context, listen: false);
    _languageController = TextEditingController(
      text: prefs.get(optionFeedLanguages) as String? ?? '',
    );
    _languageFocus = FocusNode()..addListener(_onLanguageFocusChange);
    _languageAction = parseLanguageFilterAction(
      prefs.get(optionFeedLanguageAction) as String?,
    );
    _loadOverrides();
  }

  @override
  void dispose() {
    _languageFocus.dispose();
    _languageController.dispose();
    super.dispose();
  }

  void _onLanguageFocusChange() {
    if (!_languageFocus.hasFocus && mounted) {
      _commitLanguages();
    }
  }

  Future<void> _commitLanguages() async {
    if (!mounted) {
      return;
    }
    final trimmed = _languageController.text.trim();
    if (_languageController.text != trimmed) {
      _languageController.text = trimmed;
      _languageController.selection = TextSelection.collapsed(
        offset: trimmed.length,
      );
    }

    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionFeedLanguages, trimmed);

    if (trimmed.isEmpty && _languageAction != LanguageFilterAction.off) {
      setState(() => _languageAction = LanguageFilterAction.off);
      await prefs.set(
        optionFeedLanguageAction,
        LanguageFilterAction.off.stored,
      );
    } else if (mounted) {
      setState(() {});
    }
  }

  Future<void> _saveLanguageAction(LanguageFilterAction action) async {
    if (!_languageFilterActive && action != LanguageFilterAction.off) {
      return;
    }
    setState(() => _languageAction = action);
    await PrefService.of(
      context,
      listen: false,
    ).set(optionFeedLanguageAction, action.stored);
  }

  Future<void> _loadOverrides() async {
    final counts = await context.read<GroupsModel>().countIncludeOverrides();
    if (mounted) {
      setState(() => _overrides = counts);
    }
  }

  /// Pushes the two defaults onto every feed, discarding each feed's own
  /// choice. Destructive, so it asks first.
  Future<void> _applyToAllFeeds() async {
    final l10n = L10n.of(context);
    final confirmed = await showDialog<bool>(
      context: context,
      builder: (dialogContext) => AlertDialog(
        title: Text(l10n.are_you_sure),
        content: Text(l10n.feed_defaults_apply_all_description),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, false),
            child: Text(l10n.no),
          ),
          TextButton(
            onPressed: () => Navigator.pop(dialogContext, true),
            child: Text(l10n.yes),
          ),
        ],
      ),
    );

    if (confirmed != true || !mounted) {
      return;
    }

    final model = context.read<GroupsModel>();
    await model.clearIncludeOverrides(replies: true);
    await model.clearIncludeOverrides(replies: false);
    await _loadOverrides();
  }

  @override
  Widget build(BuildContext context) {
    final overrides = _overrides;
    final keepingOwnChoice = overrides == null
        ? 0
        : (overrides.replies + overrides.retweets);

    return SettingsPageScaffold(
      title: L10n.current.tweets,
      body: SettingsList(
        children: [
          PrefSwitch(
            pref: optionUseAbsoluteTimestamp,
            title: Text(L10n.of(context).use_absolute_timestamp),
            subtitle: Text(L10n.of(context).use_absolute_timestamp_description),
          ),
          PrefCheckbox(
            title: Text(L10n.of(context).hide_sensitive_tweets),
            subtitle: Text(
              L10n.of(context).whether_to_hide_tweets_marked_as_sensitive,
            ),
            pref: optionTweetsHideSensitive,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).sensitive_media_always_show),
            pref: optionAlwaysShowSensitiveMedia,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).always_show_full_tweet_contents),
            subtitle: Text(
              L10n.of(context).always_show_full_tweet_contents_description,
            ),
            pref: alwaysShowFullTweetContents,
          ),
          PrefSwitch(
            title: Text(
              L10n.of(context).activate_non_confirmation_bias_mode_label,
            ),
            pref: optionNonConfirmationBiasMode,
            subtitle: Text(
              L10n.of(context).activate_non_confirmation_bias_mode_description,
            ),
          ),
          PrefSwitch(
            title: Text(
              L10n.of(context).disable_warnings_for_unrelated_posts_in_feed,
            ),
            subtitle: Text(
              L10n.of(
                context,
              ).disable_warnings_for_unrelated_posts_in_feed_description,
            ),
            pref: optionDisableWarningsForUnrelatedPostsInFeed,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).show_subscribe_button_on_avatars),
            subtitle: Text(
              L10n.of(context).show_subscribe_button_on_avatars_description,
            ),
            pref: optionTweetsShowSubscribeBadge,
          ),
          // The two feed defaults. They no longer touch a feed's own choice, so
          // per-group settings survive changing them.
          PrefSwitch(
            title: Text(L10n.of(context).include_replies),
            subtitle: Text(L10n.of(context).feed_default_filter_description),
            pref: optionGlobalIncludeReplies,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).include_retweets),
            subtitle: Text(L10n.of(context).feed_default_filter_description),
            pref: optionGlobalIncludeRetweets,
          ),
          ListTile(
            title: Text(L10n.of(context).feed_defaults_apply_all),
            subtitle: Text(
              '${L10n.of(context).feed_defaults_apply_all_description}\n'
              '${L10n.of(context).feed_defaults_own_choice_count(keepingOwnChoice)}',
            ),
            isThreeLine: true,
            enabled: keepingOwnChoice > 0,
            onTap: _applyToAllFeeds,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).threaded_replies),
            subtitle: Text(L10n.of(context).threaded_replies_description),
            pref: optionThreadedReplies,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).collapse_boosts),
            subtitle: Text(L10n.of(context).collapse_boosts_description),
            pref: optionFeedCollapseBoosts,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).zen_mode),
            subtitle: Text(L10n.of(context).zen_mode_description),
            pref: optionZenMode,
          ),
          PrefSwitch(
            title: Text(L10n.of(context).calm_mode),
            subtitle: Text(L10n.of(context).calm_mode_description),
            pref: optionCalmMode,
          ),
          PrefDropdown(
            fullWidth: false,
            title: Text(L10n.of(context).zen_mode_page_cap),
            subtitle: Text(L10n.of(context).zen_mode_page_cap_description),
            pref: optionZenModePageCap,
            items: [
              for (final pages in zenModePageCapChoices)
                DropdownMenuItem(value: pages, child: Text('$pages')),
            ],
          ),
          PrefSwitch(
            title: Text(L10n.of(context).remember_reading_position),
            subtitle: Text(
              L10n.of(context).remember_reading_position_description,
            ),
            pref: optionFeedReadingPosition,
          ),
          ListTile(
            title: Text(L10n.of(context).language_filter),
            subtitle: Text(L10n.of(context).language_filter_description),
            isThreeLine: true,
          ),
          Padding(
            padding: const EdgeInsets.symmetric(horizontal: 16),
            child: TextField(
              controller: _languageController,
              focusNode: _languageFocus,
              decoration: InputDecoration(
                labelText: L10n.of(context).language_filter_languages,
                border: const OutlineInputBorder(),
                isDense: true,
              ),
              onEditingComplete: _commitLanguages,
              onSubmitted: (_) => _commitLanguages(),
            ),
          ),
          Padding(
            padding: const EdgeInsets.fromLTRB(16, 8, 16, 8),
            child: SegmentedButton<LanguageFilterAction>(
              segments: [
                ButtonSegment(
                  value: LanguageFilterAction.off,
                  label: Text(L10n.of(context).language_filter_off),
                ),
                ButtonSegment(
                  value: LanguageFilterAction.hide,
                  enabled: _languageFilterActive,
                  label: Text(L10n.of(context).language_filter_hide),
                ),
                ButtonSegment(
                  value: LanguageFilterAction.fold,
                  enabled: _languageFilterActive,
                  label: Text(L10n.of(context).language_filter_fold),
                ),
              ],
              selected: {_languageAction},
              onSelectionChanged: (value) => _saveLanguageAction(value.first),
            ),
          ),
          ListTile(
            title: Text(L10n.of(context).language_filter_action),
            subtitle: Text(switch (_languageAction) {
              LanguageFilterAction.hide => L10n.of(
                context,
              ).language_filter_hide,
              LanguageFilterAction.fold => L10n.of(
                context,
              ).language_filter_fold,
              LanguageFilterAction.off => L10n.of(context).language_filter_off,
            }),
          ),
        ],
      ),
    );
  }
}
