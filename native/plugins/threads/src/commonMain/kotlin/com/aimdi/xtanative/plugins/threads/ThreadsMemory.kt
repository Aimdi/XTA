package com.aimdi.xtanative.plugins.threads

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.networks.threads.ThreadsGuardKept
import com.aimdi.xtanative.networks.threads.ThreadsKept
import com.aimdi.xtanative.networks.threads.ThreadsMemory
import com.aimdi.xtanative.store.backup.XtaTime
import com.aimdi.xtanative.store.settings.PrefValue
import com.aimdi.xtanative.store.settings.Settings
import com.aimdi.xtanative.store.settings.ThreadsPrefs
import java.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive

/**
 * What the client keeps between launches (the device id the app API is sent,
 * each handle's numeric id, the last guest token and when it was taken, when
 * a parked session may talk to Meta again, what the account guard counts),
 * in the app's settings under the
 * keys XTA keeps them by. Read from them until the client first changes
 * something; from then on the client's own copy is the one in force, and it
 * reaches the settings behind it, the newest each time.
 */
internal class ThreadsSettingsMemory(
    private val stored: StateFlow<Settings?>,
    private val write: suspend ((Settings) -> Settings) -> Unit,
    scope: CoroutineScope,
) : ThreadsMemory {
    private val local = MutableStateFlow<ThreadsKept?>(null)

    override var kept: ThreadsKept
        get() = local.value ?: read(stored.value ?: Settings())
        set(value) {
            local.value = value
        }

    init {
        // A refresh changes this at every request (the guest token's time, the guard's counts), and a write is the
        // whole settings file, heard by every screen: a burst is one write of the newest, a moment after it.
        scope.launch {
            local.filterNotNull().conflate().collect {
                delay(WRITE_AFTER_MILLIS)
                val newest = local.value ?: return@collect
                write { written(it, newest) }
            }
        }
    }

    companion object {
        /**
         * Where the account guard's counts are kept (ThreadsGuardKept): beside the
         * other Threads keys, as JSON. Times and headers only, nothing secret.
         */
        const val GUARD_KEY = "plugin.threads.direct.account_guard"

        /** How long after a change it is written: long enough for a refresh's requests to be one write. */
        const val WRITE_AFTER_MILLIS = 1_000L

        fun read(settings: Settings): ThreadsKept = ThreadsKept(
            deviceId = settings[ThreadsPrefs.directDeviceId].trim().ifEmpty { null },
            userIds = userIds(settings[ThreadsPrefs.userIds]),
            guestLsd = settings[ThreadsPrefs.guestLsd].ifEmpty { null },
            guestLsdAt = millis(settings[ThreadsPrefs.guestLsdAt]),
            cooldownUntil = millis(settings[ThreadsPrefs.directCooldownUntil]),
            guard = (settings.values[GUARD_KEY] as? PrefValue.Text)?.value?.let(ThreadsGuardKept::decode) ?: ThreadsGuardKept(),
        )

        fun written(settings: Settings, kept: ThreadsKept): Settings = settings
            .with(ThreadsPrefs.directDeviceId, kept.deviceId.orEmpty())
            .with(ThreadsPrefs.userIds, JsonObject(kept.userIds.mapValues { JsonPrimitive(it.value) }).toString())
            .with(ThreadsPrefs.guestLsd, kept.guestLsd.orEmpty())
            .with(ThreadsPrefs.guestLsdAt, iso(kept.guestLsdAt))
            .with(ThreadsPrefs.directCooldownUntil, iso(kept.cooldownUntil))
            .with(GUARD_KEY, PrefValue.Text(kept.guard.encode()))

        private fun userIds(text: String): Map<String, String> {
            val root = J.parse(text)
            return root.keys.mapNotNull { key -> root[key].string?.takeIf { it.isNotEmpty() }?.let { key to it } }.toMap()
        }

        private fun millis(text: String): Long? = XtaTime.parse(text)?.toEpochMilli()

        private fun iso(millis: Long?): String = millis?.let { XtaTime.iso(Instant.ofEpochMilli(it)) }.orEmpty()
    }
}
