package com.aimdi.xtanative.ui.theme

import com.aimdi.xtanative.store.settings.DesktopPrefs
import com.aimdi.xtanative.store.settings.Settings
import java.awt.GraphicsEnvironment
import java.awt.Toolkit
import java.util.concurrent.CompletableFuture
import java.util.concurrent.TimeUnit
import kotlin.math.abs

/**
 * The interface size a first start takes from the desktop: Java 17 scales
 * only by J2D_UISCALE, GDK_SCALE or GNOME's whole-number factor, so a
 * desktop at 150% (KDE on X11, or XWayland where apps scale themselves; GNOME's
 * large text) would draw the app at 1x, its text tiny until the reader found
 * Accessibility › Interface size. The desktop's text dpi (Xft.dpi, from its
 * XSETTINGS or X resources) over Java's own scale gives the size, rounded to
 * the nearest of [DesktopPrefs.UI_SCALES]. A size the reader chose is never
 * touched.
 */
object DesktopScale {
    /** The text dpi a desktop at 100% has. */
    private const val BASE_DPI = 96.0

    /** XSETTINGS keeps the dpi times 1024. */
    private const val XSETTINGS_UNIT = 1024.0

    /** How long `xrdb -query` may take before the start goes on without it. */
    private const val XRDB_MILLIS = 1_500L

    /**
     * The interface size for a desktop text dpi of [dpi] where Java already
     * draws at [javaScale]: the step of [steps] nearest their ratio, or null
     * when that is 1.0 or nothing is known, and the size is left to its default.
     */
    fun sizeFor(dpi: Double?, javaScale: Double, steps: List<Double>): Double? {
        if (dpi == null || dpi <= 0.0 || javaScale <= 0.0) return null
        val factor = dpi / BASE_DPI / javaScale
        return steps.minByOrNull { abs(it - factor) }?.takeIf { it != 1.0 }
    }

    /** Xft.dpi in `xrdb -query`'s [text], null when it has none. */
    fun xrdbDpi(text: String): Double? = text.lineSequence()
        .map { it.trim() }
        .firstOrNull { it.startsWith("Xft.dpi:") }
        ?.substringAfter(':')?.trim()?.toDoubleOrNull()
        ?.takeIf { it > 0.0 }

    /** The dpi an XSETTINGS value of Xft/DPI stands for (times 1024, as the desktop's settings daemon sends it); null for none. */
    fun xsettingsDpi(value: Any?): Double? = (value as? Number)?.toDouble()?.takeIf { it > 0.0 }?.div(XSETTINGS_UNIT)

    /** [settings] with the interface size the desktop asks for, where the reader has none of their own; else unchanged. */
    fun seeded(settings: Settings, detect: () -> Double?): Settings {
        if (settings.isSet(DesktopPrefs.uiScale)) return settings
        val size = detect() ?: return settings
        return settings.with(DesktopPrefs.uiScale, size)
    }

    /** The size this desktop asks for ([sizeFor]); null on a desktop at 100%, without a screen, or with nothing to read. */
    fun detected(): Double? = runCatching {
        if (GraphicsEnvironment.isHeadless()) return null
        val javaScale = GraphicsEnvironment.getLocalGraphicsEnvironment().defaultScreenDevice.defaultConfiguration.defaultTransform.scaleX
        val dpi = xsettingsDpi(Toolkit.getDefaultToolkit().getDesktopProperty("gnome.Xft/DPI")) ?: xrdbDpi(xrdbQuery().orEmpty())
        sizeFor(dpi, javaScale, DesktopPrefs.UI_SCALES)
    }.getOrNull()

    /** What `xrdb -query` prints for the display, null where there is no xrdb or it does not answer in time. */
    private fun xrdbQuery(): String? = runCatching {
        val process = ProcessBuilder("xrdb", "-query").redirectErrorStream(true).start()
        process.outputStream.close()
        val text = CompletableFuture.supplyAsync { process.inputStream.bufferedReader().readText() }
        try {
            text.get(XRDB_MILLIS, TimeUnit.MILLISECONDS)
        } finally {
            process.destroy()
        }
    }.getOrNull()
}
