import 'package:flutter/material.dart';
import 'package:xta/generated/l10n.dart';

/// Why a plugin exists, for the store — not how it is installed.
enum PluginCategory {
  /// Short-form social networks (Threads, Bluesky, Mastodon, TikTok).
  social,

  /// Forums and link communities (Reddit).
  communities,

  /// Long-form publishing and newsletters (Substack).
  newsletters,

  /// Illustration and visual feeds (Pixiv).
  art,

  /// Prices and markets (Stocks).
  markets,

  /// Send or keep bookmarks elsewhere (Karakeep, Deepmarks).
  bookmarks,

  /// Photos and media destinations (Immich).
  media,
}

extension PluginCategoryL10n on PluginCategory {
  String label(BuildContext context) {
    final l10n = L10n.of(context);
    return switch (this) {
      PluginCategory.social => l10n.plugin_category_social,
      PluginCategory.communities => l10n.plugin_category_communities,
      PluginCategory.newsletters => l10n.plugin_category_newsletters,
      PluginCategory.art => l10n.plugin_category_art,
      PluginCategory.markets => l10n.plugin_category_markets,
      PluginCategory.bookmarks => l10n.plugin_category_bookmarks,
      PluginCategory.media => l10n.plugin_category_media,
    };
  }
}

/// Store order: social first, then communities, publishing, art, markets, saves, media.
const pluginCategoryOrder = PluginCategory.values;
