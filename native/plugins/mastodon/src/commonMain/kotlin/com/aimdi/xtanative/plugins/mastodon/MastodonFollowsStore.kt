package com.aimdi.xtanative.plugins.mastodon

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.mastodon.MastodonFollow
import com.aimdi.xtanative.networks.mastodon.MastodonFollowRows
import com.aimdi.xtanative.networks.mastodon.MastodonProfile
import com.aimdi.xtanative.store.StoreFile
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * The Fediverse accounts followed on this device, in the plugin's own folder
 * as XTA's rows ([MastodonFollowRows]): what Following reads and the backup
 * keeps, as XTA's `mastodonSubscriptions`. No instance is told.
 */
internal class MastodonFollowsStore(private val file: StoreFile) {
    private val mutable = MutableStateFlow<List<MastodonFollow>?>(null)

    /** By name; null until the file has been read. */
    val state: StateFlow<List<MastodonFollow>?> = mutable.asStateFlow()

    private val mutex = Mutex()

    suspend fun load(): List<MastodonFollow> = mutex.withLock { loaded() }

    /** The accounts' addresses, for Following to read. */
    fun accts(): List<String> = mutable.value.orEmpty().map { it.acct }

    fun follows(acct: String): Boolean = mutable.value.orEmpty().any { it.isOf(acct) }

    /** [profile] followed here, its name and picture as they are now; [createdAt] is when, as XTA writes it. */
    suspend fun follow(profile: MastodonProfile, createdAt: String) =
        follow(MastodonFollow(profile.acct, profile.displayName.ifBlank { profile.acct }, profile.avatarUrl, true, createdAt))

    /** [follow] in place of the row of its account, or after the others. */
    suspend fun follow(follow: MastodonFollow) = change { follows -> MastodonFollowRows.upsert(follows, listOf(follow)) }

    suspend fun unfollow(acct: String) = change { follows -> follows.filterNot { it.isOf(acct) } }

    /** The follows as the backup's section keeps them: XTA's rows. */
    suspend fun exportRows(): String = mutex.withLock { MastodonFollowRows.encode(loaded()).toString() }

    /** A backup's rows in place of the follows. */
    suspend fun importRows(text: String) = mutex.withLock { save(MastodonFollowRows.decode(J.parse(text))) }

    private suspend fun change(edit: (List<MastodonFollow>) -> List<MastodonFollow>) = mutex.withLock { save(edit(loaded())) }

    private suspend fun save(follows: List<MastodonFollow>) {
        val ordered = MastodonFollowRows.ordered(follows)
        file.write(MastodonFollowRows.encode(ordered).toString())
        mutable.value = ordered
    }

    private suspend fun loaded(): List<MastodonFollow> =
        mutable.value ?: MastodonFollowRows.ordered(MastodonFollowRows.decode(J.parse(file.read().orEmpty()))).also { mutable.value = it }
}
