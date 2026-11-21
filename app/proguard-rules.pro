# Bypass Studio v2.1 release rules.
# Explicit reflective/runtime boundaries; library AAR consumer rules still apply.

-keepattributes *Annotation*, Exceptions, Signature, InnerClasses, EnclosingMethod, SourceFile, LineNumberTable, RuntimeVisibleAnnotations, RuntimeInvisibleAnnotations, RuntimeVisibleParameterAnnotations, RuntimeInvisibleParameterAnnotations, AnnotationDefault
-keepparameternames
-renamesourcefileattribute SourceFile
-adaptresourcefilenames **.properties,**.xml
-adaptresourcefilecontents **.properties

# Warnings from optional/platform boundaries.
-dontwarn androidx.compose.**
-dontwarn androidx.lifecycle.**
-dontwarn androidx.room.**
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn org.jsoup.**
-dontwarn androidx.webkit.**
-dontwarn kotlinx.serialization.**
-dontwarn kotlinx.coroutines.**
-dontwarn org.nanohttpd.**
-dontwarn moe.shizuku.**

# Android components referenced from manifest or created reflectively.
-keep public class * extends android.app.Activity { *; }
-keep public class * extends android.app.Service { *; }
-keep public class * extends android.content.BroadcastReceiver { *; }
-keep public class * extends android.content.ContentProvider { *; }
-keep public class com.bypass.studio.MainActivity { *; }
-keep public class com.bypass.studio.VoiceService { *; }
-keep public class com.bypass.studio.OverlayService { *; }
-keep class androidx.core.content.FileProvider { *; }

# App-specific runtime boundaries.
-keep class com.bypass.studio.BroadcastExporter { *; }
-keep class com.bypass.studio.VoiceEngine { *; }
-keep class com.bypass.studio.AiClient { *; }
-keep class com.bypass.studio.FileRepo { *; }
-keep class com.bypass.studio.StudioViewModel { *; }
-keep class com.bypass.studio.PendingAction { *; }
-keep class com.bypass.studio.ChatMessage { *; }
-keep class com.bypass.studio.ServerInfo { *; }
-keep class com.bypass.studio.Chat { *; }
-keep class com.bypass.studio.Memory { *; }
-keep class com.bypass.studio.AiConfig { *; }
-keep class com.bypass.studio.BroadcastLine { *; }
-keep class com.bypass.studio.Provider { *; }
-keep class com.bypass.studio.StudioTheme { *; }
-keep class com.bypass.studio.Palette { *; }

# Room: entities, DAOs, database and generated implementation.
-keep class * extends androidx.room.RoomDatabase { *; }
-keep @androidx.room.Entity class * { *; }
-keep @androidx.room.Dao interface * { *; }
-keep @androidx.room.Dao class * { *; }
-keep @androidx.room.Database class * { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    public static ** INSTANCE;
    public <init>(...);
}
-keepclassmembers @androidx.room.Entity class * {
    public <init>(...);
    *** get*();
    void set*(***);
}
-keep class com.bypass.studio.StudioDatabase { *; }
-keep class com.bypass.studio.StudioDatabase$* { *; }

# kotlinx.serialization reflectively generated serializers.
-keepclasseswithmembers class * {
    @kotlinx.serialization.Serializable <fields>;
}
-keepclassmembers class **$$serializer { *; }
-keepclassmembers class kotlinx.serialization.json.** {
    *** Companion;
}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}

# WebView JavaScript bridge.
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keepclassmembers class com.bypass.studio.PreviewScreen$* { *; }

# Networking and HTML parsing reflection boundaries.
-keep class okhttp3.** { *; }
-keep interface okhttp3.** { *; }
-keep class org.jsoup.** { *; }
-keep class org.jsoup.nodes.** { *; }
-keep class org.jsoup.select.** { *; }
-keep class org.nanohttpd.** { *; }
-keep interface org.nanohttpd.** { *; }

# Shizuku/AIDL and binder boundaries when present.
-keep class moe.shizuku.api.** { *; }
-keep interface moe.shizuku.api.** { *; }
-keep class * implements android.os.IInterface { *; }
-keep class com.bypass.studio.**$Stub { *; }
-keep class com.bypass.studio.**$Stub$Proxy { *; }

# MediaCodec/MediaMuxer export path.
-keep class android.media.MediaCodec { *; }
-keep class android.media.MediaCodecInfo { *; }
-keep class android.media.MediaFormat { *; }
-keep class android.media.MediaMuxer { *; }
-keep class com.bypass.studio.BroadcastExporter$* { *; }

# Bundled font resource ids must remain addressable by name.
-keepclassmembers class com.bypass.studio.R$font { public static final int *; }
-keepclassmembers class com.bypass.studio.R$raw { public static final int *; }

# Kotlin coroutines service loader factories.
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}

# App data classes and singletons referenced by name in UI/tests.
-keep class com.bypass.studio.**$* { *; }
-keepclassmembers class com.bypass.studio.** {
    public static final ** INSTANCE;
    public static ** fromJson(...);
    public java.lang.String toString();
}
