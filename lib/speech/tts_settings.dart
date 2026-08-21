import 'package:flutter/material.dart';
import 'package:flutter_tts/flutter_tts.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/speech/tts_engines.dart';
import 'package:xta/utils/urls.dart';

/// Which speech engine and voice reading aloud uses.
///
/// Android lets more than one engine be installed, and an app that never asks
/// gets whichever one is the system default. A reader who installed a engine of
/// their own — Sherpa, RHVoice, anything — had no way to tell XTA to use it
/// short of changing the system-wide default, which is why reading aloud
/// appeared to do nothing.
class TtsChoice {
  /// Engine package name, e.g. `com.k2fsa.sherpa.onnx.tts.engine`.
  final String? engine;

  /// Voice name and its locale, which the platform wants together.
  final String? voiceName;
  final String? voiceLocale;

  final double rate;

  const TtsChoice({
    this.engine,
    this.voiceName,
    this.voiceLocale,
    this.rate = 0.45,
  });

  bool get hasVoice => voiceName != null && voiceLocale != null;
}

TtsChoice readTtsChoice(BasePrefService prefs) => TtsChoice(
  engine: _orNull(prefs.get<String>(optionTtsEngine)),
  voiceName: _orNull(prefs.get<String>(optionTtsVoiceName)),
  voiceLocale: _orNull(prefs.get<String>(optionTtsVoiceLocale)),
  rate: prefs.get<double>(optionTtsRate) ?? 0.45,
);

String? _orNull(String? value) => value == null || value.isEmpty ? null : value;

/// Stores Sherpa as the engine and clears a voice that belonged to another one.
Future<void> preferSherpaTts(BasePrefService prefs) async {
  await prefs.set(optionTtsEngine, sherpaOnnxTtsEngine);
  await prefs.set(optionTtsVoiceName, '');
  await prefs.set(optionTtsVoiceLocale, '');
}

/// Applies [choice] to [tts]. Returns false when the chosen engine is gone —
/// uninstalled since, say — so the caller can fall back rather than sit mute.
Future<bool> applyTtsChoice(FlutterTts tts, TtsChoice choice) async {
  try {
    final engine = choice.engine;
    if (engine != null) {
      await tts.setEngine(engine);
    }
    if (choice.hasVoice) {
      await tts.setVoice({
        'name': choice.voiceName!,
        'locale': choice.voiceLocale!,
      });
    }
    await tts.setSpeechRate(choice.rate);
    return true;
  } catch (_) {
    return false;
  }
}

/// Everything the platform will tell us about what can speak.
typedef TtsOptions = ({
  List<String> engines,
  List<({String name, String locale})> voices,
});

Future<TtsOptions> loadTtsOptions(FlutterTts tts) async {
  final engines = <String>[];
  final voices = <({String name, String locale})>[];

  try {
    final raw = await tts.getEngines;
    if (raw is List) {
      engines.addAll(raw.whereType<String>());
    }
  } catch (_) {
    // Not every platform has engines to list; voices may still work.
  }

  try {
    final raw = await tts.getVoices;
    if (raw is List) {
      for (final entry in raw) {
        if (entry is Map) {
          final name = entry['name'];
          final locale = entry['locale'];
          if (name is String && locale is String) {
            voices.add((name: name, locale: locale));
          }
        }
      }
    }
  } catch (_) {
    // Same again: an engine that lists nothing can still speak its default.
  }

  voices.sort((a, b) => a.locale.compareTo(b.locale));
  return (engines: engines, voices: voices);
}

/// Picks the engine, the voice and the speed, and stores them.
///
/// Returns true when something changed, so the caller knows to re-apply.
Future<bool> openTtsSettings(BuildContext context, FlutterTts tts) async {
  final options = await loadTtsOptions(tts);
  if (!context.mounted) {
    return false;
  }

  final changed = await showModalBottomSheet<bool>(
    context: context,
    isScrollControlled: true,
    useSafeArea: true,
    showDragHandle: true,
    builder: (sheetContext) => FractionallySizedBox(
      heightFactor: 0.8,
      child: _TtsSettingsSheet(options: options, tts: tts),
    ),
  );

  return changed ?? false;
}

/// Settings hub page — same controls as the reader sheet, with an app bar.
class TtsSettingsScreen extends StatefulWidget {
  const TtsSettingsScreen({super.key, this.tts});

  final FlutterTts? tts;

  @override
  State<TtsSettingsScreen> createState() => _TtsSettingsScreenState();
}

class _TtsSettingsScreenState extends State<TtsSettingsScreen> {
  late final FlutterTts _tts = widget.tts ?? FlutterTts();
  TtsOptions? _options;

  @override
  void initState() {
    super.initState();
    _load();
  }

  Future<void> _load() async {
    final options = await loadTtsOptions(_tts);
    if (!mounted) return;
    setState(() => _options = options);
  }

  @override
  Widget build(BuildContext context) {
    final options = _options;
    return Scaffold(
      appBar: AppBar(title: Text(L10n.of(context).settings_speech)),
      body: options == null
          ? const Center(child: CircularProgressIndicator())
          : _TtsSettingsSheet(options: options, tts: _tts, asPage: true),
    );
  }
}

class _TtsSettingsSheet extends StatefulWidget {
  final TtsOptions options;
  final FlutterTts tts;
  final bool asPage;

