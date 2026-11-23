package com.aimdi.xtanative.ui.media.mpris

import com.aimdi.xtanative.ui.media.AudioSessionBinding

/** What the desktop's media controls are shown of what plays: positions in milliseconds. */
data class MprisNow(
    val track: Long = 0,
    val title: String = "",
    val artist: String? = null,
    val playing: Boolean = false,
    val positionMs: Long = 0,
    val durationMs: Long? = null,
    val canPlay: Boolean = false,
    val canPause: Boolean = false,
    val canSeek: Boolean = false,
    /** A picture of it on disk (file://), for the controls' artwork. */
    val artUrl: String? = null,
) {
    val status: String get() = if (playing) "Playing" else "Paused"
}

/** What a call from the media controls asks of what is bound. */
sealed interface MprisAction {
    data object Play : MprisAction

    data object Pause : MprisAction

    data object Stop : MprisAction

    data class Seek(val positionMs: Long) : MprisAction

    data object Raise : MprisAction
}

/** The answer to one call: its reply's signature and body, or an error; with the [action] it asks for. */
data class MprisAnswer(
    val signature: String = "",
    val body: List<Any?> = emptyList(),
    val error: String? = null,
    val action: MprisAction? = null,
)

/**
 * MPRIS (the Media Player Remote Interfacing Specification), as plain
 * decisions: what each property of org.mpris.MediaPlayer2 and its Player is
 * while [MprisNow] plays, and what each call does. Play/pause keys reach a
 * reading as stop, since a reading cannot be paused.
 */
object Mpris {
    const val NAME = "org.mpris.MediaPlayer2.xtanative"
    const val PATH = "/org/mpris/MediaPlayer2"
    const val ROOT = "org.mpris.MediaPlayer2"
    const val PLAYER = "org.mpris.MediaPlayer2.Player"
    const val PROPERTIES = "org.freedesktop.DBus.Properties"
    const val INTROSPECTABLE = "org.freedesktop.DBus.Introspectable"
    const val PEER = "org.freedesktop.DBus.Peer"
    const val DESKTOP_ENTRY = "com.aimdi.xtanative"
    private const val TRACKS = "/com/aimdi/xtanative/track/"
    private const val NOT_SUPPORTED = "org.freedesktop.DBus.Error.NotSupported"
    private const val UNKNOWN_PROPERTY = "org.freedesktop.DBus.Error.UnknownProperty"
    private const val UNKNOWN_INTERFACE = "org.freedesktop.DBus.Error.UnknownInterface"

    /** The track's id, which changes with each new thing bound, so a control's SetPosition aims at the one it saw. */
    fun trackId(now: MprisNow) = ObjectPath("$TRACKS${now.track}")

    fun metadata(now: MprisNow): Map<String, Variant> = buildMap {
        put("mpris:trackid", Variant("o", trackId(now)))
        if (now.title.isNotEmpty()) put("xesam:title", Variant("s", now.title))
        now.artist?.takeIf { it.isNotEmpty() }?.let { put("xesam:artist", Variant("as", listOf(it))) }
        now.durationMs?.takeIf { it > 0 }?.let { put("mpris:length", Variant("x", it * 1000)) }
        now.artUrl?.let { put("mpris:artUrl", Variant("s", it)) }
    }

    fun rootProperties(identity: String): Map<String, Variant> = mapOf(
        "CanQuit" to Variant("b", false),
        "CanRaise" to Variant("b", true),
        "HasTrackList" to Variant("b", false),
        "Identity" to Variant("s", identity),
        "DesktopEntry" to Variant("s", DESKTOP_ENTRY),
        "SupportedUriSchemes" to Variant("as", emptyList<String>()),
        "SupportedMimeTypes" to Variant("as", emptyList<String>()),
    )

    fun playerProperties(now: MprisNow): Map<String, Variant> = mapOf(
        "PlaybackStatus" to Variant("s", now.status),
        "Rate" to Variant("d", 1.0),
        "MinimumRate" to Variant("d", 1.0),
        "MaximumRate" to Variant("d", 1.0),
        "Volume" to Variant("d", 1.0),
        "Metadata" to Variant("a{sv}", metadata(now)),
        "Position" to Variant("x", now.positionMs * 1000),
        "CanGoNext" to Variant("b", false),
        "CanGoPrevious" to Variant("b", false),
        // Play/pause keys reach anything bound: a reading stops.
        "CanPlay" to Variant("b", true),
        "CanPause" to Variant("b", true),
        "CanSeek" to Variant("b", now.canSeek),
        "CanControl" to Variant("b", true),
    )

    /** What changed between [before] and [after] that controls are told of (Position never is: they ask for it). */
    fun changed(before: MprisNow, after: MprisNow): Map<String, Variant> {
        val old = playerProperties(before)
        return playerProperties(after).filter { (key, value) -> key != "Position" && old[key] != value }
    }

    /** The play/pause key: pause what plays and can pause, play what is paused and can play, else stop. */
    fun playPause(now: MprisNow): MprisAction = when {
        now.playing && now.canPause -> MprisAction.Pause
        !now.playing && now.canPlay -> MprisAction.Play
        else -> MprisAction.Stop
    }

