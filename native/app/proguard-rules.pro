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
# Commons Compress and xz-java unpack the downloaded voices and voice engine:
# the codecs Commons Compress can also use, which the app does not ship, and
# lambda classes of the JDK that Android's runtime lacks.
-dontwarn com.github.luben.zstd.**
-dontwarn org.brotli.dec.**
-dontwarn org.objectweb.asm.**
-dontwarn java.lang.invoke.MethodHandleProxies
-dontwarn java.lang.invoke.SerializedLambda

# Downloadable plugins (PLUGINS.md) are compiled against the app's modules and
# libraries without bundling them, and call them by name once loaded. So every
# public class and member a plugin may use keeps its name and stays in the APK:
# XTA Native's own modules (:core, :store, :services, :ui with its plugin API
# in ui.plugins and the data package, and the types every network shares, in
# the networks package itself), the Kotlin standard library and kotlinx,
# Compose, lifecycle, navigation, OkHttp, jsoup and Coil. A network's own
# package (networks.<id>) is not kept: what is a plugin is not in the app,
# and what is not yet one goes unless the app uses it.
-keep public class com.aimdi.xtanative.core.** { public protected *; }
-keep public class com.aimdi.xtanative.store.** { public protected *; }
-keep public class com.aimdi.xtanative.services.** { public protected *; }
-keep public class com.aimdi.xtanative.networks.* { public protected *; }
-keep public class com.aimdi.xtanative.ui.** { public protected *; }
-keep public class com.aimdi.xtanative.data.** { public protected *; }
# Kotlin's own description of each class (kotlin.Metadata) is for compilers and
# kotlin-reflect; a plugin already compiled, and the app, which reflects on
# nothing, run without it. Leaving the annotation out of what is kept lets R8
# drop it from every class, which is a good part of a megabyte of the APK.
-keep public class !kotlin.Metadata, kotlin.** { public protected *; }
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

# Kotlin checks every parameter and platform value it was promised is not null
# and throws at once when it is. In a release build that is a call and a name
# at the head of nearly every function; without them a null goes on to where
# it is used and fails there instead. `!!` (checkNotNull) is left as written.
-assumenosideeffects class kotlin.jvm.internal.Intrinsics {
    public static void checkNotNullParameter(java.lang.Object, java.lang.String);
    public static void checkParameterIsNotNull(java.lang.Object, java.lang.String);
    public static void checkNotNullExpressionValue(java.lang.Object, java.lang.String);
    public static void checkExpressionValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String);
    public static void checkReturnedValueIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String);
    public static void checkFieldIsNotNull(java.lang.Object, java.lang.String, java.lang.String);
}
