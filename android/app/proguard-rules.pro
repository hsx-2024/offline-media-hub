# Add project specific ProGuard rules here.

# 保留应用核心类
-keep class com.offline.mediahub.** { *; }

# 保留数据类
-keep class com.offline.mediahub.model.** { *; }

# 保留Parcelable实现
-keepclassmembers class * implements android.os.Parcelable {
    static ** CREATOR;
}

# ExoPlayer优化
-keep class androidx.media3.** { *; }
-dontwarn androidx.media3.**

# Glide优化
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind(android.os.ParcelFileDescriptor);
}

# Kotlin优化
-keep class kotlin.** { *; }
-keep class kotlin.Metadata { *; }
-dontwarn kotlin.**
-keepclassmembers class **$WhenMappings {
    <fields>;
}
-keepclassmembers class kotlin.Metadata {
    public <methods>;
}

# Coroutines
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-keepclassmembers class kotlinx.coroutines.** {
    volatile <fields>;
}

# 移除日志（发布版本）
-assumenosideeffects class android.util.Log {
    public static boolean isLoggable(java.lang.String, int);
    public static int v(...);
    public static int i(...);
    public static int d(...);
    public static int w(...);
    public static int e(...);
}

# 优化选项
-optimizationpasses 5
-dontusemixedcaseclassnames
-dontskipnonpubliclibraryclasses
-verbose
-optimizations !code/simplification/arithmetic,!field/*,!class/merging/*
