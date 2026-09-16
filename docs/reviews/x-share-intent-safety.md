# Best practices and security alignment update: incoming Android shares

- **Area:** exported share receiver and incoming URL validation.
- **Impact and priority:** medium; new external input must not launch nested intents,
  read supplied content URIs, accept deceptive hosts, or follow arbitrary redirects.
- **Scope:** MainActivity, SharedTextChannel, AndroidManifest, shared_links.dart and
  the main.dart subscription/navigation wiring. No dependencies or permissions added.
- **Implementation:** accept SEND/text/plain; cap literal payloads at 64 KiB and
  pending events at eight; buffer cold-start events and consume warm intents once.
  Set the activity intent before dispatch. Extract only HTTP(S) links with exact
  supported hosts and no userinfo/custom ports. Resolve t.co with automatic
  redirects disabled, an eight-second per-request timeout and a three-hop limit.
  Apply the same extraction to launch and onNewIntent deliveries. Reject unsupported
  shares using the existing translated error. Only existing reader routes open.
- **Verification:** shared-link tests cover captions, punctuation, deceptive hosts,
  unsupported schemes, redirects, loops and ordinary links requiring no resolution
  request. The PR Actions checks also compile the Android implementation. Physical
  Android chooser delivery remains a hands-on check; CI results are in PR #271.

## Implementation diff

