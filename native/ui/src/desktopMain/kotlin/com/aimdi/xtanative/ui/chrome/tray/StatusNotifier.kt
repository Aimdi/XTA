package com.aimdi.xtanative.ui.chrome.tray

import androidx.compose.runtime.Composable
import com.aimdi.xtanative.ui.generated.resources.Res
import com.aimdi.xtanative.ui.generated.resources.keys_quit
import com.aimdi.xtanative.ui.generated.resources.tray_hide
import com.aimdi.xtanative.ui.generated.resources.tray_show
import com.aimdi.xtanative.ui.media.mpris.DBusConnection
import com.aimdi.xtanative.ui.media.mpris.DBusMessage
import com.aimdi.xtanative.ui.media.mpris.ObjectPath
import com.aimdi.xtanative.ui.media.mpris.Variant
import org.jetbrains.compose.resources.stringResource

/** What the tray's icon and its menu say: the app's name and the menu's words, in the reader's language. */
data class TrayWords(
    val title: String = "XTA Native",
    val show: String = "Show",
    val hide: String = "Hide",
    val quit: String = "Quit",
)

/** The tray's words in the reader's language, under the app's name [title]. */
@Composable
fun trayWords(title: String): TrayWords =
    TrayWords(title, stringResource(Res.string.tray_show), stringResource(Res.string.tray_hide), stringResource(Res.string.keys_quit))

/** What the tray is shown of the app: its words, and whether the window is on screen. */
data class TrayNow(
    val words: TrayWords = TrayWords(),
    val windowShown: Boolean = true,
    /** The menu's revision, which goes up each time its items change. */
    val revision: Long = 1,
)

/** What a click in the tray asks of the app. */
enum class TrayAction {
    /** The icon clicked: the window on screen and in front. */
    SHOW,

    /** The menu's Show/Hide. */
    TOGGLE,

    /** The menu's Quit. */
    QUIT,
}

/** The answer to one call: its reply's signature and body, or an error; with the [action] it asks for. */
data class TrayAnswer(
    val signature: String = "",
    val body: List<Any?> = emptyList(),
    val error: String? = null,
    val action: TrayAction? = null,
)

/**
 * The tray icon, as plain decisions: a StatusNotifierItem (the
 * freedesktop/KDE tray specification Waybar, quickshell's bar, KDE and
 * GNOME's AppIndicator extension show) at [ITEM_PATH] and its
 * com.canonical.dbusmenu menu at [MENU_PATH]: Show or Hide, then Quit.
 * Each property and each call's answer is decided here; [TrayService]
 * carries them over the bus.
 */
object StatusNotifier {
    const val WATCHER = "org.kde.StatusNotifierWatcher"
    const val WATCHER_PATH = "/StatusNotifierWatcher"
    const val ITEM = "org.kde.StatusNotifierItem"
    const val ITEM_PATH = "/StatusNotifierItem"
    const val MENU = "com.canonical.dbusmenu"
    const val MENU_PATH = "/MenuBar"
    const val PROPERTIES = "org.freedesktop.DBus.Properties"
    const val INTROSPECTABLE = "org.freedesktop.DBus.Introspectable"
    const val PEER = "org.freedesktop.DBus.Peer"
    const val ICON_NAME = "com.aimdi.xtanative"
    private const val UNKNOWN_PROPERTY = "org.freedesktop.DBus.Error.UnknownProperty"
    private const val UNKNOWN_INTERFACE = "org.freedesktop.DBus.Error.UnknownInterface"
    private const val INVALID_ARGS = "org.freedesktop.DBus.Error.InvalidArgs"

    /** The menu's items: Show/Hide, a line, Quit; 0 is the root that holds them. */
    const val ROOT_ID = 0
    const val TOGGLE_ID = 1
    const val LINE_ID = 2
    const val QUIT_ID = 3
    private val ITEMS = listOf(TOGGLE_ID, LINE_ID, QUIT_ID)

    /** The well-known name the item takes on the bus, as the specification asks: one per process. */
    fun busName(pid: Long) = "org.kde.StatusNotifierItem-$pid-1"

