# kotlinx.serialization keeps what its generated serializers need.
-keepattributes *Annotation*, InnerClasses
-dontnote kotlinx.serialization.**
-keepclassmembers class kotlinx.serialization.json.** { *** Companion; }
-keep,includedescriptorclasses class com.aimdi.xtanative.**$$serializer { *; }
-keepclassmembers class com.aimdi.xtanative.** { *** Companion; }
-keepclasseswithmembers class com.aimdi.xtanative.** { kotlinx.serialization.KSerializer serializer(...); }
# OkHttp and jsoup ship their own rules; these platforms are optional.
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**
-dontwarn com.google.re2j.**

# Downloadable plugins (PLUGINS.md) are compiled against the app's modules and
# libraries without bundling them, and call them by name once loaded. So every
# public class and member a plugin may use keeps its name and stays in the APK:
# XTA Native's own modules (:core, :store, :services, :plugin-host, :plugin-api
# and :ui's shared components), the Kotlin standard library and kotlinx,
# Compose, lifecycle, navigation, OkHttp, jsoup and Coil.
-keep public class com.aimdi.xtanative.** { public protected *; }
-keep public class kotlin.** { public protected *; }
-keep public class kotlinx.coroutines.** { public protected *; }
-keep public class kotlinx.serialization.** { public protected *; }
-keep public class androidx.compose.runtime.** { public protected *; }
-keep public class androidx.compose.ui.** { public protected *; }
-keep public class androidx.compose.foundation.** { public protected *; }
-keep public class androidx.compose.animation.** { public protected *; }
-keep public class androidx.compose.material3.** { public protected *; }
-keep public class androidx.compose.material.** { public protected *; }
-keep public class androidx.lifecycle.** { public protected *; }
-keep public class androidx.navigation.** { public protected *; }
-keep public class androidx.savedstate.** { public protected *; }
-keep public class okhttp3.** { public protected *; }
-keep public class okio.** { public protected *; }
-keep public class org.jsoup.** { public protected *; }
-keep public class coil3.** { public protected *; }
# Annotations jsoup's kept API names but the app does not ship.
-dontwarn org.jspecify.annotations.**
-dontwarn javax.annotation.**