    /** The answer to [call] while [now] is bound, the app known as [identity]. */
    fun answer(call: DBusMessage, now: MprisNow, identity: String): MprisAnswer {
        val args = call.body
        return when (call.iface to call.member) {
            PEER to "Ping" -> MprisAnswer()
            INTROSPECTABLE to "Introspect" -> MprisAnswer("s", listOf(INTROSPECTION))
            PROPERTIES to "Get" -> {
                val all = propertiesOf(args.getOrNull(0) as? String, now, identity) ?: return MprisAnswer(error = UNKNOWN_INTERFACE)
                all[args.getOrNull(1)]?.let { MprisAnswer("v", listOf(it)) } ?: MprisAnswer(error = UNKNOWN_PROPERTY)
            }
            PROPERTIES to "GetAll" -> MprisAnswer("a{sv}", listOf(propertiesOf(args.getOrNull(0) as? String, now, identity) ?: emptyMap<String, Variant>()))
            PROPERTIES to "Set" -> MprisAnswer(error = NOT_SUPPORTED)
            ROOT to "Raise" -> MprisAnswer(action = MprisAction.Raise)
            ROOT to "Quit" -> MprisAnswer(error = NOT_SUPPORTED)
            PLAYER to "Play" -> MprisAnswer(action = if (now.canPlay) MprisAction.Play else null)
            PLAYER to "Pause" -> MprisAnswer(action = if (now.canPause) MprisAction.Pause else MprisAction.Stop.takeIf { !now.canPlay })
            PLAYER to "PlayPause" -> MprisAnswer(action = playPause(now))
            PLAYER to "Stop" -> MprisAnswer(action = MprisAction.Stop)
            PLAYER to "Next", PLAYER to "Previous", PLAYER to "OpenUri" -> MprisAnswer()
            PLAYER to "Seek" -> MprisAnswer(action = seekBy(now, (args.getOrNull(0) as? Long) ?: 0))
            PLAYER to "SetPosition" -> MprisAnswer(
                action = if (args.getOrNull(0) == trackId(now)) seekTo(now, ((args.getOrNull(1) as? Long) ?: -1) / 1000) else null,
            )
            else -> MprisAnswer(error = DBusConnection.UNKNOWN_METHOD)
        }
    }

    private fun propertiesOf(iface: String?, now: MprisNow, identity: String): Map<String, Variant>? = when (iface) {
        ROOT -> rootProperties(identity)
        PLAYER -> playerProperties(now)
        else -> null
    }

    /** MPRIS's Seek: by [offsetUs] from where it is, to the start at least; past the end is the next track, which there is none of: it stops. */
    private fun seekBy(now: MprisNow, offsetUs: Long): MprisAction? {
        if (!now.canSeek) return null
        val target = (now.positionMs + offsetUs / 1000).coerceAtLeast(0)
        val end = now.durationMs
        return if (end != null && end > 0 && target > end) MprisAction.Stop else MprisAction.Seek(target)
    }

    /** SetPosition: ignored outside the track, as the specification says. */
    private fun seekTo(now: MprisNow, positionMs: Long): MprisAction? {
        if (!now.canSeek || positionMs < 0) return null
        val end = now.durationMs
        return if (end != null && end > 0 && positionMs > end) null else MprisAction.Seek(positionMs)
    }

    /** What [binding] offers the controls. */
    fun offered(now: MprisNow, binding: AudioSessionBinding): MprisNow =
        now.copy(canPlay = binding.onPlay != null, canPause = binding.onPause != null, canSeek = binding.onSeek != null)

    private val INTROSPECTION = """
        <!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN" "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">
        <node>
          <interface name="org.freedesktop.DBus.Introspectable"><method name="Introspect"><arg name="data" type="s" direction="out"/></method></interface>
          <interface name="org.freedesktop.DBus.Peer"><method name="Ping"/></interface>
          <interface name="org.freedesktop.DBus.Properties">
            <method name="Get"><arg name="interface" type="s" direction="in"/><arg name="property" type="s" direction="in"/><arg name="value" type="v" direction="out"/></method>
            <method name="GetAll"><arg name="interface" type="s" direction="in"/><arg name="properties" type="a{sv}" direction="out"/></method>
            <method name="Set"><arg name="interface" type="s" direction="in"/><arg name="property" type="s" direction="in"/><arg name="value" type="v" direction="in"/></method>
            <signal name="PropertiesChanged"><arg name="interface" type="s"/><arg name="changed" type="a{sv}"/><arg name="invalidated" type="as"/></signal>
          </interface>
          <interface name="org.mpris.MediaPlayer2">
            <method name="Raise"/><method name="Quit"/>
            <property name="CanQuit" type="b" access="read"/><property name="CanRaise" type="b" access="read"/>
            <property name="HasTrackList" type="b" access="read"/><property name="Identity" type="s" access="read"/>
            <property name="DesktopEntry" type="s" access="read"/>
            <property name="SupportedUriSchemes" type="as" access="read"/><property name="SupportedMimeTypes" type="as" access="read"/>
          </interface>
          <interface name="org.mpris.MediaPlayer2.Player">
            <method name="Next"/><method name="Previous"/><method name="Pause"/><method name="PlayPause"/><method name="Stop"/><method name="Play"/>
            <method name="Seek"><arg name="Offset" type="x" direction="in"/></method>
            <method name="SetPosition"><arg name="TrackId" type="o" direction="in"/><arg name="Position" type="x" direction="in"/></method>
            <method name="OpenUri"><arg name="Uri" type="s" direction="in"/></method>
            <signal name="Seeked"><arg name="Position" type="x"/></signal>
            <property name="PlaybackStatus" type="s" access="read"/><property name="Rate" type="d" access="read"/>
            <property name="Metadata" type="a{sv}" access="read"/><property name="Volume" type="d" access="read"/>
            <property name="Position" type="x" access="read"/><property name="MinimumRate" type="d" access="read"/>
            <property name="MaximumRate" type="d" access="read"/><property name="CanGoNext" type="b" access="read"/>
            <property name="CanGoPrevious" type="b" access="read"/><property name="CanPlay" type="b" access="read"/>
            <property name="CanPause" type="b" access="read"/><property name="CanSeek" type="b" access="read"/>
            <property name="CanControl" type="b" access="read"/>
          </interface>
        </node>
    """.trimIndent()
}