    /**
     * The item's properties: the app's icon as [pixmaps] (each a width, a
     * height and its ARGB32 pixels), named [iconName] too where the
     * package installed it in the icon theme.
     */
    fun itemProperties(now: TrayNow, iconName: String, pixmaps: List<List<Any>>): Map<String, Variant> = mapOf(
        "Category" to Variant("s", "ApplicationStatus"),
        "Id" to Variant("s", "xta-native"),
        "Title" to Variant("s", now.words.title),
        "Status" to Variant("s", "Active"),
        "WindowId" to Variant("i", 0),
        "IconName" to Variant("s", iconName),
        "IconPixmap" to Variant("a(iiay)", pixmaps),
        "IconThemePath" to Variant("s", ""),
        "OverlayIconName" to Variant("s", ""),
        "OverlayIconPixmap" to Variant("a(iiay)", emptyList<Any>()),
        "AttentionIconName" to Variant("s", ""),
        "AttentionIconPixmap" to Variant("a(iiay)", emptyList<Any>()),
        "AttentionMovieName" to Variant("s", ""),
        "ToolTip" to Variant("(sa(iiay)ss)", listOf(iconName, emptyList<Any>(), now.words.title, "")),
        "ItemIsMenu" to Variant("b", false),
        "Menu" to Variant("o", ObjectPath(MENU_PATH)),
    )

    fun menuProperties(): Map<String, Variant> = mapOf(
        "Version" to Variant("u", 3L),
        "TextDirection" to Variant("s", "ltr"),
        "Status" to Variant("s", "normal"),
        "IconThemePath" to Variant("as", emptyList<String>()),
    )

    /** A label as dbusmenu reads it: '_' marks an access key there, so a plain one is doubled. */
    fun label(text: String) = text.replace("_", "__")

    /** The properties of menu item [id]; null for an item there is none of. */
    fun itemOf(id: Int, now: TrayNow): Map<String, Variant>? = when (id) {
        ROOT_ID -> mapOf("children-display" to Variant("s", "submenu"))
        TOGGLE_ID -> mapOf("label" to Variant("s", label(if (now.windowShown) now.words.hide else now.words.show)))
        LINE_ID -> mapOf("type" to Variant("s", "separator"))
        QUIT_ID -> mapOf("label" to Variant("s", label(now.words.quit)))
        else -> null
    }

    /** The layout under [id] as (ia{sv}av), its children [depth] deep (-1: all). */
    fun layout(id: Int, depth: Int, now: TrayNow): List<Any?>? {
        val props = itemOf(id, now) ?: return null
        val children = if (id == ROOT_ID && depth != 0) ITEMS.map { Variant("(ia{sv}av)", layout(it, depth - 1, now)) } else emptyList()
        return listOf(id, props, children)
    }

    private fun only(props: Map<String, Variant>, names: List<*>): Map<String, Variant> =
        if (names.isEmpty()) props else props.filterKeys { it in names }

    /** What clicking menu item [id] asks. */
    fun clicked(id: Int): TrayAction? = when (id) {
        TOGGLE_ID -> TrayAction.TOGGLE
        QUIT_ID -> TrayAction.QUIT
        else -> null
    }

    /** The answer to [call] at the item's or the menu's path, while [now] stands; the icon [iconName] and [pixmaps]. */
    fun answer(call: DBusMessage, now: TrayNow, iconName: String, pixmaps: List<List<Any>>): TrayAnswer {
        val args = call.body
        val path = call.path
        if (call.iface == PEER && call.member == "Ping") return TrayAnswer()
        if (call.iface == INTROSPECTABLE && call.member == "Introspect") {
            return when (path) {
                ITEM_PATH -> TrayAnswer("s", listOf(ITEM_INTROSPECTION))
                MENU_PATH -> TrayAnswer("s", listOf(MENU_INTROSPECTION))
                "/" -> TrayAnswer("s", listOf(ROOT_INTROSPECTION))
                else -> TrayAnswer(error = DBusConnection.UNKNOWN_METHOD)
            }
        }
        if (call.iface == PROPERTIES) {
            val all = when (args.getOrNull(0) as? String) {
                ITEM -> itemProperties(now, iconName, pixmaps).takeIf { path == ITEM_PATH }
                MENU -> menuProperties().takeIf { path == MENU_PATH }
                else -> null
            }
            return when (call.member) {
                "GetAll" -> TrayAnswer("a{sv}", listOf(all ?: emptyMap<String, Variant>()))
                "Get" -> {
                    all ?: return TrayAnswer(error = UNKNOWN_INTERFACE)
                    all[args.getOrNull(1)]?.let { TrayAnswer("v", listOf(it)) } ?: TrayAnswer(error = UNKNOWN_PROPERTY)
                }
                else -> TrayAnswer(error = UNKNOWN_PROPERTY)
            }
        }
        return when (path) {
            ITEM_PATH -> itemCall(call)
            MENU_PATH -> menuCall(call, now)
            else -> TrayAnswer(error = DBusConnection.UNKNOWN_METHOD)
        }
    }

