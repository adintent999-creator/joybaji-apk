# Bypass Studio v2.0 release rules.
# Keep only reflective/runtime boundaries; library AAR consumer rules cover the rest.

-keepattributes *Annotation*, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable
-keepparameternames
-renamesourcefileattribute SourceFile
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
-dontwarn androidx.webkit.**

# Android components (manifest-referenced)
-keep public class com.bypass.studio.MainActivity { *; }
-keep public class com.bypass.studio.VoiceService { *; }
-keep public class com.bypass.studio.OverlayService { *; }
-keep class androidx.core.content.FileProvider { *; }

# Room
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao class * { *; }
-keep class com.bypass.studio.StudioDatabase$* { *; }

# Kotlin coroutines service loader factories
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# WebView JavaScript bridge (if used by previews)
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}

# App models and singletons kept by name for debugging/reporting (small, app-only)
-keep class com.bypass.studio.**$* { *; }
-keepclassmembers class com.bypass.studio.** {
    public static final ** INSTANCE;
    public static ** fromJson(...);
    public java.lang.String toString();
}
