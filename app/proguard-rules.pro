# Bypass Studio v2.0 release rules.
# Keep runtime-reflective types used by Android, Compose, Room, OkHttp, Jsoup and WebView.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-keepparameternames
-renamesourcefileattribute SourceFile

# Android components (manifest-referenced)
-keep public class com.bypass.studio.MainActivity { *; }
-keep public class com.bypass.studio.VoiceService { *; }
-keep public class com.bypass.studio.OverlayService { *; }

# AndroidX / Compose
-keep class androidx.compose.** { *; }
-keep class androidx.lifecycle.** { *; }
-keep class androidx.core.content.FileProvider { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-dontobfuscate class com.bypass.studio.StudioDatabase$*

# Kotlin coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# OkHttp
-keep class okhttp3.** { *; }
-dontwarn okhttp3.**
-dontwarn okio.**

# Jsoup
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# WebView JavaScript bridge (if used by previews)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class androidx.webkit.** { *; }

# Serialization-like models and data classes kept by name for debugging/reporting
-keep class com.bypass.studio.**$* { *; }
-keepclassmembers class com.bypass.studio.** {
    public static final ** INSTANCE;
    public static ** fromJson(...);
    public java.lang.String toString();
}
