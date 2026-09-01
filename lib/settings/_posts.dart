import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:provider/provider.dart';
import 'package:quax/constants.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/group/group_model.dart';
import 'package:pref/pref.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';

class SettingsPostsFragment extends StatefulWidget {
  const SettingsPostsFragment({super.key});

  @override
  State<SettingsPostsFragment> createState() => _SettingsPostsFragmentState();
}

class _SettingsPostsFragmentState extends State<SettingsPostsFragment> {
  late final SettingsFeedOverridesStore _viewStore;

  @override
  void initState() {
    super.initState();
    _viewStore = SettingsFeedOverridesStore(context.read<GroupsModel>())
      ..load();
  }

  @override
  void dispose() {
    _viewStore.destroy();
    super.dispose();
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

    await _viewStore.applyDefaults();
  }

  @override
  Widget build(BuildContext context) {
    return SettingsPageScaffold(
      title: L10n.current.tweets,
      body: ScopedBuilder<SettingsFeedOverridesStore, FeedOverrideCounts?>(
        store: _viewStore,
        onState: (_, overrides) {
          final keepingOwnChoice = overrides == null
              ? 0
              : overrides.replies + overrides.retweets;
          return SettingsList(
            children: [
              SettingsSection(
                title: L10n.of(context).tweets,
                children: [
                  PrefSwitch(
                    pref: optionUseAbsoluteTimestamp,
                    title: Text(L10n.of(context).use_absolute_timestamp),
                    subtitle: Text(
                      L10n.of(context).use_absolute_timestamp_description,
                    ),
                  ),
                  PrefCheckbox(
                    title: Text(L10n.of(context).hide_sensitive_tweets),
                    subtitle: Text(
                      L10n.of(
                        context,
                      ).whether_to_hide_tweets_marked_as_sensitive,
                    ),
                    pref: optionTweetsHideSensitive,
                  ),
                  PrefSwitch(
                    title: Text(
                      L10n.of(context).always_show_full_tweet_contents,
                    ),
                    subtitle: Text(
                      L10n.of(
                        context,
                      ).always_show_full_tweet_contents_description,
                    ),
                    pref: alwaysShowFullTweetContents,
                  ),
                  PrefSwitch(
                    title: Text(
                      L10n.of(
                        context,
                      ).activate_non_confirmation_bias_mode_label,
                    ),
                    pref: optionNonConfirmationBiasMode,
                    subtitle: Text(
                      L10n.of(
                        context,
                      ).activate_non_confirmation_bias_mode_description,
                    ),
                  ),
                  PrefSwitch(
                    title: Text(
                      L10n.of(
                        context,
                      ).disable_warnings_for_unrelated_posts_in_feed,
                    ),
                    subtitle: Text(
                      L10n.of(
                        context,
                      ).disable_warnings_for_unrelated_posts_in_feed_description,
                    ),
                    pref: optionDisableWarningsForUnrelatedPostsInFeed,
                  ),
                  PrefSwitch(
                    title: Text(
                      L10n.of(context).show_subscribe_button_on_avatars,
                    ),
                    subtitle: Text(
                      L10n.of(
                        context,
                      ).show_subscribe_button_on_avatars_description,
                    ),
                    pref: optionTweetsShowSubscribeBadge,
                  ),
                ],
              ),
              SettingsSection(
                title: L10n.of(context).default_feed_tab,
                children: [
                  // The two feed defaults. They no longer touch a feed's own choice, so
                  // per-group settings survive changing them.
                  PrefSwitch(
                    title: Text(L10n.of(context).include_replies),
                    subtitle: Text(
                      L10n.of(context).feed_default_filter_description,
                    ),
                    pref: optionGlobalIncludeReplies,
                  ),
                  PrefSwitch(
                    title: Text(L10n.of(context).include_retweets),
                    subtitle: Text(
                      L10n.of(context).feed_default_filter_description,
                    ),
                    pref: optionGlobalIncludeRetweets,
                  ),
                  SettingsRow(
                    icon: Icons.restart_alt_outlined,
                    title: L10n.of(context).feed_defaults_apply_all,
                    description:
                        '${L10n.of(context).feed_defaults_apply_all_description}\n'
                        '${L10n.of(context).feed_defaults_own_choice_count(keepingOwnChoice)}',
                    enabled: keepingOwnChoice > 0,
                    onTap: _applyToAllFeeds,
                    destructive: true,
                  ),
                ],
              ),
              SettingsSection(
                title: L10n.of(context).zen_mode,
                children: [
                  PrefSwitch(
                    title: Text(L10n.of(context).threaded_replies),
                    subtitle: Text(
                      L10n.of(context).threaded_replies_description,
                    ),
                    pref: optionThreadedReplies,
                  ),
                  PrefSwitch(
                    title: Text(L10n.of(context).zen_mode),
                    subtitle: Text(L10n.of(context).zen_mode_description),
                    pref: optionZenMode,
                  ),
                  PrefDropdown(
                    fullWidth: false,
                    title: Text(L10n.of(context).zen_mode_page_cap),
                    subtitle: Text(
                      L10n.of(context).zen_mode_page_cap_description,
                    ),
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
                ],
              ),
            ],
          );
        },
      ),
    );
  }
}
