package com.aimdi.xtanative.store.downloads

/**
 * Where the download setting sends a new download: a picked folder, the
 * shared folder for its type without asking ([SHARED]), or wherever the
 * reader picks each time ([ASK]).
 */
data class DownloadDestination(val treeUri: String?, val background: Boolean) {
    val asks: Boolean get() = treeUri == null && !background

    companion object {
        /** The values of XTA's `download.type` setting. */
        const val TYPE_AUTO = "auto"
        const val TYPE_ASK = "ask"
        const val TYPE_DIRECTORY = "directory"

        val ASK = DownloadDestination(null, false)
        val SHARED = DownloadDestination(null, true)

        fun folder(treeUri: String) = DownloadDestination(treeUri, false)

        /**
         * The destination [type] and the folder picked for it ([treeUri]) set.
         * Anything but ask or directory saves in the background, as a fresh
         * install does; a directory without a folder still asks, as it always has.
         */
        fun of(type: String?, treeUri: String?): DownloadDestination = when (type) {
            TYPE_ASK -> ASK
            TYPE_DIRECTORY -> treeUri?.takeIf { it.isNotEmpty() }?.let(::folder) ?: ASK
            else -> SHARED
        }

        /**
         * The type to keep when moving readers off the former ask default, done
         * once: an ask nobody chose becomes auto. A folder ever picked
         * ([folderChosen]) shows the setting was visited, so that ask stays, as
         * does every other choice.
         */
        fun migratedType(type: String?, folderChosen: Boolean): String? = if (type == TYPE_ASK && !folderChosen) TYPE_AUTO else type

        /** Where a batch goes: the set destination, else a folder [pick]ed once for all of it; null when the reader backs out. */
        suspend fun forBatch(configured: DownloadDestination, pick: suspend () -> String?): DownloadDestination? =
            if (!configured.asks) configured else pick()?.let(::folder)
    }
}
