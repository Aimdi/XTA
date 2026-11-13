package com.aimdi.xtanative.plugins.pixiv

import com.aimdi.xtanative.core.json.J
import com.aimdi.xtanative.store.reading.ArticleReadPoint
import com.aimdi.xtanative.store.reading.ReadingJournal
import com.aimdi.xtanative.store.settings.PixivPrefs
import com.aimdi.xtanative.store.settings.Settings

/**
 * Where the reader left each novel, in XTA's journal for it
 * ([PixivPrefs.novelReading]: places by `pixiv-novel:<id>`, the newest
 * [ReadingJournal.LIMIT]), which no backup carries. As in XTA, places are
 * kept and given back only while the app [remembers] reading positions and
 * the viewing history is not paused.
 */
internal class PixivNovelPlaces(private val settings: PixivSettingsStore, private val remembers: () -> Boolean) {
    private val on: Boolean get() = remembers() && !settings[PixivPrefs.historyPaused]

    fun point(novelId: Long): ArticleReadPoint? = if (on) placesIn(settings.now)[key(novelId)] else null

    fun put(novelId: Long, point: ArticleReadPoint) {
        if (on) settings.update { kept(it, novelId, point) }
    }

    companion object {
        /** XTA's id for a novel in the journal (pixiv_novel_reader_screen.dart's pixivNovelReadingId). */
        fun key(novelId: Long): String = "pixiv-novel:$novelId"

        fun placesIn(settings: Settings): Map<String, ArticleReadPoint> = ReadingJournal.fromXta(J.parse(settings[PixivPrefs.novelReading]))

        /** [settings] with [point] kept for [novelId], only the newest places staying. */
        fun kept(settings: Settings, novelId: Long, point: ArticleReadPoint): Settings {
            val places = ReadingJournal.newest(placesIn(settings) + (key(novelId) to point))
            return settings.with(PixivPrefs.novelReading, ReadingJournal.toXta(places).toString())
        }
    }
}
