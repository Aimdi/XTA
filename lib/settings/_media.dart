import 'package:flutter/material.dart';
import 'package:flutter_triple/flutter_triple.dart';
import 'package:pref/pref.dart';
import 'package:quax/constants.dart';
import 'package:quax/generated/l10n.dart';
import 'package:quax/settings/settings_chrome.dart';
import 'package:quax/settings/settings_view_store.dart';
import 'package:quax/utils/download_directory.dart';

class SettingsMediaFragment extends StatelessWidget {
  const SettingsMediaFragment({super.key});

  @override
  Widget build(BuildContext context) {
    final prefs = PrefService.of(context);
    final l10n = L10n.of(context);
    final qualityItems = [
      DropdownMenuItem(value: 'thumb', child: Text(l10n.quality_low)),
      DropdownMenuItem(value: 'small', child: Text(l10n.quality_medium)),
      DropdownMenuItem(value: 'medium', child: Text(l10n.quality_high)),
      DropdownMenuItem(value: 'large', child: Text(l10n.quality_maximum)),
    ];

    return SettingsPageScaffold(
      title: l10n.media,
      body: SettingsList(
        children: [
          SettingsSection(
            title: l10n.image_quality,
            children: [
              PrefSwitch(
                pref: optionMediaDisableAutoload,
                title: Text(l10n.load_media_manually),
                subtitle: Text(l10n.load_media_manually_description),
              ),
              PrefDropdown<String>(
                fullWidth: false,
                title: Text(l10n.image_quality),
                subtitle: Text(l10n.save_bandwidth_using_smaller_images),
                pref: optionImageQuality,
                items: qualityItems,
              ),
              PrefDropdown<String>(
                fullWidth: false,
                title: Text(l10n.video_quality),
                subtitle: Text(l10n.video_quality_description),
                pref: optionMediaVideoQuality,
                items: qualityItems,
              ),
              PrefDropdown<int>(
                fullWidth: false,
                title: Text(l10n.media_grid_columns),
                subtitle: Text(l10n.media_grid_columns_description),
                pref: optionMediaGridColumns,
                items: [
                  for (var count in [1, 2, 3, 4, 5])
                    DropdownMenuItem(value: count, child: Text('$count')),
                ],
              ),
            ],
          ),
          SettingsSection(
            title: l10n.media_layout,
            children: [
              SettingsPreferenceSelector<String>(
                prefs: prefs,
                pref: optionMediaGridLayout,
                options: [
                  SettingsOption(
                    value: mediaGridLayoutMasonry,
                    label: l10n.media_layout_masonry,
                    icon: Icons.dashboard_outlined,
                  ),
                  SettingsOption(
                    value: mediaGridLayoutFeed,
                    label: l10n.media_layout_feed,
                    icon: Icons.view_agenda_outlined,
                  ),
                  SettingsOption(
                    value: mediaGridLayoutTwoColumns,
                    label: l10n.media_layout_two_columns,
                    icon: Icons.grid_view_outlined,
                  ),
                ],
              ),
            ],
          ),
          SettingsSection(
            title: l10n.video_quality,
            children: [
              PrefSwitch(
                pref: optionMediaDefaultMute,
                title: Text(l10n.mute_videos),
                subtitle: Text(l10n.mute_video_description),
              ),
              PrefSwitch(
                pref: optionMediaDefaultLoop,
                title: Text(l10n.loop_videos),
                subtitle: Text(l10n.loop_videos_description),
              ),
              PrefSwitch(
                pref: optionMediaDefaultAutoPlay,
                title: Text(l10n.autoplay_videos),
                subtitle: Text(l10n.autoplay_videos_description),
              ),
              PrefDropdown<int>(
                fullWidth: false,
                title: Text(l10n.video_prefetch),
                subtitle: Text(l10n.video_prefetch_description),
                pref: optionMediaVideoPrefetchSeconds,
                items: [
                  DropdownMenuItem(
                    value: 0,
                    child: Text(l10n.video_prefetch_unlimited),
                  ),
                  for (var seconds in [1, 5, 15, 30, 60])
                    DropdownMenuItem(
                      value: seconds,
                      child: Text(l10n.video_prefetch_seconds(seconds)),
                    ),
                ],
              ),
              PrefSwitch(
                pref: optionMediaBackgroundPlayback,
                title: Text(l10n.allow_background_play),
                subtitle: Text(l10n.allow_background_play_description),
              ),
              PrefSwitch(
                pref: optionMediaAllowBackgroundPlayOtherApps,
                title: Text(l10n.allow_background_play_other_apps),
                subtitle: Text(
                  l10n.allow_background_play_other_apps_description,
                ),
              ),
            ],
          ),
          SettingsSection(
            title: l10n.download_handling,
            children: [DownloadTypeSetting(prefs: prefs)],
          ),
        ],
      ),
    );
  }
}

class DownloadTypeSetting extends StatefulWidget {
  final BasePrefService prefs;

  const DownloadTypeSetting({super.key, required this.prefs});

  @override
  State<DownloadTypeSetting> createState() => DownloadTypeSettingState();
}

class DownloadTypeSettingState extends State<DownloadTypeSetting> {
  late final SettingsRevisionStore _viewStore;

  @override
  void initState() {
    super.initState();
    _viewStore = SettingsRevisionStore();
  }

  @override
  void dispose() {
    _viewStore.destroy();
    super.dispose();
  }

  Future<void> _pickDirectory() async {
    final treeUri = await DownloadDirectory.pick();
    if (treeUri == null) return;
    await widget.prefs.set(optionDownloadTreeUri, treeUri);
    await widget.prefs.set(
      optionDownloadPath,
      DownloadDirectory.displayName(treeUri),
    );
    if (!mounted) return;
    _viewStore.refresh();
  }

  @override
  Widget build(BuildContext context) {
    return ScopedBuilder<SettingsRevisionStore, int>(
      store: _viewStore,
      onState: (_, __) {
        final downloadPath = widget.prefs.get<String>(optionDownloadPath) ?? '';
        final treeUri = widget.prefs.get<String>(optionDownloadTreeUri) ?? '';
        return Column(
          children: [
            PrefDropdown<String>(
              onChange: (_) => _viewStore.refresh(),
              fullWidth: false,
              title: Text(L10n.current.download_handling),
              subtitle: Text(L10n.current.download_handling_description),
              pref: optionDownloadType,
              items: [
                DropdownMenuItem(
                  value: optionDownloadTypeAsk,
                  child: Text(L10n.current.download_handling_type_ask),
                ),
                DropdownMenuItem(
                  value: optionDownloadTypeDirectory,
                  child: Text(L10n.current.download_handling_type_directory),
                ),
              ],
            ),
            if (widget.prefs.get(optionDownloadType) ==
                optionDownloadTypeDirectory)
              PrefButton(
                onTap: _pickDirectory,
                title: Text(L10n.current.download_path),
                subtitle: Text(
                  treeUri.isEmpty && downloadPath.isEmpty
                      ? L10n.current.not_set
                      : treeUri.isEmpty
                      ? '$downloadPath — ${L10n.current.download_path_needs_reselect}'
                      : DownloadDirectory.displayName(treeUri),
                ),
                child: Text(L10n.current.choose),
              ),
          ],
        );
      },
    );
  }
}
