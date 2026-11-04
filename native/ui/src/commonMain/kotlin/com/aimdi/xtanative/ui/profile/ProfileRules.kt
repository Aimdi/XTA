package com.aimdi.xtanative.ui.profile

import com.aimdi.xtanative.core.backup.FollowedAccount
import com.aimdi.xtanative.core.x.XAnswerException
import com.aimdi.xtanative.core.x.XUserUnavailableException
import com.aimdi.xtanative.core.x.model.XMedia
import com.aimdi.xtanative.core.x.model.XPost
import com.aimdi.xtanative.core.x.model.XThread
import com.aimdi.xtanative.core.x.model.XUser
import com.aimdi.xtanative.store.offline.TimelineCache
import com.aimdi.xtanative.store.settings.ProfileTab as StoredTab

/** A profile's tabs, in XTA's order: Posts, Replies (X's name for posts and replies), Media, and the device's Saved. */
enum class ProfileTab { POSTS, REPLIES, MEDIA, SAVED;

    companion object {
        /** The tab a profile opens on: the reader's "default profile tab". */
        fun opening(stored: StoredTab): ProfileTab = when (stored) {
            StoredTab.POSTS -> POSTS
            StoredTab.POSTS_AND_REPLIES -> REPLIES
            StoredTab.MEDIA -> MEDIA
            StoredTab.SAVED -> SAVED
        }
    }
}

/** Which posts a profile's Posts tab shows (posts_filter.dart). */
enum class PostsFilter {
    ALL,
    POSTS,
    REPOSTS,
    ;

    /** A repost is a thread whose first post carries another; a quote is the profile's own post. */
    fun accepts(thread: XThread): Boolean {
        val repost = thread.posts.firstOrNull()?.isRepost == true
        return when (this) {
            ALL -> true
            POSTS -> !repost
            REPOSTS -> repost
        }
    }
}

/** What a tile of a profile's media grid shows. GIFs play like videos, so they count as videos. */
enum class TileKind { PHOTO, VIDEO, BROADCAST }

/** One tile of the media grid: the [index]th picture of post [post], or its broadcast or Space. */
data class MediaTile(val post: XPost, val index: Int, val kind: TileKind, val thumbnailUrl: String?, val aspectRatio: Float?) {
    val key: String get() = post.id + "/" + index
}

/** Which tiles the media grid shows (media_grid_item.dart's MediaFilter). */
enum class MediaFilter {
    ALL,
    PHOTOS,
    VIDEOS,
    BROADCASTS,
    ;

    fun accepts(tile: MediaTile): Boolean = when (this) {
        ALL -> true
        PHOTOS -> tile.kind == TileKind.PHOTO
        VIDEOS -> tile.kind == TileKind.VIDEO
        BROADCASTS -> tile.kind == TileKind.BROADCAST
    }

    /** Broadcasts are found in the profile's posts, the rest in its media timeline. */
    val readsPosts: Boolean get() = this == BROADCASTS

    /** Broadcasts are sparse among posts, so more empty pages are read past before the grid says there are none. */
    val lookahead: Int get() = if (this == BROADCASTS) 12 else 4
}

object MediaTiles {
    /**
     * The tiles of [threads], each post's pictures in order. A broadcast or
     * Space is one tile, thumbed by its video, else its first picture, else
     * the card's picture: a live post with photos must not turn into photos.
     */
    fun of(threads: List<XThread>): List<MediaTile> = threads.flatMap { it.posts }.flatMap { post ->
        val live = post.live
        if (live != null) {
            val thumb = post.media.firstOrNull { it.kind == XMedia.Kind.VIDEO } ?: post.media.firstOrNull()
            listOf(MediaTile(post, 0, TileKind.BROADCAST, thumb?.url ?: live.imageUrl, thumb?.aspectRatio ?: live.aspectRatio))
        } else {
            post.media.mapIndexed { index, media ->
                MediaTile(post, index, if (media.kind == XMedia.Kind.PHOTO) TileKind.PHOTO else TileKind.VIDEO, media.url, media.aspectRatio)
            }
        }
    }

    /** [tiles] not already in [seen], which it then holds: successive media pages overlap at their edges. */
    fun unseen(tiles: List<MediaTile>, seen: MutableSet<String>): List<MediaTile> = tiles.filter { seen.add(it.key) }
}

/** Why a profile did not load, when X said so: a retry cannot change that, so it is the page (profile_load_failure.dart). */
enum class ProfileFailure {
    PRIVATE,
    NOT_FOUND,
    SUSPENDED,
    PAGE_NOT_FOUND,
    ;

    companion object {
        /** What X said, or null when the failure is one to retry. */
        fun of(error: Throwable): ProfileFailure? = when (error) {
            is XUserUnavailableException -> when (error.reason) {
                null -> NOT_FOUND
                "Suspended" -> SUSPENDED
                else -> null
            }
            is XAnswerException -> when (error.errorCode) {
                22 -> PRIVATE
                34 -> PAGE_NOT_FOUND
                50 -> NOT_FOUND
                63 -> SUSPENDED
                else -> null
            }
            else -> null
        }
    }
}

/** A profile's header, as XTA's profile.dart and profile_chrome.dart build it. */
object ProfileHeader {
    /** X's banners come in set sizes; a phone needs no more than this one. */
    fun bannerUrl(url: String): String = if (Regex("/\\d+x\\d+$").containsMatchIn(url)) url else "$url/1080x360"