```diff
diff --git a/android/app/src/main/AndroidManifest.xml b/android/app/src/main/AndroidManifest.xml
index c05a06d0..8fb9f8d0 100644
--- a/android/app/src/main/AndroidManifest.xml
+++ b/android/app/src/main/AndroidManifest.xml
@@ -56,6 +56,11 @@
                 <action android:name="android.intent.action.MAIN" />
                 <category android:name="android.intent.category.LAUNCHER" />
             </intent-filter>
+            <intent-filter>
+                <action android:name="android.intent.action.SEND" />
+                <category android:name="android.intent.category.DEFAULT" />
+                <data android:mimeType="text/plain" />
+            </intent-filter>
             <intent-filter>
                 <action android:name="android.intent.action.VIEW" />
 
diff --git a/android/app/src/main/kotlin/com/aimdi/xta/MainActivity.kt b/android/app/src/main/kotlin/com/aimdi/xta/MainActivity.kt
index be4465ce..1e82df35 100644
--- a/android/app/src/main/kotlin/com/aimdi/xta/MainActivity.kt
+++ b/android/app/src/main/kotlin/com/aimdi/xta/MainActivity.kt
@@ -20,9 +20,12 @@ class MainActivity : AudioServiceActivity() {
     // Set while the document-tree picker is open, so its result can be handed
     // back to the Dart call that opened it.
     private var pendingDirectoryResult: MethodChannel.Result? = null
+    private var sharedTextChannel: SharedTextChannel? = null
 
     override fun configureFlutterEngine(flutterEngine: FlutterEngine) {
         super.configureFlutterEngine(flutterEngine)
+        sharedTextChannel = SharedTextChannel(flutterEngine.dartExecutor.binaryMessenger)
+            .also { it.receive(intent) }
 
         MethodChannel(flutterEngine.dartExecutor.binaryMessenger, CHANNEL)
             .setMethodCallHandler { call, result ->
@@ -39,6 +42,18 @@ class MainActivity : AudioServiceActivity() {
             }
     }
 
+    override fun onNewIntent(intent: Intent) {
+        setIntent(intent)
+        super.onNewIntent(intent)
+        sharedTextChannel?.receive(intent)
+    }
+
+    override fun cleanUpFlutterEngine(flutterEngine: FlutterEngine) {
+        sharedTextChannel?.dispose()
+        sharedTextChannel = null
+        super.cleanUpFlutterEngine(flutterEngine)
+    }
+
     /**
      * Shrinks the activity into a floating window, shaped like the video.
      *
diff --git a/android/app/src/main/kotlin/com/aimdi/xta/SharedTextChannel.kt b/android/app/src/main/kotlin/com/aimdi/xta/SharedTextChannel.kt
new file mode 100644
index 00000000..41ce2d9f
--- /dev/null
+++ b/android/app/src/main/kotlin/com/aimdi/xta/SharedTextChannel.kt
@@ -0,0 +1,51 @@
+package com.aimdi.xta
+
+import android.content.Intent
+import io.flutter.plugin.common.BinaryMessenger
+import io.flutter.plugin.common.EventChannel
+import java.util.ArrayDeque
+
+/** Keeps launch-time shares until the Flutter navigator is ready. */
+class SharedTextChannel(messenger: BinaryMessenger) : EventChannel.StreamHandler {
+    private val channel = EventChannel(messenger, "com.aimdi.xta/shared_text")
+    private val pending = ArrayDeque<String>()
+    private var sink: EventChannel.EventSink? = null
+
+    init {
+        channel.setStreamHandler(this)
+    }
+
+    fun receive(intent: Intent?) {
+        if (intent?.action != Intent.ACTION_SEND || intent.type != "text/plain") return
+        val text = try {
+            val extra = intent.getCharSequenceExtra(Intent.EXTRA_TEXT)
+            val clip = intent.clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)
+            // Read literal text only: never open a content URI or nested intent.
+            (extra ?: clip?.text ?: clip?.uri?.toString())?.take(65536)?.toString() ?: ""
+        } catch (_: RuntimeException) {
+            ""
+        }
+        val receiver = sink
+        if (receiver != null) {
+            receiver.success(text)
+        } else {
+            if (pending.size == 8) pending.removeFirst()
+            pending.addLast(text)
+        }
+    }
+
+    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
+        sink = events
+        while (pending.isNotEmpty()) events.success(pending.removeFirst())
+    }
+
+    override fun onCancel(arguments: Any?) {
+        sink = null
+    }
+
+    fun dispose() {
+        channel.setStreamHandler(null)
+        pending.clear()
+        sink = null
+    }
+}
diff --git a/lib/utils/shared_links.dart b/lib/utils/shared_links.dart
new file mode 100644
index 00000000..bd1acbbc
--- /dev/null
+++ b/lib/utils/shared_links.dart
@@ -0,0 +1,57 @@
+import 'package:flutter/services.dart';
+import 'package:http/http.dart' as http;
+
+const sharedTextChannel = EventChannel('com.aimdi.xta/shared_text');
+const _shareHosts = {
+  'x.com',
+  'www.x.com',
+  'mobile.x.com',
+  'twitter.com',
+  'www.twitter.com',
+  'mobile.twitter.com',
+  't.co',
+  'fxtwitter.com',
+  'www.fxtwitter.com',
+  'vxtwitter.com',
+  'www.vxtwitter.com',
+  'fixupx.com',
+  'www.fixupx.com',
+};
+
+bool _supported(Uri uri) =>
+    (uri.scheme == 'https' || uri.scheme == 'http') &&
+    uri.userInfo.isEmpty &&
+    !uri.hasPort &&
+    _shareHosts.contains(uri.host.toLowerCase());
+
+/// Shares often contain the post's caption before the actual link.
+Uri? extractSharedXLink(String text) {
+  final urls = RegExp(r'''https?://[^\s<>"\u200b]+''', caseSensitive: false);
+  for (final match in urls.allMatches(text)) {
+    final candidate = match.group(0)!.replaceFirst(RegExp(r'''[)\]}>.,!?;:'"]+$'''), '');
+    final uri = Uri.tryParse(candidate);
+    if (uri != null && _supported(uri)) return uri;
+  }
+  return null;
+}
+
+/// Resolve only X short links, without following redirects to arbitrary hosts.
+Future<Uri?> resolveSharedXLink(String text, {http.Client? client}) async {
+  var uri = extractSharedXLink(text);
+  if (uri == null || uri.host != 't.co') return uri;
+  final transport = client ?? http.Client();
+  try {
+    for (var hop = 0; hop < 3 && uri != null && uri.host == 't.co'; hop++) {
+      final request = http.Request('GET', uri)..followRedirects = false;
+      final response = await transport.send(request).timeout(const Duration(seconds: 8));
+      await response.stream.listen((_) {}).cancel();
+      final location = response.headers['location'];
+      if (response.statusCode < 300 || response.statusCode >= 400 || location == null) return null;
+      uri = uri.resolve(location);
+      if (!_supported(uri)) return null;
+    }
+    return uri?.host == 't.co' ? null : uri;
+  } finally {
+    if (client == null) transport.close();
+  }
+}
```
