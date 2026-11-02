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
