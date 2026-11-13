package com.aimdi.xtanative.store.settings

/** How dark XTA's background is (Settings › Theme). */
enum class ThemeBackground(override val stored: String) : PrefChoice {
    /** Follows the system, so a light phone is not dragged into a black UI. */
    SYSTEM("system"),
    LIGHT("light"),
    DIM("dim"),
    LIGHTS_OUT("lights_out"),
}

/** X's own accents, each legible on every background; [argb] is the colour. */
enum class ThemeAccent(override val stored: String, val argb: Long) : PrefChoice {
    BLUE("blue", 0xFF1D9BF0),
    YELLOW("yellow", 0xFFFFD400),
    PINK("pink", 0xFFF91880),
    PURPLE("purple", 0xFF7856FF),
    ORANGE("orange", 0xFFFF7A00),
    GREEN("green", 0xFF00BA7C),
}

/** The light or dark choice from before X Look; XTA still listens to it but no screen sets it. */
enum class ThemeMode(override val stored: String) : PrefChoice {
    SYSTEM("system"),
    LIGHT("light"),
    DARK("dark"),
}

/** The colour scheme from before X Look; kept, no longer offered. */
enum class ThemeColor(override val stored: String) : PrefChoice {
    ACCENT("accent"),
    RED("red"),
    ORANGE("orange"),
    YELLOW("yellow"),
    GREEN("green"),
    BLUE("blue"),
    INDIGO("indigo"),
    VIOLET("violet"),
}

/** The theme presets from before X Look; each launch moves a stored one onto [ThemeBackground]. */
enum class ThemePreset(override val stored: String, val background: ThemeBackground) : PrefChoice {
    NONE("none", ThemeBackground.SYSTEM),
    FAIRY_FOREST("fairy_forest", ThemeBackground.SYSTEM),
    PITCH_BLACK("pitch_black", ThemeBackground.SYSTEM),
    X_LOOK_LIGHT("x_look_light", ThemeBackground.LIGHT),
    X_LOOK_DIM("x_look_dim", ThemeBackground.DIM),
    X_LOOK_LIGHTS_OUT("x_look_lights_out", ThemeBackground.LIGHTS_OUT),
}

/** The tab a profile opens on. */
enum class ProfileTab(override val stored: String) : PrefChoice {
    POSTS("posts"),
    POSTS_AND_REPLIES("postsAndReplies"),
    MEDIA("media"),
    SAVED("saved"),
}

/** Image and video sizes, smallest first; Settings › Media calls them low, medium, high and maximum. */
enum class MediaQuality(override val stored: String) : PrefChoice {
    THUMB("thumb"),
    SMALL("small"),
    MEDIUM("medium"),
    LARGE("large"),
}

/** How a media tab lays out its pictures. */
enum class MediaGridLayout(override val stored: String) : PrefChoice {
    MASONRY("masonry"),
    FEED("feed"),
    TWO_COLUMNS("two_columns"),
}

/** Where a download goes: a folder picked once, asked each time, or the shared folder for its kind without asking. */
enum class DownloadType(override val stored: String) : PrefChoice {
    DIRECTORY("directory"),
    ASK("ask"),
    AUTO("auto"),
}

/** What the feed does with posts outside the languages the reader reads. */
enum class LanguageFilterAction(override val stored: String) : PrefChoice {
    OFF("off"),
    HIDE("hide"),
    FOLD("fold"),
}

/** How the groups tab is laid out. */
enum class GroupsLayout(override val stored: String) : PrefChoice {
    BOARD("board"),
    LIST("list"),
}

/** What groups are sorted by; [POSITION] is the reader's own order. */
enum class GroupsOrder(override val stored: String) : PrefChoice {
    NAME("name"),
    CREATED_AT("created_at"),
    POSITION("position"),
}

/** What followed accounts are sorted by. */
enum class SubscriptionsOrder(override val stored: String) : PrefChoice {
    NAME("name"),
    SCREEN_NAME("screen_name"),
    CREATED_AT("created_at"),
}

