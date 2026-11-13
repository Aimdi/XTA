package com.aimdi.xtanative.networks.threads

/**
 * Which of the sizes Meta offers of a picture is shown where. Meta lists a
 * picture's candidates largest first, each with its size; a feed tile loads
 * the smallest that still fills it, the viewer and a download the largest.
 */
object ThreadsImages {
    /** The box Meta's CDN fitted a resized picture into, as its address names it: `…_p640x640_…`, `/s150x150/`. */
    private val boxInAddress = Regex("""(?:^|[_/=-])[sp](\d{2,5})x(\d{2,5})(?=$|[_/&.-])""")

    /** The size [url] names, for a candidate Meta gave no size: the box the picture was fitted into; null for one it does not name. */
    fun boxIn(url: String): Pair<Int, Int>? = boxInAddress.find(url)?.let { match ->
        val width = match.groupValues[1].toIntOrNull()
        val height = match.groupValues[2].toIntOrNull()
        if (width != null && height != null && width > 0 && height > 0) width to height else null
    }

    /**
     * [offered] with each address once, the largest first. Sizes are compared
     * by what Meta said, else by what the address names; candidates of one
     * size, and those of no known size, stay in Meta's order, which lists the
     * largest first, after the ones whose size is known.
     */
    fun ordered(offered: List<ThreadsImage>): List<ThreadsImage> {
        val once = offered.filter { it.url.isNotEmpty() }.distinctBy { it.url }
        val (sized, unsized) = once.partition { pixels(it) != null }
        return sized.sortedByDescending { pixels(it) } + unsized
    }

    /**
     * The size a box [width] by [height] pixels loads from [images] (largest
     * first, as [ordered] gives them): the smallest that covers the box on
     * both sides, so nothing is stretched; the largest when none does, or
     * when the box is not known yet. A [height] of 0 asks for the width alone.
     */
    fun fitting(images: List<ThreadsImage>, width: Int, height: Int = 0): ThreadsImage? {
        val largest = images.firstOrNull() ?: return null
        if (width <= 0) return largest
        return images.lastOrNull { covers(it, width, height) } ?: largest
    }

    private fun covers(image: ThreadsImage, width: Int, height: Int): Boolean {
        val (wide, tall) = size(image) ?: return false
        return wide >= width && (height <= 0 || tall >= height)
    }

    private fun pixels(image: ThreadsImage): Long? = size(image)?.let { (wide, tall) -> wide.toLong() * tall }

    private fun size(image: ThreadsImage): Pair<Int, Int>? {
        val wide = image.width
        val tall = image.height
        return if (wide != null && tall != null && wide > 0 && tall > 0) wide to tall else boxIn(image.url)
    }
}
