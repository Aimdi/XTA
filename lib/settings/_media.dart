import 'package:flutter/material.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:pref/pref.dart';
import 'package:xta/utils/download_directory.dart';
import 'package:xta/utils/media_quality.dart';
import 'package:xta/settings/settings_chrome.dart';

class SettingsMediaFragment extends StatelessWidget {
  const SettingsMediaFragment({super.key});

  @override
  Widget build(BuildContext context) {
    var prefs = PrefService.of(context);

    List<DropdownMenuItem<String>> qualityItems() => [
      DropdownMenuItem(
        value: MediaQuality.thumb.stored,
        child: Text(L10n.of(context).quality_low),
      ),
      DropdownMenuItem(
        value: MediaQuality.small.stored,
        child: Text(L10n.of(context).quality_medium),
      ),
      DropdownMenuItem(
        value: MediaQuality.medium.stored,
        child: Text(L10n.of(context).quality_high),
      ),
      DropdownMenuItem(
        value: MediaQuality.large.stored,
        child: Text(L10n.of(context).quality_maximum),
      ),
    ];

    return SettingsPageScaffold(
      title: L10n.current.media,
      body: SettingsList(
        children: [
          PrefSwitch(
            pref: optionMediaDisableAutoload,
            title: Text(L10n.of(context).load_media_manually),
            subtitle: Text(L10n.of(context).load_media_manually_description),
          ),
          PrefDropdown(
            fullWidth: false,
            title: Text(L10n.of(context).image_quality),
            subtitle: Text(
              L10n.of(context).save_bandwidth_using_smaller_images,
            ),
            pref: optionImageQuality,
            items: qualityItems(),
          ),
          PrefDropdown(
            fullWidth: false,
            title: Text(L10n.of(context).video_quality),
            subtitle: Text(L10n.of(context).video_quality_description),
            pref: optionMediaVideoQuality,
            items: qualityItems(),
          ),
          PrefDropdown(
            fullWidth: false,
            title: Text(L10n.of(context).media_grid_columns),
            subtitle: Text(L10n.of(context).media_grid_columns_description),
            pref: optionMediaGridColumns,
            items: [
              for (var count in [1, 2, 3, 4, 5])
                DropdownMenuItem(value: count, child: Text('$count')),
            ],
          ),
          ExpansionTile(
            leading: const Icon(Icons.grid_view),
            title: Text(L10n.of(context).media_layout),
            children: [
              PrefRadio<String>(
                title: Text(L10n.of(context).media_layout_masonry),
                value: mediaGridLayoutMasonry,
                pref: optionMediaGridLayout,
              ),
              PrefRadio<String>(
                title: Text(L10n.of(context).media_layout_feed),
                value: mediaGridLayoutFeed,
                pref: optionMediaGridLayout,
              ),
              PrefRadio<String>(
                title: Text(L10n.of(context).media_layout_two_columns),
                value: mediaGridLayoutTwoColumns,
                pref: optionMediaGridLayout,
              ),
            ],
          ),
          PrefSwitch(
            pref: optionMediaDefaultMute,
            title: Text(L10n.of(context).mute_videos),
            subtitle: Text(L10n.of(context).mute_video_description),
          ),
          PrefSwitch(
            pref: optionMediaDefaultLoop,
            title: Text(L10n.of(context).loop_videos),
            subtitle: Text(L10n.of(context).loop_videos_description),
          ),
          PrefSwitch(
            pref: optionMediaDefaultAutoPlay,
            title: Text(L10n.of(context).autoplay_videos),
            subtitle: Text(L10n.of(context).autoplay_videos_description),
          ),
          PrefDropdown(
            fullWidth: false,
            title: Text(L10n.of(context).video_prefetch),
            subtitle: Text(L10n.of(context).video_prefetch_description),
            pref: optionMediaVideoPrefetchSeconds,
            items: [
              DropdownMenuItem(
                value: 0,
                child: Text(L10n.of(context).video_prefetch_unlimited),
              ),
              for (var seconds in [1, 5, 15, 30, 60])
                DropdownMenuItem(
                  value: seconds,
                  child: Text(L10n.of(context).video_prefetch_seconds(seconds)),
                ),
            ],
          ),
          PrefSwitch(
            pref: optionMediaDirectHardwareDecoding,
            title: Text(L10n.of(context).direct_hardware_decoding),
            subtitle: Text(
              L10n.of(context).direct_hardware_decoding_description,
            ),
          ),
          PrefSwitch(
            pref: optionMediaBackgroundPlayback,
            title: Text(L10n.of(context).allow_background_play),
            subtitle: Text(L10n.of(context).allow_background_play_description),
          ),
          PrefSwitch(
            pref: optionMediaAllowBackgroundPlayOtherApps,
            title: Text(L10n.of(context).allow_background_play_other_apps),
            subtitle: Text(
              L10n.of(context).allow_background_play_other_apps_description,
            ),
          ),
          DownloadTypeSetting(prefs: prefs),
        ],
      ),
    );
  }
}

class DownloadTypeSetting extends StatefulWidget {
  final BasePrefService prefs;

  const DownloadTypeSetting({super.key, required this.prefs});

  @override
  DownloadTypeSettingState createState() => DownloadTypeSettingState();
}

class DownloadTypeSettingState extends State<DownloadTypeSetting> {
  @override
  Widget build(BuildContext context) {
    var downloadPath = widget.prefs.get<String>(optionDownloadPath) ?? '';
    var treeUri = widget.prefs.get<String>(optionDownloadTreeUri) ?? '';

    return Column(
      children: [
        PrefDropdown(
          onChange: (value) {
            setState(() {});
          },
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
        if (widget.prefs.get(optionDownloadType) == optionDownloadTypeDirectory)
          PrefButton(
            onTap: () async {
              // The system picker, which also hands over lasting write access.
              // A bare path cannot be written to on Android 11 and later.
              final treeUri = await DownloadDirectory.pick();
              if (treeUri == null) {
                return;
              }
              setState(() {
                widget.prefs.set(optionDownloadTreeUri, treeUri);
                widget.prefs.set(
                  optionDownloadPath,
                  DownloadDirectory.displayName(treeUri),
                );
              });
            },
            title: Text(L10n.current.download_path),
            subtitle: Text(
              treeUri.isEmpty && downloadPath.isEmpty
                  ? L10n.current.not_set
                  : (treeUri.isEmpty
                        // Chosen by an older build, so it cannot be written to
                        // any more; say so instead of failing at save time.
                        ? '$downloadPath — ${L10n.current.download_path_needs_reselect}'
                        : DownloadDirectory.displayName(treeUri)),
            ),
            child: Text(L10n.current.choose),
          ),
      ],
    );
  }
}
