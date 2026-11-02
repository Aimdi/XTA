package com.aimdi.xtanative.core.x.model

/** The sizes pbs.twimg.com serves a photo at, smallest first. */
enum class PhotoSize(val param: String) { THUMB("thumb"), SMALL("small"), MEDIUM("medium"), LARGE("large"), ORIGINAL("orig") }

/**
 * [url] at [size]: `https://pbs.twimg.com/media/ID.jpg` becomes
 * `https://pbs.twimg.com/media/ID?format=jpg&name=medium`, so a feed row does
 * not download the 4K original.
 */
fun photoUrl(url: String, size: PhotoSize): String {
    if (!url.startsWith("https://pbs.twimg.com/")) return url
    val path = url.substringBefore('?')
    val dot = path.lastIndexOf('.')
    val slash = path.lastIndexOf('/')
    return if (dot > slash) {
        "${path.substring(0, dot)}?format=${path.substring(dot + 1)}&name=${size.param}"
    } else {
        "$path?name=${size.param}"
    }
}

/** The MP4 a phone plays well: the best at most [maxBitrate], else the lightest there is. */
fun XMedia.playableUrl(maxBitrate: Int = 2_500_000): String? {
    val mp4s = mp4s
    return (mp4s.firstOrNull { (it.bitrate ?: 0) <= maxBitrate } ?: mp4s.lastOrNull())?.url
        ?: variants.firstOrNull()?.url
}