    /** The avatar a tap opens: X's 400 × 400 in place of its `_normal`. */
    fun fullAvatarUrl(url: String): String = url.replace("_normal.", "_400x400.")

    /** The website as the header writes it: without its scheme and its last slash. */
    fun websiteLabel(url: String): String = url.removePrefix("https://").removePrefix("http://").removePrefix("www.").removeSuffix("/")

    /**
     * Whether a protected account's posts stay behind the gate: X sends none
     * to an account that does not follow it, except to the account itself,
     * which X reports as not following itself.
     */
    fun postsGated(user: XUser, ownScreenNames: Collection<String?>): Boolean =
        user.postsHidden && ownScreenNames.none { it?.removePrefix("@").equals(user.screenName, ignoreCase = true) }

    /** The search a profile's search button opens: its own posts. */
    fun searchQuery(user: XUser): String = "from:@${user.screenName} "
}

/** XTA's "Max posts per feed load" for a followed account: off, or one of these. */
object QuietAccount {
    val CHOICES: List<Int?> = listOf(null, 1, 2, 3, 5, 10)

    /** The stored cap as a choice: anything else reads as off. */
    fun choiceOf(stored: Int?): Int? = stored?.takeIf { it in CHOICES }
}

/** A page of a list of people, without anyone already listed; [next] is null once the list is read to its end. */
data class PeoplePage(val people: List<XUser>, val next: String?)

/**
 * People listed page after page (followers, following, reposters), as XTA's
 * pagers read them: someone X sends again is not listed twice, and a page
 * with nobody new, a cursor X repeats, or its `0` ends the list.
 */
class PeoplePaging {
    private val seen = HashSet<String>()

    fun reset() = seen.clear()

    fun page(users: List<XUser>, next: String?, cursor: String?): PeoplePage {
        val fresh = users.filter { it.id.isNotEmpty() && seen.add(it.id) }
        val ends = next.isNullOrEmpty() || next == "0" || next == cursor || fresh.isEmpty()
        return PeoplePage(fresh, next.takeIf { !ends })
    }
}

/** One followed account's Following list as last read: when, and whom it follows, newest follow first. */
data class RememberedFollows(val at: Long, val ids: List<String>)

/**
 * The Following lists of followed accounts that XTA Native has read, kept
 * for this run: what "Followed by" answers from, so opening a profile asks X
 * for nothing (profile_followed_by.dart). A list older than [retentionMillis]
 * is no longer trusted.
 */
class FollowsMemory(private val clock: () -> Long = System::currentTimeMillis, private val retentionMillis: Long = 30L * 24 * 60 * 60 * 1000) {
    private val kept = HashMap<String, RememberedFollows>()

    @Synchronized
    fun remember(memberId: String, follows: List<XUser>) {
        kept[memberId] = RememberedFollows(clock(), follows.map { it.id })
    }

    @Synchronized
    fun remembered(): Map<String, RememberedFollows> = kept.filterValues { clock() - it.at <= retentionMillis }

    companion object {
        val shared = FollowsMemory()
    }
}

/** Which followed accounts follow a profile, and how many of the reader's follows that could be checked for. */
data class FollowedBy(val followers: List<FollowedAccount> = emptyList(), val checked: Int = 0, val total: Int = 0) {
    /** How the line names them: up to three, then two and how many more. */
    sealed interface Label {
        data class Names(val names: List<String>) : Label

        data class Many(val first: String, val second: String, val others: Int) : Label
    }

    val label: Label?
        get() = when {
            followers.isEmpty() -> null
            followers.size <= 3 -> Label.Names(followers.map { it.name })
            else -> Label.Many(followers[0].name, followers[1].name, followers.size - 2)
        }

    companion object {
        /**
         * The accounts of [following] whose remembered list holds
         * [profileId], the most recent follow first, then in the reader's
         * order. A list holds an account's latest follows only: a match is
         * sure, a miss proves nothing.
         */
        fun of(profileId: String, remembered: Map<String, RememberedFollows>, following: List<FollowedAccount>): FollowedBy {
            val members = following.filter { it.id != profileId }
            val checked = members.filter { it.id in remembered }
            val ranked = checked.mapIndexedNotNull { order, member ->
                val position = remembered.getValue(member.id).ids.indexOf(profileId)
                if (position < 0) null else Triple(member, position, order)
            }.sortedWith(compareBy({ it.second }, { it.third }))
            return FollowedBy(ranked.map { it.first }, checked.size, members.size)
        }
    }
}

/**
 * A profile's first pages kept on the device (_tweets.dart): a second visit
 * paints at once, and still shows something while X gives nothing. Fifteen
 * minutes; a pull reads X again. The media grid is not kept, as in XTA.
 */
object ProfileCache {
    const val MAX_AGE_MS = 15 * 60 * 1000L

    enum class Timeline { POSTS, REPOSTS, REPLIES }

    /** The key of [userId]'s own posts, its reposts, or its posts with its replies; XTA's keys, so the two never mix. */
    fun key(userId: String, timeline: Timeline): String = when (timeline) {
        Timeline.POSTS -> TimelineCache.profileKey(userId, "profile", includeReplies = false)
        Timeline.REPOSTS -> TimelineCache.profileKey(userId, "reposts", includeReplies = false)
        Timeline.REPLIES -> TimelineCache.profileKey(userId, "profile", includeReplies = true)
    }
}
