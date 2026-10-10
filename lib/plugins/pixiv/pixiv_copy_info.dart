import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:pref/pref.dart';
import 'package:xta/constants.dart';
import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/pixiv/pixiv_models.dart';
import 'package:xta/ui/errors.dart';

/// What a copy template can say about a work, each written `{name}`.
const pixivCopyPlaceholders = ['{title}', '{illust_id}', '{user_id}', '{user_name}', '{tags}'];

/// Links built from the placeholders, offered as inserts in the editor.
const pixivCopyArtworkUrl = 'https://www.pixiv.net/artworks/{illust_id}';
const pixivCopyUserUrl = 'https://www.pixiv.net/users/{user_id}';

final _placeholder = RegExp(r'\{(title|illust_id|user_id|user_name|tags)\}');

/// The template a reader starts from: title, artist and work ID, worded in
/// the app's language.
String pixivDefaultCopyTemplate(L10n l10n) =>
    l10n.plugin_pixiv_copy_template_default('{title}', '{user_name}', '{illust_id}');

/// The reader's template, or the default while they have not written one.
String pixivCopyTemplate(BasePrefService prefs, L10n l10n) {
  final stored = prefs.get<String>(optionPluginPixivCopyTemplate) ?? '';
  return stored.trim().isEmpty ? pixivDefaultCopyTemplate(l10n) : stored;
}

/// [template] with every placeholder replaced by [illust]'s value, in one
/// pass, so a title that itself contains `{user_name}` stays as written.
String pixivCopyInfo(String template, PixivIllust illust) {
  final values = {
    'title': illust.title,
    'illust_id': '${illust.id}',
    'user_id': '${illust.userId}',
    'user_name': illust.userName,
    'tags': [for (final tag in illust.tags) '#${tag.name}'].join(' '),
  };
  return template.replaceAllMapped(_placeholder, (match) => values[match.group(1)] ?? match.group(0)!);
}

/// Copies [illust]'s info as the reader's template words it.
Future<void> copyPixivInfo(BuildContext context, PixivIllust illust) async {
  final l10n = L10n.of(context);
  final text = pixivCopyInfo(pixivCopyTemplate(PrefService.of(context, listen: false), l10n), illust);
  await Clipboard.setData(ClipboardData(text: text));
  if (context.mounted) showSnackBar(context, icon: '📋', message: l10n.plugin_pixiv_info_copied);
}

/// Edits the copy template: insert chips for each placeholder and link, and
/// a reset to the default. Every change is saved as it is typed.
class PixivCopyTemplateScreen extends StatefulWidget {
  const PixivCopyTemplateScreen({super.key});

  @override
  State<PixivCopyTemplateScreen> createState() => _PixivCopyTemplateScreenState();
}

class _PixivCopyTemplateScreenState extends State<PixivCopyTemplateScreen> {
  late final TextEditingController _text;

  BasePrefService get _prefs => PrefService.of(context, listen: false);

  @override
  void initState() {
    super.initState();
    _text = TextEditingController();
    WidgetsBinding.instance.addPostFrameCallback((_) {
      if (mounted) _text.text = pixivCopyTemplate(_prefs, L10n.of(context));
    });
  }

  @override
  void dispose() {
    _text.dispose();
    super.dispose();
  }

  Future<void> _save(String value) async => _prefs.set(optionPluginPixivCopyTemplate, value);

  void _insert(String snippet) {
    final selection = _text.selection;
    final at = selection.isValid ? selection : TextSelection.collapsed(offset: _text.text.length);
    _text.value = _text.value.replaced(TextRange(start: at.start, end: at.end), snippet);
    _save(_text.text);
  }

  Future<void> _reset() async {
    _text.text = pixivDefaultCopyTemplate(L10n.of(context));
    await _save('');
  }

  @override
  Widget build(BuildContext context) {
    final l10n = L10n.of(context);
    return Scaffold(
      appBar: AppBar(
        title: Text(l10n.plugin_pixiv_copy_template),
        actions: [
          IconButton(
            tooltip: l10n.plugin_pixiv_copy_template_reset,
            icon: const Icon(Icons.restart_alt),
            onPressed: _reset,
          ),
        ],
      ),
      body: ListView(
        padding: const EdgeInsets.all(16),
        children: [
          TextField(
            key: const ValueKey('pixiv-copy-template'),
            controller: _text,
            minLines: 4,
            maxLines: null,
            keyboardType: TextInputType.multiline,
            onChanged: _save,
            decoration: InputDecoration(
              border: const OutlineInputBorder(),
              helperText: l10n.plugin_pixiv_copy_template_help,
            ),
          ),
          const SizedBox(height: 16),
          Text(l10n.plugin_pixiv_copy_template_insert, style: Theme.of(context).textTheme.titleSmall),
          const SizedBox(height: 8),
          Wrap(spacing: 8, runSpacing: 8, children: _chips(l10n)),
        ],
      ),
    );
  }

  List<Widget> _chips(L10n l10n) => [
    for (final placeholder in pixivCopyPlaceholders)
      ActionChip(label: Text(placeholder), onPressed: () => _insert(placeholder)),
    ActionChip(
      avatar: const Icon(Icons.link, size: 18),
      label: Text(l10n.plugin_pixiv_copy_template_artwork_url),
      onPressed: () => _insert(pixivCopyArtworkUrl),
    ),
    ActionChip(
      avatar: const Icon(Icons.link, size: 18),
      label: Text(l10n.plugin_pixiv_copy_template_user_url),
      onPressed: () => _insert(pixivCopyUserUrl),
    ),
  ];
}