  const _TtsSettingsSheet({
    required this.options,
    required this.tts,
    this.asPage = false,
  });

  @override
  State<_TtsSettingsSheet> createState() => _TtsSettingsSheetState();
}

class _TtsSettingsSheetState extends State<_TtsSettingsSheet> {
  late TtsChoice _choice;
  var _changed = false;

  /// Voices of the engine currently chosen. Switching engine re-reads them:
  /// the list the platform hands back is whatever engine is loaded.
  late List<({String name, String locale})> _voices = widget.options.voices;

  String? get _engineGroup =>
      isSherpaEngine(_choice.engine) ? sherpaOnnxTtsEngine : _choice.engine;

  @override
  void initState() {
    super.initState();
    _choice = readTtsChoice(PrefService.of(context, listen: false));
  }

  Future<void> _setEngine(String? engine) async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionTtsEngine, engine ?? '');
    // A voice belongs to the engine that offered it.
    await prefs.set(optionTtsVoiceName, '');
    await prefs.set(optionTtsVoiceLocale, '');

    if (engine != null) {
      try {
        await widget.tts.setEngine(engine);
      } catch (_) {
        // Reported by the list simply not changing.
      }
    }

    final refreshed = await loadTtsOptions(widget.tts);
    if (!mounted) return;
    setState(() {
      _choice = readTtsChoice(prefs);
      _voices = refreshed.voices;
      _changed = true;
    });
  }

  Future<void> _pickSherpa() =>
      _setEngine(resolveSherpaEngineId(widget.options.engines));

  Future<void> _setVoice(({String name, String locale})? voice) async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionTtsVoiceName, voice?.name ?? '');
    await prefs.set(optionTtsVoiceLocale, voice?.locale ?? '');
    if (!mounted) return;
    setState(() {
      _choice = readTtsChoice(prefs);
      _changed = true;
    });
  }

  Future<void> _setRate(double rate) async {
    final prefs = PrefService.of(context, listen: false);
    await prefs.set(optionTtsRate, rate);
    if (!mounted) return;
    setState(() {
      _choice = readTtsChoice(prefs);
      _changed = true;
    });
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    final engines = widget.options.engines;

    return Column(
      children: [
        if (!widget.asPage)
          Padding(
            padding: const EdgeInsets.only(bottom: 8),
            child: Text(
              l10n.settings_speech,
              style: Theme.of(context).textTheme.titleLarge,
            ),
          ),
        Expanded(
          child: ListView(
            children: [
              _header(context, l10n.plugin_substack_tts_engine),
              RadioListTile<String?>(
                value: null,
                groupValue: _engineGroup,
                title: Text(l10n.plugin_substack_tts_default),
                onChanged: _setEngine,
              ),
              RadioListTile<String?>(
                value: sherpaOnnxTtsEngine,
                groupValue: _engineGroup,
                title: Text(l10n.tts_engine_sherpa),
                subtitle: Text(l10n.tts_engine_sherpa_description),
                onChanged: (_) => _pickSherpa(),
              ),
              if (!sherpaIsInstalled(engines))
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 8),
                  child: Align(
                    alignment: Alignment.centerLeft,
                    child: TextButton.icon(
                      onPressed: () => openUri(context, sherpaTtsInstallUrl),
                      icon: const Icon(Icons.download_outlined),
                      label: Text(l10n.tts_sherpa_install),
                    ),
                  ),
                ),
              for (final engine in nonSherpaEngines(engines))
                RadioListTile<String?>(
                  value: engine,
                  groupValue: _engineGroup,
                  title: Text(engine),
                  onChanged: _setEngine,
                ),
              if (_voices.isNotEmpty) ...[
                _header(context, l10n.plugin_substack_tts_voice),
                RadioListTile<String?>(
                  value: null,
                  groupValue: _choice.voiceName,
                  title: Text(l10n.plugin_substack_tts_default),
                  onChanged: (_) => _setVoice(null),
                ),
                for (final voice in _voices)
                  RadioListTile<String?>(
                    value: voice.name,
                    groupValue: _choice.voiceName,
                    title: Text(voice.name),
                    subtitle: Text(voice.locale),
                    onChanged: (_) => _setVoice(voice),
                  ),
              ],
              _header(context, l10n.plugin_substack_tts_speed),
              Slider(
                value: _choice.rate,
                min: 0.2,
                max: 1.0,
                divisions: 8,
                label: _choice.rate.toStringAsFixed(2),
                onChanged: (value) => setState(
                  () => _choice = TtsChoice(
                    engine: _choice.engine,
                    voiceName: _choice.voiceName,
                    voiceLocale: _choice.voiceLocale,
                    rate: value,
                  ),
                ),
                onChangeEnd: _setRate,
              ),
            ],
          ),
        ),
        if (!widget.asPage)
          Padding(
            padding: const EdgeInsets.all(8),
            child: Align(
              alignment: Alignment.centerRight,
              child: TextButton(
                onPressed: () => Navigator.pop(context, _changed),
                child: Text(l10n.ok),
              ),
            ),
          ),
      ],
    );
  }

  Widget _header(BuildContext context, String text) => Padding(
    padding: const EdgeInsets.fromLTRB(16, 16, 16, 4),
    child: Text(text, style: Theme.of(context).textTheme.labelLarge),
  );
}
