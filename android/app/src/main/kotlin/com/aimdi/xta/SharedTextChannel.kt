package com.aimdi.xta

import android.content.Intent
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import java.util.ArrayDeque

/** Keeps launch-time shares until the Flutter navigator is ready. */
class SharedTextChannel(messenger: BinaryMessenger) : EventChannel.StreamHandler {
    private val channel = EventChannel(messenger, "com.aimdi.xta/shared_text")
    private val pending = ArrayDeque<String>()
    private var sink: EventChannel.EventSink? = null

    init {
        channel.setStreamHandler(this)
    }

    fun receive(intent: Intent?) {
        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
        val text = try {
            val extra = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
            val clip = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)
            // Read literal text only: never open a content URI or nested intent.
            (extra ?: clip?.text ?: clip?.uri?.toString())?.take(65536)?.toString() ?: ""
        } catch (_: RuntimeException) {
            ""
        }
        val receiver = sink
        if (receiver != null) {
            receiver.success(text)
        } else {
            if (pending.size == 8) pending.removeFirst()
            pending.addLast(text)
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        sink = events
        while (pending.isNotEmpty()) events.success(pending.removeFirst())
    }

    override fun onCancel(arguments: Any?) {
        sink = null
    }

    fun dispose() {
        channel.setStreamHandler(null)
        pending.clear()
        sink = null
    }
}
