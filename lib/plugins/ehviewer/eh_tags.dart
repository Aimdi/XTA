import 'package:xta/generated/l10n.dart';
import 'package:xta/plugins/ehviewer/eh_models.dart';
import 'package:xta/plugins/plugin_tag_chip.dart';

typedef EhTagGroup = ({EhNamespace? namespace, List<EhTag> tags});

/// [tags] under their namespaces, in the site's order; tags without one last.
List<EhTagGroup> ehTagGroups(Iterable<String> tags) {
  final parsed = tags.map(EhTag.parse).toList();
  return [
    for (final namespace in [...EhNamespace.values, null])
      if (parsed.where((tag) => tag.namespace == namespace).toList() case final members when members.isNotEmpty)
        (namespace: namespace, tags: members),
  ];
}

/// The colour a namespace's chips take, after the booru kinds they match.
PluginTagKind? ehTagKind(EhNamespace? namespace) => switch (namespace) {
  EhNamespace.artist || EhNamespace.group => PluginTagKind.artist,
  EhNamespace.parody => PluginTagKind.copyright,
  EhNamespace.character || EhNamespace.cosplayer => PluginTagKind.character,
  EhNamespace.male || EhNamespace.female || EhNamespace.mixed || EhNamespace.other => PluginTagKind.general,
  EhNamespace.language || EhNamespace.reclass => PluginTagKind.meta,
  EhNamespace.temp || null => null,
};

String ehNamespaceLabel(L10n l10n, EhNamespace? namespace) => switch (namespace) {
  EhNamespace.language => l10n.plugin_eh_ns_language,
  EhNamespace.parody => l10n.plugin_eh_ns_parody,
  EhNamespace.character => l10n.plugin_eh_ns_character,
  EhNamespace.group => l10n.plugin_eh_ns_group,
  EhNamespace.artist => l10n.plugin_eh_ns_artist,
  EhNamespace.cosplayer => l10n.plugin_eh_ns_cosplayer,
  EhNamespace.male => l10n.plugin_eh_ns_male,
  EhNamespace.female => l10n.plugin_eh_ns_female,
  EhNamespace.mixed => l10n.plugin_eh_ns_mixed,
  EhNamespace.other => l10n.plugin_eh_ns_other,
  EhNamespace.reclass => l10n.plugin_eh_ns_reclass,
  EhNamespace.temp || null => l10n.plugin_eh_ns_temp,
};