/** A ready-made AI endpoint: Settings › AI fills in its address and model so the reader only pastes a key. */
enum class AiProvider(val baseUrl: String, val model: String) {
    GROK("https://api.x.ai/v1", "grok-4"),
    OPENAI("https://api.openai.com/v1", "gpt-4o-mini"),
    OPENROUTER("https://openrouter.ai/api/v1", "openrouter/free"),
}

/** Which route Reddit is read through: the best credential given, or the account-free endpoints only. */
enum class RedditSource(override val stored: String) : PrefChoice {
    AUTO("auto"),
    PUBLIC("public"),
}

enum class RedditSort(override val stored: String) : PrefChoice {
    HOT("hot"),
    NEWEST("newest"),
    TOP("top"),
    RISING("rising"),
    CONTROVERSIAL("controversial"),
}

/** The window of Reddit's top and controversial listings. */
enum class RedditTimeFilter(override val stored: String) : PrefChoice {
    HOUR("hour"),
    DAY("day"),
    WEEK("week"),
    MONTH("month"),
    YEAR("year"),
    ALL("all"),
}

/** The discovery rail open in the Reddit tab. */
enum class RedditFeedMode(override val stored: String) : PrefChoice {
    FOLLOWING("following"),
    POPULAR("popular"),
    ALL("all"),
}

/** Over-18 Reddit posts are hidden, behind a tap, or shown. */
enum class RedditNsfwMode(override val stored: String) : PrefChoice {
    HIDE("hide"),
    TAP("tap"),
    SHOW("show"),
}

enum class SubstackReaderFont(override val stored: String) : PrefChoice {
    SERIF("serif"),
    SANS("sans"),
}

/** How wide the reader's column may grow, in rem (16 logical pixels). */
enum class SubstackColumnWidth(override val stored: String, val rem: Int) : PrefChoice {
    NARROW("narrow", 36),
    MEDIUM("medium", 42),
    WIDE("wide", 52),
}

/** The section the Pixiv screen opens on. */
enum class PixivSection(override val stored: String) : PrefChoice {
    HOME("home"),
    RANKING("ranking"),
    FAVORITES("favorites"),
    SEARCH("search"),
}

/** The sizes Pixiv serves every work in, smallest first. */
enum class PixivImageQuality(override val stored: String) : PrefChoice {
    MEDIUM("medium"),
    LARGE("large"),
    ORIGINAL("original"),
}

/** Whether a work's pictures sit beside its details: by width, never, or whenever they fit. */
enum class PixivDetailLayout(override val stored: String) : PrefChoice {
    AUTO("auto"),
    VERTICAL("vertical"),
    SPLIT("split"),
}

/** The API a booru speaks. XTA also reads the older names [parse] knows. */
enum class BooruEngine(override val stored: String) : PrefChoice {
    DANBOORU("danbooru"),
    MOEBOORU("moebooru"),
    GELBOORU_V2("gelbooru_v2"),
    E621("e621"),
    ;

    companion object {
        fun parse(raw: String): BooruEngine? = when (raw.trim().lowercase()) {
            "danbooru" -> DANBOORU
            "moebooru" -> MOEBOORU
            "gelbooru", "gelbooru_v2", "gelbooru2" -> GELBOORU_V2
            "e621", "e926" -> E621
            else -> null
        }
    }
}

/** The highest rating a booru shows, stored as Danbooru's letter. XTA also reads the words. */
enum class BooruRating(override val stored: String) : PrefChoice {
    GENERAL("g"),
    SENSITIVE("s"),
    QUESTIONABLE("q"),
    EXPLICIT("e"),
    ;

    companion object {
        fun parse(raw: String): BooruRating? = when (raw.trim().lowercase()) {
            "g", "general", "safe" -> GENERAL
            "s", "sensitive" -> SENSITIVE
            "q", "questionable" -> QUESTIONABLE
            "e", "explicit" -> EXPLICIT
            else -> null
        }
    }
}

/** How a post's tags are sorted. */
enum class BooruTagSort(override val stored: String) : PrefChoice {
    NAME("name"),
    COUNT("count"),
}

/** How a gallery is read: paged either way, or one long column. */
enum class EhReadingMode(override val stored: String) : PrefChoice {
    LEFT_TO_RIGHT("leftToRight"),
    RIGHT_TO_LEFT("rightToLeft"),
    VERTICAL("vertical"),
}
