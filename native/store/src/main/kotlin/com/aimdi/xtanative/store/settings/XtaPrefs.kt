package com.aimdi.xtanative.store.settings

/** Every preference XTA has, by section, in the order of XTA's settings and plugin registry. */
object XtaPrefs {
    /** The networks, in XTA's plugin registry order. */
    val networks: List<NetworkPrefs> = listOf(
        ThreadsPrefs, BlueskyPrefs, MastodonPrefs, TikTokPrefs, InstagramPrefs, RedditPrefs, HackerNewsPrefs,
        SubstackPrefs, RssPrefs, WebSourcesPrefs, PixivPrefs, BooruPrefs, EhViewerPrefs, StocksPrefs,
        KarakeepPrefs, DeepmarksPrefs, ImmichPrefs,
    )

    val sections: List<PrefSection> = listOf(
        GeneralPrefs, PostsPrefs, MediaPrefs, HomePrefs, ThemePrefs, AccessibilityPrefs, SpeechPrefs, AccountPrefs, PluginStorePrefs,
        DataPrefs, AiPrefs, CrashReportPrefs, SavedPrefs, SubscriptionPrefs, DiscoverPrefs, SearchPrefs, ReadingPrefs,
    ) + networks

    val all: List<Pref<*>> = sections.flatMap { it.prefs }

    /** Preferences kept per group or per network. */
    val families: List<PrefFamily<*>> = listOf(HomePrefs.topBarPins, PostsPrefs.catchUp)

    /** Where XTA keeps followed accounts in preferences rather than a table: an export of subscriptions alone carries them. */
    val subscriptionPrefs: List<Pref<String>> = listOf(HackerNewsPrefs.follows, WebSourcesPrefs.follows, PixivPrefs.groupSubscriptions)

    private val byKey = all.associateBy { it.key }

    fun find(key: String): Pref<*>? = byKey[key]

    /** Whether [key] is one of XTA's preferences, a family's included. */
    fun isKnown(key: String): Boolean = key in byKey || families.any { it.idOf(key) != null }
}

/**
 * Preferences that never leave the device: not in an export, a backup or a
 * crash report. XTA names them, and also takes anything shaped like a
 * credential, so the next network's key is covered before anyone remembers.
 */
object SecretPrefs {
    val keys: Set<String> = listOf(
        CrashReportPrefs.githubToken, DataPrefs.webDavPassword, AiPrefs.apiKey, ReadingPrefs.translationApiKey,
        DeepmarksPrefs.apiKey, DeepmarksPrefs.secretKey, ImmichPrefs.apiKey, KarakeepPrefs.apiKey,
        RedditPrefs.clientId, RedditPrefs.refreshToken,
        ThreadsPrefs.apiToken, ThreadsPrefs.directCookies, ThreadsPrefs.directBearer,
        PixivPrefs.refreshToken, PixivPrefs.accessToken,
        // Whose those tokens are and what that account may do: beside another device's tokens they would name the wrong account.
        PixivPrefs.userId, PixivPrefs.isPremium, PixivPrefs.accessExpiresAt, PixivPrefs.accounts, PixivPrefs.novelReading,
        // The pages saved to this device's storage, which another device does not have.
        PixivPrefs.downloadIndex,
        EhViewerPrefs.cookies, TikTokPrefs.cookies, InstagramPrefs.cookies,
    // How this device reaches the network, which another device need not share.
    ).plus(DesktopPrefs.proxy).mapTo(HashSet()) { it.key }

    /**
     * Each address a secret is sent to, with the secrets sent there. A backup
     * from elsewhere must not move one while this device holds a secret for
     * it: the reader's own key would go to whatever server the file names.
     */
    val addresses: Map<String, List<String>> = listOf(
        AiPrefs.baseUrl to listOf(AiPrefs.apiKey),
        ReadingPrefs.translation to listOf(ReadingPrefs.translationApiKey),
        KarakeepPrefs.serverUrl to listOf(KarakeepPrefs.apiKey),
        ImmichPrefs.serverUrl to listOf(ImmichPrefs.apiKey),
        DeepmarksPrefs.apiBase to listOf(DeepmarksPrefs.apiKey, DeepmarksPrefs.secretKey),
        ThreadsPrefs.apiBase to listOf(ThreadsPrefs.apiToken),
        DataPrefs.webDavUrl to listOf(DataPrefs.webDavPassword),
        // Booru sends its login and key to this host alone.
        BooruPrefs.credentialsHost to listOf(BooruPrefs.apiKey, BooruPrefs.siteCredentials),
    ).associate { (address, secrets) -> address.key to secrets.map { it.key } }

    /** Whether [settings] hold a secret sent to [key], an address in [addresses]. */
    fun guards(settings: Settings, key: String): Boolean =
        addresses[key].orEmpty().any { secret -> (settings.values[secret] as? PrefValue.Text)?.value?.isNotBlank() == true }

    fun isSecret(key: String): Boolean =
        key in keys || key.endsWith("api_key") || key.endsWith("secret_key") || key.endsWith("_token") || key.endsWith("password")
}