    private fun itemCall(call: DBusMessage): TrayAnswer = when (call.member) {
        // A click on the icon, and a middle click: the window comes on screen, in front.
        "Activate", "SecondaryActivate" -> TrayAnswer(action = TrayAction.SHOW)
        // The menu is ours ([MENU_PATH]); a host that asks for one anyway, and a scroll, change nothing.
        "ContextMenu", "Scroll" -> TrayAnswer()
        else -> TrayAnswer(error = DBusConnection.UNKNOWN_METHOD)
    }

    private fun menuCall(call: DBusMessage, now: TrayNow): TrayAnswer {
        val args = call.body
        return when (call.member) {
            "GetLayout" -> {
                val id = (args.getOrNull(0) as? Int) ?: ROOT_ID
                val depth = (args.getOrNull(1) as? Int) ?: -1
                val names = args.getOrNull(2) as? List<*> ?: emptyList<Any>()
                val layout = layout(id, depth, now) ?: return TrayAnswer(error = INVALID_ARGS)
                @Suppress("UNCHECKED_CAST")
                val filtered = listOf(layout[0], only(layout[1] as Map<String, Variant>, names), layout[2])
                TrayAnswer("u(ia{sv}av)", listOf(now.revision, filtered))
            }
            "GetGroupProperties" -> {
                val ids = (args.getOrNull(0) as? List<*>)?.filterIsInstance<Int>()?.takeIf { it.isNotEmpty() } ?: (listOf(ROOT_ID) + ITEMS)
                val names = args.getOrNull(1) as? List<*> ?: emptyList<Any>()
                TrayAnswer("a(ia{sv})", listOf(ids.mapNotNull { id -> itemOf(id, now)?.let { listOf(id, only(it, names)) } }))
            }
            "GetProperty" -> {
                val value = itemOf((args.getOrNull(0) as? Int) ?: -1, now)?.get(args.getOrNull(1))
                value?.let { TrayAnswer("v", listOf(it)) } ?: TrayAnswer(error = INVALID_ARGS)
            }
            "Event" -> {
                val id = (args.getOrNull(0) as? Int) ?: -1
                TrayAnswer(action = clicked(id).takeIf { args.getOrNull(1) == "clicked" })
            }
            "EventGroup" -> {
                // Each (id, event, data, time); the first click in it is the one acted on.
                val events = (args.getOrNull(0) as? List<*>).orEmpty().filterIsInstance<List<*>>()
                val action = events.firstNotNullOfOrNull { event -> clicked((event.getOrNull(0) as? Int) ?: -1).takeIf { event.getOrNull(1) == "clicked" } }
                val unknown = events.mapNotNull { event -> (event.getOrNull(0) as? Int)?.takeIf { itemOf(it, now) == null } }
                TrayAnswer("ai", listOf(unknown), action = action)
            }
            "AboutToShow" -> TrayAnswer("b", listOf(false))
            "AboutToShowGroup" -> TrayAnswer("aiai", listOf(emptyList<Int>(), emptyList<Int>()))
            else -> TrayAnswer(error = DBusConnection.UNKNOWN_METHOD)
        }
    }

    private const val HEAD = """<!DOCTYPE node PUBLIC "-//freedesktop//DTD D-BUS Object Introspection 1.0//EN" "http://www.freedesktop.org/standards/dbus/1.0/introspect.dtd">"""
    private const val COMMON = """
          <interface name="org.freedesktop.DBus.Introspectable"><method name="Introspect"><arg name="data" type="s" direction="out"/></method></interface>
          <interface name="org.freedesktop.DBus.Peer"><method name="Ping"/></interface>
          <interface name="org.freedesktop.DBus.Properties">
            <method name="Get"><arg name="interface" type="s" direction="in"/><arg name="property" type="s" direction="in"/><arg name="value" type="v" direction="out"/></method>
            <method name="GetAll"><arg name="interface" type="s" direction="in"/><arg name="properties" type="a{sv}" direction="out"/></method>
          </interface>"""

