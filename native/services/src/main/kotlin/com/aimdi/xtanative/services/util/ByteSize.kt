package com.aimdi.xtanative.services.util

import java.util.Locale

private const val KB = 1024L
private const val MB = 1024L * KB
private const val GB = 1024L * MB

/**
 * [bytes] as B, KB, MB or GB with one decimal place above a kilobyte, as
 * XTA writes the size of a cache, a download or a voice (byte_size.dart).
 * The decimal point is always a point: the unit names are not translated
 * either.
 */
fun formatByteSize(bytes: Long): String = when {
    bytes >= GB -> "${oneDecimal(bytes.toDouble() / GB)} GB"
    bytes >= MB -> "${oneDecimal(bytes.toDouble() / MB)} MB"
    bytes >= KB -> "${oneDecimal(bytes.toDouble() / KB)} KB"
    else -> "$bytes B"
}

private fun oneDecimal(value: Double): String = String.format(Locale.ROOT, "%.1f", value)