    private val ROOT_INTROSPECTION = """
        $HEAD
        <node><node name="StatusNotifierItem"/><node name="MenuBar"/></node>
    """.trimIndent()

    private val ITEM_INTROSPECTION = """
        $HEAD
        <node>$COMMON
          <interface name="org.kde.StatusNotifierItem">
            <property name="Category" type="s" access="read"/><property name="Id" type="s" access="read"/>
            <property name="Title" type="s" access="read"/><property name="Status" type="s" access="read"/>
            <property name="WindowId" type="i" access="read"/><property name="IconName" type="s" access="read"/>
            <property name="IconPixmap" type="a(iiay)" access="read"/><property name="IconThemePath" type="s" access="read"/>
            <property name="OverlayIconName" type="s" access="read"/><property name="OverlayIconPixmap" type="a(iiay)" access="read"/>
            <property name="AttentionIconName" type="s" access="read"/><property name="AttentionIconPixmap" type="a(iiay)" access="read"/>
            <property name="AttentionMovieName" type="s" access="read"/><property name="ToolTip" type="(sa(iiay)ss)" access="read"/>
            <property name="ItemIsMenu" type="b" access="read"/><property name="Menu" type="o" access="read"/>
            <method name="ContextMenu"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
            <method name="Activate"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
            <method name="SecondaryActivate"><arg name="x" type="i" direction="in"/><arg name="y" type="i" direction="in"/></method>
            <method name="Scroll"><arg name="delta" type="i" direction="in"/><arg name="orientation" type="s" direction="in"/></method>
            <signal name="NewTitle"/><signal name="NewIcon"/><signal name="NewAttentionIcon"/><signal name="NewOverlayIcon"/>
            <signal name="NewToolTip"/><signal name="NewStatus"><arg name="status" type="s"/></signal>
          </interface>
        </node>
    """.trimIndent()

    private val MENU_INTROSPECTION = """
        $HEAD
        <node>$COMMON
          <interface name="com.canonical.dbusmenu">
            <property name="Version" type="u" access="read"/><property name="TextDirection" type="s" access="read"/>
            <property name="Status" type="s" access="read"/><property name="IconThemePath" type="as" access="read"/>
            <method name="GetLayout"><arg type="i" name="parentId" direction="in"/><arg type="i" name="recursionDepth" direction="in"/><arg type="as" name="propertyNames" direction="in"/><arg type="u" name="revision" direction="out"/><arg type="(ia{sv}av)" name="layout" direction="out"/></method>
            <method name="GetGroupProperties"><arg type="ai" name="ids" direction="in"/><arg type="as" name="propertyNames" direction="in"/><arg type="a(ia{sv})" name="properties" direction="out"/></method>
            <method name="GetProperty"><arg type="i" name="id" direction="in"/><arg type="s" name="name" direction="in"/><arg type="v" name="value" direction="out"/></method>
            <method name="Event"><arg type="i" name="id" direction="in"/><arg type="s" name="eventId" direction="in"/><arg type="v" name="data" direction="in"/><arg type="u" name="timestamp" direction="in"/></method>
            <method name="EventGroup"><arg type="a(isvu)" name="events" direction="in"/><arg type="ai" name="idErrors" direction="out"/></method>
            <method name="AboutToShow"><arg type="i" name="id" direction="in"/><arg type="b" name="needUpdate" direction="out"/></method>
            <method name="AboutToShowGroup"><arg type="ai" name="ids" direction="in"/><arg type="ai" name="updatesNeeded" direction="out"/><arg type="ai" name="idErrors" direction="out"/></method>
            <signal name="ItemsPropertiesUpdated"><arg type="a(ia{sv})" name="updatedProps"/><arg type="a(ias)" name="removedProps"/></signal>
            <signal name="LayoutUpdated"><arg type="u" name="revision"/><arg type="i" name="parent"/></signal>
            <signal name="ItemActivationRequested"><arg type="i" name="id"/><arg type="u" name="timestamp"/></signal>
          </interface>
        </node>
    """.trimIndent()
}
